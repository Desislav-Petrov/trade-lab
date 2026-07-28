package org.dpp.tradelab.user.model

enum class FeedType {
    SYNTHETIC,
    REAL;

    companion object {
        fun getDefault(): FeedType = SYNTHETIC
    }
}
