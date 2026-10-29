package com.example.asistenciaodoo

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class ReportDocumentsTest {
    private fun report(size: Int) = AttendanceReport((1..size).map {
        TeamAttendance(Attendance(it, "2026-10-29 12:00:00", "2026-10-29 13:00:00", 1.0), "José Miguel $it")
    }, AttendanceFilter(), TimeZone.getTimeZone("America/La_Paz"))

    private fun assertPages(size: Int, expected: Int, selected: (Int) -> Boolean = { true }) {
        val output = ByteArrayOutputStream()
        writeAttendancePdf(report(size), output, selected)
        assertTrue(output.toByteArray().take(4).toByteArray().toString(Charsets.US_ASCII).startsWith("%PDF"))
        val file = File.createTempFile("pulso-report-", ".pdf", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        try {
            file.writeBytes(output.toByteArray())
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use {
                assertEquals(expected, it.pageCount)
                it.openPage(0).use { page -> assertEquals(842, page.width); assertEquals(595, page.height) }
            }
        } finally { file.delete() }
    }
    @Test fun createsReadableMultipagePdf() = assertPages(33, 3)
    @Test fun printSelectionIncludesOnlyRequestedPage() = assertPages(33, 1) { it == 1 }
    @Test fun emptyReportStillProducesValidPdf() = assertPages(0, 1)
}
