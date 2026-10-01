package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.InjectedMembers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 起動時の診断（報告のみ）。
 * エンティティの生死・ワールド管理に関わる vanilla クラスへ、他 MOD が Mixin で追加した処理を一覧にしてログへ出す。
 * 保護対象が倒される・消える不具合を調べるときの手がかり用で、他 MOD の処理には一切手を加えない。
 */
public final class ForeignHookReport {

    /** 生死・存在管理に関わる主なクラス */
    private static final Class<?>[] WATCHED = {
            Entity.class, LivingEntity.class, Mob.class, SynchedEntityData.class,
            ServerLevel.class, EntityTickList.class, PersistentEntitySectionManager.class, ChunkMap.class
    };

    /** 名前から即死・消去系と推測できる処理 */
    private static final Pattern SUSPICIOUS = Pattern.compile(
            "(?i)kill|erase|remove|death|dead|die|soul|novel|forcedie|instant|health|alive");

    private ForeignHookReport() {}

    public static void log() {
        // mixin クラス名 → (対象クラス.メソッド名)
        Map<String, Set<String>> byMixin = new TreeMap<>();
        for (Class<?> target : WATCHED) {
            for (Method method : target.getDeclaredMethods()) {
                MixinMerged merged = method.getAnnotation(MixinMerged.class);
                if (merged == null || merged.mixin().startsWith("com.automatavalkyries.")) continue;
                byMixin.computeIfAbsent(merged.mixin(), k -> new TreeSet<>())
                        .add(target.getSimpleName() + "." + method.getName());
            }
        }

        logInjectedFields();
        if (byMixin.isEmpty()) {
            AutomataValkyries.LOGGER.info("[eprotect] No foreign hooks on entity lifecycle classes");
            return;
        }
        AutomataValkyries.LOGGER.info("[eprotect] Foreign hooks on entity lifecycle classes ({} mixins):", byMixin.size());
        byMixin.forEach((mixin, methods) -> {
            boolean suspicious = methods.stream().anyMatch(m -> SUSPICIOUS.matcher(m).find());
            String line = String.format("[eprotect]   %s%s -> %s", suspicious ? "(要確認) " : "", mixin, methods);
            if (suspicious) AutomataValkyries.LOGGER.warn(line);
            else AutomataValkyries.LOGGER.info(line);
        });
    }

    /**
     * 他 MOD がエンティティのクラスに足したフィールドと、フラグ辞書での扱い（保護対象なら戻す・抹殺で外す）。
     * 辞書の取り違えを見つける手がかり（取り違えは設定 flagDictionary.ignore で外せる）。
     */
    private static void logInjectedFields() {
        for (Class<?> target : new Class<?>[]{Entity.class, LivingEntity.class, Mob.class}) {
            for (InjectedMembers.InjectedField injected : InjectedMembers.fields(target)) {
                if (CallerAttribution.SELF.equals(injected.mod())) continue;
                // 準汎用の処理が扱うのは、static でも final でもない boolean・数値だけ
                int modifiers = injected.field().getModifiers();
                Class<?> type = injected.field().getType();
                if (java.lang.reflect.Modifier.isStatic(modifiers) || java.lang.reflect.Modifier.isFinal(modifiers)
                        || !(type.isPrimitive() && type != char.class)) continue;
                String name = injected.field().getName();
                String verdict = FlagDictionary.harmfulOnProtected(name) ? "reset on protected"
                        : FlagDictionary.protectiveOnTarget(name) || FlagDictionary.livesCounter(name) ? "broken by annihilation" : "-";
                AutomataValkyries.LOGGER.info("[eprotect]   injected field {}.{} : {} ({}) -> {}", target.getSimpleName(), name,
                        injected.field().getType().getSimpleName(), injected.mod(), verdict);
            }
        }
    }
}
