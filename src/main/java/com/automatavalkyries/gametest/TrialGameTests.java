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
import net.minecraftforge.common.util.FakePlayer;
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
     * トライアルを通しで: 回想の空間へ移り、3 秒後にボスが出て、クリエイティブでもボスが勝手に退かず（エレノアの自分での退場は 10 秒）・
     * すぐ失敗しない（2026-10-04 修正: 出した直後のボスが世界の一覧に無く「消えた」扱いで失敗していた）。あきらめると元の場所へ戻る
     */
    @GameTest(template = TEMPLATE, batch = "trial_run", timeoutTicks = 800)
    public static void trialRunsWithoutInstantFail(GameTestHelper helper) {
        FakePlayer player = player(helper, new Vec3(1, 2, 1));
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        ServerLevel home = helper.getLevel();
        helper.assertTrue(TrialManager.start(player, Trial.ALL.get(0)), "trial did not start");
        helper.assertTrue(player.level().dimension() == ModTrials.RECOLLECTION, "player was not moved to the recollection");
        long fightCheck = TrialManager.COUNTDOWN + 260; // ボスが出てから 13 秒（エレノアの自分での退場より後）
        helper.runAfterDelay(fightCheck, () -> {
            var boss = TrialManager.boss(player);
            helper.assertTrue(TrialManager.inTrial(player), "trial ended early");
            helper.assertTrue(boss != null && boss.isAlive() && !boss.isRemoved(), "boss is missing or left");
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
}
