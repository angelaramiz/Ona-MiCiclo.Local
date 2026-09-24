package com.ona.miciclo.settings.domain.report

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Render del informe médico a PDF (A4) con `android.graphics.pdf` (sin dependencias).
 *
 * El archivo queda en `cacheDir/reports/` y se comparte vía FileProvider.
 */
object MedicalReportPdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48
    private const val LINE = 20f

    private val dateFmt = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es"))

    fun write(context: Context, report: MedicalReport): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "ona-informe-${report.generatedOn}.pdf")

        val doc = PdfDocument()
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var y = MARGIN.toFloat()

        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 20f
        }
        val headPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
        }
        val bodyPaint = Paint().apply { textSize = 12f }
        val smallPaint = Paint().apply {
            textSize = 10f
            color = android.graphics.Color.DKGRAY
        }

        fun newPageIfNeeded(lines: Int = 1) {
            if (y + lines * LINE > PAGE_H - MARGIN) {
                doc.finishPage(page)
                page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, doc.pages.size + 1).create()
                )
                y = MARGIN.toFloat()
            }
        }
        fun line(text: String, paint: Paint = bodyPaint, gap: Float = LINE) {
            newPageIfNeeded()
            page.canvas.drawText(text, MARGIN.toFloat(), y, paint)
            y += gap
        }

        line("Informe de ciclo menstrual — Ona", titlePaint, 28f)
        line("Generado el ${report.generatedOn.format(dateFmt)}", smallPaint, 26f)

        line("Resumen", headPaint, 22f)
        if (report.totalCycles == 0) {
            line("Aún no hay periodos registrados.")
        } else {
            line("Ciclos registrados: ${report.totalCycles}")
            line("Duración promedio: ${report.avgCycleLength} días")
            line("Sangrado promedio: ${report.avgBleeding} días")
            report.lastPeriodStart?.let { line("Último inicio: ${it.format(dateFmt)}") }
            report.nextPeriodEstimate?.let { line("Próximo estimado: ${it.format(dateFmt)}") }
            line("Registros diarios: ${report.totalLogs}")
        }

        y += 8f
        line("Últimos periodos", headPaint, 22f)
        if (report.recentPeriods.isEmpty()) {
            line("Sin datos.")
        } else {
            report.recentPeriods.forEach {
                val conf = if (it.confirmed) "confirmado" else "estimado"
                line(
                    "• ${it.start.format(dateFmt)} — ciclo ${it.cycleLength}d, " +
                        "sangrado ${it.bleedingDays}d ($conf)"
                )
            }
        }

        y += 8f
        line("Síntomas más frecuentes", headPaint, 22f)
        if (report.topSymptoms.isEmpty()) {
            line("Sin síntomas registrados.")
        } else {
            report.topSymptoms.forEach {
                line("• ${it.label}: ${it.count} veces")
            }
        }

        y += 12f
        newPageIfNeeded(2)
        line(
            "Nota: este informe no es un diagnóstico médico. Generado en el " +
                "dispositivo, sin notas personales.",
            smallPaint
        )

        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
