package com.kssidll.socialdownloader.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.kssidll.socialdownloader.R
import com.kssidll.socialdownloader.media.Media
import com.kssidll.socialdownloader.media.PlaceholderRatios
import com.kssidll.socialdownloader.media.MediaProbe
import com.kssidll.socialdownloader.ui.DefaultPreview
import com.kssidll.socialdownloader.ui.theme.Theme
import java.util.Locale

/** Every item is laid out against this; width is whatever the item's own proportions ask for. */
private val mediaStripHeight = 260.dp

/** Floor for a slot whose image never resolves a size at all, so a failure still occupies space. */
private val minItemWidth = 120.dp

/**
 * One selectable thing in the strip, flattened out of [Media]'s two lists.
 *
 * [probe] is what reading the file turned up - a frame, duration and size for a video, only a size
 * for an image - and is null while that read is still in flight.
 */
private data class MediaEntry(
    val url: String,
    val isVideo: Boolean,
    val position: Int,
    val probe: MediaProbe? = null,
)

private fun Media.entries(probes: Map<String, MediaProbe>): List<MediaEntry> =
    images.mapIndexed { index, url ->
        MediaEntry(url, isVideo = false, position = index + 1, probe = probes[url])
    } + videos.mapIndexed { index, url ->
        MediaEntry(url, isVideo = true, position = index + 1, probe = probes[url])
    }

/**
 * Lets the user pick which of the found media to keep.
 *
 * Selection is intentionally subtractive-free: picking nothing means "all of it", so the common case
 * of wanting the whole post costs no taps. [onDownload] is handed the concrete set to download, so
 * callers never have to re-apply that rule.
 *
 * Laid out as a LazyRow rather than one of the M3 carousels because those derive a single item width
 * for the whole strip, which is exactly what stops an item from keeping its own aspect ratio.
 */
