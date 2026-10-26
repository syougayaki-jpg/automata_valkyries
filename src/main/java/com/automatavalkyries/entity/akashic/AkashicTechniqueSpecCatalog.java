package com.automatavalkyries.entity.akashic;

import com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage;
import com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.TechniqueKind;

import java.util.List;

/**
 * 「カスタムダメージ辞書（36種）」および「カスタム即死辞書（72種）」に収録された全 108 種技術（{@code T001}〜{@code T108}）の
 * 仕様技術解説カタログ（2026-10-26 ユーザー指定: 「ダメージ即死辞書に仕様技術の解説をつけて」）。
 * <p>
 * 各 {@link AuditStage} について、以下を構造化して提供する:
 * <ul>
 *   <li>{@link SpecEntry#titleJa()}: 日本語の技術名称</li>
 *   <li>{@link SpecEntry#categoryJa()}: 所属ティアと技術分類名</li>
 *   <li>{@link SpecEntry#mechanismSpec()}: 内部で実行する具体的な API・フィールド・Unsafe・ASM・ワールド操作の仕様解説</li>
 *   <li>{@link SpecEntry#targetDefenseBypass()}: 想定する敵の防御機構（ダメージ上限・イベントキャンセル・DataItem 保護・自己修復等）と突破原理</li>
 * </ul>
 * </p>
 */
public final class AkashicTechniqueSpecCatalog {

    private AkashicTechniqueSpecCatalog() {}

    /**
     * 個別技術（{@code T001}〜{@code T108}）の仕様解説レコード。
     *
     * @param titleJa             日本語の技術名称（GUI リストおよび詳細パネル見出し用）
     * @param categoryJa          ティア・系統分類名
     * @param mechanismSpec       内部実装・使用技術の仕様解説（何のメソッド/フィールド/メモリをどう操作するか）
     * @param targetDefenseBypass 突破対象となる防御・耐性ギミックの解説
     */
    public record SpecEntry(
            String titleJa,
            String categoryJa,
            String mechanismSpec,
            String targetDefenseBypass
    ) {}

    /**
     * ティア（Tier 1〜10）ごとの体系解説を返す。
     */
    public static String tierOverview(int tier) {
        return switch (tier) {
            case 1 -> "Tier 1 [バニラ・特殊属性＋7段階適応調査フローチャート (T001-T012)]: "
                    + "LivingEntity.hurt() を起点とし、AkashicDamageFlowchart（7段階フロー）で無敵時間解除・上限/足切り二分探索・16属性×4攻撃者トポロジー探索・多段刻み追撃・事後書き戻し監視を連動執行する。";
            case 2 -> "Tier 2 [無敵突破・極大Float・IEEE-754特殊ビット打撃 (T013-T024)]: "
                    + "invulnerableTime/hurtTime/lastHurt の強制リセット連撃、10^6〜Float.MAX_VALUE の超巨大数値、および ±Infinity / qNaN / sNaN / -NaN の IEEE-754 特殊ビット列を hurt() に注入し算術比較ガードを破壊する。";
            case 3 -> "Tier 3 [防具剥奪・状態異常増幅・防御解体＋隔絶ダメージ (T025-T036)]: "
                    + "吸収HP・防具属性・バフ・トーテムの剥奪、PartEntity 親本体解決、StateScanner/KillLists/ModProtectionBreaker による事前防御解体、およびイベントバスを迂回する IsolationDamageEngine 隔絶打撃を行う。";
            case 4 -> "Tier 4 [直接メソッド・生死ライフサイクル直呼び (T037-T048)]: "
                    + "hurt() を一切経由せず、setHealth(0/-MAX/NaN)、kill()、die()、discard()、および全 5 種の RemovalReason による remove() / setRemoved() + onClientRemoval() を直接呼び出す。";
            case 5 -> "Tier 5 [Attribute 最大HPゼロ化・キャッシュ破壊 (T049-T056)]: "
                    + "MAX_HEALTH 属性の baseValue を 0 / 負数 / NaN に書き換え、または ADDITION(-1e9) / MULTIPLY_TOTAL(-1.0) Modifier や Unsafe による cachedValue=0.0D 直書きで最大HP自体を消滅させる。";
            case 6 -> "Tier 6 [SynchedEntityData (DataParameter) 改ざん・ロック・抜去 (T057-T066)]: "
                    + "DATA_HEALTH_ID の DataItem.value を Unsafe で 0/NaN/-MAX に書き換え、ASM 動的生成サブクラス (LockedZeroDataItem) による 0 固定ロック、または itemsById スロット抜去で外部 MOD の DataItem キャッシュ参照を切断する。";
            case 7 -> "Tier 7 [Unsafe プリミティブ・生死内部フィールド直書き (T067-T078)]: "
                    + "Mixin Accessor と sun.misc.Unsafe.put*Volatile を用い、dead=true、deathTime=20、removalReason、isAddedToWorld=false、独自HP/防御フラグ/残機カウンタ、奈落/NaN座標を直接メモリ書き込みする。";
            case 8 -> "Tier 8 [準汎用フラグ辞書・NBT・Capability・AI/部位解体 (T079-T088)]: "
                    + "StateScanner（インターフェース・注入/独自フィールド・Capability・永続NBT）、死亡NBT load() 上書き、PartEntity 部位切断、強制降車、AI/Brain 完全消去、引数なし破棄メソッド反射呼び出しを行う。";
            case 9 -> "Tier 9 [歴代チートMOD特化解体・抹殺棒技術 (T089-T098)]: "
                    + "ImmortalWither / Tesseract / Flashfur / NoSugar / Avaritia / SuperSteve / PIG2 / Madness 等、固有の不死ギミック・保護リスト・外部コントローラ・独自死亡ルートをピンポイントで解体する。";
            case 10 -> "Tier 10 [ワールド全層抹消・深層JVMメモリ崩壊・終焉機構 (T099-T108)]: "
                    + "entityTickList / EntityLookup / SectionStorage / ChunkMap からの追放、UUID/ID 隔離、非Entity参照フィールドの null 化、TrueAnnihilationEngine、JavaAgent 補助抹殺、TerminusEngine、全108層同時収束を実行する。";
            default -> "";
        };
    }

    /**
     * 辞書種別（ダメージ辞書 / 即死辞書）全体のアーキテクチャ・仕様解説サマリ（ツールチップおよび GUI ガイド用）。
     */
    public static List<String> lexiconArchitectureGuide(TechniqueKind kind) {
        if (kind == TechniqueKind.DAMAGE) {
            return List.of(
                    "【ダメージ辞書 仕様技術体系 (全36種: Tier 1〜3)】",
                    "・全段 LivingEntity.hurt(DamageSource, amount) 系のダメージ計算パイプラインを経由して検証・攻撃する。",
                    "・Tier 1 (T001-T012): 7段階適応調査フローチャート (AkashicDamageFlowchart) 連動。無敵時間&差分減衰解除 → 過剰ダメ拒否上限(Ceiling)/足切り(Floor)の二分探索 → 16属性×4攻撃者位相のUCB1探索 → 1発上限(Per-Hit Cap)に対する最大16分割スライス連撃 → Tick間書き戻し(Revert)監視マイクロバースト。",
                    "・Tier 2 (T013-T024): i-frame/hurtTime/lastHurt(-Float.MAX_VALUE) 強制突破連撃、10^6〜Float.MAX_VALUE の超数値打撃、±Infinity / qNaN(0x7fc00000) / sNaN(0x7f800001) / -NaN(0xffc00001) の IEEE-754 特殊ビット注入。",
                    "・Tier 3 (T025-T036): 吸収HP/防具/耐性バフ/不死トーテム剥奪、PartEntity 親解決、StateScanner/KillLists/ModProtectionBreaker 解体打撃、および Forge イベント妨害を迂回する IsolationDamageEngine 隔絶ダメージ。",
                    "・UniversalDamageFormula 連動: 実効与ダメDPS・EWMA瞬間DPS・時間窓DPS・推定撃破時間(ETTK)をリアルタイム算出。"
            );
        } else {
            return List.of(
                    "【即死辞書 仕様技術体系 (全72種: Tier 4〜10)】",
                    "・通常の hurt() ダメージ計算を介さず、生死状態・属性・同期データ・JVMメモリ・ワールド登録・終焉機構を直接操作する。",
                    "・Tier 4 (T037-T048): setHealth(0/-MAX/NaN)、kill()、die()、discard()、全 RemovalReason での remove()/setRemoved()。",
                    "・Tier 5 (T049-T056): MAX_HEALTH 属性 baseValue(0/-1e6/NaN)、Modifier(-1e9 / x-1.0)、Unsafe cachedValue=0 直書き。",
                    "・Tier 6 (T057-T066): SynchedEntityData(DATA_HEALTH_ID) 直書き、ASM動的生成 LockedZeroDataItem による0固定ロック、itemsById スロット抜去＋NPE防止安全再挿入。",
                    "・Tier 7 (T067-T078): Unsafe.put*Volatile による dead=true、deathTime=20、removalReason、isAddedToWorld=false、全プリミティブHP/防御boolean/残機int一掃、奈落/NaN座標注入。",
                    "・Tier 8〜9 (T079-T098): StateScanner 5系統、死亡NBT load()、部位切断、AI/Brain ロボトミー、反射cleanup、歴代チートMOD(PIG2/SuperSteve/Madness/Flashfur等)特化解体。",
                    "・Tier 10 (T099-T108): ServerLevel/EntityLookup/ChunkMap 全層追放、UUID/ID隔離、参照フィールドnull化、TrueAnnihilationEngine、TerminusEngine、全108層同時収束(Omega Terminus)。"
            );
        }
    }

