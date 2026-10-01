package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.CallerAttribution;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tick の入口で止められたときの犯人探し（報告のみ。他 MOD の処理には手を加えない）。
 * <p>
 * Eprotect がワールドの代わりに本体の Tick を回しても進まないとき、その入口に処理を差し込んで打ち切れる他 MOD の Mixin を、
 * Mixin のクラスファイルの注釈（{@code @Inject(cancellable = true)} / {@code @WrapMethod} / {@code @WrapOperation} / {@code @Redirect} /
 * {@code @Overwrite}）から割り出して攻撃の記録に候補として残す。打ち切れる作りになっているだけで、実際に止めた確証ではない。
 * 互換 Mixin を書く手がかり用。
 * </p>
 */
public final class TickEntranceHooks {

    /** どこで止められていたか */
    public enum Stage {
        /** ワールドから本体の Tick への入口（Level.guardEntityTick / ServerLevel.tickNonPassenger）。tickCount も進まない */
        ENTRANCE("entrance"),
        /** tickCount は進んだが、本体の Tick（エンティティのクラスの tick 等）の中身が走らなかった */
        ENTITY_TICK("entity_tick");

        final String id;

        Stage(String id) {
            this.id = id;
        }
    }

    /** 候補の 1 件 */
    public record Hook(String mod, String mixin, String target, String handler) {}

    private static final Set<String> ENTRANCE_METHODS = Set.of("guardEntityTick", "tickNonPassenger", "tickPassenger");
    private static final Set<String> ENTITY_TICK_METHODS = Set.of(
            "tick", "baseTick", "aiStep", "serverAiStep", "customServerAiStep", "rideTick", "inactiveTick");

