#!/bin/sh
# /data の所有者をコンテナの中で合わせてから、サーバーをその uid で起動する。
#
# イメージ側で `chown app:app /data` しても、そこにボリュームを被せると隠れてしまう。
# 所有者がボリュームに伝わるのは Docker の copy-up だけで、これはボリュームが
# 新規で空のときの 1 回しか走らず、バインドマウントでは走らない。環境によっては
# 新品のボリュームでも root のままになる。イメージのビルド時には決められない値なので、
# 起動のたびにここで合わせる。
#
# Dockerfile に USER app を書いていないのはこのため。最初から app で起動すると、
# root 所有で作られたボリュームをコンテナの中からは直せない。
set -e

DATA_DIR=/data
SERVER_BIN=/usr/local/bin/mastodon-rss

# サーバーを動かす uid/gid。既定はイメージに作ってある app。
# バインドマウントの場合、/data はホストから見てもこの uid の持ち物になり、
# ホストのユーザーからは書けなくなる。ホスト側でも読み書きしたいなら、
# そのユーザーの id を APP_UID/APP_GID で渡す。
APP_UID="${APP_UID:-10001}"
APP_GID="${APP_GID:-10001}"

# /usr/local/bin はローカルビルドのバイナリで差し替えられることがある。
# 差し替えに失敗していると exec の失敗だけが出て理由が分からないので、先に見る
if [ ! -x "$SERVER_BIN" ]; then
    echo "$SERVER_BIN が無いか実行できない。" >&2
    echo "ローカルビルドをマウントしている場合は ./gradlew :backend:nativeCompile を実行し、" >&2
    echo "出力されたバイナリに実行権限があるか確認すること。" >&2
    exit 1
fi

if [ "$(id -u)" = "0" ]; then
    # 名前ではなく数値で扱う。ホストの uid はコンテナの中に対応するユーザーが無いのが普通で、
    # 名前で解決させると存在しない側で落ちる
    for id_value in "$APP_UID" "$APP_GID"; do
        case "$id_value" in
            '' | *[!0-9]*)
                echo "APP_UID と APP_GID には数値の id を指定すること。" >&2
                echo "APP_UID=$APP_UID APP_GID=$APP_GID" >&2
                exit 1
                ;;
        esac
    done

    mkdir -p "$DATA_DIR"
    # -R にしているのは、以前 root で起動して root 所有のまま作られた DB や鍵を拾うため。
    # APP_UID を変えたときに、前の uid のまま残ったファイルを引き継ぐのも同じ理由
    chown -R "$APP_UID:$APP_GID" "$DATA_DIR"
    exec setpriv --reuid="$APP_UID" --regid="$APP_GID" --clear-groups "$SERVER_BIN" "$@"
fi

# compose の `user:` などで最初から非 root で起動された場合。chown する権限が無いので
# そのまま実行する。書けなければサーバー側が起動時に落ちる
exec "$SERVER_BIN" "$@"
