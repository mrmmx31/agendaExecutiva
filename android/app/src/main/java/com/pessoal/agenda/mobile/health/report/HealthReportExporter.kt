package com.pessoal.agenda.mobile.health.report

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class HealthReportFormat(val extension: String, val mimeType: String) {
    JSON("json", "application/json"), CSV("csv", "text/csv"), PDF("pdf", "application/pdf")
}

object HealthReportExporter {
    private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = true }

    fun export(snapshot: HealthReportSnapshot, format: HealthReportFormat): ByteArray = when (format) {
        HealthReportFormat.JSON -> json.encodeToString(snapshot).encodeToByteArray()
        HealthReportFormat.CSV -> csv(snapshot).encodeToByteArray()
        HealthReportFormat.PDF -> pdf(snapshot)
    }

    private fun csv(snapshot: HealthReportSnapshot): String = buildString {
        appendLine("metadata,value")
        listOf(
            "contract_version" to snapshot.contractVersion.toString(),
            "snapshot_id" to snapshot.snapshotId,
            "generated_at" to snapshot.generatedAt,
            "period_start" to snapshot.periodStart,
            "period_end" to snapshot.periodEnd,
            "time_zone" to snapshot.timeZone,
            "subject_label" to snapshot.subjectLabel,
            "selected_categories" to snapshot.selectedCategories.joinToString(";"),
            "permissions" to snapshot.permissions.joinToString("; ") { "${it.category}:${it.enabled}:foreground=${it.foregroundOnly}:retention=${it.retentionDays}" },
            "sources" to snapshot.sources.joinToString(";"),
            "limitations" to snapshot.limitations.joinToString("; "),
            "excluded_entry_count" to snapshot.excludedEntryCount.toString(),
        ).forEach { (key, value) -> appendLine(listOf(key, value).joinToString(",", transform = ::csvCell)) }
        appendLine()
        appendLine("snapshot_id,subject,kind,category,start_at,end_at,title,details,sources")
        snapshot.entries.forEach { entry ->
            appendLine(listOf(
                snapshot.snapshotId, snapshot.subjectLabel, entry.kind.name, entry.category,
                entry.startAt, entry.endAt.orEmpty(), entry.title,
                entry.details.entries.joinToString("; ") { "${it.key}=${it.value}" },
                entry.sources.joinToString(";"),
            ).joinToString(",", transform = ::csvCell))
        }
    }

    private fun csvCell(raw: String): String {
        val protected = if (raw.firstOrNull() in setOf('=', '+', '-', '@')) "'$raw" else raw
        return "\"${protected.replace("\"", "\"\"")}\""
    }

    private fun pdf(snapshot: HealthReportSnapshot): ByteArray {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = android.graphics.Color.BLACK }
        val titlePaint = Paint(paint).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let(document::finishPage)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            y = 42f
        }
        fun line(text: String, selectedPaint: Paint = paint) {
            if (page == null || y > 805f) newPage()
            wrap(text, 92).forEach { part ->
                if (y > 805f) newPage()
                page!!.canvas.drawText(part, 36f, y, selectedPaint)
                y += if (selectedPaint === titlePaint) 25f else 15f
            }
        }
        newPage()
        line("Relatório revisável de saúde", titlePaint)
        line("Identificação: ${snapshot.subjectLabel.ifBlank { "não informada" }}")
        line("Período: ${snapshot.periodStart} a ${snapshot.periodEnd} (${snapshot.timeZone})")
        line("Snapshot: ${snapshot.snapshotId} • schema ${snapshot.contractVersion}")
        line("Categorias: ${snapshot.selectedCategories.joinToString()}")
        line("Permissões: ${snapshot.permissions.joinToString { "${it.category}=${if (it.enabled) "ativa" else "revogada"}" }}")
        line("Fontes: ${snapshot.sources.joinToString().ifBlank { "nenhuma" }}")
        line("Linhas retiradas na revisão: ${snapshot.excludedEntryCount}")
        y += 8f
        snapshot.entries.forEach { entry ->
            line("${entry.kind.name} | ${entry.category} | ${entry.startAt}", Paint(paint).apply { typeface = Typeface.DEFAULT_BOLD })
            line(entry.title)
            if (entry.details.isNotEmpty()) line(entry.details.entries.joinToString("; ") { "${it.key}: ${it.value}" })
            if (entry.sources.isNotEmpty()) line("Origem: ${entry.sources.joinToString()}")
            y += 5f
        }
        y += 8f
        line("Limitações", Paint(paint).apply { typeface = Typeface.DEFAULT_BOLD })
        snapshot.limitations.forEach { line("• $it") }
        page?.let(document::finishPage)
        return ByteArrayOutputStream().use { output ->
            document.writeTo(output)
            document.close()
            output.toByteArray()
        }
    }

    private fun wrap(value: String, width: Int): List<String> {
        if (value.isBlank()) return listOf("")
        val lines = mutableListOf<String>()
        var current = ""
        value.split(Regex("\\s+")).flatMap { word -> word.chunked(width) }.forEach { word ->
            if (current.isNotEmpty() && current.length + word.length + 1 > width) {
                lines += current
                current = word
            } else current = if (current.isEmpty()) word else "$current $word"
        }
        lines += current
        return lines
    }
}
