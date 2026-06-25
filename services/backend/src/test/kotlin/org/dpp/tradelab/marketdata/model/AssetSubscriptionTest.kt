package org.dpp.tradelab.marketdata.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.UUID

class AssetSubscriptionTest : StringSpec({

    "isNew_freshInstance_returnsTrue" {
        val subscription = AssetSubscription(
            subscriptionId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            ticker = "AAPL",
            companyName = "Apple Inc."
        )
        subscription.isNew() shouldBe true
    }

    "getId_returnsSubscriptionId" {
        val id = UUID.randomUUID()
        val subscription = AssetSubscription(
            subscriptionId = id,
            userId = UUID.randomUUID(),
            ticker = "MSFT",
            companyName = "Microsoft Corporation"
        )
        subscription.getId() shouldBe id
    }

    "equals_sameId_returnsTrue" {
        val id = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val a = AssetSubscription(subscriptionId = id, userId = userId, ticker = "AAPL", companyName = "Apple Inc.")
        val b = AssetSubscription(subscriptionId = id, userId = userId, ticker = "AAPL", companyName = "Apple Inc.")
        a shouldBe b
    }

    "equals_differentId_returnsFalse" {
        val userId = UUID.randomUUID()
        val a = AssetSubscription(subscriptionId = UUID.randomUUID(), userId = userId, ticker = "AAPL", companyName = "Apple Inc.")
        val b = AssetSubscription(subscriptionId = UUID.randomUUID(), userId = userId, ticker = "AAPL", companyName = "Apple Inc.")
        a shouldNotBe b
    }

    "hashCode_sameId_equal" {
        val id = UUID.randomUUID()
        val a = AssetSubscription(subscriptionId = id, userId = UUID.randomUUID(), ticker = "AAPL", companyName = "Apple Inc.")
        val b = AssetSubscription(subscriptionId = id, userId = UUID.randomUUID(), ticker = "AAPL", companyName = "Apple Inc.")
        a.hashCode() shouldBe b.hashCode()
    }

    "toString_containsRelevantFields" {
        val id = UUID.randomUUID()
        val subscription = AssetSubscription(
            subscriptionId = id,
            userId = UUID.randomUUID(),
            ticker = "NVDA",
            companyName = "NVIDIA Corporation"
        )
        val str = subscription.toString()
        str.contains("NVDA") shouldBe true
        str.contains("NVIDIA Corporation") shouldBe true
        str.contains(id.toString()) shouldBe true
    }
})
