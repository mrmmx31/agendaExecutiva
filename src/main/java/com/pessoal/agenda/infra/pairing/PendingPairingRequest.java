package com.pessoal.agenda.infra.pairing;

import java.time.Instant;
import java.util.Set;

public record PendingPairingRequest(String requestId, String deviceId, String deviceName,
                                    Set<String> requestedRoles, Instant receivedAt) {}
