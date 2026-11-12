package com.finance.splitup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DateUtilsTest {

    @Test
    fun testNormalizeExpenseDate_today_storesConcreteDate() {
        val normalized = DateUtils.normalizeExpenseDate("Today")
        val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US))
        assertTrue("Normalized date should contain today's date $todayStr but was $normalized", normalized.contains(todayStr))
    }

    @Test
    fun testNormalizeExpenseDate_yesterday_storesConcreteDate() {
        val normalized = DateUtils.normalizeExpenseDate("Yesterday")
        val yesterdayStr = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US))
        assertTrue("Normalized date should contain yesterday's date $yesterdayStr but was $normalized", normalized.contains(yesterdayStr))
    }

    @Test
    fun testFormatDisplayDate_today_showsToday() {
        val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)) + ", 2:45 PM"
        val formatted = DateUtils.formatDisplayDate(todayStr)
        assertEquals("Today, 2:45 PM", formatted)
    }

    @Test
    fun testFormatDisplayDate_yesterday_showsYesterday() {
        val yesterdayStr = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)) + ", 2:45 PM"
        val formatted = DateUtils.formatDisplayDate(yesterdayStr)
        assertEquals("Yesterday", formatted)
    }

    @Test
    fun testFormatDisplayDate_twoDaysAgo_showsFormattedDate() {
        val dateTwoDaysAgo = LocalDate.now().minusDays(2)
        val dateStr = dateTwoDaysAgo.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)) + ", 2:45 PM"
        val formatted = DateUtils.formatDisplayDate(dateStr)
        val expected = dateTwoDaysAgo.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US))
        assertEquals(expected, formatted)
        assertFalse("Should not be Today", formatted.startsWith("Today"))
        assertFalse("Should not be Yesterday", formatted.startsWith("Yesterday"))
    }

    @Test
    fun testNov11ToNov12ToNov13TransitionSequence() {
        val storedDateStr = "Nov 11, 2025, 2:30 PM"

        // On Nov 11 (Same day as expense): Shows "Today, 2:30 PM"
        val displayNov11 = DateUtils.formatDisplayDate(storedDateStr, todayOverride = LocalDate.of(2025, 11, 11))
        assertEquals("Today, 2:30 PM", displayNov11)

        // On Nov 12 (1 day later): Shows "Yesterday" (without time)
        val displayNov12 = DateUtils.formatDisplayDate(storedDateStr, todayOverride = LocalDate.of(2025, 11, 12))
        assertEquals("Yesterday", displayNov12)

        // On Nov 13 (2 days later): Shows "Nov 11, 2025" (without time)
        val displayNov13 = DateUtils.formatDisplayDate(storedDateStr, todayOverride = LocalDate.of(2025, 11, 13))
        assertEquals("Nov 11, 2025", displayNov13)
    }
}
