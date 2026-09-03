package com.pessoal.agenda.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleDriveAppDataServiceTest {
    @Test
    void listsOnlyAppDataAndDownloadsNewestBackup() throws Exception {
        List<GoogleDriveAppDataService.Request> requests = new ArrayList<>();
        GoogleDriveAppDataService service = new GoogleDriveAppDataService(request -> {
            requests.add(request);
            if (request.uri().contains("alt=media")) {
                return new GoogleDriveAppDataService.Response(200, new byte[]{4, 5, 6});
            }
            String json = "{\"files\":[{\"id\":\"file-id\",\"name\":\"agenda-signing-key-v1.enc\","
                    + "\"modifiedTime\":\"2026-09-03T20:00:00Z\",\"size\":\"42\"}]}";
            return response(200, json);
        });

        assertEquals(Instant.parse("2026-09-03T20:00:00Z"),
                service.findBackup().orElseThrow().modifiedAt());
        assertArrayEquals(new byte[]{4, 5, 6}, service.download());
        assertTrue(requests.getFirst().uri().contains("spaces=appDataFolder"));
        assertFalse(requests.getFirst().uri().contains("drive.file"));
    }

    @Test
    void updatesExistingFileAndRetriesAuthenticationOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        GoogleDriveAppDataService.Transport transport = new GoogleDriveAppDataService.Transport() {
            @Override public GoogleDriveAppDataService.Response send(
                    GoogleDriveAppDataService.Request request) {
                int call = calls.incrementAndGet();
                if (call == 1) return response(401, "{}");
                if (request.method().equals("GET")) {
                    return response(200, "{\"files\":[{\"id\":\"existing\","
                            + "\"name\":\"agenda-signing-key-v1.enc\","
                            + "\"modifiedTime\":\"2026-09-03T20:00:00Z\",\"size\":\"1\"}]}");
                }
                assertEquals("PATCH", request.method());
                assertTrue(request.uri().contains("/files/existing"));
                assertTrue(new String(request.body(), StandardCharsets.ISO_8859_1)
                        .contains("application/octet-stream"));
                return response(200, "{\"id\":\"existing\","
                        + "\"name\":\"agenda-signing-key-v1.enc\","
                        + "\"modifiedTime\":\"2026-09-03T21:00:00Z\",\"size\":\"3\"}");
            }

            @Override public void refreshAuthentication() { refreshes.incrementAndGet(); }
        };

        GoogleDriveAppDataService.BackupMetadata result =
                new GoogleDriveAppDataService(transport).upload(new byte[]{1, 2, 3});

        assertEquals(Instant.parse("2026-09-03T21:00:00Z"), result.modifiedAt());
        assertEquals(1, refreshes.get());
    }

    private static GoogleDriveAppDataService.Response response(int status, String body) {
        return new GoogleDriveAppDataService.Response(status, body.getBytes(StandardCharsets.UTF_8));
    }
}
