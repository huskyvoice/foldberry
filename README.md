[English](README.en.md) | 日本語

# FoldBerry

Galaxy Z Fold8 の画面に、BlackBerry(Classic / BB10 世代)の本体を再現するホームアプリ(ランチャー)です。
画面に描いた物理 QWERTY キーボードを、文字入力ではなく「アプリを起動するキー」として使います。

> FoldBerry は非公式のファンメイドアプリです。BlackBerry Limited、Samsung Electronics とは一切関係ありません。

<p align="center">
  <img src="screenshots/shot-cover.png" alt="カバー画面" height="420">
  &nbsp;
  <img src="screenshots/shot-main-portrait.png" alt="メイン画面(縦向き)" height="420">
</p>
<p align="center">左: カバー画面 / 右: メイン画面(縦向き)</p>
<p align="center">
  <img src="screenshots/shot-main.png" alt="メイン画面(横向き・カレンダー)" height="300">
  &nbsp;
  <img src="screenshots/shot-main-hub.png" alt="メイン画面(横向き・Hub)" height="300">
</p>
<p align="center">メイン画面(横向き) 左: カレンダー / 右: Hub</p>

## 主な機能

- **キーでアプリを起動**: Q〜M の文字キー・記号キーなど 35 キーに好きなアプリを割り当て、タップで起動します。キーの長押しで割り当てを変更できます。
- **機能列**: 送話(電話)・メニュー(設定)・トラックパッド(アプリ一覧)・戻る・終話(ホームに戻る)。送話・戻る・終話にもアプリを割り当てられます。
- **カバー画面とメイン画面の両方に対応**: 開閉に合わせてレイアウトが切り替わり、割り当ては両方で共有します。
- **BB10 風の画面部**: 自前のステータス行(Wi-Fi・SIM ごとの電波・Bluetooth・電池)、時計と種類別の通知数、予定のある日に点の付く月のカレンダー、ショートカットの行(ライト・マナー・自動回転・QR・カメラ)。アラームの設定はステータス行のアイコンで分かります。
- **音楽と次の予定**: 再生中の音楽の操作(再生・一時停止・前へ・次へ)と、今日・明日の直近 2 件の予定を表示します。
- **Hub**: 通知の一覧をカレンダーと入れ替えて表示できます(見出しを下へ払うとフリップして切り替え)。行を横に払うと 1 件、「すべて消去」で表示中の通知をまとめて消せます。
- **アプリ一覧**: ページ式の一覧。上の検索欄で名前から絞り込めます。割り当て済みのアプリにはバッジが付きます。長押しで割り当て・アプリ情報・アンインストール。
- **フォルダ**: メイン画面の自由枠に、最大 4 個のアプリをまとめたフォルダを置けます。
- **設定**: 背景(プリセット・写真)、言語(日本語 / English)、時計の書式、触覚のオン/オフ、割り当て一覧、設定の書出し/読込み。

## 動作環境

| 項目 | 内容 |
|---|---|
| 端末 | Galaxy Z Fold8(動作確認はこの端末のみ) |
| OS | Android 15 以上(minSdk 35) |

同じ折りたたみの Galaxy Z Fold Ultra でも使えるかもしれませんが、試していないため分かりません。
ほかの折りたたみ端末や通常のスマートフォンでも起動はするはずですが、未確認です。レイアウトが崩れることがあります。

## インストール

