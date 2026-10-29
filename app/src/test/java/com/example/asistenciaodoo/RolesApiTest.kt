package com.example.asistenciaodoo

import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test

class RolesApiTest {
    private class Fixture(var admin: Boolean, val total: Int = 1) : AutoCloseable {
        val server = MockWebServer()
        val calls = mutableListOf<JSONObject>()
        init {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val p = JSONObject(request.body.readUtf8()).getJSONObject("params")
                    val result: Any = if (request.path == "/web/session/authenticate") JSONObject().put("uid", 23).put("name", "Admin")
                    else {
                        calls.add(p)
                        when (p.getString("model")) {
                            "res.users" -> {
                                assertEquals(23, p.getJSONArray("args").getJSONArray(0).getInt(0))
                                assertEquals("hr_attendance.group_hr_attendance_manager", p.getJSONArray("args").getString(1))
                                admin
                            }
                            "hr.employee" -> JSONArray("""[{"id":42,"name":"Alex","department_id":false,"resource_calendar_id":false}]""")
                            else -> {
                                val kwargs = p.getJSONObject("kwargs")
                                val offset = kwargs.optInt("offset")
                                val end = minOf(total, offset + kwargs.getInt("limit"))
                                JSONArray().apply {
                                    for (id in offset until end) put(JSONObject().put("id", id + 1)
                                        .put("employee_id", JSONArray().put(55).put("Ana"))
                                        .put("check_in", "2026-10-24 08:00:00").put("check_out", false).put("worked_hours", 0.0))
                                }
                            }
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
    @Test fun employeeCannotQueryTeam() {
        Fixture(false).use { fixture ->
            val api = fixture.login()
            assertEquals(AppRole.EMPLOYEE, api.role)
            try { api.teamAttendance(); fail("Employee accessed team") } catch (_: OdooException) { }
            assertFalse(fixture.calls.any { it.getString("model") == "hr.attendance" })
        }
    }
    @Test fun administratorWithoutEmployeeCanListTeamButCannotMark() {
        Fixture(true).use { fixture ->
            val api = fixture.login()
            assertEquals(AppRole.ADMIN, api.role)
            assertEquals("Ana", api.teamAttendance().single().employeeName)
            try { api.checkIn(); fail("Admin marked entry") } catch (_: OdooException) { }
            try { api.checkOut(); fail("Admin marked exit") } catch (_: OdooException) { }
            assertFalse(fixture.calls.any { it.getString("model") == "hr.employee" })
            assertFalse(fixture.calls.any { it.optString("method") in listOf("create", "write") })
        }
    }
    @Test fun revokedAdministratorIsRejectedBeforeTeamDataRequest() {
        Fixture(true).use { fixture ->
            val api = fixture.login()
            fixture.admin = false
            try { api.teamAttendance(); fail("Revoked admin accessed team") } catch (_: OdooException) { }
            assertFalse(fixture.calls.any { it.getString("model") == "hr.attendance" })
        }
    }
    @Test fun teamLoadsRecordsBeyondFirstPage() {
        Fixture(true, total = 201).use { fixture ->
            val api = fixture.login()
            val team = api.teamAttendance()
            assertEquals(201, team.size)
            assertEquals(201, team.last().attendance.id)
            val reads = fixture.calls.filter { it.getString("model") == "hr.attendance" }
            assertEquals(listOf(0, 200), reads.map { it.getJSONObject("kwargs").getInt("offset") })
            assertTrue(reads.all { it.getJSONArray("args").getJSONArray(0).length() == 0 })
        }
    }
    @Test fun administratorHasOnlyTeamAndSettingsNavigation() {
        assertEquals(listOf(4, 3), roleTabs(AppRole.ADMIN))
        assertEquals(listOf(0, 1, 2, 3), roleTabs(AppRole.EMPLOYEE))
    }
}
