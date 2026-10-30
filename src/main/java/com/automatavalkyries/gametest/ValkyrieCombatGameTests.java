package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.ValkyrieOrders;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.registry.ModWeapons;
import com.automatavalkyries.weapon.TacticalWeaponItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** 戦術兵姫の戦闘 AI（段階 3）: 交戦の方針・兵装での攻撃・味方への攻撃の無効 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ValkyrieCombatGameTests {

    private static final String TEMPLATE = "empty";

    private static ValkyrieEntity armed(GameTestHelper helper, int x, int z, ValkyrieOrders.Policy policy) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), x, 2, z);
        v.setItemSlot(EquipmentSlot.MAINHAND, ((TacticalWeaponItem) ModWeapons.PHOTON_EDGE.get()).charged());
        v.orders.set(ValkyrieOrders.Field.POLICY, policy.ordinal());
        v.setMode(ValkyrieEntity.Mode.DEFEND);
        return v;
    }

    /** 積極的: 近くの敵対モブを自分で見つけて、兵装で倒す */
    @GameTest(template = TEMPLATE, batch = "valkyrie_combat", timeoutTicks = 300)
    public static void valkyrieAggressiveKillsHostile(GameTestHelper helper) {
        ValkyrieEntity v = armed(helper, 1, 1, ValkyrieOrders.Policy.AGGRESSIVE);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 3);
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET)); // 日光で燃えないように
        WeaponGameTests.forceChunks(helper, v, zombie);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(v.getTarget() == zombie, "did not target the hostile"))
                .thenWaitUntil(() -> helper.assertTrue(!zombie.isAlive(), "did not defeat the hostile: hp=" + zombie.getHealth()))
                .thenExecute(v::releaseAndDiscard)
                .thenSucceed();
    }

    /** 反撃: 攻撃してこない敵対モブは狙わず、殴られたら狙う。非戦闘は狙わない */
    @GameTest(template = TEMPLATE, batch = "valkyrie_combat", timeoutTicks = 200)
    public static void valkyrieRetaliatesOnlyWhenAttacked(GameTestHelper helper) {
        ValkyrieEntity v = armed(helper, 1, 1, ValkyrieOrders.Policy.RETALIATE);
        ValkyrieEntity passive = armed(helper, 1, 4, ValkyrieOrders.Policy.PASSIVE);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 2);
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET)); // 日光で燃えないように
        WeaponGameTests.forceChunks(helper, v, passive, zombie);
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(v.getTarget() == null && passive.getTarget() == null, "targeted a zombie that did not attack"))
                .thenExecute(() -> {
                    v.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1.0F);
                    passive.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1.0F);
                })
                .thenWaitUntil(() -> helper.assertTrue(v.getTarget() == zombie, "did not retaliate"))
                .thenIdle(20)
                .thenExecute(() -> helper.assertTrue(passive.getTarget() == null, "a passive valkyrie engaged"))
                .thenExecute(() -> {
                    v.releaseAndDiscard();
                    passive.releaseAndDiscard();
                    zombie.discard();
                })
                .thenSucceed();
    }

    /** 味方への攻撃は当たらない: 持ち主 → 兵姫、同じ持ち主の兵姫同士。別の持ち主の兵姫の攻撃は当たる */
    @GameTest(template = TEMPLATE, batch = "valkyrie_combat")
    public static void valkyrieNoFriendlyFire(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer();
        ValkyrieEntity a = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        ValkyrieEntity b = helper.spawn(ModEntities.VALKYRIE.get(), 3, 2, 1);
        ValkyrieEntity stranger = helper.spawn(ModEntities.VALKYRIE.get(), 5, 2, 1);
        a.setOwnerUUID(owner.getUUID());
        b.setOwnerUUID(owner.getUUID());
        stranger.setOwnerUUID(UUID.randomUUID());
        float shieldA = a.getShield(), shieldB = b.getShield();
        helper.assertFalse(a.hurt(helper.getLevel().damageSources().playerAttack(owner), 5.0F), "the owner's attack hit the valkyrie");
        helper.assertFalse(b.hurt(helper.getLevel().damageSources().mobAttack(a), 5.0F), "an allied valkyrie's attack hit");
        helper.assertTrue(a.getShield() == shieldA && b.getShield() == shieldB, "friendly attacks took shield/HP");
        float before = b.getShield() + DirectRead.health(b);
        helper.assertTrue(b.hurt(helper.getLevel().damageSources().mobAttack(stranger), 5.0F), "another owner's valkyrie could not hit");
        helper.assertTrue(b.getShield() + DirectRead.health(b) < before, "the stranger's attack did no damage");
        a.releaseAndDiscard();
        b.releaseAndDiscard();
        stranger.releaseAndDiscard();
        helper.succeed();
    }

    /** オメガコアで全ステータス 3 倍: 最大 HP・シールド・防御・速度、与えるダメージも 3 倍 */
    @GameTest(template = TEMPLATE, batch = "valkyrie_combat", timeoutTicks = 40)
    public static void valkyrieOmegaCoreTriplesStats(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        ValkyrieEntity plain = helper.spawn(ModEntities.VALKYRIE.get(), 3, 2, 1);
        Zombie a = helper.spawn(EntityType.ZOMBIE, 1, 2, 4), b = helper.spawn(EntityType.ZOMBIE, 3, 2, 4);
        a.setNoAi(true);
        b.setNoAi(true);
        float baseHp = v.getMaxHealth(), baseShield = v.getMaxShield();
        double baseArmor = v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        v.setCore(new net.minecraft.world.item.ItemStack(ModWeapons.CORE_STELLA_GENESIS_OMEGA.get()));
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            helper.assertTrue(Math.abs(v.getMaxHealth() - baseHp * 3) < 0.01F, "max HP not tripled: " + v.getMaxHealth());
            helper.assertTrue(Math.abs(v.getMaxShield() - baseShield * 3) < 0.01F, "max shield not tripled: " + v.getMaxShield());
            helper.assertTrue(Math.abs(v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) - baseArmor * 3) < 0.01, "armor not tripled");
            float hpA = a.getHealth(), hpB = b.getHealth();
            a.hurt(helper.getLevel().damageSources().mobAttack(v), 2.0F);
            b.hurt(helper.getLevel().damageSources().mobAttack(plain), 2.0F);
            float dealtOmega = hpA - a.getHealth(), dealtPlain = hpB - b.getHealth();
            helper.assertTrue(dealtPlain > 0 && Math.abs(dealtOmega - dealtPlain * 3) < 0.6F, "damage not tripled: omega=" + dealtOmega + " plain=" + dealtPlain);
            v.releaseAndDiscard();
            plain.releaseAndDiscard();
            a.discard();
            b.discard();
        }).thenSucceed();
    }

    /** オメガコアはデバフ（バニラ・MOD の物とも）を受けず、積む前から掛かっていた物も消す。良い効果は残す */
    @GameTest(template = TEMPLATE, batch = "valkyrie_combat", timeoutTicks = 40)
    public static void valkyrieOmegaCoreClearsDebuffs(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        v.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        v.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200));
        helper.assertTrue(v.hasEffect(MobEffects.POISON), "poison not applied without omega core");
        v.setCore(new ItemStack(ModWeapons.CORE_STELLA_GENESIS_OMEGA.get()));
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            helper.assertFalse(v.hasEffect(MobEffects.POISON), "existing poison not cleared by omega core");
            helper.assertTrue(v.hasEffect(MobEffects.DAMAGE_BOOST), "omega core removed a beneficial effect");
            helper.assertFalse(v.addEffect(new MobEffectInstance(MobEffects.WITHER, 200)), "omega core accepted wither");
            v.forceAddEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200), null);
            helper.assertFalse(v.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "omega core accepted forced slowness");
            helper.assertFalse(v.addEffect(new MobEffectInstance(ModWeapons.FROSTBITE.get(), 200)), "omega core accepted a modded debuff");
            v.releaseAndDiscard();
        }).thenSucceed();
    }

    /** 兵姫の兵装のデバフは味方（同じ持ち主の兵姫・持ち主）に付かない。敵には付く */
    @GameTest(template = TEMPLATE, batch = "valkyrie_combat")
    public static void valkyrieDebuffsSpareAllies(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        ValkyrieEntity a = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        ValkyrieEntity b = helper.spawn(ModEntities.VALKYRIE.get(), 3, 2, 1);
        Zombie enemy = helper.spawn(EntityType.ZOMBIE, 5, 2, 1);
        enemy.setNoAi(true);
        a.setOwnerUUID(owner);
        b.setOwnerUUID(owner);
        var prev = com.automatavalkyries.entity.ai.ValkyrieCombat.setActing(a);
        try {
            b.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 100));
            com.automatavalkyries.weapon.Frostbite.apply(b);
            enemy.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 100));
        } finally {
            com.automatavalkyries.entity.ai.ValkyrieCombat.setActing(prev);
        }
        helper.assertFalse(b.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), "an ally got the valkyrie's debuff");
        helper.assertFalse(b.hasEffect(ModWeapons.FROSTBITE.get()), "an ally got frostbite");
        helper.assertTrue(enemy.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), "the enemy was spared");
        a.releaseAndDiscard();
        b.releaseAndDiscard();
        enemy.discard();
        helper.succeed();
    }

    /** 日常（段階 4）: 好物で同調率 +5%、落ちている物をチェストへ片付ける、充電台で充電する */
    @GameTest(template = TEMPLATE, batch = "valkyrie_daily")
    public static void valkyrieGiftRaisesSync(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer();
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        v.setOwnerUUID(owner.getUUID());
        float before = v.getSyncRate();
        v.life().gift(true);
        helper.assertTrue(Math.abs(v.getSyncRate() - before - 5.0F) < 0.01F, "gift did not raise sync by 5: " + (v.getSyncRate() - before));
        v.releaseAndDiscard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "valkyrie_daily_clean", timeoutTicks = 400)
    public static void valkyrieCleansUpIntoChest(GameTestHelper helper) {
        clearAround(helper); // 前の回の残りのチェスト・充電台（テストの世界は回をまたいで残る）
        net.minecraft.core.BlockPos chestPos = new net.minecraft.core.BlockPos(2, 0, 1); // 兵姫の足元の高さ（空の型の床の上）。すぐ隣に置く（前のテストの残りのチェストへ行かないよう）
        helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        v.setMode(ValkyrieEntity.Mode.WAIT);
        net.minecraft.world.entity.item.ItemEntity apple = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(),
                helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 3.5)).x, helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 3.5)).y,
                helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 3.5)).z, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE, 3));
        helper.getLevel().addFreshEntity(apple);
        WeaponGameTests.forceChunks(helper, v, apple);
        helper.succeedWhen(() -> {
            var be = helper.getBlockEntity(chestPos);
            helper.assertTrue(be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest
                    && chest.countItem(net.minecraft.world.item.Items.APPLE) == 3, "apples were not put into the chest (chests=" + chestsAround(helper, chestPos) + " has pickup=" + v.getInventory().hasPickup() + " pos=" + helper.relativeVec(v.position()) + " path=" + v.getNavigation().getPath()
                    + " goals=" + v.goalSelectorRunning() + ")");
            v.releaseAndDiscard();
        });
    }

    /** ロジック: ポーター（チェスト間）: 運び元のチェストの中身を運び先のチェストへ運ぶ */
    @GameTest(template = TEMPLATE, batch = "valkyrie_daily_porter", timeoutTicks = 600)
    public static void valkyriePorterMovesChestToChest(GameTestHelper helper) {
        clearAround(helper);
        net.minecraft.core.BlockPos src = new net.minecraft.core.BlockPos(2, 0, 1), dest = new net.minecraft.core.BlockPos(2, 0, 4);
        helper.setBlock(src, net.minecraft.world.level.block.Blocks.CHEST);
        helper.setBlock(dest, net.minecraft.world.level.block.Blocks.BARREL);
        ((net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getBlockEntity(src)).setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 20));
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 2);
        v.setMode(ValkyrieEntity.Mode.WAIT);
        ItemStack chip = new ItemStack(com.automatavalkyries.registry.ModValkyrieUnits.CHIPS.get(com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.PORTER).get());
        var tag = chip.getOrCreateTag();
        tag.putInt("PorterMode", 1);
        tag.putLong("Source", helper.absolutePos(src).asLong());
        tag.putLong("Dest", helper.absolutePos(dest).asLong());
        String dim = helper.getLevel().dimension().location().toString();
        tag.putString("SourceDim", dim);
        tag.putString("DestDim", dim);
        v.getInventory().setItem(com.automatavalkyries.entity.ValkyrieInventory.CHIP, chip);
        WeaponGameTests.forceChunks(helper, v);
        helper.succeedWhen(() -> {
            var be = helper.getBlockEntity(dest);
            helper.assertTrue(be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel
                    && barrel.countItem(net.minecraft.world.item.Items.IRON_INGOT) == 20, "the porter did not move the ingots (pickup=" + v.getInventory().hasPickup()
                    + " pos=" + helper.relativeVec(v.position()) + ")");
            v.releaseAndDiscard();
        });
    }

    /** 非戦闘のユニット（2026-10-29）: 付けていると、狙ってくるゾンビがいても相手を持たない */
    @GameTest(template = TEMPLATE, batch = "valkyrie_daily_porter", timeoutTicks = 120)
    public static void valkyrieNonCombatUnitDoesNotFight(GameTestHelper helper) {
        clearAround(helper);
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        v.setMode(ValkyrieEntity.Mode.WAIT);
        v.setEnergy(ValkyrieEntity.MAX_ENERGY);
        v.getInventory().setItem(com.automatavalkyries.entity.ValkyrieInventory.CHIP,
                new ItemStack(com.automatavalkyries.registry.ModValkyrieUnits.CHIPS.get(com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.RANCHER).get()));
        var zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, 5, 2, 5);
        zombie.setTarget(v);
        WeaponGameTests.forceChunks(helper, v, zombie);
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(com.automatavalkyries.entity.ai.ValkyrieWork.nonCombat(v), "the rancher chip is not a non-combat unit");
            helper.assertTrue(v.getTarget() == null, "a non-combat valkyrie picked a target: " + v.getTarget());
            zombie.discard();
            v.releaseAndDiscard();
            helper.succeed();
        });
    }

    /** 農作ユニット（2026-10-29）: 実った小麦を穫って、同じ所に植え直す */
    @GameTest(template = TEMPLATE, batch = "valkyrie_daily_porter", timeoutTicks = 400)
    public static void valkyrieHarvesterReplantsWheat(GameTestHelper helper) {
        clearAround(helper);
        net.minecraft.core.BlockPos soil = new net.minecraft.core.BlockPos(3, 0, 3), crop = soil.above();
        helper.setBlock(soil, net.minecraft.world.level.block.Blocks.FARMLAND);
        helper.setBlock(crop, ((net.minecraft.world.level.block.CropBlock) net.minecraft.world.level.block.Blocks.WHEAT).getStateForAge(7));
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        v.setMode(ValkyrieEntity.Mode.WAIT);
        v.setEnergy(ValkyrieEntity.MAX_ENERGY);
        v.getInventory().setItem(com.automatavalkyries.entity.ValkyrieInventory.BACKPACK,
                new ItemStack(com.automatavalkyries.registry.ModValkyrieUnits.BACKPACKS.get(com.automatavalkyries.item.unit.ValkyrieUnits.BackpackKind.HARVESTER).get()));
        WeaponGameTests.forceChunks(helper, v);
        helper.succeedWhen(() -> {
            var state = helper.getBlockState(crop);
            helper.assertTrue(state.is(net.minecraft.world.level.block.Blocks.WHEAT)
                    && ((net.minecraft.world.level.block.CropBlock) net.minecraft.world.level.block.Blocks.WHEAT).getAge(state) == 0,
                    "the wheat was not harvested and replanted: " + state + " pos=" + helper.relativeVec(v.position()) + " goals=" + v.goalSelectorRunning());
            v.releaseAndDiscard();
        });
    }

    /** 戦術級コード: 近くの自分の兵姫（同調率・コアの電力が足りる）が承認してコアから払い、クールタイム中はもう一度は通らない。同調率が足りなければ通らない */
    @GameTest(template = TEMPLATE, batch = "valkyrie_tactical", timeoutTicks = 100)
    public static void tacticalProtocolApproval(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(); // 通信の無い試験用のプレイヤー（ServerPlayer の試験用は送信で落ちる）
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 2, 2, 2);
        v.setOwnerUUID(player.getUUID());
        v.setEnergy(ValkyrieEntity.MAX_ENERGY);
        ItemStack core = ((com.automatavalkyries.weapon.LuminalCoreItem) com.automatavalkyries.registry.ModWeapons.CORE_HIGH_DENSITY.get()).charged();
        v.getInventory().setItem(com.automatavalkyries.entity.ValkyrieInventory.CORE, core);
        var iris = com.automatavalkyries.tactical.TacticalProtocol.IRIS;
        net.minecraft.world.phys.Vec3 target = player.position();
        helper.assertTrue(com.automatavalkyries.tactical.TacticalProtocols.tryRequest(player, iris, target).approver() == null, "approved with sync 0");
        v.addSync(100.0F, "test");
        int before = com.automatavalkyries.item.ItemEnergy.get(v.getCore());
        helper.assertTrue(com.automatavalkyries.tactical.TacticalProtocols.tryRequest(player, iris, target).approver() == v, "IRIS was not approved");
        helper.assertTrue(before - com.automatavalkyries.item.ItemEnergy.get(v.getCore()) == iris.fe, "the core did not pay " + iris.fe);
        helper.assertTrue(com.automatavalkyries.tactical.TacticalProtocols.tryRequest(player, iris, target).result().equals("cooldown"), "approved again during the cooldown");
        v.releaseAndDiscard();
        helper.succeed();
    }

    /** 墜落ポッド:組み立てると深く眠る兵姫・端末・チェストがあり、兵姫は右クリックでは持ち主にできず、端末（プロトコア）で目覚めて持ち主になる */
    @GameTest(template = TEMPLATE, batch = "valkyrie_pod", timeoutTicks = 100)
    public static void crashedPodWakesWithProtoCore(GameTestHelper helper) {
        clearAround(helper);
        net.minecraft.core.BlockPos surface = helper.absolutePos(new net.minecraft.core.BlockPos(4, 0, 4)); // 空の型の床の上
        var chunkPos = new net.minecraft.world.level.ChunkPos(surface);
        var manager = (net.minecraft.world.level.entity.PersistentEntitySectionManager<net.minecraft.world.entity.Entity>)
                ((com.automatavalkyries.mixin.ServerLevelTickListAccessor) helper.getLevel()).automata_valkyries$getEntityManager();
        manager.updateChunkStatus(chunkPos, net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);
        helper.getLevel().setChunkForced(chunkPos.x, chunkPos.z, true);
        com.automatavalkyries.world.CrashedPods.place(helper.getLevel(), surface, net.minecraft.util.RandomSource.create(1)); // テストの世界は海のバイオームなので場所の確認は飛ばす
        var sleepers = helper.getLevel().getEntitiesOfClass(ValkyrieEntity.class, new net.minecraft.world.phys.AABB(surface).inflate(4), ValkyrieEntity::isPodSleeper);
        helper.assertTrue(sleepers.size() == 1, "expected one sleeping valkyrie, found " + sleepers.size());
        ValkyrieEntity v = sleepers.get(0);
        helper.assertTrue(v.isAsleep() && v.getOwnerUUID() == null, "the pod valkyrie was not asleep and unowned");
        Player player = helper.makeMockPlayer();
        helper.assertTrue(v.wakeFromPod(player, com.automatavalkyries.item.ProtoCoreItem.CAPACITY), "the valkyrie did not wake");
        helper.assertTrue(!v.isAsleep() && !v.isPodSleeper() && player.getUUID().equals(v.getOwnerUUID()) && v.getEnergy() >= com.automatavalkyries.item.ProtoCoreItem.CAPACITY,
                "the woken valkyrie was not owned and powered");
        v.releaseAndDiscard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "valkyrie_daily_charge", timeoutTicks = 800)
    public static void valkyrieChargesAtStation(GameTestHelper helper) {
        clearAround(helper);
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(2, 0, 1); // すぐ隣
        helper.setBlock(pos, com.automatavalkyries.registry.ModMachines.VALKYRIE_CHARGER.get());
        var charger = (com.automatavalkyries.machine.ValkyrieChargerBlockEntity) helper.getBlockEntity(pos);
        for (int i = 0; i < 100; i++) charger.energy().receiveEnergy(10_000, false); // 1 回に 10,000 FE まで
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        v.setMode(ValkyrieEntity.Mode.WAIT);
        v.setEnergy(ValkyrieEntity.MAX_ENERGY / 10);
        WeaponGameTests.forceChunks(helper, v);
        helper.succeedWhen(() -> {
            helper.assertTrue(v.getEnergy() > ValkyrieEntity.MAX_ENERGY / 2, "did not charge at the station: " + v.getEnergy() + " pos=" + helper.relativeVec(v.position()) + " path=" + v.getNavigation().getPath()
                    + " goals=" + v.goalSelectorRunning() + " stored=" + charger.stored() + " claimant=" + charger.claimant());
            v.releaseAndDiscard();
        });
    }

    /** 電力が 30% を割ると射撃の兵装を持ち物の予備の近接の兵装と持ち替え、60% で戻す */
    @GameTest(template = TEMPLATE, batch = "valkyrie_daily", timeoutTicks = 400)
    public static void valkyrieSwapsToMeleeOnLowPower(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        v.setItemSlot(EquipmentSlot.MAINHAND, ((TacticalWeaponItem) ModWeapons.LINEAR_RAIL_RIFLE.get()).charged());
        v.getInventory().setItem(com.automatavalkyries.entity.ValkyrieInventory.SPARE, ((TacticalWeaponItem) ModWeapons.PHOTON_EDGE.get()).charged());
        v.setEnergy(ValkyrieEntity.MAX_ENERGY / 5);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(v.getMainHandItem().is(ModWeapons.PHOTON_EDGE.get()), "did not swap to the melee spare at low power"))
                .thenExecute(() -> {
                    helper.assertTrue(v.getInventory().getItem(com.automatavalkyries.entity.ValkyrieInventory.SPARE).is(ModWeapons.LINEAR_RAIL_RIFLE.get()), "the rifle was not stored in the spare slot");
                    v.setEnergy(ValkyrieEntity.MAX_ENERGY * 7 / 10);
                })
                .thenWaitUntil(() -> helper.assertTrue(v.getMainHandItem().is(ModWeapons.LINEAR_RAIL_RIFLE.get()), "did not swap back when power recovered: energy=" + v.getEnergy() + " main=" + v.getMainHandItem() + " spare=" + v.getInventory().getItem(com.automatavalkyries.entity.ValkyrieInventory.SPARE) + " slot=" + v.combat().swappedSlotForTest()))
                .thenExecute(v::releaseAndDiscard)
                .thenSucceed();
    }

    /** 二刀流: 左手の物を使う間だけ両手と攻撃力が入れ替わり、終われば元へ戻る */
    @GameTest(template = TEMPLATE, batch = "valkyrie_daily")
    public static void dualWieldSwapsHandsAndAttack(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        ItemStack main = ((TacticalWeaponItem) ModWeapons.PHOTON_EDGE.get()).charged();
        ItemStack off = new ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
        v.setItemSlot(EquipmentSlot.MAINHAND, main);
        v.setItemSlot(EquipmentSlot.OFFHAND, off);
        WeaponGameTests.forceChunks(helper, v);
        int[] waited = {0};
        helper.startSequence().thenWaitUntil(() -> {
            WeaponGameTests.kickStuckChunks(helper, waited, v);
            helper.assertTrue(helper.getLevel().getEntity(v.getUUID()) == v, "not in the world yet");
        }).thenIdle(3).thenExecute(() -> {
            helper.assertTrue(com.automatavalkyries.weapon.DualWield.isDual(v), "photon edge + iron sword is not dual");
            double before = v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            double[] during = {0};
            com.automatavalkyries.weapon.DualWield.asMainHand(v, () -> {
                helper.assertTrue(v.getMainHandItem() == off, "the off-hand item was not moved to the main hand");
                during[0] = v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            });
            double sword = 1.0 + ((net.minecraft.world.item.SwordItem) net.minecraft.world.item.Items.IRON_SWORD).getDamage();
            helper.assertTrue(Math.abs(during[0] - sword) < 0.001, "attack damage was not the off-hand item's: " + during[0] + " (expected " + sword + ", before " + before + ")");
            helper.assertTrue(v.getMainHandItem() == main && v.getOffhandItem() == off, "hands were not restored");
            helper.assertTrue(Math.abs(v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) - before) < 0.001, "attack damage was not restored");
            v.releaseAndDiscard();
        }).thenSucceed();
    }

    /** 調べ: まわりのチェストと林檎の数 */
    private static String chestsAround(GameTestHelper helper, net.minecraft.core.BlockPos rel) {
        StringBuilder sb = new StringBuilder();
        net.minecraft.core.BlockPos c = helper.absolutePos(rel);
        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(c.offset(-16, -4, -16), c.offset(16, 4, 16))) {
            if (helper.getLevel().getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
                sb.append(helper.relativePos(p.immutable())).append(":").append(chest.countItem(net.minecraft.world.item.Items.APPLE)).append(" ");
            }
        }
        return sb.toString();
    }

    /**
     * テストの場所のまわり 16 ブロックのチェスト・樽・兵姫用充電台を中身ごと消し、落ちている物も片付ける（テストの世界は回をまたいで残り、
     * 前の回の物へ兵姫が運んで・座りに行っていた）。このテストだけのバッチで使う（同じ時に動く他のテストの物を消さないよう）
     */
    private static void clearAround(GameTestHelper helper) {
        net.minecraft.core.BlockPos c = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(c.offset(-16, -4, -16), c.offset(20, 6, 20))) {
            var be = helper.getLevel().getBlockEntity(p);
            if (be instanceof net.minecraft.world.Container container) container.clearContent();
            if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity || be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity
                    || be instanceof com.automatavalkyries.machine.ValkyrieChargerBlockEntity) {
                helper.getLevel().setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
        helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(c).inflate(24))
                .forEach(net.minecraft.world.entity.Entity::discard);
        // 前の回・ほかのテストの残りの敵（兵姫が戦い始めてお掃除・運搬をしなかった。2026-10-29）。保護された物は外さない
        helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(c).inflate(32),
                        m -> m instanceof net.minecraft.world.entity.monster.Enemy && !(m instanceof com.automatavalkyries.protect.IEprotected))
                .forEach(net.minecraft.world.entity.Entity::discard);
    }

    /** 飛び道具らしい物の見分け: バニラの雪玉・Projectile を継がない速い小さな物は飛び道具、落ちている物・止まった物は違う。兵姫へ向かって来れば遠距離の脅威 */
    @GameTest(template = TEMPLATE, batch = "valkyrie_daily")
    public static void projectileLikeDetectsModdedShots(GameTestHelper helper) {
        var level = helper.getLevel();
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        var snowball = new net.minecraft.world.entity.projectile.Snowball(net.minecraft.world.entity.EntityType.SNOWBALL, level);
        snowball.setDeltaMovement(0.8, 0, 0);
        // Projectile を継がない「他 MOD の弾」の代わり: 速く動く落下ブロック
        var shot = net.minecraft.world.entity.item.FallingBlockEntity.fall(level, helper.absolutePos(new net.minecraft.core.BlockPos(4, 3, 1)),
                net.minecraft.world.level.block.Blocks.SAND.defaultBlockState());
        shot.setNoGravity(true);
        shot.setDeltaMovement(-0.8, 0, 0);
        var item = new net.minecraft.world.entity.item.ItemEntity(level, 0, 0, 0, new ItemStack(net.minecraft.world.item.Items.APPLE));
        item.setDeltaMovement(0.8, 0, 0);
        helper.assertTrue(com.automatavalkyries.weapon.ProjectileLike.is(snowball), "a moving snowball is not a projectile");
        helper.assertTrue(com.automatavalkyries.weapon.ProjectileLike.is(shot), "a fast non-Projectile shot is not projectile-like");
        helper.assertFalse(com.automatavalkyries.weapon.ProjectileLike.is(item), "a dropped item counted as a projectile");
        shot.setDeltaMovement(0, 0, 0);
        shot.xo = shot.getX(); shot.yo = shot.getY(); shot.zo = shot.getZ();
        helper.assertFalse(com.automatavalkyries.weapon.ProjectileLike.is(shot), "a still entity counted as a projectile");
        shot.discard();
        v.releaseAndDiscard();
        helper.succeed();
    }

    /** 役割のチップ（ヴァンガード・リアガード・メディック）は 1 つだけ。アタックセレクトは切り替えが読める。タレットは覚えた対象を返す */
    @GameTest(template = TEMPLATE, batch = "valkyrie_units")
    public static void valkyrieRoleChips(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        var inv = v.getInventory();
        var C = com.automatavalkyries.registry.ModValkyrieUnits.CHIPS;
        int chip = com.automatavalkyries.entity.ValkyrieInventory.CHIP;
        inv.setItem(chip, new ItemStack(C.get(com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.VANGUARD).get()));
        helper.assertFalse(inv.accepts(chip + 1, new ItemStack(C.get(com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.MEDIC).get())), "two role chips fit together");
        ItemStack mode = new ItemStack(C.get(com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.ATTACK_MODE).get());
        mode.getOrCreateTag().putBoolean("ChargeOnly", true);
        helper.assertTrue(inv.accepts(chip + 1, mode), "the attack select chip did not fit beside a role chip");
        inv.setItem(chip + 1, mode);
        ItemStack turret = new ItemStack(C.get(com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.TURRET).get());
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        list.add(net.minecraft.nbt.StringTag.valueOf("minecraft:zombie"));
        turret.getOrCreateTag().put("Targets", list);
        inv.setItem(chip + 2, turret);
        helper.assertTrue(com.automatavalkyries.entity.ai.ValkyrieUnitEffects.role(v) == com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.VANGUARD, "the role was not read");
        helper.assertTrue(Boolean.TRUE.equals(com.automatavalkyries.entity.ai.ValkyrieUnitEffects.chargeOnly(v)), "charge only was not read");
        var targets = com.automatavalkyries.entity.ai.ValkyrieUnitEffects.turretTargets(v);
        helper.assertTrue(targets != null && targets.size() == 1 && targets.get(0) == EntityType.ZOMBIE, "the turret targets were not read: " + targets);
        v.releaseAndDiscard();
        helper.succeed();
    }

    /** 特殊なフレーム: ルミナス一式で電力の最大 1.5 倍・消費 0.7 倍、テンペスト一式でスラスターの空中ダッシュ +2・滞空 1.5 倍 */
    @GameTest(template = TEMPLATE, batch = "valkyrie_units")
    public static void valkyrieSpecialFrames(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        var inv = v.getInventory();
        var F = com.automatavalkyries.registry.ModValkyrieUnits.FRAMES;
        int[] slots = {com.automatavalkyries.entity.ValkyrieInventory.HEAD, com.automatavalkyries.entity.ValkyrieInventory.CHEST, com.automatavalkyries.entity.ValkyrieInventory.LEGS};
        var parts = com.automatavalkyries.item.unit.ValkyrieUnits.FramePart.values();
        for (int i = 0; i < 3; i++) inv.setItem(slots[i], new ItemStack(F.get(com.automatavalkyries.item.unit.ValkyrieUnits.FrameType.LUMINOUS).get(parts[i]).get()));
        helper.assertTrue(v.maxEnergy() == ValkyrieEntity.MAX_ENERGY * 3 / 2, "Luminous did not raise max power: " + v.maxEnergy());
        helper.assertTrue(com.automatavalkyries.entity.ai.ValkyrieUnitEffects.powerScale(v) < 0.75F, "Luminous did not cut power use");
        for (int i = 0; i < 3; i++) inv.setItem(slots[i], new ItemStack(F.get(com.automatavalkyries.item.unit.ValkyrieUnits.FrameType.TEMPEST).get(parts[i]).get()));
        inv.setItem(com.automatavalkyries.entity.ValkyrieInventory.BACKPACK, new ItemStack(
                com.automatavalkyries.registry.ModValkyrieUnits.BACKPACKS.get(com.automatavalkyries.item.unit.ValkyrieUnits.BackpackKind.THRUSTER).get()));
        var m = com.automatavalkyries.entity.ai.ValkyrieUnitEffects.maneuver(v);
        helper.assertTrue(m != null && m.airDashes() == 5 && m.hoverTicks() == 90, "Tempest did not boost the thruster: " + m);
        helper.assertTrue(v.maxEnergy() == ValkyrieEntity.MAX_ENERGY, "max power stayed raised without Luminous");
        v.releaseAndDiscard();
        helper.succeed();
    }

    /** 補助の背部ユニット: 増設バッテリーで電力の最大 +30,000、リザレクションコアが機能停止した味方の兵姫を再起動する */
    @GameTest(template = TEMPLATE, batch = "valkyrie_units", timeoutTicks = 200)
    public static void valkyrieSupportUnits(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        ValkyrieEntity core = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        ValkyrieEntity down = helper.spawn(ModEntities.VALKYRIE.get(), 4, 2, 1);
        core.setOwnerUUID(owner);
        down.setOwnerUUID(owner);
        var B = com.automatavalkyries.registry.ModValkyrieUnits.BACKPACKS;
        int back = com.automatavalkyries.entity.ValkyrieInventory.BACKPACK;
        down.getInventory().setItem(back, new ItemStack(B.get(com.automatavalkyries.item.unit.ValkyrieUnits.BackpackKind.SPARE_BATTERY).get()));
        helper.assertTrue(down.maxEnergy() == ValkyrieEntity.MAX_ENERGY + 30_000, "the spare battery did not raise max power: " + down.maxEnergy());
        core.getInventory().setItem(back, new ItemStack(B.get(com.automatavalkyries.item.unit.ValkyrieUnits.BackpackKind.RESURRECTION_CORE).get()));
        core.setEnergy(ValkyrieEntity.MAX_ENERGY);
        down.shutdown();
        helper.succeedWhen(() -> {
            helper.assertFalse(down.isShutdown(), "the resurrection core did not reboot the ally");
            helper.assertTrue(DirectRead.health(down) >= down.getMaxHealth() * 0.45F, "the ally was not rebooted at half HP");
            core.releaseAndDiscard();
            down.releaseAndDiscard();
        });
    }

    /** メディック: 味方の兵姫の HP が半分を割ったら駆け寄ってナノマシン溶液で修理する */
    @GameTest(template = TEMPLATE, batch = "valkyrie_units", timeoutTicks = 300)
    public static void valkyrieMedicRepairsAlly(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        ValkyrieEntity medic = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        ValkyrieEntity hurt = helper.spawn(ModEntities.VALKYRIE.get(), 4, 2, 1);
        medic.setOwnerUUID(owner);
        hurt.setOwnerUUID(owner);
        medic.setMode(com.automatavalkyries.entity.ValkyrieEntity.Mode.WAIT);
        hurt.setMode(com.automatavalkyries.entity.ValkyrieEntity.Mode.WAIT);
        medic.setEnergy(ValkyrieEntity.MAX_ENERGY);
        hurt.setEnergy(ValkyrieEntity.MAX_ENERGY);
        medic.getInventory().setItem(com.automatavalkyries.entity.ValkyrieInventory.CHIP,
                new ItemStack(com.automatavalkyries.registry.ModValkyrieUnits.CHIPS.get(com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.MEDIC).get()));
        medic.getInventory().setItem(com.automatavalkyries.entity.ValkyrieInventory.AMMO, new ItemStack(ModWeapons.NANO_SOLUTION.get(), 4));
        hurt.setHealth(hurt.getMaxHealth() * 0.3F);
        float before = DirectRead.health(hurt);
        helper.succeedWhen(() -> {
            helper.assertTrue(DirectRead.health(hurt) > before, "the medic did not repair the ally");
            helper.assertTrue(medic.getInventory().getItem(com.automatavalkyries.entity.ValkyrieInventory.AMMO).getCount() == 3, "no nanomachine solution was used");
            medic.releaseAndDiscard();
            hurt.releaseAndDiscard();
        });
    }

    /** ハイマニューバスラスター:空中ダッシュは着地まで回数だけ続けて出せ（向きのクールタイムは掛けない）、空中ジャンプも回数だけ。地上では出ない */
    @GameTest(template = TEMPLATE, batch = "valkyrie_units", timeoutTicks = 200)
    public static void valkyrieThrusterAirChain(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 6, 1);
        v.setNoGravity(true); // 空中に留めて数える
        v.setEnergy(ValkyrieEntity.MAX_ENERGY);
        v.getInventory().setItem(com.automatavalkyries.entity.ValkyrieInventory.BACKPACK, new ItemStack(
                com.automatavalkyries.registry.ModValkyrieUnits.BACKPACKS.get(com.automatavalkyries.item.unit.ValkyrieUnits.BackpackKind.THRUSTER).get()));
        int[] dashes = {0};
        net.minecraft.world.phys.Vec3 fwd = new net.minecraft.world.phys.Vec3(1, 0, 0);
        // 何も無い遠くの空で数える（試験の世界には前の回の試験のロスト・カタストロフィ＝消せない保護付きのボスが残っていて、その電力の吸い取りの範囲が
        // この試験の場所まで届き、電力を吸われて休眠し空中ジャンプが出ずに落ちていた。2026-10-31）
        int slot = (int) Math.floorMod(System.nanoTime() / 1000L, 2000L);
        net.minecraft.world.phys.Vec3 start = new net.minecraft.world.phys.Vec3(-3_000_000 + slot * 64 + 0.5, 200, -3_000_000 + 0.5);
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(start));
        helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
        v.moveTo(start.x, start.y, start.z);
        var seq = helper.startSequence().thenIdle(40); // 遠くの区画が動き出すまで待つ（動く前は Tick が来ず、間隔が減らない）
        for (int i = 0; i < 4; i++) {
            seq = seq.thenExecute(() -> {
                // 毎回もとの場所へ戻してから出す（ダッシュで前へ流れて隣の試験の場所＝ロスト・カタストロフィの電力の吸い取りの範囲へ入り、
                // 電力を吸われて休眠し空中ジャンプが出ずに落ちていた。2026-10-31）
                v.moveTo(start.x, start.y, start.z);
                v.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                if (v.combat().airDash(fwd, 0.6)) dashes[0]++;
            }).thenIdle(com.automatavalkyries.entity.ai.ValkyrieCombat.AIR_GAP + 1);
        }
        seq.thenExecute(() -> {
            v.moveTo(start.x, start.y, start.z);
            helper.assertTrue(dashes[0] == 3, "the T1 thruster should allow 3 air dashes before landing, got " + dashes[0] + " (" + v.combat().airState() + ")");
            String before = v.combat().airState();
            helper.assertTrue(v.combat().airJump(), "the air jump did not fire (" + before + ")");
        }).thenIdle(com.automatavalkyries.entity.ai.ValkyrieCombat.AIR_GAP + 1).thenExecute(() -> {
            helper.assertTrue(v.combat().airJump(), "the T1 thruster did not allow a second air jump");
        }).thenIdle(com.automatavalkyries.entity.ai.ValkyrieCombat.AIR_GAP + 1).thenExecute(() -> {
            helper.assertFalse(v.combat().airJump(), "the T1 thruster allowed a third air jump");
            v.releaseAndDiscard();
            helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
        }).thenSucceed();
    }

    /** 装備ユニット: 外装は部位の枠だけに入り、一式で装甲値が上がり重さが増える。ガリア・フォートレス（背部）は 50 以下のダメージを 1 にする。チップは同じ種類 1 つまで */
    @GameTest(template = TEMPLATE, batch = "valkyrie_units", timeoutTicks = 200)
    public static void valkyrieUnitsApply(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        var inv = v.getInventory();
        var F = com.automatavalkyries.registry.ModValkyrieUnits.FRAMES;
        ItemStack head = new ItemStack(F.get(com.automatavalkyries.item.unit.ValkyrieUnits.FrameType.VALKYRIE).get(com.automatavalkyries.item.unit.ValkyrieUnits.FramePart.HEAD).get());
        ItemStack chest = new ItemStack(F.get(com.automatavalkyries.item.unit.ValkyrieUnits.FrameType.VALKYRIE).get(com.automatavalkyries.item.unit.ValkyrieUnits.FramePart.CHEST).get());
        ItemStack legs = new ItemStack(F.get(com.automatavalkyries.item.unit.ValkyrieUnits.FrameType.VALKYRIE).get(com.automatavalkyries.item.unit.ValkyrieUnits.FramePart.LEGS).get());
        ItemStack gallia = new ItemStack(com.automatavalkyries.registry.ModValkyrieUnits.BACKPACKS.get(com.automatavalkyries.item.unit.ValkyrieUnits.BackpackKind.GALLIA_FORTRESS).get());
        helper.assertFalse(inv.accepts(com.automatavalkyries.entity.ValkyrieInventory.CHEST, head), "a head frame fit the chest slot");
        helper.assertTrue(inv.accepts(com.automatavalkyries.entity.ValkyrieInventory.HEAD, head), "a head frame did not fit the head slot");
        ItemStack chip = new ItemStack(com.automatavalkyries.registry.ModValkyrieUnits.CHIPS.get(com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.PRECISION).get());
        inv.setItem(com.automatavalkyries.entity.ValkyrieInventory.CHIP, chip.copy());
        helper.assertFalse(inv.accepts(com.automatavalkyries.entity.ValkyrieInventory.CHIP + 1, chip), "the same chip fit twice");
        double baseArmor = v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        float baseWeight = v.carriedWeight();
        float baseMaxHp = v.getMaxHealth();
        float baseShield = v.getMaxShield();
        inv.setItem(com.automatavalkyries.entity.ValkyrieInventory.HEAD, head);
        inv.setItem(com.automatavalkyries.entity.ValkyrieInventory.CHEST, chest);
        inv.setItem(com.automatavalkyries.entity.ValkyrieInventory.LEGS, legs);
        com.automatavalkyries.entity.ai.ValkyrieUnitEffects.applyAttributes(v);
        helper.assertTrue(inv.frameSet() == com.automatavalkyries.item.unit.ValkyrieUnits.FrameType.VALKYRIE, "the Valkyrie frame set was not recognised");
        helper.assertTrue(Math.abs(v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) - baseArmor - 12) < 0.01, "the set did not add 12 armor");
        helper.assertTrue(Math.abs(v.getMaxHealth() - baseMaxHp - 20) < 0.01, "the set did not add 20 max health");
        helper.assertTrue(Math.abs(v.getMaxShield() - baseShield - 30) < 0.01, "the set did not add 30 max shield");
        helper.assertTrue(v.carriedWeight() > baseWeight, "frames added no weight");
        // ヴァルキリー系は Mk を混ぜても一式（一番低い Mk）
        inv.setItem(com.automatavalkyries.entity.ValkyrieInventory.CHEST, new ItemStack(F.get(com.automatavalkyries.item.unit.ValkyrieUnits.FrameType.VALKYRIE_MK4).get(com.automatavalkyries.item.unit.ValkyrieUnits.FramePart.CHEST).get()));
        helper.assertTrue(inv.frameSet() == com.automatavalkyries.item.unit.ValkyrieUnits.FrameType.VALKYRIE, "a mixed-Mk Valkyrie set did not count as the lowest Mk");
        // ガリア・フォートレス（背部ユニット）: 50 以下のダメージは 1
        helper.assertFalse(inv.accepts(com.automatavalkyries.entity.ValkyrieInventory.CHEST, gallia), "the Gallia back unit fit the chest slot");
        inv.setItem(com.automatavalkyries.entity.ValkyrieInventory.BACKPACK, gallia);
        float hp = com.automatavalkyries.protect.DirectRead.health(v) + v.getShield();
        v.hurt(helper.getLevel().damageSources().generic(), 30.0F);
        float lost = hp - (com.automatavalkyries.protect.DirectRead.health(v) + v.getShield());
        helper.assertTrue(lost <= 1.01F, "Gallia did not turn a 30 hit into 1: lost " + lost);
        v.releaseAndDiscard();
        helper.succeed();
    }
}
