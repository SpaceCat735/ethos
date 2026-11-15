package com.naechamon.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

// 긴급 경고 문구는 "점검을 받아보세요" 수준까지만. 고장이라고 단정하면 안 된다 (안전 규칙).
class EmergencyStringsTest {

    private val forbiddenWords = listOf("고장", "파손", "망가")

    @Test
    fun emergencyMessages_doNotAssertFailure() {
        val stringsFile = File("src/main/res/values/strings.xml")
        assertTrue("strings.xml을 찾지 못함", stringsFile.isFile)

        val emergencyLines = stringsFile.readLines().filter { it.contains("name=\"emergency_") }
        assertTrue("긴급 문구가 없음", emergencyLines.isNotEmpty())

        val violations = emergencyLines.filter { line -> forbiddenWords.any { line.contains(it) } }
        assertTrue("단정 표현이 있음:\n" + violations.joinToString("\n"), violations.isEmpty())
    }
}
