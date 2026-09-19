# Q

## 配信

配信は GitHub Release をトリガーに `.github/workflows/release.yml` が行う。ローカルからのビルドやアップロードはしない。

| ユーザーの指示 | 作るもの | ワークフローの動作 |
| --- | --- | --- |
| 「Firebase App Distribution に上げて」 | プレリリース | `assembleRelease` の APK を Firebase App Distribution へ |
| 「Play Store に上げて」 | リリース | `bundleRelease` の AAB を Play Store へ |

この指示自体が実行許可なので、改めて確認は取らずに `gh` で作成する。ただし下記の前提を先に検査し、満たさない場合は作成せずに報告する。

- `gh auth status` が通ること
- 対象コミットが `origin/master` の履歴に含まれること（ワークフロー側でも検査するが、先に弾く）
- `app/build.gradle` の `versionName` とタグのバージョン部分が一致すること
- 同名のタグ・リリースが存在しないこと
- `versionCode` が前回の Play リリースから上がっていること（Play は同じ `versionCode` を受け付けない）

タグ名は `versionName` が `3.1.0` のとき、プレリリースが `v3.1.0-rc1`（末尾は未使用の番号）、リリースが `v3.1.0`。

```sh
gh release create v3.1.0-rc1 --prerelease --target master --title v3.1.0-rc1 --generate-notes
gh release create v3.1.0 --target master --title v3.1.0 --generate-notes
```

プレリリースを後から正式リリースへ昇格させる場合は次のようにする。`released` イベントが飛び、Play Store へのアップロードが走る。

```sh
gh release edit v3.1.0-rc1 --prerelease=false
```

振り分けは Release の prerelease フラグで決まり、タグ名のサフィックスは見ていない。
