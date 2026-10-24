package com.example.asistenciaodoo

import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test

class RolesApiTest {
    private class Fixture(var admin: Boolean) : AutoCloseable {
        val server = MockWebServer()
        val calls = mutableListOf<JSONObject>()
        var opened = true
        init {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val p = JSONObject(request.body.readUtf8()).getJSONObject("params")
                    val result: Any = if (request.path == "/web/session/authenticate") JSONObject().put("uid", 23)
                    else {
                        calls.add(p)
                        when (p.getString("model")) {
                            "res.users" -> {
                                assertEquals(23, p.getJSONArray("args").getJSONArray(0).getInt(0))
                                assertEquals("hr_attendance.group_hr_attendance_manager", p.getJSONArray("args").getString(1))
                                admin
                            }
                            "hr.employee" -> JSONArray("""[{"id":42,"name":"Alex","department_id":false,"resource_calendar_id":false}]""")
                            else -> if (p.getString("method") == "write") { opened = false; true }
                            else if (opened) JSONArray("""[{"id":88,"employee_id":[55,"Ana"],"check_in":"2026-10-24 08:00:00","check_out":false,"worked_hours":0.0}]""")
                            else JSONArray()
                        }
                    }
                    return MockResponse().setBody(JSONObject().put("result", result).toString())
                }
            }
            server.start()
        }
        fun login(): OdooApi = OdooApi(ConnectionSettings(server.url("/").toString().trimEnd('/'), "db", "user")).also {
            it.authenticate("test-only")
        }
        override fun close() = server.shutdown()
    }
    @Test fun employeeCannotQueryTeamOrCloseOtherEmployee() {
        Fixture(false).use { fixture ->
            val api = fixture.login()
            assertEquals(AppRole.EMPLOYEE, api.role)
            try { api.teamAttendance(); fail("Employee accessed team") } catch (_: OdooException) { }
            try { api.closeTeamAttendance(88); fail("Employee closed another attendance") } catch (_: OdooException) { }
            assertFalse(fixture.calls.any { it.getString("model") == "hr.attendance" })
        }
    }
    @Test fun administratorCanListTeamAndCloseOpenAttendance() {
        Fixture(true).use { fixture ->
            val api = fixture.login()
            assertEquals(AppRole.ADMIN, api.role)
            assertEquals("Ana", api.teamAttendance().single().employeeName)
            api.closeTeamAttendance(88)
            val write = fixture.calls.single { it.optString("method") == "write" }
            assertEquals(88, write.getJSONArray("args").getJSONArray(0).getInt(0))
            assertEquals(setOf("check_out"), write.getJSONArray("args").getJSONObject(1).keys().asSequence().toSet())
        }
    }
    @Test fun revokedAdministratorIsRejectedBeforeTeamDataRequest() {
        Fixture(true).use { fixture ->
            val api = fixture.login()
            fixture.admin = false
            try { api.teamAttendance(); fail("Revoked admin accessed team") } catch (_: OdooException) { }
            try { api.closeTeamAttendance(88); fail("Revoked admin wrote attendance") } catch (_: OdooException) { }
            assertFalse(fixture.calls.any { it.getString("model") == "hr.attendance" })
        }
    }
    @Test fun alreadyClosedAttendanceCannotBeClosedAgain() {
        Fixture(true).use { fixture ->
            val api = fixture.login()
            fixture.opened = false
            try { api.closeTeamAttendance(88); fail("Closed again") } catch (_: OdooException) { }
            assertFalse(fixture.calls.any { it.optString("method") == "write" })
        }
    }
}
