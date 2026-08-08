// バンドルが追加で読むファイル（.wasm など）を root 絶対で引く。
//
// 既定は "auto" で、読み込まれた <script> の URL から推測する。index.html は
// どの画面のパスでも同じものが返るため、/admin/password-hash のような深いパスでは
// 推測がずれる。ずれると .wasm の取得が 404 になり、画面が真っ白になるだけで
// 原因が見えない。root からしか配信しないので、固定してしまう。
config.output = config.output || {};
config.output.publicPath = "/";
