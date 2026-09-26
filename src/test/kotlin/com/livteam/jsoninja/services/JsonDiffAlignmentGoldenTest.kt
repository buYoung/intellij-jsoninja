package com.livteam.jsoninja.services

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.model.JsonDiffAlignmentOptions
import com.livteam.jsoninja.model.JsonDiffAlignmentStatus
import com.livteam.jsoninja.model.JsonDiffSortMode
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import java.io.File
import java.util.Properties
import kotlinx.coroutines.runBlocking

/**
 * JsonDiffAlignmentService의 두 입력 정렬 결과를 testData/jsonDiff/alignment의 golden 파일과 비교합니다.
 * 각 케이스 디렉터리는 left/right 입력, case.properties(모드, 배열 옵션, 기대 상태), expected-left/right 출력을 가집니다.
 */
class JsonDiffAlignmentGoldenTest : BasePlatformTestCase() {

    private var originalIndentSize = DEFAULT_INDENT_SIZE

    override fun getTestDataPath(): String = "src/test/testData/jsonDiff/alignment"

    // align은 호출자의 coroutine에서 실행되므로 EDT가 아닌 test thread에서 runBlocking으로 호출한다.
    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        val settings = JsoninjaSettingsState.getInstance(project)
        originalIndentSize = settings.indentSize
        settings.indentSize = DEFAULT_INDENT_SIZE
    }

    override fun tearDown() {
        try {
            JsoninjaSettingsState.getInstance(project).indentSize = originalIndentSize
        } finally {
            super.tearDown()
        }
    }

    fun testObjectLeftOrder() = doTest("object-left-order")

    fun testObjectKeyAscending() = doTest("object-key-ascending")

    fun testArrayOrderKeptByDefault() = doTest("array-order-kept-by-default")

    fun testArrayOrderIgnored() = doTest("array-order-ignored")

    fun testArrayOrderIgnoredLeftOrder() = doTest("array-order-ignored-left-order")

    fun testArraySameIdChangedValue() = doTest("array-same-id-changed-value")

    fun testArrayDuplicates() = doTest("array-duplicates")

    fun testNestedRightOnlyKeys() = doTest("nested-right-only-keys")

    fun testInvalidRightLeftOrder() = doTest("invalid-right-left-order")

    fun testInvalidRightKeyAscending() = doTest("invalid-right-key-ascending")

    fun testInvalidRightIgnoreArray() = doTest("invalid-right-ignore-array")

    fun testPlaceholders() = doTest("placeholders")

    fun testNumbers() = doTest("numbers")

    fun testJson5LeftOrder() = doTest("json5-left-order")

    fun testNoChangeLeftOrder() = doTest("no-change-left-order")

    fun testEveryGoldenCaseHasTestMethod() {
        val caseNames = File(testDataPath).listFiles { file -> file.isDirectory }.orEmpty().map { it.name }.sorted()
        val testedCaseNames = listOf(
            "array-duplicates",
            "array-order-ignored",
            "array-order-ignored-left-order",
            "array-order-kept-by-default",
            "array-same-id-changed-value",
            "invalid-right-ignore-array",
            "invalid-right-key-ascending",
            "invalid-right-left-order",
            "json5-left-order",
            "nested-right-only-keys",
            "no-change-left-order",
            "numbers",
            "object-key-ascending",
            "object-left-order",
            "placeholders"
        )
        assertEquals(testedCaseNames, caseNames)
    }

    private fun doTest(caseName: String) {
        val caseDirectory = File(testDataPath, caseName)
        val leftText = File(caseDirectory, "left.json").readText()
        val rightText = File(caseDirectory, "right.json").readText()
        val caseProperties = Properties().apply {
            File(caseDirectory, "case.properties").reader().use { load(it) }
        }
        val options = JsonDiffAlignmentOptions(
            sortMode = JsonDiffSortMode.valueOf(caseProperties.getProperty("sortMode")),
            shouldIgnoreArrayOrder = caseProperties.getProperty("shouldIgnoreArrayOrder").toBoolean()
        )
        val expectedStatus = JsonDiffAlignmentStatus.valueOf(caseProperties.getProperty("expectedStatus"))

        val result = runBlocking {
            project.service<JsonDiffAlignmentService>().align(leftText, rightText, options)
        }

        assertEquals("$caseName status", expectedStatus, result.status)
        assertSameLinesWithFile(File(caseDirectory, "expected-left.json").path, result.leftText)
        assertSameLinesWithFile(File(caseDirectory, "expected-right.json").path, result.rightText)

        if (options.sortMode == JsonDiffSortMode.LEFT_ORDER) {
            assertEquals("$caseName: LEFT_ORDER must return the reference text byte-for-byte", leftText, result.leftText)
        }
        if (expectedStatus != JsonDiffAlignmentStatus.APPLIED) {
            assertEquals("$caseName: unavailable/no-change must keep the left input", leftText, result.leftText)
            assertEquals("$caseName: unavailable/no-change must keep the right input", rightText, result.rightText)
        }
    }

    private companion object {
        const val DEFAULT_INDENT_SIZE = 2
    }
}
