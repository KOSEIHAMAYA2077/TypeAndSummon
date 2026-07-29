# Type & Summon Client

Java Swing で実装したタイピングゲームクライアントです。表示テーマはモンスター召喚風の **Type & Summon** です。

- **1対1 対戦** と **2人協力ボス戦** に対応
- ロビー: HTTP / 対戦中: TCP
- ゲーム状態の正本はサーバー。クライアントは入力送信と表示更新

## 起動方法

### Windows（PowerShell）— 推奨

```powershell
cd client
.\run.ps1
```

- `lib/*.jar` を classpath に含めてコンパイル
- `jlayer-1.0.1.jar` が無い場合は `scripts\fetch_jlayer.ps1` で自動取得
- `ui.SushiBattleGUI` を起動

**先にサーバーを起動**してください（`server\run.ps1`）。

2 人で遊ぶ場合は PowerShell を **2 つ** 開き、それぞれで `.\run.ps1` を実行します。

### Git Bash

```bash
cd client
./run.sh
```

### 手動ビルド

```bash
./build.sh
```

PowerShell:

```powershell
$cp = "out"
Get-ChildItem lib\*.jar | ForEach-Object { $cp += ";$($_.FullName)" }
javac -encoding UTF-8 -classpath $cp -d out (Get-ChildItem -Recurse -Filter *.java src).FullName
java -classpath $cp ui.SushiBattleGUI
```

## 環境設定

必要な場合は `client/.env` を作成します。未作成時は `http://localhost:8080` を使用します。

```env
API_BASE_URL=http://localhost:8080
```

未設定時も `http://localhost:8080` を使用します。  
別 PC のサーバーへ接続する場合は IP を指定してください。

---

## 画面・操作

### タイトル画面

| ボタン | 内容 |
|--------|------|
| 対戦開始 | 1対1 対戦（3 分） |
| 協力モード | 2 人ボス戦（10 分・Lv1〜5 連戦） |
| 遊び方 | チュートリアルダイアログ |

### 協力モード接続

1. **協力モード** → ダイアログで名前・Room Name・作成/参加を選択
2. ホストが部屋作成 → ゲストが **同じ Room Name** で参加
3. 2 人揃うとボス Lv1 から開始

### 対戦画面（`BattlePanel`）

- 自分 HP / 相手（またはボス）HP / 残り時間
- 現在単語・入力欄・Lv1〜9 ボタン（数字キー 1〜9 も可）
- 直近 5 件の自分ログ（黒）・相手/共有ログ（赤）
- 協力モード: ボス画像（`assets/bossbattle/`）、ボス攻撃時の揺れアニメ
- 決着後: **メニューへ戻る**

---

## 協力モード — クライアント側の表示・挙動

| 機能 | 実装 |
|------|------|
| ボス HP 表示 | `opponentHpBar` をボス HP として表示 |
| Lv3 Lv9 強制 | `chain.png` オーバーレイ + Lv1〜8 ボタンロック |
| Lv3 移行タイミング | サーバー `forcedWordLevel9` 受信後に UI ロック（現単語完了後） |
| Lv4 文字隠し | `wordCoverOverlay` で 1 秒隠す / 1 秒表示 |
| HP 0 | 入力欄・レベルボタン無効、「HPが0のため入力できません」 |
| ボス交代 | 3 秒間入力不可 + `boss_transition.wav` |
| ボス攻撃 SE | ボス Lv 別（`GameAudio.playCoopBossAttackSfx`） |

協力モード状態管理: `CooperativeModeSession.java`  
接続フロー: `CooperativeModeLauncher.java` → `CooperativeTcpAuth.java`（`mode=coop`）

---

## 現在の本流

| ファイル | 役割 |
|----------|------|
| `src/ui/SushiBattleGUI.java` | **起動対象 GUI** — HTTP/TCP・画面更新 |
| `src/ui/BattlePanel.java` | 対戦 UI・ボス演出・入力ロック |
| `src/ui/CooperativeModeSession.java` | 協力モード状態 |
| `src/ui/GameAudio.java` | BGM/SE（MP3/WAV フォールバック） |
| `src/tcp/TcpBattleClient.java` | TCP 送受信 |
| `src/protocol/*` | メッセージ種別・パース |


---

## 素材パス

| 種類 | パス |
|------|------|
| 背景 | `assets/backgrounds/title_quest.png`, `battle_quest.png` |
| モンスター（1対1） | `assets/monsters/lv1.png` 〜 `lv9.png` |
| ボス（協力） | `assets/bossbattle/lv1.png` 〜 `lv5.png` |
| Lv3 チェーン | `assets/bossbattle/chain.png` |
| 音声 | `assets/audio/` — 詳細は [assets/audio/README.md](assets/audio/README.md) |

モンスター素材: [モンスター素材屋さん](http://sozai.creature-ya.com/)（非商用・クレジット必須）

---

## 音声

- BGM: `assets/audio/bgm/title.mp3`, `battle.mp3`（`lib/jlayer-1.0.1.jar` 必須）
- SE: WAV 優先、無ければ同名 `.mp3` を試行

---

## TCP メッセージ

形式: `TYPE|key=value;key=value`

### Client → Server

```text
AUTH|roomId=...;playerId=...;token=...
SELECT_LEVEL|level=...
TYPING_UPDATE|level=...;text=...
```

### Server → Client（協力時の追加キー含む）

```text
START|...;mode=coop;bossLevel=...;bossHpMax=...
WORD|level=...;text=...;forcedWordLevel9=...;hideThirdChar=...
STATE_UPDATE|myHp=...;opponentHp=...;bossHp=...;mode=coop;...
ANSWER_RESULT|correct=...;recoil=...;outcome=MISS|CORRECT|...
FINISH|reason=party_wiped|all_bosses_defeated|time|...
```

---

## トラブルシューティング

| 症状 | 確認 |
|------|------|
| 接続失敗 | サーバー起動済みか、`API_BASE_URL` が正しいか |
| MP3 BGM 無音 | `lib/jlayer-1.0.1.jar` の有無 |
| 協力が始まらない | 同 Room Name で 2 人接続か |
| 変更が反映されない | クライアント再起動（`run.ps1` は毎回コンパイル） |

ルート README の全体説明: [../README.md](../README.md)
