package edu.msrit.canteen

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import org.junit.Rule
import org.junit.Test

class DeveloperHubAccessUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun welcomeAndLoginLogoNoLongerOpenTestingHub() {
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("welcome_screen").fetchSemanticsNodes().isNotEmpty() ||
            rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty() }
        if (rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("Skip").performClick()
        repeat(6) { rule.onNodeWithTag("welcome_logo").performTouchInput { click() } }
        rule.onNodeWithTag("welcome_screen").assertExists()
        rule.onNodeWithTag("debug_testing_hub").assertDoesNotExist()
        rule.onNodeWithText("Login").performClick()
        repeat(6) { rule.onNodeWithTag("login_logo").performTouchInput { click() } }
        rule.onNodeWithTag("login_screen").assertExists()
        rule.onNodeWithTag("debug_testing_hub").assertDoesNotExist()
    }
}
