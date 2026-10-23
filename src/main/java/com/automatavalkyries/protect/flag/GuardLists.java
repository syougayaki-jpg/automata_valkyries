package com.automatavalkyries.protect.flag;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.FlagKillPredicates;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.util.StaticReferencePurger;
import com.automatavalkyries.util.UnsafeHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 他 MOD の無敵・保護・復活・HP固定・位置拘束・死亡拒否の static 一覧（{@code Collection} / {@code Map}）を
 * Java エージェント無しで自動発見し、抹殺・終焉機構の対象個体（プレイヤー・MOB）だけをそこから除去する（準汎用）。
 * <p>
 * {@link KillLists} が「死亡・消去リストから保護対象を救出する」のに対し、本クラスは逆方向の
 * 「無敵・保護・蘇生・HPロック・座標ロックのリストから終焉対象を抹消する」役割を担う。
 * </p>
 * 見つけ方（Java エージェント不要、初期化済みのクラスのみ走査）:
 * <ol>
 *   <li>バニラ主要クラス（{@code Entity}, {@code LivingEntity}, {@code Player}, {@code ServerPlayer},
 *       {@code LocalPlayer}, {@code Inventory}, {@code Minecraft}, {@code DeathScreen}）に適用された他 MOD の Mixin の
 *       クラスファイルを {@link ClassReader} で読み、参照先の管理クラス（{@code InvulnerableEntityManager},
 *       {@code ImdsKillManager}, {@code ResurrectionManager}, {@code HealthLockManager}, {@code EntityLocationManager} 等）を辿る</li>
 *   <li>プレイヤーが所持していたアイテムのクラスやイベントハンドラから参照される管理クラスを辿る</li>
 *   <li>見つけた管理クラスの static な {@code Collection} / {@code Map} のうち、守り・復活・HP固定・拘束を表すものを登録し、
 *       対象の {@code Entity}・{@code UUID}・文字列表現・エンティティID を一括パージする</li>
 * </ol>
 */
public final class GuardLists {

    private static final int MAX_LISTS = 384;

    private static final Map<Field, String> LISTS = new ConcurrentHashMap<>();
    private static final Set<Class<?>> SCANNED = ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> PENDING = ConcurrentHashMap.newKeySet();
    private static volatile boolean baseMixinsScanned = false;
    private static volatile boolean clientMixinsScanned = false;

    @Nullable
    private static final Method GET_MIXINS = findGetMixins();

    private GuardLists() {}

