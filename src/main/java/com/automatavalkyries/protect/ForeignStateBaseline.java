package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.StateScanner;
import net.minecraft.world.entity.LivingEntity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 正常なときの外部状態（他 MOD が保護対象に付けた値）を控えておき、死亡の兆候が出た Tick にだけ、
 * <b>辞書が知らない名前</b>のフィールドを控えた正常値へ戻す（準汎用。名前に頼らない）。
 *
 * <p>2026-10-21 ユーザー指定「終末機構を汎用処理の限界付近まで強化したので Eprotect の強化も」。
 * 抹殺側（Antigravity）が辞書に無い名前で生命維持フラグを奪う・残機を 0 にするといった汎用キルへ振り切ったので、
 * その対になる。既知名（KILL・CONTROL・HEALTH_TAMPER・BYPASS…）は {@link ForeignStateCleaner} が辞書で戻すので、
 * ここは <b>辞書に無い名前</b>だけを受け持ち、二重処理を避ける。</p>
 *
 * <p>安全のための絞り込み:</p>
 * <ul>
 *   <li>控えるのは読み取り専用の {@link StateScanner#observe}（値を書かない・他 MOD の getter も呼ばない）。
 *       コレクションを消す {@link StateScanner#apply} は控えには使わない</li>
 *   <li>控えるのは真偽・数値のスカラーだけ。戻すのは「死亡の兆候」に合う変化だけ:
 *       真偽が正常 false → 今 true（死亡の印の立ち上がり）、数値が正常 > 0 → 今 0 以下（残機・独自 HP の 0 化）。
 *       増えた値・正のまま動く値（タイマー・クールダウン・座標）は触らない</li>
 *   <li>戻すのは {@link Eprotect#repair} が死亡の兆候を見つけた Tick だけ。普段は控え直すだけ</li>
 * </ul>
 */
public final class ForeignStateBaseline {

    private static final EnumSet<StateScanner.Source> SOURCES = EnumSet.allOf(StateScanner.Source.class);

    private final LivingEntity owner;
    /** 正常なときの値（名前 → 真偽か数値）。名前が重なって値が食い違うものは {@link #ambiguous} に退ける */
    private final Map<String, Object> healthy = new HashMap<>();
    private final Set<String> ambiguous = new HashSet<>();
    private boolean captured = false;

    public ForeignStateBaseline(LivingEntity owner) {
        this.owner = owner;
    }

    /** 控えているか（一度でも正常時に控えたか） */
    public boolean hasBaseline() {
        return captured;
    }

    /**
     * 正常なときに外部のスカラー状態を丸ごと控える（読み取り専用）。
     * 既知名は {@link ForeignStateCleaner} が担当するので控えない（戻す対象と一致させ、無駄も減らす）。
     */
    public void rememberHealthy() {
        if (owner.level().isClientSide) return;
        Map<String, Object> snap = new HashMap<>();
        Set<String> conflict = new HashSet<>();
        try {
            StateScanner.observe(owner, obs -> {
                String name = obs.name();
                Object value = obs.value();
                if (!(value instanceof Boolean || value instanceof Number)) return;
                if (!isUnknown(name)) return; // 辞書が知っている名前は ForeignStateCleaner に任せる
                Object prev = snap.put(name, value);
                if (prev != null && !prev.equals(value)) conflict.add(name); // 別の場所の同名で値が食い違う
            });
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[eprotect] Could not snapshot foreign state of {}: {}", owner, t.toString());
            return;
        }
        healthy.clear();
        healthy.putAll(snap);
        ambiguous.clear();
        ambiguous.addAll(conflict);
        captured = true;
    }

    /**
     * 死亡の兆候が出たとき、辞書に無い名前のフィールドが死亡の兆候に合う変化をしていたら、控えた正常値へ戻す。
     * 戻す（書き込む）経路だけ {@link StateScanner#apply} を使う。{@code relevant} は控えたスカラー名だけ true なので、
     * 名前が合致するコレクション・マップを誤って消す恐れは実質無い。
     *
     * @return 戻した件数
     */
    public int restoreAtDeathAttempt() {
        if (!captured || owner.level().isClientSide || healthy.isEmpty()) return 0;
        try {
            List<StateScanner.Change> changes = StateScanner.apply(owner, new RestorePolicy(), SOURCES);
            for (StateScanner.Change c : changes) AttackLog.repaired(owner, "foreign_baseline:" + c);
            return changes.size();
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[eprotect] Could not restore foreign state of {}: {}", owner, t.toString());
            return 0;
        }
    }

    /** 辞書が知らない（どの分類にも当たらない）名前か。除外した名前も対象にしない */
    private static boolean isUnknown(String name) {
        return FlagDictionary.classify(name).isEmpty() && !FlagDictionary.ignored(name);
    }

    private static double toNumber(Object value) {
        return value instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    /** 戻す判定（書き込む値を返す。触らないなら null） */
    private final class RestorePolicy implements StateScanner.Policy {
        @Override
        public boolean relevant(String name) {
            return healthy.containsKey(name) && !ambiguous.contains(name) && isUnknown(name);
        }

        @Override
        public Object decide(String name, Class<?> type, Object current) {
            Object base = healthy.get(name);
            if (base == null || current == null || ambiguous.contains(name)) return null;
            if (type == boolean.class || type == Boolean.class) {
                // 正常 false → 今 true（死亡の印が立った）だけ戻す。切り替わる正規のフラグは触らない
                return Boolean.FALSE.equals(base) && Boolean.TRUE.equals(current) ? Boolean.FALSE : null;
            }
            // 数値: 正常 > 0 → 今 0 以下（残機・独自 HP を 0 にされた）だけ戻す
            double b = toNumber(base);
            double c = toNumber(current);
            if (!Double.isNaN(b) && !Double.isNaN(c) && b > 0.0D && c <= 0.0D) return base;
            return null;
        }

        @Override
        public boolean removeKey(String key) {
            return false; // 永続データのキーは消さない（未知名の消去は ForeignStateCleaner の担当でもない）
        }
    }
}
