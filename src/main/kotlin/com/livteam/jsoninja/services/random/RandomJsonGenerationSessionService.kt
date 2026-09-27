package com.livteam.jsoninja.services.random

import com.intellij.openapi.components.Service
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import kotlin.random.Random

@Service(Service.Level.PROJECT)
class RandomJsonGenerationSessionService {
    private var lastConfig: JsonGenerationConfig? = null

    fun createInitialConfig(): JsonGenerationConfig = lastConfig ?: JsonGenerationConfig()

    fun prepareGenerationConfig(config: JsonGenerationConfig): JsonGenerationConfig = config.copy(
        randomStructureSeed = if (config.shouldKeepRandomSeeds) {
            config.randomStructureSeed ?: nextSeed(null)
        } else {
            nextSeed(config.randomStructureSeed)
        },
        randomValueSeed = if (config.shouldKeepRandomSeeds) {
            config.randomValueSeed ?: nextSeed(null)
        } else {
            nextSeed(config.randomValueSeed)
        },
    )

    fun remember(config: JsonGenerationConfig) {
        lastConfig = config
    }

    private fun nextSeed(previous: Long?): Long {
        var seed: Long
        do {
            seed = Random.nextLong()
        } while (seed == previous)
        return seed
    }
}
