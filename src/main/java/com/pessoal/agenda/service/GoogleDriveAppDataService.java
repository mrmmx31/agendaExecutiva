package com.pessoal.agenda.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/** Cliente mínimo do Drive limitado à pasta privada appDataFolder. */
public final class GoogleDriveAppDataService {
    static final String BACKUP_FILE_NAME = "agenda-signing-key-v1.enc";
    private static final String API = "https://www.googleapis.com/drive/v3";
    private static final String UPLOAD_API = "https://www.googleapis.com/upload/drive/v3";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final Transport transport;

    public GoogleDriveAppDataService() {
        GoogleAuthService auth = GoogleAuthService.getInstance();
        SSLParameters tls = new SSLParameters();
        tls.setProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
        HttpClient http = HttpClient.newBuilder()
                .sslParameters(tls)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.transport = new JdkTransport(auth, http);
    }

    GoogleDriveAppDataService(Transport transport) {
        this.transport = transport;
    }

    public Optional<BackupMetadata> findBackup() throws IOException, InterruptedException {
        String query = "name='" + BACKUP_FILE_NAME + "' and trashed=false";
        String path = API + "/files?spaces=appDataFolder&q=" + encode(query)
                + "&fields=" + encode("files(id,name,modifiedTime,size)")
                + "&orderBy=" + encode("modifiedTime desc");
        Response response = sendWithAuthRetry(new Request("GET", path, null, null));
        requireSuccess(response.statusCode());
        try {
            JsonObject root = JsonParser.parseString(response.text()).getAsJsonObject();
            return root.getAsJsonArray("files").asList().stream()
                    .map(element -> element.getAsJsonObject())
                    .map(GoogleDriveAppDataService::metadata)
                    .max(Comparator.comparing(BackupMetadata::modifiedAt));
        } catch (RuntimeException error) {
            throw GoogleSyncException.invalidResponse();
        }
    }

    public BackupMetadata upload(byte[] encryptedBackup)
            throws IOException, InterruptedException {
        Optional<BackupMetadata> existing = findBackup();
        String boundary = "agenda-" + UUID.randomUUID();
        JsonObject metadata = new JsonObject();
        metadata.addProperty("name", BACKUP_FILE_NAME);
        if (existing.isEmpty()) {
            metadata.add("parents", new Gson().toJsonTree(new String[]{"appDataFolder"}));
        }
        byte[] body = multipart(boundary, metadata.toString(), encryptedBackup);
        String url = existing
                .map(file -> UPLOAD_API + "/files/" + encode(file.id()) + "?uploadType=multipart&fields="
                        + encode("id,name,modifiedTime,size"))
                .orElse(UPLOAD_API + "/files?uploadType=multipart&fields="
                        + encode("id,name,modifiedTime,size"));
        String method = existing.isPresent() ? "PATCH" : "POST";
        Response response = sendWithAuthRetry(new Request(method, url,
                "multipart/related; boundary=" + boundary, body));
        requireSuccess(response.statusCode());
        try {
            return metadata(JsonParser.parseString(response.text()).getAsJsonObject());
        } catch (RuntimeException error) {
            throw GoogleSyncException.invalidResponse();
        }
    }

    public byte[] download() throws IOException, InterruptedException {
        BackupMetadata backup = findBackup().orElseThrow(() ->
                new IOException("Nenhum backup da chave foi encontrado no Google Drive."));
        Response response = sendWithAuthRetry(new Request("GET",
                API + "/files/" + encode(backup.id()) + "?alt=media", null, null));
        requireSuccess(response.statusCode());
        return response.body();
    }

    private Response sendWithAuthRetry(Request request) throws IOException, InterruptedException {
        Response response;
        try {
            response = transport.send(request);
            if (response.statusCode() == 401) {
                transport.refreshAuthentication();
                response = transport.send(request);
            }
        } catch (IOException error) {
            throw GoogleSyncException.fromIOException(error);
        }
        return response;
    }

    private static BackupMetadata metadata(JsonObject json) {
        String id = json.get("id").getAsString();
        String name = json.get("name").getAsString();
        Instant modified = Instant.parse(json.get("modifiedTime").getAsString());
        long size = json.has("size") ? json.get("size").getAsLong() : 0;
        return new BackupMetadata(id, name, modified, size);
    }

    private static byte[] multipart(String boundary, String metadata, byte[] data) {
        byte[] prefix = ("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
                + metadata + "\r\n--" + boundary
                + "\r\nContent-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[prefix.length + data.length + suffix.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(data, 0, result, prefix.length, data.length);
        System.arraycopy(suffix, 0, result, prefix.length + data.length, suffix.length);
        return result;
    }

    private static void requireSuccess(int status) throws GoogleSyncException {
        if (status < 200 || status >= 300) throw GoogleSyncException.forStatus(status);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record BackupMetadata(String id, String name, Instant modifiedAt, long size) {}
    record Request(String method, String uri, String contentType, byte[] body) {}
    record Response(int statusCode, byte[] body) {
        String text() { return new String(body, StandardCharsets.UTF_8); }
    }

    interface Transport {
        Response send(Request request) throws IOException, InterruptedException;
        default void refreshAuthentication() throws IOException {}
    }

    private record JdkTransport(GoogleAuthService auth, HttpClient http) implements Transport {
        @Override public Response send(Request value) throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(value.uri()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + auth.getAccessToken());
            if (value.contentType() != null) builder.header("Content-Type", value.contentType());
            HttpRequest request = switch (value.method()) {
                case "GET" -> builder.GET().build();
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofByteArray(value.body())).build();
                case "PATCH" -> builder.method("PATCH",
                        HttpRequest.BodyPublishers.ofByteArray(value.body())).build();
                default -> throw new IllegalArgumentException("Método HTTP não suportado");
            };
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return new Response(response.statusCode(), response.body());
        }

        @Override public void refreshAuthentication() {
            auth.invalidateAccessToken();
        }
    }
}