    /**
     * 指定された {@link AuditStage} の仕様技術解説エントリを返す（全 108 ステージ網羅）。
     */
    public static SpecEntry of(AuditStage stage) {
        return switch (stage) {
            // ==== Tier 1: バニラ・特殊属性ダメージ系 (T001..T012) ====
            case T001_NORMAL_MOB_HURT -> new SpecEntry(
                    "標準モブ物理打撃＋7段階適応調査フロー",
                    "Tier 1: バニラ・特殊属性ダメージ (7段階フロー連動)",
                    "DamageSources.mobAttack(host) を起点に AkashicDamageFlowchart（7段階有効ダメージ調査）を実行。無敵時間・lastHurt解除後、実HP減少量を測定し、0ダメ時は上限/足切り二分探索と16属性×4攻撃者位相探索、上限検知時は最大16分割の刻み連撃を自動執行する。",
                    "通常の物理耐性、無敵時間(invulnerableTime)による連撃無効化、1発あたりダメージ上限(Per-Hit Cap)、過剰ダメージ拒否(Ceiling)、小ダメージ足切り(Floor)。"
            );
            case T002_MAGIC_HURT -> new SpecEntry(
                    "魔法属性貫通打撃 (BYPASSES_ARMOR)",
                    "Tier 1: バニラ・特殊属性ダメージ (7段階フロー連動)",
                    "DamageSources.magic()（防具貫通タグ BYPASSES_ARMOR 付き）を優先チャネルとして 7 段階適応調査フローを実行。物理防具値(ARMOR/TOUGHNESS)による軽減計算をスキップして実HP削りを行う。",
                    "超高防御力（ARMOR / ARMOR_TOUGHNESS 極大値）や物理攻撃（mobAttack/playerAttack）のみを 0 にする属性偏重ガード。"
            );
            case T003_WITHER_HURT -> new SpecEntry(
                    "凋零（ウィザー）侵食打撃",
                    "Tier 1: バニラ・特殊属性ダメージ (7段階フロー連動)",
                    "DamageSources.wither() を優先チャネルとして 7 段階適応調査フローを実行。防具貫通かつ魔法耐性の一部を迂回する凋零属性で打撃を与え、上限持ちには多段スライス追撃を行う。",
                    "物理・通常魔法耐性を持つがステータス侵食系（wither）の DamageType フィルタが漏れている防御実装。"
            );
            case T004_DRAGON_BREATH_HURT -> new SpecEntry(
                    "竜の息吹（ドラゴンブレス）打撃",
                    "Tier 1: バニラ・特殊属性ダメージ (7段階フロー連動)",
                    "DamageSources.dragonBreath() を優先チャネルとして 7 段階適応調査フローを実行。エンダードラゴン級の特殊ブレス属性による防具無視打撃と適応刻み追撃を行う。",
                    "一般的な mobAttack / magic / wither をブラックリストで弾くが dragonBreath を除外し忘れているダメージフィルタ。"
            );
            case T005_THORNS_HURT -> new SpecEntry(
                    "棘の鎧（反射属性）打撃",
                    "Tier 1: バニラ・特殊属性ダメージ (7段階フロー連動)",
                    "DamageSources.thorns(host) を優先チャネルとして 7 段階適応調査フローを実行。直接攻撃ではなく「反射ダメージ」扱いの DamageSource トポロジーで対象を打撃する。",
                    "直接攻撃（directEntity 判定）を無効化するが、反射系（thorns）のダメージイベントを通すカウンター耐性。"
            );
            case T006_CRAMMING_STARVE_DROWN_HURT -> new SpecEntry(
                    "圧死・飢餓・溺死 3連環境複合打撃",
                    "Tier 1: バニラ・特殊属性ダメージ (7段階フロー連動)",
                    "無敵時間とガードを毎回リセットしながら cramming(圧死) → starve(飢餓) → drown(溺死＋7段階適応フロー) の環境3属性を連続注入する。攻撃者エンティティを持たない環境要因 DamageSource 群。",
                    "「攻撃者 (getEntity() != null) が存在するダメージ」をすべてキャンセルする対エンティティ完全無敵ガード。"
            );
            case T007_FIRE_LAVA_LIGHTNING_HURT -> new SpecEntry(
                    "火炎・溶岩・雷撃 3連エレメンタル打撃",
                    "Tier 1: バニラ・特殊属性ダメージ (7段階フロー連動)",
                    "inFire(火炎) → lava(溶岩) → lightningBolt(雷撃＋7段階適応フロー) を無敵時間リセット付きで連続執行する。熱・電撃系の複合環境チャネルで耐性穴を突く。",
                    "物理・無属性を遮断するが、雷撃(lightningBolt)や自然災害属性のハンドリングが別分岐になっているモブ防御。"
            );
            case T008_FREEZE_DRYOUT_INWALL_HURT -> new SpecEntry(
                    "凍結・干からび・窒息 3連特殊環境打撃",
                    "Tier 1: バニラ・特殊属性ダメージ (7段階フロー連動)",
                    "dryOut(乾燥) → inWall(ブロック内窒息) → freeze(粉雪凍結＋7段階適応フロー) を連続執行する。火炎耐性や水中呼吸では防げない特殊環境 DamageType 3種を突く。",
                    "火炎・爆発・物理・魔法の主要耐性タグを網羅しているが、freeze / dryOut / inWall のマイナー環境属性を通す防御。"
            );
            case T009_EXPLOSION_STALACTITE_HURT -> new SpecEntry(
                    "爆破・鍾乳石落下 破砕複合打撃",
                    "Tier 1: バニラ・特殊属性ダメージ (7段階フロー連動)",
                    "fallingStalactite(鍾乳石落下) → explosion(爆発＋7段階適応フロー) を連続執行。防具貫通落下物と高威力爆発の二段構えでシールドと装甲を同時試験する。",
                    "近接・遠隔武器攻撃のみを弾き、落下構造物ダメージや爆発属性に対して別計算式を持つ大型ボス防御。"
            );
            case T010_OUTSIDE_BORDER_HURT -> new SpecEntry(
                    "ワールド境界外（Outside Border）侵食打撃",
                    "Tier 1: バニラ・特殊属性ダメージ (7段階フロー連動)",
                    "DamageSources.outsideBorder()（BYPASSES_ARMOR / BYPASSES_SHIELD）を用い、7段階適応フローで 250×倍率の境界外ダメージと上限スライス追撃を与える。",
                    "通常の戦闘ダメージを全カットするが、ワールド境界外の強制削減ダメージ（outsideBorder）は許可している防御。"
            );
            case T011_SONIC_BOOM_HURT -> new SpecEntry(
                    "ウォーデン衝撃波（Sonic Boom）完全貫通打撃",
                    "Tier 1: バニラ・特殊属性ダメージ (7段階フロー連動)",
                    "DamageSources.sonicBoom(host)（BYPASSES_ARMOR / BYPASSES_ENCHANTMENTS / BYPASSES_SHIELD）で 350×倍率＋7段階適応フローを実行。防具・防護エンチャント・盾をすべて標準貫通する。",
                    "防護エンチャント（Protection 極大レベル）や盾ガード、防具計算式への介入によってダメージを 99.9% 軽減するモブ。"
            );
            case T012_VOID_BYPASS_HURT -> new SpecEntry(
                    "奈落属性（BYPASSES_INVULNERABILITY）打撃",
                    "Tier 1: バニラ・特殊属性ダメージ (7段階フロー連動)",
                    "DamageSources.fellOutOfWorld()（バニラ無敵貫通タグ BYPASSES_INVULNERABILITY 付き）で 500×倍率＋7段階適応フローを実行。Creative/無敵フラグを標準突破する。",
                    "Entity.isInvulnerableTo() で BYPASSES_INVULNERABILITY タグ以外の全ダメージを拒否するバニラ準拠の無敵モブ。"
            );

            // ==== Tier 2: 無敵突破・極大 Float・IEEE-754 特殊ビットダメージ系 (T013..T024) ====
            case T013_GENERIC_KILL_HURT -> new SpecEntry(
                    "/kill 相当属性（genericKill）高出力打撃",
                    "Tier 2: 無敵突破・極大Float・IEEE-754特殊ビット",
                    "DamageSources.genericKill()（/kill コマンドと同一の BYPASSES_INVULNERABILITY + BYPASSES_EFFECTS 属性）を用い、1000×倍率＋7段階適応フローを実行する。",
                    "奈落(fellOutOfWorld)には独自耐性を持つが、/kill 用の genericKill DamageType は通す準不死モブ。"
            );
            case T014_IFRAME_ZERO_MULTI_BURST -> new SpecEntry(
                    "無敵時間ゼロ強制・5属性同時バースト連撃",
                    "Tier 2: 無敵突破・極大Float・IEEE-754特殊ビット",
                    "1 Tick 内で mobAttack / magic / sonicBoom / fellOutOfWorld / genericKill の 5 属性をループし、各打撃直前に target.invulnerableTime = 0 を強制代入して 5 連撃を同時叩き込む。",
                    "1 発目だけを受ける、または特定 1 属性の直後に長い invulnerableTime を設定して後続打撃をやり過ごす防御。"
            );
            case T015_HURT_TIME_ZERO_RAPID_STRIKE -> new SpecEntry(
                    "被弾硬直（hurtTime / hurtDuration）完全ゼロ化高速連撃",
                    "Tier 2: 無敵突破・極大Float・IEEE-754特殊ビット",
                    "invulnerableTime = 0 に加え、LivingEntity.hurtTime = 0 および hurtDuration = 0 を毎発強制リセットして genericKill 4 連撃を叩き込む。",
                    "バニラの invulnerableTime ではなく「hurtTime > 0（赤く点滅している被弾アニメ中）なら無敵」という独自判定を行うモブ。"
            );
            case T016_LAST_HURT_NEGATIVE_MAX_BYPASS -> new SpecEntry(
                    "lastHurt = -Float.MAX_VALUE 差分減衰バイパス打撃",
                    "Tier 2: 無敵突破・極大Float・IEEE-754特殊ビット",
                    "Unsafe で LivingEntity.lastHurt フィールドに -Float.MAX_VALUE を直書きした上で genericKill(5000) を与える。バニラが無敵時間中に行う「amount - lastHurt の差分のみ適用」の減衰計算を完全無力化する。",
                    "lastHurt を巨大な値に偽装・固定することで、後続のあらゆる hurt(amount) を「前回以下の威力」とみなして弾く防御。"
            );
            case T017_MEGA_DAMAGE_1E6 -> new SpecEntry(
                    "メガダメージ（1,000,000.0F = 10^6）打撃",
                    "Tier 2: 無敵突破・極大Float・IEEE-754特殊ビット",
                    "invulnerableTime=0 & lastHurt=-Float.MAX_VALUE の状態で genericKill(1.0E6F) を注入。高HPボスや 99.9% 固定割合軽減を数値の暴力で突破する。",
                    "最大HP数万〜数十万のインフレボス、および 99%〜99.9% の定率ダメージカットを持つ装甲。"
            );
            case T018_GIGA_DAMAGE_1E18 -> new SpecEntry(
                    "エクサ級ダメージ（1.0E18F = 10^18）打撃",
                    "Tier 2: 無敵突破・極大Float・IEEE-754特殊ビット",
                    "genericKill(1.0E18F) を注入。float 精度で整数部が完全に飽和する天文学的ダメージを与え、Long.MAX_VALUE 級の仮想HPシールドを一撃で吹き飛ばす。",
                    "double や long で独自HPプール（10^12〜10^17）を管理し、通常の数百万ダメージではゲージが減らない超インフレモブ。"
            );
            case T019_EXTREME_FLOAT_MAX_HURT -> new SpecEntry(
                    "単精度浮動小数点・最大有限値（Float.MAX_VALUE）打撃",
                    "Tier 2: 無敵突破・極大Float・IEEE-754特殊ビット",
                    "IEEE-754 有限最大値 Float.MAX_VALUE (3.4028235E38F) を genericKill で注入する。Float.isFinite(amount) チェックを通過できる理論上最大のダメージ値。",
                    "「!Float.isFinite(amount) なら無効化する」という無限大・NaN対策を入れつつ、有限値同士の減算でHPを計算している防御。"
            );
            case T020_POSITIVE_INFINITY_HURT -> new SpecEntry(
                    "IEEE-754 正の無限大（+Infinity / 0x7f800000）打撃",
                    "Tier 2: 無敵突破・極大Float・IEEE-754特殊ビット",
                    "Float.POSITIVE_INFINITY を hurt() に注入する。任意の有限軽減率 (amount * 0.0001F) や減算 (amount - 1e30F) を掛けても +Infinity のまま維持される特性を突く。",
                    "「受けるダメージに 0.0000001 を掛ける」等の乗算軽減・減算シールド（Float.MAX_VALUE すら削り切る軽減式）。"
            );
            case T021_NEGATIVE_INFINITY_HURT -> new SpecEntry(
                    "IEEE-754 負の無限大（-Infinity / 0xff800000）反転打撃",
                    "Tier 2: 無敵突破・極大Float・IEEE-754特殊ビット",
                    "Float.NEGATIVE_INFINITY を hurt() に注入する。「amount > maxCap ならキャンセル」という上限チェックを負数ですり抜け、内部で Math.abs(amount) や符号反転を行うバグ実装を即死させる。",
                    "上限チェック (if (amount > 100) return;) のみを持ち、負の無限大や絶対値変換のコーナーケースを考慮していない防御。"
            );
            case T022_QUIET_NAN_HURT -> new SpecEntry(
                    "IEEE-754 Quiet NaN（0x7fc00000）算術汚染打撃",
                    "Tier 2: 無敵突破・極大Float・IEEE-754特殊ビット",
                    "標準 Float.NaN (0x7fc00000) を hurt() に注入。NaN との大小比較（amount < 0, amount > cap）がすべて false になる IEEE-754 仕様を突き、比較ガードをすり抜けて HP 演算を NaN 汚染する。",
                    "if (amount <= 0) や if (amount > cap) の単純比較でガードしており、Float.isNaN チェックを忘れている防御。"
            );
            case T023_SIGNALING_NAN_HURT -> new SpecEntry(
                    "IEEE-754 Signaling NaN（sNaN: 0x7f800001）生ビット打撃",
                    "Tier 2: 無敵突破・極大Float・IEEE-754特殊ビット",
                    "Float.intBitsToFloat(0x7f800001) で生成した Signaling NaN ビット列を注入する。「Float.floatToRawIntBits(amount) == 0x7fc00000」のように標準 NaN 定数だけをビット比較で弾く防御をすり抜ける。",
                    "標準の Float.NaN ビットパターン (0x7fc00000) のみをハードコード検査して弾くカスタム NaN フィルタ。"
            );
            case T024_NEGATIVE_NAN_HURT -> new SpecEntry(
                    "IEEE-754 符号ビット反転 Negative NaN（-NaN: 0xffc00001）打撃",
                    "Tier 2: 無敵突破・極大Float・IEEE-754特殊ビット",
                    "最上位符号ビットを 1 に立てた負の NaN (0xffc00001) を Float.intBitsToFloat で生成して注入する。正の指数部ビットマスク検査や単純ビット一致ガードをバイパスする。",
                    "正の NaN ビットレンジ (0x7f800001..0x7fffffff) のみをマスク検査している不完全なビットレベル防御。"
            );

            // ==== Tier 3: 防具剥奪・状態異常増幅・防御解体打撃・隔絶ダメージ系 (T025..T036) ====
            case T025_ABSORPTION_STRIP_HURT -> new SpecEntry(
                    "吸収HP（Absorption / 黄色ハート）完全剥奪＋高出力打撃",
                    "Tier 3: 防具剥奪・防御解体打撃・隔絶ダメージ",
                    "target.setAbsorptionAmount(0.0F) で衝撃吸収シールド（金リンゴ/独自バリア）を即座に 0 に剥奪した直後、invulnerableTime=0 で genericKill(2000) を叩き込む。",
                    "極大な AbsorptionAmount（数万〜無限の黄色ハート）を毎発張って本体 HP への到達を防ぐバリア型モブ。"
            );
            case T026_ARMOR_TOUGHNESS_ZERO_HURT -> new SpecEntry(
                    "防具値・防具強度・ノックバック耐性 Base=0 破壊打撃",
                    "Tier 3: 防具剥奪・防御解体打撃・隔絶ダメージ",
                    "Attributes.ARMOR / ARMOR_TOUGHNESS / KNOCKBACK_RESISTANCE の Attribute BaseValue を強制的に 0.0D に書き換えた上で mobAttack(2000) を与える。",
                    "Attribute の ARMOR や ARMOR_TOUGHNESS に極大値を設定してダメージ計算式側で完封している重装甲モブ。"
            );
            case T027_BENEFICIAL_EFFECT_PURGE_HURT -> new SpecEntry(
                    "全ステータス効果（耐性V・再生・火炎耐性等）完全消去打撃",
                    "Tier 3: 防具剥奪・防御解体打撃・隔絶ダメージ",
                    "target.removeAllEffects() で対象に付与されている全ポーション効果（DAMAGE_RESISTANCE Lv5+ で 100% カット等）を全消去した直後、fellOutOfWorld(3000) を与える。",
                    "耐性エフェクト（Resistance V 以上の 100% ダメージ無効化）や超再生エフェクトに依存するバフ防御モブ。"
            );
            case T028_EXTREME_DEBUFF_AMPLIFY_HURT -> new SpecEntry(
                    "極限デバフ（凋零・弱体化・即時ダメージ Lv127）付与＋侵食打撃",
                    "Tier 3: 防具剥奪・防御解体打撃・隔絶ダメージ",
                    "既存バフを全消去した上で WITHER / WEAKNESS / HARM の Amplifier=127（最高レベル）を強制注入し、直後に wither(5000) 打撃を重ねる。",
                    "通常攻撃は防ぐが、高レベルの即時ダメージ（Instant Damage / Harm）やデバフAmplifierオーバーフローに弱いモブ。"
            );
            case T029_TOTEM_EQUIPMENT_STRIP_HURT -> new SpecEntry(
                    "全装備スロット（不死のトーテム・神装備）強制没収＋打撃",
                    "Tier 3: 防具剥奪・防御解体打撃・隔絶ダメージ",
                    "全 EquipmentSlot（MAINHAND/OFFHAND/HEAD/CHEST/LEGS/FEET）に ItemStack.EMPTY を強制セットして不死のトーテムやチート防具を外した直後、genericKill(5000) を与える。",
                    "手持ちの不死のトーテム無限消費や、特定防具のフルセット装着時のみ発動する無敵イベント（Avaritia型など）に頼るモブ。"
            );
            case T030_MULTIPART_PARENT_PIERCE_HURT -> new SpecEntry(
                    "マルチパート（PartEntity）親本体逆探知・直撃打撃",
                    "Tier 3: 防具剥奪・防御解体打撃・隔絶ダメージ",
                    "IsolationDamageEngine.resolveLivingTarget() で PartEntity（部位当たり判定）から親の LivingEntity 本体を逆解決し、ダミー部位ではなく親本体へ直接 genericKill(10000) を叩き込む。",
                    "エンダードラゴン派生や大型マルチパートボスのように、外殻 PartEntity への攻撃を吸収・無効化して本体HPを守る構造。"
            );
            case T031_FLAG_SCANNER_STRIP_PLUS_HURT -> new SpecEntry(
                    "準汎用 StateScanner 防御フラグ解体＋高出力打撃",
                    "Tier 3: 防具剥奪・防御解体打撃・隔絶ダメージ",
                    "StateScanner.apply(BREAK_TARGET, 全Source) を実行し、対象が持つインターフェース・フィールド・SynchedEntityData・Capability・NBTの無敵/保護フラグを解除した直後に genericKill(10000) を与える。",
                    "独自 boolean/int フラグ（invulnerable, shieldActive 等）が ON の間だけ hurt() を弾くモブ（フラグ解除後は通常ダメージで倒せるかの検証）。"
            );
            case T032_KILL_LIST_PURGE_PLUS_HURT -> new SpecEntry(
                    "準汎用 KillLists 静的保護コレクション追放＋高出力打撃",
                    "Tier 3: 防具剥奪・防御解体打撃・隔絶ダメージ",
                    "KillLists.purge(target) で外部 MOD が保持する static Set/List/Map の保護リストから対象を除去し、死亡リストへ登録した直後に genericKill(20000) を与える。",
                    "static コレクション（ProtectedEntities 等のグローバル登録表）に自身が入っている間だけダメージイベントをキャンセルするモブ。"
            );
            case T033_PROTECTION_BREAKER_PLUS_HURT -> new SpecEntry(
                    "ModProtectionBreaker 複合防御解体＋50,000打撃",
                    "Tier 3: 防具剥奪・防御解体打撃・隔絶ダメージ",
                    "ModProtectionBreaker.breakProtection(level, target) で既知および準汎用の MOD 保護機構を一括解体した上で、バニラの hurt(genericKill, 50000) パイプラインで撃破を試みる。",
                    "複数の保護層（イベント介入＋独自シールド変数）を持つが、保護さえ剥がせば通常の hurt() で死亡処理が走るモブ。"
            );
            case T034_ISOLATED_DAMAGE_STANDARD -> new SpecEntry(
                    "隔絶ダメージエンジン（IsolationDamageEngine）標準執行",
                    "Tier 3: 防具剥奪・防御解体打撃・隔絶ダメージ",
                    "IsolationDamageEngine.ensureIsolatedAttack(1000) を実行。Forge の LivingAttackEvent / LivingHurtEvent / LivingDamageEvent による外部キャンセル妨害を隔離・迂回してダメージを強制適用する。",
                    "Forge イベントバス（@SubscribeEvent LivingAttackEvent/LivingHurtEvent）で setCanceled(true) や setAmount(0) を行うイベント依存型無敵モブ。"
            );
            case T035_ISOLATED_DAMAGE_OVERDRIVE -> new SpecEntry(
                    "隔絶ダメージエンジン・オーバードライブ（最大HP×100倍）",
                    "Tier 3: 防具剥奪・防御解体打撃・隔絶ダメージ",
                    "対象の現在HPおよび最大HPの 100 倍（最低 10,000）を算出し、IsolationDamageEngine でイベント妨害を完全遮断したまま一撃で全HPを上回る隔絶打撃を与える。",
                    "超高HP（数百万以上）かつ Forge ダメージイベントのキャンセル・減衰フックを併用する重装ボス。"
            );
            case T036_ISOLATED_DAMAGE_MAX_CONVERGENCE -> new SpecEntry(
                    "ダメージ辞書最終奥義：防御完全解体＋10億（10^9）隔絶ダメージ収束",
                    "Tier 3: 防具剥奪・防御解体打撃・隔絶ダメージ",
                    "ModProtectionBreaker + StateScanner 全系統解体 + KillLists.purge を先行適用した直後、IsolationDamageEngine で 1.0E9F（10億）の隔絶ダメージを叩き込むダメージ辞書の最高峰技術。",
                    "「即死（setHealth/remove）は使わず、あくまでダメージによってHPを削り切る」という制約下で突破可能なあらゆる複合ダメージ耐性。"
            );

            // ==== Tier 4: 直接メソッド・生死ライフサイクル直呼び系 (T037..T048) ====
            case T037_DIRECT_SET_HEALTH_ZERO -> new SpecEntry(
                    "LivingEntity.setHealth(0.0F) 直接呼び出し",
                    "Tier 4: 直接メソッド・生死ライフサイクル直呼び",
                    "hurt() を一切通さず target.setHealth(0.0F) を直接呼び出す。ダメージイベントや防具計算をすべてスキップし、HPセッター単体での即死耐性を検査する。",
                    "hurt() や LivingAttackEvent だけをガードしており、setHealth(0) の直接呼び出しをオーバーライドしていないモブ。"
            );
            case T038_DIRECT_SET_HEALTH_NEGATIVE_MAX -> new SpecEntry(
                    "LivingEntity.setHealth(-Float.MAX_VALUE) 負の極限値注入",
                    "Tier 4: 直接メソッド・生死ライフサイクル直呼び",
                    "target.setHealth(-Float.MAX_VALUE) を呼び出す。「if (hp == 0.0F) return;」のようなピンポイントの 0 判定ガードを負の最大値で突破し、大幅なマイナスHPへ落とす。",
                    "setHealth(0.0F) だけを等値比較や軽微な加算で防いでいる不完全な setHealth オーバーライド。"
            );
            case T039_DIRECT_SET_HEALTH_NAN -> new SpecEntry(
                    "LivingEntity.setHealth(Float.NaN) 非数注入",
                    "Tier 4: 直接メソッド・生死ライフサイクル直呼び",
                    "target.setHealth(Float.NaN) を呼び出す。setHealth 内の「if (hp < minHp) hp = maxHp;」という大小比較ガードを IEEE-754 NaN の比較偽特性で素通りさせ、HPを NaN 崩壊させる。",
                    "setHealth 内で Float.isNaN(hp) を検査せず、hp <= 0.0F の不等号比較のみで不死化しているモブ。"
            );
            case T040_DIRECT_ENTITY_KILL -> new SpecEntry(
                    "Entity.kill() バニラ強制殺害メソッド直呼び",
                    "Tier 4: 直接メソッド・生死ライフサイクル直呼び",
                    "target.kill() を直接呼び出す（/kill コマンドの内部エントリポイント）。Entity.kill() 自体がオーバーライドまたは Mixin で無効化されているかを検査する。",
                    "hurt() は防ぐが kill() メソッドのオーバーライドを行っていないモブ。"
            );
            case T041_DIRECT_LIVING_DIE -> new SpecEntry(
                    "LivingEntity.die(genericKill) 死亡遷移メソッド直呼び",
                    "Tier 4: 直接メソッド・生死ライフサイクル直呼び",
                    "HP が 0 になった後の死亡確定処理である target.die(genericKill) を直接呼び出し、ドロップ生成・死亡通知・dead フラグ遷移を強制起動する。",
                    "HP が 0 にならないよう保護しているが、die() メソッド自体が直接呼ばれた場合のガードを持たないモブ。"
            );
            case T042_DIRECT_ENTITY_DISCARD -> new SpecEntry(
                    "Entity.discard() 即時消去メソッド直呼び",
                    "Tier 4: 直接メソッド・生死ライフサイクル直呼び",
                    "死亡演出や LivingDeathEvent を介さず、エンティティをワールドから即座に破棄する target.discard()（内部で remove(DISCARDED)）を直接呼び出す。",
                    "LivingDeathEvent のキャンセルや HP 固定だけで不死化しており、Entity.discard() による存在破棄を止めていないモブ。"
            );
            case T043_DIRECT_REMOVE_KILLED -> new SpecEntry(
                    "Entity.remove(RemovalReason.KILLED) 殺害理由付き除去",
                    "Tier 4: 直接メソッド・生死ライフサイクル直呼び",
                    "target.remove(Entity.RemovalReason.KILLED) を直接呼び出す。discard()（DISCARDED）のみを条件分岐で弾くモブに対し、KILLED 理由での除去を試みる。",
                    "if (reason == DISCARDED) return; のように特定の RemovalReason だけをブロックしている remove() ガード。"
            );
            case T044_DIRECT_REMOVE_DISCARDED -> new SpecEntry(
                    "Entity.remove(RemovalReason.DISCARDED) 破棄理由付き除去",
                    "Tier 4: 直接メソッド・生死ライフサイクル直呼び",
                    "target.remove(Entity.RemovalReason.DISCARDED) を直接呼び出す。discard() をオーバーライドして無効化していても、親クラスの remove(DISCARDED) 側が空いているケースを突く。",
                    "discard() メソッドだけを空実装にオーバーライドし、remove(RemovalReason) 本体を保護し忘れているモブ。"
            );
            case T045_DIRECT_REMOVE_UNLOADED_CHUNK -> new SpecEntry(
                    "Entity.remove(UNLOADED_TO_CHUNK) チャンクアンロード偽装除去",
                    "Tier 4: 直接メソッド・生死ライフサイクル直呼び",
                    "target.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK) を呼び出す。多くのチートモブが「チャンクアンロード時は除去を許可しないとセーブ壊れ・無限増殖する」ために開けている穴を突く。",
                    "KILLED / DISCARDED は拒否するが、システム都合の UNLOADED_TO_CHUNK（shouldDestroy() == false）は無条件に通す不死モブ。"
            );
            case T046_DIRECT_REMOVE_UNLOADED_PLAYER -> new SpecEntry(
                    "Entity.remove(UNLOADED_WITH_PLAYER) プレイヤー離脱偽装除去",
                    "Tier 4: 直接メソッド・生死ライフサイクル直呼び",
                    "target.remove(Entity.RemovalReason.UNLOADED_WITH_PLAYER) を呼び出す。プレイヤーログアウトに伴うシステム退避理由を偽装してワールドから退去させる。",
                    "戦闘死（KILLED）とコマンド消去（DISCARDED）のみを監視し、プレイヤー連動アンロード理由を通す防御。"
            );
            case T047_DIRECT_REMOVE_CHANGED_DIM -> new SpecEntry(
                    "Entity.remove(CHANGED_DIMENSION) 次元移動偽装除去",
                    "Tier 4: 直接メソッド・生死ライフサイクル直呼び",
                    "target.remove(Entity.RemovalReason.CHANGED_DIMENSION) を呼び出す。ネザー/エンド等のポータル通過時に旧次元エンティティを消すための必須ルートを突いて除去する。",
                    "ディメンション移動を可能にするために CHANGED_DIMENSION での remove() を許可しているボス・チートモブ。"
            );
            case T048_DIRECT_SET_REMOVED_AND_CLIENT_REMOVAL -> new SpecEntry(
                    "Entity.setRemoved(KILLED) ＋ onClientRemoval() 低層ライフサイクル直呼び",
                    "Tier 4: 直接メソッド・生死ライフサイクル直呼び",
                    "remove() を経由せず、より低層の target.setRemoved(KILLED) と target.onClientRemoval() を直接呼び出して内部除去フラグとクライアント破棄処理を強制発火させる。",
                    "Entity.remove() のみをオーバーライドし、その内部で呼ばれる setRemoved() をオーバーライドし忘れているモブ。"
            );

            // ==== Tier 5: 属性（Attribute）最大 HP ゼロ化・キャッシュ破壊系 (T049..T056) ====
            case T049_ATTR_MAX_HEALTH_BASE_ZERO -> new SpecEntry(
                    "MAX_HEALTH 属性 BaseValue = 0.0D ゼロ化",
                    "Tier 5: Attribute 最大HPゼロ化・キャッシュ破壊",
                    "target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(0.0D) を実行。最大HPの基礎値を 0 にすることで、上限クランプ時や次回の HP 更新時に生存不能（maxHp <= 0）へ追い込む。",
                    "現在HP（DATA_HEALTH_ID）の減少は防ぐが、Attributes.MAX_HEALTH の BaseValue 書き換えを監視していないモブ。"
            );
            case T050_ATTR_MAX_HEALTH_BASE_NEGATIVE -> new SpecEntry(
                    "MAX_HEALTH 属性 BaseValue = -1,000,000.0D 負数化",
                    "Tier 5: Attribute 最大HPゼロ化・キャッシュ破壊",
                    "Attributes.MAX_HEALTH の BaseValue に -1.0E6D を設定する。正の固定値 Modifier（+10000 等）が付いていても合算後の最大HPを確実に 0 以下へ引きずり下ろす。",
                    "BaseValue=0 にしても永続 AttributeModifier（ADDITION +数万）によって最大HPが正に保たれるモブ。"
            );
            case T051_ATTR_MAX_HEALTH_BASE_NAN -> new SpecEntry(
                    "MAX_HEALTH 属性 BaseValue = Double.NaN 汚染",
                    "Tier 5: Attribute 最大HPゼロ化・キャッシュ破壊",
                    "Attributes.MAX_HEALTH の BaseValue に Double.NaN を注入する。getMaxHealth() の計算結果自体を NaN に汚染し、「setHealth(getMaxHealth())」で自己回復するモブに自爆 NaN を書き込ませる。",
                    "毎 Tick 「this.setHealth(this.getMaxHealth())」を呼び出して全回復する自己修復ルーチンを持つモブ。"
            );
            case T052_ATTR_MAX_HEALTH_ADDITION_MINUS_1E9 -> new SpecEntry(
                    "MAX_HEALTH 属性 ADDITION(-10億) 減算 Modifier 注入",
                    "Tier 5: Attribute 最大HPゼロ化・キャッシュ破壊",
                    "Attributes.MAX_HEALTH に対し、Operation.ADDITION で -1.0E9D（-10億）の Transient AttributeModifier を追加する。setBaseValue() をフックして防ぐモブの最大HPを Modifier 層で 0 化する。",
                    "AttributeInstance.setBaseValue() は無効化するが、addTransientModifier() による負の加算補正は防いでいないモブ。"
            );
            case T053_ATTR_MAX_HEALTH_MULTIPLY_TOTAL_MINUS_ONE -> new SpecEntry(
                    "MAX_HEALTH 属性 MULTIPLY_TOTAL(-1.0) 最終乗算ゼロ化 Modifier",
                    "Tier 5: Attribute 最大HPゼロ化・キャッシュ破壊",
                    "Attributes.MAX_HEALTH に Operation.MULTIPLY_TOTAL で value = -1.0D の Modifier を注入。バニラの属性計算式 `total * (1.0 + (-1.0)) = 0.0` により、基礎値や加算値が幾らであっても最終最大HPを完全に 0 にする。",
                    "BaseValue や ADDITION に天文学的な正の値（10^30 等）を設定しており、減算では削り切れない超巨大最大HPモブ。"
            );
            case T054_ATTR_CACHED_VALUE_UNSAFE_ZERO -> new SpecEntry(
                    "AttributeInstance.cachedValue Unsafe 0.0D 直書き＆dirty凍結",
                    "Tier 5: Attribute 最大HPゼロ化・キャッシュ破壊",
                    "sun.misc.Unsafe を用い、MAX_HEALTH の AttributeInstance 内部フィールド `dirty = false` と `cachedValue = 0.0D` を直接メモリ書き込みする。再計算（calculateValue）自体をスキップさせて 0 を返させる。",
                    "setBaseValue や addModifier、さらには calculateValue() メソッド自体をオーバーライドして固定最大HPを返すモブ。"
            );
            case T055_ATTR_SPEED_AND_FOLLOW_ZERO -> new SpecEntry(
                    "MOVEMENT_SPEED ＆ FOLLOW_RANGE 属性ゼロ化（行動・索敵凍結）",
                    "Tier 5: Attribute 最大HPゼロ化・キャッシュ破壊",
                    "Attributes.MOVEMENT_SPEED と FOLLOW_RANGE の BaseValue を 0.0D に書き換え、移動能力とターゲット索敵距離を属性レベルで完全凍結する。",
                    "高速移動・ワープ回避や索敵連動型の反撃・回復トリガーを持つモブ。"
            );
            case T056_ATTR_MAP_FULL_CORRUPTION_AND_CLAMP -> new SpecEntry(
                    "MAX_HEALTH ゼロ化＋setHealth(0) 同時クランプ処刑",
                    "Tier 5: Attribute 最大HPゼロ化・キャッシュ破壊",
                    "Attributes.MAX_HEALTH の BaseValue を 0.0D に落とした直後に target.setHealth(0.0F) を呼び出し、「最大HPへのクランプ（Mth.clamp(hp, 0, getMaxHealth())）」を利用して回復先の上限ごと 0 に閉じ込める。",
                    "setHealth 内で「0 以下なら getMaxHealth() に戻す」というクランプ式不死ロジックを持つモブ。"
            );

            // ==== Tier 6: SynchedEntityData（DataParameter）改ざん・ロック系 (T057..T066) ====
            case T057_SYNCHED_DATA_SET_HEALTH_ZERO -> new SpecEntry(
                    "SynchedEntityData.set(DATA_HEALTH_ID, 0.0F) 同期データ直書き",
                    "Tier 6: SynchedEntityData 改ざん・ロック・抜去",
                    "LivingEntity.setHealth() メソッドをバイパスし、Mixin Accessor で取得した DATA_HEALTH_ID アクセサを使って target.getEntityData().set(DATA_HEALTH_ID, 0.0F) を直接実行する。",
                    "LivingEntity.setHealth() はオーバーライドして防いでいるが、その裏にある SynchedEntityData.set() は無防備なモブ。"
            );
            case T058_DATA_ITEM_SET_VALUE_ZERO -> new SpecEntry(
                    "SynchedEntityData.DataItem.setValue(0.0F) 内部コンテナ直書き",
                    "Tier 6: SynchedEntityData 改ざん・ロック・抜去",
                    "SynchedEntityData.set() すらバイパスし、`itemsById` マップから DATA_HEALTH_ID の内部 `DataItem<Float>` インスタンスを直接取り出して `dataItem.setValue(0.0F)` を実行する。",
                    "SynchedEntityData.set() に Mixin やフックを仕掛けて DATA_HEALTH_ID の書き換えを弾いているモブ。"
            );
            case T059_DATA_ITEM_SET_VALUE_NAN -> new SpecEntry(
                    "DataItem(DATA_HEALTH_ID).setValue(Float.NaN) 内部コンテナ NaN 注入",
                    "Tier 6: SynchedEntityData 改ざん・ロック・抜去",
                    "DATA_HEALTH_ID の `DataItem` に対して直接 `setValue(Float.NaN)` を注入する。getHealth() の戻り値を NaN にし、`getHealth() <= 0.0F` 以外の生存判定や描画・AI演算を崩壊させる（執行後は安全ガードでNPE防止）。",
                    "setHealth(NaN) は防ぐが、DataItem.setValue(NaN) による内部値の非数化はチェックしていないモブ。"
            );
            case T060_DATA_ITEM_SET_VALUE_NEGATIVE_MAX -> new SpecEntry(
                    "DataItem(DATA_HEALTH_ID).setValue(-Float.MAX_VALUE) 内部コンテナ極限負数注入",
                    "Tier 6: SynchedEntityData 改ざん・ロック・抜去",
                    "DATA_HEALTH_ID の `DataItem` に `-Float.MAX_VALUE` を直接セットする。毎 Tick 一定量を足して回復するタイプや、`value == 0.0F` のみを検知する保護を巨大な負のHPで突破する。",
                    "DataItem の値が 0.0F になった時だけ反応する軽量ウォッチャーや、固定値加算型の再生機構。"
            );
            case T061_DATA_ITEM_UNSAFE_VALUE_ZERO -> new SpecEntry(
                    "Unsafe.putObjectVolatile(DataItem.value, 0.0F) メモリ直書き",
                    "Tier 6: SynchedEntityData 改ざん・ロック・抜去",
                    "DataItem.setValue() メソッドすら呼ばず、`sun.misc.Unsafe` で `DataItem` インスタンス内の `Object value` フィールドオフセットへ直接 `Float.valueOf(0.0F)` を volatile 書き込みする。",
                    "DataItem.setValue() にバイトコード変換や Mixin フックを仕掛けて値の変更を阻止している高度な保護。"
            );
            case T062_DATA_ITEM_LOCKED_ZERO_SUBCLASS -> new SpecEntry(
                    "ASM動的生成 LockedZeroDataItem 差し替え（getValue() 恒久 0.0F ロック）",
                    "Tier 6: SynchedEntityData 改ざん・ロック・抜去",
                    "ObjectWeb ASM で実行時に動的生成した外部パッケージクラス `akashic.foreign.probe.LockedZeroDataItem`（getValue() が常に 0.0F を返し setValue() を無視する DataItem サブクラス）を `itemsById` に差し込む。",
                    "毎 Tick `setHealth(max)` や `DataItem.setValue(max)` で HP を書き戻す自己修復モブ（書き戻し自体が空振りし、getHealth() が恒久的に 0.0F になる）。"
            );
            case T063_DATA_ITEM_SLOT_EVICTION -> new SpecEntry(
                    "itemsById スロット抜去（旧 DataItem 参照切断）＋安全 0.0F 再挿入",
                    "Tier 6: SynchedEntityData 改ざん・ロック・抜去",
                    "`SynchedEntityData.itemsById` から DATA_HEALTH_ID のエントリを一旦 `remove()` して外部 MOD がフィールドにキャッシュしている旧 `DataItem` 参照を孤立させた後、NPE 防止のため新規の 0.0F DataItem を格納する。",
                    "スポーン時に取得した `DataItem` インスタンスへの直接参照をキャッシュし、そのインスタンスの value だけを監視・復元している MOD。"
            );
            case T064_CUSTOM_SYNCHED_ACCESSORS_SWEEP -> new SpecEntry(
                    "MOD独自 EntityDataAccessor（カスタム同期HP/シールド）全走査破壊",
                    "Tier 6: SynchedEntityData 改ざん・ロック・抜去",
                    "StateScanner(SYNCHED_DATA) を実行し、バニラの DATA_HEALTH_ID ではなく対象クラスが独自定義した static final EntityDataAccessor（独自HP・シールド・フェーズ・無敵フラグ）をすべて走査して 0 / false へ書き換える。",
                    "バニラの getHealth() を使わず、独自の EntityDataAccessor<Float/Integer/Boolean> で真のHPや無敵状態を管理しているチートモブ（PIG2/SuperSteve系など）。"
            );
            case T065_SYNCHED_POSE_DYING_LOCK -> new SpecEntry(
                    "Entity.setPose(Pose.DYING) 死亡姿勢同期ロック",
                    "Tier 6: SynchedEntityData 改ざん・ロック・抜去",
                    "SynchedEntityData の DATA_POSE を `Pose.DYING`（死亡倒れ込み状態）へ強制変更し、当たり判定・移動・アニメーション状態を死亡フェーズへ遷移させる。",
                    "HP 数値は維持していても Pose.DYING への遷移で行動不能や死亡完了トリガーが発火するモブ。"
            );
            case T066_SYNCHED_SHARED_FLAGS_CORRUPTION -> new SpecEntry(
                    "DATA_SHARED_FLAGS_ID（無重力・沈黙・発光解除）同期ビット改ざん",
                    "Tier 6: SynchedEntityData 改ざん・ロック・抜去",
                    "Entity の共有ビットフラグ（SharedFlags）および `silent=true`, `noGravity=true`, `glowing=false` を同期データ層で書き換え、物理挙動と演出トリガーを無力化する。",
                    "SharedFlags の特定ビットを自身のオーラやバリア維持条件として参照しているモブ。"
            );

            // ==== Tier 7: Unsafe プリミティブ・生死内部フィールド直書き系 (T067..T078) ====
            case T067_UNSAFE_DEAD_FLAG_TRUE -> new SpecEntry(
                    "LivingEntity.dead = true 死亡確定フラグ直書き",
                    "Tier 7: Unsafe プリミティブ・生死内部フィールド直書き",
                    "Mixin Accessor（LivingEntityAccessor）を通じて `LivingEntity.dead` boolean フィールドを直接 `true` に書き換える。die() メソッドの呼び出しすらバイパスして「既に死亡処理済み」の状態にする。",
                    "HP > 0 を維持し die() もキャンセルするが、`this.dead` フィールド自体が true になると Tick 処理や死亡カウントが進むモブ。"
            );
            case T068_UNSAFE_DEATH_TIME_TWENTY -> new SpecEntry(
                    "LivingEntity.deathTime = 20（死亡煙消滅フレーム到達）直書き",
                    "Tier 7: Unsafe プリミティブ・生死内部フィールド直書き",
                    "`target.deathTime = 20` を直接書き込む。バニラの `tickDeath()` は `deathTime == 20` に達した瞬間に `remove(KILLED)` を呼んでエンティティを消滅させるため、20 Tick の猶予を与えずに即座に消滅フレームへ飛ばす。",
                    "deathTime が 1〜19 の間に自己蘇生（deathTime = 0）を行うモブ、または tickDeath() の最終フレーム消去を利用する突破。"
            );
            case T069_UNSAFE_REMOVAL_REASON_KILLED -> new SpecEntry(
                    "Entity.removalReason = KILLED フィールド直接代入",
                    "Tier 7: Unsafe プリミティブ・生死内部フィールド直書き",
                    "`remove()` / `setRemoved()` メソッドを一切呼ばず、EntityAccessor で `Entity.removalReason` フィールドに `RemovalReason.KILLED` を直接メモリ代入する。これにより `isRemoved()` が即座に `true` を返すようになる。",
                    "remove() や setRemoved() のメソッド呼び出しをオーバーライドや Mixin @Inject(cancellable=true) でブロックしているすべてのモブ。"
            );
            case T070_UNSAFE_REMOVAL_REASON_DISCARDED -> new SpecEntry(
                    "Entity.removalReason = DISCARDED フィールド直接代入",
                    "Tier 7: Unsafe プリミティブ・生死内部フィールド直書き",
                    "`Entity.removalReason` フィールドに直接 `RemovalReason.DISCARDED` を代入する。KILLED 状態の自動復元ロジックを持つ対象に対し、破棄状態のフィールド直書きを試す。",
                    "removalReason == KILLED の場合のみ「戦闘死からの蘇生」を発動し、DISCARDED は無視するフィールド監視型モブ。"
            );
            case T071_UNSAFE_ADDED_TO_WORLD_FALSE -> new SpecEntry(
                    "Entity.isAddedToWorld = false（Forge ワールド所属フラグ剥奪）直書き",
                    "Tier 7: Unsafe プリミティブ・生死内部フィールド直書き",
                    "`UnsafeHelper.setFieldValue(target, \"isAddedToWorld\", false)` で Forge が管理するワールド所属フラグを直接 false に落とす。`isAddedToWorld()` を生存・有効判定に使っている全システムから対象を切り離す。",
                    "removalReason == null だけを復元し、Forge 固有の `isAddedToWorld` フィールドの改ざんを見落としている保護。"
            );
            case T072_UNSAFE_PRIMITIVE_FLOAT_HEALTH_ZERO -> new SpecEntry(
                    "Unsafe 全 float/double 独自HP・シールドフィールド 0.0 一掃",
                    "Tier 7: Unsafe プリミティブ・生死内部フィールド直書き",
                    "対象クラス階層（外部MOD定義クラス）の全フィールドを `UnsafeHelper.getRealDeclaredFields` で走査し、名前に `health / hp / life / shield` を含むすべての `float` / `double` プリミティブフィールドへ `Unsafe.putFloatVolatile / putDoubleVolatile` で 0.0 を直書きする。",
                    "バニラの getHealth() とは別に、独自の `private float realHealth` や `double bossHp` フィールドを持って毎 Tick バニラHPを上書き復元するモブ。"
            );
            case T073_UNSAFE_PRIMITIVE_FLOAT_HEALTH_NAN -> new SpecEntry(
                    "Unsafe 全 float/double 独自HP・シールドフィールド NaN 汚染",
                    "Tier 7: Unsafe プリミティブ・生死内部フィールド直書き",
                    "対象クラスの `health / hp / life / shield` 系プリミティブ `float` / `double` フィールドすべてに `Unsafe` で `NaN` を直接書き込む。",
                    "独自HPフィールドが `<= 0` になった時だけ初期値へリセットする（`if (customHp <= 0) customHp = max;`）自己修復ロジックを持つモブ。"
            );
            case T074_UNSAFE_BOOLEAN_GUARD_FLAGS_FALSE -> new SpecEntry(
                    "Unsafe 全 boolean 防御・無敵・神状態フラグ false 一掃",
                    "Tier 7: Unsafe プリミティブ・生死内部フィールド直書き",
                    "名前に `invul / immortal / shield / protect / barrier / anchor / alive / god` を含むすべての `boolean` フィールドに `Unsafe.putBooleanVolatile(target, off, false)` を直書きして無敵フラグを強制解除する。",
                    "`isImmortal = true` や `godMode = true` 等の独自 boolean フィールドで無敵状態を保持しているモブ。"
            );
            case T075_UNSAFE_BOOLEAN_DEATH_FLAGS_TRUE -> new SpecEntry(
                    "Unsafe 全 boolean 死亡・消去・破棄フラグ true 一掃",
                    "Tier 7: Unsafe プリミティブ・生死内部フィールド直書き",
                    "名前に `dead / dying / erase / kill / remove` を含むすべての `boolean` フィールドに `Unsafe.putBooleanVolatile(target, off, true)` を直書きし、モブ自身の正規死亡許可フラグを強制 ON にする。",
                    "`canDie = false` や `trulyDead = false`（これが true の時だけ消滅を許可する）といった死亡ゲートフラグを持つモブ。"
            );
            case T076_UNSAFE_INT_LIVES_COUNTERS_ZERO -> new SpecEntry(
                    "Unsafe 全 int 残機・フェーズ・復活回数カウンタ 0 一掃",
                    "Tier 7: Unsafe プリミティブ・生死内部フィールド直書き",
                    "名前に `lives / regen / phase / shield / revive / totem` を含むすべての `int` フィールドに `Unsafe.putIntVolatile(target, off, 0)` を直書きし、残機ストックやフェーズ移行回数を一気に 0 に枯渇させる。",
                    "複数ゲージ（残機 `lives = 99`）や多段階ボスフェーズ、復活カウンタが 0 になるまで絶対に死なないモブ。"
            );
            case T077_UNSAFE_VOID_COORDINATE_WARP -> new SpecEntry(
                    "setPosRaw(x, -10000.0, z) 深層奈落座標への直接空間転移",
                    "Tier 7: Unsafe プリミティブ・生死内部フィールド直書き",
                    "`teleportTo()` 等のイベント付き移動メソッドを通さず、`target.setPosRaw(x, -10000.0D, z)` で座標フィールドを直接 Y = -10000 の超深層奈落へ書き換える。",
                    "通常の移動・ノックバックは無効化するが、深層奈落（Y < -64〜-1000）に置かれるとバニラまたは自身の境界チェックで消滅するモブ。"
            );
            case T078_UNSAFE_NAN_COORDINATE_INJECTION -> new SpecEntry(
                    "setPosRaw(NaN, NaN, NaN) ＋ deltaMovement(NaN) 空間座標崩壊",
                    "Tier 7: Unsafe プリミティブ・生死内部フィールド直書き",
                    "対象の座標 `position (x, y, z)` と速度ベクトル `deltaMovement` に `Double.NaN` を直書きする。空間計算・チャンク所属判定・AABB 交差判定を破綻させてワールド維持を不能にする（検証継続時は安全座標へ復帰）。",
                    "座標の有限値チェック（Double.isFinite）を持たず、NaN 座標になるとチャンクセクションやAI更新から脱落するモブ。"
            );

            // ==== Tier 8: 準汎用フラグ辞書・NBT・Capability・AI/脳/部位解体系 (T079..T088) ====
            case T079_STATE_SCANNER_INTERFACE_BREAK -> new SpecEntry(
                    "StateScanner(INTERFACE) インターフェース契約経由の防御解体",
                    "Tier 8: 準汎用フラグ辞書・NBT・Capability・AI/部位解体",
                    "`StateScanner.apply(target, BREAK_TARGET, INTERFACE)` を実行。対象が実装する MOD インターフェースのセッター/状態変更メソッドを準汎用フラグ辞書（FlagDictionary）に基づいて呼び出し、正規ルートで保護を解除する。",
                    "専用の公開インターフェースメソッド（`setProtected(false)` 等）経由でのみ状態変更を受け付ける設計のモブ。"
            );
            case T080_STATE_SCANNER_INJECTED_FIELD_BREAK -> new SpecEntry(
                    "StateScanner(INJECTED_FIELD) Mixin 注入フィールド解体",
                    "Tier 8: 準汎用フラグ辞書・NBT・Capability・AI/部位解体",
                    "`StateScanner.apply(target, BREAK_TARGET, INJECTED_FIELD)` を実行。Entity / LivingEntity 等のバニラ基底クラスへ他 MOD が Mixin で注入したフィールド（`$` や MOD プレフィックス付き）を特定し、保護値を解除する。",
                    "モブ自身のサブクラスではなく、Mixin で `Entity` や `LivingEntity` に注入した隠しフィールドで不死状態を管理している MOD。"
            );
            case T081_STATE_SCANNER_OWN_FIELD_BREAK -> new SpecEntry(
                    "StateScanner(OWN_FIELD) モブ固有クラス宣言フィールド精密解体",
                    "Tier 8: 準汎用フラグ辞書・NBT・Capability・AI/部位解体",
                    "`StateScanner.apply(target, BREAK_TARGET, OWN_FIELD)` を実行。モブ固有クラスの全宣言フィールドを型・命名・値レンジのヒューリスティック辞書で解析し、HP・無敵・死亡フラグを精密に反転させる。",
                    "難読化や独自命名されたクラス内フィールドに生存・シールド状態を保持しているカスタムボスモブ。"
            );
            case T082_STATE_SCANNER_CAPABILITY_AND_INVALIDATE -> new SpecEntry(
                    "StateScanner(CAPABILITY) 解体 ＋ invalidateCaps() 強制無効化",
                    "Tier 8: 準汎用フラグ辞書・NBT・Capability・AI/部位解体",
                    "`StateScanner.apply(CAPABILITY)` で対象にアタッチされた Forge Capability 内の保護フィールドを解体した上、`target.invalidateCaps()` を呼び出して全 Capability プロバイダ自体を無効化する。",
                    "エンティティ本体ではなく Forge の `ICapabilityProvider` / カスタム Capability インスタンス側に真のHPや不死フラグを保持している MOD。"
            );
            case T083_STATE_SCANNER_PERSISTENT_NBT_BREAK -> new SpecEntry(
                    "StateScanner(PERSISTENT) 解体 ＋ ForgeData 永続 NBT 完全消去",
                    "Tier 8: 準汎用フラグ辞書・NBT・Capability・AI/部位解体",
                    "`StateScanner.apply(PERSISTENT)` で永続データの保護キーを書き換えた上、`target.getPersistentData().getAllKeys().clear()` で Forge の `ForgeData` CompoundTag を丸ごと空にする。",
                    "`entity.getPersistentData()`（ForgeData NBT）に不死フラグやカスタムHPを保存し、毎 Tick そこから状態を復元しているモブ。"
            );
            case T084_NBT_LOAD_DEAD_STATE_OVERWRITE -> new SpecEntry(
                    "CompoundTag load(Health:0f, DeathTime:20s) NBT 逆シリアライズ死亡上書き",
                    "Tier 8: 準汎用フラグ辞書・NBT・Capability・AI/部位解体",
                    "`target.saveWithoutId(tag)` で現在の NBT を取得し、`Health = 0.0F` および `DeathTime = 20s` に書き換えた CompoundTag を `target.load(tag)`（readAdditionalSaveData 含む）で対象自身に読み込ませる。",
                    "setHealth() や hurt() は完全に塞いでいるが、ワールドロード用の `Entity.load(CompoundTag)` からは素直に Health=0 を読み込んでしまうモブ。"
            );
            case T085_MULTIPART_SUBPARTS_SEVER_AND_KILL -> new SpecEntry(
                    "PartEntity 全サブパーツ（部位エンティティ）強制切断・全破棄",
                    "Tier 8: 準汎用フラグ辞書・NBT・Capability・AI/部位解体",
                    "`target.getParts()` で取得されるすべての `PartEntity<?>` 子部位に対し、`discard()` と `setRemoved(KILLED)` を個別に強制執行して部位構造を全壊させる。",
                    "「全サブパーツ（頭・翼・尾・コア等）が破壊されるまで本体が絶対に死なない」という部位連動型マルチパートボス。"
            );
            case T086_VEHICLE_AND_PASSENGER_FORCED_EJECT -> new SpecEntry(
                    "stopRiding() ＋ ejectPassengers() 騎乗・乗客リンク強制切断",
                    "Tier 8: 準汎用フラグ辞書・NBT・Capability・AI/部位解体",
                    "`target.stopRiding()` と `target.ejectPassengers()` を実行し、対象が乗っている乗り物（台座・バリア実体）および対象に乗っている守護エンティティとのリンクを強制解除する。",
                    "特定の乗り物や眷属エンティティと騎乗リンクしている間だけ無敵化するギミックボス。"
            );
            case T087_AI_GOAL_BRAIN_TOTAL_LOBOTOMY -> new SpecEntry(
                    "AI GoalSelector / TargetSelector / Brain 完全消去＆NoAI 凍結",
                    "Tier 8: 準汎用フラグ辞書・NBT・Capability・AI/部位解体",
                    "`Mob.setTarget(null)`, `setNoAi(true)`, `goalSelector.removeAllGoals()`, `targetSelector.removeAllGoals()`, `Brain.removeAllBehaviors()` を一括実行し、思考ルーチンとスキル発動を永久停止させる。",
                    "AI Goal や Brain Behavior の Tick 実行内で自己回復・無敵バリア展開・クローン召喚・カウンター即死を発動するモブ。"
            );
            case T088_REFLECTIVE_ZERO_ARG_CLEANUP_SWEEP -> new SpecEntry(
                    "外部MOD定義 dispose/close/destroy/cleanup/erase 引数なしメソッド反射一斉呼び出し",
                    "Tier 8: 準汎用フラグ辞書・NBT・Capability・AI/部位解体",
                    "対象クラス階層（外部MODクラス）から引数 0 個・戻り値 void で名前に `dispose / close / cleanup / destroy / invalidate / erase / release / kill` を含むメソッドをリフレクションで探し出し、強制 invoke する。",
                    "モブ自身が内部に「自分が正規に消える時にだけ呼ぶ `private void destroyBoss()` や `cleanup()`」を持っている設計。"
            );

            // ==== Tier 9: 歴代チート MOD 特化解体（抹殺棒技術）系 (T089..T098) ====
            case T089_COUNTER_IMMORTAL_WITHER_STRIP -> new SpecEntry(
                    "対 Immortal Wither：シャドウシールド＆超再生フィールド剥奪",
                    "Tier 9: 歴代チートMOD特化解体（抹殺棒技術）",
                    "StateScanner 全系統解体に加え、プリミティブ float/double のシャドウHP・シールドフィールドを Unsafe で 0.0F に一掃してから setHealth(0.0F) を執行する。",
                    "Immortal Wither 系の多重シャドウHPフィールドと毎 Tick 強制再生シールド。"
            );
            case T090_COUNTER_TESSERACT_BEAST_BREAKER -> new SpecEntry(
                    "対 Tesseract Beast：次元アンカー（dimensionalAnchor）＆HyperHealth 解体",
                    "Tier 9: 歴代チートMOD特化解体（抹殺棒技術）",
                    "StateScanner 全系統解体と全 boolean 防御/次元アンカーフラグ（`anchor / invul / shield`）の Unsafe false 化を同時適用し、次元固定を解除して HP を 0 化する。",
                    "次元アンカー boolean フラグが true の間はあらゆる除去とダメージを無効化する Tesseract 型モブ。"
            );
            case T091_COUNTER_FLASHFUR_OMNI_PURGE -> new SpecEntry(
                    "対 Flashfur / Omni-Mobs：グローバル保護リスト追放＆シャドウHP抹消",
                    "Tier 9: 歴代チートMOD特化解体（抹殺棒技術）",
                    "`KillLists.purge(target)` で Flashfur / Omni-Mobs 系の static 保護コレクションから対象を抹消し、StateScanner 全系統解体と setHealth(0.0F) を連続執行する。",
                    "グローバルな保護エンティティリストとエンティティ内フラグの二重チェックで不死化する Flashfur / Omni-Mobs 系モブ。"
            );
            case T092_COUNTER_NOSUGAR_ERASE_INVERT -> new SpecEntry(
                    "対 NoSugar：Erase / InstantKill 状態反転＆死亡フレーム直結",
                    "Tier 9: 歴代チートMOD特化解体（抹殺棒技術）",
                    "StateScanner 全系統で NoSugar 系の消去/不死フラグを反転させた上、`LivingEntity.dead = true`, `deathTime = 20`, `setHealth(0.0F)` を同時に叩き込む。",
                    "NoSugar 系のカスタム Erase フラグ管理と死亡キャンセル機構。"
            );
            case T093_COUNTER_AVARITIA_ENIGMA_TELETUBBIES -> new SpecEntry(
                    "対 Avaritia / Enigma / Teletubbies：神装備・神状態（God-State）強制剥奪",
                    "Tier 9: 歴代チートMOD特化解体（抹殺棒技術）",
                    "全 EquipmentSlot の強制空化（Infinity Armor 等の神防具剥奪）＋ StateScanner 全系統による神状態フラグ解除＋ setHealth(0.0F) を一括執行する。",
                    "Infinity 装備等の特定アイテム装着判定や God-Mode フラグによって全イベントを無効化する Avaritia / Enigma 系防御。"
            );
            case T094_COUNTER_SUPER_STEVE_CALLBACK_SEVER -> new SpecEntry(
                    "対 SuperSteve：独自体力フィールド抹消＆コールバック切断除去",
                    "Tier 9: 歴代チートMOD特化解体（抹殺棒技術）",
                    "`KillLists.purge(target)` ＋ 全プリミティブ float/double HPフィールドの Unsafe 0.0F 直書き ＋ `Entity.removalReason = KILLED` のフィールド直書きを組み合わせ、SuperSteve 系の独自生存管理を切断する。",
                    "バニラHPを無視して独自フィールドとエージェント/コールバック保護で生存し続ける SuperSteve 系エンティティ。"
            );
            case T095_COUNTER_PIG2_TRUELIFE_ZERO_PROXY -> new SpecEntry(
                    "対 PIG2：MOD自身の正規死亡ルート（finishWithModOwnDeath）プロキシ執行",
                    "Tier 9: 歴代チートMOD特化解体（抹殺棒技術）",
                    "`ModProtectionBreaker.finishWithModOwnDeath(target)` と `StateScanner` を実行。PIG2 の `Pig2Helper.annihilate` / `TrueLife=0` 等、相手 MOD 自身が内部に持つ「正規の死亡・消去メソッド」を逆利用して自滅させる。",
                    "外部からの死亡処理をすべて拒否し、PIG2 内部クラスの専用メソッド・TrueLife 変数からのみ死亡を受け付ける排他防御。"
            );
            case T096_COUNTER_MADNESS_CONTROLLER_ANNIHILATE -> new SpecEntry(
                    "対 Madness Entities：外部コントローラ切断（MadnessAnnihilation）",
                    "Tier 9: 歴代チートMOD特化解体（抹殺棒技術）",
                    "`MadnessAnnihilation.annihilate(level, target)` を実行（Runtime.halt トラップを踏まないよう変換器には触れず、ワールド内のエンティティ制御リンクと生存状態を安全に解体する）。",
                    "エンティティ外部のコントローラや監視リストで生存を維持する Madness Entities 系モブ。"
            );
            case T097_COUNTER_STATIC_KILL_LISTS_AND_NEUTRALIZER -> new SpecEntry(
                    "準汎用 KillLists.purge ＋ GenericCheatMobNeutralizer 包括無력化",
                    "Tier 9: 歴代チートMOD特化解体（抹殺棒技術）",
                    "`KillLists.purge(target)` で全静的リストを掃除した上、`GenericCheatMobNeutralizer.neutralize(level, target)` で未知のチートモブが持つイベントリスナー・Tick 復活・特殊フィールドを包括的に無力化する。",
                    "個別 MOD 名に依存しない準汎用構造のチートモブ（静的セット登録＋独自フィールド＋イベントフック複合型）。"
            );
            case T098_COUNTER_MOD_PROTECTION_BREAKER_FULL -> new SpecEntry(
                    "ModProtectionBreaker フルパス解体 ＋ 自MOD死亡ルート ＋ discard 処刑",
                    "Tier 9: 歴代チートMOD特化解体（抹殺棒技術）",
                    "`ModProtectionBreaker.breakProtection` → `finishWithModOwnDeath` → `setHealth(0.0F)` → `discard()` の 4 段階フルパスを連続執行する Tier 9 総決算技術。",
                    "既知・準汎用の複数 MOD 防御ギミックを多重に組み合わせたハイブリッド不死モブ。"
            );

            // ==== Tier 10: ワールド全層抹消・深層 JVM メモリ崩壊・終焉機構（Terminus）系 (T099..T108) ====
            case T099_WORLD_TICK_LIST_AND_NAV_EVICTION -> new SpecEntry(
                    "ServerLevel.entityTickList からの直接追放（Tick 更新永久停止）",
                    "Tier 10: ワールド全層抹消・深層JVMメモリ崩壊・終焉機構",
                    "ServerLevelTickListAccessor を通じて `ServerLevel.entityTickList` から対象エンティティを直接 `remove(target)` する。エンティティの `tick()` / `baseTick()` 自体が二度とサーバーから呼ばれなくなる。",
                    "自身の `tick()` メソッド内で毎 Tick HP全回復・デバフ解除・周囲即死攻撃・座標復元を実行している Tick 駆動型チートモブ。"
            );
            case T100_WORLD_LOOKUP_AND_KNOWN_UUID_EVICTION -> new SpecEntry(
                    "PersistentEntitySectionManager (EntityLookup & knownUuids) 登録抹消",
                    "Tier 10: ワールド全層抹消・深層JVMメモリ崩壊・終焉機構",
                    "`ServerLevel.entityManager` の内部フィールドを走査し、`knownUuids` セットおよび `EntityLookup` の UUID マップ・ID マップ（Int2ObjectMap）から対象の UUID と数値 ID を直接削除する。",
                    "Entity.remove() を無効化していても、ワールドのエンティティ検索インデックス（`level.getEntity(uuid/id)`）から存在を抹消する。"
            );
            case T101_WORLD_SECTION_AND_CHUNK_TRACKER_EVICTION -> new SpecEntry(
                    "ChunkMap.entityMap（TrackedEntity パケット追跡表）からの直接追放",
                    "Tier 10: ワールド全層抹消・深層JVMメモリ崩壊・終焉機構",
                    "`ServerChunkCache.chunkMap` 内の `Int2ObjectMap (entityMap)` から対象のエンティティ ID を直接除去し、サーバーからクライアントへのパケット同期トラッカーを切り離す。",
                    "ワールドのチャンク・トラッカー層に居座り続けることでクライアントへの描画・存在同期を維持するモブ。"
            );
            case T102_WORLD_LEVEL_CALLBACK_NULL_AND_AABB_ZERO -> new SpecEntry(
                    "levelCallback = NULL 切断 ＋ AABB(0,0,0,0,0,0) 当たり判定消滅",
                    "Tier 10: ワールド全層抹消・深層JVMメモリ崩壊・終焉機構",
                    "EntityAccessor で `Entity.levelCallback` を `EntityInLevelCallback.NULL` に差し替えてワールドとのコールバック配線を切断し、さらに `setBoundingBox(new AABB(0,0,0,0,0,0))` で物理体積を点（体積0）に潰す。",
                    "AABB 交差判定（getEntitiesOfClass 等）や levelCallback 経由のチャンク移動フックで存在を維持・攻撃するモブ。"
            );
            case T103_IDENTITY_UUID_SCRAMBLE_AND_NEGATIVE_ID -> new SpecEntry(
                    "Entity.id = -1 ＆ ランダム UUID 強制書き換え（アイデンティティ隔離）",
                    "Tier 10: ワールド全層抹消・深層JVMメモリ崩壊・終焉機構",
                    "EntityAccessor で対象の `uuid` と `stringUUID` を新規の乱数 UUID に書き換え、数値 `id` を `-1` に変更する（対象が生き残って次段階へ進む場合は監査ハーネスが元IDへ戻す）。",
                    "「初期スポーン時の UUID または Entity ID を外部マネージャーが記憶し、毎 Tick その UUID で検索して護衛・蘇生する」外部監視型ボスマネージャー。"
            );
            case T104_UNSAFE_OBJECT_MEMORY_OBLITERATION -> new SpecEntry(
                    "Unsafe 外部MOD参照フィールド null 化 ＆ WeakReference クリア（メモリ崩壊）",
                    "Tier 10: ワールド全層抹消・深層JVMメモリ崩壊・終焉機構",
                    "`sun.misc.Unsafe.putObjectVolatile` を用い、対象クラス（外部MOD階層）が持つ非プリミティブ参照フィールド（コントローラ・AI・シールドオブジェクト・WeakReference等、ただし Entity/Level を除く）をすべて `null` で埋め尽くす。",
                    "複雑な独自クラスオブジェクト（ShieldController, BossPhaseManager 等）をフィールドに保持して無敵ロジックを委譲しているモブ。"
            );
            case T105_TRUE_ANNIHILATION_ENGINE_EXECUTION -> new SpecEntry(
                    "真・抹殺エンジン（TrueAnnihilationEngine.annihilateEntity）執行",
                    "Tier 10: ワールド全層抹消・深層JVMメモリ崩壊・終焉機構",
                    "`TrueAnnihilationEngine.annihilateEntity` を実行。内部フラグ解体・同期データ0化・ServerLevel/ChunkMap/SectionStorage 全層からの物理的引っこ抜き・クライアント消去パケット送信を一貫執行する。",
                    "単一の除去手段では消えない、複数のワールド構造にまたがる高難度チートモブ。"
            );
            case T106_AGENT_ASSISTED_ANNIHILATION_EXECUTION -> new SpecEntry(
                    "JavaAgent 補助抹殺（AnnihilationExecutionAgent：イベントバス浄化＆特化中和）",
                    "Tier 10: ワールド全層抹消・深層JVMメモリ崩壊・終焉機構",
                    "`AnnihilationExecutionAgent` により Witherzilla / Flashfur 等の深層中和と `sanitizeEventBus()`（Forge イベントバス上の悪性不死リスナーの無力化）を実行する。",
                    "Forge EventBus 自体に最高優先度・キャンセル不可バイパスの不死リスナーを登録して介入する MOD。"
            );
            case T107_TERMINUS_ENGINE_SINGLE_EXECUTION -> new SpecEntry(
                    "終焉機構（TerminusEngine.executeTerminus）単体執行",
                    "Tier 10: ワールド全層抹消・深層JVMメモリ崩壊・終焉機構",
                    "終焉の魔杖の中核である `TerminusEngine.executeTerminus(level, host, target)` を執行。存在置換・メモリ/ワールド完全追放・終焉確定記録を一括で行う。",
                    "通常の抹殺棒では突破できない最上位クラスの不死・自己修復・イベント乗っ取りモブ。"
            );
            case T108_OMEGA_TERMINUS_CONVERGENCE -> new SpecEntry(
                    "最終段階 T108：終焉・極（Omega Terminus Convergence — 全108層同時収束）",
                    "Tier 10: ワールド全層抹消・深層JVMメモリ崩壊・終焉機構",
                    "`ModProtectionBreaker` + `finishWithModOwnDeath` + `StateScanner(全Source)` + `KillLists.purge` + `GenericCheatMobNeutralizer` + `AnnihilationExecutionAgent` + `TrueAnnihilationEngine` + `TerminusEngine` を単一アトミックフレーム内で同時収束執行する。",
                    "全 108 段階の最高到達点。どれか 1 つの防御層でも残っていると瞬時に他の層を自己修復する相互監視型・究極不死モブ。"
            );
        };
    }
}
