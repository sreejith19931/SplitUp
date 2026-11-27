package com.finance.splitup.domain


/**
 * Central money-rounding helpers.
 *
 * The app models money as Double (documented architectural debt — see notes on
 * migrating to minor units). Until that migration happens, every monetary
 * rounding MUST go through here so the whole codebase applies one identical
 * half-up 2-decimal rule instead of scattered round(x*100)/100.0 copies.
 */
object MoneyMath {

    /**
     * Rounds a monetary value to 2 decimals using the app-wide convention
     * (decimal half-up). Implemented via BigDecimal on the shortest decimal
     * representation so that values like 12.345 (which are 12.3449... in
     * binary) round to 12.35 as a human expects, instead of the
     * multiply-by-100 drift artifacts of naive double rounding.
     */
    fun round2(value: Double): Double {
        val bd = java.math.BigDecimal(value.toString())
        return bd.setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
    }

    /** True when the value rounds to effectively zero at the cent level. */
    fun isZero(value: Double): Boolean = kotlin.math.abs(value) < 0.01

    // ------------------------------------------------------------------
    // Minor-units foundation (step 1 of the Double -> cents migration)
    // ------------------------------------------------------------------
    //
    // These helpers establish the single conversion boundary between the
    // Double-based domain and integer minor units (cents). New code that
    // accumulates, compares or persists money should convert via
    // [toMinorUnits], operate on Longs, and convert back with
    // [fromMinorUnits]. Migrating existing call sites is then a mechanical
    // sweep that can be done screen-by-screen without touching this API.
    //
    // Conversion uses half-up rounding at the cent boundary, matching
    // [round2], so double<->minor round-trips are stable:
    // fromMinorUnits(toMinorUnits(x)) == round2(x) for all realistic inputs.

    /** Converts a major-unit Double to integer minor units (cents), half-up. */
    fun toMinorUnits(major: Double): Long =
        java.math.BigDecimal(major.toString())
            .movePointRight(2)
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .longValueExact()

    /** Converts integer minor units (cents) back to a major-unit Double. */
    fun fromMinorUnits(minor: Long): Double = minor / 100.0

    /**
     * Distributes [amount] across [parts] shares in minor units so the parts
     * always sum to exactly the total — the classic "penny-splitting" rule.
     * Remainder cents are assigned one per share, in order. Returns values
     * in MINOR units; convert with [fromMinorUnits] for display.
     */
    fun splitEvenlyInMinorUnits(amountMinor: Long, parts: Int): List<Long> {
        require(parts > 0) { "parts must be positive" }
        if (parts == 1) return listOf(amountMinor)
        val base = amountMinor / parts
        var remainder = amountMinor - base * parts
        return List(parts) { idx ->
            if (remainder > 0) {
                remainder--
                base + 1
            } else if (remainder < 0) {
                remainder++
                base - 1
            } else {
                base
            }
        }
    }
}
