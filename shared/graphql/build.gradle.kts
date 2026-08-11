plugins {
    `java-library`
}

// GraphQL のスキーマだけを持つ。コードは無い。
// :backend と :frontend が見る唯一の定義で、どちらかの下に置くと相手のビルドが
// そのディレクトリを見ることになるので root に置く。
