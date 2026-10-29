package com.example.asistenciaodoo

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ConnectionSettings(val server: String, val database: String, val login: String)
enum class AppRole(val label: String) { EMPLOYEE("Empleado"), ADMIN("Administrador") }
data class TeamAttendance(val attendance: Attendance, val employeeName: String)
data class Employee(val id: Int, val name: String, val department: String, val calendarId: Int)
data class Attendance(val id: Int, val checkIn: String, val checkOut: String?, val hours: Double)
data class WorkPeriod(val day: Int, val from: Double, val to: Double, val lunch: Boolean, val week: String?)
data class Dashboard(val history: List<Attendance>, val open: Attendance?, val periods: List<WorkPeriod>)

fun roleTabs(role: AppRole): List<Int> = if (role == AppRole.ADMIN) listOf(4, 3) else listOf(0, 1, 2, 3)

class OdooException(message: String) : Exception(message)

/** One instance per login. Passwords and session cookies are never persisted. */
class OdooApi(settings: ConnectionSettings) {
    private val server = normalizeServer(settings.server)
    private val database = settings.database.trim()
    private val login = settings.login.trim()
    private var cookie = ""
    private lateinit var employee: Employee
    var role: AppRole = AppRole.EMPLOYEE
        private set
    private var userId = 0

    private fun request(path: String, body: JSONObject): JSONObject {
        val connection = URL(server + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 25_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            if (cookie.isNotEmpty()) connection.setRequestProperty("Cookie", cookie)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            connection.headerFields.entries
                .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                .flatMap { it.value }
                .firstOrNull { it.startsWith("session_id=") }
                ?.let { cookie = it.substringBefore(';') }
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw OdooException("Odoo respondió HTTP $code. Revisa el servidor y la sesión.")
            return try { JSONObject(text) } catch (_: Exception) {
                throw OdooException("La dirección no devolvió JSON de Odoo. Comprueba el servidor.")
            }
        } finally { connection.disconnect() }
    }

    private fun rpc(path: String, params: JSONObject): Any {
        val response = request(path, JSONObject().apply {
            put("jsonrpc", "2.0"); put("method", "call"); put("id", 1); put("params", params)
        })
        response.optJSONObject("error")?.let {
            val data = it.optJSONObject("data")
            val detail = data?.optString("message")?.takeIf(String::isNotBlank)
                ?: "No se pudo completar la operación. Inicia sesión y revisa tus permisos."
            throw OdooException(detail.take(450))
        }
        return response.opt("result")?.takeUnless { it == JSONObject.NULL }
            ?: throw OdooException("Odoo no devolvió un resultado.")
    }

    private fun call(model: String, method: String, args: JSONArray, kwargs: JSONObject = JSONObject()): Any =
        rpc("/web/dataset/call_kw", JSONObject().apply {
            put("model", model); put("method", method); put("args", args); put("kwargs", kwargs)
        })

    private fun search(model: String, domain: JSONArray, fields: List<String>, order: String = "id", limit: Int = 100, offset: Int = 0): JSONArray =
        call(model, "search_read", JSONArray().put(domain), JSONObject().apply {
            put("fields", JSONArray(fields)); put("order", order); put("limit", limit); put("offset", offset)
        }) as JSONArray

    fun authenticate(password: String): Employee {
        if (database.isBlank() || login.isBlank() || password.isBlank())
            throw OdooException("Completa la base de datos, el usuario y la contraseña.")
        val session = rpc("/web/session/authenticate", JSONObject().apply {
            put("db", database); put("login", login); put("password", password)
        }) as? JSONObject ?: throw OdooException("Usuario o contraseña incorrectos.")
        val uid = session.optInt("uid")
        if (uid <= 0) throw OdooException("Usuario o contraseña incorrectos.")
        userId = uid
        role = if (call("res.users", "has_group", JSONArray()
            .put(JSONArray().put(uid)).put("hr_attendance.group_hr_attendance_manager")) == true)
            AppRole.ADMIN else AppRole.EMPLOYEE
        if (role == AppRole.ADMIN) {
            employee = Employee(0, session.optString("name", login), "Administración", 0)
            return employee
        }
        val company = session.optJSONObject("user_context")?.optInt("company_id") ?: 0
        val domain = JSONArray().put(condition("user_id", "=", uid))
        if (company > 0) domain.put(condition("company_id", "=", company))
        val employees = search("hr.employee", domain, listOf("name", "department_id", "resource_calendar_id"), limit = 2)
        if (employees.length() == 0) throw OdooException("Tu usuario no tiene un empleado vinculado en Odoo.")
        if (employees.length() > 1) throw OdooException("Hay empleados en varias empresas. Configura tu empresa principal en Odoo.")
        val item = employees.getJSONObject(0)
        employee = Employee(item.getInt("id"), item.getString("name"),
            item.optJSONArray("department_id")?.optString(1).orEmpty(),
            item.optJSONArray("resource_calendar_id")?.optInt(0) ?: 0)
        return employee
    }

