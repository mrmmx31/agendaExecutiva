package com.pessoal.agenda.infra.pairing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.pessoal.agenda.repository.DesktopSyncRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

final class SyncBatchProcessor {
    private static final Set<String> BATCH_FIELDS = Set.of(
            "contract_version", "device_id", "last_server_cursor", "operations");
    private static final Set<String> OPERATION_FIELDS = Set.of(
            "operation_id", "device_id", "sequence", "contract_version", "entity_type",
            "entity_id", "command_type", "occurred_at", "time_zone", "payload",
            "payload_hash", "base_revision");
    private static final Set<String> REQUIRED_OPERATION_FIELDS = Set.of(
            "operation_id", "device_id", "sequence", "contract_version", "entity_type",
            "entity_id", "command_type", "occurred_at", "time_zone", "payload", "payload_hash");
    private static final Set<String> CAPTURE_FIELDS = Set.of("capture_id", "text", "created_at");
    private static final Set<String> RUN_FIELDS = Set.of(
            "run_id", "protocol_id", "protocol_revision", "started_at");
    private static final Set<String> STEP_FIELDS = Set.of("run_id", "step_id", "completed_at");
    private static final Set<String> RUN_CANCEL_FIELDS = Set.of("run_id", "cancelled_at");
    private static final Set<String> PROTOCOL_PROPOSAL_FIELDS = Set.of(
            "protocol_id", "base_revision", "proposed_step_label", "proposed_at");

    private final DesktopSyncRepository repository;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    SyncBatchProcessor(DesktopSyncRepository repository) {
        this.repository = repository;
    }

    JsonObject process(String authenticatedDeviceId, byte[] body) {
        JsonObject batch = gson.fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
        require(batch != null && batch.keySet().equals(BATCH_FIELDS));
        require(batch.get("contract_version").getAsInt() == 1);
        require(authenticatedDeviceId.equals(requiredString(batch, "device_id")));
        long lastServerCursor = batch.get("last_server_cursor").getAsLong();
        require(lastServerCursor >= 0);
        JsonArray operations = batch.getAsJsonArray("operations");
        require(operations != null && operations.size() <= 100);

        repository.refreshSnapshot();
        repository.acknowledgeServerCursor(authenticatedDeviceId, lastServerCursor);
        JsonArray results = new JsonArray();
        for (JsonElement element : operations) {
            results.add(processOperation(authenticatedDeviceId, element.getAsJsonObject()));
        }
        DesktopSyncRepository.CursorRecord cursor = repository.cursor(authenticatedDeviceId);
        JsonObject response = new JsonObject();
        response.addProperty("contract_version", 1);
        response.addProperty("client_contiguous_sequence", cursor.clientContiguousSequence());
        response.addProperty("server_cursor", repository.refreshSnapshot().serverCursor());
        response.add("results", results);
        JsonArray conflicts = new JsonArray();
        for (JsonElement result : results) {
            JsonElement conflictId = result.getAsJsonObject().get("conflict_id");
            if (conflictId == null || conflictId.isJsonNull()) continue;
            DesktopSyncRepository.ConflictRecord conflict =
                    repository.findConflict(conflictId.getAsString());
            if (conflict != null) conflicts.add(conflictJson(conflict));
        }
        response.add("conflicts", conflicts);
        return response;
    }

    private JsonObject processOperation(String deviceId, JsonObject operation) {
        String operationId = safeOperationId(operation);
        try {
            validateEnvelope(deviceId, operation);
            String command = requiredString(operation, "command_type");
            String entityType = requiredString(operation, "entity_type");
            String entityId = requiredUuid(operation, "entity_id");
            String payloadHash = requiredString(operation, "payload_hash");
            JsonObject payload = operation.getAsJsonObject("payload");
            Long baseRevision = nullableLong(operation, "base_revision");

            DesktopSyncRepository.StoredOperation existing = repository.findStoredOperation(operationId);
            if (existing != null) {
                if (same(existing, operation, payloadHash)) {
                    return storedResult(existing);
                }
                return result(operationId, "REJECTED", "ID_REUSED", null, null);
            }

            return switch (command) {
                case "CAPTURE_CREATED" -> processCapture(
                        input(operation, "APPLIED", null, null, null), payload);
                case "PROTOCOL_RUN_STARTED" -> processRunStarted(
                        input(operation, "APPLIED", null, null, null), payload, baseRevision);
                case "PROTOCOL_STEP_COMPLETED" -> processProtocolStep(
                        input(operation, "APPLIED", null, null, null), payload);
                case "PROTOCOL_RUN_CANCELLED" -> processProtocolCancellation(
                        input(operation, "APPLIED", null, null, null), payload);
                case "PROTOCOL_STRUCTURE_PROPOSED" -> processProtocolProposal(
                        input(operation, "APPLIED", null, null, null), payload, baseRevision);
                default -> storeRejected(operation, "BUSINESS_RULE");
            };
        } catch (DesktopSyncRepository.SyncPersistenceException error) {
            return result(operationId, "REJECTED", error.code(), null, null);
        } catch (RuntimeException error) {
            if (operationId == null) throw error;
            try { return storeRejected(operation, "PAYLOAD_INVALID"); }
            catch (RuntimeException ignored) {
                return result(operationId, "REJECTED", "PAYLOAD_INVALID", null, null);
            }
        }
    }

