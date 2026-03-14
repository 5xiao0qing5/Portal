package moe.fuqiuluo.portal.android.coro

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class CoroutineController {
    private val pauseState = MutableStateFlow(false)
    val isPaused: Boolean
        get() = pauseState.value

    suspend fun controlledCoroutine() {
        pauseState.first { !it }
    }

    fun pause() {
        pauseState.value = true
    }

    fun resume() {
        pauseState.value = false
    }
}
