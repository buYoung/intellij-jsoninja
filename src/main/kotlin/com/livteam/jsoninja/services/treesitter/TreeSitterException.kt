package com.livteam.jsoninja.services.treesitter

class TreeSitterException(
    message: String,
    val errorCode: Int? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
