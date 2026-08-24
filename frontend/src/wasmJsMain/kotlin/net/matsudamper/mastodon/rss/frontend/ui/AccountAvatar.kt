package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AccountAvatar(
    username: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val colors = avatarColors(username)
    Box(
        modifier =
        modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = username.firstOrNull()?.uppercase() ?: "",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

fun avatarColors(username: String): List<Color> {
    val palette =
        listOf(
            Color(0xFF4A3FD1) to Color(0xFF7B6FF0),
            Color(0xFF1E7A6F) to Color(0xFF3FB8A6),
            Color(0xFFB05A1E) to Color(0xFFE79A4B),
            Color(0xFF8C2F6B) to Color(0xFFD167AC),
            Color(0xFF2F5FA8) to Color(0xFF6795DE),
        )

    val index = (username.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }) % palette.size
    val (start, end) = palette[index]
    return listOf(start, end)
}
