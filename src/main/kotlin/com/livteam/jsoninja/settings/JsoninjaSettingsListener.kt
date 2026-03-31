package com.livteam.jsoninja.settings

import com.intellij.util.messages.Topic

interface JsoninjaSettingsListener {
    companion object {
        val TOPIC = Topic.create("Jsoninja Settings Changed", JsoninjaSettingsListener::class.java)
    }

    fun onSettingsChanged(settings: JsoninjaSettingsState)
}
