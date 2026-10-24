package com.example.asistenciaodoo

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import java.io.IOException
import java.util.concurrent.Executors

data class AttendanceState(
    val employee: Employee? = null, val dashboard: Dashboard? = null,
    val busy: Boolean = false, val message: String = "", val error: Boolean = false,
    val uncertain: Boolean = false, val role: AppRole = AppRole.EMPLOYEE,
    val team: List<TeamAttendance> = emptyList(), val teamLoaded: Boolean = false
)

class AttendanceViewModel : ViewModel() {
    var state by mutableStateOf(AttendanceState())
        private set
    var connection: ConnectionSettings? = null
        private set
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var api: OdooApi? = null
    private var cleared = false
    private fun publish(action: () -> Unit) { main.post { if (!cleared) action() } }
    private fun task(write: Boolean = false, action: () -> Unit) {
        if (state.busy) return
        state = state.copy(busy = true, message = "", error = false)
        executor.execute {
            try { action() } catch (e: Exception) {
                publish {
                    val detail = when (e) {
                        is OdooException -> e.message.orEmpty()
                        is IOException -> "No se pudo conectar. Revisa la IP, la red y que Odoo siga encendido."
                        else -> "No se pudo leer la respuesta de Odoo. Revisa tu sesión y permisos."
                    }
                    state = state.copy(error = true, uncertain = state.uncertain || write,
                        message = detail + if (write) " Actualiza antes de repetir la operación." else "")
                }
            } finally { publish { state = state.copy(busy = false) } }
        }
    }
    fun login(settings: ConnectionSettings, password: String, success: () -> Unit) = task {
        val client = OdooApi(settings)
        val employee = client.authenticate(password)
        api = client
        publish { connection = settings; state = state.copy(employee = employee, role = client.role); success() }
        val data = client.dashboard()
        publish { state = state.copy(dashboard = data, uncertain = false) }
    }
    fun refresh() = task {
        val data = requireNotNull(api).dashboard()
        publish { state = state.copy(dashboard = data, uncertain = false) }
    }
    fun register() {
        val current = state.dashboard ?: return
        if (state.uncertain) return
        val exiting = current.open != null
        task(write = true) {
            val client = requireNotNull(api)
            if (exiting) client.checkOut() else client.checkIn()
            val data = client.dashboard()
            publish { state = state.copy(dashboard = data, uncertain = false,
                message = if (exiting) "Salida guardada en Odoo." else "Entrada guardada en Odoo.") }
        }
    }
    fun refreshTeam() = task {
        val rows = requireNotNull(api).teamAttendance()
        publish { state = state.copy(team = rows, teamLoaded = true, uncertain = false) }
    }
    fun closeTeam(id: Int) {
        if (state.role != AppRole.ADMIN || state.uncertain) return
        task(write = true) {
            val client = requireNotNull(api)
            client.closeTeamAttendance(id)
            val rows = client.teamAttendance()
            val data = client.dashboard()
            publish { state = state.copy(team = rows, teamLoaded = true, dashboard = data,
                uncertain = false, message = "Salida del empleado guardada en Odoo.") }
        }
    }
    fun logout() = task {
        try { api?.logout() } finally {
            api = null
            publish { connection = null; state = AttendanceState(busy = true) }
        }
    }
    override fun onCleared() {
        cleared = true
        executor.shutdownNow()
        main.removeCallbacksAndMessages(null)
    }
}
