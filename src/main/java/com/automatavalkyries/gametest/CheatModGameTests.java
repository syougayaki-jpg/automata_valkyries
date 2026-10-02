package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.TerminusEngine;
import com.automatavalkyries.entity.ElenorEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 対策を書いていない（このMOD が名前を知らない）チート MOD を相手に、準汎用の層がどこまで効くかを測る GameTest（2026-10-03）。
 * 対象 MOD が入っていなければ何もせず成功する（{@code -PwithCheatMods} で入れる。{@code -PgenericOnly} と併せて測る）。
 * <ul>
 *   <li>ManaitaMTK: {@code WeaponUtil.die}（HP 0 → 最大値のダメージ → die の直呼び）、剣の範囲殺し（雷＋die）、頭装備の無敵（死亡・被弾イベントの取り消し）</li>
 *   <li>ManaitaPlus Legacy: {@code ManaitaPlusUtils.attack}（攻撃の型ごとの抹殺）・{@code removeOnServer}、死亡・消去の印（UUID の表・永続データのビット・同期データ）、
 *       神装備の印（manaita）</li>
 * </ul>
 * Fantasy Ending は専用サーバーで起動できないのでここには無い（{@code -PwithFantasyEnding} でクライアントから手で確かめる）。
 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class CheatModGameTests {

    private static final String TEMPLATE = "empty";
    private static final String MTK = "manaitamtk";
    private static final String MTK_WEAPON = "com.takoy3466.manaitamtk.util.WeaponUtil";
    private static final String MANAITA = "manaita_plus_legacy";
    private static final String MANAITA_UTILS = "sen.manaita_plus_legacy.common.util.ManaitaPlusUtils";
    private static final String MANAITA_DATA = "sen.manaita_plus_legacy.common.util.entity.ManaitaPlusLegacyEntityData";

    /** ManaitaMTK: HP を 0 に書き、最大値のダメージを与え、それでも生きていれば die を直に呼ぶ */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void manaitaMtkDieIsCountered(GameTestHelper helper) {
        if (!KillModGameTests.loaded(helper, MTK)) return;
        ElenorEntity elenor = KillModGameTests.spawnElenor(helper);
        KillModGameTests.invoke(helper, MTK_WEAPON, "die", new Class<?>[]{LivingEntity.class}, elenor);
        KillModGameTests.assertSurvivesAfterRepair(helper, elenor);
    }

    /** ManaitaMTK: 剣で叩く・左クリック・範囲殺し（雷を落としてから die） */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void manaitaMtkSwordAndAreaKillAreCountered(GameTestHelper helper) {
        if (!KillModGameTests.loaded(helper, MTK)) return;
        ElenorEntity elenor = KillModGameTests.spawnElenor(helper);
        ServerPlayer wielder = fakePlayer(helper, "mtk-wielder");
        ItemStack sword = stack(MTK, "manaita_sword");
        wielder.setItemSlot(EquipmentSlot.MAINHAND, sword);
        try {
            sword.hurtEnemy(elenor, wielder);
            sword.getItem().onLeftClickEntity(sword, wielder, elenor);
        } catch (RuntimeException e) {
            AutomataValkyries.LOGGER.warn("[eprotect-test] ManaitaMTK sword threw", e);
        }
        KillModGameTests.invoke(helper, MTK_WEAPON, "lightningStriker", new Class<?>[]{LivingEntity.class, Level.class, Player.class},
                elenor, helper.getLevel(), wielder);
        KillModGameTests.assertSurvivesAfterRepair(helper, elenor);
    }

    /** ManaitaMTK の頭装備（無敵の Capability で死亡・被弾イベントを取り消す）を着けたプレイヤーにも、終焉執行は絶対死を通す */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void manaitaMtkArmorDoesNotStopTerminus(GameTestHelper helper) {
        if (!KillModGameTests.loaded(helper, MTK)) return;
        ServerLevel level = helper.getLevel();
        ServerPlayer target = fakePlayer(helper, "mtk-armored");
        equip(target, EquipmentSlot.HEAD, stack(MTK, "helmet_manaita"));
        equip(target, EquipmentSlot.CHEST, stack(MTK, "chestplate_manaita"));
        equip(target, EquipmentSlot.LEGS, stack(MTK, "legins_manaita"));
        equip(target, EquipmentSlot.FEET, stack(MTK, "boots_manaita"));
        // 頭装備の inventoryTick が無敵の Capability を立てる
        target.getItemBySlot(EquipmentSlot.HEAD).inventoryTick(level, target, 39, false);
        target.setHealth(20.0F);

        boolean done = TerminusEngine.executeTerminus(level, helper.makeMockPlayer(), target);
        helper.assertTrue(done, "Terminus did not run on the ManaitaMTK armored player");
        helper.assertTrue(target.getHealth() <= 0.0F && target.isDeadOrDying(),
                "The ManaitaMTK armored player survived Terminus (health " + target.getHealth() + ")");
        helper.succeed();
    }

    /** ManaitaPlus Legacy: 攻撃の型ごとの抹殺とサーバー側の消去。死亡・消去の印（UUID の表・永続データのビット）も残らない */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void manaitaPlusAttackIsCountered(GameTestHelper helper) {
        if (!KillModGameTests.loaded(helper, MANAITA)) return;
        ElenorEntity elenor = KillModGameTests.spawnElenor(helper);
        ServerPlayer wielder = fakePlayer(helper, "manaita-wielder");
        for (int mode = 0; mode <= 3; mode++) {
            KillModGameTests.invoke(helper, MANAITA_UTILS, "attack", new Class<?>[]{Entity.class, Player.class, int.class}, elenor, wielder, mode);
        }
        KillModGameTests.invoke(helper, MANAITA_UTILS, "removeOnServer", new Class<?>[]{Entity.class}, elenor);
        helper.runAfterDelay(5, () -> {
            for (String flag : new String[]{"death", "remove"}) {
                helper.assertFalse(manaitaFlag(flag, elenor), "ManaitaPlus '" + flag + "' mark is still on Elenor");
            }
        });
        KillModGameTests.assertSurvivesAfterRepair(helper, elenor);
    }

    /** ManaitaPlus Legacy の神の印（manaita）と神装備を持つプレイヤーにも、終焉執行は絶対死を通す */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void manaitaPlusGodPlayerDoesNotStopTerminus(GameTestHelper helper) {
        if (!KillModGameTests.loaded(helper, MANAITA)) return;
        ServerLevel level = helper.getLevel();
        ServerPlayer target = fakePlayer(helper, "manaita-god");
        equip(target, EquipmentSlot.HEAD, stack(MANAITA, "manaita_helmet"));
        equip(target, EquipmentSlot.CHEST, stack(MANAITA, "manaita_chestplate"));
        equip(target, EquipmentSlot.LEGS, stack(MANAITA, "manaita_leggings"));
        equip(target, EquipmentSlot.FEET, stack(MANAITA, "manaita_boots"));
        target.getInventory().add(stack(MANAITA, "manaita_sword_god"));
        // 神の印は装備・所持品から付く（実際の遊び方と同じにする。印を直に立てると、装備を剥がされても残る状態を作ってしまう）
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            target.getItemBySlot(slot).inventoryTick(level, target, slot.getIndex() + 36, false);
        }
        target.tick();
        AutomataValkyries.LOGGER.info("[eprotect-test] ManaitaPlus god player before Terminus: god mark {}", manaitaFlag("manaita", target));
        target.setHealth(20.0F);

        boolean done = TerminusEngine.executeTerminus(level, helper.makeMockPlayer(), target);
        helper.assertTrue(done, "Terminus did not run on the ManaitaPlus god player");
        // getHealth / isAlive / isDeadOrDying は ManaitaPlus の変換で神の印のある個体だけ偽装される。実際の値を見る
        float real = com.automatavalkyries.protect.DirectRead.health(target);
        AutomataValkyries.LOGGER.info("[eprotect-test] ManaitaPlus god player after Terminus: real health {}, getHealth {}, isDeadOrDying {}, god mark {}",
                real, target.getHealth(), target.isDeadOrDying(), manaitaFlag("manaita", target));
        helper.assertTrue(real <= 0.0F, "The ManaitaPlus god player survived Terminus (real health " + real + ")");
        helper.assertTrue(target.isDeadOrDying(), "The executed player still looks alive (isDeadOrDying is spoofed)");
        helper.succeed();
    }

    private static ServerPlayer fakePlayer(GameTestHelper helper, String name) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5D, 2.0D, 1.5D)));
        return player;
    }

    private static ItemStack stack(String namespace, String path) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(namespace, path));
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            throw new IllegalStateException("Missing item " + namespace + ":" + path);
        }
        return new ItemStack(item);
    }

    private static void equip(Player player, EquipmentSlot slot, ItemStack stack) {
        player.setItemSlot(slot, stack);
    }

    private static Object manaitaData(String constant) throws ReflectiveOperationException {
        return Class.forName(MANAITA_DATA).getField(constant).get(null);
    }

    private static boolean manaitaFlag(String constant, Entity entity) {
        try {
            Object data = manaitaData(constant);
            Method accept = data.getClass().getMethod("accept", Entity.class);
            return Boolean.TRUE.equals(accept.invoke(data, entity));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ManaitaPlus flag API changed", e);
        }
    }
}
