package com.example.asistenciaodoo

import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class AttendanceStatus(val label: String) { ALL("Todas"), OPEN("Abiertas"), CLOSED("Cerradas") }

data class AttendanceFilter(val employee: String = "", val from: String = "", val until: String = "", val status: AttendanceStatus = AttendanceStatus.ALL) {
    fun error(): String? {
        for (date in listOf(from, until)) if (date.isNotBlank() && !validDay(date)) return "Usa fechas válidas con formato AAAA-MM-DD."
        if (from.isNotBlank() && until.isNotBlank() && from > until) return "La fecha Desde debe ser anterior o igual a Hasta."
        return null
    }
    fun description(): String = "Empleado: ${employee.ifBlank { "Todos" }} · Desde: ${from.ifBlank { "Sin límite" }} · Hasta: ${until.ifBlank { "Sin límite" }} · Estado: ${status.label}"
}

private fun validDay(value: String): Boolean = runCatching {
    if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) return@runCatching false
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    format.format(format.parse(value)!!) == value
}.getOrDefault(false)

fun attendanceDay(utc: String, zone: TimeZone): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.format(
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }.parse(utc)!!)

fun filterAttendance(rows: List<TeamAttendance>, filter: AttendanceFilter, zone: TimeZone): List<TeamAttendance> {
    if (filter.error() != null) return emptyList()
    return rows.filter {
        val date = attendanceDay(it.attendance.checkIn, zone)
        searchName(it.employeeName).contains(searchName(filter.employee.trim())) &&
            (filter.from.isBlank() || date >= filter.from) && (filter.until.isBlank() || date <= filter.until) &&
            when (filter.status) {
                AttendanceStatus.ALL -> true
                AttendanceStatus.OPEN -> it.attendance.checkOut == null
                AttendanceStatus.CLOSED -> it.attendance.checkOut != null
            }
    }
}

data class AttendanceReport(val rows: List<TeamAttendance>, val filter: AttendanceFilter,
    val zone: TimeZone, val generated: Date = Date()) {
    val totalHours: Double get() = rows.filter { it.attendance.checkOut != null }.sumOf { it.attendance.hours }
    val generatedLabel: String get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es")).apply { timeZone = zone }.format(generated)
}

/** Real XLSX: inline text cells prevent spreadsheet formula injection from names. */
fun writeAttendanceExcel(report: AttendanceReport, output: OutputStream) {
    ZipOutputStream(output).use { zip ->
        fun entry(name: String, xml: String) {
            zip.putNextEntry(ZipEntry(name)); zip.write(xml.toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }
        entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
        entry("_rels/.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
        entry("xl/workbook.xml", """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Asistencias" sheetId="1" r:id="rId1"/></sheets></workbook>""")
        entry("xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
        val sheet = buildString {
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="5" topLeftCell="A6" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols><col min="1" max="1" width="30" customWidth="1"/><col min="2" max="3" width="25" customWidth="1"/><col min="4" max="6" width="22" customWidth="1"/></cols><sheetData>""")
            fun textRow(number: Int, values: List<String>) {
                append("<row r=\"$number\">")
                values.forEachIndexed { i, text -> append("<c r=\"${'A' + i}$number\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xmlText(text)}</t></is></c>") }
                append("</row>")
            }
            textRow(1, listOf("PULSO · Informe de asistencias"))
            textRow(2, listOf(report.filter.description()))
            textRow(3, listOf("Generado: ${report.generatedLabel}", "Zona: ${report.zone.id}"))
            textRow(4, listOf("${report.rows.size} registros", "Horas de jornadas cerradas", report.totalHours.toString()))
            textRow(5, listOf("Empleado", "Entrada", "Salida", "Horas decimales", "Estado", "Zona horaria"))
            report.rows.forEachIndexed { index, row ->
                val n = index + 6
                val attendance = row.attendance
                append("<row r=\"$n\">")
                val values = listOf(row.employeeName, reportDate(attendance.checkIn, report.zone),
                    attendance.checkOut?.let { reportDate(it, report.zone) } ?: "Pendiente", "",
                    if (attendance.checkOut == null) "Abierta" else "Cerrada", report.zone.id)
                values.forEachIndexed { col, value ->
                    val reference = "${'A' + col}$n"
                    if (col == 3 && attendance.checkOut != null) append("<c r=\"$reference\"><v>${attendance.hours}</v></c>")
                    else append("<c r=\"$reference\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xmlText(value)}</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData><autoFilter ref=\"A5:F${report.rows.size + 5}\"/></worksheet>")
        }
        entry("xl/worksheets/sheet1.xml", sheet)
    }
}

private fun xmlText(value: String): String = value.filter { it == '\n' || it == '\r' || it == '\t' || it.code >= 32 }
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

fun reportDate(utc: String, zone: TimeZone): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = zone }.format(
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }.parse(utc)!!)

private fun searchName(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
