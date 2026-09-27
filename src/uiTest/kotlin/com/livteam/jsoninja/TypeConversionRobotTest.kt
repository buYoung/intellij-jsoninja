package com.livteam.jsoninja

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.awt.Color
import javax.imageio.ImageIO

/** Runs against the plugin installed by runIdeForUiTests, rather than a fixture-only editor. */
class TypeConversionRobotTest {
    private val robot = RemoteRobot(System.getProperty("robot.url", "http://127.0.0.1:8082"))
    private val output = Path.of("build/reports/type-conversion-robot")

    @Test fun allLanguagesInTheVisibleConversionDialog() {
        Files.createDirectories(output)
        val results = mutableListOf("provider,scheme,language,visible_preview,custom_color_pixels,type_to_json")
        waitUntil("UI test project") {
            robot.callJs<Boolean>("com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects().length > 0")
        }
        robot.runJs("""
            global.put('originalScheme', com.intellij.openapi.editor.colors.EditorColorsManager.getInstance().getGlobalScheme());
            global.put('originalTypes', new java.util.HashMap());
        """.trimIndent(), true)
        try {
            for (bundled in listOf(false, true)) {
                if (bundled) forceBundledHighlighters()
                for (scheme in listOf("Default", "Darcula")) {
                    robot.runJs("""
                        var colors = com.intellij.openapi.editor.colors.EditorColorsManager.getInstance();
                        var selected = colors.getScheme('$scheme').clone();
                        selected.setName('JSONinja $scheme test');
                        selected.setEditorFontName('Monospaced');
                        colors.setGlobalScheme(selected);
                    """.trimIndent(), true)
                    openConversionDialog()
                    val dialog = robot.find<ComponentFixture>(byXpath("//div[@title='Convert JSON and Type']"), Duration.ofSeconds(20))
                    try {
                        dialog.runJs("java.awt.Desktop.getDesktop().requestForeground(true); component.toFront(); component.requestFocus();", true)
                        waitUntil("focused conversion dialog") { dialog.callJs<Boolean>("component.isFocused()", true) }
                        for ((name, extension) in LANGUAGES) {
                            val selector = robot.find<ComponentFixture>(byXpath("//div[@class='LanguageSelectorComponent' and @visible='true']//div[@class='ComboBox']"))
                            val isSameLanguage = selector.callJs<Boolean>("String(component.getSelectedItem().name()) == '$name'", true)
                            dialog.runJs(FIND_PREVIEW + "global.put('previousField', field);", true)
                            // Change the actual JComboBox model: its production action listener schedules conversion.
                            selector.runJs("for (var i=0; i<component.getItemCount(); i++) { if (String(component.getItemAt(i).name()) == '$name') component.setSelectedIndex(i); }", true)
                            waitUntil("$name preview") {
                                dialog.callJs<Boolean>(FIND_PREVIEW + "field != null && field.getText().length() > 0 && ($isSameLanguage || field != global.get('previousField'));", true)
                            }
                            val prefix = "${if (bundled) "bundled" else "installed"}-${scheme.lowercase()}-$extension"
                            val tokens = dialog.callJs<String>(FIND_PREVIEW + """
                                var editor = field.getEditor();
                                if (editor == null || !editor.isViewer() || !editor.getContentComponent().isShowing()) throw new Error('Preview is not a visible viewer');
                                editor.getScrollingModel().disableAnimation();
                                editor.getScrollingModel().scrollVertically(0);
                                var iterator = editor.getHighlighter().createIterator(0);
                                var result = [];
                                while (!iterator.atEnd()) {
                                    var attributes = iterator.getTextAttributes();
                                    var color = attributes.getForegroundColor();
                                    result.push(String(iterator.getTokenType()) + '\t' + (color == null ? 'default' : String(color.getRGB())) + '\t' + String(field.getText().substring(iterator.getStart(), iterator.getEnd())).replace(/\n/g, '\\n'));
                                    iterator.advance();
                                }
                                result.join('\n');
                            """.trimIndent(), true)
                            Files.writeString(output.resolve("$prefix.tokens.tsv"), tokens)
                            if (!bundled && extension in listOf("kt", "java")) {
                                waitUntil("$name native field highlighting") {
                                    dialog.callJs<Boolean>(FIND_PREVIEW + """
                                        var editor = field.getEditor();
                                        var highlights = editor.getFilteredDocumentMarkupModel().getAllHighlighters();
                                        var isHighlighted = false;
                                        for (var i=0;i<highlights.length;i++) {
                                            var h = highlights[i];
                                            var key = h.getTextAttributesKey();
                                            if (key != null && String(key.getExternalName()) == '${if (extension == "kt") "KOTLIN_INSTANCE_PROPERTY" else "INSTANCE_FIELD_ATTRIBUTES"}') isHighlighted = true;
                                        }
                                        isHighlighted;
                                    """.trimIndent(), true)
                                }
                            }
                            if (bundled) {
                                assertTrue("$prefix must classify Root", tokens.lineSequence().any { it.startsWith("TYPE_NAME\t") && it.endsWith("\tRoot") })
                                assertTrue("$prefix must classify id", tokens.lineSequence().any { it.startsWith("TYPE_FIELD\t") && it.substringAfterLast('\t').equals("id", true) })
                                assertTrue("$prefix must classify nested fields", tokens.lineSequence().any { it.startsWith("TYPE_FIELD\t") && it.substringAfterLast('\t').equals("enabled", true) })
                            }
                            Thread.sleep(800) // Give native annotators a chance to paint the visible viewer.
                            ImageIO.write(dialog.getScreenshot(true), "png", output.resolve("$prefix.png").toFile())
                            assertLiveFieldColor(dialog, prefix, if (!bundled && extension == "kt") "KOTLIN_INSTANCE_PROPERTY"
                                else if (!bundled && extension == "java") "INSTANCE_FIELD_ATTRIBUTES" else "DEFAULT_INSTANCE_FIELD")
                            assertTypeToJsonTab(dialog, prefix, bundled)
                            results += "${if (bundled) "bundled" else "installed"},$scheme,$name,passed,passed,passed"
                        }
                    } catch (failure: Throwable) {
                        ImageIO.write(dialog.getScreenshot(true), "png", output.resolve("failure.png").toFile())
                        Files.writeString(output.resolve("failure-components.txt"), dialog.callJs<String>("""
                            var rows = [];
                            function collect(node) {
                                rows.push(String(node.getClass().getName()) + ' showing=' + node.isShowing() + ' ' + String(node));
                                if (typeof node.getComponents == 'function') { var children = node.getComponents(); for (var i=0;i<children.length;i++) collect(children[i]); }
                            }
                            collect(component); rows.join('\n');
                        """.trimIndent(), true))
                        throw failure
                    } finally {
                        robot.find<ComponentFixture>(byXpath("//div[@text='Cancel' and @class='JButton']")).runJs("component.doClick();", true)
                    }
                }
            }
        } finally {
            Files.writeString(output.resolve("results.csv"), results.joinToString("\n"))
            robot.runJs("""
                var colors = com.intellij.openapi.editor.colors.EditorColorsManager.getInstance();
                colors.setGlobalScheme(global.get('originalScheme'));
            """.trimIndent(), true)
            runWriteAction("""
                var entries = global.get('originalTypes').entrySet().iterator();
                while (entries.hasNext()) { var entry = entries.next(); com.intellij.openapi.fileTypes.FileTypeManager.getInstance().associateExtension(entry.getValue(), entry.getKey()); }
            """.trimIndent())
        }
    }

