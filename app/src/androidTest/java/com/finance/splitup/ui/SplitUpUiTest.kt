package com.finance.splitup.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finance.splitup.SplitUpApp
import com.finance.splitup.ui.theme.SplitUpTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplitUpUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testAppLaunchAndDashboardNavigation() {
        composeTestRule.setContent {
            SplitUpTheme {
                SplitUpApp()
            }
        }

        // Verify lock overlay and click unlock
        composeTestRule.onNodeWithText("SplitUp Security").assertExists()
        composeTestRule.onNodeWithText("Unlock with Biometrics / PIN").performClick()

        // Verify Dashboard displays title
        composeTestRule.onNodeWithText("SplitUp").assertExists()
    }
}
