package com.pessoal.agenda.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleTasksTransportTest {

    @TempDir
    Path tempDir;

    @Test
    void readRetriesOnceAfterTemporaryServerFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        GoogleTasksService service = new GoogleTasksService(request ->
                calls.incrementAndGet() == 1
                        ? new GoogleTasksService.ApiResponse(503, "sensitive-body")
                        : new GoogleTasksService.ApiResponse(200, "{\"items\":[]}"));

        assertTrue(service.listTaskLists().isEmpty());
        assertEquals(2, calls.get());
    }

    @Test
    void writeDoesNotRetryAfterAmbiguousServerFailureOrExposeBody() {
        AtomicInteger calls = new AtomicInteger();
        GoogleTasksService service = new GoogleTasksService(request -> {
            calls.incrementAndGet();
            return new GoogleTasksService.ApiResponse(
                    503, "{\"access_token\":\"never-log-this\"}");
        });

        GoogleSyncException error = assertThrows(GoogleSyncException.class,
                () -> service.createTask("list", "Título", null, LocalDate.now()));

        assertEquals(GoogleSyncException.Kind.SERVER, error.kind());
        assertEquals(1, calls.get());
        assertFalse(error.userMessage().contains("never-log-this"));
    }

    @Test
    void unauthorizedRequestRefreshesAuthenticationOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        GoogleTasksService.ApiTransport transport = new GoogleTasksService.ApiTransport() {
            @Override
            public GoogleTasksService.ApiResponse send(GoogleTasksService.ApiRequest request) {
                return calls.incrementAndGet() == 1
                        ? new GoogleTasksService.ApiResponse(401, "expired-token")
                        : new GoogleTasksService.ApiResponse(200, "{\"id\":\"created-1\"}");
            }

            @Override
            public void refreshAuthentication() {
                refreshes.incrementAndGet();
            }
        };

        String id = new GoogleTasksService(transport)
                .createTask("list", "Título", null, LocalDate.now());

        assertEquals("created-1", id);
        assertEquals(2, calls.get());
        assertEquals(1, refreshes.get());
    }

    @Test
    void persistentUnauthorizedResponseStopsAfterOneRefresh() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        GoogleTasksService.ApiTransport transport = new GoogleTasksService.ApiTransport() {
            @Override
            public GoogleTasksService.ApiResponse send(GoogleTasksService.ApiRequest request) {
                calls.incrementAndGet();
                return new GoogleTasksService.ApiResponse(401, "token details");
            }

            @Override
            public void refreshAuthentication() {
                refreshes.incrementAndGet();
            }
        };

        GoogleSyncException error = assertThrows(GoogleSyncException.class,
                () -> new GoogleTasksService(transport).listTaskLists());

        assertEquals(GoogleSyncException.Kind.AUTHENTICATION, error.kind());
        assertEquals(2, calls.get());
        assertEquals(1, refreshes.get());
    }

    @Test
    void idempotentPatchRetriesOnceAfterTemporaryFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        GoogleTasksService service = new GoogleTasksService(request ->
                calls.incrementAndGet() == 1
                        ? new GoogleTasksService.ApiResponse(503, "temporary")
                        : new GoogleTasksService.ApiResponse(200, "{}"));

        service.completeTask("list", "task");

        assertEquals(2, calls.get());
    }

    @Test
    void rateLimitIsClassifiedWithoutImmediateRetry() {
        AtomicInteger calls = new AtomicInteger();
        GoogleTasksService service = new GoogleTasksService(request -> {
            calls.incrementAndGet();
            return new GoogleTasksService.ApiResponse(429, "quota details");
        });

        GoogleSyncException error = assertThrows(GoogleSyncException.class,
                service::listTaskLists);

        assertEquals(GoogleSyncException.Kind.RATE_LIMIT, error.kind());
        assertTrue(error.recoveryAction().contains("Aguarde"));
        assertEquals(1, calls.get());
    }

    @Test
    void timeoutOnReadRetriesOnlyOnceAndKeepsActionRecoverable() {
        AtomicInteger calls = new AtomicInteger();
        GoogleTasksService service = new GoogleTasksService(request -> {
            calls.incrementAndGet();
            throw new HttpTimeoutException("secret request details");
        });

        GoogleSyncException error = assertThrows(GoogleSyncException.class,
                service::listTaskLists);

        assertEquals(GoogleSyncException.Kind.TIMEOUT, error.kind());
        assertTrue(error.retryable());
        assertEquals(2, calls.get());
        assertFalse(error.userMessage().contains("secret"));
    }

    @Test
    void malformedOrCyclicPagesAreRejected() {
        GoogleTasksService malformed = new GoogleTasksService(request ->
                new GoogleTasksService.ApiResponse(200, "{\"items\":["));
        assertEquals(GoogleSyncException.Kind.INVALID_RESPONSE,
                assertThrows(GoogleSyncException.class, malformed::listTaskLists).kind());

        GoogleTasksService invalidItems = new GoogleTasksService(request ->
                new GoogleTasksService.ApiResponse(200, "{\"items\":broken}"));
        assertEquals(GoogleSyncException.Kind.INVALID_RESPONSE,
                assertThrows(GoogleSyncException.class, invalidItems::listTaskLists).kind());

        GoogleTasksService cyclic = new GoogleTasksService(request ->
                new GoogleTasksService.ApiResponse(200,
                        "{\"items\":[],\"nextPageToken\":\"same\"}"));
        assertEquals(GoogleSyncException.Kind.INVALID_RESPONSE,
                assertThrows(GoogleSyncException.class, cyclic::listTaskLists).kind());
    }

    @Test
    void presenterAndLogsNeverUseRawExceptionMessage() {
        Exception error = new java.io.IOException(
                "Bearer access-secret refresh_token=refresh-secret");

        String userMessage = GoogleSyncErrorPresenter.userMessage(error);
        String logMessage = GoogleSyncErrorPresenter.logMessage(error);

        assertFalse(userMessage.contains("access-secret"));
        assertFalse(logMessage.contains("refresh-secret"));
        assertEquals("NETWORK", logMessage);
    }

    @Test
    void tokenFileIsWrittenWithOwnerOnlyPermissions() throws Exception {
        Path tokenFile = tempDir.resolve("nested/google-tokens.json");

        GoogleAuthService.writePrivateFile(tokenFile, "{\"token\":\"secret\"}");

        assertEquals("rw-------", PosixFilePermissions.toString(
                Files.getPosixFilePermissions(tokenFile)));
    }

    @Test
    void oauthStatusIsValidatedAndClassified() {
        GoogleSyncException revoked = assertThrows(GoogleSyncException.class,
                () -> GoogleAuthService.requireOAuthSuccess(400));
        GoogleSyncException unavailable = assertThrows(GoogleSyncException.class,
                () -> GoogleAuthService.requireOAuthSuccess(503));

        assertEquals(GoogleSyncException.Kind.AUTHENTICATION, revoked.kind());
        assertEquals(GoogleSyncException.Kind.SERVER, unavailable.kind());
        assertTrue(unavailable.retryable());
    }
}
