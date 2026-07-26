package com.kssidll.socialdownloader.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import kotlin.time.Duration.Companion.minutes

private const val TAG = "ClipboardEffect"

/**
 * How recently a clip must have been copied for us to read it as "the user copied this to bring it
 * over here". Swiping to another app, copying a link and swiping back takes seconds; anything older
 * is leftover clipboard content that the user isn't thinking about.
 */
private val clipFreshnessWindow = 5.minutes

/**
 * Hands [onFreshText] whatever text the user copied elsewhere, each time this app takes window
 * focus with a clip newer than the last one delivered.
 *
 * Keyed on window focus rather than on lifecycle resume deliberately: since API 29 the clipboard
 * only reads back to the app that currently holds focus, and resume fires before focus is granted.
 *
 * @param onFreshText Receives the copied text and returns whether it was consumed. Consumed clips
 *   are cleared from the clipboard, so text this app had no use for survives to be pasted wherever
 *   the user actually meant it to go.
 */
@Composable
fun FreshClipboardTextEffect(onFreshText: (String) -> Boolean) {
    val context = LocalContext.current
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    val currentOnFreshText by rememberUpdatedState(onFreshText)

    // Saved, so coming back to a process that was killed in the background doesn't re-deliver a
    // clip that was already acted on.
    var lastDeliveredAt by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(isWindowFocused) {
        if (!isWindowFocused) return@LaunchedEffect

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        if (clipboard == null) {
            Log.w(TAG, "FreshClipboardTextEffect: no ClipboardManager available")
            return@LaunchedEffect
        }

        // Inspecting the description is free and silent, whereas reading the clip itself raises the
        // "app pasted from your clipboard" toast on API 31+ - so rule the clip out from here first.
        val description = clipboard.primaryClipDescription
        if (description == null) {
            Log.d(TAG, "FreshClipboardTextEffect: clipboard is empty")
            return@LaunchedEffect
        }

        val isText = description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        if (!isText) {
            Log.d(TAG, "FreshClipboardTextEffect: clip holds no text, ignoring")
            return@LaunchedEffect
        }

        val copiedAt = description.timestamp
        if (copiedAt <= lastDeliveredAt) {
            Log.d(TAG, "FreshClipboardTextEffect: clip from $copiedAt already delivered")
            return@LaunchedEffect
        }

        val age = System.currentTimeMillis() - copiedAt
        if (age > clipFreshnessWindow.inWholeMilliseconds) {
            Log.d(TAG, "FreshClipboardTextEffect: clip is ${age}ms old, too stale to act on")
            return@LaunchedEffect
        }

        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        if (text.isNullOrBlank()) {
            Log.d(TAG, "FreshClipboardTextEffect: clip coerced to no text")
            return@LaunchedEffect
        }

        Log.d(TAG, "FreshClipboardTextEffect: delivering clip from $copiedAt")
        lastDeliveredAt = copiedAt

        if (!currentOnFreshText(text)) {
            Log.d(TAG, "FreshClipboardTextEffect: clip wasn't claimed, leaving it on the clipboard")
            return@LaunchedEffect
        }

        // The app has taken the link over, so consume the clip rather than leaving it around to be
        // pasted somewhere by accident. Writing to the clipboard needs focus just as reading does,
        // which is still held here - but an OEM policy can still refuse, hence the catch.
        try {
            clipboard.clearPrimaryClip()
            Log.d(TAG, "FreshClipboardTextEffect: cleared the consumed clip")
        } catch (e: SecurityException) {
            Log.w(TAG, "FreshClipboardTextEffect: not allowed to clear the clipboard", e)
        }
    }
}
