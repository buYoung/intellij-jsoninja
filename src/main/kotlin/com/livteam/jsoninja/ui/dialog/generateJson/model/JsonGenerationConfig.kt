package com.livteam.jsoninja.ui.dialog.generateJson.model

// 다이얼로그 설정값을 담는 데이터 클래스
data class JsonGenerationConfig(
    val jsonRootType: JsonRootType = JsonRootType.OBJECT,
    val objectPropertyCount: Int = 5, // 루트=객체 일 때 기본값
    val arrayElementCount: Int = 5, // 루트=배열 일 때 기본값
    val propertiesPerObjectInArray: Int = 3, // 배열 내 객체의 속성 개수 기본값
    val maxDepth: Int = 3,
    val isJson5: Boolean = false // JSON5 생성 여부
)
