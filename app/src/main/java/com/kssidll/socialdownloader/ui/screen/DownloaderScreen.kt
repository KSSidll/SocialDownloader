package com.kssidll.socialdownloader.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kssidll.socialdownloader.R
import com.kssidll.socialdownloader.media.FetchFailure
import com.kssidll.socialdownloader.media.Media
import com.kssidll.socialdownloader.ui.DefaultPreview
import com.kssidll.socialdownloader.ui.FreshClipboardTextEffect
import com.kssidll.socialdownloader.ui.theme.Theme

@Composable
fun DownloaderScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloaderViewModel = viewModel(),
) {
    FreshClipboardTextEffect(onFreshText = viewModel::onPastedText)

    val state = viewModel.state

    DownloaderScreen(
        input = viewModel.input,
        state = state,
        canSubmit = viewModel.canSubmit,
        onInputChange = viewModel::onInputChange,
        onSubmit = viewModel::submit,
        onFoundClick = viewModel::onFoundStatusClick,
        modifier = modifier,
    )

    if (viewModel.isMediaDialogVisible && state is DownloadState.WithMedia) {
        MediaDialog(
            media = state.media,
            mediaProbes = viewModel.mediaProbes,
            placeholderRatios = viewModel.placeholderRatios,
            onDismiss = viewModel::onMediaDialogDismiss,
            onDownload = viewModel::onDownloadRequested,
        )
    }
}

@Composable
private fun DownloaderScreen(
    input: String,
    state: DownloadState,
    canSubmit: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFoundClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    val submit = {
        keyboard?.hide()
        onSubmit()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.input_placeholder)) },
                shape = MaterialTheme.shapes.large,
                // Shared posts arrive as a blob of text with the link buried in it, so this can't
                // be single line, but it shouldn't be allowed to push the button off screen either.
                maxLines = 3,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
            )

            FilledIconButton(
                onClick = submit,
                modifier = Modifier.size(56.dp),
                enabled = canSubmit,
                // Deliberately tighter than shapes.large - at a fixed 56dp this reads as a squircle
                // rather than the pill a 24dp radius would give.
                shape = RoundedCornerShape(20.dp),
            ) {
                if (state is DownloadState.Working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_download),
                        contentDescription = stringResource(R.string.action_find_media),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        StatusArea(state = state, onFoundClick = onFoundClick)
    }
}

/**
 * Reports the outcome under the input. Animating between whole states (rather than toggling a
 * single card's visibility) keeps the exit of the old status readable while the new one arrives.
 */
@Composable
private fun StatusArea(
    state: DownloadState,
    onFoundClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "status",
    ) { current ->
        when (current) {
            DownloadState.Idle -> Spacer(Modifier.fillMaxWidth())
            // Only a state still holding a result has somewhere to go when tapped - the rest are
            // read-only reports.
            is DownloadState.WithMedia -> StatusCard(current, onClick = onFoundClick)
            else -> StatusCard(current)
        }
    }
}

@Composable
private fun StatusCard(
    state: DownloadState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme

    // A save that lost items reports as a failure even though some of it worked - a partial result
    // is the case worth noticing.
    val isSetback = state is DownloadState.Failed ||
        (state is DownloadState.Saved && state.failed > 0)
    val isResult = state is DownloadState.Found ||
        (state is DownloadState.Saved && state.failed == 0)

    val containerColor = when {
        isSetback -> colorScheme.errorContainer
        isResult -> colorScheme.primaryContainer
        else -> colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        isSetback -> colorScheme.onErrorContainer
        isResult -> colorScheme.onPrimaryContainer
        else -> colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusIcon(state)

            Text(
                text = statusText(state),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StatusIcon(state: DownloadState) {
    when (state) {
        DownloadState.Idle -> Unit

        DownloadState.Working, is DownloadState.Saving -> CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = LocalContentColor.current,
            strokeWidth = 2.5.dp,
        )

        is DownloadState.Found -> Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )

        is DownloadState.Saved -> Icon(
            painter = painterResource(
                if (state.failed > 0) R.drawable.ic_error else R.drawable.ic_check,
            ),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )

        is DownloadState.Failed -> Icon(
            painter = painterResource(R.drawable.ic_error),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun statusText(state: DownloadState): String = when (state) {
    DownloadState.Idle -> ""
    DownloadState.Working -> stringResource(R.string.status_working)
    is DownloadState.Found -> stringResource(R.string.status_found, mediaSummary(state.media))

    is DownloadState.Saving ->
        stringResource(R.string.status_saving, state.completed, state.total)

    is DownloadState.Saved -> if (state.failed > 0) {
        stringResource(R.string.status_saved_partial, state.saved, state.failed)
    } else {
        stringResource(R.string.status_saved, itemCount(state.saved))
    }
    DownloadState.Failed.NoLink -> stringResource(R.string.status_no_link)
    is DownloadState.Failed.Unsupported -> stringResource(R.string.status_unsupported, state.platform)
    DownloadState.Failed.Unrecognized -> stringResource(R.string.status_unrecognized)
    is DownloadState.Failed.Fetch -> stringResource(
        when (state.reason) {
            FetchFailure.Unreachable -> R.string.status_unreachable
            FetchFailure.AgeRestricted -> R.string.status_age_restricted
            FetchFailure.LoginRequired -> R.string.status_login_required
            FetchFailure.Gone -> R.string.status_gone
            FetchFailure.NothingFound -> R.string.status_nothing_found
        },
    )
}

@Composable
private fun itemCount(count: Int): String = pluralStringResource(R.plurals.item_count, count, count)

/** Renders a [Media] as e.g. "3 images · 1 video", leaving out whichever kind isn't present. */
@Composable
internal fun mediaSummary(media: Media): String {
    val parts = mutableListOf<String>()

    if (media.images.isNotEmpty()) {
        parts += pluralStringResource(R.plurals.image_count, media.images.size, media.images.size)
    }
    if (media.videos.isNotEmpty()) {
        parts += pluralStringResource(R.plurals.video_count, media.videos.size, media.videos.size)
    }

    return parts.joinToString(" · ")
}

private val previewMedia = Media(
    images = listOf("https://sns-img.xhscdn.com/a", "https://sns-img.xhscdn.com/b"),
    videos = listOf("https://sns-video.xhscdn.com/c.mp4"),
)

@DefaultPreview
@Composable
private fun DownloaderScreenIdlePreview() {
    Theme {
        DownloaderScreen(
            input = "",
            state = DownloadState.Idle,
            canSubmit = false,
            onInputChange = {},
            onSubmit = {},
            onFoundClick = {},
        )
    }
}

@DefaultPreview
@Composable
private fun DownloaderScreenWorkingPreview() {
    Theme {
        DownloaderScreen(
            input = "http://xhslink.cn/o/example",
            state = DownloadState.Working,
            canSubmit = false,
            onInputChange = {},
            onSubmit = {},
            onFoundClick = {},
        )
    }
}

@DefaultPreview
@Composable
private fun DownloaderScreenFoundPreview() {
    Theme {
        DownloaderScreen(
            input = "http://xhslink.cn/o/example",
            state = DownloadState.Found(previewMedia),
            canSubmit = true,
            onInputChange = {},
            onSubmit = {},
            onFoundClick = {},
        )
    }
}

@DefaultPreview
@Composable
private fun DownloaderScreenFailedPreview() {
    Theme {
        DownloaderScreen(
            input = "https://instagram.com/p/example",
            state = DownloadState.Failed.Unsupported("Instagram"),
            canSubmit = true,
            onInputChange = {},
            onSubmit = {},
            onFoundClick = {},
        )
    }
}
