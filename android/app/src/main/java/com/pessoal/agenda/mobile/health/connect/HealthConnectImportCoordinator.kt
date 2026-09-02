package com.pessoal.agenda.mobile.health.connect

import com.pessoal.agenda.mobile.health.HealthCategory
import com.pessoal.agenda.mobile.health.HealthStore
import com.pessoal.agenda.mobile.health.HealthSummary
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class HealthConnectImportCoordinator(
    private val gateway: HealthConnectGateway,
    private val store: HealthStore,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun importEnabled(): Int {
        check(gateway.status() == HealthConnectStatus.AVAILABLE) { "Health Connect indisponível." }
        val consents = store.consents().filter {
            it.enabled && HealthCategory.valueOf(it.category) in AndroidHealthConnectGateway.IMPORTABLE
        }
        if (consents.isEmpty()) throw IllegalArgumentException("Ative ao menos uma categoria de saúde.")
        val categories = consents.mapTo(linkedSetOf()) { HealthCategory.valueOf(it.category) }
        check(gateway.grantedPermissions().containsAll(gateway.permissionsFor(categories))) {
            "Permissões Health Connect não concedidas."
        }
        val end = Instant.now(clock)
        val start = end.minus(Duration.ofDays(7))
        val byCategory = consents.associateBy { HealthCategory.valueOf(it.category) }
        gateway.readSummaries(categories, start, end).forEach { imported ->
            val consent = requireNotNull(byCategory[imported.category])
            store.saveHealthSummary(
                HealthSummary(
                    id = newId(), consentId = consent.id, category = imported.category,
                    periodStart = imported.periodStart.toString(), periodEnd = imported.periodEnd.toString(),
                    coverageStart = imported.coverageStart?.toString(), coverageEnd = imported.coverageEnd?.toString(),
                    sampleCount = imported.sampleCount, metrics = imported.metrics,
                    sourcePackages = imported.sourcePackages, missingReason = imported.missingReason,
                    importedAt = end.toString(),
                ),
            )
        }
        return categories.size
    }
}
