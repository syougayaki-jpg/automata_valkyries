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
        for (String id : new String[]{"supply_depot", "chrono_outpost", "dimension_foundry"}) {
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
                                helper.assertTrue(level.getBlockEntity(q) instanceof com.automatavalkyries.block.ChronoGateBlock.Gate g && ids[i].equals(g.kind()),
                                        "a gate in " + ids[i] + " does not know its dungeon");
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
        double[] expect = {100.0, 150.0, 200.0};
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
}
