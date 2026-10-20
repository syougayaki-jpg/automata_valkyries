package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.ValkyrieEntity.Mode;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** 戦術兵姫の基本（段階 1）: 表情・行動モードと追従の上限・機能停止と修理・オートマタヴァルキリー protect（消去の拒否・バックアップからの復元） */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ValkyrieGameTests {

    private static final String TEMPLATE = "empty";

    /** 出した兵姫がみな世界に入って（エンティティが読み込まれて）から run する */
    private static void whenLoaded(GameTestHelper helper, Runnable run, ValkyrieEntity... vs) {
        java.util.Set<net.minecraft.world.level.ChunkPos> forced = WeaponGameTests.forceChunks(helper, vs);
        Runnable inner = run;
        run = () -> {
            try {
                inner.run();
            } finally {
                for (net.minecraft.world.level.ChunkPos c : forced) helper.getLevel().setChunkForced(c.x, c.z, false);
            }
        };
        int[] waited = {0};
        helper.startSequence().thenWaitUntil(() -> {
            WeaponGameTests.kickStuckChunks(helper, waited, vs);
            for (ValkyrieEntity v : vs) {
                helper.assertTrue(helper.getLevel().getEntity(v.getUUID()) == v, "not in the world yet");
                helper.assertTrue(helper.getLevel().areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(v.blockPosition())), "entities not loaded yet");
            }
        }).thenExecute(run).thenIdle(1).thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "valkyrie")
    public static void valkyrieSpawnsAndKeepsExpression(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        helper.assertTrue(v.getMaxHealth() == 40.0F, "max health is not 40: " + v.getMaxHealth());
        helper.assertTrue(v.expressionName().equals("normal"), "starts with " + v.expressionName());
        v.setExpression(v.getExpression() + 1);
        helper.assertTrue(v.expressionName().equals("smile"), "expression did not advance: " + v.expressionName());
        v.setExpression(ValkyrieEntity.EXPRESSIONS.length); // 一周して戻る
        helper.assertTrue(v.getExpression() == 0, "expression did not wrap");
        v.setExpression(3);
        UUID owner = UUID.randomUUID();
        v.setOwnerUUID(owner);
        v.setMode(Mode.PATROL);
        CompoundTag tag = new CompoundTag();
        v.addAdditionalSaveData(tag);
        ValkyrieEntity copy = helper.spawn(ModEntities.VALKYRIE.get(), 3, 2, 1);
        copy.readAdditionalSaveData(tag);
        helper.assertTrue(copy.expressionName().equals("angry"), "expression not saved: " + copy.expressionName());
        helper.assertTrue(owner.equals(copy.getOwnerUUID()) && copy.getMode() == Mode.PATROL && copy.getHome() != null, "owner / mode / home not saved");
        helper.assertFalse(v.removeWhenFarAway(1000), "valkyrie would despawn");
        v.releaseAndDiscard();
        copy.releaseAndDiscard();
        helper.succeed();
    }

    /** 追従は持ち主ごとに 4 体まで */
    /**
     * 同調率の能力上昇（2026-10-20）: 100% で 1.2 倍。最大 HP・装甲・シールドの最大は 1.2 倍、電力の消費・攻撃の間隔は 1/1.2。0% では何も変わらない。
     * Tick を待たずに属性を直に掛けて確かめる
     */
    @GameTest(template = TEMPLATE, batch = "valkyrie")
    public static void valkyrieSyncRateRaisesStats(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        try {
            v.applySyncBonus();
            float hp0 = v.getMaxHealth(), shield0 = v.getMaxShield();
            double armor0 = v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            float power0 = com.automatavalkyries.entity.ai.ValkyrieUnitEffects.powerScale(v), interval0 = com.automatavalkyries.entity.ai.ValkyrieUnitEffects.attackIntervalScale(v);
            helper.assertTrue(v.syncMultiplier() == 1.0F, "0% sync is not neutral: " + v.syncMultiplier());
            v.addSync(100.0F, "test");
            v.applySyncBonus();
            helper.assertTrue(Math.abs(v.syncMultiplier() - 1.2F) < 1.0e-4F, "100% sync multiplier: " + v.syncMultiplier());
            helper.assertTrue(Math.abs(v.getMaxHealth() - hp0 * 1.2F) < 0.01F, "max health " + hp0 + " -> " + v.getMaxHealth());
            helper.assertTrue(Math.abs(v.getMaxShield() - shield0 * 1.2F) < 0.01F, "max shield " + shield0 + " -> " + v.getMaxShield());
            double armor = v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            helper.assertTrue(Math.abs(armor - armor0 * 1.2) < 0.01, "armor " + armor0 + " -> " + armor);
            helper.assertTrue(Math.abs(com.automatavalkyries.entity.ai.ValkyrieUnitEffects.powerScale(v) - power0 / 1.2F) < 1.0e-4F, "power cost did not drop");
            helper.assertTrue(Math.abs(com.automatavalkyries.entity.ai.ValkyrieUnitEffects.attackIntervalScale(v) - interval0 / 1.2F) < 1.0e-4F, "attack interval did not shorten");
        } finally {
            v.discard();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "valkyrie", timeoutTicks = 200)
    public static void valkyrieFollowerLimit(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        ValkyrieEntity[] vs = new ValkyrieEntity[ValkyrieEntity.MAX_FOLLOWERS + 1];
        for (int i = 0; i < vs.length; i++) {
            vs[i] = helper.spawn(ModEntities.VALKYRIE.get(), 1 + i, 2, 3);
            vs[i].setOwnerUUID(owner);
        }
        // 新しく作られた区画では mob が世界に入るまで数えられないので、全部入ってから確かめる
        whenLoaded(helper, () -> {
            for (int i = 0; i < ValkyrieEntity.MAX_FOLLOWERS; i++) helper.assertTrue(vs[i].setMode(Mode.FOLLOW), "follower " + i + " refused");
            helper.assertFalse(vs[ValkyrieEntity.MAX_FOLLOWERS].setMode(Mode.FOLLOW), "a 5th valkyrie was allowed to follow");
            helper.assertTrue(vs[0].setMode(Mode.WAIT) && vs[ValkyrieEntity.MAX_FOLLOWERS].setMode(Mode.FOLLOW), "freed slot not reusable");
            for (ValkyrieEntity v : vs) v.releaseAndDiscard();
        }, vs);
    }

    /** HP 0・即死（die / kill / setHealth(0)）は死なずに機能停止。修理で起きる */
    @GameTest(template = TEMPLATE, batch = "valkyrie")
    public static void valkyrieShutsDownInsteadOfDying(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        v.hurt(v.damageSources().generic(), 1000.0F);
        helper.assertTrue(v.isShutdown() && !v.isRemoved() && DirectRead.health(v) > 0, "lethal damage did not shut down: shutdown=" + v.isShutdown() + " removed=" + v.isRemoved());
        helper.assertFalse(v.hurt(v.damageSources().generic(), 5.0F), "a shut-down valkyrie took damage");
        v.repair(ValkyrieEntity.REPAIR);
        helper.assertFalse(v.isShutdown(), "repair did not wake it");
        v.kill();
        helper.assertTrue(v.isShutdown() && !v.isRemoved(), "kill() removed it");
        v.repair(ValkyrieEntity.REPAIR);
        v.setHealth(0.0F);
        helper.assertTrue(v.isShutdown() && DirectRead.health(v) > 0, "setHealth(0) did not shut down");
        v.repair(ValkyrieEntity.REPAIR);
        v.die(v.damageSources().generic());
        helper.assertTrue(v.isShutdown() && !v.isRemoved(), "die() removed it");
        v.releaseAndDiscard();
        helper.succeed();
    }

    /** オメガコア: 即死（kill / die / setHealth(0) / 桁外れのダメージ）を受け付けない。通常のダメージで HP 0 なら機能停止 */
    @GameTest(template = TEMPLATE, batch = "valkyrie")
    public static void valkyrieOmegaCoreIgnoresInstantKills(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        v.setCore(new net.minecraft.world.item.ItemStack(com.automatavalkyries.registry.ModWeapons.CORE_STELLA_GENESIS_OMEGA.get()));
        helper.assertTrue(v.hasOmegaCore(), "omega core not installed");
        float hp = DirectRead.health(v);
        v.kill();
        v.die(v.damageSources().generic());
        v.setHealth(0.0F);
        v.hurt(v.damageSources().generic(), Float.MAX_VALUE);
        helper.assertFalse(v.isShutdown(), "an instant kill shut down an omega-core valkyrie");
        helper.assertTrue(DirectRead.health(v) == hp, "an instant kill changed health: " + DirectRead.health(v));
        v.invulnerableTime = 0;
        v.hurt(v.damageSources().generic(), 500.0F);
        helper.assertTrue(v.isShutdown(), "lethal normal damage did not shut down an omega-core valkyrie");
        v.releaseAndDiscard();
        helper.succeed();
    }

    /** オメガコア: 消去（discard / remove(KILLED) / setRemoved(KILLED)）は断る。保護を通らずに消されたら、バックアップから自動で戻る */
    @GameTest(template = TEMPLATE, batch = "valkyrie", timeoutTicks = 200)
    public static void valkyrieProtectRefusesAndRestores(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        v.setCore(new net.minecraft.world.item.ItemStack(com.automatavalkyries.registry.ModWeapons.CORE_STELLA_GENESIS_OMEGA.get()));
        whenLoaded(helper, () -> refusesAndRestores(helper, v), v);
    }

    /** オメガコア無し: 消去は通り、自動では戻らない（失われた印）。救済通信機（recall）で持ち主のそばへ戻る */
    @GameTest(template = TEMPLATE, batch = "valkyrie", timeoutTicks = 200)
    public static void valkyrieWithoutOmegaIsRecalledByCommunicator(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        UUID owner = UUID.randomUUID();
        v.setOwnerUUID(owner);
        whenLoaded(helper, () -> {
            var server = helper.getLevel().getServer();
            UUID id = v.getUUID();
            ValkyrieProtect.snapshot(v);
            v.discard();
            helper.assertTrue(v.isRemoved(), "erasure of a valkyrie without an omega core was refused");
            for (int i = 0; i < ValkyrieProtect.MISSES_TO_RESTORE + 1; i++) ValkyrieProtect.check(server);
            helper.assertTrue(ValkyrieProtect.findAnywhere(server, id) == null, "a valkyrie without an omega core was restored automatically");
            net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2, 3.5));
            helper.assertTrue(ValkyrieProtect.recall(server, owner, helper.getLevel(), at) == 1, "rescue communicator did not recall it");
            ValkyrieEntity back = ownedBy(helper, owner);
            helper.assertTrue(back != null && back.distanceToSqr(at) < 1.0, "recalled valkyrie is missing or elsewhere");
            helper.assertTrue(!back.getUUID().equals(id) && ValkyrieProtect.isKnown(server, back.getUUID()) && !ValkyrieProtect.isKnown(server, id),
                    "recall did not move the backup to a new UUID");
            helper.assertTrue(ValkyrieProtect.recall(server, owner, helper.getLevel(), at) == 0, "recalled twice");
            back.releaseAndDiscard();
        }, v);
    }

    /** 素体タイプ（2026-10-07 S/H/C 追加）: 型ごとの HP・ノックバック耐性・索敵範囲が入り、保存して読み直しても型と属性が残る。素体ユニットで起こすと持ち主が付く */
    @GameTest(template = TEMPLATE, batch = "valkyrie")
    public static void valkyrieBodyTypesApplyTraits(GameTestHelper helper) {
        ValkyrieEntity h = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        h.setBody(com.automatavalkyries.entity.ValkyrieBody.H);
        helper.assertTrue(h.getMaxHealth() == 60.0F, "Type-H max HP is " + h.getMaxHealth());
        helper.assertTrue(h.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "Type-H is not knockback immune");
        CompoundTag saved = new CompoundTag();
        h.saveWithoutId(saved);
        ValkyrieEntity copy = ModEntities.VALKYRIE.get().create(helper.getLevel());
        copy.load(saved);
        helper.assertTrue(copy.getBody() == com.automatavalkyries.entity.ValkyrieBody.H
                && copy.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "Type-H was not restored on load");
        h.setBody(com.automatavalkyries.entity.ValkyrieBody.C);
        helper.assertTrue(h.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) == 40.0
                && h.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) == 0.0, "Type-C attributes are wrong");
        helper.assertTrue(com.automatavalkyries.entity.ai.ValkyrieTypeTraits.stepPowerScale(h) == 1.0, "Type-C step scale is wrong");
        h.setBody(com.automatavalkyries.entity.ValkyrieBody.S);
        helper.assertTrue(com.automatavalkyries.entity.ai.ValkyrieTypeTraits.stepPowerScale(h) > 1.0, "Type-S step is not longer");
        h.releaseAndDiscard();
        var player = helper.makeMockPlayer();
        ValkyrieEntity woke = com.automatavalkyries.item.ValkyrieBodyItem.spawn(helper.getLevel(), helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2)), 0.0F, com.automatavalkyries.entity.ValkyrieBody.S);
        helper.assertTrue(woke != null && woke.isPodSleeper() && woke.getBody() == com.automatavalkyries.entity.ValkyrieBody.S, "the body unit did not place a sleeping Type-S");
        helper.assertTrue(woke.getHealth() >= woke.getMaxHealth(), "the body unit valkyrie was not at full health: " + woke.getHealth());
        helper.assertTrue(woke.wakeFromPod(player, com.automatavalkyries.item.ProtoCoreItem.CAPACITY) && player.getUUID().equals(woke.getOwnerUUID()), "the body unit valkyrie did not wake for the player");
        woke.releaseAndDiscard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ 段階 2: 装備・シールド・電力・作戦指示

    /** 兵装を 1 つ探す（両手持ちか） */
    private static net.minecraft.world.item.ItemStack weapon(boolean twoHanded) {
        for (var entry : com.automatavalkyries.registry.ModItems.ITEMS.getEntries()) {
            if (entry.get() instanceof com.automatavalkyries.weapon.TacticalWeaponItem w && w.twoHanded() == twoHanded) return new net.minecraft.world.item.ItemStack(w);
        }
        throw new IllegalStateException("no weapon with twoHanded=" + twoHanded);
    }

    /** 装備の枠の決まり: 外装・背部・チップは入らない、両手持ちと左手は取り合う、弾薬庫は素体タイプの枠数まで、保存して読み直しても残る */
    @GameTest(template = TEMPLATE, batch = "valkyrie")
    public static void valkyrieEquipmentRulesAndSave(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        var inv = v.getInventory();
        var ammo = new net.minecraft.world.item.ItemStack(com.automatavalkyries.registry.ModWeapons.AMMO_AP_ROUND.get(), 16);
        helper.assertFalse(inv.accepts(com.automatavalkyries.entity.ValkyrieInventory.HEAD, ammo), "an unimplemented slot accepted an item");
        helper.assertTrue(inv.accepts(com.automatavalkyries.entity.ValkyrieInventory.AMMO, ammo), "ammo was refused by the magazine");
        int last = com.automatavalkyries.entity.ValkyrieInventory.AMMO + v.getBody().ammoSlots;
        helper.assertFalse(inv.accepts(last, ammo), "a magazine slot beyond the body type's count accepted ammo");
        var two = weapon(true);
        var one = weapon(false);
        inv.setItem(com.automatavalkyries.entity.ValkyrieInventory.MAIN_HAND, two);
        helper.assertFalse(inv.accepts(com.automatavalkyries.entity.ValkyrieInventory.OFF_HAND, one), "the off hand accepted a weapon while a two-handed weapon is held");
        inv.setItem(com.automatavalkyries.entity.ValkyrieInventory.MAIN_HAND, one.copy());
        inv.setItem(com.automatavalkyries.entity.ValkyrieInventory.AMMO, ammo);
        inv.setItem(com.automatavalkyries.entity.ValkyrieInventory.CORE, ((com.automatavalkyries.weapon.LuminalCoreItem) com.automatavalkyries.registry.ModWeapons.CORE_BASIC.get()).charged());
        CompoundTag saved = new CompoundTag();
        v.saveWithoutId(saved);
        ValkyrieEntity copy = ModEntities.VALKYRIE.get().create(helper.getLevel());
        copy.load(saved);
        var ci = copy.getInventory();
        helper.assertTrue(ci.getItem(com.automatavalkyries.entity.ValkyrieInventory.MAIN_HAND).is(one.getItem())
                && ci.getItem(com.automatavalkyries.entity.ValkyrieInventory.AMMO).getCount() == 16
                && ci.getItem(com.automatavalkyries.entity.ValkyrieInventory.CORE).is(com.automatavalkyries.registry.ModWeapons.CORE_BASIC.get()), "equipment was not saved");
        helper.assertTrue(Math.abs(copy.carriedWeight() - (((com.automatavalkyries.weapon.TacticalWeaponItem) one.getItem()).weight() + 5.0F)) < 0.01F, "carried weight is wrong: " + copy.carriedWeight());
        v.releaseAndDiscard();
        helper.succeed();
    }

    /** シールドが HP より先にダメージを受ける */
    @GameTest(template = TEMPLATE, batch = "valkyrie")
    public static void valkyrieShieldAbsorbsDamage(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        float hp = com.automatavalkyries.protect.DirectRead.health(v), shield = v.getShield();
        v.hurt(helper.getLevel().damageSources().generic(), 8.0F);
        helper.assertTrue(com.automatavalkyries.protect.DirectRead.health(v) == hp && v.getShield() < shield, "shield did not take the hit: hp=" + com.automatavalkyries.protect.DirectRead.health(v) + " shield=" + v.getShield());
        v.releaseAndDiscard();
        helper.succeed();
    }

    /** 電力 0 で休眠し、コアから補充されると起きる */
    @GameTest(template = TEMPLATE, batch = "valkyrie", timeoutTicks = 200)
    public static void valkyrieSleepsWithoutPowerAndWakesWithCore(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        v.setEnergy(1);
        int[] waited = {0};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    WeaponGameTests.kickStuckChunks(helper, waited, v); // 区画のエンティティが読み込まれないことがある
                    helper.assertTrue(v.isAsleep(), "did not fall asleep at zero power");
                })
                .thenExecute(() -> v.getInventory().setItem(com.automatavalkyries.entity.ValkyrieInventory.CORE,
                        ((com.automatavalkyries.weapon.LuminalCoreItem) com.automatavalkyries.registry.ModWeapons.CORE_BASIC.get()).charged()))
                .thenWaitUntil(() -> helper.assertTrue(!v.isAsleep() && v.getEnergy() > ValkyrieEntity.WAKE_ENERGY, "did not wake after the core recharged her"))
                .thenExecute(v::releaseAndDiscard)
                .thenSucceed();
    }

    /** 作戦指示の値は範囲に収まり、防衛半径・巡回範囲が拠点の範囲に効く */
    @GameTest(template = TEMPLATE, batch = "valkyrie")
    public static void valkyrieOrdersAreClampedAndApplied(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        v.orders.set(com.automatavalkyries.entity.ValkyrieOrders.Field.DEFEND_RADIUS, 9999);
        v.orders.set(com.automatavalkyries.entity.ValkyrieOrders.Field.PATROL_RADIUS, 40);
        v.orders.set(com.automatavalkyries.entity.ValkyrieOrders.Field.POLICY, -5);
        v.setMode(ValkyrieEntity.Mode.DEFEND);
        helper.assertTrue(v.homeRadius == com.automatavalkyries.entity.ValkyrieOrders.Field.DEFEND_RADIUS.max, "defense radius was not clamped/applied: " + v.homeRadius);
        v.setMode(ValkyrieEntity.Mode.PATROL);
        helper.assertTrue(v.homeRadius == 40 && v.orders.policy == com.automatavalkyries.entity.ValkyrieOrders.Policy.AGGRESSIVE, "patrol range/policy not applied");
        v.releaseAndDiscard();
        helper.succeed();
    }

    /** その持ち主の兵姫（呼び出しは新しい UUID で戻るので、持ち主で探す） */
    @org.jetbrains.annotations.Nullable
    private static ValkyrieEntity ownedBy(GameTestHelper helper, UUID owner) {
        for (var e : helper.getLevel().getAllEntities()) {
            if (e instanceof ValkyrieEntity cand && !cand.isRemoved() && owner.equals(cand.getOwnerUUID())) return cand;
        }
        return null;
    }

    /**
     * 壊れた HP（即死 MOD が書いた -Infinity / 0）のバックアップから呼び出すと、オメガコア無しは機能停止（HP 1）で、
     * 入った途端の即死の扱い（死亡モーションのやり直し）にならない（2026-10-05、IMDS で呼び出した兵姫がすぐ止まった報告から）
     */
    @GameTest(template = TEMPLATE, batch = "valkyrie", timeoutTicks = 200)
    public static void valkyrieRecalledFromBrokenHealthBackup(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        UUID owner = UUID.randomUUID();
        v.setOwnerUUID(owner);
        whenLoaded(helper, () -> {
            var server = helper.getLevel().getServer();
            UUID id = v.getUUID();
            ValkyrieProtect.snapshot(v);
            var backups = com.automatavalkyries.protect.valkyrie.ValkyrieBackups.get(server);
            var entry = backups.get(id);
            CompoundTag broken = entry.nbt().copy();
            broken.putFloat("Health", Float.NEGATIVE_INFINITY);
            broken.putBoolean("Shutdown", false);
            v.releaseAndDiscard(); // 体を消す（記録も消える）。壊れた HP の失われた記録を置き直す
            backups.put(id, new com.automatavalkyries.protect.valkyrie.ValkyrieBackups.Entry(broken, entry.dimension(), entry.pos(), owner,
                    entry.time(), false, true));
            net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2, 3.5));
            helper.assertTrue(ValkyrieProtect.recall(server, owner, helper.getLevel(), at) == 1, "could not recall from a broken-health backup");
            ValkyrieEntity back = ownedBy(helper, owner);
            helper.assertTrue(back != null, "recalled valkyrie is missing");
            float hp = com.automatavalkyries.protect.DirectRead.health(back);
            helper.assertTrue(Float.isFinite(hp) && hp > 0.0F && back.isShutdown(), "broken health was not sanitized: hp=" + hp + " shutdown=" + back.isShutdown());
            back.releaseAndDiscard();
        }, v);
    }

    private static void refusesAndRestores(GameTestHelper helper, ValkyrieEntity v) {
        v.discard();
        v.remove(Entity.RemovalReason.KILLED);
        v.setRemoved(Entity.RemovalReason.KILLED);
        helper.assertFalse(v.isRemoved(), "removal was not refused");
        UUID id = v.getUUID();
        v.setExpression(4);
        ValkyrieProtect.snapshot(v);
        v.eraseBypassingProtectForTest();
        helper.assertTrue(ValkyrieProtect.findAnywhere(helper.getLevel().getServer(), id) == null, "bypass erasure did not remove it");
        for (int i = 0; i < ValkyrieProtect.MISSES_TO_RESTORE; i++) ValkyrieProtect.check(helper.getLevel().getServer());
        ValkyrieEntity back = ValkyrieProtect.findAnywhere(helper.getLevel().getServer(), id);
        helper.assertTrue(back != null, "valkyrie was not restored from backup");
        helper.assertTrue(back.expressionName().equals("sad"), "restored data is off: " + back.expressionName());
        back.releaseAndDiscard();
        helper.assertFalse(ValkyrieProtect.isKnown(helper.getLevel().getServer(), id), "release kept the backup");
    }

    /**
     * オートマタヴァルキリー protect のエージェント（-PwithValkyrieProtect）: vanilla の Entity / LivingEntity を変換し、
     * 入口の問い合わせで兵姫の消去を断り、即死を機能停止に置き換える。兵姫でない mob には触らない。エージェントが無ければ何もしない
     */
    @GameTest(template = TEMPLATE, batch = "valkyrie", timeoutTicks = 200)
    public static void valkyrieProtectAgentGuardsEntryPoints(GameTestHelper helper) {
        if (!com.automatavalkyries.protect.valkyrie.ValkyrieAgentLink.isInstalled()) {
            helper.succeed();
            return;
        }
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new net.minecraft.core.BlockPos(3, 2, 1));
        whenLoaded(helper, () -> {
            String status = com.automatavalkyries.protect.valkyrie.ValkyrieAgentLink.status();
            helper.assertTrue(status.contains("guarded 2 class(es)"), "agent did not guard Entity and LivingEntity: " + status);
            try {
                Class<?> hooks = Class.forName("com.automatavalkyries.valkyrieprotect.hooks.ValkyrieHooks", false, null);
                java.lang.reflect.Method refuse = hooks.getMethod("refuse", Object.class, String.class, Object.class);
                helper.assertFalse((Boolean) refuse.invoke(null, zombie, "setRemoved", Entity.RemovalReason.KILLED), "agent hook touched a zombie");
                // オメガコア無し: 即死は機能停止に置き換えて打ち切る
                helper.assertTrue((Boolean) refuse.invoke(null, v, "die", null) && v.isShutdown(), "agent hook did not turn die into shutdown");
                v.repair(ValkyrieEntity.REPAIR);
                // オメガコア: 消去を断り、即死は何もしないで打ち切る
                v.setCore(new net.minecraft.world.item.ItemStack(com.automatavalkyries.registry.ModWeapons.CORE_STELLA_GENESIS_OMEGA.get()));
                helper.assertTrue((Boolean) refuse.invoke(null, v, "setRemoved", Entity.RemovalReason.KILLED), "agent hook let an omega-core valkyrie be removed");
                helper.assertTrue((Boolean) refuse.invoke(null, v, "kill", null) && !v.isShutdown(), "agent hook shut down an omega-core valkyrie");
            } catch (ReflectiveOperationException e) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("could not reach the agent hooks: " + e);
            }
            zombie.discard();
            v.releaseAndDiscard();
        }, v);
    }

    /** 超抹殺の棒（抹殺の棒の全手段＋深い追い出し＋同じ個体の再参加拒否）で消された兵姫（オメガコア無し）も、救済通信機で呼び出せる */
    @GameTest(template = TEMPLATE, batch = "valkyrie", timeoutTicks = 200)
    public static void valkyrieRecalledAfterSuperKillStick(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        UUID owner = UUID.randomUUID();
        v.setOwnerUUID(owner);
        whenLoaded(helper, () -> {
            var server = helper.getLevel().getServer();
            UUID id = v.getUUID();
            ValkyrieProtect.snapshot(v);
            com.automatavalkyries.item.DebugKillStickItem.annihilate(helper.getLevel(), null, v);
            com.automatavalkyries.item.SuperKillStickItem.evict(helper.getLevel(), v);
            com.automatavalkyries.item.SuperKillStickItem.refuseRejoin(v);
            for (int i = 0; i < ValkyrieProtect.MISSES_TO_RESTORE + 1; i++) ValkyrieProtect.check(server);
            var entry = com.automatavalkyries.protect.valkyrie.ValkyrieBackups.get(server).get(id);
            helper.assertTrue(entry != null, "backup entry is gone after the super kill stick");
            helper.assertTrue(entry.lost(), "backup entry was not marked lost (found=" + ValkyrieProtect.findAnywhere(server, id) + ")");
            net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2, 3.5));
            int n = ValkyrieProtect.recall(server, owner, helper.getLevel(), at);
            ValkyrieEntity back = ownedBy(helper, owner);
            helper.assertTrue(n == 1 && back != null, "recall after the super kill stick failed: recalled=" + n + " back=" + back);
            back.releaseAndDiscard();
        }, v);
    }

    /** 救済通信機の一覧と 1 体の呼び出し: 持ち主のいない失われた兵姫は一覧に出て、呼び出すと自分の登録になる。登録は 100 体まで */
    @GameTest(template = TEMPLATE, batch = "valkyrie", timeoutTicks = 200)
    public static void valkyrieCommunicatorListsAndRecallsOne(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        whenLoaded(helper, () -> {
            var server = helper.getLevel().getServer();
            net.minecraft.server.level.ServerPlayer player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "rescue_tester"));
            player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2, 3.5)));
            UUID id = v.getUUID();
            ValkyrieProtect.snapshot(v);
            v.discard(); // 持ち主なし・オメガコア無し: 消去は通り、失われた印
            helper.assertTrue(ValkyrieProtect.summaries(player).stream().anyMatch(sm -> sm.id().equals(id) && !sm.owned()
                    && sm.state() == ValkyrieProtect.Summary.LOST), "unclaimed lost valkyrie is not listed");
            helper.assertTrue(ValkyrieProtect.recallOne(player, id).equals("recalled"), "could not recall the unclaimed valkyrie");
            ValkyrieEntity back = ownedBy(helper, player.getUUID());
            helper.assertTrue(back != null && player.getUUID().equals(back.getOwnerUUID()), "recalled valkyrie is not registered to the player");
            helper.assertTrue(!ValkyrieProtect.recallOne(player, id).equals("recalled"), "the old UUID could be recalled again");
            helper.assertTrue(ValkyrieProtect.recallOne(player, back.getUUID()).equals("not_lost"), "an active valkyrie was recalled again");
            // 登録の上限: 100 体分の記録を持たせると、持ち主のいない兵姫はもう呼び出せない
            var backups = com.automatavalkyries.protect.valkyrie.ValkyrieBackups.get(server);
            java.util.List<UUID> fillers = new java.util.ArrayList<>();
            for (int i = ValkyrieProtect.ownedCount(server, player.getUUID()); i < ValkyrieProtect.MAX_REGISTERED; i++) {
                UUID f = UUID.randomUUID();
                fillers.add(f);
                backups.put(f, new com.automatavalkyries.protect.valkyrie.ValkyrieBackups.Entry(new CompoundTag(), helper.getLevel().dimension(),
                        net.minecraft.world.phys.Vec3.ZERO, player.getUUID(), 0, false, false));
            }
            ValkyrieEntity other = ModEntities.VALKYRIE.get().create(helper.getLevel());
            other.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 3.5)));
            helper.getLevel().addFreshEntity(other);
            ValkyrieProtect.snapshot(other);
            other.discard();
            String full = ValkyrieProtect.recallOne(player, other.getUUID());
            for (UUID f : fillers) ValkyrieProtect.release(server, f);
            ValkyrieProtect.release(server, other.getUUID());
            back.releaseAndDiscard();
            helper.assertTrue(full.equals("too_many"), "registry limit not enforced: " + full);
        }, v);
    }

    /**
     * 抹殺の仕組み（超絶抹殺の棒・終焉の魔杖が使う annihilate/AnnihilationExecutionAgent）で消された兵姫は、同じ UUID の参加を拒否される。
     * 救済通信機は新しい UUID の体にデータを移して呼び出す（2026-10-04 ユーザーの報告「超絶抹殺と終焉の魔杖は呼び出せなかった」）
     */
    @GameTest(template = TEMPLATE, batch = "valkyrie_annihilate", timeoutTicks = 200)
    public static void valkyrieRecalledAfterAnnihilation(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        var server = helper.getLevel().getServer();
        net.minecraft.server.level.ServerPlayer player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "annihilate_tester"));
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2, 3.5)));
        UUID id = v.getUUID();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getEntity(id) == v, "not in the world yet"))
                .thenExecute(() -> {
                    v.setOwnerUUID(player.getUUID());
                    v.setExpression(2);
                    ValkyrieProtect.snapshot(v);
                    com.automatavalkyries.annihilate.AnnihilationExecutionAgent.annihilateEntity(helper.getLevel(), player, v);
                    for (int i = 0; i < ValkyrieProtect.MISSES_TO_RESTORE + 1; i++) ValkyrieProtect.check(server);
                    helper.assertTrue(ValkyrieProtect.findAnywhere(server, id) == null, "annihilated valkyrie is still in the world");
                })
                // 抹殺の直後は同じ種類の出現が近くで少しの間禁止される（annihilate/SpawnBanZone）。それが明けてから呼び出す
                .thenIdle(com.automatavalkyries.annihilate.SpawnBanZone.DURATION_TICKS + 5)
                .thenExecute(() -> {
                    String result = ValkyrieProtect.recallOne(player, id);
                    helper.assertTrue(result.equals("recalled"), "recall after annihilation failed: " + result);
                    ValkyrieEntity back = null;
                    for (var e : helper.getLevel().getAllEntities()) {
                        if (e instanceof ValkyrieEntity cand && player.getUUID().equals(cand.getOwnerUUID())) back = cand;
                    }
                    helper.assertTrue(back != null && back.expressionName().equals("shy"), "recalled valkyrie is missing or lost its data");
                    helper.assertTrue(!back.getUUID().equals(id) && ValkyrieProtect.isKnown(server, back.getUUID()) && !ValkyrieProtect.isKnown(server, id),
                            "backup was not moved to the new UUID");
                    back.releaseAndDiscard();
                })
                .thenSucceed();
    }

    /** 救済通信機でバックアップを破棄: 消去された兵姫は二度と呼び出せず、稼働中の兵姫は保護を外して消える。ほかの人の兵姫は破棄できない */
    @GameTest(template = TEMPLATE, batch = "valkyrie", timeoutTicks = 200)
    public static void valkyrieBackupCanBeDiscarded(GameTestHelper helper) {
        ValkyrieEntity lost = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        ValkyrieEntity active = helper.spawn(ModEntities.VALKYRIE.get(), 3, 2, 1);
        ValkyrieEntity others = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 3);
        whenLoaded(helper, () -> {
            var server = helper.getLevel().getServer();
            net.minecraft.server.level.ServerPlayer player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "discard_tester"));
            lost.setOwnerUUID(player.getUUID());
            active.setOwnerUUID(player.getUUID());
            others.setOwnerUUID(UUID.randomUUID());
            for (ValkyrieEntity v : new ValkyrieEntity[]{lost, active, others}) ValkyrieProtect.snapshot(v);
            UUID lostId = lost.getUUID(), activeId = active.getUUID(), othersId = others.getUUID();
            lost.discard();
            helper.assertTrue(ValkyrieProtect.discardBackup(player, lostId).equals("discarded"), "could not discard a lost valkyrie's backup");
            helper.assertFalse(ValkyrieProtect.isKnown(server, lostId), "lost valkyrie's backup still exists");
            helper.assertTrue(ValkyrieProtect.recallOne(player, lostId).equals("failed"), "a discarded backup could be recalled");
            helper.assertTrue(ValkyrieProtect.discardBackup(player, activeId).equals("discarded"), "could not discard an active valkyrie's backup");
            helper.assertTrue(active.isRemoved() && ValkyrieProtect.findAnywhere(server, activeId) == null, "active valkyrie was not dismissed");
            helper.assertTrue(ValkyrieProtect.discardBackup(player, othersId).equals("not_yours"), "someone else's backup was discarded");
            others.releaseAndDiscard();
        }, lost, active, others);
    }
}
