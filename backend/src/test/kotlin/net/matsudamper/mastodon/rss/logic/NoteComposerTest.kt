package net.matsudamper.mastodon.rss.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NoteComposerTest {
    @Test
    fun `段落と改行だけの HTML にする`() {
        val composed = assertIs<NoteComposer.ComposeResult.Composed>(
            NoteComposer.compose("こんにちは\n世界\n\n2 つめの段落"),
        )

        assertEquals("<p>こんにちは<br>世界</p><p>2 つめの段落</p>", composed.contentHtml)
    }

    @Test
    fun `HTML はそのまま流さない`() {
        val composed = assertIs<NoteComposer.ComposeResult.Composed>(
            NoteComposer.compose("<script>alert(1)</script>"),
        )

        // 管理画面を通して任意のタグをフォロワーに配れないようにする
        assertEquals("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>", composed.contentHtml)
    }

    @Test
    fun `空白だけの本文は弾く`() {
        assertIs<NoteComposer.ComposeResult.Empty>(NoteComposer.compose("   "))
    }

    @Test
    fun `上限ちょうどは通り 1 文字でも超えたら弾く`() {
        assertIs<NoteComposer.ComposeResult.Composed>(NoteComposer.compose("あ".repeat(NoteComposer.MAX_LENGTH)))
        assertIs<NoteComposer.ComposeResult.TooLong>(NoteComposer.compose("あ".repeat(NoteComposer.MAX_LENGTH + 1)))
    }

    @Test
    fun `長さは書いた人が数えるとおりに サロゲートペアを 1 文字として数える`() {
        // Java の文字列長で数えると、絵文字だけの本文は上限の半分で弾かれる
        assertIs<NoteComposer.ComposeResult.Composed>(NoteComposer.compose("\uD83D\uDC31".repeat(NoteComposer.MAX_LENGTH)))
    }
}
