package com.finance.splitup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryDetectorTest {

    @Test
    fun testFoodAndDiningDetection() {
        val titles = listOf(
            "Whole Foods Market",
            "Starbucks Coffee",
            "McDonald's Drive Thru",
            "DoorDash Order",
            "Boba Tea & Snacks",
            "Local Ramen Shop",
            "Weekly Groceries"
        )
        titles.forEach { title ->
            val result = CategoryDetector.detectCategory(title)
            assertEquals("Failed for title: $title", ExpenseCategories.FOOD, result.category)
        }
    }

    @Test
    fun testTransportDetection() {
        val titles = listOf(
            "Uber Ride to Downtown",
            "Shell Gas Station Fuel",
            "Monthly Metro Pass",
            "Delta Airfare Ticket",
            "Tesla Supercharger",
            "Yellow Cab Taxi",
            "Highway Toll"
        )
        titles.forEach { title ->
            val result = CategoryDetector.detectCategory(title)
            assertEquals("Failed for title: $title", ExpenseCategories.TRANSPORT, result.category)
        }
    }

    @Test
    fun testShoppingDetection() {
        val titles = listOf(
            "Apple Store iPhone",
            "Best Buy Laptop",
            "Zara Summer Dress",
            "Amazon Prime Order",
            "Nike Sneakers",
            "IKEA Office Desk"
        )
        titles.forEach { title ->
            val result = CategoryDetector.detectCategory(title)
            assertEquals("Failed for title: $title", ExpenseCategories.SHOPPING, result.category)
        }
    }

    @Test
    fun testBillsAndUtilitiesDetection() {
        val titles = listOf(
            "Monthly Electric Bill",
            "Comcast Internet WiFi",
            "Apartment House Rent",
            "Health Insurance Premium",
            "College Tuition Fee",
            "Water Utility Payment"
        )
        titles.forEach { title ->
            val result = CategoryDetector.detectCategory(title)
            assertEquals("Failed for title: $title", ExpenseCategories.BILLS, result.category)
        }
    }

    @Test
    fun testEntertainmentDetection() {
        val titles = listOf(
            "Netflix Monthly Subscription",
            "Spotify Premium",
            "IMAX Movie Tickets",
            "Steam Video Game",
            "Planet Fitness Gym Membership",
            "Concert Ticketmaster"
        )
        titles.forEach { title ->
            val result = CategoryDetector.detectCategory(title)
            assertEquals("Failed for title: $title", ExpenseCategories.ENTERTAINMENT, result.category)
        }
    }

    @Test
    fun testIncomeDetection() {
        val titles = listOf(
            "Monthly Salary Paycheck",
            "Freelance Client Payout",
            "Tax Refund Deposit",
            "Quarterly Dividends"
        )
        titles.forEach { title ->
            val result = CategoryDetector.detectCategory(title)
            assertEquals("Failed for title: $title", ExpenseCategories.INCOME, result.category)
        }
    }

    @Test
    fun testUserLearningMechanism() {
        val customTitle = "Joe's Special Workshop"
        CategoryDetector.learnCategory(customTitle, ExpenseCategories.ENTERTAINMENT)

        val result = CategoryDetector.detectCategory(customTitle)
        assertEquals(ExpenseCategories.ENTERTAINMENT, result.category)
        assertTrue(result.isLearned)
    }

    @Test
    fun testFuzzyMatchingAndSanitization() {
        val typoTitle = "Starbux Coffee"
        val result1 = CategoryDetector.detectCategory(typoTitle)
        assertEquals(ExpenseCategories.FOOD, result1.category)

        val clutteredTitle = "SQ *WALMART SUPERCENTER #1234 90210"
        val result2 = CategoryDetector.detectCategory(clutteredTitle)
        assertEquals(ExpenseCategories.FOOD, result2.category)
    }

    @Test
    fun testMultiLanguageDetection() {
        // Hinglish
        val hinglishFood = CategoryDetector.detectCategory("chai aur nashta")
        assertEquals(ExpenseCategories.FOOD, hinglishFood.category)

        val hinglishBill = CategoryDetector.detectCategory("bijli bill")
        assertEquals(ExpenseCategories.BILLS, hinglishBill.category)

        // German
        val germanFood = CategoryDetector.detectCategory("kaffee und kuchen")
        assertEquals(ExpenseCategories.FOOD, germanFood.category)

        val germanTransport = CategoryDetector.detectCategory("fahrkarte berlin")
        assertEquals(ExpenseCategories.TRANSPORT, germanTransport.category)

        val germanBill = CategoryDetector.detectCategory("monatsmiete und strom")
        assertEquals(ExpenseCategories.BILLS, germanBill.category)
    }
}
