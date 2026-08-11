// 開発サーバー (8081) に来た GraphQL を backend (8080) に転送する。
// ブラウザから見たオリジンが 1 つのままなので、CORS も Cookie の SameSite も緩めずに済む。
config.devServer = config.devServer || {};
config.devServer.proxy = [
    {
        context: ["/graphql"],
        target: "http://localhost:8080",
    },
];
