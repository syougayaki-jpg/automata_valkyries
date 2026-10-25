package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorArts;
import com.automatavalkyries.entity.ElenorPressure;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** エレノアの 3 フェーズの戦い方（2026-10-22） */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ElenorGameTests {

    private static final String TEMPLATE = "empty";

    /** 凶悪度の分け方: 即死 MOD 専用の対策は操作の名前で分け、ブラックリストの MOD は一段だけ上げる */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void viciousSeverityByOperation(GameTestHelper helper) {
        helper.assertTrue(ElenorPressure.classify("compat:omnimobs:erase", false) == ElenorPressure.Vicious.ERASURE, "erase should be IV");
        helper.assertTrue(ElenorPressure.classify("compat:omnimobs:instantKill", false) == ElenorPressure.Vicious.DEATH, "instantKill should be III");
        helper.assertTrue(ElenorPressure.classify("compat:omnimobs:setHealth", false) == ElenorPressure.Vicious.LETHAL, "setHealth should be II");
        helper.assertTrue(ElenorPressure.classify("compat:nosugar:timeStop", false) == ElenorPressure.Vicious.TAMPER, "time stop should be V");
        helper.assertTrue(ElenorPressure.classify("damage_penalty", true) == ElenorPressure.Vicious.LETHAL, "a blacklisted ordinary hit should be one step up (II)");
        helper.assertTrue(ElenorPressure.classify("compat:omnimobs:erase", true) == ElenorPressure.Vicious.TAMPER, "a blacklisted erase should be one step up (V)");
        helper.assertTrue(ElenorPressure.classify("retreat", false) == null, "retreat is not counted");
        helper.succeed();
    }

    /** 未知のダメージ属性: データで作った 4 種が読み込まれ、最果てはすべての貫通のタグに入っている */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void unknownDamageTypesBypass(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
        for (String name : new String[]{"elenor_hollow", "elenor_ribbon", "elenor_afterimage", "elenor_farthest"}) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, name));
            helper.assertTrue(registry.getHolder(key).isPresent(), name + " must be registered");
        }
        var farthest = new net.minecraft.world.damagesource.DamageSource(registry.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DAMAGE_TYPE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "elenor_farthest"))));
        helper.assertTrue(farthest.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                && farthest.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)
                && farthest.is(net.minecraft.tags.DamageTypeTags.BYPASSES_EFFECTS)
                && farthest.is(net.minecraft.tags.DamageTypeTags.BYPASSES_RESISTANCE)
                && farthest.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ENCHANTMENTS)
                && farthest.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD),
                "elenor_farthest must be in every bypass tag");
        helper.succeed();
    }

    /** 第 2 は HP 60% 以下、第 3 は 30% 以下。最果てのエレノアは技を使わない */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void phasesFollowHealth(GameTestHelper helper) {
        helper.assertTrue(ElenorArts.phaseFor(1.0F) == 1 && ElenorArts.phaseFor(0.61F) == 1, "phase 1 range");
        helper.assertTrue(ElenorArts.phaseFor(0.6F) == 2 && ElenorArts.phaseFor(0.31F) == 2, "phase 2 range");
        helper.assertTrue(ElenorArts.phaseFor(0.3F) == 3 && ElenorArts.phaseFor(0.0F) == 3, "phase 3 range");
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(3, 2, 3));
        var farthest = helper.spawnWithNoFreeWill(ModEntities.FARTHEST_ELENOR.get(), new BlockPos(5, 2, 5));
        helper.assertTrue(elenor.arts() != null, "Elenor has no arts");
        helper.assertTrue(farthest.arts() != null && farthest.arts().fullPower(), "Farthest Elenor must fight at full power from the start");
        helper.assertTrue(farthest.arts().pressure().tier(helper.makeMockPlayer()) == ElenorPressure.MAX_TIER, "Farthest Elenor must start at tier 7");
        elenor.retreat();
        farthest.retreat();
        helper.succeed();
    }

    /** 標的（ダメージを受けないモックのプレイヤー）に向けて技を回し続けても落ちず、エレノアは生きている */
    @GameTest(template = TEMPLATE, batch = "elenor", timeoutTicks = 400)
    public static void artsRunWithoutErrors(GameTestHelper helper) {
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(3, 2, 3));
        var player = helper.makeMockPlayer();
        elenor.setTarget(player);
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (elenor.arts() != null && ticks[0]++ < 300) {
                elenor.arts().tick(player);
                elenor.arts().tickProjectiles();
            }
        });
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(elenor.isAlive(), "Elenor died while running arts");
            elenor.retreat();
            helper.succeed();
        });
    }

    /**
     * 全部の技（新しい舞台装置の技と幕の大技を含む）を、1 体ずつ別のエレノアに、生きた標的（ゾンビ）へ向けて使わせても落ちない。
     * 舞台装置の技は仕掛けを置き、終わったら宙に浮いたままにならない（2026-10-24）
     */
    @GameTest(template = TEMPLATE, batch = "elenor_moves", timeoutTicks = 400)
    public static void everyMoveRunsWithoutErrors(GameTestHelper helper) {
        java.util.List<String> names = ElenorArts.testMoveNames();
        java.util.List<com.automatavalkyries.entity.ElenorEntity> elenors = new java.util.ArrayList<>();
        java.util.List<net.minecraft.world.entity.monster.Zombie> targets = new java.util.ArrayList<>();
        java.util.Set<String> placedProps = new java.util.HashSet<>();
        for (int i = 0; i < names.size(); i++) {
            var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(1 + i % 5 * 2, 2, 1 + i / 5 * 2));
            var zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(1 + i % 5 * 2, 2, 2 + i / 5 * 2));
            var health = zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (health != null) health.setBaseValue(1000.0D); // 技が終わるまで倒れない標的
            zombie.setHealth(1000.0F);
            elenor.setTarget(zombie);
            elenor.arts().testPerform(names.get(i));
            elenors.add(elenor);
            targets.add(zombie);
        }
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (ticks[0]++ >= 150) return;
            for (int i = 0; i < elenors.size(); i++) {
                var elenor = elenors.get(i);
                var zombie = targets.get(i);
                if (elenor.arts() == null || !elenor.isAlive()) continue;
                if (zombie.isAlive()) elenor.arts().tick(zombie);
                elenor.arts().tickProjectiles(); // 実体が Tick されない枠でも、舞台装置と止まった技の後始末を進める
                if (elenor.arts().testPropCount() > 0) placedProps.add(names.get(i));
            }
        });
        helper.runAfterDelay(160, () -> {
            StringBuilder floating = new StringBuilder();
            for (int i = 0; i < elenors.size(); i++) {
                var elenor = elenors.get(i);
                helper.assertTrue(elenor.isAlive(), "Elenor died while running " + names.get(i));
                if (elenor.isNoGravity()) floating.append(names.get(i)).append("->").append(elenor.arts().testMoveName()).append(' ');
            }
            helper.assertTrue(floating.length() == 0, "Elenor stayed floating after a move: " + floating);
            for (String prop : new String[]{"SPOTLIGHT", "FLY_DROP", "MARIONETTE", "TRAPDOOR", "MIE", "CHORDS", "OVERTURE"}) {
                helper.assertTrue(placedProps.contains(prop), prop + " did not place its stage props");
            }
            for (var zombie : targets) zombie.discard();
            StringBuilder left = new StringBuilder();
            for (int i = 0; i < elenors.size(); i++) {
                elenors.get(i).retreat();
                if (!elenors.get(i).isRemoved()) left.append(names.get(i)).append(' ');
            }
            helper.assertTrue(left.length() == 0, "Elenor did not leave after the test: " + left);
            helper.succeed();
        });
    }

    /** ティアごとの演出（着弾・炸裂の飾り・構えの気配・リボン）が、ティア 0〜7 のどれでも落ちず、色が段々深くなる */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void tierEffectsRunAtEveryTier(GameTestHelper helper) {
        var level = helper.getLevel();
        var at = net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        var dir = new net.minecraft.world.phys.Vec3(1, 0, 0);
        for (int tier = 0; tier <= 7; tier++) {
            com.automatavalkyries.entity.ElenorFx.impact(level, at, tier);
            com.automatavalkyries.entity.ElenorFx.tierFlourish(level, at, tier, 2.0);
            com.automatavalkyries.entity.ElenorFx.tierAura(level, at, tier);
            com.automatavalkyries.entity.ElenorFx.ribbonStrand(level, at, at.add(dir), tier * 3, tier);
            com.automatavalkyries.entity.ElenorFx.ribbonWhip(level, at, dir, 3.0, 1, tier);
            com.automatavalkyries.entity.ElenorFx.ribbonArc(level, at, dir, 2.5, 1.0, tier);
            com.automatavalkyries.entity.ElenorFx.ribbonBurst(level, at, 6, 0.3, tier);
        }
        helper.assertTrue(com.automatavalkyries.entity.ElenorFx.pal(0, 0) != com.automatavalkyries.entity.ElenorFx.pal(7, 0), "tier 7 must not look like tier 0");
        helper.assertTrue(com.automatavalkyries.entity.ElenorFx.pal(7, 5) == com.automatavalkyries.entity.ElenorFx.tierColor(7), "palette must clamp at tier 7");
        helper.succeed();
    }

    /** 足切りの二分探索: 通らなかった量と通った量の間を半分ずつ詰め、境目が分かれば少し上で撃つ。足切りより大きい攻撃はそのまま */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void floorSearchBisects(GameTestHelper helper) {
        float none = com.automatavalkyries.entity.ElenorAdapt.aboveFloor(6.0F, 0.0F, Float.MAX_VALUE, false);
        helper.assertTrue(none == 6.0F, "no floor known must keep the amount: " + none);
        float jump = com.automatavalkyries.entity.ElenorAdapt.aboveFloor(6.0F, 6.0F, Float.MAX_VALUE, false);
        helper.assertTrue(Math.abs(jump - 9.6F) < 1.0e-3F, "first failure must jump to 1.6x: " + jump);
        float mid = com.automatavalkyries.entity.ElenorAdapt.aboveFloor(6.0F, 6.0F, 10.0F, false);
        helper.assertTrue(Math.abs(mid - 8.0F) < 1.0e-3F, "between fail and pass must bisect: " + mid);
        float known = com.automatavalkyries.entity.ElenorAdapt.aboveFloor(6.0F, 7.0F, 7.5F, true);
        helper.assertTrue(Math.abs(known - 7.65F) < 1.0e-3F, "known floor must shoot just above the lowest pass: " + known);
        float big = com.automatavalkyries.entity.ElenorAdapt.aboveFloor(20.0F, 7.0F, 7.5F, true);
        helper.assertTrue(big == 20.0F, "an attack above the floor must stay as is: " + big);
        helper.succeed();
    }

    /** ティア 4 の割合ダメージ: 普通の相手には足さず、HP を盛った相手にだけ最大 HP の 3% を足し、1 発の上限で止まる */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void percentDamageOnlyForBloatedHealth(GameTestHelper helper) {
        float normal = com.automatavalkyries.entity.ElenorAdapt.percentBonus(6.0F, 20.0F);
        helper.assertTrue(normal == 0.0F, "a 20 HP target must not get percent damage: " + normal);
        float bloated = com.automatavalkyries.entity.ElenorAdapt.percentBonus(6.0F, 5000.0F);
        helper.assertTrue(Math.abs(bloated - 150.0F) < 1.0e-3F, "5000 HP must get 3% (150): " + bloated);
        float huge = com.automatavalkyries.entity.ElenorAdapt.percentBonus(6.0F, 1.0e9F);
        helper.assertTrue(huge == com.automatavalkyries.entity.ElenorAdapt.PERCENT_CAP, "the bonus must stop at the per-hit cap: " + huge);
        float broken = com.automatavalkyries.entity.ElenorAdapt.percentBonus(6.0F, Float.NaN);
        helper.assertTrue(broken == 0.0F, "a broken max health must not add damage: " + broken);
        helper.succeed();
    }

    /** ティア 1〜4 の調べて合わせる攻め方を、生きた標的に何度も当てても落ちず、削れて、調べた結果が残る */
    @GameTest(template = TEMPLATE, batch = "elenor", timeoutTicks = 200)
    public static void adaptiveStrikesProbeAndDamage(GameTestHelper helper) {
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(3, 2, 3));
        var pressure = elenor.arts().pressure();
        java.util.List<net.minecraft.world.entity.monster.Zombie> zombies = new java.util.ArrayList<>();
        for (int tier = 1; tier <= 4; tier++) {
            var zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(1 + tier, 2, 1));
            var health = zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (health != null) health.setBaseValue(1000.0D);
            zombie.setHealth(1000.0F);
            for (int i = 0; i < tier; i++) pressure.boost(zombie);
            zombies.add(zombie);
        }
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (ticks[0]++ >= 80 || ticks[0] % 4 != 0) return;
            for (var zombie : zombies) if (zombie.isAlive()) pressure.strike(zombie, 6.0F);
        });
        helper.runAfterDelay(100, () -> {
            for (int i = 0; i < zombies.size(); i++) {
                var zombie = zombies.get(i);
                helper.assertTrue(pressure.tier(zombie) >= i + 1, "tier " + (i + 1) + " was not set: " + pressure.tier(zombie));
                helper.assertTrue(zombie.getHealth() < 1000.0F, "tier " + (i + 1) + " strikes did no damage");
                helper.assertTrue(!pressure.describeAdapt(zombie.getUUID()).isEmpty(), "tier " + (i + 1) + " left no probe profile");
                zombie.discard();
            }
            elenor.retreat();
            helper.succeed();
        });
    }

    /**
     * 最果てのエレノアの反撃: モブのゴールの Tick の中から殴られても、その場では撃たず（ゴールの一覧を回している途中に消して落ちない）、
     * 自分の次の Tick で撃つ（2026-10-24。モブジラが殴ったとき ConcurrentModificationException で落ちていた）
     */
    @GameTest(template = TEMPLATE, batch = "elenor", timeoutTicks = 100)
    public static void farthestRetaliationFromInsideGoalTickDoesNotCrash(GameTestHelper helper) {
        var farthest = helper.spawnWithNoFreeWill(ModEntities.FARTHEST_ELENOR.get(), new BlockPos(2, 2, 2));
        var zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        zombie.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.Goal() {
            @Override
            public boolean canUse() {
                return true;
            }

            @Override
            public void tick() { // ゴールの Tick の中から殴る（モブジラの近接と同じ道）
                farthest.hurt(helper.getLevel().damageSources().mobAttack(zombie), 5.0F);
            }

            @Override
            public boolean requiresUpdateEveryTick() {
                return true;
            }
        });
        zombie.goalSelector.tick(); // ここで ConcurrentModificationException が出ないこと
        zombie.goalSelector.tick();
        farthest.baseTick(); // 次の自分の Tick で反撃する
        helper.assertTrue(zombie.isRemoved() || !zombie.isAlive(), "the deferred counterattack did not eliminate the attacker");
        farthest.getEprotect().triggerDespawn();
        helper.succeed();
    }

    /** 打ち上げからの復帰: ワールドの上限よりはるか上へ飛ばされたら、その場で足場へ戻る（皆殺しの剣で y=5761 へ飛ばされて消えた。2026-10-24） */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void elenorReturnsWhenThrownAboveTheWorld(GameTestHelper helper) {
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        var level = helper.getLevel();
        elenor.setPos(elenor.getX(), level.getMaxBuildHeight() + 5000, elenor.getZ());
        elenor.setDeltaMovement(0, 300, 0);
        elenor.baseTick();
        helper.assertTrue(elenor.getY() <= level.getMaxBuildHeight(), "Elenor was left above the world: y=" + elenor.getY());
        helper.assertTrue(elenor.getDeltaMovement().lengthSqr() < 1.0, "the launch speed was not cancelled: " + elenor.getDeltaMovement());
        elenor.retreat();
        helper.succeed();
    }

    /**
     * 最大 HP を他 MOD に 1 へ下げられた Tick に致死の 1 撃を受けても、正規の撃破と見なさず、最大 HP と切り詰められた HP を戻す
     * （2026-10-24。Madness・DOOM が最大 HP を 1 にした Tick に、クルーシブルの連打・皆殺しの剣で倒されていた）
     */
    @GameTest(template = TEMPLATE, batch = "elenor", timeoutTicks = 100)
    public static void loweredMaxHealthDoesNotAllowDefeat(GameTestHelper helper) {
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        helper.runAfterDelay(3, () -> {
            elenor.getEprotect().tick(); // 手つかずの HP を控えさせる
            var attribute = elenor.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    java.util.UUID.randomUUID(), "foreign_test_shrink", -499.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            elenor.setHealth(elenor.getHealth()); // vanilla と同じく、下げた最大 HP まで切り詰める
            elenor.hurt(helper.getLevel().damageSources().generic(), 5.0F);
            helper.assertTrue(elenor.isAlive() && elenor.deathTime == 0, "a lowered max health let a single hit count as a legitimate defeat");
            elenor.getEprotect().tick(); // 次の保護の Tick で戻る（致死に届いた Tick は、死亡の入口でその場で戻す）
            helper.assertTrue(elenor.getMaxHealth() >= 499.0F, "the lowered max health was not restored: " + elenor.getMaxHealth());
            helper.assertTrue(com.automatavalkyries.protect.DirectRead.health(elenor) > 100.0F, "the clamped health was not restored: " + com.automatavalkyries.protect.DirectRead.health(elenor));
            elenor.retreat();
            helper.succeed();
        });
    }

    /**
     * 撃破が認められていないのに撃破の演出（tickDeath）を回されても、演出を進めず、復元の記録も消さない（2026-10-25。The King Sword は
     * LivingEntity.tick の先頭で dead を立てて deathTime を進め、HP を 0 に書くので、演出が最後まで進んで消えていた）
     */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void forcedDeathAnimationDoesNotFinishElenor(GameTestHelper helper) {
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        var accessor = (com.automatavalkyries.mixin.LivingEntityAccessor) elenor;
        for (int i = 0; i < com.automatavalkyries.entity.ElenorEntity.DEATH_TICKS + 10; i++) {
            accessor.automata_valkyries$setDead(true);
            elenor.deathTime++;
            accessor.automata_valkyries$tickDeath();
        }
        helper.assertTrue(!elenor.isRemoved(), "a forced death animation removed Elenor");
        helper.assertTrue(elenor.deathTime == 0, "the forced death animation kept running: deathTime=" + elenor.deathTime);
        helper.assertTrue(elenor.getEprotect().isProtectionActive() && !elenor.getEprotect().isDying(), "the forced death animation ended the protection");
        // 演出の終わりだけを直に呼ばれても、復元の記録を消さない
        elenor.getEprotect().finishDeath();
        elenor.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
        helper.assertTrue(!elenor.isRemoved(), "finishDeath without an accepted defeat let Elenor be removed");
        // HP を 0 に書かれた Tick でも倒れかけと答えない（上書きした isDeadOrDying で vanilla が演出を回していた）
        elenor.getEprotect().tick();
        float before = com.automatavalkyries.protect.DirectRead.health(elenor);
        elenor.getEntityData().set(com.automatavalkyries.mixin.LivingEntityAccessor.automata_valkyries$getDataHealthId(), 0.0F);
        helper.assertTrue(!elenor.isDeadOrDying() && elenor.isAlive(), "a health written to 0 made Elenor look dead");
        // 手元への「消した」知らせから抜く相手か: 近くのプレイヤーには抜き、遠く・退場後は抜かない（EprotectRemovePacketMixin）
        var viewer = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "remove-packet-viewer")); // ワールドには入れない
        viewer.setPos(elenor.getX() + 4, elenor.getY(), elenor.getZ());
        var manager = com.automatavalkyries.protect.EprotectManager.getInstance();
        helper.assertTrue(manager.findVisibleTo(viewer, elenor.getId()) == elenor.getEprotect(), "a removal packet to a nearby player would not be filtered");
        viewer.setPos(elenor.getX() + 4000, elenor.getY(), elenor.getZ());
        helper.assertTrue(manager.findVisibleTo(viewer, elenor.getId()) == null, "a removal packet to a far player would be filtered");
        viewer.setPos(elenor.getX() + 4, elenor.getY(), elenor.getZ());
        // die を通さずに死亡イベントだけ投げられても断り、HP は最大まで戻さず、削られていた値へ戻す
        var event = new net.minecraftforge.event.entity.living.LivingDeathEvent(elenor, helper.getLevel().damageSources().generic());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
        helper.assertTrue(event.isCanceled(), "a foreign death event was not cancelled");
        float after = com.automatavalkyries.protect.DirectRead.health(elenor);
        helper.assertTrue(after > 0.0F && after <= Math.max(before, 1.0F), "a refused death event restored the wrong health: " + before + " -> " + after);
        elenor.retreat();
        helper.assertTrue(manager.findVisibleTo(viewer, elenor.getId()) == null, "a removal packet after a retreat would be filtered");

        // tickDeath を上書きするほかのボス（メメントモリ）と、上書きしない個体（vanilla の tickDeath の Mixin）でも同じ
        var mori = helper.spawnWithNoFreeWill(ModEntities.MEMENTO_MORI.get(), new BlockPos(4, 2, 4));
        var dummy = helper.spawnWithNoFreeWill(ModEntities.PROTECT_DUMMY_ZOMBIE.get(), new BlockPos(1, 2, 4));
        for (var boss : java.util.List.<net.minecraft.world.entity.LivingEntity>of(mori, dummy)) {
            var acc = (com.automatavalkyries.mixin.LivingEntityAccessor) boss;
            for (int i = 0; i < 200; i++) {
                acc.automata_valkyries$setDead(true);
                boss.deathTime++;
                acc.automata_valkyries$tickDeath();
            }
            helper.assertTrue(!boss.isRemoved() && boss.deathTime == 0, "a forced death animation ran on " + boss.getType() + ": deathTime=" + boss.deathTime);
        }
        mori.getEprotect().triggerDespawn();
        dummy.getEprotect().triggerDespawn();

        // 同期データから HP の項目そのものを抜かれても落ちず、健全な値で作り直す（The King Sword と組んで落ちた。2026-10-26）
        var stripped = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(1, 2, 1));
        stripped.getEprotect().tick();
        var items = ((com.automatavalkyries.mixin.SynchedEntityDataAccessor) stripped.getEntityData()).automata_valkyries$getItemsById();
        items.remove(com.automatavalkyries.mixin.LivingEntityAccessor.automata_valkyries$getDataHealthId().getId());
        float read = stripped.getHealth();
        stripped.setHealth(read);
        stripped.getEprotect().tick();
        helper.assertTrue(read > 0.0F && stripped.isAlive() && com.automatavalkyries.protect.DirectRead.health(stripped) > 0.0F,
                "a missing health entry was not put back: " + read);
        stripped.retreat();

        // 撃破を認めた後の vanilla の撃破の演出は、20 Tick で終わって消える（消去を断られ続けて煙を出し続けない）
        var fallen = helper.spawnWithNoFreeWill(ModEntities.PROTECT_DUMMY_ZOMBIE.get(), new BlockPos(4, 2, 1));
        fallen.getEprotect().markDefeated();
        fallen.setHealth(0.0F);
        var fallenAcc = (com.automatavalkyries.mixin.LivingEntityAccessor) fallen;
        for (int i = 0; i < 25 && !fallen.isRemoved(); i++) fallenAcc.automata_valkyries$tickDeath();
        helper.assertTrue(fallen.isRemoved(), "a defeated entity with the vanilla death animation was not removed: deathTime=" + fallen.deathTime);

        // UUID・ID を乱数に書き換えられ、追跡から外されても戻る（The King Sword の EntityChunkMapEraser。リログで複製になっていた）
        var marked = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 6));
        var fields = (com.automatavalkyries.mixin.EntityAccessor) marked;
        java.util.UUID uuid = fields.automata_valkyries$getUuidField();
        int id = fields.automata_valkyries$getIdField();
        var chunkMap = (com.automatavalkyries.mixin.ChunkMapAccessor) helper.getLevel().getChunkSource().chunkMap;
        boolean wasTracked = chunkMap.automata_valkyries$getEntityMap().containsKey(id);
        chunkMap.automata_valkyries$getEntityMap().remove(id);
        fields.automata_valkyries$setUuidField(java.util.UUID.randomUUID());
        fields.automata_valkyries$setIdField(7_000_000 + helper.getLevel().random.nextInt(1_000_000));
        marked.discard();
        marked.getEprotect().tick();
        helper.assertTrue(uuid.equals(fields.automata_valkyries$getUuidField()) && id == fields.automata_valkyries$getIdField(),
                "the rewritten uuid/id were not restored: " + fields.automata_valkyries$getUuidField() + " / " + fields.automata_valkyries$getIdField());
        helper.assertTrue(helper.getLevel().getEntity(uuid) == marked && !marked.isRemoved(), "Elenor is not in the world under her own uuid");
        helper.assertTrue(!wasTracked || chunkMap.automata_valkyries$getEntityMap().containsKey(marked.getId()), "the dropped tracker was not restored");
        marked.retreat();
        helper.succeed();
    }

    /**
     * 他 MOD に別の次元へ送られそうになったら、移動ごと止まる。移動先に写しができて両方の次元に 1 体ずつ残らない（2026-10-25）
     */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void foreignDimensionChangeIsStoppedWithoutACopy(GameTestHelper helper) {
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        helper.assertTrue(nether != null, "no nether level");
        com.automatavalkyries.protect.attack.CallerAttribution.overrideForTest("com.automatavalkyries.gametest.fake", "faketeleporter");
        net.minecraft.world.entity.Entity moved;
        try {
            moved = com.automatavalkyries.gametest.fake.FakeEntityEraser.sendTo(elenor, nether);
        } finally {
            com.automatavalkyries.protect.attack.CallerAttribution.overrideForTest(null, null);
        }
        helper.assertTrue(moved == null, "a foreign dimension change went through: " + moved);
        helper.assertTrue(!elenor.isRemoved() && elenor.level() == helper.getLevel(), "Elenor left the overworld: " + elenor);
        helper.assertTrue(nether.getEntity(elenor.getUUID()) == null, "a copy of Elenor was made in the nether");
        elenor.retreat();
        helper.succeed();
    }

    /**
     * The King Sword の消去（{@code EntityChunkMapEraser}）と同じ手順で消されても、Tick が進むうちに元の UUID・ID でワールドと追跡へ戻り、
     * 複製も出ない（2026-10-25 ユーザー指定「まだ消えるし増殖する」）。Tick の一覧から外されて本体の Tick が来ない型も見る
     */
    @GameTest(template = TEMPLATE, batch = "elenor", timeoutTicks = 80)
    public static void erasedElenorComesBackWithHerOwnIdentity(GameTestHelper helper) {
        var level = helper.getLevel();
        var purged = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(1, 2, 1));
        var wiped = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(3, 2, 3));
        var forced = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(1, 2, 3));
        java.util.UUID purgedUuid = purged.getUUID(), wipedUuid = wiped.getUUID(), forcedUuid = forced.getUUID();
        var before = new java.util.HashSet<java.util.UUID>();
        var around = new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(2, 2, 2))).inflate(3.0);
        for (var e : level.getEntitiesOfClass(com.automatavalkyries.entity.ElenorEntity.class, around, e -> true)) before.add(e.getUUID());
        helper.runAfterDelay(3, () -> {
            // 偽 MOD を他 MOD と見なす（呼び出し元の MOD はクラスごとに覚えるので、最初の呼び出しより前に）
            com.automatavalkyries.protect.attack.CallerAttribution.overrideForTest("com.automatavalkyries.gametest.fake", "fakeeraser");
            try {
                com.automatavalkyries.gametest.fake.FakeEntityEraser.purgeMaximum(purged);
                com.automatavalkyries.gametest.fake.FakeEntityEraser.wipeAllReferences(wiped);
                com.automatavalkyries.gametest.fake.FakeEntityEraser.forceRemove(forced);
                // 倒せなかった相手からドロップを出させる（ForceDropHelp）。足元に何も出ない
                purged.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
                com.automatavalkyries.gametest.fake.FakeEntityEraser.forceDrop(purged);
            } finally {
                com.automatavalkyries.protect.attack.CallerAttribution.overrideForTest(null, null);
            }
        });
        helper.runAfterDelay(30, () -> {
            var chunkMap = (com.automatavalkyries.mixin.ChunkMapAccessor) level.getChunkSource().chunkMap;
            for (java.util.UUID uuid : java.util.List.of(purgedUuid, wipedUuid, forcedUuid)) {
                var found = level.getEntity(uuid);
                helper.assertTrue(found instanceof com.automatavalkyries.entity.ElenorEntity && !found.isRemoved(),
                        "an erased Elenor is not in the world under her own uuid " + uuid + ": " + found);
                helper.assertTrue(java.util.UUID.fromString(found.getStringUUID()).equals(uuid), "the string uuid was left rewritten: " + found.getStringUUID());
                helper.assertTrue(chunkMap.automata_valkyries$getEntityMap().containsKey(found.getId()), "an erased Elenor is not tracked again: " + found);
                helper.assertTrue(Float.isFinite((float) found.getY()), "an erased Elenor was left at NaN: " + found);
            }
            var all = level.getEntitiesOfClass(com.automatavalkyries.entity.ElenorEntity.class, around, e -> true);
            for (var e : all) {
                helper.assertTrue(before.contains(e.getUUID()), "a copy with a foreign uuid appeared: " + e.getUUID());
            }
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around.inflate(4.0), e -> true);
            helper.assertTrue(drops.isEmpty(), "a forced death drop came out of an undefeated Elenor: " + drops);
            helper.assertTrue(com.automatavalkyries.protect.EprotectManager.getInstance().isRegistered(forced.getEprotect())
                    || level.getEntity(forcedUuid) != forced, "the protection stopped watching Elenor after a reflective onRemovedFromWorld");
            for (var e : all) e.retreat();
            helper.succeed();
        });
    }

    /**
     * 持ち物の差し替えの守り（2026-10-24 の Madness Entities の調べ）: 守られたプレイヤーの持ち物を、攻撃の記録がある他 MOD が
     * 一覧へ直接・setItemSlot で別のアイテムへ書き換えようとしたら止める。このMOD 自身のふつうの書き込みは通す
     */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void hostileItemSwapIsBlocked(GameTestHelper helper) {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "item_swap_target"));
        player.getInventory().setItem(9, new net.minecraft.world.item.ItemStack(com.automatavalkyries.registry.ModItems.INVINCIBLE_CHARM.get()));
        com.automatavalkyries.protect.InvincibleCharm.refresh(player);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
        player.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
        var arrows = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW, 16);
        arrows.getOrCreateTag().putBoolean("keep", true);
        player.getInventory().setItem(3, arrows);
        com.automatavalkyries.protect.attack.CallerAttribution.overrideForTest("com.automatavalkyries.gametest.fake", "fakeswapper");
        try {
            com.automatavalkyries.protect.attack.AttackLog.refused(player, "test:kill", "fakeswapper"); // この守りが断った記録のある MOD
            com.automatavalkyries.gametest.fake.FakeItemSwapper.listSwap(player, 1);
            helper.assertTrue(player.getInventory().getItem(1).is(net.minecraft.world.item.Items.SHIELD), "a direct inventory list write replaced the item");
            com.automatavalkyries.gametest.fake.FakeItemSwapper.editInPlace(player, 3); // 止められた直後の、束そのものの書き換え
            var kept = player.getInventory().getItem(3);
            helper.assertTrue(kept.getCount() == 16 && kept.getTag() != null && kept.getTag().getBoolean("keep") && !kept.hasCustomHoverName(),
                    "the stack itself was rewritten in place: " + kept + " " + kept.getTag());
            com.automatavalkyries.gametest.fake.FakeItemSwapper.slotSwap(player);
            helper.assertTrue(player.getMainHandItem().is(net.minecraft.world.item.Items.DIAMOND_SWORD), "setItemSlot replaced the held item");
            com.automatavalkyries.gametest.fake.FakeItemSwapper.dropAndClear(player);
            helper.assertTrue(player.getInventory().getItem(1).is(net.minecraft.world.item.Items.SHIELD)
                    && player.getMainHandItem().is(net.minecraft.world.item.Items.DIAMOND_SWORD), "dropAll / clearContent wiped the inventory");
            com.automatavalkyries.gametest.fake.FakeSelfItem.rewriteOwnSlot(player, 2); // アイテム自身の処理は通す
            helper.assertTrue(player.getInventory().getItem(2).is(net.minecraft.world.item.Items.STICK), "an item's own handling of its holder's inventory was blocked");
        } finally {
            com.automatavalkyries.protect.attack.CallerAttribution.overrideForTest(null, null);
        }
        player.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK)); // このMOD 自身の書き込みは通る
        helper.assertTrue(player.getInventory().getItem(1).is(net.minecraft.world.item.Items.BOOK), "this mod's own inventory write was blocked");
        helper.succeed();
    }

    /**
     * 生きている相手の見分けの汎用化（2026-10-25）: isAlive が偽でも、実際の HP が正、または、ワールドに載ったまま倒れる演出に入っておらず
     * 最近攻撃してきた相手は生きていると見なす。倒れる演出に入った相手・戦っていない「倒れた扱い」は外す
     */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void deadSeemingBossCountsAsLiving(GameTestHelper helper) {
        var level = helper.getLevel();
        var boss = new com.automatavalkyries.gametest.fake.FakeDeadSeemingBoss(level);
        boss.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        level.addFreshEntity(boss);
        helper.assertTrue(com.automatavalkyries.entity.ElenorPressure.living(boss), "a boss with real HP that answers isAlive=false was not treated as living");
        boss.getEntityData().set(com.automatavalkyries.mixin.LivingEntityAccessor.automata_valkyries$getDataHealthId(), 0.0F); // 実際の HP も 0（プロキシ）
        helper.assertFalse(com.automatavalkyries.entity.ElenorPressure.living(boss), "a dead-seeming entity that is not fighting was treated as living");
        com.automatavalkyries.protect.attack.AttackLog.recordAttacker(boss); // 最近攻撃してきた
        helper.assertTrue(com.automatavalkyries.entity.ElenorPressure.living(boss), "a fighting proxy boss was not treated as living");
        boss.deathTime = 5; // 倒れる演出に入った
        helper.assertFalse(com.automatavalkyries.entity.ElenorPressure.living(boss), "an entity in its death animation was treated as living");
        boss.discard();
        helper.succeed();
    }

    /**
     * 消された後に残った古い写し（同じ UUID）は、生きた個体がワールドにいれば入り直しを試さず保護を解く（2026-10-25。皆殺しの剣で飛ばされた
     * 古い写しが入り直しを試し続け、ときどき生きた個体の代わりに入って、エレノアが消えたように見えた）
     */
    @GameTest(template = TEMPLATE, batch = "elenor", timeoutTicks = 100)
    public static void staleElenorCopyRetires(GameTestHelper helper) {
        var live = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        helper.runAfterDelay(3, () -> {
            var stale = ModEntities.ELENOR.get().create(helper.getLevel());
            stale.setUUID(live.getUUID());
            stale.moveTo(-999999.0, -999999.0, -999999.0);
            stale.getEprotect().onAddedToWorld(); // 保護だけ生きている（ワールドには載っていない）
            stale.getEprotect().tick();
            helper.assertTrue(stale.isRemoved(), "the stale copy kept its protection instead of retiring");
            helper.assertTrue(helper.getLevel().getEntity(live.getUUID()) == live && live.isAlive(), "the live Elenor was displaced by the stale copy");
            live.retreat();
            helper.succeed();
        });
    }

    /** サーバーのスレッドの外から殴られても、その場では受けず、サーバーのスレッドへ回して受ける（2026-10-25。Madness の別スレッドからの hurt で落ちた） */
    @GameTest(template = TEMPLATE, batch = "elenor", timeoutTicks = 100)
    public static void offThreadHurtIsDeferred(GameTestHelper helper) {
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        boolean[] result = {true};
        Throwable[] error = {null};
        helper.runAfterDelay(3, () -> {
            float before = com.automatavalkyries.protect.DirectRead.health(elenor);
            Thread other = new Thread(() -> {
                try {
                    result[0] = elenor.hurt(helper.getLevel().damageSources().generic(), 10.0F);
                } catch (Throwable t) {
                    error[0] = t;
                }
            }, "elenor-offthread-test");
            other.start();
            try {
                other.join(5000L);
            } catch (InterruptedException ignored) {
            }
            helper.assertTrue(error[0] == null, "an off-thread hurt threw: " + error[0]);
            helper.assertFalse(result[0], "an off-thread hurt was handled on the other thread");
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(com.automatavalkyries.protect.DirectRead.health(elenor) < before, "the deferred hurt never landed on the server thread");
                elenor.retreat();
                helper.succeed();
            });
        });
    }

    /** 動きを読む: 横へ動き続ける相手には、届く時にいる所を狙う */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void leadAimsAhead(GameTestHelper helper) {
        var aim = new net.minecraft.world.phys.Vec3(10, 1, 0);
        var lead = com.automatavalkyries.entity.ElenorTactics.leadPoint(aim, new net.minecraft.world.phys.Vec3(0, 0, 0.2), net.minecraft.world.phys.Vec3.ZERO, 0.5, 1.0);
        helper.assertTrue(lead.z > 3.0 && Math.abs(lead.x - 10) < 1.0e-6, "lead did not aim ahead of the moving target: " + lead);
        helper.succeed();
    }

    /** HP 10% 以下（第 3 フェーズ・カーテンコール・残像・リボンの縛めが選ばれうる）で技を回しても落ちない */
    @GameTest(template = TEMPLATE, batch = "elenor", timeoutTicks = 400)
    public static void lowHealthArtsRunWithoutErrors(GameTestHelper helper) {
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(3, 2, 3));
        var player = helper.makeMockPlayer();
        helper.assertTrue(elenor.arts() != null, "Elenor has no arts");
        elenor.arts().setTestHealthRatio(0.05F); // HP の直接の書き換えは Eprotect が止める
        elenor.setTarget(player);
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (elenor.arts() != null && ticks[0]++ < 300) {
                elenor.arts().tick(player);
                elenor.arts().tickProjectiles();
            }
        });
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(elenor.arts() != null && elenor.arts().phase() == 3, "Elenor is not in phase 3");
            helper.assertTrue(elenor.isAlive(), "Elenor died while running arts");
            elenor.retreat();
            helper.succeed();
        });
    }

    /** 髪留めを兵姫に渡すと結ばれ（1 つ減る）、ハサミで外すと戻ってくる */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void hairRibbonTiesAndUnties(GameTestHelper helper) {
        var valkyrie = helper.spawn(ModEntities.VALKYRIE.get(), 2, 2, 2);
        valkyrie.setNoAi(true);
        var player = helper.makeMockPlayer();
        valkyrie.setOwnerUUID(player.getUUID());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(com.automatavalkyries.registry.ModBossDrops.ELENOR_RIBBON.get(), 2));
        valkyrie.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(valkyrie.hasHairRibbon(), "the ribbon was not tied");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "the ribbon was not consumed");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHEARS));
        valkyrie.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(!valkyrie.hasHairRibbon(), "the ribbon was not untied");
        helper.assertTrue(player.getInventory().countItem(com.automatavalkyries.registry.ModBossDrops.ELENOR_RIBBON.get()) >= 1, "the ribbon was not returned");
        valkyrie.releaseAndDiscard();
        helper.succeed();
    }

    /** ちっぽけな勇気: ホットバー（0〜8 番）にあれば守られ、リュックの奥（9 番以降）なら守られない。ボスのタグにエレノアが入っている */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void tinyCourageProtectsOnlyFromHotbar(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        var courage = new net.minecraft.world.item.ItemStack(com.automatavalkyries.registry.ModBossDrops.TINY_COURAGE.get());
        helper.assertTrue(!com.automatavalkyries.item.TinyCourageItem.protects(player), "protected without the item");
        player.getInventory().setItem(20, courage.copy());
        helper.assertTrue(!com.automatavalkyries.item.TinyCourageItem.protects(player), "an item outside the hotbar must not protect");
        player.getInventory().setItem(20, net.minecraft.world.item.ItemStack.EMPTY);
        player.getInventory().setItem(8, courage.copy());
        helper.assertTrue(com.automatavalkyries.item.TinyCourageItem.protects(player), "the hotbar item did not protect");
        helper.assertTrue(ModEntities.ELENOR.get().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES), "Elenor is not tagged as a boss");
        helper.assertTrue(ModEntities.FARTHEST_ELENOR.get().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES), "Farthest Elenor is not tagged as a boss");
        helper.succeed();
    }

    /** ペナルティのティア: 技が通らない（無敵の相手）と数えて上がり、過剰な攻撃・即死を感知すると上がりやすくなる。レベル 1000 超は強く掛かる */
    @GameTest(template = TEMPLATE, batch = "elenor", timeoutTicks = 200)
    public static void penaltyTierEscalates(GameTestHelper helper) {
        helper.assertTrue(ElenorPressure.levelFactor(1500) > ElenorPressure.levelFactor(1000) + 0.9F, "levels above 1000 are not weighted strongly");
        helper.assertTrue(ElenorPressure.levelFactor(100) < 1.3F, "level 100 should be an ordinary level");
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(3, 2, 3));
        var player = helper.makeMockPlayer();
        var pressure = elenor.arts().pressure();
        helper.assertTrue(pressure.tier(player) == 0, "tier must start at 0");
        int normal = pressure.failThreshold(player.getUUID());
        // 過剰な攻撃・即死の感知（攻撃者が分からないものは全員の上がりやすさに効く）
        elenor.hurt(helper.getLevel().damageSources().generic(), Float.POSITIVE_INFINITY);
        helper.assertTrue(pressure.failThreshold(player.getUUID()) < normal, "sensing an excessive attack did not make tiers easier to reach");
        for (int i = 0; i < 40; i++) { // 技が通らなかった（模擬プレイヤーはクリエイティブなので、結果を直接数える）
            pressure.noteStrikeResult(player, 0.0F, 8.0F);
        }
        for (int tier = 0; tier <= ElenorPressure.MAX_TIER; tier++) { // どのティアの攻め方でも落ちない（生きた標的に）
            var target = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(5, 2, 5));
            pressure.strike(target, 4.0F);
            target.discard();
        }
        helper.assertTrue(pressure.tier(player) >= 1, "failed strikes did not raise the tier");
        helper.assertTrue(pressure.tier(player) <= ElenorPressure.MAX_TIER, "tier above the maximum");
        elenor.retreat();
        helper.succeed();
    }
}