    private JsonObject processCapture(DesktopSyncRepository.OperationInput input, JsonObject payload) {
        if (!repository.hasRole(input.deviceId(), "CAPTURES_WRITE")) {
            return storeRejected(input, "ROLE_DENIED");
        }
        require(payload != null && payload.keySet().equals(CAPTURE_FIELDS));
        require(requiredUuid(payload, "capture_id").equals(input.entityId()));
        String text = requiredString(payload, "text");
        Instant createdAt = Instant.parse(requiredString(payload, "created_at"));
        DesktopSyncRepository.StoredOperation stored = repository.applyCapture(input, text, createdAt);
        return storedResult(stored);
    }

    private JsonObject processRunStarted(DesktopSyncRepository.OperationInput input,
                                         JsonObject payload, Long baseRevision) {
        if (!repository.hasRole(input.deviceId(), "PROTOCOLS_EXECUTE")) {
            return storeRejected(input, "ROLE_DENIED");
        }
        require(payload != null && payload.keySet().equals(RUN_FIELDS));
        requiredUuid(payload, "run_id");
        String protocolId = requiredUuid(payload, "protocol_id");
        long requestedRevision = payload.get("protocol_revision").getAsLong();
        require(requestedRevision >= 1);
        Instant.parse(requiredString(payload, "started_at"));
        long currentRevision = repository.currentRevision("protocol", protocolId);
        if (currentRevision == 0) return storeRejected(input, "BUSINESS_RULE");
        if (requestedRevision != currentRevision
                || (baseRevision != null && baseRevision != currentRevision)) {
            DesktopSyncRepository.StoredOperation conflict = repository.storeConflict(
                    input, baseRevision != null ? baseRevision : requestedRevision,
                    "STRUCTURE_DIVERGED", gson.toJson(payload),
                    repository.currentEntityJson("protocol", protocolId), currentRevision);
            return gson.fromJson(conflict.resultJson(), JsonObject.class);
        }
        DesktopSyncRepository.StoredOperation stored = repository.applyProtocolEvent(
                input, gson.toJson(payload));
        return storedResult(stored);
    }

    private JsonObject processProtocolStep(DesktopSyncRepository.OperationInput input,
                                           JsonObject payload) {
        if (!repository.hasRole(input.deviceId(), "PROTOCOLS_EXECUTE")) {
            return storeRejected(input, "ROLE_DENIED");
        }
        require(payload != null && payload.keySet().equals(STEP_FIELDS));
        requiredUuid(payload, "run_id");
        require(requiredUuid(payload, "step_id").equals(input.entityId()));
        Instant.parse(requiredString(payload, "completed_at"));
        DesktopSyncRepository.StoredOperation stored = repository.applyProtocolEvent(
                input, gson.toJson(payload));
        return storedResult(stored);
    }

    private JsonObject processProtocolCancellation(DesktopSyncRepository.OperationInput input,
                                                     JsonObject payload) {
        if (!repository.hasRole(input.deviceId(), "PROTOCOLS_EXECUTE")) {
            return storeRejected(input, "ROLE_DENIED");
        }
        require(payload != null && payload.keySet().equals(RUN_CANCEL_FIELDS));
        require(requiredUuid(payload, "run_id").equals(input.entityId()));
        Instant.parse(requiredString(payload, "cancelled_at"));
        DesktopSyncRepository.StoredOperation stored = repository.applyProtocolEvent(
                input, gson.toJson(payload));
        return storedResult(stored);
    }

    private JsonObject processProtocolProposal(DesktopSyncRepository.OperationInput input,
                                               JsonObject payload, Long baseRevision) {
        if (!repository.hasRole(input.deviceId(), "PROTOCOLS_EXECUTE")) {
            return storeRejected(input, "ROLE_DENIED");
        }
        require(payload != null && payload.keySet().equals(PROTOCOL_PROPOSAL_FIELDS));
        String protocolId = requiredUuid(payload, "protocol_id");
        require(protocolId.equals(input.entityId()));
        long proposedBase = payload.get("base_revision").getAsLong();
        require(proposedBase >= 1 && baseRevision != null && baseRevision == proposedBase);
        String label = requiredString(payload, "proposed_step_label").trim();
        require(!label.isEmpty() && label.length() <= 120);
        Instant.parse(requiredString(payload, "proposed_at"));
        long currentRevision = repository.currentRevision("protocol", protocolId);
        if (currentRevision == 0) return storeRejected(input, "BUSINESS_RULE");
        DesktopSyncRepository.StoredOperation conflict = repository.storeConflict(
                input, proposedBase, "STRUCTURE_DIVERGED", gson.toJson(payload),
                repository.currentEntityJson("protocol", protocolId), currentRevision);
        return gson.fromJson(conflict.resultJson(), JsonObject.class);
    }

