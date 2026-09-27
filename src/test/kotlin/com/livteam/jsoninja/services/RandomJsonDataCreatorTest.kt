package com.livteam.jsoninja.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.RandomJsonDataCreator
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonRootType
import com.livteam.jsoninja.services.random.RandomJsonGenerationSessionService
import com.livteam.jsoninja.services.random.RandomJsonKeyCatalog
import com.livteam.jsoninja.services.random.RandomJsonKeyCategory
import com.livteam.jsoninja.services.random.RandomJsonValueRule
import com.livteam.jsoninja.services.random.RandomJsonShape
import com.livteam.jsoninja.services.random.RandomJsonValueGenerator
import com.livteam.jsoninja.services.random.RandomJsonValueKind
import com.livteam.jsoninja.ui.dialog.generateJson.random.GenerateRandomJsonTabPresenter
import com.livteam.jsoninja.ui.dialog.generateJson.random.GenerateRandomJsonTabView
import kotlinx.coroutines.CancellationException
import net.datafaker.Faker
import java.awt.Component
import java.awt.Container
import java.math.BigDecimal
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

class RandomJsonDataCreatorTest : BasePlatformTestCase() {
    private lateinit var randomJsonDataCreator: RandomJsonDataCreator
    private lateinit var objectMapper: ObjectMapper

    override fun setUp() {
        super.setUp()
        randomJsonDataCreator = RandomJsonDataCreator()
        objectMapper = service<JsonObjectMapperService>().objectMapper
    }

    fun testGenerateConfiguredJsonString_Object() {
        // Test generating a JSON object
        val config = JsonGenerationConfig(
            jsonRootType = JsonRootType.OBJECT,
            objectPropertyCount = 5,
            arrayElementCount = 0,
            propertiesPerObjectInArray = 0,
            maxDepth = 2
        )

        val jsonString = randomJsonDataCreator.generateConfiguredJsonString(config)

        // Verify the result is valid JSON
        val jsonNode = objectMapper.readTree(jsonString)
        assertTrue("Result should be a JSON object", jsonNode.isObject)

        // Verify the object has the expected number of properties
        assertEquals("Object should have 5 properties", 5, jsonNode.size())
    }

    fun testGenerateConfiguredJsonString_ArrayOfObjects() {
        // Test generating an array of JSON objects
        val config = JsonGenerationConfig(
            jsonRootType = JsonRootType.ARRAY_OF_OBJECTS,
            objectPropertyCount = 0,
            arrayElementCount = 3,
            propertiesPerObjectInArray = 4,
            maxDepth = 2
        )

        val jsonString = randomJsonDataCreator.generateConfiguredJsonString(config)

        // Verify the result is valid JSON
        val jsonNode = objectMapper.readTree(jsonString)
        assertTrue("Result should be a JSON array", jsonNode.isArray)

        // Verify the array has the expected number of elements
        assertEquals("Array should have 3 elements", 3, jsonNode.size())

        // Verify each element is an object with the expected number of properties
        for (i in 0 until jsonNode.size()) {
            val element = jsonNode.get(i)
            assertTrue("Array element should be an object", element.isObject)
            assertEquals("Object in array should have 4 properties", 4, element.size())
        }
    }

    fun testGenerateConfiguredJsonString_MaxDepth() {
        // Test that the max depth is respected
        val config = JsonGenerationConfig(
            jsonRootType = JsonRootType.OBJECT,
            objectPropertyCount = 3,
            arrayElementCount = 0,
            propertiesPerObjectInArray = 0,
            maxDepth = 1
        )

        val jsonString = randomJsonDataCreator.generateConfiguredJsonString(config)

        // Verify the result is valid JSON
        val jsonNode = objectMapper.readTree(jsonString)

        // Check that no property is an object or array (depth limit)
        jsonNode.fields().forEach { (_, value) ->
            assertFalse("Properties should not be objects at max depth", value.isObject && value.size() > 0)
            assertFalse("Properties should not be arrays at max depth", value.isArray && value.size() > 0)
        }
    }

