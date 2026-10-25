package com.automatavalkyries.eprotectplus;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;

/**
 * JVM Hidden Class ({@link MethodHandles.Lookup#defineHiddenClass}) によるエージェント中枢の隠蔽金庫。
 * <p>
 * Hidden Class は {@link Class#forName} で名前解決できず、{@link Instrumentation#isModifiableClass} も
 * {@code false} となるため、他の Java エージェントによるバイトコード改ざんやクラス名指定のリフレクションから隔離される。
 * {@link EprotectPlusAgent} や {@code FlagDictionaryProtector} の公開クラス側フィールドが外部から
 * {@code Unsafe} 等で {@code null} 化されても、この金庫から即座に自己修復（Self-Heal）する。
 * </p>
 */
public final class HiddenAgentVault {

    private static final MethodHandle GET_INST;
    private static final MethodHandle SET_INST;
    private static final MethodHandle GET_FLAG_INST;
    private static final MethodHandle SET_FLAG_INST;
    private static final MethodHandle GET_BASELINE;
    private static final MethodHandle SET_BASELINE;
    private static final MethodHandle GET_DICT;
    private static final MethodHandle SET_DICT;
    private static final MethodHandle GET_DIGEST;
    private static final MethodHandle SET_DIGEST;
    private static final boolean HIDDEN_ACTIVE;

    static {
        MethodHandle getInst = null, setInst = null;
        MethodHandle getFlagInst = null, setFlagInst = null;
        MethodHandle getBase = null, setBase = null;
        MethodHandle getDict = null, setDict = null;
        MethodHandle getDigest = null, setDigest = null;
        boolean ok = false;
        try {
            byte[] bytes = buildVaultClassBytes();
            MethodHandles.Lookup lookup = MethodHandles.lookup().defineHiddenClass(
                    bytes, true, MethodHandles.Lookup.ClassOption.STRONG);
            Class<?> vault = lookup.lookupClass();
            getInst = lookup.findStaticGetter(vault, "s0", Object.class);
            setInst = lookup.findStaticSetter(vault, "s0", Object.class);
            getFlagInst = lookup.findStaticGetter(vault, "s1", Object.class);
            setFlagInst = lookup.findStaticSetter(vault, "s1", Object.class);
            getBase = lookup.findStaticGetter(vault, "s2", Object.class);
            setBase = lookup.findStaticSetter(vault, "s2", Object.class);
            getDict = lookup.findStaticGetter(vault, "s3", Object.class);
            setDict = lookup.findStaticSetter(vault, "s3", Object.class);
            getDigest = lookup.findStaticGetter(vault, "s4", Object.class);
            setDigest = lookup.findStaticSetter(vault, "s4", Object.class);
            ok = vault.isHidden();
        } catch (Throwable ignored) {}
        GET_INST = getInst;
        SET_INST = setInst;
        GET_FLAG_INST = getFlagInst;
        SET_FLAG_INST = setFlagInst;
        GET_BASELINE = getBase;
        SET_BASELINE = setBase;
        GET_DICT = getDict;
        SET_DICT = setDict;
        GET_DIGEST = getDigest;
        SET_DIGEST = setDigest;
        HIDDEN_ACTIVE = ok;
    }

    private HiddenAgentVault() {}

    private static final StackWalker VAULT_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    public static boolean isHiddenActive() {
        return HIDDEN_ACTIVE;
    }

    private static boolean isTrustedVaultCaller() {
        return VAULT_WALKER.walk(frames -> frames
                .map(StackWalker.StackFrame::getClassName)
                .filter(cn -> !cn.startsWith("java.") && !cn.startsWith("jdk.") && !cn.startsWith("sun.")
                        && !cn.equals(HiddenAgentVault.class.getName()))
                .findFirst()
                .map(cn -> cn.startsWith("com.automatavalkyries.eprotectplus."))
                .orElse(false));
    }

    public static void storeInstrumentation(Instrumentation inst) {
        if (SET_INST == null || inst == null || loadInstrumentation() != null || !isTrustedVaultCaller()) return;
        try {
            SET_INST.invokeExact((Object) inst);
        } catch (Throwable ignored) {}
    }

