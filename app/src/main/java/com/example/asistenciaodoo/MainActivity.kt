package com.example.asistenciaodoo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.TimeZone

private val Navy = Color(0xFF102D46)
private val Teal = Color(0xFF007E78)
private val Mist = Color(0xFFF2F6FA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AttendanceApp() }
    }
}

@Composable
fun AttendanceApp(vm: AttendanceViewModel = viewModel()) {
    val state = vm.state
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    var employeeFilter by rememberSaveable { mutableStateOf("") }
    var fromFilter by rememberSaveable { mutableStateOf("") }
    var untilFilter by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableIntStateOf(0) }
    val reportFilter = AttendanceFilter(employeeFilter, fromFilter, untilFilter, AttendanceStatus.entries[statusFilter])
    val zone = TimeZone.getDefault()
    val filteredTeam = remember(state.team, reportFilter, zone.id) { filterAttendance(state.team, reportFilter, zone) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Teal, secondary = Navy,
        background = Mist, surface = Color.White)) {
        Scaffold(containerColor = Mist, bottomBar = {
            if (state.employee != null) NavigationBar(containerColor = Color.White) {
                val labels = listOf("Inicio", "Horario", "Historial", "Ajustes", "Equipo")
                val icons = listOf(Icons.Outlined.Home, Icons.Outlined.DateRange, Icons.Outlined.List, Icons.Outlined.Settings, Icons.Outlined.Person)
                roleTabs(state.role).forEach { i -> NavigationBarItem(selected = tab == i,
                    onClick = { tab = i; if (i == 4 && !state.teamLoaded && !state.busy) vm.refreshTeam() },
                    icon = { Icon(icons[i], null) }, label = { Text(labels[i]) }) }

            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                Row(Modifier.fillMaxWidth().background(Navy).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF57D9C4), modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("PULSO", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                        Text("Tu asistencia, conectada", color = Color(0xFFB7CEDD))
                    }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.message.isNotBlank()) Surface(color = if (state.error) Color(0xFFFFE7E7) else Color(0xFFDDF5EC),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(16.dp)) {
                    Text(state.message, Modifier.padding(14.dp), color = Navy)
                }
                if (state.employee == null) LoginScreen(state.busy) { settings, secret -> vm.login(settings, secret) { tab = if (vm.state.role == AppRole.ADMIN) 4 else 0 } }
                else LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (tab) {
                        0 -> if (state.role == AppRole.EMPLOYEE) {
                            item { PageTitle("Hola, ${state.employee.name.substringBefore(' ')}", "${state.role.label} · ${state.employee.department.ifBlank { "Mi espacio de trabajo" }}") }
                            item {
                                val data = state.dashboard
                                val active = data?.open != null
                                Section("MI JORNADA", Icons.Outlined.DateRange) {
                                    Text(if (data == null) "Estado sin actualizar" else if (active) "Estás en jornada" else "Jornada cerrada",
                                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Navy)
                                    Text(if (active) "Entrada · ${displayDate(data!!.open!!.checkIn)}" else "Registra tu asistencia al comenzar a trabajar.")
                                    Button(onClick = { confirm = true }, enabled = data != null && !state.busy && !state.uncertain,
                                        modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(16.dp)) {
                                        Icon(if (active) Icons.Outlined.ExitToApp else Icons.Outlined.ExitToApp, null)
                                        Spacer(Modifier.width(10.dp)); Text(if (active) "Registrar salida" else "Registrar entrada")
                                    }
                                    RefreshButton(state.busy, vm::refresh)
                                }
                            }
                            item {
                                val closed = state.dashboard?.history.orEmpty().filter { it.checkOut != null }
                                Section("RESUMEN RECIENTE", Icons.Outlined.Info) {
                                    Text(durationLabel(closed.sumOf { it.hours }), style = MaterialTheme.typography.headlineMedium, color = Teal, fontWeight = FontWeight.Bold)
                                    Text("En ${closed.size} jornadas cerradas de los últimos 100 registros consultados.")
                                }
                            }
                            state.dashboard?.history?.firstOrNull()?.let { last -> item { AttendanceCard(last) } }
                        }
                        1 -> if (state.role == AppRole.EMPLOYEE) {
                            item {
                                PageTitle("Mi horario", "Calendario asignado en Odoo")
                                Text("Horas del calendario, sin convertir a la zona del teléfono. Los descansos están identificados.", style = MaterialTheme.typography.bodySmall)
                                RefreshButton(state.busy, vm::refresh)
                            }
                            if (state.dashboard == null) item { Text("Actualiza para cargar tu horario.") }
                            else listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo").forEachIndexed { index, label ->
                                item {
                                    Section(label, Icons.Outlined.DateRange) {
                                        val periods = state.dashboard.periods.filter { it.day == index }
                                        if (periods.isEmpty()) Text("Sin horario asignado", color = Color.Gray)
                                        periods.forEach { period ->
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Column {
                                                    Text("${clockLabel(period.from)} — ${clockLabel(period.to)}", fontWeight = FontWeight.SemiBold)
                                                    period.week?.let { Text("Semana de calendario $it", style = MaterialTheme.typography.bodySmall) }
                                                }
                                                Text(if (period.lunch) "Descanso" else "Trabajo", color = if (period.lunch) Color.Gray else Teal)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> if (state.role == AppRole.EMPLOYEE) {
                            item {
                                PageTitle("Mis asistencias", "Hasta 100 registros recientes")
                                Text("Zona del dispositivo: ${TimeZone.getDefault().id}", style = MaterialTheme.typography.bodySmall)
                                RefreshButton(state.busy, vm::refresh)
                            }
                            val history = state.dashboard?.history.orEmpty()
                            if (history.isEmpty()) item { Section("Sin registros", Icons.Outlined.List) { Text("Actualiza o registra tu primera entrada.") } }
                            items(history.size, key = { history[it].id }) { AttendanceCard(history[it]) }
                        }
                        4 -> if (state.role == AppRole.ADMIN) {
                            item {
                                PageTitle("Mi equipo", "Panel de Administrador")
                                Text("Consulta de todas las asistencias de las empresas permitidas en Odoo.")
                                Text("${state.team.size} registros · ${state.team.count { it.attendance.checkOut == null }} jornadas abiertas")
                                RefreshButton(state.busy, vm::refreshTeam)
                            }
                            item(key = "admin-report-filters") {
                                AdminReportControls(reportFilter, { filter ->
                                    employeeFilter = filter.employee; fromFilter = filter.from
                                    untilFilter = filter.until; statusFilter = filter.status.ordinal
                                }, filteredTeam, state.teamLoaded && !state.busy)
                            }
                            if (!state.teamLoaded) item { Text("Pulsa Actualizar para cargar las asistencias del equipo.") }
                            else if (filteredTeam.isEmpty()) item { Text("No hay asistencias que coincidan con los filtros.") }
                            items(filteredTeam.size, key = { filteredTeam[it].attendance.id }) { index ->
                                val record = filteredTeam[index]
                                Section(record.employeeName, Icons.Outlined.Person) {
                                    Text("Entrada · ${displayDate(record.attendance.checkIn)}")
                                    Text("Salida · ${record.attendance.checkOut?.let { displayDate(it) } ?: "Pendiente"}")
                                    if (record.attendance.checkOut == null) Text("Jornada abierta", color = Teal)
                                    else Text(durationLabel(record.attendance.hours), color = Teal)
                                }
                            }
                        }
                        3 -> {
                            item { PageTitle("Mi cuenta", "Conexión con Odoo") }
                            item {
                                Section(state.employee.name, Icons.Outlined.Person) {
                                    Text("Rol: ${state.role.label}", color = Teal, fontWeight = FontWeight.Bold)
                                    Text(state.employee.department)
                                    if (state.role == AppRole.EMPLOYEE) Text("Empleado #${state.employee.id}")
                                    HorizontalDivider()
                                    Text("Servidor", fontWeight = FontWeight.Bold)
                                    Text(vm.connection?.server.orEmpty())
                                    Text("Base de datos · ${vm.connection?.database.orEmpty()}")
                                    Text("Usuario · ${vm.connection?.login.orEmpty()}")
                                }
                            }
                            item {
                                Section("Tu sesión", Icons.Outlined.Lock) {
                                    Text("La contraseña y la sesión no se guardan en el dispositivo. Si cierras la aplicación por completo, vuelve a iniciar sesión.")
                                    Text("Mantén Ubuntu y Odoo encendidos. La IP de VMware puede cambiar al reiniciar.")
                                    if (vm.connection?.server?.startsWith("http://") == true) Text("HTTP es solo para esta práctica local. Usa HTTPS en un despliegue real.", style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = vm::logout, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Outlined.ExitToApp, null); Spacer(Modifier.width(8.dp)); Text("Cerrar sesión")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (confirm && state.role == AppRole.EMPLOYEE) AlertDialog(onDismissRequest = { confirm = false }, title = {
            Text(if (state.dashboard?.open != null) "¿Finalizar jornada?" else "¿Comenzar jornada?")
        }, text = { Text("Se guardará la fecha y hora actual en Odoo para ${state.employee?.name}.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.register() }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
    }
}

@Composable
private fun LoginScreen(busy: Boolean, onLogin: (ConnectionSettings, String) -> Unit) {
    var server by rememberSaveable { mutableStateOf("http://192.168.245.132:8069") }
    var database by rememberSaveable { mutableStateOf("odoo_pruebas") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().imePadding()) {
        item { PageTitle("Bienvenido a tu jornada", "Accede con tu cuenta de Odoo.") }
        item { LoginField(server, { server = it }, "Servidor Odoo", busy, KeyboardType.Uri) }
        item { LoginField(database, { database = it }, "Base de datos", busy) }
        item { LoginField(user, { user = it }, "Usuario o correo", busy) }
        item {
            OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, singleLine = true, enabled = !busy,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { visible = !visible }) {
                    Text(if (visible) "Ocultar" else "Ver", style = MaterialTheme.typography.labelSmall)
                } })
        }
        item {
            Button(onClick = { val secret = password; password = ""; onLogin(ConnectionSettings(server, database, user), secret) },
                enabled = !busy && server.isNotBlank() && database.isNotBlank() && user.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(14.dp)) {
                Text(if (busy) "Conectando…" else "Iniciar sesión"); Spacer(Modifier.width(10.dp)); Icon(Icons.Outlined.ArrowForward, null)
            }
        }
        item { Text("Necesitas un empleado vinculado a tu usuario y permisos de Asistencias. Usa HTTP solo en tu red local de pruebas.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun LoginField(value: String, change: (String) -> Unit, label: String, busy: Boolean, type: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value, change, label = { Text(label) }, enabled = !busy, singleLine = true,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = type))
}
@Composable
private fun PageTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Navy)
    Text(subtitle, color = Color(0xFF65758A))
}
@Composable
private fun RefreshButton(busy: Boolean, refresh: () -> Unit) {
    TextButton(onClick = refresh, enabled = !busy) { Icon(Icons.Outlined.Refresh, null); Text("Actualizar") }
}
@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Teal, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
                Text(title, color = Navy, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}
@Composable
private fun AttendanceCard(record: Attendance) {
    Section(if (record.checkOut == null) "En curso" else "Jornada completada", Icons.Outlined.DateRange) {
        Text("Entrada · ${displayDate(record.checkIn)}", fontWeight = FontWeight.Medium)
        Text("Salida · ${record.checkOut?.let { displayDate(it) } ?: "Pendiente"}")
        if (record.checkOut != null) Text(durationLabel(record.hours), color = Teal, fontWeight = FontWeight.Bold)
    }
}
