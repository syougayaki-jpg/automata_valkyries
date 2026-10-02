package com.automatavalkyries.protect.flag;

import com.automatavalkyries.protect.attack.CallerAttribution;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 同期データの鍵（{@code SynchedEntityData.defineId}）がどこで作られ、どの static フィールドに入ったかを覚え、名前を引けるようにする（準汎用）。
 * <p>
 * {@link StateScanner} はエンティティのクラス（と、そこに足されたフィールド）に置かれた鍵しか見つけられない。他 MOD が鍵を
 * 自分の補助クラスに置くと（Fantasy Ending の {@code EntityASMUtil.FE_GET_HEALTH_DATA}: getHealth の結果を下げる差分。2026-10-03）
 * 名前が分からず、辞書で見分けられなかった。
 * </p>
 * 作られたときの呼び出し元から探す: 呼び出し元のクラス自身の static フィールド、呼び出し元が Mixin の差し込み（{@link MixinMerged}）なら
 * その Mixin のクラスが書き込む static フィールド。
 */
public final class SynchedKeyOrigins {

    /** 作られた鍵 1 つ */
    private record Origin(Class<? extends Entity> owner, EntityDataAccessor<?> accessor, Class<?> caller, String method) {}

    private static final List<Origin> ORIGINS = new CopyOnWriteArrayList<>();
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private SynchedKeyOrigins() {}

    /** {@code defineId} の戻り際に呼ぶ（Mixin） */
    public static void record(Class<? extends Entity> owner, EntityDataAccessor<?> accessor) {
        if (owner == null || accessor == null) return;
        try {
            Optional<StackWalker.StackFrame> frame = WALKER.walk(s -> s
                    .filter(f -> f.getDeclaringClass() != SynchedEntityData.class && f.getDeclaringClass() != SynchedKeyOrigins.class)
                    .findFirst());
            frame.ifPresent(f -> ORIGINS.add(new Origin(owner, accessor, f.getDeclaringClass(), f.getMethodName())));
        } catch (Throwable ignored) {}
    }

    /** その型に当てはまり、他 MOD が作った鍵（名前が引けたものだけ） */
    static List<StateScanner.DataKey> keysFor(Class<?> entityType) {
        List<StateScanner.DataKey> keys = new ArrayList<>();
        for (Origin origin : ORIGINS) {
            if (!origin.owner().isAssignableFrom(entityType)) continue;
            StateScanner.DataKey key = resolve(origin);
            if (key != null && !CallerAttribution.SELF.equals(key.mod())) keys.add(key);
        }
        return keys;
    }

    private static StateScanner.DataKey resolve(Origin origin) {
        // 1. 呼び出し元のクラス自身（他 MOD のクラスが自分の static フィールドに入れる、ふつうの書き方）
        if (CallerAttribution.isForeign(CallerAttribution.modOf(origin.caller()))) {
            StateScanner.DataKey key = findIn(origin.caller(), origin.accessor());
            if (key != null) return key;
        }
        // 2. 呼び出し元が Mixin の差し込み: その Mixin のクラスが書き込む static フィールドを探す
        String mixin = mixinOf(origin.caller(), origin.method());
        if (mixin == null) return null;
        for (String[] target : putStaticTargets(origin.caller().getClassLoader(), mixin)) {
            try {
                Class<?> owner = Class.forName(target[0].replace('/', '.'), false, origin.caller().getClassLoader());
                StateScanner.DataKey key = findIn(owner, origin.accessor());
                if (key != null) return key;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static StateScanner.DataKey findIn(Class<?> owner, EntityDataAccessor<?> accessor) {
        try {
            for (Field field : owner.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != EntityDataAccessor.class) continue;
                field.setAccessible(true);
                if (field.get(null) == accessor) return new StateScanner.DataKey(accessor, field.getName(), CallerAttribution.modOf(owner));
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String mixinOf(Class<?> caller, String methodName) {
        try {
            for (Method m : caller.getDeclaredMethods()) {
                if (!m.getName().equals(methodName)) continue;
                MixinMerged merged = m.getAnnotation(MixinMerged.class);
                if (merged != null) return merged.mixin();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Mixin のクラスファイルを読み、EntityDataAccessor 型の static フィールドへの書き込み先（所有クラス, 名前）を集める */
    private static List<String[]> putStaticTargets(ClassLoader loader, String mixinClass) {
        Set<String> seen = new LinkedHashSet<>();
        List<String[]> out = new ArrayList<>();
        String resource = mixinClass.replace('.', '/') + ".class";
        try (InputStream in = loader != null ? loader.getResourceAsStream(resource) : ClassLoader.getSystemResourceAsStream(resource)) {
            if (in == null) return out;
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String field, String fieldDescriptor) {
                            if (opcode == Opcodes.PUTSTATIC && "Lnet/minecraft/network/syncher/EntityDataAccessor;".equals(fieldDescriptor)
                                    && seen.add(owner + "." + field)) {
                                out.add(new String[]{owner, field});
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (Throwable ignored) {}
        return out;
    }
}
