#!/bin/sh
# /data の所有者をコンテナの中で合わせてから、サーバーを app として起動する。
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

# /usr/local/bin はローカルビルドのバイナリで差し替えられることがある。
# 差し替えに失敗していると exec の失敗だけが出て理由が分からないので、先に見る
if [ ! -x "$SERVER_BIN" ]; then
    echo "$SERVER_BIN が無いか実行できない。" >&2
    echo "ローカルビルドをマウントしている場合は ./gradlew :backend:nativeCompile を実行し、" >&2
    echo "出力されたバイナリに実行権限があるか確認すること。" >&2
    exit 1
fi

if [ "$(id -u)" = "0" ]; then
    mkdir -p "$DATA_DIR"
    # -R にしているのは、以前 root で起動して root 所有のまま作られた DB や鍵を拾うため
    chown -R app:app "$DATA_DIR"
    exec setpriv --reuid=app --regid=app --clear-groups "$SERVER_BIN" "$@"
fi

# compose の `user:` などで最初から非 root で起動された場合。chown する権限が無いので
# そのまま実行する。書けなければサーバー側が起動時に落ちる
exec "$SERVER_BIN" "$@"
