package com.automatavalkyries.lifeguard;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生死の getter の戻り際に {@code LifeHooks} の呼び出しを足す（いちばん外側で包むので、他のエージェント・ASM の偽装より後に効く）。
 * <ul>
 *   <li>戻り値: {@code LivingEntity} の getHealth / isDeadOrDying / isAlive の各 return の直前。このMOD のクラスは変換しない
 *       （観察用エージェントの改ざん防止 IntegrityGuard が「他のエージェントの書き換え」として戻し、並べ直しと戻しが延々と続くため。
 *       2026-10-03 に実機で起きた。保護対象はこれらの getter を上書きしていない）</li>
 *   <li>呼び出し箇所: 「生死の getter を呼ぶ → 実体を積む → 他の MOD の static な値の差し替え（(F, Object)F / (Z, Object)Z）」の
 *       並び（Fantasy Ending の StaticHealthManagerVisitor が作る形）の直後にも同じ呼び出しを足す</li>
 * </ul>
 * 名前は開発環境の公式名と配布環境の SRG 名の両方を見る。
 */
final class LifeGuardTransformer implements ClassFileTransformer {

    static final String HOOKS = "com/automatavalkyries/lifeguard/hooks/LifeHooks";
    private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    private static final String OWN_PREFIX = "com/automatavalkyries/";

    enum Kind {
        HEALTH("health", "(FLjava/lang/Object;)F", Opcodes.FRETURN),
        DEAD_OR_DYING("deadOrDying", "(ZLjava/lang/Object;)Z", Opcodes.IRETURN),
        ALIVE("alive", "(ZLjava/lang/Object;)Z", Opcodes.IRETURN);

        final String hook;
        final String desc;
        final int returnOpcode;

        Kind(String hook, String desc, int returnOpcode) {
            this.hook = hook;
            this.desc = desc;
            this.returnOpcode = returnOpcode;
        }
    }

    /** 変換したクラス（並べ直しの再変換の対象） */
    final Set<String> transformed = ConcurrentHashMap.newKeySet();
    /** 自分が再変換している最中か（他のエージェントの再変換と見分ける） */
    static final ThreadLocal<Boolean> SELF = ThreadLocal.withInitial(() -> false);

    private final LifeGuardAgent agent;

    LifeGuardTransformer(LifeGuardAgent agent) {
        this.agent = agent;
    }

    static Kind kindOf(String name, String desc) {
        if ("()F".equals(desc) && ("getHealth".equals(name) || "m_21223_".equals(name))) return Kind.HEALTH;
        if ("()Z".equals(desc) && ("isDeadOrDying".equals(name) || "m_21224_".equals(name))) return Kind.DEAD_OR_DYING;
        if ("()Z".equals(desc) && ("isAlive".equals(name) || "m_6084_".equals(name))) return Kind.ALIVE;
        return null;
    }

    static boolean isTarget(String className) {
        return LIVING.equals(className);
    }

    @Override
    public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || !isTarget(className)) return null;
        if (classBeingRedefined != null && !SELF.get() && !inHouseRetransform()) agent.scheduleReorder(className);
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            boolean[] changed = {false};
            boolean wrapReturns = LIVING.equals(className);
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ((access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) return mv;
                    Kind returnKind = (access & Opcodes.ACC_STATIC) == 0 && wrapReturns ? kindOf(name, descriptor) : null;
                    return new HookingMethodVisitor(mv, returnKind, changed);
                }
            }, 0);
            if (!changed[0]) return null;
            transformed.add(className);
            agent.log("transformed " + className.replace('/', '.') + (classBeingRedefined != null ? " (retransform)" : ""));
            return writer.toByteArray();
        } catch (Throwable t) {
            agent.log("failed to transform " + className + ": " + t);
            return null;
        }
    }

    /** return の直前と、他 MOD の値の差し替えの直後に LifeHooks を呼ぶ */
    private static final class HookingMethodVisitor extends MethodVisitor {
        private final Kind returnKind;
        private final boolean[] changed;
        /** 直前に呼んだ生死の getter（呼び出し箇所の差し替えの検出用） */
        private Kind lastReader;
        /** その後に積まれた実体の変数（ALOAD） */
        private int lastLoad = -1;
        private int state; // 0: 何も無い, 1: getter を呼んだ, 2: その後 ALOAD した

        HookingMethodVisitor(MethodVisitor mv, Kind returnKind, boolean[] changed) {
            super(Opcodes.ASM9, mv);
            this.returnKind = returnKind;
            this.changed = changed;
        }

        @Override
        public void visitInsn(int opcode) {
            if (returnKind != null && opcode == returnKind.returnOpcode) {
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, returnKind.hook, returnKind.desc, false);
                changed[0] = true;
            }
            state = 0;
            super.visitInsn(opcode);
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            super.visitVarInsn(opcode, varIndex);
            if (state == 1 && opcode == Opcodes.ALOAD) {
                lastLoad = varIndex;
                state = 2;
            } else {
                state = 0;
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            if (state == 2 && opcode == Opcodes.INVOKESTATIC && lastReader != null && descriptor.equals(lastReader.desc)
                    && isForeignOwner(owner)) {
                // 他 MOD の差し替えの結果を、さらに実際の値へ直す
                super.visitVarInsn(Opcodes.ALOAD, lastLoad);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, lastReader.hook, lastReader.desc, false);
                changed[0] = true;
                state = 0;
                return;
            }
            Kind reader = opcode != Opcodes.INVOKESTATIC ? kindOf(name, descriptor) : null;
            if (reader != null) {
                lastReader = reader;
                state = 1;
            } else {
                state = 0;
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            state = 0;
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }

        @Override
        public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
            state = 0;
            super.visitJumpInsn(opcode, label);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            state = 0;
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitLdcInsn(Object value) {
            state = 0;
            super.visitLdcInsn(value);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            state = 0;
            super.visitIntInsn(opcode, operand);
        }

        private static boolean isForeignOwner(String owner) {
            return !owner.startsWith("java/") && !owner.startsWith("jdk/") && !owner.startsWith("net/minecraft/")
                    && !owner.startsWith("com/mojang/") && !owner.startsWith(OWN_PREFIX) && !owner.equals(HOOKS);
        }
    }

    /**
     * この再変換・再定義を呼んだのがこのMOD（このMOD のほかのエージェント・Eprotect+・MOD 本体）か（2026-10-05）。
     * 呼び出し履歴で、Instrumentation の再変換・再定義のすぐ外側の呼び出し元を見る。このMOD 同士の再変換を「他のエージェントの書き換え」と
     * 数えると、オートマタヴァルキリー protect と LifeGuard が互いに「いちばん外側へ並べ直す → 再変換」を上限まで繰り返していた
     */
    static boolean inHouseRetransform() {
        return StackWalker.getInstance().walk(frames -> {
            boolean afterInstrumentation = false;
            for (java.util.Iterator<StackWalker.StackFrame> it = frames.iterator(); it.hasNext(); ) {
                StackWalker.StackFrame f = it.next();
                String c = f.getClassName(), m = f.getMethodName();
                if (c.equals("sun.instrument.InstrumentationImpl") && (m.startsWith("retransformClasses") || m.startsWith("redefineClasses"))) {
                    afterInstrumentation = true;
                    continue;
                }
                if (afterInstrumentation) return c.startsWith("com.automatavalkyries.");
            }
            return false;
        });
    }
}
