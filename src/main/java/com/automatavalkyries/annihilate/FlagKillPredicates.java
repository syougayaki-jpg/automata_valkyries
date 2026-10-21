package com.automatavalkyries.annihilate;

import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.FlagDictionary.Category;
import com.automatavalkyries.protect.flag.FlagDictionaryLink;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 抹殺・解体側で使うフラグ辞書の判定と JVM エージェント整合性連携（2026-10-21 段 2: {@link FlagDictionary} から分離）。
 * <p>
 * 設計: {@code docs/SPEC_FLAG_DICTIONARY_V2.md} §4。
 * 辞書本体（{@link FlagDictionary}）には語彙の読み込み・単語分け・{@code classify} と守る側の判定を残し、
 * 抹殺対象（チート MOB・不死プレイヤー等）の守り・復活回数・死亡フラグ・終了メソッド・危険度判定および
 * フラグ辞書エージェント連携をこのクラスで担う。
 * </p>
 */
public final class FlagKillPredicates {

    private FlagKillPredicates() {}

    // ---- JVM エージェント整合性連携 ---------------------------------------------

    /**
     * JVM エージェント（FlagDictionaryProtector / FlagDictionaryAgent）と連携し、辞書の整合性を点検・修復する。
     */
    public static synchronized boolean ensureAgentProtection() {
        boolean repaired = FlagDictionaryLink.verifyAndRepair();
        if (FlagDictionary.entries() == null || FlagDictionary.entries().isEmpty()) {
            FlagDictionary.reloadFromConfig();
            repaired = true;
        }
        return repaired;
    }

    /** JVM エージェントによるフラグ辞書防護が有効か */
    public static boolean isProtectedByAgent() {
        return FlagDictionaryLink.isAgentActive();
    }

    /** JVM エージェント側の統計情報を取得 */
    public static Map<String, String> getAgentStats() {
        return FlagDictionaryLink.getAgentStats();
    }

    // ---- 抹殺・解体側の語彙判定 -------------------------------------------------

    /** 抹殺の対象から外すべき守りか（守り・復活の許可。迂回・待ち時間は含めない） */
    public static boolean protectiveOnTarget(String name) {
        if (name == null || name.isEmpty()) return false;
        Set<Category> c = FlagDictionary.classify(name);
        if (c.contains(Category.BYPASS) || c.contains(Category.TIMER)) return false;
        if (c.contains(Category.GUARD) || c.contains(Category.REVIVE)) return true;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("isoldgod") || lower.equals("oldgod") || lower.equals("isoldone") || lower.equals("oldone")
                || lower.equals("isneccessory") || lower.equals("isnecessary") || lower.equals("necessaryboss");
    }

    /** 残り復活回数（0 にする数）か。待ち時間・間隔は含めない */
    public static boolean livesCounter(String name) {
        if (name == null || name.isEmpty()) return false;
        Set<Category> c = FlagDictionary.classify(name);
        if (c.contains(Category.TIMER)) return false;
        List<String> words = FlagDictionary.words(name);
        if (words.size() == 1 && (words.get(0).equals("lives") || words.get(0).equals("extralives"))) return true;
        return c.contains(Category.REVIVE) && c.contains(Category.COUNT);
    }

    /**
     * チート MOB やプレイヤーのフィールド・NBT・Capability で「無敵・生命維持・防御・HP固定」を表すか。
     * 終焉執行・解体時に false または 0 にリセットすべき対象。
     */
    public static boolean isDefensiveField(String name) {
        if (name == null || name.isEmpty()) return false;
        if (protectiveOnTarget(name)) return true;
        Set<Category> c = FlagDictionary.classify(name);
        if (c.contains(Category.BYPASS) || c.contains(Category.TIMER)) return false;
        if (c.contains(Category.HEALTH_TAMPER) || c.contains(Category.GUARD) || c.contains(Category.REVIVE)) return true;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("helpo") || lower.equals("m_health") || lower.equals("data_value_id") || lower.equals("data_health_id");
    }

