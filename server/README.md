# Type & Summon Server

Java 標準ライブラリ中心で実装したタイピングバトル用サーバーです。

- **ロビー**: HTTP（部屋作成・参加）
- **対戦中**: `java.net.ServerSocket` / `java.net.Socket` による TCP リアルタイム通信
- **1対1対戦**: `server.BattleStateManager`（3 分・HP 1500）
- **2人協力ボス戦**: `server.coop.CooperativeBossRoom`（10 分・ボス Lv1〜5 連戦）

## 起動方法

### Windows（PowerShell）

```powershell
cd server
.\run.ps1
```

- 8080 / 9090 を使用中のプロセスを停止 → DB 準備 → コンパイル → 起動
- 既存プロセスを止めない: `.\run.ps1 -NoKillExisting`

### macOS / Linux

```bash
cd server
./run.sh
```

`Permission denied` が出る場合:

```bash
chmod +x run.sh build.sh prepare_data.sh
./run.sh
```

### Windows（Git Bash）

```bash
cd server
./run.sh
```

### 手動ビルド

Windows:

```powershell
.\build.ps1
java -cp "out;lib\sqlite-jdbc.jar" Main
```

macOS / Linux / Git Bash:

```bash
./build.sh
java -cp "out:lib/sqlite-jdbc.jar" Main
```

### 初回・データ準備

`run.ps1` / `run.sh` は内部で `prepare_data.ps1` / `prepare_data.sh` を呼び出し、SQLite 単語 DB を展開・初期化します。

## 環境設定

必要な場合は `server/.env` を作成します。未作成時は既定値で動作します。

```env
SERVER_HOST=127.0.0.1
SERVER_PORT=8080
SOCKET_HOST=127.0.0.1
SOCKET_PORT=9090
PUBLIC_SOCKET_HOST=127.0.0.1
SQLITE_URL=jdbc:sqlite:data/game_server.db
```

| ポート | 用途 |
|--------|------|
| 8080 | HTTP ロビー API |
| 9090 | TCP 対戦通信 |

## 現在の本流

| クラス | 役割 |
|--------|------|
| `Main` | DB・DAO・サービス・`GameServer` の組み立て |
| `server.GameServer` | HTTP + TCP サーバー起動 |
| `server.ClientHandler` | TCP メッセージ受信・1対1 対戦処理 |
| `server.BattleStateManager` | 1対1 対戦の正本（HP・単語・コンボ・ログ） |
| `server.coop.CooperativeBossRoom` | 協力ボス戦の正本 |
| `server.coop.CooperativeBattleService` | 協力モード TCP/状態配信 |
| `server.coop.CooperativeBossProfile` | ボス Lv1〜5 の HP・攻撃・特殊能力定数 |
| `service.BattleScoreCalculator` | ダメージ・反動・回復・コンボ |
| `dao.WordDao` / `dao.impl.SqliteWordDao` | レベル別単語取得 |


---

## 1対1 対戦ルール

- 試合時間: **3 分**
- HP 初期値: **1500**
- HP 0 で即終了 / 時間切れは HP 多い方が勝ち
- 難易度 Lv1〜9
- 入力変更のたびに `TYPING_UPDATE` を受信し、prefix 一致・完全一致・ミスを判定
- 正解: 相手ダメージ + 自分回復 + コンボ / ミス: 反動 + コンボリセット

---

## 協力モード（ボス戦）

### 開始条件

- TCP 認証時に `mode=coop` を指定（クライアント `CooperativeTcpAuth`）
- 同一ルームに **2 人** が接続・参加完了で自動開始

### ルール概要

| 項目 | 値 |
|------|-----|
| 制限時間 | 10 分（`CooperativeBossRoom.MATCH_DURATION_MILLIS`） |
| プレイヤー HP | 1500 / 人 |
| ボス攻撃間隔 | 5 秒 |
| クリア | ボス Lv5 撃破 |
| 敗北 | 全員 HP 0（`party_wiped`）または時間切れ |

### ボス定義（`CooperativeBossProfile.defaults()`）

| Lv | HP | 攻撃 | 対象 | 特殊 |
|----|-----|------|------|------|
| 1 | 500 | 50〜70 | 1 人 | なし |
| 2 | 800 | 80〜120 | 1 人 | なし |
| 3 | 1,200 | 120〜150 | 1 人 | Lv9 強制（15 秒周期・10 秒 ON） |
| 4 | 1,500 | 100 | **全員** | 3 文字目ごと非表示フラグ |
| 5 | 2,000 | 150〜180 | 1 人 | 3 単語に 1 回罠単語 |

### 協力モード追加仕様

- **Lv3 スキル移行**: スキル ON 時、入力中単語があるプレイヤーは `pendingLv9Transition` で現単語完了まで待機し、正解/ミス後に Lv9 へ移行
- **HP 0 プレイヤー**: `handleTyping` / `selectLevel` で入力を拒否（パートナー生存中も戦闘継続）
- **ミス通知**: Lv1〜2 など反動 0 でも `TypingResult.miss()` で `ANSWER_RESULT` を送信

### 協力モード TCP 追加フィールド

`STATE_UPDATE` / `WORD` / `START` などに協力用キーが付加されます:

```text
mode=coop
bossLevel=...
bossHp=...
bossHpMax=...
forcedWordLevel9=true    … Lv3 スキル発動中
hideThirdChar=true       … Lv4 ボス戦中
phase=boss_transition    … ボス交代中（3 秒）
decoy=true               … Lv5 罠単語
```

---

## TCP メッセージ

形式:

```text
TYPE|key=value;key=value
```

### Client → Server

```text
AUTH|roomId=...;playerId=...;token=...;mode=coop   … 協力時
GET_ROOM|roomId=...
SELECT_LEVEL|level=...
TYPING_UPDATE|level=...;text=...
```

### Server → Client

```text
AUTH_OK|playerId=...
ROOM_STATE|roomId=...;status=...;playerCount=...
START|durationSec=...;remainingMillis=...;mode=coop;bossLevel=...;bossHpMax=...
WORD|level=...;text=...;forcedWordLevel9=...;hideThirdChar=...;decoy=...
ANSWER_RESULT|correct=...;level=...;damage=...;recoil=...;heal=...;combo=...;outcome=...
STATE_UPDATE|myHp=...;opponentHp=...;remainingMillis=...;mode=coop;bossHp=...
BATTLE_LOG|log1=...;log2=...
LEVEL_INFO|level=...;damage=...;recoil=...;heal=...;comboStep=...
FINISH|winnerPlayerId=...;draw=...;reason=...
ERROR|message=...
```

## HTTP API

| Method | Path | 用途 |
|--------|------|------|
| `POST` | `/rooms` | 部屋作成 |
| `POST` | `/rooms/join` | 部屋参加 |
| `GET` | `/rooms/{roomId}` | 部屋状態 |
| `POST` | `/rooms/{roomId}/finish` | 待機中ルーム終了 |

レスポンスの `socketHost` / `socketPort` で TCP 接続します。

## コンパイル確認

サーバー側の確認として、Java ソースをコンパイルできます。

PowerShell:

```powershell
cd server
$out = "out-check"
New-Item -ItemType Directory -Force -Path $out | Out-Null
$sources = Get-ChildItem -Recurse -Filter *.java -Path src | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d $out $sources
```

Git Bash:

```bash
cd server
./build.sh
```


## ビルド時の注意

- コンパイル: SQLite JDBC **不要**
- 実行: `lib/sqlite-jdbc.jar` **必要**
