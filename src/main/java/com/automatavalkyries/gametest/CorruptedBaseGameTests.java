package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.ChronoGuardianEntity;
import com.automatavalkyries.entity.chrono.MimicContainerEntity;
import com.automatavalkyries.entity.chrono.MortarShellEntity;
import com.automatavalkyries.entity.chrono.PhaseCondenserEntity;
import com.automatavalkyries.entity.chrono.TrooperEntity;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.world.CorruptedBases;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 侵食基地（2026-10-15）: 3 種のダンジョンが登録されている・山なりの砲弾が狙った所に落ちる・ミミックは攻撃されるまで化けている・ガーディアンの盾は正面だけ受ける。
 * 区画によっては世界の側でエンティティが動かないので、どれも Tick を待たずに直に確かめる
 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class CorruptedBaseGameTests {

    /** 物資倉庫・クロノ・アウトリポスト・ディメンション・ファウンドリの構造物と置き方がある */
    @GameTest(template = "empty", batch = "corrupted_base", timeoutTicks = 20)
    public static void corruptedBasesRegistered(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        for (String id : new String[]{"supply_depot", "chrono_outpost", "dimension_foundry", "chrono_bastion"}) {
            ResourceLocation key = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, id);
            helper.assertTrue(access.registryOrThrow(Registries.STRUCTURE).containsKey(key), "structure " + id + " is not registered");
            helper.assertTrue(access.registryOrThrow(Registries.STRUCTURE_SET).containsKey(key), "structure set " + id + " is not registered");
        }
        // 山なりの砲弾: 投げた物の動き（速さの分だけ動き、0.99 倍して重力を引く）を数えると、狙った所に落ちる
        Vec3 from = new Vec3(0, 5, 0), to = new Vec3(18, -2, -7);
        int flight = 40;
        Vec3 v = MortarShellEntity.launchVelocity(from, to, flight, MortarShellEntity.GRAVITY);
        Vec3 p = from;
        for (int i = 0; i < flight; i++) {
            p = p.add(v);
            v = v.scale(0.99).subtract(0, MortarShellEntity.GRAVITY, 0);
        }
        helper.assertTrue(p.distanceTo(to) < 0.05, "a lobbed shell missed its mark: " + p + " instead of " + to);
        // 実際に置いて部屋が描かれるかを確かめる: ほかの試験から離れた所の塊を読み込み、敵は置かずに 3 種を置き、それぞれの壁の材質が描かれたかを見る
        // （読み込んだばかりの塊に置いた敵は世界の側ですぐには探せず、残すと試験の世界に溜まるので、敵の置き方はゲームで確かめる）。
        // 試験の世界はふつうの地形なので、水の上・急な斜面で断られたら場所を変えて試し、地表の高さのまわりを探す
        var level = helper.getLevel();
        var server = level.getServer();
        var generator = level.getChunkSource().getGenerator();
        net.minecraft.core.BlockPos base = helper.absolutePos(net.minecraft.core.BlockPos.ZERO).offset(800, 0, 800);
        String[] ids = {"supply_depot", "chrono_outpost", "dimension_foundry"};
        net.minecraft.world.level.block.Block[] walls = {com.automatavalkyries.registry.ModTechBlocks.TECH_PANEL_WHITE.get(),
                com.automatavalkyries.registry.ModTechBlocks.TECH_PANEL_DARK.get(), com.automatavalkyries.registry.ModTechBlocks.TECH_PANEL_DARK.get()};
        int gates = 0, glass = 0;
        com.automatavalkyries.world.CorruptedBases.populate = false;
        try {
            for (int i = 0; i < ids.length; i++) {
                boolean placed = false;
                java.util.List<String> said = new java.util.ArrayList<>();
                for (int attempt = 0; attempt < 8 && !placed; attempt++) {
                    net.minecraft.core.BlockPos at = base.offset(i * 160, 0, attempt * 112);
                    net.minecraft.world.level.ChunkPos c = new net.minecraft.world.level.ChunkPos(at);
                    for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                        level.setChunkForced(c.x + dx, c.z + dz, true);
                        level.getChunk(c.x + dx, c.z + dz);
                    }
                    net.minecraft.commands.CommandSource listener = new net.minecraft.commands.CommandSource() {
                        @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) { said.add(message.getString()); }
                        @Override public boolean acceptsSuccess() { return true; }
                        @Override public boolean acceptsFailure() { return true; }
                        @Override public boolean shouldInformAdmins() { return false; }
                    };
                    var source = new net.minecraft.commands.CommandSourceStack(listener, Vec3.atCenterOf(at), net.minecraft.world.phys.Vec2.ZERO, level, 4,
                            "gametest", net.minecraft.network.chat.Component.literal("gametest"), server, null);
                    if (server.getCommands().performPrefixedCommand(source, "place structure " + AutomataValkyries.MODID + ":" + ids[i] + " " + at.getX() + " " + at.getY() + " " + at.getZ()) > 0) {
                        placed = true;
                        int cx = c.getMiddleBlockX(), cz = c.getMiddleBlockZ();
                        int surface = generator.getFirstOccupiedHeight(cx, cz, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level,
                                level.getChunkSource().randomState());
                        int found = 0;
                        for (net.minecraft.core.BlockPos q : net.minecraft.core.BlockPos.betweenClosed(cx - 48, surface - 20, cz - 48, cx + 48, surface + 12, cz + 48)) {
                            var st = level.getBlockState(q);
                            if (st.is(walls[i])) found++;
                            if (st.is(com.automatavalkyries.registry.ModTechBlocks.CHRONO_GATE.get())) {
                                gates++;
                                // 試験の世界には前の回に置いた別の基地が残ることがある（試験が増えると置く所がずれる）ので、知っている基地の名ならよい（2026-10-20）
                                helper.assertTrue(level.getBlockEntity(q) instanceof com.automatavalkyries.block.ChronoGateBlock.Gate g && java.util.Arrays.asList(ids).contains(g.kind()),
                                        "a gate in " + ids[i] + " does not know its dungeon: "
                                                + (level.getBlockEntity(q) instanceof com.automatavalkyries.block.ChronoGateBlock.Gate g2 ? g2.kind() : "no gate entity"));
                            }
                            if (st.is(com.automatavalkyries.registry.ModTechBlocks.TECH_GLASS.get()) || st.is(com.automatavalkyries.registry.ModTechBlocks.TECH_SKY_PANEL.get())) glass++;
                        }
                        helper.assertTrue(found > 20, ids[i] + " drew no rooms (" + found + " wall blocks around " + cx + ", " + surface + ", " + cz + "; " + said + ")");
                    }
                    for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) level.setChunkForced(c.x + dx, c.z + dz, false);
                }
                helper.assertTrue(placed, "could not place " + ids[i] + " anywhere: " + said);
            }
            helper.assertTrue(gates > 0, "no chrono gate was placed in any dungeon");
            helper.assertTrue(glass > 0, "no corridor roof (glass or sky panel) was placed");
        } finally {
            com.automatavalkyries.world.CorruptedBases.populate = true;
        }
        helper.succeed();
    }

    /** ミミックは攻撃されるまで化けている。ガーディアンの盾は正面の攻撃だけ減らす */
    @GameTest(template = "empty", batch = "corrupted_base", timeoutTicks = 20)
    public static void mimicWakesAndGuardianShieldBlocksFront(GameTestHelper helper) {
        var level = helper.getLevel();
        MimicContainerEntity mimic = helper.spawn(ModEntities.MIMIC_CONTAINER.get(), 2, 2, 2);
        helper.assertTrue(mimic.isDormant(), "a new mimic was not disguised");
        var pig = helper.spawn(EntityType.PIG, 4, 2, 4);
        pig.setNoAi(true);
        mimic.hurt(level.damageSources().mobAttack(pig), 1.0F);
        helper.assertFalse(mimic.isDormant(), "hitting a mimic did not wake it");
        mimic.discard();

        ChronoGuardianEntity guardian = helper.spawn(ModEntities.CHRONO_GUARDIAN.get(), 6, 2, 6);
        guardian.setNoAi(true);
        guardian.setYRot(0.0F);
        guardian.yBodyRot = 0.0F;                          // 南（+Z）を向く
        Vec3 g = guardian.position();
        pig.moveTo(g.x, g.y, g.z + 3.0, 0.0F, 0.0F);        // 正面
        float hp = guardian.getHealth();
        guardian.hurt(level.damageSources().mobAttack(pig), 10.0F);
        float front = hp - guardian.getHealth();
        guardian.invulnerableTime = 0;
        pig.moveTo(g.x, g.y, g.z - 3.0, 0.0F, 0.0F);        // 背後
        hp = guardian.getHealth();
        guardian.hurt(level.damageSources().mobAttack(pig), 10.0F);
        float back = hp - guardian.getHealth();
        helper.assertTrue(front > 0 && back > front * 3.0F, "the shield did not cut only frontal damage (front " + front + ", back " + back + ")");
        guardian.discard();
        pig.discard();
        helper.succeed();
    }

    /** 雑魚の最大 HP はダンジョンのティアで倍になる（倉庫 ×1・基地 ×1.5・工場 ×2）。二度掛けしない・コンデンサーは除く */
    @GameTest(template = "empty", batch = "corrupted_base", timeoutTicks = 20)
    public static void baseMobHealthScalesWithTier(GameTestHelper helper) {
        var level = helper.getLevel();
        double[] expect = {100.0, 150.0, 200.0, 300.0};
        CorruptedBases.Kind[] kinds = CorruptedBases.Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            TrooperEntity t = ModEntities.CHRONO_TROOPER.get().create(level);
            kinds[i].scaleHealth(t);
            kinds[i].scaleHealth(t);
            helper.assertTrue(Math.abs(t.getMaxHealth() - expect[i]) < 0.01 && Math.abs(t.getHealth() - expect[i]) < 0.01,
                    kinds[i].id + ": trooper hp " + t.getHealth() + "/" + t.getMaxHealth() + ", expected " + expect[i]);
        }
        PhaseCondenserEntity c = ModEntities.PHASE_CONDENSER.get().create(level);
        float before = c.getMaxHealth();
        CorruptedBases.Kind.DIMENSION_FOUNDRY.scaleHealth(c);
        helper.assertTrue(c.getMaxHealth() == before, "condenser hp must not scale");
        helper.assertTrue(CorruptedBases.Kind.byId("chrono_outpost") == CorruptedBases.Kind.CHRONO_OUTPOST, "byId");
        helper.succeed();
    }

    /**
     * 空中庭園要塞（クロノ・バスティオン。2026-10-19）: 実際に置くと、空に塔の真ん中の眠れる時の門（膜 3×5、戸口を向く、要石付き）、芝と水路の庭、下の岩、
     * 東西南北の着陸の甲板（先の縁が警告の帯）が描かれる。中ボスは体力の倍率を受けず、雑魚は ×3
     */
    @GameTest(template = "empty", batch = "corrupted_base", timeoutTicks = 20)
    public static void chronoBastionFloatsWithKeeperAltarAndPortal(GameTestHelper helper) {
        var level = helper.getLevel();
        net.minecraft.core.BlockPos at = helper.absolutePos(net.minecraft.core.BlockPos.ZERO).offset(-1800, 0, 2600); // 前の形の要塞が残る所（試験の世界は残る）を避ける
        net.minecraft.world.level.ChunkPos c = new net.minecraft.world.level.ChunkPos(at);
        int portals = 0, keystones = 0, grass = 0, water = 0, rock = 0, lowestGate = Integer.MAX_VALUE;
        CorruptedBases.populate = false;
        try {
            for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                level.setChunkForced(c.x + dx, c.z + dz, true);
                level.getChunk(c.x + dx, c.z + dz);
            }
            var server = level.getServer();
            var source = server.createCommandSourceStack().withLevel(level).withPosition(Vec3.atCenterOf(at)).withSuppressedOutput().withPermission(4);
            helper.assertTrue(server.getCommands().performPrefixedCommand(source,
                    "place structure " + AutomataValkyries.MODID + ":chrono_bastion " + at.getX() + " " + at.getY() + " " + at.getZ()) > 0, "could not place chrono_bastion");
            int cx = c.getMiddleBlockX(), cz = c.getMiddleBlockZ();
            for (net.minecraft.core.BlockPos q : net.minecraft.core.BlockPos.betweenClosed(cx - 56, CorruptedBases.SKY_Y - 40, cz - 56,
                    cx + 56, CorruptedBases.SKY_MAX + 50, cz + 56)) {
                var st = level.getBlockState(q);
                if (st.is(com.automatavalkyries.registry.ModTechBlocks.RULER_PORTAL.get())) {
                    portals++;
                    lowestGate = Math.min(lowestGate, q.getY());
                    helper.assertTrue(q.getZ() == cz - 1 && Math.abs(q.getX() - cx) <= 1, "a gate block is out of place: " + q);
                    helper.assertTrue(st.getValue(com.automatavalkyries.block.RulerPortalBlock.AXIS) == net.minecraft.core.Direction.Axis.X, "the gate does not face the door");
                }
                if (st.is(com.automatavalkyries.registry.ModTechBlocks.RULER_GATE_KEYSTONE.get())) keystones++;
                if (st.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) grass++;
                if (st.is(net.minecraft.world.level.block.Blocks.WATER)) water++;
                if (st.is(net.minecraft.world.level.block.Blocks.STONE) || st.is(net.minecraft.world.level.block.Blocks.DEEPSLATE)) rock++;
            }
            helper.assertTrue(portals == 15, "expected a 3x5 dormant gate, found " + portals + " gate blocks");
            helper.assertTrue(keystones >= 4, "the gate has too few keystones (" + keystones + ")");
            int y0 = lowestGate - com.automatavalkyries.world.SkyGardenFortress.R3 - 2;
            helper.assertTrue(y0 >= CorruptedBases.SKY_Y, "the fortress is too low: " + y0);
            int pad = com.automatavalkyries.world.SkyGardenFortress.PAD;
            for (int[] d : new int[][]{{0, pad}, {0, -pad}, {pad, 0}, {-pad, 0}}) {
                var st = level.getBlockState(new net.minecraft.core.BlockPos(cx + d[0], y0, cz + d[1]));
                helper.assertTrue(st.is(com.automatavalkyries.registry.ModTechBlocks.TECH_HAZARD.get()), "no landing pad edge at " + d[0] + ", " + d[1] + ": " + st);
            }
        } finally {
            CorruptedBases.populate = true;
            for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) level.setChunkForced(c.x + dx, c.z + dz, false);
        }
        helper.assertTrue(grass > 1500, "the garden has too little grass (" + grass + ")");
        helper.assertTrue(water > 300, "the canal and the water pillars are missing (" + water + ")");
        helper.assertTrue(rock > 3000, "the island has no rock under it (" + rock + ")");
        // 新しい中ボスは倍率を受けない。雑魚は要塞の中では ×3
        var keeper = ModEntities.BASTION_KEEPER.get().create(level);
        CorruptedBases.Kind.CHRONO_BASTION.scaleHealth(keeper);
        helper.assertTrue(keeper.getMaxHealth() == com.automatavalkyries.entity.chrono.BastionKeeperEntity.MAX_HP, "keeper hp " + keeper.getMaxHealth());
        var jet = ModEntities.CHRONO_INTERCEPTOR.get().create(level);
        CorruptedBases.Kind.CHRONO_BASTION.scaleHealth(jet);
        helper.assertTrue(Math.abs(jet.getMaxHealth() - 150.0F) < 0.01, "interceptor hp in the bastion " + jet.getMaxHealth());
        helper.succeed();
    }

    /**
     * タイムルーラーの時間停止（2026-10-19）: 止めている間は、止めた者以外の Tick が進まず、時刻も進まない。止めた者は動ける。
     * 動き出すと元どおり。ほかの試験を止めないよう、止めて確かめて動かすまでを 1 回の呼び出しの中で済ませる
     */
    @GameTest(template = "empty", batch = "corrupted_base", timeoutTicks = 20)
    public static void timeStopFreezesEveryoneButTheSource(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 c = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        var source = ModEntities.CHRONO_TROOPER.get().create(level);
        source.moveTo(c.x, c.y, c.z, 0, 0);
        level.addFreshEntity(source);
        var arrow = new net.minecraft.world.entity.projectile.Arrow(level, c.x + 2, c.y + 1, c.z);
        level.addFreshEntity(arrow);
        long day = level.getDayTime();
        try {
            com.automatavalkyries.entity.chrono.TimeStop.start(level, source, 40);
            helper.assertTrue(com.automatavalkyries.entity.chrono.TimeStop.active(), "time did not stop");
            helper.assertTrue(com.automatavalkyries.entity.chrono.TimeStop.frozen(arrow), "the arrow is not frozen");
            helper.assertFalse(com.automatavalkyries.entity.chrono.TimeStop.frozen(source), "the source is frozen by its own time stop");
            int arrowTicks = arrow.tickCount, sourceTicks = source.tickCount;
            level.tickNonPassenger(arrow);
            level.tickNonPassenger(source);
            helper.assertTrue(arrow.tickCount == arrowTicks, "a frozen arrow still ticked");
            helper.assertTrue(source.tickCount == sourceTicks + 1, "the source could not move in stopped time");
            ((com.automatavalkyries.mixin.TimeStopTestAccess) level).automata_valkyries$tickTime();
            helper.assertTrue(level.getDayTime() == day, "the day time moved in stopped time");
        } finally {
            com.automatavalkyries.entity.chrono.TimeStop.stop();
            arrow.discard();
            source.discard();
        }
        helper.assertFalse(com.automatavalkyries.entity.chrono.TimeStop.active(), "time did not flow again");
        int before = arrow.tickCount;
        helper.assertFalse(com.automatavalkyries.entity.chrono.TimeStop.frozen(arrow), "the arrow stays frozen after time flows");
        helper.assertTrue(before == arrow.tickCount, "sanity");
        helper.succeed();
    }

    /**
     * 時の狭間とタイムルーラーの門（2026-10-19）: 次元があり、闘技場（真ん中の要石・帰りの門の眠った膜 3×5）が描ける。
     * 眠れる時の門は時の鍵で目覚め（守護機がいなければ。鍵はなくなる）、守護機が近くに生きていれば目覚めない。タイムルーラーの体力
     */
    @GameTest(template = "empty", batch = "corrupted_base", timeoutTicks = 20)
    public static void timeRiftArenaAndGateKey(GameTestHelper helper) {
        var level = helper.getLevel();
        var rift = level.getServer().getLevel(com.automatavalkyries.world.TimeRift.RIFT);
        helper.assertTrue(rift != null, "the time rift dimension is missing");
        com.automatavalkyries.world.TimeRift.ensureArena(rift);
        int y = com.automatavalkyries.world.TimeRift.Y, gz = com.automatavalkyries.world.TimeRift.GATE_Z;
        helper.assertTrue(rift.getBlockState(new net.minecraft.core.BlockPos(0, y, 0)).is(com.automatavalkyries.registry.ModTechBlocks.RULER_GATE_KEYSTONE.get()), "no keystone at the arena centre");
        int sleeping = 0;
        for (int dx = -1; dx <= 1; dx++) for (int h = 1; h <= 5; h++) {
            var s = rift.getBlockState(new net.minecraft.core.BlockPos(dx, y + h, gz));
            if (s.is(com.automatavalkyries.registry.ModTechBlocks.RULER_PORTAL.get()) && !s.getValue(com.automatavalkyries.block.RulerPortalBlock.AWAKE)) sleeping++;
        }
        helper.assertTrue(sleeping == 15, "the return gate should be 15 sleeping gate blocks, found " + sleeping);
        // 眠れる時の門を時の鍵で目覚めさせる
        net.minecraft.core.BlockPos gate = helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2));
        var portal = com.automatavalkyries.registry.ModTechBlocks.RULER_PORTAL.get().defaultBlockState();
        level.setBlock(gate, portal, 3);
        level.setBlock(gate.above(), portal, 3);
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.automatavalkyries.registry.ModItems.CHRONO_KEY.get(), 2));
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(gate), net.minecraft.core.Direction.NORTH, gate, false);
        try {
            level.getBlockState(gate).use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(level.getBlockState(gate).getValue(com.automatavalkyries.block.RulerPortalBlock.AWAKE)
                    && level.getBlockState(gate.above()).getValue(com.automatavalkyries.block.RulerPortalBlock.AWAKE), "the key did not wake the whole gate");
            helper.assertTrue(player.getMainHandItem().getCount() == 1, "the key was not used up");
        } finally {
            level.setBlock(gate, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(gate.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        var ruler = ModEntities.TIME_RULER.get().create(level);
        helper.assertTrue(ruler.getMaxHealth() == com.automatavalkyries.entity.chrono.TimeRulerEntity.MAX_HP && ruler.phase() == 1, "time ruler hp/phase " + ruler.getMaxHealth());
        helper.succeed();
    }

    /**
     * タイムルーラーの因果停止（2026-10-20）: 止まっている間はその場に縫い留められ回復もせず、明けると防具・耐性 V・衝撃吸収を通して倒れる。不死のトーテムを持つ者だけは耐える。
     * Tick を待たずに見張りの処理を直に呼んで確かめる
     */
    @GameTest(template = "empty", batch = "corrupted_base", timeoutTicks = 20)
    public static void causalLockSeversThroughEverythingButTotem(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 c = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        java.util.List<net.minecraft.world.entity.monster.Zombie> zombies = new java.util.ArrayList<>();
        for (int i = 0; i < 2; i++) {
            var z = EntityType.ZOMBIE.create(level);
            z.moveTo(c.x + i * 2, c.y, c.z, 0, 0);
            z.setNoAi(true);
            for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                var item = switch (slot) {
                    case HEAD -> net.minecraft.world.item.Items.NETHERITE_HELMET;
                    case CHEST -> net.minecraft.world.item.Items.NETHERITE_CHESTPLATE;
                    case LEGS -> net.minecraft.world.item.Items.NETHERITE_LEGGINGS;
                    default -> net.minecraft.world.item.Items.NETHERITE_BOOTS;
                };
                var stack = new net.minecraft.world.item.ItemStack(item);
                stack.enchant(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, 4);
                z.setItemSlot(slot, stack);
            }
            z.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 200, 4));
            z.setAbsorptionAmount(40.0F);
            if (i == 1) z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
            level.addFreshEntity(z);
            zombies.add(z);
        }
        try {
            var plain = zombies.get(0);
            Vec3 held = plain.position();
            plain.setHealth(10.0F);
            float before = plain.getHealth();
            com.automatavalkyries.entity.chrono.CausalLock.lock(plain, 2, null);
            plain.heal(10.0F);
            helper.assertTrue(plain.getHealth() == before, "a causally locked zombie healed: " + before + " -> " + plain.getHealth());
            plain.setPos(held.x + 3, held.y, held.z);
            net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent tick = new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(plain);
            com.automatavalkyries.entity.chrono.CausalLock.onLivingTick(tick);
            helper.assertTrue(plain.position().distanceTo(held) < 0.05, "the locked zombie was not held in place: " + plain.position() + " vs " + held);
            helper.assertTrue(plain.isAlive(), "the zombie died before the lock ran out");
            com.automatavalkyries.entity.chrono.CausalLock.onLivingTick(tick);
            helper.assertTrue(plain.isDeadOrDying(), "netherite + protection IV + resistance V + absorption survived the severance: hp " + plain.getHealth());
            var totem = zombies.get(1);
            com.automatavalkyries.entity.chrono.CausalLock.sever(totem, null);
            helper.assertTrue(totem.isAlive() && !totem.isDeadOrDying(), "the totem did not save the zombie");
            helper.assertTrue(!totem.getMainHandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING), "the totem was not used up");
        } finally {
            for (var z : zombies) z.discard();
        }
        helper.succeed();
    }

    /** 因果断ちの大剣の帯（2026-10-20）: 根元から倒れる向きへ長さ 40・幅 4.4 の帯の上だけ。跳んで 6 m 上に居ても当たり、帯の横や根元の後ろは外れる */
    @GameTest(template = "empty", batch = "corrupted_base", timeoutTicks = 20)
    public static void causalBladeHitsOnlyItsLine(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 pivot = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)), d = new Vec3(1, 0, 0);
        var z = EntityType.ZOMBIE.create(level);
        java.util.function.Function<Vec3, Boolean> at = off -> {
            z.setPos(pivot.x + off.x, pivot.y + off.y, pivot.z + off.z);
            return com.automatavalkyries.entity.chrono.TimeRulerEntity.onBladeLine(pivot, d, z);
        };
        helper.assertTrue(at.apply(new Vec3(20, 0, 0)), "on the line in the middle");
        helper.assertTrue(at.apply(new Vec3(38, 0, 1.5)), "near the tip, inside the band");
        helper.assertTrue(at.apply(new Vec3(10, 6, 0)), "jumping or flying 6 m up still on the line");
        helper.assertFalse(at.apply(new Vec3(20, 0, 3.5)), "beside the band");
        helper.assertFalse(at.apply(new Vec3(-4, 0, 0)), "behind the hilt");
        helper.assertFalse(at.apply(new Vec3(45, 0, 0)), "past the tip");
        helper.succeed();
    }

    /** 時の狭間の着く所（2026-10-20）: 着く所の床に穴が開いていても、入る前に埋まる（奈落に落ちない） */
    @GameTest(template = "empty", batch = "corrupted_base", timeoutTicks = 20)
    public static void timeRiftLandingIsNeverVoid(GameTestHelper helper) {
        var rift = helper.getLevel().getServer().getLevel(com.automatavalkyries.world.TimeRift.RIFT);
        helper.assertTrue(rift != null, "no time rift dimension");
        com.automatavalkyries.world.TimeRift.ensureArena(rift);
        var landing = new net.minecraft.core.BlockPos(0, com.automatavalkyries.world.TimeRift.Y, com.automatavalkyries.world.TimeRift.GATE_Z - 10);
        var beside = landing.offset(1, 0, 1);
        var old = rift.getBlockState(beside);
        rift.setBlock(beside, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        try {
            com.automatavalkyries.world.TimeRift.ensureLanding(rift);
            helper.assertFalse(rift.getBlockState(landing).isAir(), "no floor where players land");
            helper.assertFalse(rift.getBlockState(beside).isAir(), "a hole next to the landing spot was not filled");
        } finally {
            rift.setBlock(beside, old, 2);
        }
        helper.succeed();
    }

    /** 無敵の護符の入・切（2026-10-20）: 切った護符は持っていても効かず、無敵の名残も残らない。入れ直すと戻る */
    @GameTest(template = "empty", batch = "corrupted_base", timeoutTicks = 20)
    public static void invincibleCharmToggles(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        var charm = new net.minecraft.world.item.ItemStack(com.automatavalkyries.registry.ModItems.INVINCIBLE_CHARM.get());
        player.getInventory().setItem(9, charm);
        var held = player.getInventory().getItem(9);
        com.automatavalkyries.protect.InvincibleCharm.refresh(player);
        helper.assertTrue(com.automatavalkyries.protect.InvincibleCharm.isActive(player), "a fresh charm is not active");
        held.getOrCreateTag().putBoolean("Off", true);
        com.automatavalkyries.protect.InvincibleCharm.disableNow(player);
        helper.assertFalse(com.automatavalkyries.protect.InvincibleCharm.isActive(player), "a switched-off charm still protects");
        held.removeTagKey("Off");
        com.automatavalkyries.protect.InvincibleCharm.refresh(player);
        helper.assertTrue(com.automatavalkyries.protect.InvincibleCharm.isActive(player), "switching back on did not protect");
        helper.succeed();
    }

    /** 瞬間転移の端末（2026-10-19）: 開けた所では見ている向きへ 10 m 先、壁があれば壁の手前で止まる */
    @GameTest(template = "empty", batch = "corrupted_base", timeoutTicks = 20)
    public static void blinkDeviceStopsShortOfWalls(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockPlayer();
        Vec3 start = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        player.moveTo(start.x, start.y, start.z, -90.0F, 0.0F); // +X を向く
        Vec3 open = com.automatavalkyries.item.BlinkItem.destination(level, player);
        helper.assertTrue(open != null && Math.abs(open.x - start.x - 10.0) < 0.01 && Math.abs(open.y - start.y) < 0.01, "open blink went to " + open);
        net.minecraft.core.BlockPos wall = net.minecraft.core.BlockPos.containing(start.x + 6, start.y, start.z);
        for (int dy = -1; dy <= 3; dy++) for (int dz = -2; dz <= 2; dz++) level.setBlockAndUpdate(wall.offset(0, dy, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        try {
            Vec3 stopped = com.automatavalkyries.item.BlinkItem.destination(level, player);
            helper.assertTrue(stopped != null && stopped.x + 0.3 <= wall.getX() && stopped.x > start.x + 3.0, "blink went through or too short of the wall: " + stopped + ", wall at x " + wall.getX());
        } finally {
            for (int dy = -1; dy <= 3; dy++) for (int dz = -2; dz <= 2; dz++) level.setBlockAndUpdate(wall.offset(0, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    /** 時間干渉機（2026-10-19）: 輪の中の相手の飛び道具を遅くし、相手を鈍らせる。仲間の弾は遅らせない。Tick を待たずに直に 1 回動かして確かめる */
    @GameTest(template = "empty", batch = "corrupted_base", timeoutTicks = 20)
    public static void delayNodeSlowsForeignProjectiles(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 c = helper.absoluteVec(new Vec3(1.5, 3.0, 1.5));
        var node = ModEntities.DELAY_NODE.get().create(level);
        node.moveTo(c.x, c.y, c.z, 0, 0);
        level.addFreshEntity(node);
        var arrow = new net.minecraft.world.entity.projectile.Arrow(level, c.x + 3, c.y, c.z);
        arrow.setDeltaMovement(1.0, 0, 0);
        level.addFreshEntity(arrow);
        var bolt = com.automatavalkyries.entity.chrono.ChronoBolt.fireDir(node, c.add(-3, 0, 0), new Vec3(1, 0, 0), 1.0F, 1.0F, 0.0F, false);
        bolt.setDeltaMovement(1.0, 0, 0);
        var dummy = ModEntities.VALKYRIE_DUMMY.get().create(level);
        dummy.moveTo(c.x, c.y - 2, c.z + 2, 0, 0);
        level.addFreshEntity(dummy);
        try {
            node.tickCount = 10;
            node.aiStep();
            helper.assertTrue(arrow.getDeltaMovement().x < 0.85, "the arrow was not slowed: " + arrow.getDeltaMovement());
            helper.assertTrue(bolt.getDeltaMovement().length() > 0.99, "a friendly bolt was slowed: " + bolt.getDeltaMovement());
            helper.assertTrue(dummy.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "the dummy in the field was not slowed");
        } finally {
            arrow.discard();
            bolt.discard();
            dummy.discard();
            node.discard();
        }
        helper.succeed();
    }

    /** 侵食基地は初めのリスポーン地点から遠いほど出やすい（2026-10-30）: 割合の曲線と、4 種の置き方が distance_spread で読めること */
    @GameTest(template = "empty")
    public static void basesGetMoreCommonFarFromSpawn(GameTestHelper helper) {
        double near = com.automatavalkyries.config.ValkyrieConfig.baseChance(0, 1.0), half = com.automatavalkyries.config.ValkyrieConfig.baseChance(1500, 1.0),
                far = com.automatavalkyries.config.ValkyrieConfig.baseChance(3000, 1.0), depotFar = com.automatavalkyries.config.ValkyrieConfig.baseChance(1500, 0.5);
        helper.assertTrue(Math.abs(near - 0.1) < 1e-6 && Math.abs(half - 0.55) < 1e-6 && far == 1.0 && depotFar == 1.0,
                "chance curve: near " + near + ", half " + half + ", far " + far + ", depot at 1500 " + depotFar);
        var sets = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
        for (String id : new String[]{"supply_depot", "chrono_outpost", "dimension_foundry", "chrono_bastion"}) {
            var set = sets.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, id));
            helper.assertTrue(set != null && set.placement() instanceof com.automatavalkyries.world.DistanceSpreadPlacement, id + " does not use distance_spread");
        }
        // 侵食基地レーダーが探すタグ（全部と種類ごと）
        var structures = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        for (String mode : com.automatavalkyries.item.BaseRadarItem.MODES) {
            var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.STRUCTURE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "radar/" + mode));
            int n = structures.getTag(tag).map(h -> h.size()).orElse(0);
            helper.assertTrue(n == (mode.equals("corrupted_bases") ? 4 : 1), "radar tag " + mode + " has " + n + " structures");
        }
        helper.succeed();
    }
}
