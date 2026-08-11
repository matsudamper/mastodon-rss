// 開発サーバー (8081) に来た GraphQL のリクエストを backend (8080) に転送する。
//
// 画面は dev サーバーから、API は backend から返ることになるが、ブラウザから見た
// オリジンは 8081 のまま 1 つなので、CORS もセッション Cookie の SameSite も
// 緩めずに済む。直接 8080 を叩く形にすると、開発時だけ緩めた設定が必要になり、
// 本番と違う条件で動かすことになる。
//
// この設定が効くのは wasmJsBrowserDevelopmentRun のときだけ。配布物を
// backend から配信する場合は、そもそも同じオリジンなので転送は要らない。
config.devServer = config.devServer || {};
config.devServer.proxy = [
    {
        context: ["/graphql"],
        target: "http://localhost:8080",
    },
];
