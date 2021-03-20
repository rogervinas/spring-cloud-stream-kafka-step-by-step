package com.rogervinas.stream.shared

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

class MyEventPayload @JsonCreator constructor(
        @JsonProperty("string") val string: String,
        @JsonProperty("number") val number: Int
)