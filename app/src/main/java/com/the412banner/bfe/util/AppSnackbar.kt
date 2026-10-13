package com.the412banner.bfe.util

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * App-wide short messages. The root scaffold in MainActivity collects [messages] into a Material3
 * SnackbarHost, so any screen (or plain callback) can post feedback without threading a
 * SnackbarHostState through every composable. When nothing is collecting — a screen hosted by
 * another activity, or the app is in the background — it falls back to a Toast so a message is
 * never silently dropped.
 */
object AppSnackbar {
    data class Message(val text: String, val long: Boolean)

    private val _messages = MutableSharedFlow<Message>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<Message> get() = _messages

    fun show(context: Context, text: String, long: Boolean = false) {
        if (_messages.subscriptionCount.value > 0) {
            _messages.tryEmit(Message(text, long))
        } else {
            Toast.makeText(context.applicationContext, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }
}