    public static Instrumentation loadInstrumentation() {
        if (GET_INST == null) return null;
        try {
            Object raw = GET_INST.invokeExact();
            return (Instrumentation) raw;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void storeFlagInstrumentation(Instrumentation inst) {
        if (SET_FLAG_INST == null || inst == null || loadFlagInstrumentation() != null || !isTrustedVaultCaller()) return;
        try {
            SET_FLAG_INST.invokeExact((Object) inst);
        } catch (Throwable ignored) {}
    }

    public static Instrumentation loadFlagInstrumentation() {
        if (GET_FLAG_INST == null) return null;
        try {
            Object raw = GET_FLAG_INST.invokeExact();
            return (Instrumentation) raw;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void storeBaseline(BaselineStore store) {
        if (SET_BASELINE == null || store == null || loadBaseline() != null || !isTrustedVaultCaller()) return;
        try {
            SET_BASELINE.invokeExact((Object) store);
        } catch (Throwable ignored) {}
    }

    public static BaselineStore loadBaseline() {
        if (GET_BASELINE == null) return null;
        try {
            Object raw = GET_BASELINE.invokeExact();
            return (BaselineStore) raw;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void storeDictionary(Map<String, List<String>> dict, String digest) {
        boolean needDict = SET_DICT != null && dict != null && !dict.isEmpty() && loadDictionary() == null;
        boolean needDigest = SET_DIGEST != null && digest != null && !digest.isEmpty() && loadDigest() == null;
        if ((!needDict && !needDigest) || !isTrustedVaultCaller()) return;
        try {
            if (needDict) {
                SET_DICT.invokeExact((Object) dict);
            }
            if (needDigest) {
                SET_DIGEST.invokeExact((Object) digest);
            }
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("unchecked")
    public static Map<String, List<String>> loadDictionary() {
        if (GET_DICT == null) return null;
        try {
            Object raw = GET_DICT.invokeExact();
            return (Map<String, List<String>>) raw;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String loadDigest() {
        if (GET_DIGEST == null) return null;
        try {
            Object raw = GET_DIGEST.invokeExact();
            return (String) raw;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static byte[] buildVaultClassBytes() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(0xCAFEBABE);
            dos.writeShort(0);
            dos.writeShort(52); // Java 8 bytecode format
            dos.writeShort(11); // constant_pool_count = 11 (1..10)

            // #1: Utf8 class name (must match lookup package)
            dos.writeByte(1);
            dos.writeUTF("com/automatavalkyries/eprotectplus/Vault");
            // #2: Class #1
            dos.writeByte(7);
            dos.writeShort(1);
            // #3: Utf8 super name
            dos.writeByte(1);
            dos.writeUTF("java/lang/Object");
            // #4: Class #3
            dos.writeByte(7);
            dos.writeShort(3);
            // #5: Utf8 descriptor
            dos.writeByte(1);
            dos.writeUTF("Ljava/lang/Object;");
            // #6..#10: Utf8 field names s0..s4
            for (int i = 0; i < 5; i++) {
                dos.writeByte(1);
                dos.writeUTF("s" + i);
            }

            dos.writeShort(0x0031); // ACC_PUBLIC | ACC_FINAL | ACC_SUPER
            dos.writeShort(2);      // this_class = #2
            dos.writeShort(4);      // super_class = #4
            dos.writeShort(0);      // interfaces_count = 0
            dos.writeShort(5);      // fields_count = 5
            for (int i = 0; i < 5; i++) {
                dos.writeShort(0x0009); // ACC_PUBLIC | ACC_STATIC
                dos.writeShort(6 + i);  // name_index = #6..#10
                dos.writeShort(5);      // descriptor_index = #5
                dos.writeShort(0);      // attributes_count = 0
            }
            dos.writeShort(0); // methods_count = 0
            dos.writeShort(0); // attributes_count = 0
        }
        return baos.toByteArray();
    }
}
