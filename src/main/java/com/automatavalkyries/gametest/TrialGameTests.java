package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.item.ItemEnergy;
import com.automatavalkyries.registry.ModTrials;
import com.automatavalkyries.registry.ModWeapons;
import com.automatavalkyries.trial.Trial;
import com.automatavalkyries.trial.TrialManager;
import com.automatavalkyries.weapon.ChargedWeaponItem;
import com.automatavalkyries.weapon.Overload;
import com.automatavalkyries.weapon.OverloadRecipe;
import com.automatavalkyries.weapon.TacticalWeaponItem;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import com.automatavalkyries.annihilate.AnnihilationExecutionAgent;
import com.automatavalkyries.item.SuperKillStickItem;
import com.automatavalkyries.weapon.RagnarokEdgeItem;
import com.automatavalkyries.weapon.WeaponDamage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** 回想のトライアルと兵装のオーバーロード化（2026-10-04 ユーザー指定） */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class TrialGameTests {

    private static final String TEMPLATE = "empty";

    private static FakePlayer player(GameTestHelper helper, Vec3 relative) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "trial_test"));
        Vec3 pos = helper.absoluteVec(relative);
        player.moveTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
        return player;
    }

    private static ItemStack memory() {
        return new ItemStack(ModTrials.OVERLOAD_MEMORY.get());
    }

    /** 作業台: 兵装＋オーバーロードメモリ → オーバーロードの兵装（電力はそのまま）。2 回目・メモリ 2 つ・兵装 2 つは合わない */
    @GameTest(template = TEMPLATE, batch = "trials")
    public static void overloadRecipeCombines(GameTestHelper helper) {
        ItemStack edge = ((TacticalWeaponItem) ModWeapons.PHOTON_EDGE.get()).charged();
        ItemStack out = OverloadRecipe.combine(List.of(ItemStack.EMPTY, edge, memory()));
        helper.assertTrue(Overload.is(out) && out.is(ModWeapons.PHOTON_EDGE.get()), "weapon was not overloaded");
        helper.assertTrue(ItemEnergy.get(out) == ItemEnergy.get(edge), "energy was not kept");
        helper.assertTrue(OverloadRecipe.combine(List.of(out, memory())).isEmpty(), "overloaded twice");
        helper.assertTrue(OverloadRecipe.combine(List.of(edge, memory(), memory())).isEmpty(), "two memories matched");
        helper.assertTrue(OverloadRecipe.combine(List.of(edge, edge.copy(), memory())).isEmpty(), "two weapons matched");
        helper.assertTrue(OverloadRecipe.combine(List.of(new ItemStack(ModWeapons.REQUIEM_BACKDROP.get()), memory())).isEmpty(), "display item matched");
        helper.succeed();
    }

    /** オーバーロードの兵装を持っていると、与えるダメージがティアの倍率（T1 のフォトンエッジは ×40）。表に全部の兵装が載っている */
    @GameTest(template = TEMPLATE, batch = "trials")
    public static void overloadBoostsDamage(GameTestHelper helper) {
        FakePlayer normal = player(helper, new Vec3(0.5, 2, 0.5)), over = player(helper, new Vec3(0.5, 2, 4.5));
        ItemStack edge = ((TacticalWeaponItem) ModWeapons.PHOTON_EDGE.get()).charged();
        normal.setItemInHand(InteractionHand.MAIN_HAND, edge);
        over.setItemInHand(InteractionHand.MAIN_HAND, Overload.apply(edge));
        Zombie a = helper.spawn(EntityType.HUSK, new Vec3(2.5, 2, 0.5)), b = helper.spawn(EntityType.HUSK, new Vec3(2.5, 2, 4.5));
        for (Zombie z : new Zombie[]{a, b}) {
            z.setNoAi(true);
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0); // 防具で比が崩れないように
            z.setHealth(200);
        }
        a.hurt(helper.getLevel().damageSources().playerAttack(normal), 2F);
        b.hurt(helper.getLevel().damageSources().playerAttack(over), 2F);
        float da = 200 - a.getHealth(), db = 200 - b.getHealth();
        float mult = Overload.damageMult(ModWeapons.PHOTON_EDGE.get());
        helper.assertTrue(mult == 40F, "T1 multiplier is " + mult);
        helper.assertTrue(da > 0 && Math.abs(db / da - mult) < 0.05F * mult, "overload ratio is off: " + da + " -> " + db);
        helper.assertTrue(Overload.damageMult(ModWeapons.LUXION_SABER.get()) == 15F && Overload.damageMult(ModWeapons.QUANTUM_BLADE.get()) == 6F
                && Overload.damageMult(ModWeapons.RAGNAROK_EDGE.get()) == 2F, "tier multipliers are off");
        for (var item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
            var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
            if (item instanceof TacticalWeaponItem && !(item instanceof com.automatavalkyries.weapon.RequiemBackdropItem) && key != null
                    && key.getNamespace().equals(AutomataValkyries.MODID)) {
                helper.assertTrue(com.automatavalkyries.weapon.WeaponTiers.all().containsKey(key.getPath()), "no tier for " + key);
            }
        }
        a.discard();
        b.discard();
        helper.succeed();
    }

    /** オーバーロードの兵装は無敵時間を貫通し、防具を無視する（ネザライトの胴でも、T1 のフォトンエッジの ×40 がそのまま入る） */
    @GameTest(template = TEMPLATE, batch = "trials")
    public static void overloadPiercesFramesAndArmor(GameTestHelper helper) {
        FakePlayer over = player(helper, new Vec3(0.5, 2, 0.5));
        over.setItemInHand(InteractionHand.MAIN_HAND, Overload.apply(((TacticalWeaponItem) ModWeapons.PHOTON_EDGE.get()).charged()));
        Zombie husk = helper.spawn(EntityType.HUSK, new Vec3(2.5, 2, 2.5));
        husk.setNoAi(true);
        husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        husk.setHealth(1000);
        husk.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE));
        husk.hurt(helper.getLevel().damageSources().playerAttack(over), 2F);
        helper.assertTrue(Math.abs(husk.getHealth() - 920F) < 0.05F, "armor was not ignored: " + husk.getHealth());
        husk.invulnerableTime = 20; // 当たった直後の無敵時間の最中
        husk.hurt(helper.getLevel().damageSources().playerAttack(over), 2F);
        helper.assertTrue(Math.abs(husk.getHealth() - 840F) < 0.1F, "invulnerability frames blocked the hit: " + husk.getHealth());
        husk.discard();
        helper.succeed();
    }

    /** オーバーロードのチャージ兵装はチャージ時間が 7 割 */
    @GameTest(template = TEMPLATE, batch = "trials")
    public static void overloadShortensCharge(GameTestHelper helper) {
        ChargedWeaponItem railgun = (ChargedWeaponItem) ModWeapons.PUNISHMENT_RAILGUN.get();
        ItemStack normal = railgun.charged(), over = Overload.apply(normal);
        helper.assertTrue(railgun.chargeTicks(normal) == railgun.chargeTicks(), "normal charge changed");
        helper.assertTrue(railgun.chargeTicks(over) == Math.round(railgun.chargeTicks() * Overload.CHARGE_MULT), "overload charge: " + railgun.chargeTicks(over));
        helper.succeed();
    }

    /** オーバーロードの T4 兵装のチャージ攻撃の即死: 耐性 V で通常のダメージが効かない相手も倒しきる。オーバーロードでなければ効かない。保護対象には何もしない */
    @GameTest(template = TEMPLATE, batch = "trials_overkill", timeoutTicks = 100)
    public static void overloadT4Overkill(GameTestHelper helper) {
        FakePlayer over = player(helper, new Vec3(0.5, 2, 0.5)), plain = player(helper, new Vec3(0.5, 2, 4.5));
        ItemStack ragnarok = ((TacticalWeaponItem) ModWeapons.RAGNAROK_EDGE.get()).charged();
        over.setItemInHand(InteractionHand.MAIN_HAND, Overload.apply(ragnarok));
        plain.setItemInHand(InteractionHand.MAIN_HAND, ragnarok);
        helper.assertTrue(Overload.range(over) == Overload.RANGE_MULT && Overload.range(plain) == 1.0, "range multiplier is off");
        Zombie a = helper.spawn(EntityType.HUSK, new Vec3(3.5, 2, 0.5)), b = helper.spawn(EntityType.HUSK, new Vec3(3.5, 2, 4.5));
        for (Zombie z : new Zombie[]{a, b}) {
            z.setNoAi(true);
            z.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 200, 4));
        }
        Overload.overkill(b, plain);
        helper.assertTrue(b.isAlive(), "plain weapon killed outright");
        Overload.overkill(a, over);
        helper.assertTrue(a.isDeadOrDying(), "overloaded T4 did not kill outright: " + a.getHealth());
        helper.assertTrue(a.isRemoved() && helper.getLevel().getEntity(a.getUUID()) == null, "killed entity is still in the world (super annihilation eviction)");
        var elenor = helper.spawnWithNoFreeWill(com.automatavalkyries.registry.ModEntities.ELENOR.get(), new BlockPos(5, 2, 2));
        Overload.overkill(elenor, over);
        helper.assertTrue(elenor.isAlive(), "protected boss was killed outright");
        elenor.retreat();
        b.discard();
        helper.succeed();
    }

    /**
     * オーバーロードの T4 兵装の通常攻撃: 出現直後で無敵のウィザーにも当たる（オーバーロードでなければ当たらない）。
     * 割合ダメージは最大 HP の 1% ちょうど（強化・オーバーロードの倍率を掛けない）
     */
    @GameTest(template = TEMPLATE, batch = "trials_wither", timeoutTicks = 100)
    public static void overloadNormalAttackPiercesWither(GameTestHelper helper) {
        FakePlayer over = player(helper, new Vec3(0.5, 2, 0.5)), plain = player(helper, new Vec3(0.5, 2, 6.5));
        ItemStack ragnarok = ((TacticalWeaponItem) ModWeapons.RAGNAROK_EDGE.get()).charged();
        over.setItemInHand(InteractionHand.MAIN_HAND, Overload.apply(ragnarok));
        plain.setItemInHand(InteractionHand.MAIN_HAND, ragnarok);
        net.minecraft.world.entity.boss.wither.WitherBoss wither = helper.spawn(EntityType.WITHER, new Vec3(3.5, 2, 3.5));
        wither.setNoAi(true);
        wither.makeInvulnerable();
        float before = wither.getHealth();
        com.automatavalkyries.weapon.T4NormalAttacks.hit(wither, plain, com.automatavalkyries.weapon.WeaponDamage.RAGNAROK, plain, plain, 50F);
        helper.assertTrue(wither.getHealth() == before, "plain weapon hurt the invulnerable wither");
        com.automatavalkyries.weapon.T4NormalAttacks.hit(wither, over, com.automatavalkyries.weapon.WeaponDamage.RAGNAROK, over, over, 50F);
        helper.assertTrue(wither.getHealth() < before, "overloaded normal attack did not pierce the wither's invulnerability");
        wither.discard();
        Zombie husk = helper.spawn(EntityType.HUSK, new Vec3(3.5, 2, 0.5));
        husk.setNoAi(true);
        husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        husk.setHealth(1000);
        com.automatavalkyries.weapon.T4NormalAttacks.hit(husk, over, com.automatavalkyries.weapon.WeaponDamage.ENERGY, over, over, 0F);
        helper.assertTrue(Math.abs(husk.getHealth() - 990F) < 0.01F, "rend is not 1% of max HP: " + husk.getHealth());
        husk.discard();
        helper.succeed();
    }

    /** 落とし物の補い: 生きたままの相手にも、戦利品テーブルの物とプレイヤーが倒した分の経験値が落ちる（羊: 羊毛は必ず落ちる） */
    @GameTest(template = TEMPLATE, batch = "trials_loot", timeoutTicks = 100)
    public static void deathLootFallbackDrops(GameTestHelper helper) {
        FakePlayer over = player(helper, new Vec3(0.5, 2, 0.5));
        var sheep = helper.spawn(EntityType.SHEEP, new Vec3(3.5, 2, 3.5));
        sheep.setNoAi(true);
        var source = helper.getLevel().damageSources().playerAttack(over);
        com.automatavalkyries.weapon.DeathLootFallback.drop(helper.getLevel(), sheep, source, over);
        var area = new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(3, 2, 3))).inflate(4);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area,
                e -> e.getItem().is(net.minecraft.tags.ItemTags.WOOL)).size() >= 1, "no wool dropped");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, area).isEmpty(), "no experience dropped");
        for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area, e -> !(e instanceof net.minecraft.world.entity.player.Player))) e.discard();
        helper.succeed();
    }

    /** The Trial Monolith（-PwithTrialMonolith）: オーバーロードの T4 の超抹殺で消え、落とし物か経験値が落ちる（その MOD が無ければ素通り） */
    @GameTest(template = TEMPLATE, batch = "trials_monolith_loot", timeoutTicks = 200)
    public static void overloadKillDropsTrialMonolithLoot(GameTestHelper helper) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("the_trial_monolith")) {
            helper.succeed();
            return;
        }
        var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("the_trial_monolith", "trial_monolith"));
        FakePlayer over = player(helper, new Vec3(0.5, 2, 0.5));
        over.setItemInHand(InteractionHand.MAIN_HAND, Overload.apply(((TacticalWeaponItem) ModWeapons.GENESIS_HALO.get()).charged()));
        helper.runAfterDelay(2, () -> {
            var boss = (net.minecraft.world.entity.LivingEntity) helper.spawn(type, new BlockPos(3, 2, 3));
            helper.runAfterDelay(5, () -> {
                Overload.overkill(boss, over);
                var area = new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(3, 2, 3))).inflate(8);
                int items = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).size();
                int orbs = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, area).size();
                com.automatavalkyries.AutomataValkyries.LOGGER.info("[loot-check] trial monolith: removed={} items={} orbs={} xpReward={}", boss.isRemoved(), items, orbs, boss.getExperienceReward());
                helper.assertTrue(boss.isRemoved(), "trial monolith was not annihilated");
                helper.assertTrue(items + orbs > 0, "no loot or experience from the trial monolith");
                helper.succeed();
            });
        });
    }

    /** オーバーロードの T4 兵装を持っている間の加護は純粋強化（耐性 IV、ほかは 2 段上） */
    @GameTest(template = TEMPLATE, batch = "trials")
    public static void overloadT4Blessing(GameTestHelper helper) {
        Zombie z = helper.spawn(EntityType.HUSK, new Vec3(1.5, 2, 1.5));
        z.setNoAi(true);
        z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, Overload.apply(((TacticalWeaponItem) ModWeapons.RAGNAROK_EDGE.get()).charged()));
        com.automatavalkyries.weapon.RagnarokEdgeItem.blessHolder(z);
        helper.assertTrue(z.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE).getAmplifier() == 3, "resistance is not IV");
        helper.assertTrue(z.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST).getAmplifier() == 5, "strength is not VI");
        z.discard();
        helper.succeed();
    }

    /**
     * 守護の護符（2026-10-04 作り直し）: 持っているプレイヤーは /kill 型のダメージ・die() の直接呼び出し・被弾以外の HP 0 化を受けない。
     * 通常のダメージ（エレノアの攻撃と同じ、mob の近接）は通り、命に届く量なら倒れる。持っていなければ守られない
     */
    @GameTest(template = TEMPLATE, batch = "ward_charm", timeoutTicks = 100)
    public static void wardCharmRefusesInstantKillOnly(GameTestHelper helper) {
        // FakePlayer はどのダメージも受けず die() も空なので、ふつうに傷つくテスト用のプレイヤーを使う
        var warded = helper.makeMockPlayer();
        var plain = helper.makeMockPlayer();
        warded.getInventory().add(new ItemStack(com.automatavalkyries.registry.ModItems.WARD_CHARM.get()));
        com.automatavalkyries.protect.PlayerWard.refresh(warded);
        com.automatavalkyries.protect.PlayerWard.refresh(plain);
        helper.assertTrue(com.automatavalkyries.protect.PlayerWard.isActive(warded), "ward is not active");
        helper.assertFalse(com.automatavalkyries.protect.PlayerWard.isActive(plain), "player without the charm is warded");
        float before = warded.getHealth();
        warded.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
        helper.assertTrue(warded.getHealth() == before && warded.isAlive(), "/kill-type damage went through: " + warded.getHealth());
        warded.setHealth(0F);
        helper.assertTrue(warded.getHealth() == before, "health was zeroed outside of damage");
        warded.die(helper.getLevel().damageSources().generic());
        helper.assertFalse(warded.isDeadOrDying(), "direct die() went through");
        var elenor = helper.spawnWithNoFreeWill(com.automatavalkyries.registry.ModEntities.ELENOR.get(), new BlockPos(3, 2, 3));
        var ordinary = helper.getLevel().damageSources().mobAttack(elenor);
        // 護符の無いテスト用のプレイヤーにも敵の攻撃が入らない環境（ECA を入れたとき。テスト用のプレイヤーへの敵の攻撃を ECA のどこかが止める。
        // 2026-10-09 に確認、護符とは関係ない）では、普通のダメージが通るかは確かめられないので飛ばす
        var control = helper.makeMockPlayer();
        control.invulnerableTime = 0;
        control.hurt(ordinary, 4F);
        if (control.getHealth() < control.getMaxHealth()) {
            warded.invulnerableTime = 0;
            warded.hurt(ordinary, 4F);
            helper.assertTrue(warded.getHealth() < before, "ordinary damage (Elenor's attack) was blocked");
            warded.invulnerableTime = 0;
            warded.hurt(ordinary, 1000F);
            helper.assertTrue(warded.isDeadOrDying(), "lethal ordinary damage did not kill the warded player");
        } else {
            com.automatavalkyries.AutomataValkyries.LOGGER.warn("[test] Mob attacks do not land on mock players in this environment; skipping the ordinary-damage part of the ward charm test");
        }
        plain.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
        helper.assertTrue(plain.isDeadOrDying(), "/kill did not kill the player without the charm");
        elenor.retreat();
        helper.succeed();
    }

    /** 無敵の護符: 持っているプレイヤーはどのダメージも受けず、HP を減らされても最大のまま。持っていなければふつうに減る */
    @GameTest(template = TEMPLATE, batch = "invincible_charm", timeoutTicks = 100)
    public static void invincibleCharmNullifiesDamage(GameTestHelper helper) {
        var guarded = helper.makeMockPlayer();
        var plain = helper.makeMockPlayer();
        guarded.getInventory().add(new ItemStack(com.automatavalkyries.registry.ModItems.INVINCIBLE_CHARM.get()));
        com.automatavalkyries.protect.InvincibleCharm.refresh(guarded);
        com.automatavalkyries.protect.InvincibleCharm.refresh(plain);
        float max = guarded.getMaxHealth();
        guarded.hurt(helper.getLevel().damageSources().magic(), 10F);
        guarded.invulnerableTime = 0;
        guarded.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
        helper.assertTrue(guarded.getHealth() == max && guarded.isAlive(), "damage went through the invincibility charm: " + guarded.getHealth());
        guarded.setHealth(1F);
        helper.assertTrue(guarded.getHealth() == max, "health was lowered: " + guarded.getHealth());
        guarded.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        guarded.knockback(2.0, 1.0, 0.0);
        guarded.push(1.0, 0.5, 0.0);
        helper.assertTrue(guarded.getDeltaMovement().lengthSqr() == 0, "invincible player was knocked back: " + guarded.getDeltaMovement());
        plain.hurt(helper.getLevel().damageSources().magic(), 10F);
        helper.assertTrue(plain.getHealth() < plain.getMaxHealth(), "player without the charm took no damage");
        helper.succeed();
    }

    /** 護符を手放させない: 捨てずに持ち物から無くなった護符は戻る。無敵の護符は無くなっても少しの間は無敵が続く */
    @GameTest(template = TEMPLATE, batch = "charm_keeper", timeoutTicks = 100)
    public static void charmsAreKeptAndInvincibilityLingers(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        player.getInventory().add(new ItemStack(com.automatavalkyries.registry.ModItems.WARD_CHARM.get()));
        player.getInventory().add(new ItemStack(com.automatavalkyries.registry.ModItems.INVINCIBLE_CHARM.get()));
        com.automatavalkyries.protect.CharmKeeper.keep(player);
        com.automatavalkyries.protect.InvincibleCharm.refresh(player);
        player.getInventory().clearContent(); // 他の処理に持ち物から消された
        com.automatavalkyries.protect.InvincibleCharm.refresh(player);
        helper.assertTrue(com.automatavalkyries.protect.InvincibleCharm.isActive(player), "invincibility ended as soon as the charm left");
        com.automatavalkyries.protect.CharmKeeper.keep(player);
        helper.assertTrue(player.getInventory().countItem(com.automatavalkyries.registry.ModItems.WARD_CHARM.get()) == 1
                && player.getInventory().countItem(com.automatavalkyries.registry.ModItems.INVINCIBLE_CHARM.get()) == 1, "charms were not returned");
        // 地面に落とされた護符は拾い直す（新しく作らない）
        var ward = player.getInventory().items.stream().filter(st -> st.is(com.automatavalkyries.registry.ModItems.WARD_CHARM.get())).findFirst().orElseThrow();
        var dropped = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), ward.copy());
        ward.setCount(0);
        helper.getLevel().addFreshEntity(dropped);
        com.automatavalkyries.protect.CharmKeeper.keep(player);
        helper.assertTrue(dropped.isRemoved(), "the dropped charm was not picked up");
        helper.assertTrue(player.getInventory().countItem(com.automatavalkyries.registry.ModItems.WARD_CHARM.get()) == 1, "ward charm count is off after pick-up");
        helper.succeed();
    }

    /** 回想を全部クリアするとオーバーロードメモリ、その後はクリアするたびにもう 1 つ */
    @GameTest(template = TEMPLATE, batch = "trials")
    public static void fullClearGivesOverloadMemory(GameTestHelper helper) {
        FakePlayer player = player(helper, new Vec3(1, 2, 1));
        for (int i = 0; i < Trial.ALL.size() - 1; i++) helper.assertFalse(TrialManager.recordClear(player, Trial.ALL.get(i)), "reward before full clear");
        helper.assertTrue(player.getInventory().countItem(ModTrials.OVERLOAD_MEMORY.get()) == 0, "memory given early");
        helper.assertTrue(TrialManager.recordClear(player, Trial.ALL.get(Trial.ALL.size() - 1)), "no reward on full clear");
        helper.assertTrue(player.getInventory().countItem(ModTrials.OVERLOAD_MEMORY.get()) == 1, "full clear did not give one memory");
        TrialManager.recordClear(player, Trial.ALL.get(0));
        helper.assertTrue(player.getInventory().countItem(ModTrials.OVERLOAD_MEMORY.get()) == 2, "re-clear did not give another memory");
        helper.assertTrue(TrialManager.clearedCount(player) == Trial.ALL.size(), "cleared count is off");
        helper.succeed();
    }

    /**
     * トライアルを通しで: 回想の空間へ移り、3 秒後にボスが出て、クリエイティブでもボスが勝手に退かず（ボスの自分での退場は 10 秒）・
     * すぐ失敗しない（2026-10-04 修正: 出した直後のボスが世界の一覧に無く「消えた」扱いで失敗していた）。あきらめると元の場所へ戻る
     */
    @GameTest(template = TEMPLATE, batch = "trial_run", timeoutTicks = 800)
    public static void trialRunsWithoutInstantFail(GameTestHelper helper) {
        FakePlayer player = player(helper, new Vec3(1, 2, 1));
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        ServerLevel home = helper.getLevel();
        helper.assertTrue(TrialManager.start(player, Trial.get(Trial.indexOf("luxion"))), "trial did not start");
        helper.assertTrue(player.level().dimension() == ModTrials.RECOLLECTION, "player was not moved to the recollection");
        long fightCheck = TrialManager.COUNTDOWN + 260; // ボスが出てから 13 秒（エレノアの自分での退場より後）
        helper.runAfterDelay(fightCheck, () -> {
            var boss = TrialManager.boss(player);
            helper.assertTrue(TrialManager.inTrial(player), "trial ended early");
            helper.assertTrue(boss != null && boss.isAlive() && !boss.isRemoved(), "boss is missing or left");
            // 光速の残影は本物のルクシオン（2026-10-09）。回想のボスは保存しない
            helper.assertTrue(boss instanceof com.automatavalkyries.entity.chrono.LuxionEntity, "the Luxion trial did not spawn Luxion: " + boss);
            helper.assertFalse(boss.shouldBeSaved(), "the trial boss would be saved");
            TrialManager.abandon(player);
        });
        helper.runAfterDelay(fightCheck + TrialManager.AFTER_FAIL + 10, () -> {
            helper.assertFalse(TrialManager.inTrial(player), "trial did not end after abandoning");
            helper.assertTrue(player.level() == home, "player was not returned home");
            helper.succeed();
        });
    }

    /** 回想の空間（データパックのディメンション）が読み込まれ、闘技場が建つ */
    @GameTest(template = TEMPLATE, batch = "trials")
    public static void recollectionArenaBuilds(GameTestHelper helper) {
        ServerLevel arena = helper.getLevel().getServer().getLevel(ModTrials.RECOLLECTION);
        helper.assertTrue(arena != null, "recollection dimension is missing");
        BlockPos c = new BlockPos(-4096, TrialManager.ARENA_Y, -4096); // プレイヤーの闘技場（正の座標）と重ならない所
        TrialManager.buildArena(arena, c);
        helper.assertTrue(arena.getBlockState(c.below()).is(Blocks.SEA_LANTERN), "no center light");
        helper.assertTrue(arena.getBlockState(c.offset(TrialManager.ARENA_RADIUS / 2, -1, 0)).isSolid(), "no floor");
        helper.assertTrue(arena.getBlockState(c.offset(TrialManager.ARENA_RADIUS + 1, 3, 0)).is(Blocks.BARRIER), "no wall");
        helper.succeed();
    }

    /**
     * 超絶抹殺: オーバーロード T4 兵装のチャージ攻撃（Overload.overkill）で、
     * 通常の mob が抹消され（AnnihilationExecutionAgent）、同じ UUID でのワールド再参加が拒否される
     */
    @GameTest(template = TEMPLATE, batch = "trials_annihilation", timeoutTicks = 100)
    public static void overloadT4AnnihilationBlocksRejoin(GameTestHelper helper) {
        FakePlayer over = player(helper, new Vec3(0.5, 2, 0.5));
        ItemStack ragnarok = ((TacticalWeaponItem) ModWeapons.RAGNAROK_EDGE.get()).charged();
        over.setItemInHand(InteractionHand.MAIN_HAND, Overload.apply(ragnarok));

        Zombie target = helper.spawn(EntityType.HUSK, new Vec3(3.5, 2, 0.5));
        UUID targetUuid = target.getUUID();
        Overload.overkill(target, over);

        helper.assertTrue(target.isRemoved(), "target mob was not removed");
        helper.assertTrue(AnnihilationExecutionAgent.isAnnihilated(targetUuid), "UUID was not registered in annihilation blacklist");
        helper.assertTrue(SuperKillStickItem.isRefused(target), "instance was not registered in refuse list");

        // 同じ UUID での再登録が拒否されることを検証
        Zombie rejoining = new Zombie(EntityType.HUSK, helper.getLevel());
        rejoining.setUUID(targetUuid);
        var joinEvent = new EntityJoinLevelEvent(rejoining, helper.getLevel());
        MinecraftForge.EVENT_BUS.post(joinEvent);
        helper.assertTrue(joinEvent.isCanceled(), "entity with annihilated UUID was not blocked on rejoin");
        helper.assertTrue(rejoining.isRemoved(), "rejoining entity was not discarded");

        helper.succeed();
    }

    /**
     * 超絶抹殺でのドロップ補正: 抹消された mob の戦利品（羊毛等）およびプレイヤー討伐時の経験値オーブが正常に出現する（DeathLootFallback）
     */
    @GameTest(template = TEMPLATE, batch = "trials_annihilation_loot", timeoutTicks = 100)
    public static void overloadT4AnnihilationDropsLootAndExp(GameTestHelper helper) {
        boolean prevLoot = helper.getLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT);
        helper.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT).set(true, helper.getLevel().getServer());
        net.minecraft.core.BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        var chunkPos = new net.minecraft.world.level.ChunkPos(pos);
        var manager = (net.minecraft.world.level.entity.PersistentEntitySectionManager<net.minecraft.world.entity.Entity>)
                ((com.automatavalkyries.mixin.ServerLevelTickListAccessor) helper.getLevel()).automata_valkyries$getEntityManager();
        manager.updateChunkStatus(chunkPos, net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);
        helper.getLevel().setChunkForced(chunkPos.x, chunkPos.z, true);
        FakePlayer over = player(helper, new Vec3(0.5, 2, 0.5));
        ItemStack ragnarok = ((TacticalWeaponItem) ModWeapons.RAGNAROK_EDGE.get()).charged();
        over.setItemInHand(InteractionHand.MAIN_HAND, Overload.apply(ragnarok));

        var sheep = helper.spawn(EntityType.SHEEP, new Vec3(3.5, 2, 3.5));
        sheep.setNoAi(true);
        helper.runAfterDelay(2, () -> Overload.overkill(sheep, over));
        helper.runAfterDelay(5, () -> {
            try {
                var area = new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(3, 2, 3))).inflate(6);
                var allItems = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area);
                var items = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area,
                        e -> e.getItem().is(net.minecraft.tags.ItemTags.WOOL));
                var orbs = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, area);
                com.automatavalkyries.AutomataValkyries.LOGGER.info("[loot-check] found allItems={}, woolItems={}, orbs={}",
                        allItems.stream().map(i -> i.getItem().getItem().toString()).toList(), items.size(), orbs.size());

                helper.assertTrue(!items.isEmpty(), "no wool dropped by annihilated sheep (allItems=" + allItems.stream().map(i -> i.getItem().getItem().toString()).toList() + ")");
                helper.assertTrue(!orbs.isEmpty(), "no exp orbs dropped by annihilated sheep");

                for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area, e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
                    e.discard();
                }
                helper.succeed();
            } finally {
                helper.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT).set(prevLoot, helper.getLevel().getServer());
            }
        });
    }

    /**
     * プレイヤー安全性: プレイヤーは抹殺対象から除外され、チャージ攻撃のダメージによって通常通り死亡し、持ち物をドロップする
     */
    @GameTest(template = TEMPLATE, batch = "trials_player_safety", timeoutTicks = 100)
    public static void overloadT4PlayerTakesDamageNotAnnihilated(GameTestHelper helper) {
        FakePlayer over = player(helper, new Vec3(0.5, 2, 0.5));
        ItemStack ragnarok = ((TacticalWeaponItem) ModWeapons.RAGNAROK_EDGE.get()).charged();
        over.setItemInHand(InteractionHand.MAIN_HAND, Overload.apply(ragnarok));

        var testPlayer = helper.makeMockPlayer();
        testPlayer.getAbilities().invulnerable = false;
        Vec3 pos = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
        testPlayer.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        ItemStack apple = new ItemStack(net.minecraft.world.item.Items.APPLE, 5);
        testPlayer.getInventory().add(apple);

        // チャージ攻撃の直撃（ダメージ + overkill）
        testPlayer.invulnerableTime = 0;
        testPlayer.hurt(WeaponDamage.source(over, WeaponDamage.RAGNAROK, over, over), RagnarokEdgeItem.CLEAVE_DAMAGE);
        Overload.overkill(testPlayer, over);

        // プレイヤーは抹殺されない（ブラックリスト未登録、インスタンス拒否なし）
        helper.assertFalse(AnnihilationExecutionAgent.isAnnihilated(testPlayer.getUUID()), "player UUID was annihilated");
        helper.assertFalse(SuperKillStickItem.isRefused(testPlayer), "player was added to refuse list");
        helper.assertTrue(testPlayer.isDeadOrDying(), "player was not killed by lethal charge damage");

        // 死亡に伴うアイテムドロップの検証（ServerPlayer は死亡時にインベントリを dropAll してワールドに ItemEntity を出現させる）
        var area = testPlayer.getBoundingBox().inflate(6);
        var droppedItems = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area,
                e -> e.getItem().is(net.minecraft.world.item.Items.APPLE));
        helper.assertTrue(!droppedItems.isEmpty() || testPlayer.getInventory().isEmpty()
                || testPlayer.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY),
                "player items were not dropped on death");

        for (var it : droppedItems) it.discard();
        helper.succeed();
    }

    /**
     * 保護対象の免除: エレノア（IEprotected）および戦術兵姫（ValkyrieEntity）は抹殺されない
     */
    @GameTest(template = TEMPLATE, batch = "trials_protected_exemption", timeoutTicks = 100)
    public static void overloadT4ProtectedEntitiesAreNotAnnihilated(GameTestHelper helper) {
        FakePlayer over = player(helper, new Vec3(0.5, 2, 0.5));
        ItemStack ragnarok = ((TacticalWeaponItem) ModWeapons.RAGNAROK_EDGE.get()).charged();
        over.setItemInHand(InteractionHand.MAIN_HAND, Overload.apply(ragnarok));

        // エレノア
        var elenor = helper.spawnWithNoFreeWill(com.automatavalkyries.registry.ModEntities.ELENOR.get(), new BlockPos(3, 2, 3));
        Overload.overkill(elenor, over);
        helper.assertTrue(elenor.isAlive() && !elenor.isRemoved(), "Elenor was annihilated or removed");
        helper.assertFalse(AnnihilationExecutionAgent.isAnnihilated(elenor), "Elenor was registered in annihilation blacklist");
        elenor.retreat();

        // 戦術兵姫
        var valkyrie = helper.spawnWithNoFreeWill(com.automatavalkyries.registry.ModEntities.VALKYRIE.get(), new BlockPos(5, 2, 3));
        Overload.overkill(valkyrie, over);
        helper.assertTrue(!valkyrie.isRemoved(), "Valkyrie was removed by overkill");
        helper.assertFalse(AnnihilationExecutionAgent.isAnnihilated(valkyrie), "Valkyrie was registered in annihilation blacklist");
        valkyrie.discard();

        helper.succeed();
    }

    /**
     * 発動条件の限定: オーバーロードしていない兵装、または T4 未満の兵装では抹殺（Overload.overkill）が発動しない
     */
    @GameTest(template = TEMPLATE, batch = "trials_condition_check", timeoutTicks = 100)
    public static void nonOverloadOrNonT4DoesNotAnnihilate(GameTestHelper helper) {
        FakePlayer plainT4 = player(helper, new Vec3(0.5, 2, 0.5));
        FakePlayer overT1 = player(helper, new Vec3(0.5, 2, 4.5));

        ItemStack plainRagnarok = ((TacticalWeaponItem) ModWeapons.RAGNAROK_EDGE.get()).charged(); // 非オーバーロード T4
        plainT4.setItemInHand(InteractionHand.MAIN_HAND, plainRagnarok);

        ItemStack overPhotonEdge = Overload.apply(((TacticalWeaponItem) ModWeapons.PHOTON_EDGE.get()).charged()); // オーバーロード T1
        overT1.setItemInHand(InteractionHand.MAIN_HAND, overPhotonEdge);

        Zombie mobA = helper.spawn(EntityType.HUSK, new Vec3(3.5, 2, 0.5));
        mobA.setNoAi(true);
        mobA.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 200, 4));

        Zombie mobB = helper.spawn(EntityType.HUSK, new Vec3(3.5, 2, 4.5));
        mobB.setNoAi(true);
        mobB.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 200, 4));

        Overload.overkill(mobA, plainT4);
        helper.assertTrue(mobA.isAlive() && !mobA.isRemoved(), "plain T4 weapon triggered overkill");
        helper.assertFalse(AnnihilationExecutionAgent.isAnnihilated(mobA), "mobA was marked annihilated by plain T4");

        Overload.overkill(mobB, overT1);
        helper.assertTrue(mobB.isAlive() && !mobB.isRemoved(), "overloaded T1 weapon triggered overkill");
        helper.assertFalse(AnnihilationExecutionAgent.isAnnihilated(mobB), "mobB was marked annihilated by T1");

        mobA.discard();
        mobB.discard();
        helper.succeed();
    }
}
