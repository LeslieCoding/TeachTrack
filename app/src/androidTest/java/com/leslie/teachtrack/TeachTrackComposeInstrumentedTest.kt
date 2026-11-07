package com.leslie.teachtrack

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test
    fun bottomNavigationAdaptsToScreenWidth() {
        val compact = composeRule.activity.resources.configuration.screenWidthDp < 600
        listOf("首页", "学生", "计划", "统计").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        if (compact) {
            composeRule.onNodeWithText("更多").assertIsDisplayed()
            composeRule.onNodeWithText("设置").assertDoesNotExist()
            composeRule.onNodeWithText("更多").performClick()
        }
        listOf("设置", "Agent", "题库").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onNodeWithText("题库").performClick()
        composeRule.onNodeWithText("云端工作台").assertIsDisplayed()
    }
}
