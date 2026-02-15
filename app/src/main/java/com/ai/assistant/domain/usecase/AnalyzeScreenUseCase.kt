package com.ai.assistant.domain.usecase

import com.ai.assistant.domain.model.ScreenNode
import com.ai.assistant.service.ScreenAnalyzer
import javax.inject.Inject

class AnalyzeScreenUseCase @Inject constructor(
    private val screenAnalyzer: ScreenAnalyzer
) {
    operator fun invoke(): ScreenNode? {
        return screenAnalyzer.captureCurrentScreen()
    }

    fun getCurrentPackage(): String? {
        return screenAnalyzer.getCurrentPackageName()
    }
}
