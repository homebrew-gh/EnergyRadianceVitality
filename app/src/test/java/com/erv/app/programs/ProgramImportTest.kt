package com.erv.app.programs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramImportTest {

    @Test
    fun parseEnvelopeWithPrograms() {
        val json = """
            {
              "ervImportVersion": 1,
              "programs": [
                {
                  "id": "p1",
                  "name": "Coach plan",
                  "weeklySchedule": [
                    { "dayOfWeek": 1, "blocks": [] }
                  ]
                }
              ],
              "activeProgramId": "p1"
            }
        """.trimIndent()
        val (env, errs) = ProgramImport.parse(json)
        assertTrue(errs.isEmpty())
        assertNotNull(env)
        assertEquals(1, env!!.programs.size)
        assertEquals("Coach plan", env.programs.first().name)
        assertEquals("p1", env.activeProgramId)
    }

    @Test
    fun parseSingleProgramObject() {
        val json = """{"name":"Solo","weeklySchedule":[{"dayOfWeek":2,"blocks":[]}]}"""
        val (env, errs) = ProgramImport.parse(json)
        assertTrue(errs.isEmpty())
        assertEquals("Solo", env!!.programs.single().name)
    }

    @Test
    fun parseArrayOfPrograms() {
        val json = """[{"name":"A","weeklySchedule":[]},{"name":"B","weeklySchedule":[]}]"""
        val (env, errs) = ProgramImport.parse(json)
        assertTrue(errs.isEmpty())
        assertEquals(listOf("A", "B"), env!!.programs.map { it.name })
    }

    @Test
    fun rejectsEmptyProgramsList() {
        val json = """{"ervImportVersion":1,"programs":[]}"""
        val (env, errs) = ProgramImport.parse(json)
        assertNull(env)
        assertTrue(errs.any { it.contains("program", ignoreCase = true) })
    }

    @Test
    fun rejectsBadDayOfWeek() {
        val json = """
            {"ervImportVersion":1,"programs":[
              {"name":"X","weeklySchedule":[{"dayOfWeek":9,"blocks":[]}]}
            ]}
        """.trimIndent()
        val (env, errs) = ProgramImport.parse(json)
        assertNull(env)
        assertTrue(errs.any { it.contains("dayOfWeek") })
    }
}
