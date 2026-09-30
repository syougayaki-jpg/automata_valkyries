package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
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
}
