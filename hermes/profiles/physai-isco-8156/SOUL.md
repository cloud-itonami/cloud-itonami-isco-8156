# physai-isco-8156 — 製靴機オペレーター（ISCO 8156）の工場物流を担うロボットの physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isco-8156`、ISCO 8156 製靴機オペレーター）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか


README の Robotics premise: 工場の段取り・物流調整ロボットが、製靴班の勤務編成、生産・在庫の記録、革・靴底・接着剤の補給を扱う（プレス・裁断型・つり込み機は操作しない）。
その物理的な仕事（靴底の束をプレス室の供給ラックへ載せることと、資材倉庫から裁断室への台車搬送）を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:sole-stack-to-feed-rack` | manipulator | 納品箱のゴム底の束をソールプレス供給ラックの上段へ持ち上げる（2 リンクアーム、2.0 s） | 肩関節ピークトルク | 60 N·m（estimate） |
| `:materials-cart-to-cutting-room` | transport | 革と接着剤缶の台車を資材倉庫から裁断室へ運ぶ（AMR、60 m、積荷 60 kg） | 1 区間の所要時間 | 90 s（estimate） |

測定の入口: `kbb -M:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:physai-test`（`test-physai/shoecoord/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する。repo 自身の `test/` の .cljk も同じ runner で走り、計 32 test / 70 assertion）。

## 測って分かったこと・限界（成長の第一候補）

1. **アーム**: 肩トルクは束 1 kg で 26.2 N·m、6 kg で 56.1 N·m（1 kg あたり約 6 N·m 増）。関節仕事は 29.66 J → 59.08 J。
   限界 60 N·m に達する積荷は **6.65 kg**。掃引範囲（1〜6 kg）は全て限界内。
2. **搬送**: 60 m の所要時間は最高速度で決まる（0.5 m/s で 120.8 s、1.0 m/s で 61.6 s、1.5 m/s で 42.4 s）。駆動力 250 N は制約しない（drive-limited? false）。
   限界 90 s に収まる最高速度は **0.675 m/s 以上**。エネルギーは速度とともにわずかに増える（1424 J → 1515 J）。転倒余裕は 0.85 で一定。
3. **estimate のままの値**: 肩トルク上限 60 N·m（協働ロボットの仕様書で置き換える）、区間所要時間 90 s（裁断プレスのバッチ間隔の実測で置き換える）、
   アームの寸法・質量、AMR の駆動力・転がり抵抗係数・制動減速度。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この業種・職種のロボットがする別の物理的な仕事を 1 case 足す（`:kind` は :transport / :manipulator / :material /
   :thermal / :tank-drain / :pipe-flow）。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isco-8156 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:physai-test → kbb -M:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isco-8156 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
