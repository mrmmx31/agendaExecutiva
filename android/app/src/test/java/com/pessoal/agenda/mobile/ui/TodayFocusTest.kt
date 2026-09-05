package com.pessoal.agenda.mobile.ui

import com.pessoal.agenda.mobile.data.local.DailyPlanTaskRow
import com.pessoal.agenda.mobile.data.local.FocusSelectionEntity
import com.pessoal.agenda.mobile.data.local.TaskReplicaEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayFocusTest {
    private val tasks = listOf(
        task("b", "Apoio", "PENDING"),
        task("a", "Essencial", "PENDING"),
        task("c", "Em curso", "IN_PROGRESS"),
    )

    @Test
    fun manualFocusWinsThenPlanThenDeterministicAutomatic() {
        val plan = listOf(DailyPlanTaskRow("2026-09-05", "a", "ESSENTIAL", 0, "Essencial", "PENDING"))

        assertEquals("b" to "MANUAL", resolveFocus(
            tasks, plan, FocusSelectionEntity(taskId = "b", selectedAt = "2026-09-05T12:00:00Z"),
        ).let { it.first?.id to it.second })
        assertEquals("a" to "PLAN", resolveFocus(tasks, plan, null).let { it.first?.id to it.second })
        assertEquals("c" to "AUTOMATIC", resolveFocus(tasks, emptyList(), null).let { it.first?.id to it.second })
    }

    @Test
    fun invalidManualSelectionFallsBackWithoutHidingOpenWork() {
        val result = resolveFocus(
            tasks,
            emptyList(),
            FocusSelectionEntity(taskId = "missing", selectedAt = "2026-09-05T12:00:00Z"),
        )
        assertEquals("c", result.first?.id)
        assertEquals("AUTOMATIC", result.second)
    }

    private fun task(id: String, title: String, status: String) = TaskReplicaEntity(
        id = id,
        title = title,
        status = status,
        revision = 1,
        updatedAt = "2026-09-05T12:00:00Z",
    )
}
