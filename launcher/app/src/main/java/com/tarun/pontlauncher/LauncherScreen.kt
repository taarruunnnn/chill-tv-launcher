package com.tarun.pontlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

private val BgTop = Color(0xFF1C1E26)
private val BgBottom = Color(0xFF0A0B0E)
private val CardBg = Color(0xFF2A2C36)
private val TextPrimary = Color(0xFFF2F3F7)
private val TextDim = Color(0x8CF2F3F7)
private val CardShape = RoundedCornerShape(12.dp)

@Composable
fun LauncherScreen(
    apps: List<TvApp>,
    favorites: List<String>,
    hasUsb: Boolean,
    onLaunch: (TvApp) -> Unit,
    onToggleFavorite: (TvApp) -> Unit,
    onAppInfo: (TvApp) -> Unit,
    onUninstall: (TvApp) -> Unit,
    onMoveFavorite: (String, Int) -> Unit,
    onOpenUsb: () -> Unit,
    onSettings: () -> Unit,
) {
    var menuFor by remember { mutableStateOf<TvApp?>(null) }
    var showAllApps by remember { mutableStateOf(false) }
    var movingPkg by remember { mutableStateOf<String?>(null) }
    val homeApps = favorites.mapNotNull { pkg -> apps.find { it.packageName == pkg } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        Crossfade(targetState = showAllApps, animationSpec = tween(250), label = "screen") { all ->
            if (all) {
                AllAppsScreen(
                    apps = apps,
                    onLaunch = onLaunch,
                    onMenu = { menuFor = it },
                    onClose = { showAllApps = false },
                )
            } else {
                HomeScreen(
                    homeApps = homeApps,
                    hasUsb = hasUsb,
                    movingPkg = movingPkg,
                    onMoveDelta = { delta -> movingPkg?.let { onMoveFavorite(it, delta) } },
                    onDoneMoving = { movingPkg = null },
                    onLaunch = onLaunch,
                    onMenu = { menuFor = it },
                    onOpenUsb = onOpenUsb,
                    onAllApps = { showAllApps = true },
                    onSettings = onSettings,
                )
            }
        }
    }

    menuFor?.let { app ->
        AppMenuDialog(
            app = app,
            isOnHome = app.packageName in favorites,
            onDismiss = { menuFor = null },
            onMove = if (app.packageName in favorites) {
                { movingPkg = app.packageName; showAllApps = false; menuFor = null }
            } else null,
            onToggleFavorite = { onToggleFavorite(app); menuFor = null },
            onAppInfo = { onAppInfo(app); menuFor = null },
            onUninstall = { onUninstall(app); menuFor = null },
        )
    }
}

@Composable
private fun HomeScreen(
    homeApps: List<TvApp>,
    hasUsb: Boolean,
    movingPkg: String?,
    onMoveDelta: (Int) -> Unit,
    onDoneMoving: () -> Unit,
    onLaunch: (TvApp) -> Unit,
    onMenu: (TvApp) -> Unit,
    onOpenUsb: () -> Unit,
    onAllApps: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 20.dp)
            // Move mode: arrows reorder the held app, OK/Back finishes. All other
            // keys are swallowed so focus can't wander mid-move.
            .onPreviewKeyEvent { e ->
                if (movingPkg == null) return@onPreviewKeyEvent false
                when {
                    e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft -> {
                        onMoveDelta(-1); true
                    }
                    e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight -> {
                        onMoveDelta(1); true
                    }
                    e.type == KeyEventType.KeyUp &&
                        (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.Back) -> {
                        onDoneMoving(); true
                    }
                    else -> true
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                if (movingPkg == null) "Hey, let’s chill" else "Moving — ◀ ▶ to reorder, OK when done",
                style = TextStyle(color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.weight(1f))
            TvCard(onClick = onSettings, scaleFocused = 1.05f) { focused ->
                BasicText(
                    "Settings",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = TextStyle(color = if (focused) TextPrimary else TextDim, fontSize = 14.sp),
                )
            }
            Spacer(Modifier.width(20.dp))
            Clock()
        }

        Spacer(Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 216.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
        ) {
            items(homeApps, key = { it.packageName }) { app ->
                Box(Modifier.animateItem()) {
                    LabeledCard(
                        label = app.label,
                        width = 216.dp,
                        moving = app.packageName == movingPkg,
                        onClick = { onLaunch(app) },
                        onLongClick = { onMenu(app) },
                    ) {
                        AppArt(app, width = 216.dp, height = 122.dp)
                    }
                }
            }
            if (hasUsb) {
                item(key = "::usb") {
                    LabeledCard(label = "Open in VLC", width = 216.dp, onClick = onOpenUsb) {
                        UsbCard()
                    }
                }
            }
            item(key = "::all") {
                LabeledCard(label = "All your apps", width = 216.dp, onClick = onAllApps) {
                    AllAppsCard()
                }
            }
        }
    }
}

@Composable
private fun AllAppsScreen(
    apps: List<TvApp>,
    onLaunch: (TvApp) -> Unit,
    onMenu: (TvApp) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                "All apps",
                style = TextStyle(color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                "Hold OK on an app to add it to home · Back to close",
                style = TextStyle(color = TextDim, fontSize = 13.sp),
            )
        }

        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                LabeledCard(
                    label = app.label,
                    width = 148.dp,
                    onClick = { onLaunch(app) },
                    onLongClick = { onMenu(app) },
                ) {
                    AppArt(app, width = 148.dp, height = 84.dp)
                }
            }
        }
    }
}

