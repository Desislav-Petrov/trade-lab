package org.dpp.tradelab.marketdata.messaging

import io.kotest.core.spec.style.FunSpec
import org.dpp.tradelab.marketdata.service.MarketDataFeedService
import org.dpp.tradelab.user.messaging.UserSettingsChangedEvent
import org.dpp.tradelab.user.model.FeedType
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.UUID

class UserEventListenerTest : FunSpec({

    val marketDataFeedService = mock<MarketDataFeedService>()
    val listener = UserEventListener(marketDataFeedService)

    test("onUserSettingsChanged_event_delegatesToService") {
        val event = UserSettingsChangedEvent(
            userId = UUID.randomUUID(),
            feedType = FeedType.REAL,
            updatedAt = Instant.now()
        )

        listener.onUserSettingsChanged(event)

        verify(marketDataFeedService).handleUserSettingsChanged(event)
    }
})