@Composable
fun MediaDialog(
    media: Media,
    mediaProbes: Map<String, MediaProbe>,
    placeholderRatios: PlaceholderRatios,
    onDismiss: () -> Unit,
    onDownload: (Set<String>) -> Unit,
) {
    val entries = remember(media, mediaProbes) { media.entries(mediaProbes) }
    var selection by rememberSaveable { mutableStateOf(emptySet<String>()) }

    Dialog(
        onDismissRequest = onDismiss,
        // The strip needs the full dialog width to scroll through wide items properly.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(vertical = 24.dp)) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = stringResource(R.string.status_found, mediaSummary(media)),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.media_dialog_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(20.dp))

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mediaStripHeight),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries, key = { it.url }) { entry ->
                        MediaStripItem(
                            entry = entry,
                            placeholderRatio = placeholderRatios.ratioFor(entry.isVideo),
                            isSelected = entry.url in selection,
                            // With nothing picked everything is taken, so nothing looks excluded.
                            isDimmed = selection.isNotEmpty() && entry.url !in selection,
                            onClick = {
                                selection = if (entry.url in selection) {
                                    selection - entry.url
                                } else {
                                    selection + entry.url
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }

                    Button(
                        onClick = {
                            onDownload(selection.ifEmpty { entries.mapTo(mutableSetOf()) { it.url } })
                        },
                    ) {
                        Text(
                            text = if (selection.isEmpty()) {
                                stringResource(R.string.action_download_all, entries.size)
                            } else {
                                stringResource(R.string.action_download_selected, selection.size)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaStripItem(
    entry: MediaEntry,
    placeholderRatio: Float,
    isSelected: Boolean,
    isDimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        if (entry.isVideo) R.string.media_video_label else R.string.media_image_label,
        entry.position,
    )

    var isImageLoading by remember(entry.url) { mutableStateOf(true) }
    val thumbnail = entry.probe?.thumbnail

    // A video waits on its probe, an image on Coil.
    val isLoading = if (entry.isVideo) entry.probe == null else isImageLoading

    // Nothing to measure until the frame or the image arrives, and a video whose file gave up no
    // frame at all never gets anything to measure.
    val usesPlaceholderShape = isLoading || (entry.isVideo && thumbnail == null)

    // Height is fixed and width is left to wrap, so the media inside decides how wide the slot is.
    Box(
        modifier = modifier
            .height(mediaStripHeight)
            .widthIn(min = minItemWidth)
            .then(if (usesPlaceholderShape) Modifier.aspectRatio(placeholderRatio) else Modifier)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick)
            // Backs the media so a load that fails leaves a visible slot rather than a hole.
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        // FillHeight rather than Fit: both cover the card's full height and let the width follow the
        // source ratio, but Fit also honours the width axis, so anything that bounds the width
        // leaves the slot letterboxed instead of filled.
        when {
            thumbnail != null -> Image(
                bitmap = remember(thumbnail) { thumbnail.asImageBitmap() },
                contentDescription = label,
                modifier = Modifier.fillMaxHeight(),
                contentScale = ContentScale.FillHeight,
            )

            entry.isVideo -> VideoPlaceholder(label = label, placeholderRatio = placeholderRatio)

            else -> AsyncImage(
                model = entry.url,
                contentDescription = label,
                modifier = Modifier.fillMaxHeight(),
                contentScale = ContentScale.FillHeight,
                onState = { state ->
                    // Empty is the state before the request starts, so it counts as unsettled too.
                    isImageLoading = state is AsyncImagePainter.State.Empty ||
                        state is AsyncImagePainter.State.Loading
                },
            )
        }

        // Marks the item as a video whatever it managed to draw, so a poster frame is never
        // mistaken for a still.
        if (entry.isVideo && !isLoading) {
            PlayBadge(Modifier.align(Alignment.Center))
        }

        // Arrives on its own schedule - the probe can land after the picture has already drawn.
        if (!isLoading) {
            mediaDetail(entry.probe)?.let { detail ->
                DetailChip(
                    text = detail,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
            }
        }

        if (isLoading) {
            ShimmerOverlay(Modifier.matchParentSize())
        }

        if (isDimmed) {
            // matchParentSize rather than fillMaxSize: overlays must not take part in deciding the
            // width, or they would try to fill the LazyRow's unbounded width constraint.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
            )
        }

        if (isSelected) {
            SelectedBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            )
        }
    }
}

/**
 * A highlight sweeping across the slot while an image is still in flight, so a slow load reads as
 * work in progress rather than as a card that came back blank.
 */
@Composable
private fun ShimmerOverlay(modifier: Modifier = Modifier) {
    val sweep = rememberInfiniteTransition(label = "shimmer")
    val progress by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
        ),
        label = "shimmerSweep",
    )

    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface

    Box(
        // Reading progress inside the draw lambda keeps the animation in the draw phase, so it
        // never recomposes - it only redraws.
        modifier = modifier.drawBehind {
            val start = progress * size.width * 2f - size.width

            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(start, 0f),
                    end = Offset(start + size.width, size.height),
                ),
            )
        },
    )
}

/** Sits over a poster frame so a video always reads as a video rather than as a still. */
@Composable
private fun PlayBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(56.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
        contentColor = Color.White,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_play),
            contentDescription = null,
            modifier = Modifier.padding(14.dp),
        )
    }
}

/** Whatever the probe could say, e.g. "0:06 · 590 kB" for a video or just "138 kB" for an image. */
@Composable
private fun DetailChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
        contentColor = Color.White,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun mediaDetail(probe: MediaProbe?): String? {
    if (probe == null) return null

    val context = LocalContext.current
    val parts = mutableListOf<String>()

    probe.durationMs?.let { parts += formatDuration(it) }
    probe.sizeBytes?.let { parts += Formatter.formatShortFileSize(context, it) }

    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/** Stands in for a video whose file gave up no frame at all - it stays selectable regardless. */
@Composable
private fun VideoPlaceholder(label: String, placeholderRatio: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(mediaStripHeight)
            .aspectRatio(placeholderRatio)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun SelectedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(32.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = stringResource(R.string.media_selected),
            modifier = Modifier.padding(6.dp),
        )
    }
}

@DefaultPreview
@Composable
private fun MediaDialogPreview() {
    Theme {
        MediaDialog(
            media = Media(
                images = listOf(
                    "https://sns-img.xhscdn.com/a",
                    "https://sns-img.xhscdn.com/b",
                    "https://sns-img.xhscdn.com/c",
                ),
                videos = listOf("https://sns-video.xhscdn.com/d.mp4"),
            ),
            mediaProbes = emptyMap(),
            placeholderRatios = PlaceholderRatios.Default,
            onDismiss = {},
            onDownload = {},
        )
    }
}