    /**
     * 死亡・削除済みを表すフラグ名か（例: isDead, dead, removed, erased, killed, isDeathCrazy, out, setOut）。
     * 終焉執行・解体時に true に設定すべきフラグ。
     */
    public static boolean isDeathStateField(String name) {
        if (name == null || name.isEmpty()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals("isdeathcrazy") || lower.equals("deathcrazy") || lower.equals("out")
                || lower.equals("isout") || lower.equals("setout")) {
            return true;
        }
        Set<Category> c = FlagDictionary.classify(name);
        if (!c.contains(Category.KILL)) return false;
        List<String> words = FlagDictionary.words(name);
        for (String w : words) {
            if (w.equals("dead") || w.equals("death") || w.equals("die") || w.equals("removed")
                    || w.equals("erased") || w.equals("killed") || w.equals("discarded") || w.equals("destroyed")
                    || w.equals("out") || w.equals("banished") || w.equals("eliminated") || w.equals("terminated")) {
                return true;
            }
        }
        return lower.contains("dead") || lower.contains("death") || lower.contains("removed")
                || lower.contains("eras");
    }

    /**
     * 自爆・ゲーム強制終了（Runtime.halt / System.exit）に使われやすい危険なメソッド名か。
     */
    public static boolean isTrapTerminationName(String methodName) {
        if (methodName == null || methodName.isEmpty()) return true;
        String lower = methodName.toLowerCase(Locale.ROOT);
        return lower.equals("end") || lower.equals("endgame") || lower.equals("halt")
                || lower.equals("exit") || lower.equals("crash") || lower.equals("abort")
                || lower.contains("runtimehalt") || lower.contains("systemexit");
    }

    /**
     * オブジェクトやコントローラーを破棄・終了・解体するためのメソッド名か。
     * 自爆トラップ名（end, endGame, halt, exit 等）は必ず除外する。
     */
    public static boolean isTerminationMethod(String methodName) {
        if (methodName == null || methodName.isEmpty() || isTrapTerminationName(methodName)) return false;
        String lower = methodName.toLowerCase(Locale.ROOT);
        if (lower.equals("remove") || lower.equals("kill") || lower.equals("discard")
                || lower.equals("close") || lower.equals("stop") || lower.equals("cleanup")
                || lower.equals("destroy") || lower.equals("dispose") || lower.equals("terminate")
                || lower.equals("shutdown") || lower.equals("finish") || lower.equals("clear")) {
            return true;
        }
        Set<Category> c = FlagDictionary.classify(methodName);
        return c.contains(Category.KILL);
    }