    @Nullable
    private static Method findGetMixins() {
        try {
            Method method = ClassInfo.class.getDeclaredMethod("getMixins");
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 覚えている保護一覧（テスト・診断用） */
    public static Set<Field> lists() {
        return Collections.unmodifiableSet(LISTS.keySet());
    }

    /**
     * 指定クラスおよびそのバイトコードが参照する他 MOD の管理クラスから、保護・無敵・復活・拘束の static 一覧を探す。
     */
    public static void discoverFrom(@Nullable Class<?> origin) {
        retryPending();
        if (origin == null || !isForeign(origin) || !SCANNED.add(origin)) return;
        if (SCANNED.size() > 4096) return;

        ClassLoader loader = origin.getClassLoader();
        Set<Class<?>> hop1 = new LinkedHashSet<>();
        hop1.add(origin);
        for (String owner : referencedClassNames(origin.getName().replace('.', '/'), loader)) {
            loadForeignIfPresent(owner, loader, hop1);
        }

        // 2ホップ目（Item -> Procedure -> KillManager 等）も辿る
        Set<Class<?>> allCandidates = new LinkedHashSet<>(hop1);
        for (Class<?> c1 : hop1) {
            if (c1 == origin) continue;
            if (SCANNED.add(c1)) {
                for (String owner2 : referencedClassNames(c1.getName().replace('.', '/'), loader)) {
                    if (allCandidates.size() >= 96) break;
                    loadForeignIfPresent(owner2, loader, allCandidates);
                }
            }
        }

        for (Class<?> candidate : allCandidates) {
            collectGuardLists(candidate);
        }
    }

    /**
     * 抹殺・終焉機構の対象を、発見済みのすべての無敵・保護・復活・HP固定・位置拘束リストから除去する。
     */
    public static int purge(Entity target) {
        if (target == null) return 0;
        ensureBaseScanned(target);
        retryPending();
        if (LISTS.isEmpty()) return 0;

        Set<Object> victims = Collections.newSetFromMap(new IdentityHashMap<>());
        victims.add(target);
        int total = 0;
        for (Map.Entry<Field, String> entry : LISTS.entrySet()) {
            Field field = entry.getKey();
            int removed = StaticReferencePurger.purgeFields(
                    List.of(field),
                    victims,
                    target.getUUID(),
                    target.getScoreboardName()
            );
            if (removed > 0) {
                total += removed;
            }
        }
        return total;
    }

    private static void ensureBaseScanned(Entity target) {
        ClassLoader loader = target.getClass().getClassLoader();
        if (loader == null) loader = GuardLists.class.getClassLoader();
        if (!baseMixinsScanned) {
            baseMixinsScanned = true;
            scanInjectedStaticFields();
            scanAppliedMixins(loader,
                    "net/minecraft/world/entity/Entity",
                    "net/minecraft/world/entity/LivingEntity",
                    "net/minecraft/world/entity/Mob",
                    "net/minecraft/world/entity/player/Player",
                    "net/minecraft/server/level/ServerPlayer",
                    "net/minecraft/world/entity/player/Inventory",
                    "net/minecraft/network/syncher/SynchedEntityData",
                    "net/minecraft/server/players/PlayerList"
            );
        }
        if (target.level() != null && target.level().isClientSide && !clientMixinsScanned) {
            clientMixinsScanned = true;
            scanAppliedMixins(loader,
                    "net/minecraft/client/Minecraft",
                    "net/minecraft/client/player/LocalPlayer",
                    "net/minecraft/client/gui/screens/DeathScreen",
                    "net/minecraft/client/multiplayer/ClientPacketListener"
            );
        }
        if (isForeign(target.getClass())) {
            discoverFrom(target.getClass());
        }
    }

    private static void scanInjectedStaticFields() {
        for (Class<?> c : new Class<?>[]{Entity.class, LivingEntity.class, Mob.class, Player.class, Inventory.class}) {
            for (InjectedMembers.InjectedField injected : InjectedMembers.fields(c)) {
                if (CallerAttribution.SELF.equals(injected.mod())) continue;
                Field field = injected.field();
                if (isListField(field) && isGuardOrLockFieldName(field.getName())) {
                    remember(field, injected.mod());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void scanAppliedMixins(ClassLoader loader, String... targetInternalNames) {
        for (String targetInternal : targetInternalNames) {
            try {
                ClassInfo info = ClassInfo.fromCache(targetInternal);
                if (info == null) continue;
                Set<IMixinInfo> mixins = new LinkedHashSet<>();
                if (GET_MIXINS != null) {
                    try {
                        mixins.addAll((Set<? extends IMixinInfo>) GET_MIXINS.invoke(info));
                    } catch (Throwable ignored) {}
                }
                mixins.addAll(info.getAppliedMixins());
                for (IMixinInfo mixin : mixins) {
                    String mixinClass = mixin.getClassName();
                    String mod = CallerAttribution.modOfClassName(mixinClass);
                    if (!CallerAttribution.isForeign(mod)) continue;
                    Set<String> refs = referencedClassNames(mixinClass.replace('.', '/'), loader);
                    for (String owner : refs) {
                        try {
                            Class<?> cls = Class.forName(owner.replace('/', '.'), false, loader);
                            if (isForeign(cls)) {
                                discoverFrom(cls);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void retryPending() {
        if (PENDING.isEmpty()) return;
        for (Class<?> type : PENDING.toArray(new Class<?>[0])) {
            if (UnsafeHelper.isInitialized(type)) {
                PENDING.remove(type);
                collectGuardLists(type);
            }
        }
    }

    private static void collectGuardLists(Class<?> type) {
        if (!UnsafeHelper.isInitialized(type)) {
            if (PENDING.size() < 1024) PENDING.add(type);
            return;
        }
        String classSimple = type.getSimpleName();
        boolean guardClass = isGuardOrLockClassName(classSimple);
        Field[] fields = UnsafeHelper.getRealDeclaredFields(type);
        String mod = CallerAttribution.modOf(type);
        for (Field field : fields) {
            if (!isListField(field)) continue;
            String name = field.getName();
            if (guardClass || isGuardOrLockFieldName(name)) {
                remember(field, mod);
            }
        }
    }

    private static void remember(Field field, String mod) {
        if (LISTS.size() >= MAX_LISTS || LISTS.containsKey(field)) return;
        try {
            field.setAccessible(true);
        } catch (Throwable t) {
            return;
        }
        if (LISTS.putIfAbsent(field, mod) == null) {
            AutomataValkyries.LOGGER.info("[terminus] Watching guard/lock list {}.{} ({})",
                    field.getDeclaringClass().getName(), field.getName(), mod);
        }
    }

    private static boolean isListField(Field field) {
        return Modifier.isStatic(field.getModifiers())
                && (Collection.class.isAssignableFrom(field.getType()) || Map.class.isAssignableFrom(field.getType()));
    }

    private static boolean isGuardOrLockClassName(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) return false;
        Set<FlagDictionary.Category> cats = FlagDictionary.classify(simpleName);
        if (cats.contains(FlagDictionary.Category.GUARD)
                || cats.contains(FlagDictionary.Category.REVIVE)
                || cats.contains(FlagDictionary.Category.HEALTH_TAMPER)
                || cats.contains(FlagDictionary.Category.RESPAWN_BLOCK)) {
            return true;
        }
        String lower = simpleName.toLowerCase(Locale.ROOT);
        return lower.contains("invulnerab") || lower.contains("immortal") || lower.contains("protect")
                || lower.contains("resurrect") || lower.contains("revive") || lower.contains("healthlock")
                || lower.contains("locationmanager") || lower.contains("killmanager") || lower.contains("defense")
                || lower.contains("defend") || lower.contains("undying") || lower.contains("godmode")
                || lower.contains("whitelist") || lower.contains("keepinv");
    }

    private static boolean isGuardOrLockFieldName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (FlagKillPredicates.protectiveOnTarget(name) || FlagDictionary.heldOnDeadPlayer(name)) {
            return true;
        }
        Set<FlagDictionary.Category> cats = FlagDictionary.classify(name);
        if (cats.contains(FlagDictionary.Category.GUARD)
                || cats.contains(FlagDictionary.Category.REVIVE)
                || cats.contains(FlagDictionary.Category.HEALTH_TAMPER)
                || cats.contains(FlagDictionary.Category.CONTROL)
                || cats.contains(FlagDictionary.Category.RESPAWN_BLOCK)) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("invulnerab") || lower.contains("inv_uuid") || lower.contains("keepinv")
                || lower.contains("protect") || lower.contains("immortal") || lower.contains("resurrect")
                || lower.contains("revive") || lower.contains("locked") || lower.contains("lock_map")
                || lower.contains("health_lock") || lower.contains("healthlock") || lower.contains("whitelist")
                || lower.contains("god") || lower.contains("undying") || lower.contains("shield")
                || lower.contains("ward") || lower.contains("bless") || lower.contains("immune");
    }

    private static void loadForeignIfPresent(String internalOwner, ClassLoader loader, Set<Class<?>> out) {
        if (internalOwner == null || internalOwner.isEmpty()) return;
        if (internalOwner.startsWith("java/") || internalOwner.startsWith("jdk/")
                || internalOwner.startsWith("sun/") || internalOwner.startsWith("net/minecraft/")
                || internalOwner.startsWith("net/minecraftforge/") || internalOwner.startsWith("com/mojang/")
                || internalOwner.startsWith("org/spongepowered/") || internalOwner.startsWith("com/automatavalkyries/")) {
            return;
        }
        try {
            Class<?> cls = Class.forName(internalOwner.replace('/', '.'), false, loader);
            if (isForeign(cls)) {
                out.add(cls);
            }
        } catch (Throwable ignored) {}
    }

    /** ASM でクラスファイルのリソースから参照先クラス名（内部名）を抽出する（Java エージェント不要） */
    public static Set<String> referencedClassNames(String internalClassName, @Nullable ClassLoader loader) {
        Set<String> owners = new LinkedHashSet<>();
        int slashIdx = internalClassName.indexOf('$');
        String resource = internalClassName + ".class";
        try (InputStream in = loader != null ? loader.getResourceAsStream(resource) : ClassLoader.getSystemResourceAsStream(resource)) {
            if (in != null) {
                new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String methodName, String desc, boolean isInterface) {
                                owners.add(owner);
                            }

                            @Override
                            public void visitFieldInsn(int opcode, String owner, String fieldName, String desc) {
                                owners.add(owner);
                            }

                            @Override
                            public void visitTypeInsn(int opcode, String typeName) {
                                if (opcode == Opcodes.NEW || opcode == Opcodes.CHECKCAST || opcode == Opcodes.INSTANCEOF) {
                                    owners.add(typeName);
                                }
                            }

                            @Override
                            public void visitLdcInsn(Object value) {
                                if (value instanceof Type t && t.getSort() == Type.OBJECT) {
                                    owners.add(t.getInternalName());
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        } catch (Throwable ignored) {}
        owners.remove(internalClassName);
        if (slashIdx > 0) {
            owners.remove(internalClassName.substring(0, slashIdx));
        }
        return owners;
    }

    private static boolean isForeign(Class<?> type) {
        return CallerAttribution.isForeign(CallerAttribution.modOf(type));
    }
}
