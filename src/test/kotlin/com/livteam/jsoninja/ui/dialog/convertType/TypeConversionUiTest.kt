package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.component.convertType.CodePreviewPanel
import com.livteam.jsoninja.ui.component.convertType.highlighting.TypeCodeToken
import java.awt.Color
import java.awt.Point
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.JSplitPane
import javax.swing.SwingUtilities

class TypeConversionUiTest : BasePlatformTestCase() {
    fun testEveryTargetLanguageThroughTheActualDropdownAndRenderedPreview() {
        val fileTypes = FileTypeManager.getInstance()
        val originalTypes = SupportedLanguage.entries.associateWith { fileTypes.getFileTypeByExtension(it.fileExtension) }
        val colors = EditorColorsManager.getInstance()
        val originalScheme = colors.globalScheme
        val settings = JsonToTypeDialogSettingsAdapter(JsoninjaSettingsState.getInstance(project))
        val originalConfig = settings.load()
        val output = Path.of("build/reports/type-conversion-ui")
        Files.createDirectories(output)
        val reports = mutableListOf<String>()
        try {
            WriteCommandAction.runWriteCommandAction(project) {
                SupportedLanguage.entries.forEach { fileTypes.associateExtension(PlainTextFileType.INSTANCE, it.fileExtension) }
            }
            for (schemeName in listOf("Default", "Darcula")) {
                val scheme = checkNotNull(colors.getScheme(schemeName)) { "Missing IDE scheme $schemeName" }.clone() as EditorColorsScheme
                colors.setGlobalScheme(scheme)
                val lifetime = Disposer.newDisposable()
                val presenter = JsonToTypeDialogPresenter(project, SOURCE_JSON) {}
                val initializedFields = mutableSetOf<EditorTextField>()
                try {
                    val root = presenter.component
                    val dropdown = descendants(root).filterIsInstance<ComboBox<*>>().single { it.itemCount > 0 && it.getItemAt(0) is SupportedLanguage }
                    for (language in SupportedLanguage.entries) {
                        dropdown.selectedItem = language
                        awaitPreview { presenter.getOutputFileExtension() == language.fileExtension && presenter.getCurrentPreviewText().isNotBlank() }
                        val preview = descendants(root).filterIsInstance<CodePreviewPanel>().single()
                        val field = descendants(preview).filterIsInstance<EditorTextField>().single()
                        if (initializedFields.add(field)) field.setDisposedWith(lifetime)
                        val editor = checkNotNull(field.getEditor(true))
                        assertTrue(editor.isViewer)
                        assertSame(PlainTextFileType.INSTANCE, field.fileType)
                        assertEquals(presenter.getCurrentPreviewText(), editor.document.text)
                        descendants(root).filterIsInstance<EditorTextField>().forEach {
                            if (initializedFields.add(it)) it.setDisposedWith(lifetime)
                            it.getEditor(true)
                        }
                        root.setSize(1400, maxOf(900, editor.document.lineCount * editor.lineHeight + 240))
                        descendants(root).filterIsInstance<JSplitPane>().forEach { it.dividerLocation = 650 }
                        repeat(3) { layoutRecursively(root) }
                        editor.scrollingModel.disableAnimation()
                        editor.scrollingModel.scrollVertically(0)
                        val image = BufferedImage(root.width, root.height, BufferedImage.TYPE_INT_RGB)
                        val graphics = image.createGraphics()
                        root.printAll(graphics)
                        graphics.dispose()
                        ImageIO.write(image, "png", output.resolve("${schemeName.lowercase()}-${language.fileExtension}.png").toFile())
                        val iterator = editor.highlighter.createIterator(0)
                        val lines = mutableListOf<String>()
                        val fields = mutableSetOf<String>()
                        while (!iterator.atEnd()) {
                            val foreground = iterator.textAttributes.foregroundColor ?: editor.colorsScheme.defaultForeground
                            val text = field.text.substring(iterator.start, iterator.end).replace("\n", "\\n")
                            lines += "${iterator.tokenType}\t${Integer.toHexString(foreground.rgb)}\t$text"
                            if (text == "Root" || text == "RootItem") assertSame("${language.name}: $text", TypeCodeToken.TYPE_NAME, iterator.tokenType)
                            if (text == "JsonPropertyName") assertSame(TypeCodeToken.METADATA, iterator.tokenType)
                            if (iterator.tokenType == TypeCodeToken.FIELD) {
                                fields += text.replace("_", "").lowercase()
                                val expectedColor = scheme.getAttributes(TypeCodeToken.FIELD.keys.single()).foregroundColor ?: scheme.defaultForeground
                                assertEquals("${language.name}: $text scheme", expectedColor, foreground)
                                val point = SwingUtilities.convertPoint(editor.contentComponent, editor.offsetToXY(iterator.start), root)
                                val end = SwingUtilities.convertPoint(editor.contentComponent, editor.offsetToXY(iterator.end), root)
                                assertPainted(image, point, end.x - point.x, editor.lineHeight, expectedColor, "${language.name}: $text")
                            }
                            iterator.advance()
                        }
                        assertTrue("${language.name}: missing fields ${EXPECTED_FIELDS - fields}", fields.containsAll(EXPECTED_FIELDS))
                        Files.writeString(output.resolve("${schemeName.lowercase()}-${language.fileExtension}.tokens.tsv"), lines.joinToString("\n"))
                        reports += "$schemeName ${language.name}: ${editor.contentComponent.size}, ${lines.size} tokens"
                    }
                } finally {
                    presenter.dispose()
                    Disposer.dispose(lifetime)
                    UIUtil.dispatchAllInvocationEvents()
                }
            }
        } finally {
            settings.save(originalConfig)
            colors.setGlobalScheme(originalScheme)
            WriteCommandAction.runWriteCommandAction(project) { originalTypes.forEach { (language, type) -> fileTypes.associateExtension(type, language.fileExtension) } }
            Files.writeString(output.resolve("summary.txt"), reports.joinToString("\n"))
        }
    }

    private fun awaitPreview(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            UIUtil.dispatchAllInvocationEvents()
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("UI preview did not settle", condition())
    }

    private fun descendants(component: Component): List<Component> = listOf(component) +
        if (component is Container) component.components.flatMap(::descendants) else emptyList()

    private fun layoutRecursively(component: Component) {
        if (component is Container) {
            component.doLayout()
            component.components.forEach(::layoutRecursively)
        }
    }

    private fun assertPainted(image: BufferedImage, start: Point, width: Int, height: Int, color: Color, message: String) {
        for (y in start.y.coerceAtLeast(0) until (start.y + height).coerceAtMost(image.height)) {
            for (x in start.x.coerceAtLeast(0) until (start.x + width).coerceAtMost(image.width)) {
                val pixel = Color(image.getRGB(x, y))
                if (kotlin.math.abs(pixel.red - color.red) < 20 && kotlin.math.abs(pixel.green - color.green) < 20 &&
                    kotlin.math.abs(pixel.blue - color.blue) < 20) return
            }
        }
        fail("$message: configured field color was not painted")
    }

    private companion object {
        val EXPECTED_FIELDS = setOf("createdat", "limit", "role", "username", "startdate", "offset", "sessionid", "results", "id", "details", "updatedat")
        val SOURCE_JSON = """[{"createdAt":3459.47,"limit":false,"role":false},{"username":true,"startDate":"2025-01-25","offset":82323},{"limit":"value","sessionId":"uuid","username":"Ada"},{"results":77668,"id":true,"details":89975},{"updatedAt":80050,"startDate":76459,"offset":"2025-06-29"}]"""
    }
}