    /**
     * アイテムが不死・身代わり・防御・復活効果を持つか判定する（Totem 互換品、チート防具、無敵チャーム等）。
     * プレイヤー終焉執行時にインベントリ・装備から剥奪すべき対象。
     */
    public static boolean isDefensiveItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.TOTEM_OF_UNDYING)) return true;

        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key != null) {
            String path = key.getPath();
            if (protectiveOnTarget(path) || FlagDictionary.is(path, Category.GUARD) || FlagDictionary.is(path, Category.REVIVE)) return true;
            String fullKey = key.toString().toLowerCase(Locale.ROOT);
            if (fullKey.contains("infinity") || fullKey.contains("immortal") || fullKey.contains("totem")
                    || fullKey.contains("invulnerab") || fullKey.contains("god") || fullKey.contains("ward")
                    || fullKey.contains("halo") || fullKey.contains("sugar") || fullKey.contains("snack")
                    || fullKey.contains("bless")) {
                return true;
            }
        }

        String className = stack.getItem().getClass().getSimpleName();
        if (protectiveOnTarget(className) || FlagDictionary.is(className, Category.GUARD) || FlagDictionary.is(className, Category.REVIVE)) return true;

        if (stack.hasCustomHoverName()) {
            String hover = stack.getHoverName().getString();
            if (protectiveOnTarget(hover) || FlagDictionary.is(hover, Category.GUARD) || FlagDictionary.is(hover, Category.REVIVE)) return true;
        }

        CompoundTag tag = stack.getTag();
        if (tag != null) {
            for (String tagKey : tag.getAllKeys()) {
                if (protectiveOnTarget(tagKey) || FlagDictionary.is(tagKey, Category.GUARD) || FlagDictionary.is(tagKey, Category.REVIVE)) return true;
                if (tag.contains(tagKey, Tag.TAG_STRING)) {
                    String strVal = tag.getString(tagKey);
                    if (protectiveOnTarget(strVal) || FlagDictionary.is(strVal, Category.GUARD) || FlagDictionary.is(strVal, Category.REVIVE)) return true;
                }
            }
        }

        return false;
    }

    /**
     * コントローラー・本体・コア・親管理体を表すフィールド名か。
     */
    public static boolean isCoreOrControllerName(String name) {
        if (name == null || name.isEmpty()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("controller") || lower.contains("core") || lower.contains("parent")
                || lower.contains("master") || lower.contains("manager") || lower.contains("boss")
                || lower.contains("source") || lower.contains("origin") || lower.contains("root")
                || lower.contains("holder") || lower.contains("owner")) {
            return true;
        }
        List<String> words = FlagDictionary.words(name);
        for (String w : words) {
            if (w.equals("controller") || w.equals("core") || w.equals("parent")
                    || w.equals("master") || w.equals("manager") || w.equals("boss")
                    || w.equals("source") || w.equals("origin") || w.equals("root")) {
                return true;
            }
        }
        return false;
    }

    /**
     * エンティティが危険なフラグ（チートMODの即死・消滅・無敵・生命維持・防御貫通フラグ等）を
     * 所有しているか判定する。
     * バニラの一般モンスター（ゾンビ、スケルトン等）や家畜・村人等は false になる。
     */
    public static boolean hasDangerousFlag(@Nullable Entity entity) {
        if (entity == null || DirectRead.isRemoved(entity)) return false;
        if (entity instanceof IEprotected || entity instanceof Player) return false;

        // 1. 直近に eprotect を攻撃してきた攻撃者
        if (AttackLog.isRecentAttacker(entity)) {
            return true;
        }

        // 2. 危険な即死・処刑アイテムを所持している場合
        if (entity instanceof LivingEntity living) {
            if (FlagDictionary.isKillItem(living.getMainHandItem()) || FlagDictionary.isKillItem(living.getOffhandItem())) {
                return true;
            }
        }

        // 3. PersistentData (Forge NBT) の危険フラグキー走査
        try {
            CompoundTag tag = entity.getPersistentData();
            for (String key : tag.getAllKeys()) {
                if (FlagDictionary.harmfulOnProtected(key) || FlagDictionary.is(key, Category.KILL) || FlagDictionary.is(key, Category.BYPASS)
                        || FlagDictionary.is(key, Category.HEALTH_TAMPER) || FlagDictionary.is(key, Category.GUARD) || FlagDictionary.is(key, Category.BANISH)
                        || isDefensiveField(key)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        // 4. クラス名・パッケージ名の照合（既知のチートMOD）
        Class<?> clazz = entity.getClass();
        String className = clazz.getName().toLowerCase(Locale.ROOT);
        if (className.contains("witherzilla") || className.contains("omnimobs") || className.contains("flashfur")
                || className.contains("nosugar") || className.contains("imds") || className.contains("the_last_sword")
                || className.contains("hyperdaimc") || className.contains("shizuku") || className.contains("moreavaritia")
                || className.contains("madness")) {
            return true;
        }

        // 5. 独自フィールド・インターフェースの動的走査（他 MOD のクラスである場合）
        if (CallerAttribution.isForeign(CallerAttribution.modOf(clazz))) {
            for (Class<?> c = clazz; c != null && c != Object.class && CallerAttribution.isForeign(CallerAttribution.modOf(c)); c = c.getSuperclass()) {
                for (Class<?> iface : c.getInterfaces()) {
                    String ifaceName = iface.getSimpleName();
                    if (FlagDictionary.harmfulOnProtected(ifaceName) || FlagDictionary.is(ifaceName, Category.KILL) || FlagDictionary.is(ifaceName, Category.BYPASS)
                            || FlagDictionary.is(ifaceName, Category.GUARD) || FlagDictionary.is(ifaceName, Category.HEALTH_TAMPER)) {
                        return true;
                    }
                }
                for (Field f : c.getDeclaredFields()) {
                    String fname = f.getName();
                    if (FlagDictionary.harmfulOnProtected(fname) || FlagDictionary.is(fname, Category.KILL) || FlagDictionary.is(fname, Category.BYPASS)
                            || FlagDictionary.is(fname, Category.GUARD) || FlagDictionary.is(fname, Category.HEALTH_TAMPER) || isDefensiveField(fname)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