    fun testGenerateConfiguredJsonString_PrettyPrint() {
        // Test pretty printing
        val config = JsonGenerationConfig(
            jsonRootType = JsonRootType.OBJECT,
            objectPropertyCount = 2,
            arrayElementCount = 0,
            propertiesPerObjectInArray = 0,
            maxDepth = 2
        )

        // With pretty print
        val prettyJson = randomJsonDataCreator.generateConfiguredJsonString(config, prettyPrint = true)
        assertTrue("Pretty JSON should contain newlines", prettyJson.contains("\n"))
        assertTrue("Pretty JSON should contain indentation", prettyJson.contains("  "))

        // Without pretty print
        val uglyJson = randomJsonDataCreator.generateConfiguredJsonString(config, prettyPrint = false)
        assertFalse("Ugly JSON should not contain newlines", uglyJson.contains("\n"))

        // Both should be valid JSON
        val prettyNode = objectMapper.readTree(prettyJson)
        val uglyNode = objectMapper.readTree(uglyJson)
        assertTrue("Pretty JSON should be a valid JSON object", prettyNode.isObject)
        assertTrue("Ugly JSON should be a valid JSON object", uglyNode.isObject)
    }

    fun testArrayObjectsShareKeysAndNestedTypesWithoutNulls() {
        var hasNestedArray = false
        for (seed in 1L..8L) {
            val data = generateArray(seed, 42, maxDepth = 5)
            val expectedShape = shapeOf(data.first())
            data.forEach { record -> assertEquals(expectedShape, shapeOf(record)) }
            assertNoNulls(data)
            hasNestedArray = hasNestedArray || data.any { record -> containsArray(record) }
        }
        assertTrue("The fixtures must exercise nested arrays", hasNestedArray)
    }

    fun testNewStructureSeedsRandomizeKeys() {
        val keys = (1L..8L).map { seed -> generateArray(seed, 42).first().fieldNames().asSequence().toList() }
        assertTrue("A new structure must not reuse a fixed field preset", keys.toSet().size > 1)
    }

    fun testNewValueSeedPreservesStructureAndChangesValues() {
        val first = generateArray(42, 100)
        val next = generateArray(42, 200)
        assertEquals(shapeOf(first), shapeOf(next))
        assertFalse("Changing the value seed must change the generated data", first == next)
        assertTrue("Rows must have independently generated values", first.toList().distinct().size > 1)
    }

    fun testSeedsReplayAcrossGeneratorInstancesAndJson5() {
        for (isJson5 in listOf(false, true)) {
            val config = arrayConfig(42, 100).copy(isJson5 = isJson5)
            val first = randomJsonDataCreator.generateConfiguredJsonString(config)
            randomJsonDataCreator.generateConfiguredJsonString(arrayConfig(99, 999))
            val replay = RandomJsonDataCreator().generateConfiguredJsonString(config)
            assertEquals("All random values and JSON5 comments must replay", first, replay)
            assertEquals(20, objectMapper.readTree(replay).size())
        }
    }

    fun testArrayAtMinimumDepthKeepsRequestedPropertyCount() {
        val data = generateArray(42, 100, maxDepth = 1)
        assertEquals(20, data.size())
        data.forEach { record ->
            assertEquals(12, record.size())
            record.forEach { value -> assertTrue("Depth one must contain only scalar values", value.isValueNode) }
        }
    }

    fun testLargePropertyCountIsExact() {
        val config = arrayConfig(42, 100).copy(arrayElementCount = 3, propertiesPerObjectInArray = 100, maxDepth = 1)
        val data = objectMapper.readTree(randomJsonDataCreator.generateConfiguredJsonString(config))
        data.forEach { record -> assertEquals(100, record.size()) }
    }

    fun testIdValuesAreRandomAndUniqueWithinArray() {
        val shape = RandomJsonShape.ObjectShape(linkedMapOf("id" to RandomJsonShape.PrimitiveShape(RandomJsonValueKind.UNIQUE_ID)))
        val generator = RandomJsonValueGenerator(Random(42), Faker(), 5, 0.0) {}
        val data = objectMapper.valueToTree<JsonNode>(generator.generateArray(shape, 100))
        val ids = data.map { it["id"].asInt() }
        assertEquals(100, ids.toSet().size)
        assertTrue("IDs must remain random rather than a row counter", ids.zipWithNext().any { (a, b) -> b != a + 1 })
    }

