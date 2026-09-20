# Q

## Media3

media3-session / media3-exoplayer / media3-ui は Google 公式の 1.11.0 を使う。公式が配布していない media3-decoder-ffmpeg だけ、ローカルでビルドしたものを GitHub Packages (https://maven.pkg.github.com/geckour/Q) に置いて参照している。認証は `secret.properties` の `gpr.usr` / `gpr.key`、CI では同名のキーを Secrets の `GPR_USER` / `GPR_TOKEN` から生成する。

FFmpeg のデコーダ構成は `script/build_my_ffmpeg.sh` にあり、特許ライセンスの都合で ac3 / eac3 / dca / mlp / truehd は外してある。

Media3 を更新するときの手順。

1. `/Users/geckour/develop/android/git/media` を新しいタグへ更新する
2. `script/build_my_ffmpeg.sh` で FFmpeg を 4 ABI 分ビルドする
3. media 側で publish する
   ```sh
   GPR_USER=... GPR_TOKEN=... ./gradlew :lib-decoder-ffmpeg:publish \
     -PmavenRepo=https://maven.pkg.github.com/geckour/Q
   ```
4. `app/build.gradle` の `media3_ver` を上げる
5. FFmpeg のバージョンやデコーダ構成を変えた場合は、`license_text_ffmpeg` の記載（バージョン、デコーダ一覧）も更新する。LGPL の表示義務があるため必須

ビルドには JDK 21 以上が必要（AGP の lint が JDK 21 の API を使うため、17 では `lintVitalAnalyzeRelease` が落ちる）。CI は 25 を使う。

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

リリースノートを書くのは Play Store へ公開するリリースだけ。GitHub Release の本文が唯一の原本で、ロケール別の見出しで区切って書く。Play の「新機能」は本文から `script/split_release_notes.py` が生成する（1 ロケール 500 文字以内、超えるとワークフローが失敗する）。プレリリースは配信先が自分たちなのでノートを書かず、本文は空でよい。Firebase のリリースノートにはタグ名が入る。

```markdown
## ja-JP
- 変更点

## en-US
- What changed
```

土台にするのは直近のプレリリースでないリリースのノート。それが Play Store に公開されていない場合（審査中のものを含む）は、その項目を引き継いだうえで新しい項目を追記する。毎回これを守れば、最新のノートに前回の公開以降の変更がすべて入っている状態が保たれる。公開済みかどうかはワークフローの成否ではなく、公開ストアページの更新日と「新機能」で確かめる。

Play へのリリースはまず下書きで作り、本文を確認してもらってから公開する。下書きの間はタグが作られず、ワークフローも走らない。

```sh
gh release create v3.2.0 --draft --target master --title v3.2.0 --notes-file <(...)
```

作成したらリリースの URL を提示し、確認の返事を待つ。公開は次のコマンド、またはユーザーが GitHub 上で行う。公開した時点でワークフローが発火する。

```sh
gh release edit v3.2.0 --draft=false
```

プレリリースはノートの確認がいらないので、下書きにせずそのまま作成する。作成した時点でワークフローが発火する。下書きを後から公開する経路では `prereleased` イベントが飛ばずワークフローが走らないので、プレリリースを下書きで作ってはいけない。

タグ名は `versionName` が `3.1.0` のとき、プレリリースが `v3.1.0-rc1`（末尾は未使用の番号）、リリースが `v3.1.0`。

```sh
gh release create v3.1.0-rc1 --prerelease --target master --title v3.1.0-rc1 --notes ''
gh release create v3.1.0 --draft --target master --title v3.1.0 --notes-file <(...)
```

プレリリースを後から正式リリースへ昇格させる場合は、先に本文へリリースノートを書いてから次のようにする。`released` イベントが飛び、Play Store へのアップロードが走る。ノートがないままだとワークフローが落ちる。

```sh
gh release edit v3.1.0-rc1 --prerelease=false
```

振り分けは Release の prerelease フラグで決まり、タグ名のサフィックスは見ていない。