    /** 打ち切れる注釈（値は「cancellable = true が要るか」） */
    private static final Map<String, Boolean> STOPPING_ANNOTATIONS = Map.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;", true,
            "Lorg/spongepowered/asm/mixin/injection/Redirect;", false,
            "Lorg/spongepowered/asm/mixin/Overwrite;", false,
            "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;", false,
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", false,
            "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;", false,
            "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;", false);

    /** Mixin のクラス名 → そのクラスの打ち切れる処理（対象メソッド名 → 処理名）。読めなければ空 */
    private static final Map<String, List<String[]>> PARSED = new ConcurrentHashMap<>();

    private TickEntranceHooks() {}

    /**
     * 止められていた場所に差し込まれた、打ち切れる他 MOD の処理を候補としてログと攻撃の記録に出す。
     * Tick 停止 1 回につき 1 度だけ呼ぶこと。
     */
    public static void report(Entity owner, Stage stage) {
        List<Hook> hooks = candidates(owner, stage);
        String op = "frozen_tick:" + stage.id;
        if (hooks.isEmpty()) {
            AutomataValkyries.LOGGER.warn("[eprotect] The tick of {} is blocked at the {}, but no foreign hook there could stop it", owner, stage.id);
            AttackLog.detected(owner, op, CallerAttribution.UNKNOWN, "(no candidate hook)");
            return;
        }
        AutomataValkyries.LOGGER.warn("[eprotect] The tick of {} is blocked at the {}; hooks that can stop it ({}):", owner, stage.id, hooks.size());
        for (Hook hook : hooks) {
            AutomataValkyries.LOGGER.warn("[eprotect]   {} {} -> {} ({})", hook.mod(), hook.mixin(), hook.target(), hook.handler());
            AttackLog.detected(owner, op, hook.mod(), hook.mixin() + "." + hook.handler() + " -> " + hook.target());
        }
    }

    /** 止められていた場所に差し込まれた、打ち切れる他 MOD の処理 */
    public static List<Hook> candidates(Entity owner, Stage stage) {
        Set<String> names = stage == Stage.ENTRANCE ? ENTRANCE_METHODS : ENTITY_TICK_METHODS;
        List<Hook> hooks = new ArrayList<>();
        for (Class<?> type : classes(owner, stage)) {
            for (String mixin : mixinsOn(type)) {
                for (String[] stopper : parse(mixin)) {
                    if (!names.contains(stopper[0])) continue;
                    hooks.add(new Hook(CallerAttribution.modOfClassName(mixin), mixin, type.getSimpleName() + "." + stopper[0], stopper[1]));
                }
            }
        }
        return hooks;
    }

    private static List<Class<?>> classes(Entity owner, Stage stage) {
        List<Class<?>> classes = new ArrayList<>();
        if (stage == Stage.ENTRANCE) {
            classes.add(Level.class);
            classes.add(ServerLevel.class);
            return classes;
        }
        for (Class<?> type = owner.getClass(); type != null && Entity.class.isAssignableFrom(type); type = type.getSuperclass()) {
            classes.add(type);
        }
        return classes;
    }

    /** そのクラスに処理を合成した他 MOD の Mixin（合成された処理に付く {@link MixinMerged} から引く） */
    private static Set<String> mixinsOn(Class<?> type) {
        Set<String> mixins = new LinkedHashSet<>();
        for (Method method : type.getDeclaredMethods()) {
            MixinMerged merged = method.getAnnotation(MixinMerged.class);
            if (merged != null && !merged.mixin().startsWith("com.automatavalkyries.")) mixins.add(merged.mixin());
        }
        return mixins;
    }

    private static List<String[]> parse(String mixin) {
        return PARSED.computeIfAbsent(mixin, TickEntranceHooks::readStoppers);
    }

    /** Mixin のクラスファイルを読み、打ち切れる注釈の付いた処理を（対象メソッド名, 処理名）で返す */
    private static List<String[]> readStoppers(String mixin) {
        List<String[]> stoppers = new ArrayList<>();
        String path = mixin.replace('.', '/') + ".class";
        try (InputStream in = open(path)) {
            if (in == null) return stoppers;
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            for (MethodNode method : node.methods) {
                for (List<AnnotationNode> list : annotationLists(method)) {
                    if (list == null) continue;
                    for (AnnotationNode annotation : list) {
                        Boolean needsCancellable = STOPPING_ANNOTATIONS.get(annotation.desc);
                        if (needsCancellable == null) continue;
                        if (needsCancellable && !Boolean.TRUE.equals(value(annotation, "cancellable"))) continue;
                        List<String> targets = targetNames(annotation, method);
                        for (String target : targets) stoppers.add(new String[]{target, method.name});
                    }
                }
            }
        } catch (Exception e) {
            AutomataValkyries.LOGGER.debug("[eprotect] Could not read mixin {}", mixin, e);
        }
        return stoppers;
    }

    @SuppressWarnings("unchecked")
    private static List<AnnotationNode>[] annotationLists(MethodNode method) {
        return new List[]{method.visibleAnnotations, method.invisibleAnnotations};
    }

    /** 注釈の対象メソッド名（{@code @Overwrite} は処理名そのもの）。記述子や持ち主のクラスは落とす */
    private static List<String> targetNames(AnnotationNode annotation, MethodNode method) {
        List<String> names = new ArrayList<>();
        if (annotation.desc.endsWith("/Overwrite;")) {
            names.add(method.name);
            return names;
        }
        Object raw = value(annotation, "method");
        if (raw instanceof List<?> list) {
            for (Object entry : list) if (entry instanceof String s) names.add(simpleName(s));
        } else if (raw instanceof String s) {
            names.add(simpleName(s));
        }
        return names;
    }

    /** "Lnet/minecraft/world/entity/Entity;tick()V" / "tick()V" / "tick" → "tick" */
    private static String simpleName(String reference) {
        String name = reference;
        int paren = name.indexOf('(');
        if (paren >= 0) name = name.substring(0, paren);
        int semicolon = name.lastIndexOf(';');
        if (semicolon >= 0) name = name.substring(semicolon + 1);
        int dot = name.lastIndexOf('.');
        if (dot >= 0) name = name.substring(dot + 1);
        return name;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static InputStream open(String path) {
        InputStream in = TickEntranceHooks.class.getClassLoader().getResourceAsStream(path);
        if (in == null && Thread.currentThread().getContextClassLoader() != null) {
            in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        }
        return in;
    }
}