    fun testIdUniquenessIsScopedToEachNestedArray() {
        val repeatedRandom = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
        }
        val item = RandomJsonShape.ObjectShape(linkedMapOf("id" to RandomJsonShape.PrimitiveShape(RandomJsonValueKind.UNIQUE_ID)))
        val group = RandomJsonShape.ObjectShape(linkedMapOf("items" to RandomJsonShape.ArrayShape(item)))
        val generator = RandomJsonValueGenerator(repeatedRandom, Faker(), 2, 0.0) {}
        val data = objectMapper.valueToTree<JsonNode>(generator.generateArray(group, 2))
        assertEquals("Independent item arrays may reuse the same ID", data[0]["items"][0]["id"], data[1]["items"][0]["id"])
    }

    fun testGenerationPropagatesCancellation() {
        val cancellation = CancellationException("Stop generation")
        var checks = 0
        try {
            randomJsonDataCreator.generateConfiguredJsonString(arrayConfig(42, 100)) {
                checks++
                if (checks == 10) throw cancellation
            }
            fail("Generation should have been cancelled")
        } catch (exception: CancellationException) {
            assertSame(cancellation, exception)
        }
    }

    fun testDialogSeedsReachGeneratorAndSessionKeepsStructure() {
        val config = arrayConfig(42, 100).copy(shouldKeepRandomSeeds = true, isJson5 = true)
        val presenter = GenerateRandomJsonTabPresenter(config)
        try {
            presenter.getComponent()
            assertNull(presenter.validate())
            val dialogConfig = presenter.getConfig()
            assertEquals(42L, dialogConfig.randomStructureSeed)
            assertEquals(100L, dialogConfig.randomValueSeed)
            assertEquals(
                randomJsonDataCreator.generateConfiguredJsonString(config),
                randomJsonDataCreator.generateConfiguredJsonString(dialogConfig),
            )
            val session = RandomJsonGenerationSessionService()
            val prepared = session.prepareGenerationConfig(dialogConfig)
            session.remember(prepared)
            val reopened = session.createInitialConfig()
            assertEquals(dialogConfig.randomStructureSeed, reopened.randomStructureSeed)
            assertEquals(dialogConfig.propertiesPerObjectInArray, reopened.propertiesPerObjectInArray)
            assertEquals("Opening a dialog must preserve the last used value seed", dialogConfig.randomValueSeed, reopened.randomValueSeed)
            assertTrue(reopened.shouldKeepRandomSeeds)
            val reopenedPresenter = GenerateRandomJsonTabPresenter(reopened)
            try {
                val component = reopenedPresenter.getComponent()
                for (name in listOf("randomStructureSeed", "randomValueSeed")) {
                    val field = componentTree(component).filterIsInstance<JBTextField>().single { it.name == name }
                    assertTrue("Retained seeds must be visible when the dialog is reopened", isVisibleWithin(field, component))
                }
                val replay = session.prepareGenerationConfig(reopenedPresenter.getConfig())
                assertEquals(prepared, replay)
                assertEquals(
                    randomJsonDataCreator.generateConfiguredJsonString(prepared),
                    randomJsonDataCreator.generateConfiguredJsonString(replay),
                )
            } finally {
                reopenedPresenter.dispose()
            }
        } finally {
            presenter.dispose()
        }
    }

    fun testAutomaticSeedsRefreshOnlyWhenGenerating() {
        val session = RandomJsonGenerationSessionService()
        assertFalse(session.createInitialConfig().shouldKeepRandomSeeds)
        val previous = arrayConfig(42, 100)
        session.remember(previous)
        assertEquals(previous, session.createInitialConfig())
        assertEquals("Opening again must not consume or change seeds", previous, session.createInitialConfig())

        val presenter = GenerateRandomJsonTabPresenter(session.createInitialConfig())
        try {
            presenter.getComponent()
            val selected = presenter.getConfig()
            assertEquals(selected, presenter.getConfig())
            val generated = session.prepareGenerationConfig(selected)
            assertFalse(generated.shouldKeepRandomSeeds)
            assertFalse(selected.randomStructureSeed == generated.randomStructureSeed)
            assertFalse(selected.randomValueSeed == generated.randomValueSeed)
            assertFalse(
                randomJsonDataCreator.generateConfiguredJsonString(previous) ==
                    randomJsonDataCreator.generateConfiguredJsonString(generated)
            )
            assertEquals("Unapplied generation must not replace the remembered seeds", previous, session.createInitialConfig())
            session.remember(generated)
            assertEquals(generated, session.createInitialConfig())
        } finally {
            presenter.dispose()
        }
    }

    fun testDisablingSeedRetentionRestoresAutomaticGeneration() {
        val session = RandomJsonGenerationSessionService()
        val previous = arrayConfig(42, 100).copy(shouldKeepRandomSeeds = true)
        session.remember(previous)
        val presenter = GenerateRandomJsonTabPresenter(session.createInitialConfig())
        try {
            val component = presenter.getComponent()
            val toggle = componentTree(component).filterIsInstance<JBCheckBox>().single { it.name == "keepRandomSeeds" }
            assertTrue(toggle.isSelected)
            toggle.doClick()
            val generated = session.prepareGenerationConfig(presenter.getConfig())
            assertFalse(generated.shouldKeepRandomSeeds)
            assertFalse(previous.randomStructureSeed == generated.randomStructureSeed)
            assertFalse(previous.randomValueSeed == generated.randomValueSeed)
            session.remember(generated)
            assertFalse(session.createInitialConfig().shouldKeepRandomSeeds)
            val reopenedView = GenerateRandomJsonTabView(session.createInitialConfig())
            val reopenedComponent = reopenedView.component
            assertFalse("Automatic mode should reopen with Advanced collapsed", isVisibleWithin(reopenedView.getValueSeedField(), reopenedComponent))
        } finally {
            presenter.dispose()
        }
    }

    fun testAdvancedSeedOptionsStartCollapsedAndResizeWhenToggled() {
        val view = GenerateRandomJsonTabView(arrayConfig(42, 100))
        val component = view.component
        val collapsedHeight = component.preferredSize.height
        var layoutChanges = 0
        view.setOnAdvancedOptionsChanged { layoutChanges++ }
        assertFalse(isVisibleWithin(view.getStructureSeedField(), component))
        assertFalse(isVisibleWithin(view.getValueSeedField(), component))

        view.setAdvancedOptionsExpanded(true)
        assertTrue(isVisibleWithin(view.getStructureSeedField(), component))
        assertTrue(component.preferredSize.height > collapsedHeight)
        assertFalse(view.getStructureSeedField().isEnabled)
        assertFalse(view.getValueSeedField().isEnabled)
        val toggle = componentTree(component).filterIsInstance<JBCheckBox>().single { it.name == "keepRandomSeeds" }
        toggle.doClick()
        assertTrue(view.getStructureSeedField().isEnabled)
        assertTrue(view.getValueSeedField().isEnabled)

        view.setAdvancedOptionsExpanded(false)
        assertEquals(collapsedHeight, component.preferredSize.height)
        assertFalse(isVisibleWithin(view.getStructureSeedField(), component))
        assertEquals(2, layoutChanges)
        assertEquals("42", view.getStructureSeedText())
        assertEquals("100", view.getValueSeedText())
    }

    fun testSeedValidationOnlyAppliesToRetentionAndRevealsHiddenErrors() {
        val presenter = GenerateRandomJsonTabPresenter(arrayConfig(42, 100).copy(shouldKeepRandomSeeds = true))
        try {
            val component = presenter.getComponent()
            presenter.registerValidators(testRootDisposable)
            val seedField = componentTree(component).filterIsInstance<JBTextField>().single { it.name == "randomValueSeed" }
            val baseSummary = LocalizationBundle.message("dialog.generate.json.summary.array", 20, 12, "JSON")
            assertEquals(LocalizationBundle.message("dialog.generate.json.random.summary.kept", baseSummary), presenter.getSummary())
            seedField.text = "invalid-seed"
            assertNotNull(presenter.validate(shouldRevealSeedErrors = true))
            assertTrue("The invalid field must be revealed before reporting its error", isVisibleWithin(seedField, component))
            val toggle = componentTree(component).filterIsInstance<JBCheckBox>().single { it.name == "keepRandomSeeds" }
            toggle.doClick()
            assertNull("Automatic mode must ignore an unused seed input", presenter.validate())
            assertEquals(baseSummary, presenter.getSummary())
            val generated = RandomJsonGenerationSessionService().prepareGenerationConfig(presenter.getConfig())
            assertNotNull(generated.randomStructureSeed)
            assertNotNull(generated.randomValueSeed)
        } finally {
            presenter.dispose()
        }
    }

    private fun componentTree(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(componentTree(it)) }
    }

    private fun isVisibleWithin(component: Component, root: Component): Boolean {
        var current: Component? = component
        while (current != null) {
            if (!current.isVisible) return false
            if (current === root) return true
            current = current.parent
        }
        return false
    }

    fun testCatalogHasOneThousandUniqueKeysAcrossFortyFiveCategories() {
        val definitions = RandomJsonKeyCatalog.entries
        assertEquals(1_000, definitions.size)
        assertEquals(1_000, definitions.map { it.key.lowercase(Locale.ROOT) }.toSet().size)
        assertEquals(45, RandomJsonKeyCatalog.byCategory.size)
        assertEquals(RandomJsonKeyCategory.entries.toSet(), RandomJsonKeyCatalog.byCategory.keys)
        definitions.forEach { definition ->
            assertTrue("Invalid catalog key: ${definition.key}", definition.key.matches(Regex("[a-z][a-zA-Z0-9]*")))
            val isObject = definition.category == RandomJsonKeyCategory.OBJECT || definition.category == RandomJsonKeyCategory.OBJECT_ARRAY
            assertEquals("Missing or misplaced value rule: ${definition.key}", isObject, definition.rule == null)
        }
    }

    fun testAllGeneratedKeysComeFromCatalogAndAliasesDoNotCoexist() {
        for (seed in 1L..10L) {
            val config = arrayConfig(seed, seed + 100).copy(arrayElementCount = 3, propertiesPerObjectInArray = 100)
            val data = objectMapper.readTree(randomJsonDataCreator.generateConfiguredJsonString(config))
            assertCatalogKeys(data)
        }
    }

    fun testChangingFakerDoesNotChangeKeysOrStructure() {
        val config = arrayConfig(42, 100)
        val firstCreator = RandomJsonDataCreator(faker = Faker(Locale.ENGLISH, java.util.Random(1)))
        val secondCreator = RandomJsonDataCreator(faker = Faker(Locale.ENGLISH, java.util.Random(2)))
        val first = objectMapper.readTree(firstCreator.generateConfiguredJsonString(config))
        val second = objectMapper.readTree(secondCreator.generateConfiguredJsonString(config))
        assertEquals(shapeOf(first), shapeOf(second))
        assertFalse("Faker must still contribute random values", first == second)
    }

    fun testEveryCatalogValueRuleReachesJsonWithItsDeclaredTypeAndRange() {
        val definitions = RandomJsonKeyCatalog.entries.filter { it.rule != null }
        for ((index, batch) in definitions.chunked(50).withIndex()) {
            val fields = batch.associate { definition ->
                val scalar = RandomJsonShape.PrimitiveShape(requireNotNull(definition.rule))
                definition.key to if (definition.category == RandomJsonKeyCategory.VALUE_ARRAY) {
                    RandomJsonShape.ArrayShape(scalar)
                } else scalar
            }
            val generator = RandomJsonValueGenerator(
                Random(index), Faker(Locale.ENGLISH, java.util.Random(index.toLong())), 3, 0.0,
            ) {}
            val json = objectMapper.writeValueAsString(generator.generateArray(RandomJsonShape.ObjectShape(fields), 3))
            val records = objectMapper.readTree(json)
            for (record in records) {
                for (definition in batch) {
                    val value = record[definition.key]
                    val rule = requireNotNull(definition.rule)
                    if (definition.category == RandomJsonKeyCategory.VALUE_ARRAY) {
                        assertTrue(value.isArray)
                        value.forEach { assertValueRule(definition.key, rule, it) }
                    } else {
                        assertValueRule(definition.key, rule, value)
                    }
                }
            }
        }
    }

    private fun assertCatalogKeys(node: JsonNode) {
        if (node.isObject) {
            val groups = mutableSetOf<String>()
            node.fieldNames().forEachRemaining { key ->
                val definition = RandomJsonKeyCatalog.byKey[key]
                assertNotNull("Unknown generated key: $key", definition)
                assertTrue("Duplicate semantic field: $key", groups.add(requireNotNull(definition).semanticGroup))
                assertCatalogKeys(node[key])
            }
        } else if (node.isArray) {
            node.forEach(::assertCatalogKeys)
        }
    }

    private fun assertValueRule(key: String, rule: RandomJsonValueRule, value: JsonNode) {
        when (rule) {
            is RandomJsonValueRule.IntegerRange -> {
                assertTrue("$key must be an integer", value.isIntegralNumber)
                assertTrue("$key is outside its configured range", value.asLong() in rule.minimum..rule.maximum)
            }
            is RandomJsonValueRule.DecimalRange -> {
                assertTrue("$key must remain a JSON number", value.isNumber)
                val minimum = BigDecimal.valueOf(rule.minimumUnscaled, rule.scale)
                val maximum = BigDecimal.valueOf(rule.maximumUnscaled, rule.scale)
                assertTrue("$key is outside its configured range", value.decimalValue() >= minimum && value.decimalValue() <= maximum)
            }
            is RandomJsonValueRule.Choice -> {
                assertTrue("$key must be a string", value.isTextual)
                assertTrue("$key is not an allowed choice", value.asText() in rule.values)
            }
            is RandomJsonValueRule.Generated -> assertGeneratedFormat(key, rule.kind, value)
        }
    }

    private fun assertGeneratedFormat(key: String, kind: RandomJsonValueKind, value: JsonNode) {
        val numericKinds = setOf(RandomJsonValueKind.UNIQUE_ID, RandomJsonValueKind.INTEGER, RandomJsonValueKind.COUNT,
            RandomJsonValueKind.HTTP_STATUS, RandomJsonValueKind.EPOCH_SECONDS, RandomJsonValueKind.EPOCH_MILLIS)
        when {
            kind in numericKinds -> assertTrue("$key must be an integer", value.isIntegralNumber)
            kind == RandomJsonValueKind.BOOLEAN -> assertTrue("$key must be boolean", value.isBoolean)
            kind == RandomJsonValueKind.DECIMAL -> assertTrue("$key must be numeric", value.isNumber)
            else -> {
                assertTrue("$key must be a string", value.isTextual)
                assertTrue("$key must not be empty", value.asText().isNotBlank())
            }
        }
        val text = value.asText()
        when (kind) {
            RandomJsonValueKind.UUID -> UUID.fromString(text)
            RandomJsonValueKind.DATE, RandomJsonValueKind.BIRTH_DATE -> LocalDate.parse(text)
            RandomJsonValueKind.DATE_TIME -> Instant.parse(text)
            RandomJsonValueKind.TIME -> LocalTime.parse(text)
            RandomJsonValueKind.URL, RandomJsonValueKind.IMAGE_URL -> {
                val uri = URI(text)
                assertEquals("https", uri.scheme)
                assertNotNull("$key must contain a valid host", uri.host)
            }
            RandomJsonValueKind.EMAIL -> assertTrue(text.matches(Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")))
            RandomJsonValueKind.HEX_COLOR -> assertTrue(text.matches(Regex("#[0-9a-f]{6}")))
            RandomJsonValueKind.COUNTRY_CODE -> assertTrue("$key has an invalid country code: $text", text.matches(Regex("[A-Z]{2}")))
            RandomJsonValueKind.CURRENCY_CODE -> assertTrue("$key has an invalid currency code: $text", text.matches(Regex("[A-Z]{3}")))
            RandomJsonValueKind.HEX_16, RandomJsonValueKind.HEX_32, RandomJsonValueKind.HEX_40,
            RandomJsonValueKind.HEX_64, RandomJsonValueKind.HEX_128 -> {
                val length = kind.name.substringAfter("HEX_").toInt()
                assertTrue("$key has an invalid hash format", text.matches(Regex("[0-9a-f]{$length}")))
            }
            else -> Unit
        }
    }

    private fun arrayConfig(structureSeed: Long, valueSeed: Long, maxDepth: Int = 4) = JsonGenerationConfig(
        jsonRootType = JsonRootType.ARRAY_OF_OBJECTS,
        arrayElementCount = 20,
        propertiesPerObjectInArray = 12,
        maxDepth = maxDepth,
        randomStructureSeed = structureSeed,
        randomValueSeed = valueSeed,
    )

    private fun generateArray(structureSeed: Long, valueSeed: Long, maxDepth: Int = 4): JsonNode =
        objectMapper.readTree(randomJsonDataCreator.generateConfiguredJsonString(arrayConfig(structureSeed, valueSeed, maxDepth)))

    private fun shapeOf(node: JsonNode): String = when {
        node.isObject -> node.fieldNames().asSequence().joinToString(prefix = "{", postfix = "}") { key -> "$key:${shapeOf(node[key])}" }
        node.isArray -> {
            assertTrue("Normal generation must use nonempty arrays", node.size() > 0)
            val elementShape = shapeOf(node.first())
            node.forEach { assertEquals("Every nested array item must share its structure", elementShape, shapeOf(it)) }
            "[$elementShape]"
        }
        node.isNumber -> node.numberType().toString()
        else -> node.nodeType.toString()
    }

    private fun assertNoNulls(node: JsonNode) {
        assertFalse("Nulls are excluded by default", node.isNull)
        if (node.isContainerNode) node.forEach(::assertNoNulls)
    }

    private fun containsArray(node: JsonNode): Boolean =
        node.isArray || (node.isObject && node.any(::containsArray))
}