    private fun openConversionDialog() {
        val sample = Path.of("build/ui-test-project/sample.json").toAbsolutePath().toString().replace("\\", "\\\\").replace("'", "\\'")
        robot.runJs("""
            var project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            var file = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByPath('$sample');
            var editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openTextEditor(new com.intellij.openapi.fileEditor.OpenFileDescriptor(project, file), true);
            var context = new com.intellij.openapi.actionSystem.DataContext({ getData: function(id) {
                if (id == 'project') return project;
                if (id == 'editor') return editor;
                return null;
            }});
            var event = com.intellij.openapi.actionSystem.AnActionEvent.createFromDataContext('UI_TEST', null, context);
            var action = com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction('com.livteam.jsoninja.action.TypeConversionAction');
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(new java.lang.Runnable({run: function() { action.actionPerformed(event); }}));
        """.trimIndent(), true)
    }

    private fun assertLiveFieldColor(dialog: ComponentFixture, prefix: String, keyName: String) {
        val bounds = dialog.callJs<String>(FIND_PREVIEW + """
            var editor = field.getEditor();
            var start = -1;
            var end = -1;
            if ('$keyName' == 'DEFAULT_INSTANCE_FIELD') {
                var iterator = editor.getHighlighter().createIterator(0);
                while (!iterator.atEnd()) {
                    if (String(iterator.getTokenType()) == 'TYPE_FIELD') { start=iterator.getStart(); end=iterator.getEnd(); break; }
                    iterator.advance();
                }
            } else {
                var highlights = editor.getFilteredDocumentMarkupModel().getAllHighlighters();
                for (var i=0;i<highlights.length;i++) {
                    var h = highlights[i];
                    if (h.getTextAttributesKey() != null && String(h.getTextAttributesKey().getExternalName()) == '$keyName') {
                        if (start < 0 || h.getStartOffset() < start) { start=h.getStartOffset(); end=h.getEndOffset(); }
                    }
                }
            }
            if (start < 0) throw new Error('No field color target');
            editor.getScrollingModel().disableAnimation();
            editor.getScrollingModel().scrollTo(editor.offsetToLogicalPosition(start), com.intellij.openapi.editor.ScrollType.CENTER);
            var point = javax.swing.SwingUtilities.convertPoint(editor.getContentComponent(), editor.offsetToXY(start), component);
            var last = javax.swing.SwingUtilities.convertPoint(editor.getContentComponent(), editor.offsetToXY(end), component);
            global.put('customEditor', editor);
            global.put('customDocument', editor.getDocument());
            if (String(editor.getColorsScheme().getEditorFontName()) != 'Monospaced') throw new Error('Editor font did not follow the scheme');
            var colors = com.intellij.openapi.editor.colors.EditorColorsManager.getInstance();
            global.put('schemeBeforeCustom', colors.getGlobalScheme());
            var scheme = colors.getGlobalScheme().clone();
            scheme.setName('JSONinja UI test colors');
            var key = com.intellij.openapi.editor.colors.TextAttributesKey.find('$keyName');
            if (key == null) throw new Error('Missing IDE color key: $keyName');
            scheme.setAttributes(key, new com.intellij.openapi.editor.markup.TextAttributes(new java.awt.Color(0x11ADDD), null, null, null, 0));
            colors.setGlobalScheme(scheme);
            [point.x, point.y, last.x-point.x, editor.getLineHeight()].join(',');
        """.trimIndent(), true).split(',').map(String::toInt)
        try {
            waitUntil("$prefix live scheme") {
                dialog.callJs<Boolean>(FIND_PREVIEW + """
                    var editor = field.getEditor();
                    if (!editor.equals(global.get('customEditor')) || !editor.getDocument().equals(global.get('customDocument'))) throw new Error('Scheme change replaced the editor or document');
                    if (String(editor.getColorsScheme().getEditorFontName()) != 'Monospaced' || String(com.intellij.openapi.editor.colors.EditorColorsManager.getInstance().getGlobalScheme().getEditorFontName()) != 'Monospaced') throw new Error('Scheme change overwrote the user font');
                    editor.getColorsScheme().getAttributes(com.intellij.openapi.editor.colors.TextAttributesKey.find('$keyName')).getForegroundColor().equals(new java.awt.Color(0x11ADDD));
                """.trimIndent(), true)
            }
            val image = dialog.getScreenshot(true)
            ImageIO.write(image, "png", output.resolve("$prefix-custom.png").toFile())
            var hasFieldColor = false
            for (y in bounds[1].coerceAtLeast(0) until (bounds[1] + bounds[3]).coerceAtMost(image.height)) {
                for (x in bounds[0].coerceAtLeast(0) until (bounds[0] + bounds[2]).coerceAtMost(image.width)) {
                    val pixel = Color(image.getRGB(x, y))
                    if (kotlin.math.abs(pixel.red - 17) < 20 && kotlin.math.abs(pixel.green - 173) < 20 && kotlin.math.abs(pixel.blue - 221) < 20) hasFieldColor = true
                }
            }
            assertTrue("$prefix did not paint the user's field color", hasFieldColor)
        } finally {
            robot.runJs("com.intellij.openapi.editor.colors.EditorColorsManager.getInstance().setGlobalScheme(global.get('schemeBeforeCustom'));", true)
        }
    }

