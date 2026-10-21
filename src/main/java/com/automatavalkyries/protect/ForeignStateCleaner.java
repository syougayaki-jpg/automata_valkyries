package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.StateScanner;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 保護対象に他 MOD が付けた「状態」を毎 Tick 元に戻す（準汎用。MOD ごとの対策が無い即死 MOD にも効かせる）。
 * 名前の分類は {@link FlagDictionary}、読み書きする場所は {@link StateScanner}。
 * <ol>
 *   <li>状態: 他 MOD のインターフェース・既存クラスに足されたフィールド・Capability・永続データのうち、
 *       名前が死亡・消去・停止・HP 改ざん・守りの迂回を表すものを、boolean は false、数値は 0 に戻す・キーを消す
 *       （保護・免除など名前が守りを表すものは触らない）</li>
 *   <li>状態異常: 他 MOD の「有害」な状態異常と、名前が死亡・停止を表す他 MOD の状態異常を外す
 *       （vanilla の状態異常は通常の戦闘の一部なので残す）</li>
 * </ol>
 */
public final class ForeignStateCleaner {

    private static final EnumSet<StateScanner.Source> SOURCES = EnumSet.allOf(StateScanner.Source.class);

    private ForeignStateCleaner() {}

    /** @return 何か戻した場合 true */
    public static boolean clean(LivingEntity entity) {
        com.automatavalkyries.annihilate.FlagKillPredicates.ensureAgentProtection();
        boolean cleaned = resetStates(entity);
        cleaned |= removeForeignHarmfulEffects(entity);
        var box = entity.getBoundingBox();
        if (box.getXsize() < 0.001D || box.getYsize() < 0.001D || box.getZsize() < 0.001D) {
            entity.refreshDimensions();
            AttackLog.neutralized(entity, CallerAttribution.UNKNOWN, "hitbox:aabb_collapsed");
            cleaned = true;
        }
        if (entity.noPhysics && !entity.isPassenger()) {
            entity.noPhysics = false;
            AttackLog.neutralized(entity, CallerAttribution.UNKNOWN, "physics:no_physics");
            cleaned = true;
        }
        return cleaned;
    }

    private static boolean resetStates(LivingEntity entity) {
        List<StateScanner.Change> changes = StateScanner.apply(entity, StateScanner.CLEAN_PROTECTED, SOURCES);
        for (StateScanner.Change change : changes) {
            AttackLog.neutralized(entity, change.mod(), "state:" + change);
        }
        return !changes.isEmpty();
    }

    private static boolean removeForeignHarmfulEffects(LivingEntity entity) {
        List<MobEffect> toRemove = new ArrayList<>();
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            MobEffect effect = instance.getEffect();
            ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (key == null || !CallerAttribution.isForeign(key.getNamespace())) continue;
            if (effect.getCategory() == MobEffectCategory.HARMFUL || FlagDictionary.harmfulOnProtected(key.getPath())) {
                toRemove.add(effect);
            }
        }
        for (MobEffect effect : toRemove) {
            entity.removeEffect(effect);
            ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            AttackLog.neutralized(entity, key != null ? key.getNamespace() : CallerAttribution.UNKNOWN, "effect:" + key);
        }
        if (!toRemove.isEmpty()) AutomataValkyries.LOGGER.debug("[eprotect] Removed {} foreign effect(s) from {}", toRemove.size(), entity);
        return !toRemove.isEmpty();
    }
}
