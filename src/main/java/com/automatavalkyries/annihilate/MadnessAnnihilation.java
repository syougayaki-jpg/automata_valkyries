package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.network.AnnihilatedEntitiesPacket;
import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.SpecificCountermeasures;
import com.google.gson.JsonArray;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Explicit Madness compatibility; no instrumentation, transformer manipulation or guessed termination methods.
 * The hidden package is read as a String field, never through ModLoadingHelper3.Name.toString().
 * Global boss shutdown and the one known save file are intentionally part of an explicit annihilation request.
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class MadnessAnnihilation {
    private static final String[] LOADERS = {
            "net.minecraft.madcrazy.ModLoadingHelper3", "net.madness.util.ModLoadingHelper3"};
    private static final Map<Field, Boolean> ORIGINAL_SWITCHES = new LinkedHashMap<>();
    private static final Set<String> REPORTED = new java.util.HashSet<>();

    private MadnessAnnihilation() {}

    /** Includes the Entity-only tier, without loading any foreign class. */
    public static boolean isMadness(Entity entity) {
        if (entity == null) return false;
        for (Class<?> c = entity.getClass(); c != null && c != Entity.class; c = c.getSuperclass()) {
            if (c.getName().startsWith("net.madness.")) return true;
        }
        return false;
    }

    public record Identity(UUID uuid, int id) {}

    /** Pin fields to Entity.class so a subclass cannot substitute an identity field or a getter. */
    public static Identity readIdentity(Entity target) {
        try {
            return new Identity((UUID) declaredField(Entity.class, UUID.class, "uuid", "f_19820_").get(target),
                    declaredField(Entity.class, int.class, "id", "f_19848_").getInt(target)); // f_19843_ は ENTITY_COUNTER（静的な AtomicInteger）。取り違えて配布環境で落ちた（2026-10-21）
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read stored Entity identity", failure);
        }
    }

    public static UUID uuid(Entity target) {
        return readIdentity(target).uuid();
    }

    public static int id(Entity target) {
        return readIdentity(target).id();
    }

    public static boolean isRemovalConfirmed(Entity target) {
        if (!(target.level() instanceof ServerLevel level)) return false;
        Identity identity = readIdentity(target);
        return com.automatavalkyries.protect.DirectRead.isRemoved(target)
                && level.getEntity(identity.uuid()) != target && level.getEntity(identity.id()) != target;
    }

    /** A failed specialized path must never fall through to generic end/kill method discovery. */
    public static boolean annihilate(ServerLevel level, Entity target) {
        if (!SpecificCountermeasures.enabled() || !isMadness(target) || target instanceof Player
                || target instanceof IEprotected || target.level() != level || !level.getServer().isSameThread()) return false;
        Identity identity = readIdentity(target);
        try {
            prepare(target);
            nativeDelete(target);
            if (!isRemovalConfirmed(target)) {
                report("entity removal", null);
                return false;
            }
            AnnihilationExecutionAgent.markAnnihilated(identity.uuid());
            ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),
                    new AnnihilatedEntitiesPacket(List.of(identity.uuid()), List.of(identity.id())));
            return true;
        } catch (ReflectiveOperationException failure) {
            report("entity preparation", failure);
            return false;
        } catch (RuntimeException | LinkageError failure) {
            report("entity execution", failure);
            return false;
        }
    }

    static void prepare(Entity target) throws ReflectiveOperationException {
        Bindings bindings = resolve(target.getClass().getClassLoader());
        if (bindings == null) throw new ClassNotFoundException("Madness hidden package is unavailable");
        disableGlobalActivity(bindings.switches());
        // These names are data slots, not dictionary guesses or arbitrary public methods.
        // Direct data writes also cover the Entity-only C846446 tier.
        SynchedEntityData data = (SynchedEntityData) declaredField(Entity.class, SynchedEntityData.class, "entityData", "f_19804_").get(target);
        for (Class<?> c = target.getClass(); c != null && c.getName().startsWith("net.madness."); c = c.getSuperclass()) {
            for (String name : List.of("NCHelPo", "f_198383_", "DATA_VALUE_ID", "DATA_HEALTH_ID")) {
                Field field = optionalField(c, name);
                if (field == null || !Modifier.isStatic(field.getModifiers())
                        || !EntityDataAccessor.class.isAssignableFrom(field.getType())) continue;
                writeHealthSlot(data, (EntityDataAccessor<?>) field.get(null), name.equals("NCHelPo") ? -1F : 0F);
            }
            Field dead = optionalField(c, "isDead");
            if (dead != null && dead.getType() == boolean.class && !Modifier.isStatic(dead.getModifiers())) dead.setBoolean(target, true);
        }
        target.getPersistentData().putFloat("m_health", -1F);
        clearBossBars(target);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void writeHealthSlot(SynchedEntityData data, EntityDataAccessor<?> accessor, float health) {
        Object current = data.get(accessor);
        if (current instanceof String) data.set((EntityDataAccessor) accessor, Float.toString(health));
        else if (current instanceof Float) data.set((EntityDataAccessor) accessor, health);
    }

    /**
     * 3.3.1's deleteEntity is the bounded native storage-removal path. m_708554_ also changes game rules,
     * traverses other mods and schedules work; C840004.removeDamnEntity accesses Minecraft.player.
     * Neither is needed here. Do not broaden this allowlist to guessed end/remove methods.
     */
    static void nativeDelete(Entity target) throws ReflectiveOperationException {
        Bindings bindings = resolve(target.getClass().getClassLoader());
        if (bindings == null) throw new ClassNotFoundException("Madness hidden package is unavailable");
        Method method = bindings.executor().getDeclaredMethod("deleteEntity", Entity.class, Level.class, boolean.class);
        if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class) {
            throw new NoSuchMethodException("Unsupported Madness deleteEntity signature");
        }
        method.setAccessible(true);
        method.invoke(null, target, target.level(), true);
    }

    /** Called by the client packet before its ordinary removal path. */
    public static void removeClientEntity(Entity target) {
        if (!isMadness(target) || target instanceof Player || target instanceof IEprotected) return;
        try {
            prepare(target);
            nativeDelete(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            report("client entity removal", failure);
        }
    }

    /** Explicit global sweep: the foreign registry/save are global, so this does not pretend to be radius-limited. */
    public static int sweep(ServerLevel level) {
        if (!SpecificCountermeasures.enabled() || level == null || !level.getServer().isSameThread()) return 0;
        try {
            Bindings bindings = resolve(MadnessAnnihilation.class.getClassLoader());
            if (bindings == null) return 0;
            disableGlobalActivity(bindings.switches());
            int count = purgeRegistry(bindings.registry(), bindings.boss());
            clearSaveBuffer(bindings.persistence());
            clearKnownSave(FMLPaths.CONFIGDIR.get());
            ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                    new AnnihilatedEntitiesPacket(List.of(), List.of(), true));
            return count;
        } catch (ReflectiveOperationException | IOException | RuntimeException | LinkageError failure) {
            report("non-Entity sweep", failure);
            return 0;
        }
    }

    public static void sweepClient() {
        try {
            Bindings bindings = resolve(MadnessAnnihilation.class.getClassLoader());
            if (bindings == null) return;
            disableGlobalActivity(bindings.switches());
            purgeRegistry(bindings.registry(), bindings.boss());
            clearSaveBuffer(bindings.persistence());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            report("client non-Entity sweep", failure);
        }
    }

    record Bindings(Class<?> switches, Class<?> executor, Class<?> registry, Class<?> boss, Class<?> persistence) {}

    static Bindings resolve(ClassLoader preferred) throws ReflectiveOperationException {
        for (String loaderName : LOADERS) {
            Class<?> helper;
            try { helper = Class.forName(loaderName, false, preferred); }
            catch (ClassNotFoundException ignored) { continue; }
            String packageName = readPackageName(helper);
            if (packageName == null) continue;
            Field custom = optionalField(helper, "f_169321_");
            Object value = custom == null ? null : custom.get(null);
            ClassLoader loader = value instanceof ClassLoader cl ? cl : preferred;
            String prefix = "net.minecraft." + packageName + ".";
            return new Bindings(Class.forName(prefix + "C846554", false, loader),
                    Class.forName(prefix + "C842479", false, loader),
                    Class.forName(prefix + "C846359", false, loader),
                    Class.forName(prefix + "C841537", false, loader),
                    Class.forName(prefix + "C845861", false, loader));
        }
        return null;
    }

    static String readPackageName(Class<?> helper) throws ReflectiveOperationException {
        Field listField = optionalField(helper, "list");
        Object entries = listField == null ? null : listField.get(null);
        if (entries instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof String name
                && name.matches("[a-z]{5,10}")) return name;
        Field nameField = optionalField(helper, "name");
        Object nameObject = nameField == null ? null : nameField.get(null);
        if (nameObject == null) return null;
        Field valueField = optionalField(nameObject.getClass(), "var56");
        Object value = valueField == null ? null : valueField.get(nameObject);
        return value instanceof String name && name.matches("[a-z]{5,10}") ? name : null;
    }

    static synchronized void disableGlobalActivity(Class<?> switches) throws ReflectiveOperationException {
        for (String name : List.of("stopAttack", "hasMain", "hasRenderer", "isGodMode", "isOldGod")) {
            Field field = optionalField(switches, name);
            if (field == null) continue; // 3.3.1 has no stopAttack/isGodMode fields.
            if (field.getType() != boolean.class || !Modifier.isStatic(field.getModifiers())
                    || Modifier.isFinal(field.getModifiers())) throw new NoSuchFieldException("Unsupported Madness switch: " + name);
            ORIGINAL_SWITCHES.putIfAbsent(field, field.getBoolean(null));
            field.setBoolean(null, true);
        }
    }

    static int purgeRegistry(Class<?> registry, Class<?> bossType) throws ReflectiveOperationException {
        Object value = declaredField(registry, "m_691848_").get(null);
        if (!(value instanceof CopyOnWriteArrayList<?> list)) throw new IllegalStateException("Unsupported Madness registry");
        return purgeBossList(list, bossType);
    }

    /** Only inherited JDK methods are allowed; never call the foreign clear/remove/toString implementations. */
    static int purgeBossList(CopyOnWriteArrayList<?> list, Class<?> bossType) throws ReflectiveOperationException {
        for (Method method : List.of(list.getClass().getMethod("toArray"),
                list.getClass().getMethod("removeIf", Predicate.class))) {
            if (method.getDeclaringClass() != CopyOnWriteArrayList.class) {
                throw new IllegalStateException("Unsupported Madness registry override: " + method.getName());
            }
        }
        Object[] snapshot = list.toArray();
        Set<Object> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        Field hp = declaredField(bossType, "f_699991_");
        Field dead = declaredField(bossType, "isDeathCrazy");
        if (hp.getType() != int.class || dead.getType() != boolean.class
                || Modifier.isStatic(hp.getModifiers()) || Modifier.isStatic(dead.getModifiers())) {
            throw new IllegalStateException("Unsupported Madness boss fields");
        }
        for (Object boss : snapshot) {
            if (!bossType.isInstance(boss) || boss instanceof Entity) {
                throw new IllegalStateException("Unexpected entry in Madness boss registry");
            }
        }
        for (Object boss : snapshot) {
            hp.setInt(boss, 0);
            dead.setBoolean(boss, true);
            clearBossBars(boss);
            selected.add(boss);
        }
        list.removeIf(selected::contains);
        return selected.size();
    }

    static void clearBossBars(Object owner) throws IllegalAccessException {
        for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !ServerBossEvent.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                if (field.get(owner) instanceof ServerBossEvent boss) {
                    boss.removeAllPlayers();
                    boss.setVisible(false);
                }
            }
        }
    }

    private static void clearSaveBuffer(Class<?> persistence) throws ReflectiveOperationException {
        Field buffer = optionalField(persistence, "f_695182_");
        if (buffer != null && buffer.getType() == JsonArray.class && Modifier.isStatic(buffer.getModifiers())
                && !Modifier.isFinal(buffer.getModifiers())) buffer.set(null, new JsonArray());
    }

    /** An empty JSON array is valid for Madness's loader; a zero-byte file is not. No directory deletion. */
    static void clearKnownSave(Path configRoot) throws IOException {
        Path root = configRoot.toAbsolutePath().normalize();
        Path file = root.resolve("madness/data/entity_info.json");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
        for (Path part = file; !part.equals(root); part = part.getParent()) {
            if (Files.isSymbolicLink(part)) throw new IOException("Refusing linked Madness save path");
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || !file.toRealPath().startsWith(root.toRealPath())) throw new IOException("Unexpected Madness save path");
        Files.writeString(file, "[]", StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    static Field declaredField(Class<?> owner, Class<?> expectedType, String... names) throws NoSuchFieldException {
        for (String name : names) {
            Field field = optionalField(owner, name);
            if (field != null) {
                if (expectedType != null && !expectedType.isAssignableFrom(field.getType())) {
                    throw new NoSuchFieldException(owner.getName() + "." + name + " has type "
                            + field.getType().getName() + ", expected " + expectedType.getName());
                }
                return field;
            }
        }
        throw new NoSuchFieldException(owner.getName() + ": " + String.join(",", names));
    }

    static Field declaredField(Class<?> owner, String... names) throws NoSuchFieldException {
        return declaredField(owner, null, names);
    }

    private static Field optionalField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ignored) { return null; }
    }

    public static synchronized void resetSwitches() {
        ORIGINAL_SWITCHES.forEach((field, value) -> {
            try { field.setBoolean(null, value); }
            catch (IllegalAccessException failure) { report("switch restoration", failure); }
        });
        ORIGINAL_SWITCHES.clear();
        REPORTED.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { resetSwitches(); }

    private static synchronized void report(String operation, Throwable failure) {
        if (REPORTED.add(operation)) AutomataValkyries.LOGGER.warn(
                "[Madness compatibility] {} incomplete; generic termination fallback is disabled", operation, failure);
    }
}
