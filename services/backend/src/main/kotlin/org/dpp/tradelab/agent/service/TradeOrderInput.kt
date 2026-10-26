package org.dpp.tradelab.agent.service

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class TradeOrderInput @JsonCreator constructor(
    @JsonProperty("ticker") val ticker: String,
    @JsonProperty("side") val side: String,
    @JsonProperty("quantity") val quantity: String
)
