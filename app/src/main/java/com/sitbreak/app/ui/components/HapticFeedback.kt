package com.sitbreak.app.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.ripple.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView

/**
 * 点击带震动反馈 + 轻微按压缩放，并保留 Material 涟漪。
 * 用于关键 CTA（开始/起身/检查更新等），满足"点击有明确动效与震动反馈"。
 */
@Composable
fun Modifier.hapticClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    return this
        .scale(if (pressed && enabled) 0.96f else 1f)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = ripple(),
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        )
}

/**
 * 返回一个带震动的点击回调，供 Material3 Button 的 onClick 使用，
 * 避免与 Button 内部 clickable 叠加导致双重点击。
 */
@Composable
fun rememberHapticClick(action: () -> Unit): () -> Unit {
    val view = LocalView.current
    return remember(action) {
        {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            action()
        }
    }
}
