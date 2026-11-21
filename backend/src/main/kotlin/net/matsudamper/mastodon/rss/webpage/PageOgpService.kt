package net.matsudamper.mastodon.rss.webpage

import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorUrls
import net.matsudamper.kotpub.actor.StoredActorAppearances
import net.matsudamper.kotpub.actor.StoredActorProfiles
import net.matsudamper.kotpub.entity.PublicNoteId
import net.matsudamper.kotpub.note.NoteStore
import net.matsudamper.kotpub.note.StoredNote
import net.matsudamper.mastodon.rss.shared.WebPagePath

/**
 * 画面の URL を他のサービスに貼ったときに出すカードの中身を返す。
 *
 * 画像はアカウントのアイコンだけを出す。投稿のリンク先の記事の画像は、
 * 閲覧注意かどうかを確かめずに取り込んだ他人のサイトのものなので、こちらからは広めない。
 */
class PageOgpService(
    private val directory: ActorDirectory,
    private val profiles: StoredActorProfiles,
    private val appearances: StoredActorAppearances,
    private val notes: NoteStore,
) {
    /**
     * @return アカウントが無ければ null
     */
    fun account(username: String): PageOgp? {
        val actor = directory.resolve(username) ?: return null
        val profile = profiles.find(actor.username)
        return PageOgp(
            type = PageOgpType.Profile,
            title = titleOf(actor = actor, displayName = profile.displayName),
            description = profile.summary?.let { HtmlText.plainDescription(it) }?.ifEmpty { null },
            url = "https://${actor.domain}${WebPagePath.account(actor.username)}",
            imageUrl = iconUrlOf(actor),
        )
    }

    /**
     * @return 投稿が無いか、[username] の投稿でなければ null
     */
    fun note(
        username: String,
        publicId: String,
    ): PageOgp? {
        val note = notes.find(PublicNoteId(publicId)) ?: return null
        if (!note.username.equals(username, ignoreCase = true)) return null
        val actor = directory.resolve(note.username) ?: return null
        return PageOgp(
            type = PageOgpType.Article,
            title = titleOf(actor = actor, displayName = profiles.find(actor.username).displayName),
            description = noteDescriptionOf(note),
            url = "https://${actor.domain}${WebPagePath.accountNote(username = actor.username, noteId = publicId)}",
            imageUrl = iconUrlOf(actor),
        )
    }

    /**
     * 閲覧注意の投稿と注意書きのある投稿は、本文を出さずに注意書きかその旨を出す
     */
    private fun noteDescriptionOf(note: StoredNote): String? {
        val contentWarning = note.summary?.let { HtmlText.description(it) }?.ifEmpty { null }
        return when {
            contentWarning != null -> contentWarning
            note.sensitive -> SENSITIVE_DESCRIPTION
            else -> HtmlText.description(note.contentHtml).ifEmpty { null }
        }
    }

    private fun iconUrlOf(actor: ActorUrls): String? = appearances.find(actor.username).iconVersion?.let { actor.icon(it) }

    private fun titleOf(
        actor: ActorUrls,
        displayName: String?,
    ): String {
        val name = displayName?.trim()?.ifEmpty { null } ?: actor.username
        return "$name (${actor.mention})"
    }

    private companion object {
        const val SENSITIVE_DESCRIPTION = "閲覧注意の投稿"
    }
}

/**
 * @param description 出すものが無ければ null
 * @param imageUrl 出す画像が無ければ null
 */
data class PageOgp(
    val type: PageOgpType,
    val title: String,
    val description: String?,
    val url: String,
    val imageUrl: String?,
)

enum class PageOgpType(
    val ogType: String,
) {
    Profile("profile"),
    Article("article"),
}
