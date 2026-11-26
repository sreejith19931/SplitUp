package com.finance.splitup.domain

import kotlin.math.round

/**
 * Central money-rounding helpers.
 *
 * The app models money as Double (documented architectural debt — see notes on
 * migrating to minor units). Until that migration happens, every monetary
 * rounding MUST go through here so the whole codebase applies one identical
 * half-up 2-decimal rule instead of scattered round(x*100)/100.0 copies.
 */
object MoneyMath {

    /** Rounds a monetary value to 2 decimals using the app-wide convention. */
    fun round2(value: Double): Double = round(value * 100) / 100.0

    /** True when the value rounds to effectively zero at the cent level. */
    fun isZero(value: Double): Boolean = kotlin.math.abs(value) < 0.01
}
