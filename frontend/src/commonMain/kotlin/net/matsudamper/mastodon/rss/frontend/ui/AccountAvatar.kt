package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
internal fun AccountAvatar(
    username: String,
    iconUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(avatarColors(username))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = username.firstOrNull()?.uppercase() ?: "",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        AsyncImage(
            model = iconUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * ユーザー名から決まる 2 色。アイコンとヘッダーの代わりに使う。
 *
 * 同じ名前なら必ず同じ色になるようにする。開くたびに色が変わると、
 * 名前を変えながら検証しているときに見分けが付かない。
 * 画面ごとに色が変わらないよう、名前から色を決めるのはここだけにする。
 */
internal fun avatarColors(username: String): List<Color> {
    val palette = listOf(
        Color(0xFF4A3FD1) to Color(0xFF7B6FF0),
        Color(0xFF1E7A6F) to Color(0xFF3FB8A6),
        Color(0xFFB05A1E) to Color(0xFFE79A4B),
        Color(0xFF8C2F6B) to Color(0xFFD167AC),
        Color(0xFF2F5FA8) to Color(0xFF6795DE),
    )
    val (start, end) = palette[username.hashCode().mod(palette.size)]
    return listOf(start, end)
}
