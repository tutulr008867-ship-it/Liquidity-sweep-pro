package com.example.data.remote

import com.example.domain.model.MarketType
import com.example.domain.model.TradingPair

object MarketPairsCatalog {

    val allPairs: List<TradingPair> = listOf(
        // Major Forex Pairs & Spot Gold
        TradingPair(
            symbol = "EUR/USD",
            displayName = "Euro / US Dollar",
            marketType = MarketType.FOREX,
            binanceSymbol = null,
            yahooSymbol = "EURUSD=X",
            decimals = 5,
            pipSize = 0.0001,
            typicalSpread = 0.00012,
            referencePrice = 1.08450
        ),
        TradingPair(
            symbol = "GBP/USD",
            displayName = "British Pound / USD",
            marketType = MarketType.FOREX,
            binanceSymbol = null,
            yahooSymbol = "GBPUSD=X",
            decimals = 5,
            pipSize = 0.0001,
            typicalSpread = 0.00015,
            referencePrice = 1.27380
        ),
        TradingPair(
            symbol = "USD/JPY",
            displayName = "US Dollar / Japanese Yen",
            marketType = MarketType.FOREX,
            binanceSymbol = null,
            yahooSymbol = "JPY=X",
            decimals = 3,
            pipSize = 0.01,
            typicalSpread = 0.014,
            referencePrice = 154.280
        ),
        TradingPair(
            symbol = "AUD/USD",
            displayName = "Australian Dollar / USD",
            marketType = MarketType.FOREX,
            binanceSymbol = null,
            yahooSymbol = "AUDUSD=X",
            decimals = 5,
            pipSize = 0.0001,
            typicalSpread = 0.00016,
            referencePrice = 0.65420
        ),
        TradingPair(
            symbol = "USD/CAD",
            displayName = "US Dollar / Canadian Dollar",
            marketType = MarketType.FOREX,
            binanceSymbol = null,
            yahooSymbol = "CAD=X",
            decimals = 5,
            pipSize = 0.0001,
            typicalSpread = 0.00018,
            referencePrice = 1.39650
        ),
        TradingPair(
            symbol = "EUR/JPY",
            displayName = "Euro / Japanese Yen",
            marketType = MarketType.FOREX,
            binanceSymbol = null,
            yahooSymbol = "EURJPY=X",
            decimals = 3,
            pipSize = 0.01,
            typicalSpread = 0.018,
            referencePrice = 167.310
        ),
        TradingPair(
            symbol = "GBP/JPY",
            displayName = "British Pound / Yen",
            marketType = MarketType.FOREX,
            binanceSymbol = null,
            yahooSymbol = "GBPJPY=X",
            decimals = 3,
            pipSize = 0.01,
            typicalSpread = 0.024,
            referencePrice = 196.520
        ),
        TradingPair(
            symbol = "XAU/USD",
            displayName = "Gold Spot / US Dollar",
            marketType = MarketType.FOREX,
            binanceSymbol = null,
            yahooSymbol = "GC=F",
            decimals = 2,
            pipSize = 0.10,
            typicalSpread = 0.25,
            referencePrice = 2642.50
        ),

        // Major Cryptocurrency Pairs
        TradingPair(
            symbol = "BTC/USDT",
            displayName = "Bitcoin / Tether",
            marketType = MarketType.CRYPTO,
            binanceSymbol = "BTCUSDT",
            yahooSymbol = "BTC-USD",
            decimals = 2,
            pipSize = 1.0,
            typicalSpread = 2.50,
            referencePrice = 91450.00
        ),
        TradingPair(
            symbol = "ETH/USDT",
            displayName = "Ethereum / Tether",
            marketType = MarketType.CRYPTO,
            binanceSymbol = "ETHUSDT",
            yahooSymbol = "ETH-USD",
            decimals = 2,
            pipSize = 0.10,
            typicalSpread = 0.45,
            referencePrice = 3185.40
        ),
        TradingPair(
            symbol = "SOL/USDT",
            displayName = "Solana / Tether",
            marketType = MarketType.CRYPTO,
            binanceSymbol = "SOLUSDT",
            yahooSymbol = "SOL-USD",
            decimals = 2,
            pipSize = 0.05,
            typicalSpread = 0.06,
            referencePrice = 218.75
        ),
        TradingPair(
            symbol = "BNB/USDT",
            displayName = "BNB / Tether",
            marketType = MarketType.CRYPTO,
            binanceSymbol = "BNBUSDT",
            yahooSymbol = "BNB-USD",
            decimals = 2,
            pipSize = 0.10,
            typicalSpread = 0.20,
            referencePrice = 628.40
        ),
        TradingPair(
            symbol = "XRP/USDT",
            displayName = "Ripple / Tether",
            marketType = MarketType.CRYPTO,
            binanceSymbol = "XRPUSDT",
            yahooSymbol = "XRP-USD",
            decimals = 4,
            pipSize = 0.0005,
            typicalSpread = 0.0004,
            referencePrice = 1.1240
        ),
        TradingPair(
            symbol = "AVAX/USDT",
            displayName = "Avalanche / Tether",
            marketType = MarketType.CRYPTO,
            binanceSymbol = "AVAXUSDT",
            yahooSymbol = "AVAX-USD",
            decimals = 2,
            pipSize = 0.02,
            typicalSpread = 0.03,
            referencePrice = 36.85
        ),
        TradingPair(
            symbol = "ADA/USDT",
            displayName = "Cardano / Tether",
            marketType = MarketType.CRYPTO,
            binanceSymbol = "ADAUSDT",
            yahooSymbol = "ADA-USD",
            decimals = 4,
            pipSize = 0.0005,
            typicalSpread = 0.0003,
            referencePrice = 0.7420
        ),
        TradingPair(
            symbol = "DOGE/USDT",
            displayName = "Dogecoin / Tether",
            marketType = MarketType.CRYPTO,
            binanceSymbol = "DOGEUSDT",
            yahooSymbol = "DOGE-USD",
            decimals = 5,
            pipSize = 0.0001,
            typicalSpread = 0.00012,
            referencePrice = 0.36850
        )
    )

    fun findBySymbol(symbol: String): TradingPair {
        return allPairs.firstOrNull { it.symbol == symbol } ?: allPairs.first()
    }
}
