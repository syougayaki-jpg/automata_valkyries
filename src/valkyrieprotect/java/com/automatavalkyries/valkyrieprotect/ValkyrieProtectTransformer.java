package com.automatavalkyries.valkyrieprotect;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * vanilla の消去・即死の入口の先頭に「ValkyrieHooks が断ったら return」を差し込む（いちばん外側の変換なので、Mixin や他のエージェントが
 * 差し込んだ処理より前に効く）。
 * <ul>
 *   <li>{@code Entity}: remove(RemovalReason) / setRemoved(RemovalReason) / kill()</li>
 *   <li>{@code LivingEntity}: remove(RemovalReason) / kill() / die(DamageSource) / setHealth(float)</li>
 * </ul>
 * 名前は開発環境の公式名と配布環境の SRG 名の両方を見る。他 MOD のクラスも、このMOD のクラスも書き換えない。
 */
final class ValkyrieProtectTransformer implements ClassFileTransformer {

    static final String HOOKS = "com/automatavalkyries/valkyrieprotect/hooks/ValkyrieHooks";
    static final String ENTITY = "net/minecraft/world/entity/Entity";
    static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    private static final String REASON = "(Lnet/minecraft/world/entity/Entity$RemovalReason;)V";

    final Set<String> transformed = ConcurrentHashMap.newKeySet();
    static final ThreadLocal<Boolean> SELF = ThreadLocal.withInitial(() -> false);

    private final ValkyrieProtectAgent agent;

    ValkyrieProtectTransformer(ValkyrieProtectAgent agent) {
        this.agent = agent;
    }

    static boolean isTarget(String className) {
        return ENTITY.equals(className) || LIVING.equals(className);
    }

    /** 差し込む入口の名前（hook の op）。対象でなければ null */
    static String opOf(String owner, String name, String desc) {
        if (REASON.equals(desc) && ("remove".equals(name) || "m_142687_".equals(name))) return "remove";
        if (ENTITY.equals(owner) && REASON.equals(desc) && ("setRemoved".equals(name) || "m_142467_".equals(name))) return "setRemoved";
        if ("()V".equals(desc) && ("kill".equals(name) || "m_6074_".equals(name))) return "kill";
        if (LIVING.equals(owner) && "(Lnet/minecraft/world/damagesource/DamageSource;)V".equals(desc) && ("die".equals(name) || "m_6667_".equals(name))) return "die";
        if (LIVING.equals(owner) && "(F)V".equals(desc) && ("setHealth".equals(name) || "m_21153_".equals(name))) return "setHealth";
        return null;
    }

    @Override
    public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || !isTarget(className)) return null;
        if (classBeingRedefined != null && !SELF.get()) agent.scheduleReorder(className);
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            int[] count = {0};
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ((access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_STATIC)) != 0) return mv;
                    String op = opOf(className, name, descriptor);
                    if (op == null) return mv;
                    count[0]++;
                    Object[] locals = switch (op) {
                        case "setHealth" -> new Object[]{className, Opcodes.FLOAT};
                        case "die" -> new Object[]{className, "net/minecraft/world/damagesource/DamageSource"};
                        case "kill" -> new Object[]{className};
                        default -> new Object[]{className, "net/minecraft/world/entity/Entity$RemovalReason"};
                    };
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            // if (ValkyrieHooks.refuse(this, op, arg)) return;
                            Label go = new Label();
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            if (op.equals("setHealth")) {
                                super.visitVarInsn(Opcodes.FLOAD, 1);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "refuseHealth", "(Ljava/lang/Object;F)Z", false);
                            } else {
                                super.visitLdcInsn(op);
                                if (op.equals("kill")) super.visitInsn(Opcodes.ACONST_NULL);
                                else super.visitVarInsn(Opcodes.ALOAD, 1);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "refuse", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Z", false);
                            }
                            super.visitJumpInsn(Opcodes.IFEQ, go);
                            super.visitInsn(Opcodes.RETURN);
                            super.visitLabel(go);
                            // 分岐先には明示のフレームが要る（入口の時点の局所変数 = this と引数）。元のコードの先頭にフレームがあっても
                            // 同じ位置に二つ並ばないよう、NOP を一つ挟む
                            super.visitFrame(Opcodes.F_NEW, locals.length, locals, 0, new Object[0]);
                            super.visitInsn(Opcodes.NOP);
                        }
                    };
                }
            }, ClassReader.EXPAND_FRAMES);
            if (count[0] == 0) return null;
            transformed.add(className);
            agent.log("guarded " + count[0] + " entry point(s) in " + className.replace('/', '.') + (classBeingRedefined != null ? " (retransform)" : ""));
            return writer.toByteArray();
        } catch (Throwable t) {
            agent.log("failed to transform " + className + ": " + t);
            return null;
        }
    }
}