1. [Releases の最新版](https://github.com/huskyvoice/foldberry/releases/latest)から `FoldBerry.apk` を端末にダウンロードします([直接ダウンロード](https://github.com/huskyvoice/foldberry/releases/latest/download/FoldBerry.apk))。
2. 端末でファイルを開き、インストールします。「提供元不明のアプリ」の許可を求められたら、ダウンロードに使ったアプリ(ブラウザなど)に許可してください。
3. 端末の「設定 → アプリ → 標準アプリ → ホーム画面」で FoldBerry を選びます。FoldBerry の設定(メニューキー)の「既定のホームアプリ」からも開けます。

更新するときは、Releases から新しい `FoldBerry.apk` をダウンロードし、そのまま上書きインストールしてください。割り当てと設定は残ります。

> **注意**: アンインストールすると割り当てと設定は消えます(端末のバックアップの対象外です)。アンインストールや機種変更の前に、設定の「設定を書き出す」でファイルに保存してください。

## ロック画面用の壁紙

カバー画面とメイン画面のロック画面に合わせた壁紙を用意しています。FoldBerry の背景とキーボードにロゴを重ねたもので、ロックを解除すると FoldBerry のホームにそのままつながる見た目になります。
メイン画面用は、左半分にカバー用と同じ絵を置き、右半分にロゴを縦に並べて、左のロゴと同じ高さの 1 つだけを色付きにしています。

<p align="center">
  <img src="wallpaper/FoldBerry-lockscreen-cover.png" alt="カバー画面用の壁紙" height="360">
  &nbsp;
  <img src="wallpaper/FoldBerry-lockscreen-main.png" alt="メイン画面用の壁紙" height="360">
</p>
<p align="center">左: カバー画面用 / 右: メイン画面用</p>

| 画面 | ファイル | 大きさ |
|---|---|---|
| カバー画面 | [`wallpaper/FoldBerry-lockscreen-cover.png`](wallpaper/FoldBerry-lockscreen-cover.png) | 1248×1972 |
| メイン画面 | [`wallpaper/FoldBerry-lockscreen-main.png`](wallpaper/FoldBerry-lockscreen-main.png) | 2448×1848(横向き) |

端末に保存し、それぞれの画面のロック画面の壁紙に設定してください。

## 初回の設定(任意)

次の許可はどれも任意です。許可しない場合は、その表示が出ないだけで、ほかの機能はそのまま使えます。

| 許可 | 使う機能 | 許可の方法 |
|---|---|---|
| 付近のデバイス・電話 | ステータス行の Bluetooth と SIM ごとの電波 | 初回起動時のダイアログ(後からはアプリ情報で変更) |
| 通知へのアクセス | 時計の横の通知数、Hub、音楽の操作 | 設定の「通知の件数を表示」から許可画面を開く |
| カレンダー | 次の予定、カレンダーの点 | 予定の場所(「予定を表示」)を押すと確認が出る |
| システム設定の変更 | ショートカットの行の自動回転 | 自動回転を初めて押したときに許可画面を開く |

## プライバシー

FoldBerry は**インターネット通信を一切行いません**(インターネットの権限を持っていません)。
通知の内容・アプリの一覧・割り当てなどの情報は端末の中だけで使い、外部に送ることはありません。
広告・解析・クラッシュレポートの仕組みも入っていません。

## 自分でビルドする

ビルドはすべて Docker のコンテナの中で行います。PC に必要なのは Docker と Git Bash(Windows の場合)だけです。

```sh
scripts/fb.sh doctor     # 環境の確認
scripts/fb.sh keygen     # 署名鍵を作る(初回のみ)
scripts/fb.sh check      # lint と単体テスト
scripts/fb.sh build      # リリースビルド → dist/FoldBerry.apk
```

ワイヤレスデバッグで端末に入れる場合は次のとおりです(IP とポートは端末の「ワイヤレスデバッグ」の画面に出ます)。

```sh
scripts/fb.sh pair <IP> <ペアリング用ポート> <コード>   # 初回のみ
scripts/fb.sh connect <IP> <接続用ポート>
scripts/fb.sh install
```

自分でビルドした APK は自分の鍵で署名されるため、配布している `FoldBerry.apk` の上に上書きインストールはできません(先にアンインストールが必要です)。

## 商標・免責

- BlackBerry は BlackBerry Limited の商標です。Galaxy、Galaxy Z Fold は Samsung Electronics Co., Ltd. の商標です。その他の名称は各社の商標です。
- FoldBerry は上記の各社が開発・承認・支援するものではありません。BlackBerry のロゴや公式の素材は使っておらず、アイコンやキーのデザインはすべて自作です。
- 本ソフトウェアは無保証で提供されます。使用によって生じたいかなる損害についても、作者は責任を負いません(詳しくは [LICENSE](LICENSE))。

## ライセンス

[MIT License](LICENSE)
