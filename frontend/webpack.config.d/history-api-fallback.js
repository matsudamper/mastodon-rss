// Enable history API fallback for client-side routing
// This ensures that all routes (like /@kotlin) serve index.html
// so that the SPA can handle the routing on the client side
config.devServer = config.devServer || {};
config.devServer.historyApiFallback = true;
