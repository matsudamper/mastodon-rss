package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.browser.window

/** 画面の中身の最大幅。これ以上広い画面では左右に余白を作って中央に寄せる */
val ContentMaxWidth = 1040.dp

/** 1 カラムと 2 カラムを切り替える幅。タブレット縦を 1 カラム側に入れている */
val WideBreakpoint = 900.dp

/**
 * 見出し付きの枠。画面の中の 1 かたまりを表す。
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

/**
 * 短い印。アカウントの種類や取得状態のように、一目で分かればよいものに使う。
 */
@Composable
fun AppBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * 「項目名」とその値。
 *
 * 値は項目名の下に置く。横に並べると、値が URL のように長いときに
 * 項目名が潰れて読めなくなる。広い画面でもこの型の値は 2 カラムの
 * 狭い方に入るので、幅で出し分けても得るものが無い。
 */
@Composable
fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val valueColor =
        if (onClick == null) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.primary
        }

    val valueModifier =
        if (onClick == null) {
            Modifier
        } else {
            Modifier.clickable(onClick = onClick)
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = valueModifier,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            textDecoration = if (onClick == null) null else TextDecoration.Underline,
        )
    }
}

/**
 * 押せる文字。画面の中のリンクに使う。
 */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.clickable(onClick = onClick),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
}

/**
 * 状態を表す丸。取得の成否のように、色だけで分かるものに添える。
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
        ) {}
    }
}

/**
 * 外部サイトを別タブで開く。
 *
 * canvas の上に描いているので `<a>` は無く、リンクは自分で開くことになる。
 * `noopener` を付けないと開いた先から `window.opener` でこちらを操作できる。
 */
fun openExternalLink(url: String) {
    window.open(url, "_blank", "noopener,noreferrer")
}

/** 枠線 1 本ぶんの色。区切り線に使う */
@Composable
fun dividerColor(): Color = MaterialTheme.colorScheme.outlineVariant

/** 枠線付きの箱。区切りたいが見出しは要らないものに使う */
@Composable
fun OutlinedBox(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}
