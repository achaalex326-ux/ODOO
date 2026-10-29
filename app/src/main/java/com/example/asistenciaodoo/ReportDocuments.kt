package com.example.asistenciaodoo

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import java.io.OutputStream
import java.util.concurrent.Executors

fun reportPageCount(rows: Int): Int = maxOf(1, (rows + 15) / 16)

fun writeAttendancePdf(report: AttendanceReport, output: OutputStream,
    selected: (Int) -> Boolean = { true }, canceled: () -> Boolean = { false }) {
    val document = PdfDocument()
    try {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val pages = reportPageCount(report.rows.size)
        for (index in 0 until pages) {
            if (canceled()) throw java.io.InterruptedIOException("Impresión cancelada")
            if (!selected(index)) continue
            val page = document.startPage(PdfDocument.PageInfo.Builder(842, 595, index + 1).create())
            val canvas = page.canvas
            fun text(value: String, x: Float, y: Float, size: Float = 10f, bold: Boolean = false) {
                paint.color = Color.rgb(16, 45, 70); paint.textSize = size
                paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                canvas.drawText(value, x, y, paint)
            }
            fun fit(value: String, width: Float): String {
                paint.textSize = 10f
                return if (paint.measureText(value) <= width) value
                else value.take(paint.breakText(value, true, width - 12, null)) + "…"
            }
            text("PULSO · Informe de asistencias", 32f, 40f, 20f, true)
            text("Empleado: ${fit(report.filter.employee.ifBlank { "Todos" }, 480f)}", 32f, 66f)
            text("Desde: ${report.filter.from.ifBlank { "Sin límite" }} · Hasta: ${report.filter.until.ifBlank { "Sin límite" }} · Estado: ${report.filter.status.label}", 32f, 84f)
            text("Generado: ${report.generatedLabel} · Zona horaria: ${report.zone.id}", 32f, 102f)
            text("${report.rows.size} registros · Total jornadas cerradas: ${durationLabel(report.totalHours)}", 32f, 124f, bold = true)
            val columns = listOf(32f, 230f, 410f, 590f, 700f)
            listOf("Empleado", "Entrada", "Salida", "Horas", "Estado").forEachIndexed { i, label -> text(label, columns[i], 154f, bold = true) }
            paint.color = Color.LTGRAY; canvas.drawLine(32f, 163f, 810f, 163f, paint)
            report.rows.drop(index * 16).take(16).forEachIndexed { rowIndex, row ->
                val attendance = row.attendance
                val y = 185f + rowIndex * 22f
                val values = listOf(fit(row.employeeName, 185f), reportDate(attendance.checkIn, report.zone),
                    attendance.checkOut?.let { reportDate(it, report.zone) } ?: "Pendiente",
                    if (attendance.checkOut == null) "—" else durationLabel(attendance.hours),
                    if (attendance.checkOut == null) "Abierta" else "Cerrada")
                values.forEachIndexed { col, value -> text(value, columns[col], y) }
            }
            if (report.rows.isEmpty()) text("No hay registros para los filtros seleccionados.", 32f, 185f)
            text("Filtro por fecha local de entrada · Página ${index + 1} de $pages", 32f, 566f, 9f)
            document.finishPage(page)
        }
        document.writeTo(output)
    } finally { document.close() }
}

class AttendancePrintAdapter(private val report: AttendanceReport) : PrintDocumentAdapter() {
    private val worker = Executors.newSingleThreadExecutor()
    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
        signal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
        if (signal.isCanceled) callback.onLayoutCancelled()
        else callback.onLayoutFinished(PrintDocumentInfo.Builder("Pulso-asistencias.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(reportPageCount(report.rows.size)).build(), true)
    }
    override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor,
        signal: CancellationSignal, callback: WriteResultCallback) {
        worker.execute {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                    writeAttendancePdf(report, output, { page -> pages.any { page in it.start..it.end } }, { signal.isCanceled })
                }
                if (signal.isCanceled) callback.onWriteCancelled() else callback.onWriteFinished(pages)
            } catch (_: Exception) {
                if (signal.isCanceled) callback.onWriteCancelled() else callback.onWriteFailed("No se pudo crear el informe para imprimir.")
            }
        }
    }
    override fun onFinish() { worker.shutdown() }
}

fun printAttendance(context: Context, report: AttendanceReport) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    manager.print("Pulso · Asistencias", AttendancePrintAdapter(report), PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape()).setColorMode(PrintAttributes.COLOR_MODE_COLOR).build())
}
