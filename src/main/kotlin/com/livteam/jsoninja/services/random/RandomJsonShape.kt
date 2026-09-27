package com.livteam.jsoninja.services.random

internal sealed interface RandomJsonShape {
    data class ObjectShape(val fields: Map<String, RandomJsonShape>) : RandomJsonShape
    data class ArrayShape(val element: RandomJsonShape) : RandomJsonShape
    data class PrimitiveShape(val rule: RandomJsonValueRule) : RandomJsonShape {
        constructor(kind: RandomJsonValueKind) : this(RandomJsonValueRule.Generated(kind))
    }
}
