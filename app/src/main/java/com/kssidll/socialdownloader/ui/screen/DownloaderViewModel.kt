package com.kssidll.socialdownloader.ui.screen

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kssidll.socialdownloader.instagram.downloadInstagram
import com.kssidll.socialdownloader.media.FetchFailure
import com.kssidll.socialdownloader.media.FetchOutcome
import com.kssidll.socialdownloader.media.Media
import com.kssidll.socialdownloader.media.PlaceholderRatios
import com.kssidll.socialdownloader.media.placeholderRatios
import com.kssidll.socialdownloader.media.MediaProbe
import com.kssidll.socialdownloader.media.saveMedia
import com.kssidll.socialdownloader.media.probeImage
import com.kssidll.socialdownloader.media.probeVideo
import com.kssidll.socialdownloader.util.SocialMediaUrl
import com.kssidll.socialdownloader.util.parseSocialMediaUrl
import com.kssidll.socialdownloader.xhs.downloadXhs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "DownloaderViewModel"

/** What the status area under the input is currently reporting. */
@Immutable
sealed interface DownloadState {
    /**
     * The states that still hold a result. Saving doesn't discard what was found, so the picker
     * stays available throughout and afterwards - handy for grabbing the rest of a post on a
     * second pass.
     */
    sealed interface WithMedia : DownloadState {
        val media: Media
    }

    data object Idle : DownloadState

    data object Working : DownloadState

    data class Found(override val media: Media) : WithMedia

    data class Saving(
        override val media: Media,
        val completed: Int,
        val total: Int,
    ) : WithMedia

    data class Saved(
        override val media: Media,
        val saved: Int,
        val failed: Int,
    ) : WithMedia

    sealed interface Failed : DownloadState {
        /** The text had no http(s) URL in it at all. */
        data object NoLink : Failed

        /** A site we recognise but haven't written a downloader for yet. */
        data class Unsupported(val platform: String) : Failed

        /** A URL, but not one we associate with a kind of link we can act on. */
        data object Unrecognized : Failed

        /** A downloader ran and came back with a reason rather than media. */
        data class Fetch(val reason: FetchFailure) : Failed
    }
}

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {
    var input by mutableStateOf("")
        private set

    var state: DownloadState by mutableStateOf(DownloadState.Idle)
        private set

    /**
     * Whether the picker is open. Held here rather than in the composable so it survives rotation,
     * and gated on [state] being [DownloadState.Found] by the screen, so a result going away closes
     * the dialog without any extra bookkeeping.
     */
    var isMediaDialogVisible by mutableStateOf(false)
        private set

    /**
     * Per media URL, what reading the file turned up. Absent means still in flight, which is what
     * the picker shows a video shimmer for; present but empty means the file gave nothing away.
     */
    var mediaProbes by mutableStateOf<Map<String, MediaProbe>>(emptyMap())
        private set

    /** Shapes the picker falls back to for the platform the current result came from. */
    var placeholderRatios by mutableStateOf(PlaceholderRatios.Default)
        private set

    private var resolveJob: Job? = null
    private var probeJob: Job? = null
    private var saveJob: Job? = null

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

    /** Reopens the picker for a result the user dismissed without downloading. */
    fun onFoundStatusClick() {
        if (state is DownloadState.WithMedia) isMediaDialogVisible = true
    }

    fun onMediaDialogDismiss() {
        isMediaDialogVisible = false
    }

    /**
     * Saves the media the user settled on - already a concrete set, so "nothing picked means all of
     * it" has been applied by the time it lands here.
     *
     * Saves run one at a time rather than all at once: it keeps the progress count honest, and a
     * post's worth of media isn't enough work for the parallelism to buy anything.
     */
    fun onDownloadRequested(urls: Set<String>) {
        val media = (state as? DownloadState.WithMedia)?.media ?: return
        Log.d(TAG, "onDownloadRequested: ${urls.size} url(s) chosen")

        isMediaDialogVisible = false

        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            val targets = urls.toList()
            var saved = 0
            var failed = 0

            state = DownloadState.Saving(media, completed = 0, total = targets.size)

            targets.forEachIndexed { index, url ->
                val uri = saveMedia(
                    context = getApplication(),
                    url = url,
                    // Only a hint - the response's own content type decides where it really goes.
                    expectVideo = url in media.videos,
                )

                if (uri != null) saved++ else failed++
                state = DownloadState.Saving(media, completed = index + 1, total = targets.size)
            }

            Log.d(TAG, "onDownloadRequested: finished with $saved saved, $failed failed")
            state = DownloadState.Saved(media, saved = saved, failed = failed)
        }
    }

    private fun startResolve() {
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            state = DownloadState.Working

            val result = resolve(input)
            state = result

            val media = (result as? DownloadState.Found)?.media
            startProbes(media)

            // With one item there is nothing to choose between, so the picker would be a
            // confirmation step and nothing more - save it and let the status area report. Anything
            // else opens straight onto the picker, which is what keeps the copy-and-return flow
            // hands-free.
            val onlyUrl = media?.singleUrl
            if (onlyUrl != null) {
                Log.d(TAG, "startResolve: single item, saving it without the picker")
                onDownloadRequested(setOf(onlyUrl))
            } else {
                isMediaDialogVisible = media != null
            }
        }
    }

    /**
     * Fills in what no parse step can know - a poster frame, a duration, a byte size - by reading
     * the video files themselves.
     *
     * Lives here rather than in the picker so results survive the dialog being dismissed and
     * reopened, and so starting a new resolve is what clears them.
     */
    private fun startProbes(media: Media?) {
        probeJob?.cancel()
        mediaProbes = emptyMap()

        if (media == null || media.isEmpty) return

        Log.d(TAG, "startProbes: probing ${media.images.size} image(s), ${media.videos.size} video(s)")
        probeJob = viewModelScope.launch {
            // Recorded even when they come back empty, so the picker can tell "gave up" from
            // "still working" and stop shimmering either way.
            media.videos.forEach { url -> launch { recordProbe(url, probeVideo(url)) } }
            media.images.forEach { url -> launch { recordProbe(url, probeImage(url)) } }
        }
    }

    /**
     * Folds one finished probe into the map.
     *
     * Deliberately a call taking the finished probe rather than `mediaProbes += url to probe(url)`:
     * in that form Kotlin evaluates the map receiver *before* the suspending probe runs, so every
     * concurrent probe starts from the same snapshot and all but the last to finish are dropped.
     */
    private fun recordProbe(url: String, probe: MediaProbe) {
        mediaProbes = mediaProbes + (url to probe)
    }

    private suspend fun resolve(text: String): DownloadState {
        val parsed = parseSocialMediaUrl(text)
        Log.d(TAG, "resolve: parsed as $parsed")

        placeholderRatios = parsed?.placeholderRatios ?: PlaceholderRatios.Default

        // Each platform owns fetching, parsing and screening its own results, so all that lands
        // here is an outcome to translate into something the status area can say.
        val outcome = when (parsed) {
            null -> return DownloadState.Failed.NoLink
            is SocialMediaUrl.Xhs -> downloadXhs(parsed.url)
            is SocialMediaUrl.InstagramShortcode -> downloadInstagram(parsed)
            is SocialMediaUrl.Threads -> return DownloadState.Failed.Unsupported("Threads")
            is SocialMediaUrl.Unrecognized -> return DownloadState.Failed.Unrecognized
        }

        return when (outcome) {
            is FetchOutcome.Success -> DownloadState.Found(outcome.media)
            is FetchOutcome.Failure -> DownloadState.Failed.Fetch(outcome.reason)
        }
    }
}