    private JsonObject storeRejected(JsonObject operation, String errorCode) {
        return storeRejected(input(operation, "REJECTED", errorCode, null, null), errorCode);
    }

    private JsonObject storeRejected(DesktopSyncRepository.OperationInput input, String errorCode) {
        DesktopSyncRepository.OperationInput rejected = new DesktopSyncRepository.OperationInput(
                input.operationId(), input.deviceId(), input.sequence(), input.commandType(),
                input.entityType(), input.entityId(), input.payloadHash(), "REJECTED", errorCode,
                null, null, DesktopSyncRepository.resultJson(
                        input.operationId(), "REJECTED", errorCode, null, null), input.occurredAt());
        DesktopSyncRepository.StoredOperation stored = repository.storeTerminal(rejected);
        return storedResult(stored);
    }

    private DesktopSyncRepository.OperationInput input(JsonObject operation, String status,
                                                       String errorCode, Long revision,
                                                       String conflictId) {
        String operationId = requiredUuid(operation, "operation_id");
        String result = DesktopSyncRepository.resultJson(
                operationId, status, errorCode, revision, conflictId);
        return new DesktopSyncRepository.OperationInput(
                operationId, requiredUuid(operation, "device_id"),
                operation.get("sequence").getAsLong(), requiredString(operation, "command_type"),
                requiredString(operation, "entity_type"), requiredUuid(operation, "entity_id"),
                requiredString(operation, "payload_hash"), status, errorCode, revision,
                conflictId, result, requiredString(operation, "occurred_at"));
    }

    private void validateEnvelope(String deviceId, JsonObject operation) {
        require(operation != null && OPERATION_FIELDS.containsAll(operation.keySet())
                && operation.keySet().containsAll(REQUIRED_OPERATION_FIELDS));
        require(operation.get("contract_version").getAsInt() == 1);
        require(deviceId.equals(requiredUuid(operation, "device_id")));
        requiredUuid(operation, "operation_id");
        require(operation.get("sequence").getAsLong() >= 1);
        Instant.parse(requiredString(operation, "occurred_at"));
        ZoneId.of(requiredString(operation, "time_zone"));
        JsonObject payload = operation.getAsJsonObject("payload");
        require(payload != null);
        String hash = sha256(gson.toJson(payload));
        require(MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8),
                requiredString(operation, "payload_hash").getBytes(StandardCharsets.UTF_8)));
        nullableLong(operation, "base_revision");
    }

    private static boolean same(DesktopSyncRepository.StoredOperation stored,
                                JsonObject operation, String hash) {
        return stored.deviceId().equals(requiredString(operation, "device_id"))
                && stored.sequence() == operation.get("sequence").getAsLong()
                && stored.commandType().equals(requiredString(operation, "command_type"))
                && stored.entityType().equals(requiredString(operation, "entity_type"))
                && stored.entityId().equals(requiredString(operation, "entity_id"))
                && stored.payloadHash().equals(hash);
    }

    private static JsonObject result(String operationId, String status, String error,
                                     Long revision, String conflictId) {
        return new GsonBuilder().serializeNulls().create().fromJson(
                DesktopSyncRepository.resultJson(operationId, status, error, revision, conflictId),
                JsonObject.class);
    }

    private static JsonObject storedResult(DesktopSyncRepository.StoredOperation stored) {
        return result(stored.operationId(), stored.status(), stored.errorCode(),
                stored.serverRevision(), stored.conflictId());
    }

    private JsonObject conflictJson(DesktopSyncRepository.ConflictRecord conflict) {
        JsonObject value = new JsonObject();
        value.addProperty("conflict_id", conflict.conflictId());
        value.addProperty("operation_id", conflict.operationId());
        value.addProperty("entity_type", conflict.entityType());
        value.addProperty("entity_id", conflict.entityId());
        if (conflict.baseRevision() == null) value.add("base_revision", JsonNull.INSTANCE);
        else value.addProperty("base_revision", conflict.baseRevision());
        value.addProperty("server_revision", conflict.serverRevision());
        value.addProperty("reason", conflict.reason());
        value.add("local_value", gson.fromJson(conflict.localValueJson(), JsonObject.class));
        value.add("server_value", gson.fromJson(conflict.serverValueJson(), JsonObject.class));
        value.addProperty("created_at", conflict.createdAt());
        return value;
    }

    private static String safeOperationId(JsonObject operation) {
        try { return requiredUuid(operation, "operation_id"); }
        catch (RuntimeException error) { return null; }
    }

    private static String requiredUuid(JsonObject value, String field) {
        String result = requiredString(value, field);
        UUID.fromString(result);
        return result;
    }

    private static String requiredString(JsonObject value, String field) {
        JsonElement element = value == null ? null : value.get(field);
        require(element != null && !element.isJsonNull() && element.isJsonPrimitive());
        String result = element.getAsString();
        require(!result.isBlank());
        return result;
    }

    private static Long nullableLong(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || element instanceof JsonNull || element.isJsonNull()) return null;
        long result = element.getAsLong();
        require(result >= 0);
        return result;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Lote inválido.");
    }
}
