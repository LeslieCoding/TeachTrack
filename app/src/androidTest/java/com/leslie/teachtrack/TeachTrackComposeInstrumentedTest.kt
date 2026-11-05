package com.leslie.teachtrack

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TeachTrackComposeInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardEmptyStateAndQuickStartAreVisible() {
        composeRule.onNodeWithText("今天关注谁？").assertIsDisplayed()
        composeRule.onNodeWithText("快速开始").assertIsDisplayed()
    }
}
