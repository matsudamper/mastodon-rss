package dev.matsudamper.mastodonrss.frontend

import dev.matsudamper.mastodonrss.admin.api.AdminApiPaths
import kotlinx.browser.window
import kotlin.js.ExperimentalWasmJsInterop

/**
 * 管理画面の中の画面。
 *
 * @param path URL の末尾。空文字はトップ
 */
internal enum class AdminRoute(
    val path: String,
) {
    Home(""),
    PasswordHash("password-hash"),
    ;

    companion object {
        fun ofPath(path: String): AdminRoute = entries.firstOrNull { it.path == path } ?: Home
    }
}

/**
 * URL と画面の対応。
 *
 * 画面の切り替えを状態だけで持たず URL も動かしているのは、ハッシュ生成の画面を
 * 人に伝えられるようにするため。「起動 → ハッシュを作る → 環境変数に入れて起動し直す」
 * という手順を README に書くとき、URL が無いと説明できない。
 *
 * `:backend` は `/admin/` 以下でファイルとして存在しないパスに index.html を返すので、
 * ハッシュ生成の画面を直接開いてもリロードしても同じ画面になる。
 */
internal object AdminRouter {
    /**
     * URL の基準。
     *
     * `:backend` から配信されるときは `/admin` の下に居るが、`:frontend` の開発サーバー
     * (8081) では root に居る。どちらでも動くよう実行時の URL から決める。
     */
    private val basePath: String
        get() =
            if (window.location.pathname.startsWith(AdminApiPaths.BASE)) {
                AdminApiPaths.BASE
            } else {
                ""
            }

    fun currentRoute(): AdminRoute =
        AdminRoute.ofPath(
            window.location.pathname
                .removePrefix(basePath)
                .trim('/'),
        )

    /** 画面を切り替える。戻るボタンで戻れるよう履歴に積む */
    @OptIn(ExperimentalWasmJsInterop::class)
    fun navigate(route: AdminRoute) {
        window.history.pushState(null, "", "$basePath/${route.path}")
    }

    /**
     * 戻る・進むで URL が変わったときに呼ばれるようにする。
     *
     * `onpopstate` に直接入れているのは、`addEventListener` に渡す `EventListener` を
     * Kotlin/Wasm 側で作る手間を避けるため。管理画面が使う口はこれ 1 つしかない。
     */
    @OptIn(ExperimentalWasmJsInterop::class)
    fun onRouteChanged(listener: (AdminRoute) -> Unit) {
        window.onpopstate = { listener(currentRoute()) }
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    fun clearRouteChangedListener() {
        window.onpopstate = null
    }
}
