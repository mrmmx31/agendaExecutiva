package com.pessoal.agenda.infra.pairing;

import java.time.Instant;

public record PairingSession(String invitation, String oneTimeCode, Instant expiresAt) {}
