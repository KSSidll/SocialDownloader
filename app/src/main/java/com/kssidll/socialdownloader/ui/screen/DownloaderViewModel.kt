package com.kssidll.socialdownloader.ui.screen

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kssidll.socialdownloader.media.Media
import com.kssidll.socialdownloader.util.SocialMediaUrl
import com.kssidll.socialdownloader.util.parseSocialMediaUrl
import com.kssidll.socialdownloader.xhs.downloadXhs
import com.kssidll.socialdownloader.xhs.filterXhs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "DownloaderViewModel"

/**
 * What the status area under the input is currently reporting.
 *
 * [Found] carries the resolved [Media] so the preview/save pass can hang a dialog off it without
 * the state model having to change shape.
 */
@Immutable
sealed interface DownloadState {
    data object Idle : DownloadState

    data object Working : DownloadState

    data class Found(val media: Media) : DownloadState

    sealed interface Failed : DownloadState {
        /** The text had no http(s) URL in it at all. */
        data object NoLink : Failed

        /** A site we recognise but haven't written a downloader for yet. */
        data class Unsupported(val platform: String) : Failed

        /** A URL, but not one we associate with any known site. */
        data object Unrecognized : Failed

        /** Reached the page but came away with nothing - covers fetch failures and empty parses. */
        data object NothingFound : Failed
    }
}

class DownloaderViewModel : ViewModel() {
    var input by mutableStateOf("")
        private set

    var state: DownloadState by mutableStateOf(DownloadState.Idle)
        private set

    private var resolveJob: Job? = null

    val canSubmit: Boolean get() = input.isNotBlank() && state !is DownloadState.Working

    fun onInputChange(value: String) {
        input = value

        // The previous result describes a link the user has since edited away - drop it, but leave
        // an in-flight run alone so its own completion is what clears the spinner.
        if (state !is DownloadState.Working) state = DownloadState.Idle
    }

    fun submit() {
        if (!canSubmit) return
        startResolve()
    }

    /**
     * Takes over the input with a link the user copied in another app and resolves it immediately,
     * which is what makes "swipe out, copy a link, swipe back" work without touching the keyboard.
     *
     * The clipboard carries everything the user copies, so this only claims the input for text that
     * actually holds a link we have a downloader for - anything else is left alone. Only the URL is
     * kept, so the field shows exactly what is about to be fetched rather than the whole shared blob.
     *
     * @return whether the text held a link this claimed. A caller reading the text out of the
     *   clipboard can use it to decide whether the clip has been consumed and should be cleared.
     */
    fun onPastedText(text: String): Boolean {
        val parsed = parseSocialMediaUrl(text)
        if (parsed == null || parsed is SocialMediaUrl.Unrecognized) {
            Log.d(TAG, "onPastedText: nothing actionable in the pasted text, leaving input alone")
            return false
        }

        Log.d(TAG, "onPastedText: taking over the input with $parsed")
        input = parsed.url

        // Deliberately bypasses canSubmit: a link the user just copied should win over whatever
        // resolve is still in flight, and startResolve() cancels that one anyway.
        startResolve()

        return true
    }

    private fun startResolve() {
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            state = DownloadState.Working
            state = resolve(input)
        }
    }

    private suspend fun resolve(text: String): DownloadState {
        val parsed = parseSocialMediaUrl(text)
        Log.d(TAG, "resolve: parsed as $parsed")

        val media: Media? = when (parsed) {
            null -> return DownloadState.Failed.NoLink
            is SocialMediaUrl.Xhs -> filterXhs(downloadXhs(parsed.url))
            is SocialMediaUrl.Instagram -> return DownloadState.Failed.Unsupported("Instagram")
            is SocialMediaUrl.Threads -> return DownloadState.Failed.Unsupported("Threads")
            is SocialMediaUrl.Unrecognized -> return DownloadState.Failed.Unrecognized
        }

        return if (media == null) DownloadState.Failed.NothingFound else DownloadState.Found(media)
    }
}
