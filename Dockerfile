# syntax=docker/dockerfile:1

# ---- ビルド ----
# native-image のビルドには GraalVM が要るが、実行時には要らない。
# ステージを分けて、最終イメージに JDK を持ち込まないようにする
FROM ghcr.io/graalvm/native-image-community:25 AS build

# このイメージは最小構成で xargs が入っておらず、gradlew が
# 「xargs is not available」で起動できない
RUN microdnf install -y findutils \
    && microdnf clean all

WORKDIR /src

# 先にビルド定義だけを入れて依存を解決しておく。
# こうするとソースだけ変えたときに依存の再ダウンロードが起きない
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY backend/build.gradle.kts ./backend/
COPY backend/crypto/build.gradle.kts ./backend/crypto/
COPY backend/repository/build.gradle.kts ./backend/repository/
COPY backend/rss/build.gradle.kts ./backend/rss/
COPY frontend/build.gradle.kts ./frontend/
COPY shared/graphql/build.gradle.kts ./shared/graphql/
# build-logic は複合ビルドなので、定義だけでなくソースごと要る。
# :backend:repository がここのプラグインを適用するため、構成の時点で読まれる
COPY build-logic ./build-logic

# 出力は捨てるが失敗は握り潰さない。ここで転ぶならビルド環境の問題で、
# 先に進んでも本ビルドで同じ理由で落ちるだけ
RUN ./gradlew --no-daemon :backend:dependencies > /dev/null

COPY . .

RUN ./gradlew --no-daemon :backend:nativeCompile

# ---- 実行 ----
# native バイナリは動的リンクなので、ビルド時より古い glibc のイメージに置くと起動しない。
# ビルドステージは Oracle Linux 9 (glibc 2.34)、こちらは Debian 13 (glibc 2.41) で、
# 新しい側に置いているため動く
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

COPY --from=build /src/backend/build/native/nativeCompile/mastodon-rss /usr/local/bin/mastodon-rss

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
