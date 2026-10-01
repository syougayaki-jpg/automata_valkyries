package com.automatavalkyries.protect.attack;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 保護対象への操作を「どの MOD のコードが呼んだか」をスタックから割り出す。
 * <ul>
 *   <li>直接の呼び出し元（ブラックリストの判定に使う）: 自分の仕組み・Mixin の仕組み・守っているエンティティ自身のクラス・
 *       Java / プラットフォームのフレームを飛ばした最初のフレーム。自分（このMOD）なら決して拒否しない</li>
 *   <li>最寄りの他 MOD（記録に使う）: minecraft / forge / このMOD 以外で最初に現れる MOD</li>
 * </ul>
 * 他 MOD が vanilla のクラスへ Mixin で差し込んだメソッド（{@link MixinMerged}）は、その Mixin の MOD のフレームとして扱う。
 * MOD の jar のモジュール名は最初の MOD ID なので、クラスのモジュール名がそのまま MOD ID になる。
 */
public final class CallerAttribution {

    public static final String SELF = AutomataValkyries.MODID;
    public static final String MINECRAFT = "minecraft";
    public static final String FORGE = "forge";
    /** モジュールを持たないクラス（Unsafe・エージェントで定義されたもの等） */
    public static final String UNKNOWN = "unknown";
    /** ブート層・サービス層（Java・modlauncher・Mixin・イベントバス等） */
    public static final String PLATFORM = "platform";

    private static final int MAX_FRAMES = 48;
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final AtomicLong WALKS = new AtomicLong();

    private static volatile String testPrefix;
    private static volatile String testMod;

    /**
     * @param callerMod    直接の呼び出し元の MOD（見つからなければ {@link #UNKNOWN}）
     * @param callerFrame  その呼び出し箇所（クラス.メソッド:行）
     * @param foreignMod   最寄りの他 MOD（無ければ null）
     * @param otherForeign 直接の呼び出し元とは別の、最寄りの他 MOD（互換 Mixin で「経由した MOD」と「攻撃した MOD」を分ける用）
     */
    public record Attribution(String callerMod, String callerFrame, @Nullable String foreignMod, @Nullable String otherForeign,
                              @Nullable Class<?> callerClass, @Nullable Class<?> foreignClass) {
        public boolean callerIsForeign() {
            return isForeign(callerMod);
        }
    }

    private CallerAttribution() {}

