// 開発サーバー (8081) に来た GraphQL を backend (8080) に転送する。
config.devServer = config.devServer || {};
config.devServer.proxy = [
    {
        context: ["/graphql"],
        target: "http://localhost:8080",
    },
];
