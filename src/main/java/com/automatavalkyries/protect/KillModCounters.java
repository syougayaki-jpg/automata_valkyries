package com.automatavalkyries.protect;

import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.CallerAttribution;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** 登録済みの {@link KillModCounter} をまとめて適用する */
public final class KillModCounters {

    /**
     * @param name       記録に出す対策名
     * @param probeClass 相手の MOD のクラス（その MOD の ID を引くため。読み込みはしない）
     */
    private record Entry(String name, String probeClass, KillModCounter counter) {}

    /**
     * 適用順。値を偽装する ASM / エージェントを持つ MOD（No-Sugar, The Trial Monolith）を先に置くこと。フラグが立ったままだと以後の
     * getHealth / isRemoved 等の読み出しがすべて偽装される。
     */
    private static final List<Entry> COUNTERS = List.of(
            new Entry("no_sugar", "com.test.nosugar.utils.interfaces.ILivingEntity", EprotectNoSugarCounter::counter),
            new Entry("omni_mobs", "flashfur.omnimobs.util.GetHealthModifyUtil", EprotectOmniMobsCounter::counter),
            new Entry("trial_monolith", "io.github.kosianodangoo.trialmonolith.api.mixin.ISoulDamage", EprotectTrialMonolithCounter::counter),
            new Entry("imds", "net.mcreator.imds_reborn.ImdsKillManager", EprotectImdsCounter::counter),
            new Entry("hyperlink", "com.sakurafuld.hyperdaimc.api.mixin.ILivingEntityMuteki", EprotectHyperlinkCounter::counter)
    );

    private KillModCounters() {}

    /** すべての MOD の後始末を適用する。どれかがフラグを解除した場合は true（解除した MOD を攻撃として記録する） */
    public static boolean counterAll(LivingEntity entity) {
        boolean found = false;
        for (Entry entry : COUNTERS) {
            if (entry.counter().counter(entity)) {
                found = true;
                AttackLog.neutralized(entity, CallerAttribution.modOfClassName(entry.probeClass()), entry.name());
            }
        }
        return found;
    }
}
