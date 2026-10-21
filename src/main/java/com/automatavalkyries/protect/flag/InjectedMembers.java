package com.automatavalkyries.protect.flag;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.CallerAttribution;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 他 MOD が Mixin・ASM・Java エージェント（クラスの読み込み時）で、既存のクラスに後から足したフィールドを見つける。
 * <p>
 * 実行中のクラスに宣言されているフィールドと、jar にある元のクラスファイル（書き換え前）のフィールドを比べ、
 * 元に無いものを「足されたもの」とする。名前の付け方（開発環境の公式名・本番の SRG 名）に左右されない。
 * 足した MOD は、そのクラスに適用された Mixin のうち同じ名前のフィールドを持つものから求める（分からなければ unknown）。
 * </p>
 */
public final class InjectedMembers {

    /** 足されたフィールドと、足した MOD */
    public record InjectedField(Field field, String mod) {}

    private static final ClassValue<List<InjectedField>> FIELDS = new ClassValue<>() {
        @Override
        protected List<InjectedField> computeValue(Class<?> type) {
            return findInjectedFields(type);
        }
    };

    private InjectedMembers() {}

    /** そのクラス自身に足されたフィールド（親クラスは含まない。元のクラスファイルが読めなければ空） */
    public static List<InjectedField> fields(Class<?> type) {
        return FIELDS.get(type);
    }

    private static List<InjectedField> findInjectedFields(Class<?> type) {
        if (type.isArray() || type.isPrimitive() || type.getName().startsWith("java.")) return List.of();
        Set<String> original = originalFieldNames(type);
        if (original == null) return List.of();
        List<InjectedField> result = new ArrayList<>();
        for (Field field : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(type)) {
            if (field.isSynthetic() || original.contains(field.getName())) continue;
            result.add(new InjectedField(field, injectingMod(type, field.getName())));
        }
        return List.copyOf(result);
    }

    /** jar にある元のクラスファイルのフィールド名（読めなければ null） */
    @Nullable
    private static Set<String> originalFieldNames(Class<?> type) {
        String rawClassName = type.getName();
        int slashIdx = rawClassName.indexOf('/');
        if (slashIdx > 0) {
            rawClassName = rawClassName.substring(0, slashIdx);
        }
        String resource = rawClassName.replace('.', '/') + ".class";
        try (InputStream in = open(type, resource)) {
            if (in == null) return null;
            Set<String> names = new HashSet<>();
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    names.add(name);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return names;
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[eprotect] Could not read the original class file of {}", type.getName(), t);
            return null;
        }
    }

    @Nullable
    private static InputStream open(Class<?> type, String resource) {
        InputStream in = type.getResourceAsStream("/" + resource);
        if (in != null) return in;
        ClassLoader loader = type.getClassLoader();
        return loader != null ? loader.getResourceAsStream(resource) : ClassLoader.getSystemResourceAsStream(resource);
    }

    /**
     * Mixin のクラスが宣言しているフィールド名。適用後は Mixin の ClassNode が手放されていることがあるので、
     * 取れなければ jar にある Mixin のクラスファイルを読む（Mixin のクラスは読み込めないが、ファイルとしては読める）。
     */
    private static Set<String> mixinFieldNames(Class<?> target, IMixinInfo mixin) {
        Set<String> names = new HashSet<>();
        try {
            ClassNode node = mixin.getClassNode(0);
            if (node != null && node.fields != null) for (FieldNode field : node.fields) names.add(field.name);
        } catch (Throwable ignored) {}
        if (!names.isEmpty()) return names;
        String resource = mixin.getClassName().replace('.', '/') + ".class";
        ClassLoader loader = target.getClassLoader();
        try (InputStream in = loader != null ? loader.getResourceAsStream(resource) : ClassLoader.getSystemResourceAsStream(resource)) {
            if (in == null) return names;
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    names.add(name);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[eprotect] Could not read the mixin class file {}", resource, t);
        }
        return names;
    }

    @Nullable
    private static final java.lang.reflect.Method GET_MIXINS = findGetMixins();

    @Nullable
    private static java.lang.reflect.Method findGetMixins() {
        try {
            java.lang.reflect.Method method = ClassInfo.class.getDeclaredMethod("getMixins");
            method.setAccessible(true);
            return method;
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[eprotect] ClassInfo.getMixins is not accessible", t);
            return null;
        }
    }

    /**
     * そのクラスを対象にした Mixin。公開の getAppliedMixins() は環境によって記録されない（空のまま）ので、
     * 対象として登録された一覧（getMixins、非公開）を優先する。
     */
    @SuppressWarnings("unchecked")
    private static Set<IMixinInfo> mixinsOf(ClassInfo info) {
        Set<IMixinInfo> result = new java.util.LinkedHashSet<>();
        if (GET_MIXINS != null) {
            try {
                result.addAll((Set<? extends IMixinInfo>) GET_MIXINS.invoke(info));
            } catch (Throwable ignored) {}
        }
        result.addAll(info.getAppliedMixins());
        return result;
    }

    /** そのクラスに適用された Mixin のうち、同じ名前のフィールドを持つものの MOD */
    private static String injectingMod(Class<?> type, String fieldName) {
        try {
            ClassInfo info = ClassInfo.fromCache(type.getName().replace('.', '/'));
            if (info != null) {
                for (IMixinInfo mixin : mixinsOf(info)) {
                    if (mixinFieldNames(type, mixin).contains(fieldName)) return CallerAttribution.modOfClassName(mixin.getClassName());
                }
            }
        } catch (Throwable ignored) {}
        // Mixin の慣習の接頭辞（modid$name）
        int dollar = fieldName.indexOf('$');
        if (dollar > 0) {
            String prefix = fieldName.substring(0, dollar);
            if (net.minecraftforge.fml.ModList.get() != null && net.minecraftforge.fml.ModList.get().isLoaded(prefix)) return prefix;
        }
        return CallerAttribution.UNKNOWN;
    }
}
