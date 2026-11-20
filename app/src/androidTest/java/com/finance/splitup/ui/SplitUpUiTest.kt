package com.finance.splitup.ui

import androidx.compose.ui.graphics.asAndroidBitmap
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

    @Test
    fun testNavigationTabs() {
        composeTestRule.setContent {
            SplitUpTheme {
                SplitUpApp()
            }
        }

        // Unlock app
        composeTestRule.onNodeWithText("Unlock with Biometrics / PIN").performClick()

        // Verify dashboard is shown
        composeTestRule.onNodeWithText("SplitUp").assertExists()

        // Click on Groups bottom nav item if present
        composeTestRule.onAllNodesWithText("Groups").onFirst().performClick()
    }

    @Test
    fun testRenderCircularNavyPlatinumLogoToPng() {
        composeTestRule.setContent {
            com.finance.splitup.ui.components.PreviewCircularNavyPlatinumLogo()
        }

        val bitmap = composeTestRule.onRoot().captureToImage().asAndroidBitmap()
        val scratchDir = java.io.File("C:/Users/Sreejith/AppData/Local/Google/AndroidStudio2026.2.1/projects/splitup.80c49d07/.artifacts/972cdb61-6614-473d-b1a5-fba1f6fd704a/scratch")
        if (!scratchDir.exists()) scratchDir.mkdirs()
        val file = java.io.File(scratchDir, "SplitUpCircularLogo.png")
        val out = java.io.FileOutputStream(file)
        try {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        } finally {
            out.close()
        }
    }
}
