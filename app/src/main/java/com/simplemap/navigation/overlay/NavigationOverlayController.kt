package com.simplemap.navigation.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.simplemap.MainActivity
import com.simplemap.navigation.NavigationPhase
import com.simplemap.navigation.NavigationUiState
import kotlin.math.abs
import kotlin.math.roundToInt

// Overlay-local palette, kept visually consistent with the in-app navigation panels.
private val OverlayPanelColor = Color(0xF21A2B42)
private val OverlaySecondaryText = Color(0xFFB9CBE4)
private val OverlayAccentText = Color(0xFF8EC7FF)
private val OverlayShape = RoundedCornerShape(18.dp)

/**
 * Floating navigation card shown over other apps while navigation keeps running
 * in the background. All methods must be called on the main thread.
 */
class NavigationOverlayController {

    private var appContext: Context? = null
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var uiState by mutableStateOf(NavigationUiState())

    val isShowing: Boolean
        get() = overlayView != null

    fun show(context: Context) {
        checkMainThread()
        if (overlayView != null) return
        if (!NavigationOverlayPermission.canDrawOverlays(context)) return
        val applicationContext = context.applicationContext
        val manager = applicationContext.getSystemService(WindowManager::class.java) ?: return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = INITIAL_OFFSET_PX
            y = INITIAL_OFFSET_PX
        }
        val owner = OverlayLifecycleOwner()
        owner.create()
        val view = ComposeView(applicationContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    NavigationOverlayCard(state = uiState)
                }
            }
        }
        attachDragAndClick(view, applicationContext, manager, params)
        try {
            manager.addView(view, params)
        } catch (exception: RuntimeException) {
            owner.destroy()
            return
        }
        owner.resume()
        appContext = applicationContext
        windowManager = manager
        overlayView = view
        layoutParams = params
        lifecycleOwner = owner
    }

    fun update(state: NavigationUiState) {
        checkMainThread()
        uiState = state
    }

    fun hide() {
        checkMainThread()
        val view = overlayView ?: return
        try {
            windowManager?.removeView(view)
        } catch (exception: IllegalArgumentException) {
            // View was already detached by the system; nothing else to do.
        }
        view.disposeComposition()
        lifecycleOwner?.destroy()
        overlayView = null
        layoutParams = null
        windowManager = null
        lifecycleOwner = null
        appContext = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragAndClick(
        view: View,
        context: Context,
        manager: WindowManager,
        params: WindowManager.LayoutParams,
    ) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        view.setOnTouchListener { touchedView, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!dragging && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        params.x = startX + deltaX.roundToInt()
                        params.y = startY + deltaY.roundToInt()
                        if (touchedView.isAttachedToWindow) {
                            manager.updateViewLayout(touchedView, params)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        touchedView.performClick()
                        returnToApp(context)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun returnToApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        }
        context.startActivity(intent)
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "NavigationOverlayController 只能在主线程调用"
        }
    }

    private companion object {
        const val INITIAL_OFFSET_PX = 48
    }
}

/** Minimal lifecycle/saved-state owner backing the overlay ComposeView. */
private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun create() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun resume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

@Composable
private fun NavigationOverlayCard(state: NavigationUiState) {
    val instructionText = overlayInstructionText(state)
    val distanceText = formatOverlayDistance(state.maneuverDistanceMeters)
    val remainingText = "剩余 ${formatOverlayDistance(state.remainingDistanceMeters)} · " +
        formatOverlayTime(state.remainingTimeSeconds)
    Surface(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 280.dp)
            .semantics {
                contentDescription =
                    "悬浮导航提示：$instructionText，距转向 $distanceText，$remainingText，点击返回导航"
            },
        color = OverlayPanelColor,
        shape = OverlayShape,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayManeuverIcon(state)
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (state.maneuverDistanceMeters > 0) {
                    Text(
                        text = distanceText,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Text(
                    text = instructionText,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = remainingText,
                    color = OverlaySecondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun OverlayManeuverIcon(state: NavigationUiState) {
    val iconBitmap = state.maneuverIconBitmap
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap.asImageBitmap(),
            contentDescription = "转向指示图标",
            modifier = Modifier.size(40.dp),
        )
    } else {
        Text(
            text = overlayManeuverSymbol(state.maneuverIconType),
            color = OverlayAccentText,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                contentDescription = "转向指示 ${overlayManeuverLabel(state.maneuverIconType)}"
            },
        )
    }
}

private fun overlayInstructionText(state: NavigationUiState): String = when (state.phase) {
    NavigationPhase.Preparing, NavigationPhase.Calculating -> "正在规划路线"
    NavigationPhase.Arrived -> "已到达目的地"
    NavigationPhase.Failed -> state.message ?: "导航已中断"
    NavigationPhase.Navigating -> when {
        state.nextRoad.isNotBlank() -> "${overlayManeuverLabel(state.maneuverIconType)}进入${state.nextRoad}"
        state.instruction.isNotBlank() -> state.instruction
        else -> "继续行驶"
    }
}

private fun overlayManeuverSymbol(iconType: Int): String = when (iconType) {
    in RIGHT_TURN_ICON_TYPES -> "→"
    in LEFT_TURN_ICON_TYPES -> "←"
    in U_TURN_ICON_TYPES -> "↶"
    else -> "↑"
}

private fun overlayManeuverLabel(iconType: Int): String = when (iconType) {
    in RIGHT_TURN_ICON_TYPES -> "右转"
    in LEFT_TURN_ICON_TYPES -> "左转"
    in U_TURN_ICON_TYPES -> "掉头"
    else -> "直行"
}

private val RIGHT_TURN_ICON_TYPES = setOf(2, 4, 6, 10, 12)
private val LEFT_TURN_ICON_TYPES = setOf(3, 5, 7, 11, 13)
private val U_TURN_ICON_TYPES = setOf(8, 9)

private fun formatOverlayDistance(distanceMeters: Int): String = when {
    distanceMeters < 0 -> "--"
    distanceMeters < 1_000 -> "$distanceMeters 米"
    else -> "%.1f 公里".format(distanceMeters / 1_000.0)
}

private fun formatOverlayTime(remainingSeconds: Int): String {
    val minutes = (remainingSeconds.coerceAtLeast(0) + 59) / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours == 0 -> "$minutes 分钟"
        remainingMinutes == 0 -> "$hours 小时"
        else -> "$hours 小时 $remainingMinutes 分"
    }
}
