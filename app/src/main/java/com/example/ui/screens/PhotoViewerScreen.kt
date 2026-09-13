package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaItem
import com.example.data.model.ReadingDirection
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.PrimaryViolet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PhotoViewerScreen(
    initialItem: MediaItem,
    allPhotos: List<MediaItem>,
    initialReadingDirection: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
    onClose: () -> Unit,
    onFavoriteToggle: (MediaItem) -> Unit,
    onShowInfo: (MediaItem) -> Unit
) {
    val photoList = remember(allPhotos, initialItem) {
        if (allPhotos.isNotEmpty() && allPhotos.any { it.id == initialItem.id }) {
            allPhotos
        } else {
            listOf(initialItem)
        }
    }

    val startIndex = remember(photoList, initialItem) {
        val idx = photoList.indexOfFirst { it.id == initialItem.id }
        if (idx >= 0) idx else 0
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { photoList.size }
    )
    val coroutineScope = rememberCoroutineScope()

    var showControls by remember { mutableStateOf(true) }
    var isSlideshowActive by remember { mutableStateOf(false) }
    var readingDirection by remember(initialReadingDirection) { mutableStateOf(initialReadingDirection) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    // Active zoom scale and offset for the currently viewed photo
    var activeScale by remember { mutableFloatStateOf(1f) }
    var activeOffset by remember { mutableStateOf(Offset.Zero) }

    // Reset zoom and offset whenever page changes so the new picture is ready to slide
    LaunchedEffect(pagerState.currentPage) {
        activeScale = 1f
        activeOffset = Offset.Zero
    }

    // Slideshow loop
    LaunchedEffect(isSlideshowActive, pagerState.currentPage) {
        if (isSlideshowActive) {
            delay(3500)
            val next = (pagerState.currentPage + 1) % photoList.size
            pagerState.animateScrollToPage(next)
        }
    }

    val currentPhoto = photoList.getOrNull(pagerState.currentPage) ?: initialItem

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepObsidian)
            .testTag("photo_viewer_screen")
    ) {
        // Pager for photos respecting reading direction (LTR, RTL, Vertical)
        when (readingDirection) {
            ReadingDirection.LEFT_TO_RIGHT -> {
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = false,
                    userScrollEnabled = activeScale <= 1.05f,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("photo_pager_ltr")
                ) { page ->
                    val photo = photoList[page]
                    val isCurrent = page == pagerState.currentPage
                    ZoomablePhotoImage(
                        item = photo,
                        isCurrent = isCurrent,
                        rotationAngle = if (isCurrent) rotationAngle else 0f,
                        scale = if (isCurrent) activeScale else 1f,
                        onScaleChange = { if (isCurrent) activeScale = it },
                        offset = if (isCurrent) activeOffset else Offset.Zero,
                        onOffsetChange = { if (isCurrent) activeOffset = it },
                        onToggleControls = { showControls = !showControls }
                    )
                }
            }
            ReadingDirection.RIGHT_TO_LEFT -> {
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = true,
                    userScrollEnabled = activeScale <= 1.05f,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("photo_pager_rtl")
                ) { page ->
                    val photo = photoList[page]
                    val isCurrent = page == pagerState.currentPage
                    ZoomablePhotoImage(
                        item = photo,
                        isCurrent = isCurrent,
                        rotationAngle = if (isCurrent) rotationAngle else 0f,
                        scale = if (isCurrent) activeScale else 1f,
                        onScaleChange = { if (isCurrent) activeScale = it },
                        offset = if (isCurrent) activeOffset else Offset.Zero,
                        onOffsetChange = { if (isCurrent) activeOffset = it },
                        onToggleControls = { showControls = !showControls }
                    )
                }
            }
            ReadingDirection.VERTICAL_CONTINUOUS -> {
                VerticalPager(
                    state = pagerState,
                    userScrollEnabled = activeScale <= 1.05f,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("photo_pager_vertical")
                ) { page ->
                    val photo = photoList[page]
                    val isCurrent = page == pagerState.currentPage
                    ZoomablePhotoImage(
                        item = photo,
                        isCurrent = isCurrent,
                        rotationAngle = if (isCurrent) rotationAngle else 0f,
                        scale = if (isCurrent) activeScale else 1f,
                        onScaleChange = { if (isCurrent) activeScale = it },
                        offset = if (isCurrent) activeOffset else Offset.Zero,
                        onOffsetChange = { if (isCurrent) activeOffset = it },
                        onToggleControls = { showControls = !showControls }
                    )
                }
            }
        }

        // --- Top Controls Overlay ---
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = GlassSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("photo_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = currentPhoto.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "${pagerState.currentPage + 1} of ${photoList.size} • ${currentPhoto.mediaFormat.name} • ${
                                when (readingDirection) {
                                    ReadingDirection.LEFT_TO_RIGHT -> "LTR"
                                    ReadingDirection.RIGHT_TO_LEFT -> "RTL"
                                    ReadingDirection.VERTICAL_CONTINUOUS -> "Vertical"
                                }
                            } • ${(activeScale * 100).toInt()}% Zoom",
                            color = AccentCyan,
                            fontSize = 11.sp
                        )
                    }

                    // Reading Direction Toggle Button
                    IconButton(
                        onClick = {
                            readingDirection = when (readingDirection) {
                                ReadingDirection.LEFT_TO_RIGHT -> ReadingDirection.RIGHT_TO_LEFT
                                ReadingDirection.RIGHT_TO_LEFT -> ReadingDirection.VERTICAL_CONTINUOUS
                                ReadingDirection.VERTICAL_CONTINUOUS -> ReadingDirection.LEFT_TO_RIGHT
                            }
                        },
                        modifier = Modifier.testTag("photo_reading_direction_toggle")
                    ) {
                        Icon(
                            imageVector = when (readingDirection) {
                                ReadingDirection.LEFT_TO_RIGHT -> Icons.Default.SwapHoriz
                                ReadingDirection.RIGHT_TO_LEFT -> Icons.Default.ViewCarousel
                                ReadingDirection.VERTICAL_CONTINUOUS -> Icons.Default.ViewStream
                            },
                            contentDescription = "Toggle Flow: ${readingDirection.name}",
                            tint = AccentCyan
                        )
                    }

                    // Rotate Button
                    IconButton(
                        onClick = { rotationAngle = (rotationAngle + 90f) % 360f },
                        modifier = Modifier.testTag("photo_rotate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Rotate",
                            tint = Color.White
                        )
                    }

                    // Slideshow Toggle
                    IconButton(
                        onClick = { isSlideshowActive = !isSlideshowActive },
                        modifier = Modifier.testTag("photo_slideshow_toggle")
                    ) {
                        Icon(
                            imageVector = if (isSlideshowActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Slideshow",
                            tint = if (isSlideshowActive) PrimaryViolet else Color.White
                        )
                    }

                    // Info Button
                    IconButton(
                        onClick = { onShowInfo(currentPhoto) },
                        modifier = Modifier.testTag("photo_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color.White
                        )
                    }

                    // Favorite Button
                    IconButton(
                        onClick = { onFavoriteToggle(currentPhoto) },
                        modifier = Modifier.testTag("photo_favorite_toggle")
                    ) {
                        Icon(
                            imageVector = if (currentPhoto.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (currentPhoto.isFavorite) Color(0xFFFF4081) else Color.White
                        )
                    }
                }
            }
        }

        // --- Dedicated Floating Zoom Controls (Zoom In, Zoom Out, Zoom %, Fit) ---
        AnimatedVisibility(
            visible = showControls || activeScale > 1.05f,
            enter = fadeIn() + slideInVertically(initialOffsetY = { 20 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { 20 }),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (showControls && photoList.size > 1) 96.dp else 24.dp)
        ) {
            Surface(
                color = DeepObsidian.copy(alpha = 0.92f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                shadowElevation = 8.dp,
                modifier = Modifier.testTag("photo_zoom_controls")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    // Zoom Out Button
                    IconButton(
                        onClick = {
                            activeScale = (activeScale - 0.5f).coerceAtLeast(1f)
                            if (activeScale <= 1.05f) activeOffset = Offset.Zero
                        },
                        enabled = activeScale > 1.05f,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_zoom_out")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOut,
                            contentDescription = "Zoom Out",
                            tint = if (activeScale > 1.05f) Color.White else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Clickable Zoom Percentage Badge (cycles 100% -> 200% -> 300% -> 100%)
                    Surface(
                        color = PrimaryViolet.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clickable {
                                activeScale = when {
                                    activeScale < 1.4f -> 2f
                                    activeScale < 2.5f -> 3f
                                    else -> 1f
                                }
                                if (activeScale <= 1.05f) activeOffset = Offset.Zero
                            }
                            .padding(horizontal = 4.dp)
                            .testTag("btn_zoom_preset")
                    ) {
                        Text(
                            text = "${(activeScale * 100).toInt()}%",
                            color = AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Zoom In Button
                    IconButton(
                        onClick = {
                            activeScale = (activeScale + 0.5f).coerceAtMost(5f)
                        },
                        enabled = activeScale < 4.95f,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_zoom_in")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Zoom In",
                            tint = if (activeScale < 4.95f) Color.White else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Reset / Fit to Screen Button (visible when zoomed in)
                    if (activeScale > 1.05f) {
                        IconButton(
                            onClick = {
                                activeScale = 1f
                                activeOffset = Offset.Zero
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_zoom_reset")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitScreen,
                                contentDescription = "Reset Zoom to Fit",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- Bottom Thumbnail Strip Overlay ---
        AnimatedVisibility(
            visible = showControls && photoList.size > 1,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = GlassSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(photoList) { idx, photo ->
                        val isSelected = idx == pagerState.currentPage
                        val thumbModel = photo.thumbnailUrl?.takeIf { it.isNotBlank() } ?: photo.uriString
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E1B2C),
                            border = if (isSelected) BorderStroke(2.dp, AccentCyan) else null,
                            modifier = Modifier
                                .size(width = 54.dp, height = 54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(idx)
                                    }
                                }
                                .testTag("photo_thumb_$idx")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (photo.drawableResId != null) {
                                    Image(
                                        painter = painterResource(id = photo.drawableResId),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (thumbModel.isNotBlank()) {
                                    AsyncImage(
                                        model = thumbModel,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Photo,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ZoomablePhotoImage(
    item: MediaItem,
    isCurrent: Boolean,
    rotationAngle: Float,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    onToggleControls: () -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Transformable handles two-finger gesture pinching zoom in and zoom out smoothly
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        if (newScale <= 1.05f) {
            onScaleChange(1f)
            onOffsetChange(Offset.Zero)
        } else {
            onScaleChange(newScale)
            val maxOffsetX = if (containerSize.width > 0) ((newScale - 1f) * containerSize.width) / 2f else 500f
            val maxOffsetY = if (containerSize.height > 0) ((newScale - 1f) * containerSize.height) / 2f else 500f
            val targetOffset = offset + panChange
            onOffsetChange(
                Offset(
                    x = targetOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                    y = targetOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            // 1. Single-finger pan ONLY when zoomed in (scale > 1.05f).
            // When scale <= 1.05f, this is NOT active, so horizontal swipes cleanly slide to the next picture!
            .then(
                if (isCurrent && scale > 1.05f) {
                    Modifier.pointerInput(scale) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val maxOffsetX = if (containerSize.width > 0) ((scale - 1f) * containerSize.width) / 2f else 500f
                            val maxOffsetY = if (containerSize.height > 0) ((scale - 1f) * containerSize.height) / 2f else 500f
                            val targetOffset = offset + dragAmount
                            onOffsetChange(
                                Offset(
                                    x = targetOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                    y = targetOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            )
                        }
                    }
                } else {
                    Modifier
                }
            )
            // 2. Gesture pinching zoom & zoom out using two fingers (smoothly scales between 1x and 5x)
            .transformable(
                state = transformState,
                canPan = { scale > 1.05f },
                enabled = isCurrent,
                lockRotationOnZoomPan = true
            )
            // 3. Taps: single tap toggles controls, double tap toggles zoom
            .pointerInput(isCurrent, scale) {
                if (!isCurrent) return@pointerInput
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            onScaleChange(1f)
                            onOffsetChange(Offset.Zero)
                        } else {
                            onScaleChange(2.5f)
                            onOffsetChange(Offset.Zero)
                        }
                    },
                    onTap = {
                        onToggleControls()
                    }
                )
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                rotationZ = rotationAngle,
                translationX = offset.x,
                translationY = offset.y
            ),
        contentAlignment = Alignment.Center
    ) {
        if (item.drawableResId != null) {
            Image(
                painter = painterResource(id = item.drawableResId),
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = item.uriString,
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
