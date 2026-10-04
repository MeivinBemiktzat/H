package com.ailocal.app.tools.impl

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.ailocal.app.tools.ToolResult

/**
 * Sends standard media key events through AudioManager, which routes them
 * to whatever app currently holds media session focus (Android handles this
 * natively - no special permission is required for these key events).
 */
class MediaActions(private val context: Context) {

    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private fun sendKey(keyCode: Int, label: String): ToolResult {
        return try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            ToolResult.Success(label)
        } catch (e: Exception) {
            ToolResult.Failure("Media control failed: ${e.message}")
        }
    }

    fun play() = sendKey(KeyEvent.KEYCODE_MEDIA_PLAY, "Play")
    fun pause() = sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE, "Paused")
    fun stop() = sendKey(KeyEvent.KEYCODE_MEDIA_STOP, "Stopped")
    fun next() = sendKey(KeyEvent.KEYCODE_MEDIA_NEXT, "Next track")
    fun previous() = sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous track")
}
