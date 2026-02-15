package com.ai.assistant.domain.usecase

import com.ai.assistant.domain.model.ActionPlan
import com.ai.assistant.domain.model.UiAction
import com.ai.assistant.service.ActionExecutor
import kotlinx.coroutines.delay
import javax.inject.Inject

class ExecuteActionPlanUseCase @Inject constructor(
    private val actionExecutor: ActionExecutor
) {
    /**
     * Executes a plan's actions sequentially.
     * Returns the list of completed action descriptions.
     */
    suspend operator fun invoke(
        plan: ActionPlan,
        delayBetweenActions: Long = 500
    ): List<ExecutionResult> {
        val results = mutableListOf<ExecutionResult>()

        for ((index, action) in plan.actions.withIndex()) {
            if (action is UiAction.Done || action is UiAction.Error) {
                results.add(
                    ExecutionResult(
                        action = action,
                        success = action is UiAction.Done,
                        description = when (action) {
                            is UiAction.Done -> action.message
                            is UiAction.Error -> action.reason
                            else -> ""
                        }
                    )
                )
                break
            }

            val success = actionExecutor.execute(action)
            results.add(
                ExecutionResult(
                    action = action,
                    success = success,
                    description = describeAction(action)
                )
            )

            if (!success) break

            // Delay between actions to let the UI update
            if (index < plan.actions.lastIndex) {
                val waitTime = if (action is UiAction.Wait) action.milliseconds else delayBetweenActions
                delay(waitTime)
            }
        }

        return results
    }

    private fun describeAction(action: UiAction): String = when (action) {
        is UiAction.Click -> "Нажал на ${action.nodeText ?: action.nodeDescription ?: "элемент #${action.nodeIndex}"}"
        is UiAction.LongClick -> "Долгое нажатие на ${action.nodeText ?: "элемент #${action.nodeIndex}"}"
        is UiAction.TypeText -> "Ввёл текст: \"${action.text}\""
        is UiAction.SetText -> "Установил текст: \"${action.text}\""
        is UiAction.ClearText -> "Очистил поле ввода"
        is UiAction.Scroll -> "Прокрутил ${action.direction.name.lowercase()}"
        is UiAction.PressButton -> "Нажал кнопку ${action.button.name}"
        is UiAction.OpenApp -> "Открыл приложение ${action.appName ?: action.packageName}"
        is UiAction.Wait -> "Подождал ${action.milliseconds}мс"
        is UiAction.Swipe -> "Свайп"
        is UiAction.Done -> "Готово: ${action.message}"
        is UiAction.Error -> "Ошибка: ${action.reason}"
    }

    data class ExecutionResult(
        val action: UiAction,
        val success: Boolean,
        val description: String
    )
}
