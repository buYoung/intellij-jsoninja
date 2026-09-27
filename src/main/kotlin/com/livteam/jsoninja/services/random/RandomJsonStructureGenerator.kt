package com.livteam.jsoninja.services.random

import kotlin.random.Random

internal class RandomJsonStructureGenerator(
    private val random: Random,
    private val maxDepth: Int,
    private val maxObjectProperties: Int,
    private val checkCancellation: () -> Unit,
) {
    private var nodeCount = 0

    fun generateObject(
        propertyCount: Int,
        depth: Int = 1,
        domain: RandomJsonDomain = RandomJsonDomain.entries.random(random),
    ): RandomJsonShape.ObjectShape {
        checkCancellation()
        val remaining = RandomJsonKeyCatalog.byCategory.mapValues { (_, definitions) -> definitions.toMutableList() }
        val fields = linkedMapOf<String, RandomJsonShape>()
        repeat(propertyCount) {
            checkCancellation()
            val available = remaining.filter { (category, definitions) ->
                definitions.isNotEmpty() && category.containerLevels <= maxDepth - depth &&
                    (category.containerLevels == 0 || nodeCount < MAX_SHAPE_NODES)
            }
            val category = chooseCategory(available.keys.toList(), domain, depth)
            val definition = remaining.getValue(category).random(random)
            // Aliases such as firstName/givenName compete for one semantic field in this object.
            remaining.values.forEach { definitions ->
                definitions.removeAll { it.semanticGroup == definition.semanticGroup }
            }
            fields[definition.key] = generateShape(definition, depth, domain)
        }
        return RandomJsonShape.ObjectShape(fields)
    }

    private fun chooseCategory(
        available: List<RandomJsonKeyCategory>,
        domain: RandomJsonDomain,
        depth: Int,
    ): RandomJsonKeyCategory {
        val containers = available.filter { it.containerLevels > 0 }
        val scalars = available.filter { it.containerLevels == 0 }
        val structureProbability = 0.4 * (1.0 - depth.toDouble() / maxDepth)
        if (containers.isNotEmpty() && random.nextDouble() < structureProbability) return containers.random(random)
        val candidates = scalars.ifEmpty { containers }
        val preferred = candidates.filter { it in domain.preferredCategories }
        // Category selection prevents large key families from dominating every record.
        return if (preferred.isNotEmpty() && random.nextDouble() < 0.75) preferred.random(random) else candidates.random(random)
    }

    private fun generateShape(
        definition: RandomJsonKeyDefinition,
        depth: Int,
        domain: RandomJsonDomain,
    ): RandomJsonShape {
        checkCancellation()
        nodeCount++
        val childDomain = definition.childDomain ?: domain
        return when (definition.category) {
            RandomJsonKeyCategory.OBJECT -> generateObject(random.nextInt(1, maxObjectProperties + 1), depth + 1, childDomain)
            RandomJsonKeyCategory.VALUE_ARRAY -> RandomJsonShape.ArrayShape(
                RandomJsonShape.PrimitiveShape(requireNotNull(definition.rule))
            )
            RandomJsonKeyCategory.OBJECT_ARRAY -> RandomJsonShape.ArrayShape(
                generateObject(random.nextInt(1, maxObjectProperties + 1), depth + 2, childDomain)
            )
            else -> RandomJsonShape.PrimitiveShape(requireNotNull(definition.rule))
        }
    }

    private companion object {
        const val MAX_SHAPE_NODES = 2_000
    }
}
