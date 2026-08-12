# syntax=docker/dockerfile:1

# native バイナリのビルドはここではやらない。GitHub Actions
# (.github/workflows/publish.yml) で ./gradlew :backend:nativeCompile を回し、
# その出力をコンテキスト直下に mastodon-rss として置いてから docker build する。
# ここは出来上がったものを置くだけ。
# 手元でイメージを作る場合も同じで、先にバイナリを用意しておくこと
#
#   ./gradlew :backend:nativeCompile
#   cp backend/build/native/nativeCompile/mastodon-rss .
#   docker build -t mastodon-rss .
#
# native バイナリは動的リンクなので、ビルドしたところより古い glibc のイメージに
# 置くと起動しない。ここは Debian 13 (glibc 2.41) で、GHA の ubuntu-latest
# (Ubuntu 24.04, glibc 2.39) より新しいため動く。runner が上がって逆転したら、
# ここの base image も上げること。publish.yml の起動確認で気付ける
FROM debian:13-slim

# curl は HEALTHCHECK で /healthz を叩くためだけに入れている。
# util-linux は entrypoint が権限を落とすのに使う setpriv のために明示している
# （基本パッケージなので実際には既に入っているが、消えたらビルドで気付けるようにする）
#
# /app は app の home であり WORKDIR でもある。useradd はこれを 0750 で作るので、
# APP_UID に別の uid を渡すと cwd に入れなくなる。ktor は起動時に cwd を解決するため、
# FileNotFoundException: /app/. で起動前に落ちる。どの uid でも辿れるようにしておく
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl util-linux \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --create-home app \
    && chmod 0755 /app \
    && mkdir -p /data \
    && chown app:app /data

# 実行権限は元のファイルの属性に頼らず、ここで付ける
COPY --chmod=0755 mastodon-rss /usr/local/bin/mastodon-rss

# entrypoint をバイナリと同じ /usr/local/bin に置かないのは、ローカルでビルドした
# バイナリを差し替えるときに /usr/local/bin ごとマウントするため。同じ場所に置くと
# entrypoint がマウントに隠されて、コンテナが起動すらできなくなる
COPY --chmod=0755 docker-entrypoint.sh /docker-entrypoint.sh

# DB とアクターの秘密鍵はボリュームに置く。コンテナを作り直しても
# フォロワーが消えず、アクターも同一人物のままになるように
ENV DB_PATH=/data/mastodon-rss.db \
    ACTOR_PRIVATE_KEY_PATH=/data/actor-private-key.pem \
    HOST=0.0.0.0 \
    PORT=8080

VOLUME ["/data"]
EXPOSE 8080

WORKDIR /app

# 起動時にマイグレーションと書き込み確認が走るので、
# /healthz が 200 を返せば DB まで通っていることになる
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${PORT}/healthz" > /dev/null || exit 1

ENTRYPOINT ["/docker-entrypoint.sh"]
