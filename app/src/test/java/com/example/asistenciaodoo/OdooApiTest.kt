package com.example.asistenciaodoo

import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class OdooApiTest {
    @Test fun rejectsAddressesWithCredentialsPathsOrWrongScheme() {
        listOf("ftp://example.com", "http://user:secret@example.com", "http://example.com/odoo", "http://example.com?db=other", "http://example.com/#x").forEach {
            try { OdooApi.normalizeServer(it); fail("Accepted $it") } catch (_: OdooException) { }
        }
        assertEquals("http://192.168.245.132:8069", OdooApi.normalizeServer(" http://192.168.245.132:8069/ "))
    }
    @Test fun parsesFalseAndNullCheckoutAsOpen() {
        for (value in listOf(false, JSONObject.NULL, "")) {
            val row = JSONObject().put("id", 9).put("check_in", "2026-10-22 12:00:00").put("check_out", value)
            assertNull(OdooApi.parseAttendance(row).checkOut)
        }
    }
    @Test fun parsesClosedAttendanceAndConvertsUtcToBolivia() {
        val row = JSONObject("""{"id":1,"check_in":"2026-10-22 12:00:00","check_out":"2026-10-22 13:00:00","worked_hours":1.0}""")
        assertEquals(1.0, OdooApi.parseAttendance(row).hours, 0.001)
        assertTrue(displayDate(row.getString("check_in"), TimeZone.getTimeZone("America/La_Paz")).endsWith("08:00"))
        assertEquals("1 h 30 min", durationLabel(1.5))
        assertEquals("13:30", clockLabel(13.5))
    }
    @Test fun loginDashboardAndWritesStayBoundToAuthenticatedEmployee() {
        val server = MockWebServer()
        val calls = mutableListOf<JSONObject>()
        var open = false
        server.dispatcher = object : Dispatcher() {
          override fun dispatch(request: RecordedRequest): MockResponse {
            val body = JSONObject(request.body.readUtf8())
            val p = body.getJSONObject("params")
            val result: Any = if (request.path == "/web/session/authenticate") {
                JSONObject().put("uid", 23).put("user_context", JSONObject().put("company_id", 4))
            } else if (request.path == "/hr_attendance/systray_check_in_out") {
                assertEquals("session_id=test-session", request.getHeader("Cookie"))
                assertEquals(0, p.length())
                open = !open
                JSONObject().put("attendance_state", if (open) "checked_in" else "checked_out")
            } else {
                assertEquals("session_id=test-session", request.getHeader("Cookie"))
                calls.add(p)
                when (p.getString("model")) {
                    "res.users" -> false
                    "hr.employee" -> JSONArray("""[{"id":42,"name":"Alexander","department_id":[2,"Management"],"resource_calendar_id":[3,"Standard"]}]""")
                    "resource.calendar.attendance" -> JSONArray("""[{"dayofweek":"0","hour_from":12.0,"hour_to":13.0,"day_period":"lunch","week_type":false}]""")
                    else -> when (p.getString("method")) {
                        "create" -> { open = true; 88 }
                        "write" -> { open = false; true }
                        else -> if (open) JSONArray("""[{"id":88,"check_in":"2026-10-22 12:00:00","check_out":false,"worked_hours":0.0}]""") else JSONArray()
                    }
                }
            }
            return MockResponse().addHeader("Set-Cookie", "session_id=test-session; HttpOnly; Path=/")
                .setBody(JSONObject().put("jsonrpc", "2.0").put("result", result).toString())
          }
        }
        server.start()
        try {
            val api = OdooApi(ConnectionSettings(server.url("/").toString().trimEnd('/'), "test", "alex"))
            assertEquals(42, api.authenticate("not-a-real-password").id)
            val data = api.dashboard()
            assertTrue(data.periods.single().lunch)
            assertNull(data.open)
            api.checkIn()
            assertNotNull(api.dashboard().open)
            try { api.checkIn(); fail("Allowed duplicate entry") } catch (_: OdooException) { }
            api.checkOut()
            assertNull(api.dashboard().open)
            assertEquals(AppRole.EMPLOYEE, api.role)
            assertFalse(calls.any { it.optString("method") in listOf("create", "write") })
            val employeeDomain = calls.first { it.getString("model") == "hr.employee" }.getJSONArray("args").getJSONArray(0)
            assertTrue(employeeDomain.toString().contains("23"))
            assertTrue(employeeDomain.toString().contains("company_id"))
        } finally { server.shutdown() }
    }
    @Test fun surfacesAuthenticationErrorsWithoutLeakingPassword() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
          override fun dispatch(request: RecordedRequest): MockResponse {
            return MockResponse().setBody("""{"error":{"data":{"message":"Acceso denegado"}}}""")
          }
        }
        server.start()
        try {
            val api = OdooApi(ConnectionSettings(server.url("/").toString().trimEnd('/'), "test", "alex"))
            try { api.authenticate("test-password"); fail("Accepted error") }
            catch (e: OdooException) { assertEquals("Acceso denegado", e.message) }
        } finally { server.shutdown() }
    }
}
