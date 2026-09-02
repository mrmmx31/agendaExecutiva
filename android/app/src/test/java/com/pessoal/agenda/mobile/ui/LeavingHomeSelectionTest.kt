package com.pessoal.agenda.mobile.ui

import com.pessoal.agenda.mobile.data.local.ProtocolTemplateEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LeavingHomeSelectionTest {
    @Test
    fun prioritizesExitProtocolsAndLimitsDecisionSurface() {
        val protocols = listOf(
            protocol("4", "Revisão semanal"),
            protocol("3", "Sair para reunião"),
            protocol("2", "Saída rápida"),
            protocol("1", "Compras"),
        )

        assertEquals(
            listOf("Sair para reunião", "Saída rápida", "Compras"),
            leavingHomeCandidates(protocols).map { it.title },
        )
    }

    private fun protocol(id: String, title: String) = ProtocolTemplateEntity(
        id = "00000000-0000-4000-8000-${id.padStart(12, '0')}",
        title = title,
        revision = 1,
        createdAt = "2026-09-02T12:00:00Z",
        updatedAt = "2026-09-02T12:00:00Z",
    )
}
