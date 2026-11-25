package com.banglatv.app

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0A0E1A)
val BgTop = Color(0xFF1B1646)
val Surface1 = Color(0xFF151B2E)
val SurfaceHi = Color(0xFF222B47)
val Accent = Color(0xFF7C5CFF)
val AccentDark = Color(0xFF4B3AA8)
val Cyan = Color(0xFF22D3EE)
val Pink = Color(0xFFFF4D8D)
val TextDim = Color(0xFF9AA3BD)

private val palette = listOf(
    Color(0xFF7C5CFF), Color(0xFF22D3EE), Color(0xFFFF4D8D), Color(0xFFFFB020),
    Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFFF472B6), Color(0xFFFB7185),
)

@Composable
fun BanglaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = Cyan,
            background = Bg,
            surface = Surface1,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        content = content,
    )
}

fun Context.isTv(): Boolean {
    val m = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return m?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

/** Round avatar with the channel's first letter, used when there is no logo. */
@Composable
fun Initial(name: String, size: Int = 44) {
    val c = palette[(name.hashCode() and 0x7fffffff) % palette.size]
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.45f).sp,
        )
    }
}

/** Button that is clearly highlighted when focused with a TV remote and works with touch. */
@Composable
fun FocusButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: Shape = RoundedCornerShape(14.dp),
    content: @Composable RowScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "scale")
    val bg = when {
        focused -> Accent
        selected -> AccentDark
        else -> SurfaceHi
    }
    Row(
        modifier
            .scale(scale)
            .clip(shape)
            .background(bg)
            .then(if (focused) Modifier.border(2.dp, Cyan, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}
