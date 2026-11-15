package com.naechamon.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

// 두뇌 층은 순수 Kotlin이어야 한다(3층 구조 규칙). 안드로이드·SDK import가 섞이면 실패한다.
class DomainPurityTest {

    private val forbiddenPrefixes = listOf("import android.", "import androidx.", "import ai.pleos.")

    @Test
    fun domainHasNoAndroidImports() {
        val domainDir = File("src/main/java/com/naechamon/domain")
        assertTrue("domain 폴더를 찾지 못함: ${domainDir.absolutePath}", domainDir.isDirectory)

        val violations = domainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filter { line -> forbiddenPrefixes.any { line.trim().startsWith(it) } }
                    .map { "${file.name}: ${it.trim()}" }
            }
            .toList()

        assertTrue("두뇌 층에 안드로이드 의존성이 있음:\n" + violations.joinToString("\n"), violations.isEmpty())
    }
}
