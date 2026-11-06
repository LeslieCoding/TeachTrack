package com.leslie.teachtrack.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object QuestionBankAgentBridge {
    private val _examAnalysisText = MutableStateFlow("")
    val examAnalysisText: StateFlow<String> = _examAnalysisText.asStateFlow()

    private val _openExamAnalysisRequests = MutableStateFlow(0L)
    val openExamAnalysisRequests: StateFlow<Long> = _openExamAnalysisRequests.asStateFlow()

    fun fillExamAnalysisText(text: String, append: Boolean): Int {
        _examAnalysisText.update { current ->
            if (append && current.isNotBlank()) "$current\n$text" else text
        }
        _openExamAnalysisRequests.update { it + 1 }
        return _examAnalysisText.value.length
    }

    fun updateExamAnalysisText(text: String) {
        _examAnalysisText.value = text
    }
}
