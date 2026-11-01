package com.sportshub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.delay

private const val MAX_RETRIES = 5

/**
 * An image from the network that keeps trying. Coil gives up after one failed
 * download, so a brief drop (say, while the app is waking up) used to leave a
 * placeholder on screen for good. This retries with a growing pause instead.
 * Retries don't change the cache key, so a success is cached like any other load.
 *
 * [onLoadedChange] reports whether the picture is showing, so callers can draw
 * a placeholder underneath until it is.
 */
@Composable
fun RetryingImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onLoadedChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var attempt by remember(url) { mutableIntStateOf(0) }
    var failedAttempt by remember(url) { mutableIntStateOf(-1) }
    val request = remember(url, attempt) {
        ImageRequest.Builder(context)
            .data(url)
            // Changing this makes Coil load again, without touching the cache keys.
            .setParameter("attempt", attempt, memoryCacheKey = null)
            .build()
    }
    LaunchedEffect(url, failedAttempt) {
        if (failedAttempt >= 0 && failedAttempt == attempt && attempt < MAX_RETRIES) {
            delay(retryDelayMs(attempt))
            attempt++
        }
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onState = { state ->
            onLoadedChange(state is AsyncImagePainter.State.Success)
            if (state is AsyncImagePainter.State.Error) failedAttempt = attempt
        },
    )
}

/** 1s, 2s, 4s, 8s, 15s: quick for a blip, gentle on a dead connection. */
internal fun retryDelayMs(attempt: Int): Long = minOf(1_000L shl attempt.coerceIn(0, 4), 15_000L)

/**
 * A player's headshot in a circle, with their initials until it loads or when
 * there is none (ESPN has no photo for some backups and new signings).
 */
@Composable
fun PlayerHeadshot(url: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                initials(name),
                fontSize = (size.value * 0.34f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (url != null) {
            RetryingImage(
                url = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onLoadedChange = { loaded = it },
            )
        }
    }
}

/** "J. Allen" -> "JA", "Josh Allen" -> "JA". */
internal fun initials(name: String): String =
    name.split(' ', '.').filter { it.isNotBlank() }.let { parts ->
        when {
            parts.isEmpty() -> ""
            parts.size == 1 -> parts[0].take(2)
            else -> "${parts.first().first()}${parts.last().first()}"
        }
    }.uppercase()
