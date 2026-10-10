package com.automatavalkyries.trial;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * 回想のトライアル 1 つ（過去の回想という設定で、回想の空間で指定された条件のもとボスを倒す。2026-10-04 ユーザー指定。条件と数値は【提案】）。
 * ボスはできているもの（ルクシオン・ガリアフォート）は本物が、まだのものは検証用ボスのエレノアが代わりに出る（{@link TrialManager#spawnBoss} で出し分ける）。
 *
 * @param id         名前（{@code trial.automata_valkyries.<id>}）。回想するボスの名前（{@code boss.automata_valkyries.<id>}）も兼ねる
 * @param timeLimit  ボスが出てから倒すまでの制限時間（Tick）
 * @param hitLimit   被弾してよい回数（0 = 制限なし）。超えると失敗
 * @param frail      最大 HP が半分になる
 * @param noHeal     体力が回復しない
 * @param meleeOnly  ボスには自分の手の攻撃（近接）しか効かない
 * @param bossAttack ボスの攻撃力の倍率
 */
public record Trial(String id, int timeLimit, int hitLimit, boolean frail, boolean noHeal, boolean meleeOnly, float bossAttack) {

    /** 回想のトライアルの一覧（ボスの登場順。ロストカタストロフィーが最後） */
    public static final List<Trial> ALL = List.of(
            new Trial("luxion", 20 * 180, 0, false, false, false, 1.5F),           // 光速の残影: 3 分以内
            new Trial("galliafort", 20 * 300, 0, false, false, true, 1.0F),        // 不落の城壁: 近接攻撃のみ
            new Trial("memento_mori", 20 * 300, 5, false, false, false, 1.5F),     // 死蝶の舞踏: 被弾 5 回まで
            new Trial("time_ruler", 20 * 240, 0, false, true, false, 2.0F),        // 凍てつく刻: 回復なし・4 分以内
            new Trial("lost_catastrophe", 20 * 300, 10, true, false, false, 2.5F)); // 零の追憶: 最大 HP 半分・被弾 10 回まで

    public static Trial get(int index) {
        return ALL.get(Math.floorMod(index, ALL.size()));
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).id.equals(id)) return i;
        return -1;
    }

    public Component title() {
        return Component.translatable("trial.automata_valkyries." + id);
    }

    public Component bossName() {
        return Component.translatable("boss.automata_valkyries." + id);
    }

    /** 条件の説明（「制限時間 3:00・被弾 5 回まで」など） */
    public Component conditions() {
        MutableComponent c = Component.translatable("trial.automata_valkyries.cond.time", clock(timeLimit));
        if (hitLimit > 0) c.append(" / ").append(Component.translatable("trial.automata_valkyries.cond.hits", hitLimit));
        if (frail) c.append(" / ").append(Component.translatable("trial.automata_valkyries.cond.frail"));
        if (noHeal) c.append(" / ").append(Component.translatable("trial.automata_valkyries.cond.no_heal"));
        if (meleeOnly) c.append(" / ").append(Component.translatable("trial.automata_valkyries.cond.melee_only"));
        return c;
    }

    /** Tick を「分:秒」に */
    public static String clock(int ticks) {
        int s = Math.max(0, ticks) / 20;
        return s / 60 + ":" + String.format("%02d", s % 60);
    }
}
