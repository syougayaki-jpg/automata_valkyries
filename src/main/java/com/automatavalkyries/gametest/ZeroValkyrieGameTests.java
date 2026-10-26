package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieBody;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.item.ValkyrieBodyItem;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 零号機（素体タイプ Z、2026-10-20）: 同調率 100% の固定と、零号機再起動パッチで眠った零号機が持ち主の兵姫として目覚めること。Tick には頼らず直接呼ぶ */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ZeroValkyrieGameTests {

    private static final String TEMPLATE = "empty";

    /** Z は一番強い型で、同調率は 100% から下がらない（減らしても・読み直しても 100%） */
    @GameTest(template = TEMPLATE, batch = "valkyrie_zero")
    public static void zeroSyncIsFixedAt100(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        v.setBody(ValkyrieBody.Z);
        helper.assertTrue(v.getBody() == ValkyrieBody.Z, "body Z was not set");
        helper.assertTrue(v.getSyncRate() == 100.0F, "Zero did not start at 100% sync: " + v.getSyncRate());
        v.addSync(-50.0F, "test");
        helper.assertTrue(v.getSyncRate() == 100.0F, "Zero sync dropped to " + v.getSyncRate());
        v.addSync(-100.0F, "test");
        helper.assertTrue(v.getSyncRate() == 100.0F, "Zero sync dropped to " + v.getSyncRate());
        for (ValkyrieBody b : ValkyrieBody.values()) {
            if (b != ValkyrieBody.Z) helper.assertTrue(ValkyrieBody.Z.maxHealth > b.maxHealth, "Zero is not the strongest (" + b + ")");
        }
        helper.assertTrue(v.getMaxHealth() >= 200.0F, "Zero max health was not raised: " + v.getMaxHealth());   // 2026-10-26 の強化
        helper.assertTrue(v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "Zero still takes knockback");
        CompoundTag saved = new CompoundTag();
        v.saveWithoutId(saved);
        saved.putFloat("Sync", 12.0F); // 書き換えられた保存でも 100% に戻る
        ValkyrieEntity copy = ModEntities.VALKYRIE.get().create(helper.getLevel());
        copy.load(saved);
        helper.assertTrue(copy.getBody() == ValkyrieBody.Z && copy.getSyncRate() == 100.0F, "Zero sync was not 100% after load: " + copy.getSyncRate());
        // 他の型の同調率は今までどおり動く
        v.setBody(ValkyrieBody.V);
        v.addSync(-50.0F, "test");
        helper.assertTrue(v.getSyncRate() == 50.0F, "Type-V sync did not change: " + v.getSyncRate());
        v.releaseAndDiscard();
        helper.succeed();
    }

    /** 眠った零号機（持ち主なし）は傷つかず、端末では起きず、再起動パッチで持ち主の兵姫として完全に目覚める */
    @GameTest(template = TEMPLATE, batch = "valkyrie_zero")
    public static void zeroRebootPatchWakesDormantZero(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        ValkyrieEntity v = ValkyrieBodyItem.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), 0.0F, ValkyrieBody.Z);
        helper.assertTrue(v != null, "the dormant Zero was not placed");
        helper.assertTrue(v.isDormantZero() && v.isAsleep() && v.getOwnerUUID() == null, "the placed Zero is not dormant and unowned");
        float hp = v.getHealth();
        helper.assertFalse(v.hurt(helper.getLevel().damageSources().generic(), 10.0F), "the dormant Zero took damage");
        helper.assertTrue(v.getHealth() == hp, "the dormant Zero lost HP");
        helper.assertFalse(v.wakeFromPod(player, com.automatavalkyries.item.ProtoCoreItem.CAPACITY), "the dormant Zero woke from a proto core");
        helper.assertTrue(v.getOwnerUUID() == null, "the dormant Zero got an owner without the patch");

        // 普通の兵姫にはパッチは効かない
        ValkyrieEntity other = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        ItemStack patch = new ItemStack(ModItems.ZERO_REBOOT_PATCH.get());
        helper.assertFalse(patch.interactLivingEntity(player, other, InteractionHand.MAIN_HAND).consumesAction(), "the patch worked on a normal valkyrie");
        helper.assertTrue(other.getOwnerUUID() == null, "the patch claimed a normal valkyrie");
        other.releaseAndDiscard();

        helper.assertTrue(patch.interactLivingEntity(player, v, InteractionHand.MAIN_HAND).consumesAction(), "the patch did not reboot the Zero");
        helper.assertTrue(patch.isEmpty(), "the patch was not used up");
        helper.assertTrue(player.getUUID().equals(v.getOwnerUUID()), "the rebooted Zero is not the player's");
        helper.assertTrue(!v.isDormantZero() && !v.isAsleep() && !v.isPodSleeper() && !v.isShutdown(), "the rebooted Zero is not awake");
        helper.assertTrue(v.getMode() == ValkyrieEntity.Mode.FOLLOW, "the rebooted Zero does not follow");
        helper.assertTrue(v.getSyncRate() == 100.0F, "the rebooted Zero sync is " + v.getSyncRate());
        helper.assertTrue(v.getHealth() >= v.getMaxHealth() && v.getEnergy() >= v.maxEnergy() && v.getShield() >= v.getMaxShield(),
                "the rebooted Zero is not at full HP / energy / shield");
        // 起きた後は二度とパッチが効かない
        ItemStack again = new ItemStack(ModItems.ZERO_REBOOT_PATCH.get());
        helper.assertFalse(again.interactLivingEntity(player, v, InteractionHand.MAIN_HAND).consumesAction(), "the patch worked twice");
        v.releaseAndDiscard();
        helper.succeed();
    }
}
