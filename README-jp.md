# TrialTracker

![Unit Tests](https://github.com/backfromlunch/TrialTracker/actions/workflows/tests.yml/badge.svg)
![License](https://img.shields.io/github/license/backfromlunch/TrialTracker)
![Latest Tag](https://img.shields.io/github/v/tag/backfromlunch/TrialTracker)

短期間の個人試験において、毎日の繰り返し観察結果を記録するための汎用 Android フォームエンジンアプリです（例：数週間から数か月にわたり、1日あたり約10件の記録を行う）。

FHIRの慣行とほぼ整列することを意図しています。

<table>
  <tr>
    <td align="center">
      <img src="assets/entry.png" alt="Entry screen" width="300">
    </td>
    <td align="center">
      <img src="assets/summary.png" alt="Summary screen" width="300">
    </td>
  </tr>
  <tr>
    <td align="center">入力</td>
    <td align="center">概要</td>
  </tr>
</table>

## 目標

* 毎日のデータ入力時の操作負担を最小限にする（明示的な保存ボタンを不要とし、大きなタッチ領域を使用して素早く入力できるようにする）。
* FHIR 形式に沿った `ServiceRequest` および `Questionnaire` の JSON ファイルから設定できる。
* 記録したすべての結果を、FHIR 形式に沿った `QuestionnaireResponse` JSON としてエクスポート可能（オプションで CSV にも対応）。
* 過去の日付の入力内容を確認・編集可能。
* アプリが要求する権限を最小限にする。

## 互換性

* `minSdk` = API 30（Android 11）。

## アプリの使用方法

初回起動時、アプリは「Trial and Questionnaire」画面を表示します。ここで次の 2 つの JSON ファイルを読み込む必要があります。

* 試験全体を定義する `ServiceRequest`ファイル
* 質問する具体的な項目を一覧化した `Questionnaire`ファイル

サンプルファイルについては `samples/` ディレクトリを参照してください。

2 回目以降の起動時には、「Entry」画面が表示されます。ハンバーガーメニュー（モーダルナビゲーションドロワー）から、次の画面にアクセスできます。

* **Entry** — 毎日の入力を行うメイン画面。フィールドが縦方向に一覧表示されます。すべての入力は、有効なキー入力が行われるたびに自動保存されるため、明示的な保存ボタンはありません。
* **Summary** — これまでの試験結果をカレンダー形式で表示します。
* **Trial and Questionnaire** — 現在有効な `ServiceRequest` と `Questionnaire` の読み込みおよび確認を行います。
* **Export results** — 結果を `.json` または `.csv` として出力します。
* **Settings**
* **Help**

## プライバシー / データの取り扱い

このアプリはAndroidの権限を一切宣言していません。ネットワークにはアクセスできず、共有ストレージ、カメラ、連絡先にもアクセスできません。アプリ専用の内部ストレージのみを使用します。

Android の自動バックアップは意図的に無効化されています（マニフェストの `android:allowBackup="false"`）。そのため、次のようになります。

* 機密性のある可能性があるデータがデバイス外へ送信され、クラウドに保存されることはありません。
* デバイスの紛失または破損が発生した場合、すべての試験データが失われます（定期的に手動でエクスポートし、外部に保存することで、このリスクを軽減できます）。

## アーキテクチャ / コントリビューション

アーキテクチャに関する説明、ビルド環境固有の注意事項、テストおよび lint の規約については、[ARCHITECTURE.md](ARCHITECTURE.md) を参照してください。

コントリビューションの状況については、[CONTRIBUTING.md](CONTRIBUTING.md) を参照してください。

## ライセンス

MIT — [LICENCE](LICENCE) を参照してください。
