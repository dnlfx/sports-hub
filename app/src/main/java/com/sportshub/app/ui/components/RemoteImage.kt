package com.sportshub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import kotlinx.coroutines.delay

private const val MAX_RETRIES = 5

/**
 * Every logo and headshot is decoded at this one size (in pixels) and cached in memory
 * under its URL alone. That way an image already shown anywhere in the app is found in
 * memory before the first frame is drawn, instead of flashing its placeholder first.
 */
private const val IMAGE_SIZE_PX = 256

/**
 * An image from the network, with [placeholder] drawn until it's showing.
 *
 * - Images already in memory appear on the very first frame, with no placeholder flash.
 * - Coil gives up after one failed download, so a brief drop used to leave the
 *   placeholder up for good. This retries with a growing pause instead, without
 *   changing the cache keys, so a later success is cached like any other.
 */
@Composable
fun RetryingImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val loader = context.imageLoader
    var loaded by remember(url) {
        mutableStateOf(url != null && loader.memoryCache?.get(MemoryCache.Key(url)) != null)
    }
    var attempt by remember(url) { mutableIntStateOf(0) }
    var failedAttempt by remember(url) { mutableIntStateOf(-1) }
    LaunchedEffect(url, failedAttempt) {
        if (failedAttempt >= 0 && failedAttempt == attempt && attempt < MAX_RETRIES) {
            delay(retryDelayMs(attempt))
            attempt++
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!loaded) placeholder()
        if (url != null) {
            val request = remember(url, attempt) {
                ImageRequest.Builder(context)
                    .data(url)
                    .size(IMAGE_SIZE_PX)
                    .memoryCacheKey(url)
                    // Changing this makes Coil load again, without touching the cache keys.
                    .setParameter("attempt", attempt, memoryCacheKey = null)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
                onState = { state ->
                    // Only success and failure change what's shown; "loading" is ignored so an
                    // image that's already up never blinks back to its placeholder.
                    when (state) {
                        is AsyncImagePainter.State.Success -> loaded = true
                        is AsyncImagePainter.State.Error -> failedAttempt = attempt
                        else -> Unit
                    }
                },
            )
        }
    }
}

/** 1s, 2s, 4s, 8s, 15s: quick for a blip, gentle on a dead connection. */
internal fun retryDelayMs(attempt: Int): Long = minOf(1_000L shl attempt.coerceIn(0, 4), 15_000L)

/**
 * A player's headshot in a circle, with their initials until it loads or when
 * there is none (ESPN has no photo for some backups and new signings).
 */
@Composable
fun PlayerHeadshot(url: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    RetryingImage(
        url = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        placeholder = {
            Text(
                initials(name),
                fontSize = (size.value * 0.34f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
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
