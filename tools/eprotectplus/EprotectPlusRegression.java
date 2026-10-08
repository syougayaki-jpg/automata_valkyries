import com.automatavalkyries.eprotectplus.BaselineStore;
import com.automatavalkyries.eprotectplus.EprotectPlusAgent;
import com.automatavalkyries.eprotectplus.EprotectPlusBridge;
import com.automatavalkyries.eprotectplus.flag.FlagDictionaryAgent;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/** Standalone Java 17 regression checks; no Minecraft or external MOD execution. */
public final class EprotectPlusRegression {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        BaselineStore store = new BaselineStore();
        ClassLoader first = new EqualLoader(), second = new EqualLoader();
        store.capture(first, "same/Name", new byte[]{1});
        store.capture(second, "same/Name", new byte[]{2});
        check(store.get(first, "same/Name")[0] == 1 && store.get(second, "same/Name")[0] == 2, "Loader identity collision");
        byte[] copy = store.get(first, "same/Name");
        copy[0] = 99;
        check(store.get(first, "same/Name")[0] == 1, "Baseline exposed mutable bytes");
        store.capture(null, "same/Name", new byte[]{3});
        check(store.get(null, "same/Name")[0] == 3, "Bootstrap loader unsupported");

        RuntimeException expected = new RuntimeException("expected");
        int[] calls = {0};
        EprotectPlusBridge.runPrivileged(() -> {
            try {
                EprotectPlusBridge.runCloaked(() -> { calls[0]++; throw expected; });
                throw new AssertionError("Exception swallowed");
            } catch (RuntimeException actual) { check(actual == expected, "Exception identity lost"); }
            check(EprotectPlusBridge.isPrivileged(), "Outer scope lost");
        });
        check(calls[0] == 1 && !EprotectPlusBridge.isPrivileged(), "Duplicate execution or leaked flag");
        EprotectPlusBridge.triggerReinforced(60000, "monotonic test");
        EprotectPlusBridge.triggerReinforced(1, "shorter extension");
        check(EprotectPlusBridge.getReinforcedRemainingMs() > 1000
                && EprotectPlusBridge.getReinforcedRemainingMs() <= 60000, "Reinforcement shortened or units changed");
        EprotectPlusBridge.resetReinforced();
        check(!EprotectPlusBridge.isReinforced(), "Reinforcement reset failed");
        check(!EprotectPlusAgent.annihilateEntityJvm(new NoopEntity()), "Noop entity falsely reported dead");

        Field transformerField = FlagDictionaryAgent.class.getDeclaredField("TRANSFORMER");
        transformerField.setAccessible(true);
        ClassFileTransformer transformer = (ClassFileTransformer) transformerField.get(null);
        byte[] bytes = EprotectPlusRegression.class.getResourceAsStream("/EprotectPlusRegression.class").readAllBytes();
        String name = "com/automatavalkyries/protect/flag/FlagDictionary";
        check(transformer.transform(first, name, null, null, bytes) == null, "Initial flag load modified");
        check(transformer.transform(first, name, EprotectPlusRegression.class, null, bytes) == null, "Identical flag input falsely restored");
        byte[] altered = bytes.clone();
        altered[altered.length - 1] ^= 1;
        check(Arrays.equals(transformer.transform(first, name, EprotectPlusRegression.class, null, altered), bytes), "Flag baseline not returned");
        check(transformer.transform(second, name, EprotectPlusRegression.class, null, altered) == null, "Unknown loader inherited another baseline");

        Method critical = EprotectPlusAgent.class.getDeclaredMethod("isCriticalClass", String.class);
        critical.setAccessible(true);
        check(!(boolean) critical.invoke(null, "net/minecraft/world/entity/EntityType"), "Entity prefix too broad");
        check(!(boolean) critical.invoke(null, name), "Flag transformers overlap");
        check((boolean) critical.invoke(null, "net/minecraft/world/entity/Entity"), "Entity protection missing");
        Method handle = EprotectPlusAgent.class.getDeclaredMethod("handleTransform", ClassLoader.class,
                String.class, Class.class, byte[].class);
        handle.setAccessible(true);
        String protectedName = "com/automatavalkyries/entity/RegressionEntity";
        handle.invoke(null, first, protectedName, null, bytes);
        check(handle.invoke(null, first, protectedName, EprotectPlusRegression.class, bytes) == null, "Identical general input falsely restored");
        check(Arrays.equals((byte[]) handle.invoke(null, first, protectedName, EprotectPlusRegression.class, altered), bytes), "Unknown external caller bypassed protection");
        check(Arrays.equals((byte[]) handle.invoke(null, first, protectedName, EprotectPlusRegression.class, new byte[]{0}), bytes), "Malformed external input bypassed saved baseline");
        check(handle.invoke(null, second, protectedName, EprotectPlusRegression.class, altered) == null, "General baseline crossed loader boundary");

        Instrumentation rejecting = instrumentation(true);
        EprotectPlusAgent.agentmain("", rejecting);
        check(!EprotectPlusBridge.isActive() && !EprotectPlusAgent.hasInstrumentation(), "Failed registration marked active");
        EprotectPlusAgent.agentmain("", instrumentation(false));
        check(EprotectPlusBridge.isActive() && EprotectPlusAgent.hasInstrumentation(), "Registration retry failed");
        check(!EprotectPlusAgent.canRetransform(), "Unsupported retransform advertised");
        System.out.println("EprotectPlusRegression: PASS (loader isolation, scopes, success contract, transform policy, registration retry)");
    }

    private static Instrumentation instrumentation(boolean reject) {
        return (Instrumentation) Proxy.newProxyInstance(EprotectPlusRegression.class.getClassLoader(),
                new Class<?>[]{Instrumentation.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("addTransformer") && reject) throw new UnsupportedOperationException("registration rejected");
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
    }

    private static final class EqualLoader extends ClassLoader {
        @Override public boolean equals(Object object) { return object instanceof EqualLoader; }
        @Override public int hashCode() { return 1; }
    }
    private static final class NoopEntity {
        public void setHealth(float health) { }
        public float getHealth() { return 20F; }
    }
}
