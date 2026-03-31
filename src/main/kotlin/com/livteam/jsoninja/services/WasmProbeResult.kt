package com.livteam.jsoninja.services

data class WasmProbeResult(
    val exportName: String,
    val leftValue: Int,
    val rightValue: Int,
    val resultValue: Int,
    val moduleByteCount: Int,
)
