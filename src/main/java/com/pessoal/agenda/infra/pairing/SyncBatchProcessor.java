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
    private static final Set<String> TASK_FIELDS = Set.of(
            "task_id", "title", "notes", "due_date", "priority", "status", "updated_at");
    private static final Set<String> TASK_STATUS_FIELDS = Set.of("task_id", "status", "updated_at");
    private static final Set<String> TASK_DELETE_FIELDS = Set.of("task_id", "deleted_at");
    private static final Set<String> TASK_CHECKLIST_FIELDS = Set.of("task_id", "items", "updated_at");
    private static final Set<String> CHECKLIST_ITEM_FIELDS = Set.of("id", "text", "done", "position");
    private static final Set<String> TASK_SESSION_FIELDS = Set.of(
            "session_id", "task_id", "started_at", "ended_at", "duration_seconds", "notes");

    private final DesktopSyncRepository repository;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    SyncBatchProcessor(DesktopSyncRepository repository) {
        this.repository = repository;
    }

    JsonObject process(String authenticatedDeviceId, byte[] body) {
        JsonObject batch = gson.fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
        require(batch != null && batch.keySet().equals(BATCH_FIELDS));
        int contractVersion = batch.get("contract_version").getAsInt();
        require(contractVersion == 1 || contractVersion == 2);
        DesktopSyncRepository.DeviceRecord device = repository.findDevice(authenticatedDeviceId);
        require(device != null && contractVersion >= device.contractMin()
                && contractVersion <= device.contractMax());
        require(authenticatedDeviceId.equals(requiredString(batch, "device_id")));
        long lastServerCursor = batch.get("last_server_cursor").getAsLong();
        require(lastServerCursor >= 0);
        JsonArray operations = batch.getAsJsonArray("operations");
        require(operations != null && operations.size() <= 100);

        repository.refreshSnapshot();
        repository.acknowledgeServerCursor(authenticatedDeviceId, lastServerCursor);
        JsonArray results = new JsonArray();
        for (JsonElement element : operations) {
            results.add(processOperation(authenticatedDeviceId, element.getAsJsonObject(), contractVersion));
        }
        DesktopSyncRepository.CursorRecord cursor = repository.cursor(authenticatedDeviceId);
        JsonObject response = new JsonObject();
        response.addProperty("contract_version", contractVersion);
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

    private JsonObject processOperation(String deviceId, JsonObject operation, int contractVersion) {
        String operationId = safeOperationId(operation);
        try {
            validateEnvelope(deviceId, operation, contractVersion);
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

            if (contractVersion == 1 && command.startsWith("TASK_")
                    && !"TASKS_READ".equals(command)) return storeRejected(operation, "CONTRACT_VERSION");
            if (contractVersion == 1 && Set.of("CHECKLIST_ITEM_CHANGED", "SESSION_RECORDED").contains(command))
                return storeRejected(operation, "CONTRACT_VERSION");
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
                case "TASK_CREATED" -> processTaskCreate(operation, payload, baseRevision);
                case "TASK_UPDATED" -> processTaskUpdate(operation, payload, baseRevision);
                case "TASK_STATUS_CHANGED" -> processTaskStatus(operation, payload, baseRevision);
                case "CHECKLIST_ITEM_CHANGED" -> processTaskChecklist(operation, payload, baseRevision);
                case "TASK_DELETED" -> processTaskDelete(operation, payload, baseRevision);
                case "SESSION_RECORDED" -> processTaskSession(operation, payload);
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

    private JsonObject processTaskCreate(JsonObject operation, JsonObject payload, Long baseRevision) {
        DesktopSyncRepository.OperationInput basic = input(operation, "APPLIED", null, 1L, null);
        if (!repository.hasRole(basic.deviceId(), "TASKS_WRITE")) return storeRejected(basic, "ROLE_DENIED");
        require(baseRevision == null && repository.currentRevision("task", basic.entityId()) == 0);
        TaskValues values = taskValues(payload, basic.entityId());
        DesktopSyncRepository.StoredOperation stored = repository.applyTaskCreate(
                basic, values.title, values.notes, values.dueDate, values.priority, values.status);
        repository.refreshSnapshot();
        return storedResult(stored);
    }

    private JsonObject processTaskUpdate(JsonObject operation, JsonObject payload, Long baseRevision) {
        DesktopSyncRepository.OperationInput basic = input(operation, "APPLIED", null, null, null);
        if (!repository.hasRole(basic.deviceId(), "TASKS_WRITE")) return storeRejected(basic, "ROLE_DENIED");
        TaskValues values = taskValues(payload, basic.entityId());
        long current = checkedTaskRevision(basic, payload, baseRevision, "TEXT_DIVERGED");
        if (current < 0) return gson.fromJson(repository.findStoredOperation(basic.operationId()).resultJson(), JsonObject.class);
        DesktopSyncRepository.OperationInput applied = input(operation, "APPLIED", null, current + 1, null);
        DesktopSyncRepository.StoredOperation stored = repository.applyTaskUpdate(
                applied, values.title, values.notes, values.dueDate, values.priority);
        repository.refreshSnapshot();
        return storedResult(stored);
    }

    private JsonObject processTaskStatus(JsonObject operation, JsonObject payload, Long baseRevision) {
        DesktopSyncRepository.OperationInput basic = input(operation, "APPLIED", null, null, null);
        if (!repository.hasRole(basic.deviceId(), "TASKS_WRITE")) return storeRejected(basic, "ROLE_DENIED");
        require(payload != null && payload.keySet().equals(TASK_STATUS_FIELDS));
        require(requiredUuid(payload, "task_id").equals(basic.entityId()));
        String status = taskStatus(payload.get("status").getAsString());
        Instant.parse(requiredString(payload, "updated_at"));
        long current = checkedTaskRevision(basic, payload, baseRevision, "STATE_DIVERGED");
        if (current < 0) return gson.fromJson(repository.findStoredOperation(basic.operationId()).resultJson(), JsonObject.class);
        DesktopSyncRepository.StoredOperation stored = repository.applyTaskStatus(
                input(operation, "APPLIED", null, current + 1, null), status);
        repository.refreshSnapshot();
        return storedResult(stored);
    }

    private JsonObject processTaskChecklist(JsonObject operation, JsonObject payload, Long baseRevision) {
        DesktopSyncRepository.OperationInput basic = input(operation, "APPLIED", null, null, null);
        if (!repository.hasRole(basic.deviceId(), "TASKS_WRITE")) return storeRejected(basic, "ROLE_DENIED");
        require(payload != null && payload.keySet().equals(TASK_CHECKLIST_FIELDS));
        require(requiredUuid(payload, "task_id").equals(basic.entityId()));
        Instant.parse(requiredString(payload, "updated_at"));
        JsonArray items = payload.getAsJsonArray("items");
        require(items != null && items.size() <= 200);
        Set<String> ids = new java.util.HashSet<>();
        Set<Integer> positions = new java.util.HashSet<>();
        for (JsonElement element : items) {
            JsonObject item = element.getAsJsonObject();
            require(item.keySet().equals(CHECKLIST_ITEM_FIELDS));
            require(ids.add(requiredUuid(item, "id")));
            String text = requiredString(item, "text").trim();
            require(!text.isEmpty() && text.length() <= 240);
            require(item.get("done").isJsonPrimitive());
            int position = item.get("position").getAsInt();
            require(position >= 0 && positions.add(position));
        }
        long current = checkedTaskRevision(basic, payload, baseRevision, "STRUCTURE_DIVERGED");
        if (current < 0) return gson.fromJson(repository.findStoredOperation(basic.operationId()).resultJson(), JsonObject.class);
        DesktopSyncRepository.StoredOperation stored = repository.applyTaskChecklist(
                input(operation, "APPLIED", null, current + 1, null), items);
        repository.refreshSnapshot();
        return storedResult(stored);
    }

    private JsonObject processTaskDelete(JsonObject operation, JsonObject payload, Long baseRevision) {
        DesktopSyncRepository.OperationInput basic = input(operation, "APPLIED", null, null, null);
        if (!repository.hasRole(basic.deviceId(), "TASKS_WRITE")) return storeRejected(basic, "ROLE_DENIED");
        require(payload != null && payload.keySet().equals(TASK_DELETE_FIELDS));
        require(requiredUuid(payload, "task_id").equals(basic.entityId()));
        Instant.parse(requiredString(payload, "deleted_at"));
        long current = checkedTaskRevision(basic, payload, baseRevision, "TOMBSTONE_DIVERGED");
        if (current < 0) return gson.fromJson(repository.findStoredOperation(basic.operationId()).resultJson(), JsonObject.class);
        DesktopSyncRepository.StoredOperation stored = repository.applyTaskDelete(
                input(operation, "APPLIED", null, current + 1, null));
        repository.refreshSnapshot();
        return storedResult(stored);
    }

    private JsonObject processTaskSession(JsonObject operation, JsonObject payload) {
        DesktopSyncRepository.OperationInput basic = input(operation, "APPLIED", null, null, null);
        if (!repository.hasRole(basic.deviceId(), "TASKS_WRITE")) return storeRejected(basic, "ROLE_DENIED");
        require(payload != null && payload.keySet().equals(TASK_SESSION_FIELDS));
        require(requiredUuid(payload, "session_id").equals(basic.entityId()));
        String taskId = requiredUuid(payload, "task_id");
        String startedAt = requiredString(payload, "started_at");
        String endedAt = requiredString(payload, "ended_at");
        require(!Instant.parse(endedAt).isBefore(Instant.parse(startedAt)));
        long seconds = payload.get("duration_seconds").getAsLong();
        require(seconds >= 1 && seconds <= 86400);
        String notes = payload.get("notes").getAsString();
        require(notes.length() <= 1000);
        return storedResult(repository.applyTaskSession(basic, taskId, startedAt, endedAt, seconds, notes));
    }

    private long checkedTaskRevision(DesktopSyncRepository.OperationInput input, JsonObject payload,
                                     Long baseRevision, String reason) {
        long current = repository.currentRevision("task", input.entityId());
        if (current == 0) throw new IllegalArgumentException();
        if (baseRevision != null && baseRevision == current) return current;
        repository.storeConflict(input, baseRevision, reason, gson.toJson(payload),
                repository.currentEntityJson("task", input.entityId()), current);
        return -1;
    }

    private TaskValues taskValues(JsonObject payload, String entityId) {
        require(payload != null && payload.keySet().equals(TASK_FIELDS));
        require(requiredUuid(payload, "task_id").equals(entityId));
        String title = requiredString(payload, "title").trim();
        String notes = payload.get("notes").getAsString().trim();
        require(!title.isEmpty() && title.length() <= 240 && notes.length() <= 4000);
        String dueDate = payload.get("due_date").isJsonNull()
                ? java.time.LocalDate.now().toString() : java.time.LocalDate.parse(payload.get("due_date").getAsString()).toString();
        String priority = payload.get("priority").getAsString();
        require(Set.of("LOW", "NORMAL", "HIGH").contains(priority));
        String status = taskStatus(payload.get("status").getAsString());
        Instant.parse(requiredString(payload, "updated_at"));
        return new TaskValues(title, notes, dueDate, priority, status);
    }

    private static String taskStatus(String status) {
        require(Set.of("PENDING", "IN_PROGRESS", "COMPLETED", "BLOCKED", "CANCELLED").contains(status));
        return status;
    }

    private record TaskValues(String title, String notes, String dueDate, String priority, String status) {}

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

    private void validateEnvelope(String deviceId, JsonObject operation, int contractVersion) {
        require(operation != null && OPERATION_FIELDS.containsAll(operation.keySet())
                && operation.keySet().containsAll(REQUIRED_OPERATION_FIELDS));
        require(operation.get("contract_version").getAsInt() == contractVersion);
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
