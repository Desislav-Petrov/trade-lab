package org.dpp.tradelab.ledger.model

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import java.util.UUID

class LedgerEntryTest : DescribeSpec({

    val entryId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    val accountId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    describe("LedgerEntry construction") {

        it("returns correct entryId") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("500.0000"),
                currency = "USD",
                description = "Top-up"
            )

            entry.entryId shouldBe entryId
        }

        it("getId returns the entryId (Persistable contract)") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("500.0000"),
                currency = "USD",
                description = "Top-up"
            )

            entry.id shouldBe entryId
        }

        it("isNew returns true by default") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("500.0000"),
                currency = "USD"
            )

            entry.isNew() shouldBe true
        }

        it("returns correct accountId") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.DEBIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("100.0000"),
                currency = "GBP"
            )

            entry.accountId shouldBe accountId
        }

        it("returns correct type") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.DEBIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("100.0000"),
                currency = "GBP"
            )

            entry.type shouldBe EntryType.DEBIT
        }

        it("returns correct assetType") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("250.0000"),
                currency = "EUR"
            )

            entry.assetType shouldBe AssetType.CASH
        }

        it("returns correct amount") {
            val amount = BigDecimal("1234.5678")
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = amount,
                currency = "USD"
            )

            entry.amount shouldBe amount
        }

        it("returns correct currency") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("500.0000"),
                currency = "USD",
                description = "Top-up"
            )

            entry.currency shouldBe "USD"
        }

        it("returns correct description when provided") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("500.0000"),
                currency = "USD",
                description = "Top-up"
            )

            entry.description shouldBe "Top-up"
        }

        it("description defaults to null when omitted") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("500.0000"),
                currency = "USD"
            )

            entry.description shouldBe null
        }

        it("createdAt defaults to null before JPA persistence") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("500.0000"),
                currency = "USD"
            )

            entry.createdAt shouldBe null
        }

        it("ticker defaults to null when omitted") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("500.0000"),
                currency = "USD"
            )

            entry.ticker shouldBe null
        }

        it("returns correct ticker when provided") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.DEBIT,
                assetType = AssetType.STOCK_BUY,
                amount = BigDecimal("1500.0000"),
                currency = "USD",
                ticker = "AAPL",
                shares = BigDecimal("10.0000")
            )

            entry.ticker shouldBe "AAPL"
        }

        it("shares defaults to null when omitted") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("500.0000"),
                currency = "USD"
            )

            entry.shares shouldBe null
        }

        it("returns correct shares when provided") {
            val shares = BigDecimal("10.0000")
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.DEBIT,
                assetType = AssetType.STOCK_BUY,
                amount = BigDecimal("1500.0000"),
                currency = "USD",
                ticker = "AAPL",
                shares = shares
            )

            entry.shares shouldBe shares
        }

        it("supports STOCK_SELL assetType with ticker and shares") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.STOCK_SELL,
                amount = BigDecimal("1500.0000"),
                currency = "USD",
                ticker = "AAPL",
                shares = BigDecimal("10.0000")
            )

            entry.assetType shouldBe AssetType.STOCK_SELL
            entry.ticker shouldBe "AAPL"
            entry.shares shouldBe BigDecimal("10.0000")
        }
    }

    describe("LedgerEntry equals and hashCode") {

        it("two instances with the same entryId are equal") {
            val a = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("100.0000"),
                currency = "USD"
            )
            val b = LedgerEntry(
                entryId = entryId,
                accountId = UUID.randomUUID(),
                type = EntryType.DEBIT,
                assetType = AssetType.STOCK_BUY,
                amount = BigDecimal("999.0000"),
                currency = "GBP"
            )

            (a == b) shouldBe true
        }

        it("two instances with different entryIds are not equal") {
            val a = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("100.0000"),
                currency = "USD"
            )
            val b = LedgerEntry(
                entryId = UUID.randomUUID(),
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("100.0000"),
                currency = "USD"
            )

            (a == b) shouldBe false
        }

        it("same instance equals itself") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("100.0000"),
                currency = "USD"
            )

            (entry == entry) shouldBe true
        }

        it("hashCode is based on entryId") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("100.0000"),
                currency = "USD"
            )

            entry.hashCode() shouldBe entryId.hashCode()
        }
    }

    describe("LedgerEntry toString") {

        it("toString contains entryId") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("500.0000"),
                currency = "USD",
                description = "Top-up"
            )

            entry.toString() shouldNotBe null
            entry.toString().contains(entryId.toString()) shouldBe true
        }

        it("toString contains currency") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.CREDIT,
                assetType = AssetType.CASH,
                amount = BigDecimal("500.0000"),
                currency = "USD",
                description = "Top-up"
            )

            entry.toString().contains("USD") shouldBe true
        }

        it("toString contains ticker when set") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.DEBIT,
                assetType = AssetType.STOCK_BUY,
                amount = BigDecimal("1500.0000"),
                currency = "USD",
                ticker = "AAPL",
                shares = BigDecimal("10.0000")
            )

            entry.toString().contains("AAPL") shouldBe true
        }

        it("toString contains shares when set") {
            val entry = LedgerEntry(
                entryId = entryId,
                accountId = accountId,
                type = EntryType.DEBIT,
                assetType = AssetType.STOCK_BUY,
                amount = BigDecimal("1500.0000"),
                currency = "USD",
                ticker = "AAPL",
                shares = BigDecimal("10.0000")
            )

            entry.toString().contains("shares") shouldBe true
        }
    }
})