    private fun openAttendance(): Attendance? {
        val rows = search("hr.attendance", JSONArray()
            .put(condition("employee_id", "=", employee.id))
            .put(condition("check_out", "=", false)), attendanceFields, "check_in desc", 1)
        return if (rows.length() > 0) parseAttendance(rows.getJSONObject(0)) else null
    }

    fun dashboard(): Dashboard {
        val records = search("hr.attendance", JSONArray().put(condition("employee_id", "=", employee.id)),
            attendanceFields, "check_in desc", 100)
        val history = (0 until records.length()).map { parseAttendance(records.getJSONObject(it)) }
        val periods = if (employee.calendarId == 0) emptyList() else {
            val rows = search("resource.calendar.attendance", JSONArray()
                .put(condition("calendar_id", "=", employee.calendarId))
                .put(condition("display_type", "=", false)),
                listOf("dayofweek", "hour_from", "hour_to", "day_period", "week_type"),
                "dayofweek, hour_from", 100)
            (0 until rows.length()).map {
                val row = rows.getJSONObject(it)
                WorkPeriod(row.getString("dayofweek").toInt(), row.getDouble("hour_from"),
                    row.getDouble("hour_to"), row.optString("day_period") == "lunch",
                    (row.opt("week_type") as? String)?.takeIf(String::isNotBlank))
            }
        }
        return Dashboard(history, openAttendance(), periods)
    }

    fun checkIn() {
        requireEmployee()
        if (openAttendance() != null) throw OdooException("Ya existe una jornada abierta. Actualiza el estado.")
        toggleOwnAttendance("checked_in")
    }

    fun checkOut() {
        requireEmployee()
        if (openAttendance() == null) throw OdooException("No hay una entrada abierta. Actualiza el estado.")
        toggleOwnAttendance("checked_out")
    }

    private fun toggleOwnAttendance(expected: String) {
        // Odoo chooses the employee from the authenticated session and uses server time.
        // No employee ID is accepted here, and there are no automatic retries.
        val result = rpc("/hr_attendance/systray_check_in_out", JSONObject()) as? JSONObject
            ?: throw OdooException("Odoo no confirmó el fichaje. Actualiza el estado.")
        if (result.optString("attendance_state") != expected)
            throw OdooException("El estado cambió durante la operación. Actualiza antes de continuar.")
    }

    private fun requireAdmin() {
        // Revalidate against Odoo on each administrative request, including revocations.
        if (call("res.users", "has_group", JSONArray().put(JSONArray().put(userId))
                .put("hr_attendance.group_hr_attendance_manager")) != true)
            throw OdooException("Esta función requiere el rol Administrador de Asistencias en Odoo.")
    }

    fun teamAttendance(): List<TeamAttendance> {
        requireAdmin()
        val result = mutableListOf<TeamAttendance>()
        var offset = 0
        while (true) {
            val rows = search("hr.attendance", JSONArray(), attendanceFields + "employee_id", "check_in desc, id desc", 200, offset)
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                result.add(TeamAttendance(parseAttendance(row), row.optJSONArray("employee_id")?.optString(1).orEmpty()))
            }
            if (rows.length() < 200) break
            offset += rows.length()
        }
        return result.distinctBy { it.attendance.id }
    }

    private fun requireEmployee() {
        if (role != AppRole.EMPLOYEE)
            throw OdooException("La cuenta Administrador solo consulta asistencias. Usa una cuenta Empleado para marcar.")
    }

    fun logout() {
        try { rpc("/web/session/destroy", JSONObject()) } finally { cookie = "" }
    }

    companion object {
        private val attendanceFields = listOf("id", "check_in", "check_out", "worked_hours")
        fun normalizeServer(value: String): String {
            val uri = try { URI(value.trim().trimEnd('/')) } catch (_: Exception) {
                throw OdooException("La dirección del servidor no es válida.")
            }
            if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank() ||
                uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
                !uri.path.isNullOrEmpty()) throw OdooException("Usa solo la dirección y el puerto, por ejemplo http://192.168.245.132:8069")
            return uri.toString()
        }
        private fun condition(field: String, operator: String, value: Any) = JSONArray().put(field).put(operator).put(value)
        fun parseAttendance(row: JSONObject) = Attendance(row.getInt("id"), row.getString("check_in"),
            (row.opt("check_out") as? String)?.takeIf(String::isNotBlank), row.optDouble("worked_hours", 0.0))
        fun utcNow(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }
}

fun displayDate(value: String, zone: TimeZone = TimeZone.getDefault()): String = runCatching {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC"); isLenient = false
    }.parse(value) ?: error("Fecha inválida")
    SimpleDateFormat("dd MMM · HH:mm", Locale("es")).apply { timeZone = zone }.format(date)
}.getOrDefault(value)

fun durationLabel(hours: Double): String {
    val minutes = kotlin.math.round(hours.coerceAtLeast(0.0) * 60).toInt()
    return "${minutes / 60} h ${minutes % 60} min"
}

fun clockLabel(hours: Double): String {
    val minutes = kotlin.math.round(hours * 60).toInt()
    return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
}
