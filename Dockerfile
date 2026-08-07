# syntax=docker/dockerfile:1

# ---- ビルド ----
# native-image のビルドには GraalVM が要るが、実行時には要らない。
# ステージを分けて、最終イメージに JDK を持ち込まないようにする
FROM ghcr.io/graalvm/native-image-community:21 AS build

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
COPY crypto/build.gradle.kts ./crypto/
COPY repository/build.gradle.kts ./repository/
COPY frontend/build.gradle.kts ./frontend/

# 出力は捨てるが失敗は握り潰さない。ここで転ぶならビルド環境の問題で、
# 先に進んでも本ビルドで同じ理由で落ちるだけ
RUN ./gradlew --no-daemon :backend:dependencies > /dev/null

COPY . .

RUN ./gradlew --no-daemon :backend:nativeCompile

# ---- 実行 ----
# native バイナリは動的リンクなので、ビルド時より古い glibc のイメージに置くと起動しない。
# ビルドステージは Oracle Linux 9 (glibc 2.34)、こちらは Debian 12 (glibc 2.36) で、
# 新しい側に置いているため動く
FROM debian:13-slim

# curl は HEALTHCHECK で /healthz を叩くためだけに入れている
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app --create-home app \
    && mkdir -p /data \
    && chown app:app /data

COPY --from=build /src/backend/build/native/nativeCompile/mastodon-rss /usr/local/bin/mastodon-rss

# DB はボリュームに置く。コンテナを作り直してもフォロワーが消えないように
ENV DB_PATH=/data/mastodon-rss.db \
    HOST=0.0.0.0 \
    PORT=8080

VOLUME ["/data"]
EXPOSE 8080

USER app
WORKDIR /app

# 起動時にマイグレーションと書き込み確認が走るので、
# /healthz が 200 を返せば DB まで通っていることになる
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${PORT}/healthz" > /dev/null || exit 1

ENTRYPOINT ["/usr/local/bin/mastodon-rss"]
