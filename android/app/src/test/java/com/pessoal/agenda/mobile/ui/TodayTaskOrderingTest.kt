package com.pessoal.agenda.mobile.ui

import com.pessoal.agenda.mobile.data.local.TaskReplicaEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayTaskOrderingTest {
    @Test
    fun keepsOpenTasksBeforeCompletedWithoutChangingRelativeOrder() {
        val tasks = listOf(
            task("completed-1", "COMPLETED"),
            task("pending-1", "PENDING"),
            task("completed-2", "COMPLETED"),
            task("progress-1", "IN_PROGRESS"),
        )

        assertEquals(
            listOf("pending-1", "progress-1", "completed-1", "completed-2"),
            orderTasksForToday(tasks).map(TaskReplicaEntity::id),
        )
    }

    private fun task(id: String, status: String) = TaskReplicaEntity(
        id = id,
        title = id,
        status = status,
        revision = 1,
        updatedAt = "2026-09-05T00:00:00Z",
    )
}
