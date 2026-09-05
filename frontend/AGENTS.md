Ui,UiStateはバックエンドの情報を知っていてはいけない。何を表示したいかで記述する
UiにUi以外のロジックを書かない。ロジックはViewModelに記述する。

## UiState の Boolean の名前

どの部品がどうなるかで名付ける。`canX` / `isX` は「できるか」というドメインの可否になり、
画面が何をするかが名前から落ちる。

```kotlin
// 名前が「保存できるか」までしか言っていない。画面が何をするかは KDoc にしか無い
/** false の間は登録のボタンを押せなくする */
val canSave: Boolean

// 名前で分かる。KDoc は要らない
val saveButtonEnabled: Boolean
```

「〜の間はボタンを押せなくする」「〜でボタンの文字が変わる」と説明したくなったら、それは名前。
KDoc に残すのは、名前にしても分からない理由だけ。

```kotlin
/** 閉じるとこの画面ごと消えて登録も打ち切られるので、登録の途中は false になる */
val closeEnabled: Boolean
```

`closeEnabled` のように部品が 1 つに決まらないもの（やめるボタンと、枠の外を押したときの両方）は、
部品名を入れず操作で名付ける。

## 表示条件は UiState が組み立てる

Composable の中で条件を組み立てない。どういうときに押せるかは表示の判断で、UI の仕事ではない。

```kotlin
// Ui にロジックがある
OutlinedTextField(enabled = !uiState.fetching && !uiState.saving)

// 組み立てた結果を UiState が持つ
OutlinedTextField(enabled = uiState.urlInputEnabled)
```
