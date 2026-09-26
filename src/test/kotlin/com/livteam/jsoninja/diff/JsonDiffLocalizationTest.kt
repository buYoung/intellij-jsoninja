package com.livteam.jsoninja.diff

import java.io.File
import java.util.Properties
import junit.framework.TestCase

/**
 * JSON Diff 새 문구가 모든 LocalizationBundle 변형에 있고, 한국어 문구가 승인된 표기와 같은지 확인합니다.
 */
class JsonDiffLocalizationTest : TestCase() {

    fun testEveryBundleDefinesDiffWorkflowMessages() {
        BUNDLE_FILE_NAMES.forEach { bundleFileName ->
            val bundle = loadBundle(bundleFileName)
            NEW_MESSAGE_KEYS.forEach { key ->
                assertFalse("$bundleFileName must define non-blank '$key'", bundle.getProperty(key).isNullOrBlank())
            }
            EXISTING_DEFAULT_TITLE_KEYS.forEach { key ->
                assertFalse("$bundleFileName must keep '$key'", bundle.getProperty(key).isNullOrBlank())
            }
        }
    }

    fun testKoreanBundleUsesApprovedLabels() {
        val koreanBundle = loadBundle("LocalizationBundle_ko.properties")
        mapOf(
            "action.diff.sort.mode.key.ascending" to "키 사전순",
            "action.diff.sort.mode.left.order" to "왼쪽 기준",
            "action.diff.sort.auto" to "자동 정렬",
            "action.diff.ignore.array.order" to "배열 순서 무시",
            "action.diff.sort.restore" to "정렬 되돌리기",
            "action.com.livteam.jsoninja.action.editor.EditorShowJsonDiffAction.text" to "선택 JSON 비교",
            "editor.action.show.json.diff.selection" to "선택 JSON 비교"
        ).forEach { (key, expectedText) ->
            assertEquals(key, expectedText, koreanBundle.getProperty(key))
        }
    }

    private fun loadBundle(bundleFileName: String): Properties {
        return Properties().apply {
            File(MESSAGES_DIRECTORY, bundleFileName).inputStream().use { load(it) }
        }
    }

    private companion object {
        const val MESSAGES_DIRECTORY = "src/main/resources/messages"

        val BUNDLE_FILE_NAMES = listOf(
            "LocalizationBundle.properties",
            "LocalizationBundle_en.properties",
            "LocalizationBundle_ko.properties",
            "LocalizationBundle_ja.properties",
            "LocalizationBundle_zh_CN.properties"
        )

        val EXISTING_DEFAULT_TITLE_KEYS = listOf(
            "dialog.json.diff.title",
            "dialog.json.diff.left",
            "dialog.json.diff.right",
            "action.diff.sort.keys.once"
        )

        val NEW_MESSAGE_KEYS = listOf(
            "action.diff.sort.mode",
            "action.diff.sort.mode.description",
            "action.diff.sort.mode.selected",
            "action.diff.sort.mode.key.ascending",
            "action.diff.sort.mode.key.ascending.description",
            "action.diff.sort.mode.left.order",
            "action.diff.sort.mode.left.order.description",
            "action.diff.sort.auto",
            "action.diff.sort.auto.description",
            "action.diff.ignore.array.order",
            "action.diff.ignore.array.order.description",
            "action.diff.sort.restore",
            "action.diff.sort.restore.description",
            "action.diff.sort.unavailable",
            "action.diff.rename.titles",
            "action.diff.rename.titles.description",
            "dialog.json.diff.titles.title",
            "dialog.json.diff.titles.left",
            "dialog.json.diff.titles.right",
            "dialog.json.diff.titles.comment",
            "action.com.livteam.jsoninja.action.editor.EditorShowJsonDiffAction.text",
            "action.com.livteam.jsoninja.action.editor.EditorShowJsonDiffAction.description",
            "editor.action.error.invalid.json.document",
            "editor.action.show.json.diff.selection",
            "editor.action.show.json.diff.document"
        )
    }
}