    private fun assertTypeToJsonTab(dialog: ComponentFixture, prefix: String, bundled: Boolean) {
        dialog.runJs(FIND_PREVIEW + """
            global.put('generatedType', field.getText());
            find(component, 'JBTabbedPane').setSelectedIndex(1);
            var input = find(component, 'CodeInputPanel');
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(new java.lang.Runnable({run: function() {
                input.setText(global.get('generatedType'));
            }}), com.intellij.openapi.application.ModalityState.stateForComponent(component));
        """.trimIndent(), true)
        try {
            waitUntil("$prefix Type to JSON") {
                dialog.callJs<Boolean>(FIND_PREVIEW + "var insert = find(component, 'JButton', 'Insert to Editor'); field != null && insert != null && insert.isEnabled() && String(field.getText()).trim().charAt(0) == '{';", true)
            }
            val inputTokens = dialog.callJs<String>(FIND_PREVIEW + """
                var input = find(component, 'CodeInputPanel');
                var inputField = find(input, 'EditorTextField');
                var editor = inputField.getEditor();
                if (editor.isViewer() || !editor.getContentComponent().isShowing()) throw new Error('Type input is not editable and visible');
                if (!inputField.getText().equals(global.get('generatedType'))) throw new Error('Type input was changed');
                editor.getScrollingModel().disableAnimation();
                editor.getScrollingModel().scrollVertically(0);
                var iterator = editor.getHighlighter().createIterator(0);
                var rows = [];
                while (!iterator.atEnd()) {
                    rows.push(String(iterator.getTokenType()) + '\t' + String(inputField.getText().substring(iterator.getStart(), iterator.getEnd())).replace(/\n/g, '\\n'));
                    iterator.advance();
                }
                rows.join('\n');
            """.trimIndent(), true)
            if (bundled) assertTrue("$prefix type input fields", inputTokens.lineSequence().any { it.startsWith("TYPE_FIELD\t") && it.substringAfterLast('\t').equals("id", true) })
            Files.writeString(output.resolve("$prefix-input.tokens.tsv"), inputTokens)
            ImageIO.write(dialog.getScreenshot(true), "png", output.resolve("$prefix-input.png").toFile())
        } finally {
            dialog.runJs(FIND_PREVIEW + "find(component, 'JBTabbedPane').setSelectedIndex(0);", true)
        }
    }

