package com.livteam.jsoninja.ui.dialog.generateJson

import com.intellij.openapi.components.Service
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode

@Service(Service.Level.PROJECT)
class GenerateJsonSessionService {
    var lastGenerationMode: JsonGenerationMode = JsonGenerationMode.RANDOM
        private set

    var lastSchemaConfig: JsonGenerationConfig = JsonGenerationConfig(generationMode = JsonGenerationMode.SCHEMA)
        private set

    var lastSchemaUrlText: String = ""
        private set

    fun rememberGenerationMode(mode: JsonGenerationMode) {
        lastGenerationMode = mode
    }

    fun rememberSchemaState(config: JsonGenerationConfig, schemaUrlText: String) {
        lastSchemaConfig = config.copy(generationMode = JsonGenerationMode.SCHEMA)
        lastSchemaUrlText = schemaUrlText
    }
}
