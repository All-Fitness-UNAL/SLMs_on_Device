package com.edu.unal.haspima.slmsondevice

import com.edu.unal.haspima.slmsondevice.llm.Metrics
import org.junit.Assert.assertEquals
import org.junit.Test

class MetricsTest {

    @Test
    fun decodeSpeedExcludesFirstToken() {
        // 101 tokens; el primero llega en t=0.5 s y el último en t=5.5 s -> 100 tokens en 5 s.
        val tps = Metrics.decodeTokensPerSecond(101, firstTokenNanos = 500_000_000, endNanos = 5_500_000_000)
        assertEquals(20.0, tps, 1e-9)
    }

    @Test
    fun decodeSpeedIsZeroWhenNotMeasurable() {
        assertEquals(0.0, Metrics.decodeTokensPerSecond(1, 0, 1_000_000_000), 0.0)
        assertEquals(0.0, Metrics.decodeTokensPerSecond(50, 1_000, 1_000), 0.0)
    }

    @Test
    fun tokenEstimateUsesFourCharsPerToken() {
        assertEquals(0, Metrics.estimateTokens(""))
        assertEquals(1, Metrics.estimateTokens("hola"))
        assertEquals(2, Metrics.estimateTokens("hola!"))
        assertEquals(25, Metrics.estimateTokens("x".repeat(100)))
    }
}
