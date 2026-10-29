package com.example.asistenciaodoo

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.TimeZone
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class AttendanceReportsTest {
    private val zone = TimeZone.getTimeZone("America/La_Paz")
    private val rows = listOf(
        TeamAttendance(Attendance(1, "2026-10-29 02:00:00", "2026-10-29 03:00:00", 1.0), "José Miguel"),
        TeamAttendance(Attendance(2, "2026-10-29 12:00:00", null, 5.0), "Alexander"),
        TeamAttendance(Attendance(3, "2026-10-30 00:00:00", "2026-10-30 01:30:00", 1.5), "José Miguel")
    )
    @Test fun filtersByLocalEntryDayInclusiveAndName() {
        val result = filterAttendance(rows, AttendanceFilter(employee = "JOSE", from = "2026-10-29", until = "2026-10-29"), zone)
        assertEquals(listOf(3), result.map { it.attendance.id })
        assertEquals("2026-10-28", attendanceDay(rows.first().attendance.checkIn, zone))
    }
    @Test fun filtersOpenAndClosedWithoutIncludingRunningHoursInTotals() {
        assertEquals(listOf(2), filterAttendance(rows, AttendanceFilter(status = AttendanceStatus.OPEN), zone).map { it.attendance.id })
        assertEquals(2, filterAttendance(rows, AttendanceFilter(status = AttendanceStatus.CLOSED), zone).size)
        assertEquals(2.5, AttendanceReport(rows, AttendanceFilter(), zone).totalHours, 0.001)
    }
    @Test fun invalidAndReversedDatesDoNotExportUnfilteredRecords() {
        for (filter in listOf(AttendanceFilter(from = "2026-02-30"), AttendanceFilter(from = "2026-10-30", until = "2026-10-29"), AttendanceFilter(from = "29/10/2026"))) {
            assertNotNull(filter.error())
            assertTrue(filterAttendance(rows, filter, zone).isEmpty())
        }
    }
    @Test fun excelIsValidXmlPackageWithOnlyFilteredRecordsAndNumericHours() {
        val filter = AttendanceFilter(from = "2026-10-29", status = AttendanceStatus.CLOSED)
        val selected = filterAttendance(rows, filter, zone)
        val output = ByteArrayOutputStream()
        writeAttendanceExcel(AttendanceReport(selected, filter, zone), output)
        java.io.File("build/reports/export-test.xlsx").apply { parentFile.mkdirs(); writeBytes(output.toByteArray()) }
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; files[entry.name] = zip.readBytes() }
        }
        assertTrue(files.containsKey("[Content_Types].xml"))
        assertTrue(files.containsKey("xl/workbook.xml"))
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        files.values.forEach { parser.parse(it.inputStream()) }
        val sheet = files.getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8)
        assertTrue(sheet.contains("José Miguel"))
        assertFalse(sheet.contains("Alexander"))
        assertTrue(sheet.contains("<v>1.5</v>"))
        assertTrue(sheet.contains("2026-10-29 20:00"))
    }
    @Test fun excelEscapesNamesAndNeverCreatesFormulaCells() {
        val row = TeamAttendance(Attendance(1, "2026-10-29 12:00:00", null, 0.0), "=HYPERLINK(\"x\") <Ana & José>")
        val out = ByteArrayOutputStream()
        writeAttendanceExcel(AttendanceReport(listOf(row), AttendanceFilter(), zone), out)
        ZipInputStream(out.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    val sheet = zip.readBytes().toString(Charsets.UTF_8)
                    assertFalse(sheet.contains("<f>"))
                    assertTrue(sheet.contains("&lt;Ana &amp; José&gt;"))
                    assertTrue(sheet.contains("t=\"inlineStr\""))
                }
            }
        }
    }
}
