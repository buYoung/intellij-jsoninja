package com.livteam.jsoninja.diff

import com.intellij.openapi.util.Key

/**
 * Shared keys for identifying JSONinja-specific diff requests.
 */
object JsonDiffKeys {
    val JSON_DIFF_REQUEST_MARKER: Key<Boolean> = Key.create("JSONINJA_DIFF_REQUEST_MARKER")
    val JSON_DIFF_SORT_KEYS: Key<Boolean> = Key.create("JSONINJA_DIFF_SORT_KEYS")
    val JSON_DIFF_CHANGE_GUARD: Key<Boolean> = Key.create("JSONINJA_DIFF_CHANGE_GUARD")
}
