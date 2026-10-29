package com.example.asistenciaodoo

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun AdminReportControls(filter: AttendanceFilter, change: (AttendanceFilter) -> Unit,
    rows: List<TeamAttendance>, enabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<AttendanceReport?>(null) }
    var writing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    fun snapshot() = AttendanceReport(rows.toList(), filter, TimeZone.getDefault())
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val report = pending; pending = null
        if (uri != null && report != null) scope.launch {
            writing = true
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { writeAttendancePdf(report, it) }
                        ?: error("No se pudo abrir el archivo")
                }
                message = "PDF guardado: ${report.rows.size} registros."
            } catch (_: Exception) { message = "No se pudo guardar el PDF. Revisa la carpeta seleccionada y vuelve a intentar." }
            finally { writing = false }
        }
    }
    val excel = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        val report = pending; pending = null
        if (uri != null && report != null) scope.launch {
            writing = true
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { writeAttendanceExcel(report, it) }
                        ?: error("No se pudo abrir el archivo")
                }
                message = "Excel guardado: ${report.rows.size} registros."
            } catch (_: Exception) { message = "No se pudo guardar el Excel. Revisa la carpeta seleccionada y vuelve a intentar." }
            finally { writing = false }
        }
    }
    val ready = enabled && !writing && pending == null && filter.error() == null && rows.isNotEmpty()
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Filtrar e informar", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(filter.employee, { change(filter.copy(employee = it)) }, label = { Text("Buscar empleado por nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !writing)
            DateFilterField("Desde", filter.from, !writing) { change(filter.copy(from = it)) }
            DateFilterField("Hasta", filter.until, !writing) { change(filter.copy(until = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AttendanceStatus.entries.forEach { status -> FilterChip(selected = filter.status == status,
                    onClick = { change(filter.copy(status = status)) }, label = { Text(status.label) }, enabled = !writing) }
            }
            TextButton(onClick = { change(AttendanceFilter()) }, enabled = !writing) { Text("Limpiar filtros") }
            filter.error()?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("${rows.size} registros encontrados. Fechas según la entrada en ${TimeZone.getDefault().id}.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pending = snapshot(); pdf.launch("Pulso-asistencias.pdf") }, enabled = ready, modifier = Modifier.weight(1f)) { Text("PDF") }
                Button(onClick = { pending = snapshot(); excel.launch("Pulso-asistencias.xlsx") }, enabled = ready, modifier = Modifier.weight(1f)) { Text("Excel") }
            }
            OutlinedButton(onClick = {
                try { printAttendance(context, snapshot()) } catch (_: Exception) { message = "No se pudo abrir la impresión de Android." }
            }, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text("Imprimir informe") }
            Text("PDF, Excel e impresión incluyen únicamente los resultados filtrados. Las horas totales cuentan jornadas cerradas.", style = MaterialTheme.typography.bodySmall)
            if (writing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (message.isNotBlank()) Text(message)
        }
    }
}

@Composable
private fun DateFilterField(label: String, value: String, enabled: Boolean, change: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedTextField(value, {}, label = { Text("$label · AAAA-MM-DD") }, readOnly = true, modifier = Modifier.fillMaxWidth(),
        trailingIcon = { TextButton(enabled = enabled, onClick = {
            val calendar = Calendar.getInstance()
            if (value.isNotEmpty()) {
                val parts = value.split('-').map { it.toInt() }
                calendar.set(parts[0], parts[1] - 1, parts[2])
            }
            DatePickerDialog(context, { _, year, month, day -> change(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)) },
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }) { Text("Elegir") } })
}