    private fun forceBundledHighlighters() {
        val extensions = LANGUAGES.values.joinToString(",") { "'$it'" }
        runWriteAction("""
                var types = com.intellij.openapi.fileTypes.FileTypeManager.getInstance();
                var extensions = [$extensions];
                for (var i=0; i<extensions.length; i++) {
                    var extension = extensions[i];
                    global.get('originalTypes').put(extension, types.getFileTypeByExtension(extension));
                    types.associateExtension(com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE, extension);
                }
        """.trimIndent())
    }

    private fun runWriteAction(script: String) {
        robot.runJs("""
            var application = com.intellij.openapi.application.ApplicationManager.getApplication();
            application.invokeAndWait(new java.lang.Runnable({run: function() {
                application.runWriteAction(new java.lang.Runnable({run: function() { $script }}));
            }}), com.intellij.openapi.application.ModalityState.nonModal());
        """.trimIndent())
    }

    private fun waitUntil(description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        fail("Timed out waiting for $description")
    }

    private companion object {
        val LANGUAGES = linkedMapOf("KOTLIN" to "kt", "JAVA" to "java", "TYPESCRIPT" to "ts", "GO" to "go", "C" to "c", "CPP" to "cpp", "CSHARP" to "cs", "PYTHON" to "py", "RUST" to "rs", "SCALA" to "scala", "JSDOC" to "js")
        val FIND_PREVIEW = """
            function find(node, simpleName, text) {
                if (node.isShowing() && (!text || typeof node.getText == 'function' && String(node.getText()) == text) && (String(node.getClass().getSimpleName()) == simpleName || simpleName == 'EditorTextField' && typeof node.getEditor == 'function' && typeof node.getDocument == 'function')) return node;
                if (typeof node.getComponents == 'function') {
                    var children = node.getComponents();
                    for (var j=0; j<children.length; j++) { var found = find(children[j], simpleName, text); if (found != null) return found; }
                }
                return null;
            }
            var panel = find(component, 'CodePreviewPanel');
            var field = panel == null ? null : find(panel, 'EditorTextField');
        """.trimIndent() + "\n"
    }
}
