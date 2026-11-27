package com.finance.splitup.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyMathTest {

    // ---- round2 ----

    @Test
    fun `round2 keeps 2-decimal values unchanged`() {
        assertEquals(12.34, MoneyMath.round2(12.34), 1e-9)
        assertEquals(0.0, MoneyMath.round2(0.0), 1e-9)
    }

    @Test
    fun `round2 rounds half up`() {
        assertEquals(12.35, MoneyMath.round2(12.345), 1e-9)
        assertEquals(12.34, MoneyMath.round2(12.3449), 1e-9)
    }

    @Test
    fun `round2 handles negative values`() {
        assertEquals(-12.35, MoneyMath.round2(-12.345), 1e-9)
        assertEquals(-12.34, MoneyMath.round2(-12.3449), 1e-9)
    }

    @Test
    fun `round2 tames floating point drift`() {
        // Classic drift case: 0.1 + 0.2 = 0.30000000000000004
        assertEquals(0.3, MoneyMath.round2(0.1 + 0.2), 1e-9)
    }

    // ---- isZero ----

    @Test
    fun `isZero at cent boundary`() {
        assertTrue(MoneyMath.isZero(0.0))
        assertTrue(MoneyMath.isZero(0.004))
        assertTrue(MoneyMath.isZero(-0.009))
        assertTrue(!MoneyMath.isZero(0.01))
        assertTrue(!MoneyMath.isZero(-0.02))
    }

    // ---- minor units round trip ----

    @Test
    fun `minor unit round trip is stable`() {
        val samples = listOf(0.0, 12.34, 12.345, -12.345, 0.1 + 0.2, 1234567.89, 0.01, -0.01)
        for (value in samples) {
            val minor = MoneyMath.toMinorUnits(value)
            val back = MoneyMath.fromMinorUnits(minor)
            assertEquals(
                "round trip failed for $value",
                MoneyMath.round2(value),
                back,
                1e-9
            )
        }
    }

    @Test
    fun `toMinorUnits converts cents correctly`() {
        assertEquals(1234L, MoneyMath.toMinorUnits(12.34))
        assertEquals(1L, MoneyMath.toMinorUnits(0.01))
        assertEquals(-1234L, MoneyMath.toMinorUnits(-12.34))
        assertEquals(1235L, MoneyMath.toMinorUnits(12.345)) // half-up at cent boundary
    }

    @Test
    fun `fromMinorUnits converts back`() {
        assertEquals(12.34, MoneyMath.fromMinorUnits(1234L), 1e-9)
        assertEquals(0.0, MoneyMath.fromMinorUnits(0L), 1e-9)
        assertEquals(-12.34, MoneyMath.fromMinorUnits(-1234L), 1e-9)
    }

    // ---- even splitting in minor units ----

    @Test
    fun `splitEvenlyInMinorUnits sums exactly to the total`() {
        val cases = listOf(
            1000L to 3,   // 10.00 across 3 people
            999L to 3,    // 9.99 across 3
            1L to 3,      // single cent across 3
            100L to 7,    // remainder spread
            0L to 4,
            -999L to 3    // negative amounts (discounts)
        )
        for ((amount, parts) in cases) {
            val split = MoneyMath.splitEvenlyInMinorUnits(amount, parts)
            assertEquals("parts count", parts, split.size)
            assertEquals(
                "sum mismatch for $amount / $parts",
                amount,
                split.sum()
            )
            // fair split: max deviation between shares is 1 cent
            assertTrue(
                "shares not within a cent: $split",
                (split.max() - split.min()) <= 1
            )
        }
    }

    @Test
    fun `splitEvenlyInMinorUnits single share returns amount`() {
        assertEquals(listOf(4321L), MoneyMath.splitEvenlyInMinorUnits(4321L, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `splitEvenlyInMinorUnits rejects zero parts`() {
        MoneyMath.splitEvenlyInMinorUnits(100L, 0)
    }
}
