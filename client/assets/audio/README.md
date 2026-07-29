# 音声素材（BGM・効果音）

クライアントの作業ディレクトリは `client/` です。
`ui.GameAudio` が `assets/audio/` 配下の BGM と効果音を読み込みます。

## フォルダ構成

```text
assets/audio/
  README.md
  bgm/
    title.mp3
    battle.mp3
  sfx/
    attack1.wav
    attack2.wav
    boss_attack.wav
    boss_transition.wav
    click.wav
    damage_opponent.wav
    damage_player.wav
    defeat.wav
    lv1.wav
    lv3.wav
    lv4.wav
    lv5.wav
    lv5_2.wav
    match_start.wav
    miss.wav
    typing_correct.wav
    ui_confirm.wav
    victory.wav
```

## BGM

| ファイル | 用途 |
|----------|------|
| `bgm/title.mp3` | タイトル画面 |
| `bgm/battle.mp3` | 1対1対戦と協力モードの対戦画面 |

上記BGMは、濱谷 康生がSunoを利用して制作したオリジナル楽曲です。

## 効果音

| ファイル | 用途 |
|----------|------|
| `sfx/attack1.wav` | Lv1-Lv4 の攻撃 |
| `sfx/attack2.wav` | Lv5-Lv9 の攻撃、協力ボス Lv2 の攻撃 |
| `sfx/lv1.wav` | 協力ボス Lv1 の攻撃 |
| `sfx/lv3.wav` | 協力ボス Lv3 の攻撃 |
| `sfx/lv4.wav` | 協力ボス Lv4 の攻撃 |
| `sfx/lv5.wav` | 協力ボス Lv5 の戦闘開始 |
| `sfx/lv5_2.wav` | 協力ボス Lv5 の攻撃 |
| `sfx/click.wav` | タイトル画面のボタン操作 |
| `sfx/ui_confirm.wav` | 接続ダイアログの確定 |
| `sfx/match_start.wav` | 対戦開始 |
| `sfx/typing_correct.wav` | 単語正解 |
| `sfx/miss.wav` | タイプミス |
| `sfx/damage_player.wav` | 自分がダメージを受けた演出 |
| `sfx/damage_opponent.wav` | 相手またはボスにダメージを与えた演出 |
| `sfx/boss_attack.wav` | ボス攻撃の共通演出 |
| `sfx/boss_transition.wav` | ボス撃破後の次ボス待ち |
| `sfx/victory.wav` | 勝利、全ボス撃破 |
| `sfx/defeat.wav` | 敗北、全滅、時間切れ |

## 音量設定

- `-Dgame.audio.enabled=false` で音声を無効化できます。
- `-Dgame.audio.bgmVolume=0.5` と `-Dgame.audio.sfxVolume=0.8` で音量を調整できます。