    private static final ClassValue<String> MOD_OF = new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> type) {
            return resolveMod(type);
        }
    };

    /** クラス → そのクラスに差し込まれた他 MOD のメソッド（名前 + 記述子 → Mixin のクラス名） */
    private static final ClassValue<Map<String, String>> MERGED = new ClassValue<>() {
        @Override
        protected Map<String, String> computeValue(Class<?> type) {
            Map<String, String> map = new HashMap<>();
            try {
                for (Method method : type.getDeclaredMethods()) {
                    MixinMerged merged = method.getAnnotation(MixinMerged.class);
                    if (merged == null) continue;
                    String descriptor = MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString();
                    map.put(method.getName() + descriptor, merged.mixin());
                }
            } catch (Throwable ignored) {
                // 専用サーバーでクライアント専用の型が引数にあると NoClassDefFoundError になる。その場合は差し込み無しとして扱う
            }
            return map;
        }
    };

    private static volatile Map<String, String> packageToModule;

    /** クラスが属する MOD（キャッシュ付き） */
    public static String modOf(Class<?> type) {
        return MOD_OF.get(type);
    }

    public static boolean isForeign(@Nullable String mod) {
        return mod != null && !mod.equals(SELF) && !mod.equals(MINECRAFT) && !mod.equals(FORGE)
                && !mod.equals(UNKNOWN) && !mod.equals(PLATFORM) && !mod.startsWith("lib:");
    }

    /** ブラックリストの判定用: 直接の呼び出し元の MOD だけを求める（最寄りの他 MOD は探さない） */
    public static String immediateCallerMod(@Nullable Entity target) {
        return walk(target, true).callerMod();
    }

    /** 記録用: 直接の呼び出し元と最寄りの他 MOD を求める */
    public static Attribution attribute(@Nullable Entity target) {
        return walk(target, false);
    }

    /**
     * 呼び出し経路で最初に現れる他 MOD（vanilla のクラスへ差し込まれたメソッドは数えない）。無ければ null。
     * 守護の護符で使う: vanilla の経路の途中に性能系 MOD の差し込みがあっても、それを攻撃とみなさないため。
     */
    @Nullable
    public static String foreignClassMod(@Nullable Entity target) {
        WALKS.incrementAndGet();
        Class<?> targetClass = target != null ? target.getClass() : null;
        return WALKER.walk(frames -> {
            Iterator<StackWalker.StackFrame> it = frames.limit(64).iterator();
            while (it.hasNext()) {
                StackWalker.StackFrame frame = it.next();
                Class<?> type = frame.getDeclaringClass();
                String method = frame.getMethodName();
                if (isOwnPlumbing(type, method)) continue;
                if (method.indexOf('$') >= 0 && mergedMod(type, method, frame.getDescriptor()) != null) continue;
                if (targetClass != null && type.isAssignableFrom(targetClass)) continue;
                String mod = modOf(type);
                if (isForeign(mod)) return mod;
            }
            return null;
        });
    }

    public static long walkCount() {
        return WALKS.get();
    }

    /** GameTest 用: このパッケージのクラスを架空の MOD として扱う */
    public static void overrideForTest(@Nullable String packagePrefix, @Nullable String fakeModId) {
        testPrefix = packagePrefix;
        testMod = fakeModId;
    }

    private static Attribution walk(@Nullable Entity target, boolean callerOnly) {
        WALKS.incrementAndGet();
        Class<?> targetClass = target != null ? target.getClass() : null;
        return WALKER.walk(frames -> {
            String callerMod = null;
            String callerFrame = "?";
            String foreign = null;
            String other = null;
            Class<?> callerClass = null;
            Class<?> foreignClass = null;
            Iterator<StackWalker.StackFrame> it = frames.limit(MAX_FRAMES).iterator();
            while (it.hasNext()) {
                StackWalker.StackFrame frame = it.next();
                Class<?> type = frame.getDeclaringClass();
                String method = frame.getMethodName();
                if (isOwnPlumbing(type, method)) continue;

                String mod = null;
                boolean merged = false;
                if (method.indexOf('$') >= 0) {
                    mod = mergedMod(type, method, frame.getDescriptor());
                    merged = mod != null;
                }
                if (mod == null) {
                    // 守っているエンティティ自身のクラス（Entity / LivingEntity / 兵姫のクラス等）は、そこを呼んだ側を探すため飛ばす
                    if (targetClass != null && type.isAssignableFrom(targetClass)) continue;
                    mod = modOf(type);
                    if (mod.equals(PLATFORM) || type.getName().contains("ASMEventHandler")) continue;
                }

                if (callerMod == null) {
                    callerMod = mod;
                    callerFrame = type.getName() + "." + method + ":" + frame.getLineNumber();
                    callerClass = merged ? null : type;
                    if (callerOnly) break;
                    if (isForeign(mod)) {
                        foreign = mod;
                        foreignClass = callerClass;
                    }
                    continue;
                }
                // 直接の呼び出し元より先では、vanilla へ差し込まれたメソッドは数えない（Hyperlink のように全エンティティの Tick に
                // 差し込む MOD が、どの攻撃の経路にも現れて攻撃者と取り違えられるため）
                if (merged) continue;
                if (isForeign(mod)) {
                    if (foreign == null) {
                        foreign = mod;
                        foreignClass = type;
                    }
                    if (other == null && !mod.equals(callerMod)) other = mod;
                    if (foreign != null && other != null) break;
                }
            }
            return new Attribution(callerMod != null ? callerMod : UNKNOWN, callerFrame, foreign, other, callerClass, foreignClass);
        });
    }

    /**
     * 記録・判定の仕組みそのもの（このパッケージ・保護の判定・互換判定・このMOD の Mixin の差し込み・Mixin の実行時）。
     * 保護の判定（{@link com.automatavalkyries.protect.Eprotect}）を飛ばしても、自分の修復が動かした場合はその先の
     * 呼び出し元（このMOD の修復処理か vanilla の Tick）になるので、自分の操作がブラックリストで止まることはない
     */
    private static boolean isOwnPlumbing(Class<?> type, String method) {
        String name = type.getName();
        return name.startsWith("com.automatavalkyries.protect.attack.")
                || name.equals("com.automatavalkyries.protect.Eprotect")
                || name.equals("com.automatavalkyries.protect.EprotectCompat")
                || name.equals("com.automatavalkyries.protect.EprotectForgeEvents")
                || name.equals("com.automatavalkyries.protect.PlayerWard")
                || method.contains("automata_valkyries$")
                || name.startsWith("org.spongepowered.");
    }

    /** vanilla 等のクラスに差し込まれた他 MOD のメソッドなら、その MOD を返す（このMOD の差し込みは {@link #isOwnPlumbing} で除外済み） */
    @Nullable
    private static String mergedMod(Class<?> type, String method, String descriptor) {
        String mixin = MERGED.get(type).get(method + descriptor);
        if (mixin == null) return null;
        return modOfClassName(mixin);
    }

    /** クラスを読み込まずに、クラス名（パッケージ）から MOD を引く（Mixin のクラスや、入っていないかもしれない MOD のクラス用） */
    public static String modOfClassName(String className) {
        if (testPrefix != null && className.startsWith(testPrefix)) return testMod;
        if (className.startsWith("com.automatavalkyries.")) return SELF;
        int dot = className.lastIndexOf('.');
        String module = dot > 0 ? packageModules().get(className.substring(0, dot)) : null;
        return module != null ? moduleToMod(module) : UNKNOWN;
    }

    private static String resolveMod(Class<?> type) {
        String name = type.getName();
        if (testPrefix != null && name.startsWith(testPrefix)) return testMod;
        if (name.startsWith("com.automatavalkyries.")) return SELF;
        if (name.startsWith("net.minecraft.") || name.startsWith("com.mojang.")) return MINECRAFT;
        if (name.startsWith("net.minecraftforge.") && !name.startsWith("net.minecraftforge.eventbus.")) return FORGE;

        Module module = type.getModule();
        if (!module.isNamed()) return UNKNOWN;
        if (module.getLayer() != Entity.class.getModule().getLayer()) return PLATFORM;
        return moduleToMod(module.getName());
    }

    private static String moduleToMod(String moduleName) {
        ModList mods = ModList.get();
        return mods != null && mods.isLoaded(moduleName) ? moduleName : "lib:" + moduleName;
    }

    /** ゲーム層の各モジュールのパッケージ → モジュール名（Mixin のクラスは読み込めないので、パッケージから MOD を引く） */
    private static Map<String, String> packageModules() {
        Map<String, String> map = packageToModule;
        if (map != null) return map;
        map = new HashMap<>();
        ModuleLayer layer = Entity.class.getModule().getLayer();
        if (layer != null) {
            for (Module module : layer.modules()) {
                for (String pkg : module.getPackages()) map.put(pkg, module.getName());
            }
        }
        packageToModule = map;
        return map;
    }
}
