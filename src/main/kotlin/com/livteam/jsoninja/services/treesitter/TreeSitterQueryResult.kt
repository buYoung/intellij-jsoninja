package com.livteam.jsoninja.services.treesitter

data class TreeSitterQueryResult(
    val captures: List<TreeSitterCapture> = emptyList(),
    val error: String? = null,
)

data class TreeSitterCapture(
    val name: String = "",
    val text: String = "",
    val startRow: Int = 0,
    val startCol: Int = 0,
    val endRow: Int = 0,
    val endCol: Int = 0,
)