/** A TvCard with a label that fades in underneath while focused (tvOS style). */
@Composable
private fun LabeledCard(
    label: String,
    width: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    moving: Boolean = false,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TvCard(
            onClick = onClick,
            onLongClick = onLongClick,
            moving = moving,
            onFocusChange = { focused = it },
        ) { _ -> content() }
        val labelAlpha by animateFloatAsState(if (focused) 1f else 0f, label = "label")
        BasicText(
            label,
            modifier = Modifier
                .width(width)
                .padding(top = 2.dp)
                .graphicsLayer { alpha = labelAlpha },
            style = TextStyle(color = TextPrimary, fontSize = 13.sp, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Clock() {
    val context = LocalContext.current
    val format = remember { android.text.format.DateFormat.getTimeFormat(context) }
    var time by remember { mutableStateOf(format.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            time = format.format(Date())
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    BasicText(
        time,
        style = TextStyle(color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    )
}

/** Card art: banner stretched to fill, or icon centered on a card background. */
@Composable
private fun AppArt(app: TvApp, width: Dp, height: Dp) {
    Box(
        modifier = Modifier
            .size(width, height)
            .background(CardBg),
        contentAlignment = Alignment.Center,
    ) {
        if (app.artIsBanner) {
            Image(
                bitmap = app.art,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Image(
                bitmap = app.art,
                contentDescription = app.label,
                modifier = Modifier.size(height * 0.6f),
            )
        }
    }
}

@Composable
private fun UsbCard() {
    Box(
        modifier = Modifier
            .size(216.dp, 122.dp)
            .background(Brush.linearGradient(listOf(Color(0xFF33415C), Color(0xFF1B2233)))),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "USB Drive",
            style = TextStyle(color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun AllAppsCard() {
    Box(
        modifier = Modifier
            .size(216.dp, 122.dp)
            .background(CardBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Little 3x2 dot grid, tvOS "App Library" style.
            repeat(2) { row ->
                Row {
                    repeat(3) { col ->
                        Box(
                            Modifier
                                .padding(3.dp)
                                .size(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    listOf(
                                        Color(0xFFE8590C), Color(0xFF37B24D), Color(0xFF4C6EF5),
                                        Color(0xFFF59F00), Color(0xFFAE3EC9), Color(0xFF15AABF),
                                    )[row * 3 + col]
                                )
                        )
                    }
                }
            }
            BasicText(
                "All Apps",
                modifier = Modifier.padding(top = 8.dp),
                style = TextStyle(color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/**
 * tvOS-style focusable card: springs up in scale with a soft white glow when
 * focused. Long-press is detected manually from d-pad key repeats because
 * combinedClickable never sees key-based long presses; the action fires on
 * key-up so the just-opened dialog can't receive the tail of the press.
 */
@Composable
private fun TvCard(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    scaleFocused: Float = 1.12f,
    moving: Boolean = false,
    onFocusChange: ((Boolean) -> Unit)? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var longFired by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) scaleFocused else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "scale",
    )
    val borderColor = when {
        moving -> Color(0xFF66D9E8)
        focused -> Color.White.copy(alpha = 0.7f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (focused) 14.dp else 0.dp,
                shape = CardShape,
                ambientColor = if (moving) Color(0xFF66D9E8) else Color.White,
                spotColor = if (moving) Color(0xFF66D9E8) else Color.White,
            )
            .clip(CardShape)
            .border(if (focused || moving) 2.dp else 0.dp, borderColor, CardShape)
            .onFocusChanged { focused = it.isFocused; onFocusChange?.invoke(it.isFocused) }
            // Long press by hand: a 550ms timer from the first key-down, plus key
            // repeats as a second signal (covers remotes that send no repeats and
            // remotes that only send repeats). Fires on key-up so the menu that
            // opens can't swallow the tail of the same press.
            .onPreviewKeyEvent { e ->
                val select = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                when {
                    onLongClick == null -> false
                    select && e.type == KeyEventType.KeyDown -> {
                        if (e.nativeKeyEvent.repeatCount == 0) {
                            longFired = false
                            holdJob?.cancel()
                            holdJob = scope.launch { delay(550); longFired = true }
                        } else {
                            longFired = true
                        }
                        e.nativeKeyEvent.repeatCount > 0
                    }
                    select && e.type == KeyEventType.KeyUp -> {
                        holdJob?.cancel(); holdJob = null
                        if (longFired) {
                            longFired = false
                            onLongClick()
                            true
                        } else false
                    }
                    e.key == Key.Menu && e.type == KeyEventType.KeyUp -> {
                        onLongClick()
                        true
                    }
                    else -> false
                }
            }
            .clickable(onClick = onClick)
    ) {
        content(focused)
    }
}

@Composable
private fun AppMenuDialog(
    app: TvApp,
    isOnHome: Boolean,
    onDismiss: () -> Unit,
    onMove: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .padding(24.dp)
                .width(300.dp)
        ) {
            BasicText(
                app.label,
                style = TextStyle(color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(16.dp))
            MenuButton(if (isOnHome) "Remove from home" else "Add to home", onToggleFavorite)
            if (onMove != null) MenuButton("Move", onMove)
            MenuButton("App info", onAppInfo)
            MenuButton("Uninstall", onUninstall)
            MenuButton("Cancel", onDismiss)
        }
    }
}

@Composable
private fun MenuButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        BasicText(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = TextStyle(color = if (focused) Color(0xFF16171C) else TextPrimary, fontSize = 16.sp),
        )
    }
}
