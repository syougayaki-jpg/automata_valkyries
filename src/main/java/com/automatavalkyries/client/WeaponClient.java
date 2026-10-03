package com.automatavalkyries.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 銃の構えと反動（クライアントだけ）。撃つと殴る振りではなく、銃が跳ね上がって戻り、視点も少し上へ跳ねる。
 * 構えは三人称で腕を前へ（両手兵装はクロスボウと同じ両手持ち）。反動の大きさは兵装ごと（{@code TacticalWeaponItem#recoil}）。
 */
public final class WeaponClient {

    public static final HumanoidModel.ArmPose GUN_POSE = HumanoidModel.ArmPose.create("AUTOMATA_VALKYRIES_GUN", false, WeaponClient::poseOneHanded);
    public static final HumanoidModel.ArmPose RIFLE_POSE = HumanoidModel.ArmPose.create("AUTOMATA_VALKYRIES_RIFLE", true, WeaponClient::poseTwoHanded);

    private static long shotTick = Long.MIN_VALUE / 2;
    private static float shotStrength;
    private static Item shotItem;

    private WeaponClient() {}

    /** クライアントの準備のときに呼ぶ（静的なフィールドで ArmPose を作らせる） */
    public static void init() {}

    /** 撃った瞬間（クライアントの use から） */
    public static void kick(Item item, float strength) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        shotTick = player.level().getGameTime();
        shotStrength = strength;
        shotItem = item;
        player.setXRot(player.getXRot() - 3.5F * strength); // 視点の跳ね上がり
    }

    /** 反動の進み（0〜1）: 1.5 Tick で跳ね上がり、その後ゆっくり戻る */
    private static float kickAmount(Item item, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || item != shotItem) return 0;
        float t = player.level().getGameTime() - shotTick + partialTick;
        if (t < 0 || t > 30) return 0;
        return (t < 1.5F ? t / 1.5F : (float) Math.exp(-(t - 1.5F) / 4F)) * shotStrength;
    }

    /** 一人称: 振りの動きの代わりに、反動で銃口が上がって手前へ引く */
    public static boolean handTransform(PoseStack pose, HumanoidArm arm, ItemStack stack, float partialTick, float equipProcess) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.translate(side * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.isUsingItem() && player.getUseItem() == stack
                && stack.getItem() instanceof com.automatavalkyries.weapon.ChargedWeaponItem charged) {
            float c = charged.chargeProgress(player);
            float time = player.tickCount + partialTick;
            float shake = c >= 1 ? 0.004F : 0.012F * c;
            pose.translate(Math.sin(time * 2.7) * shake, Math.cos(time * 3.1) * shake, 0.08F * c);
        }
        float k = kickAmount(stack.getItem(), partialTick);
        if (k > 0) {
            pose.translate(0, 0.06F * k, 0.14F * k);
            pose.mulPose(Axis.XP.rotationDegrees(22F * k));
        }
        return true;
    }

    private static float thirdPersonKick(LivingEntity entity) {
        if (entity != Minecraft.getInstance().player) return 0;
        Item held = entity.getMainHandItem().getItem();
        return kickAmount(held, Minecraft.getInstance().getPartialTick()) * 0.6F;
    }

    private static void poseOneHanded(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        var part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        part.xRot = -((float) Math.PI / 2F) + model.head.xRot - thirdPersonKick(entity);
        part.yRot = model.head.yRot + (arm == HumanoidArm.RIGHT ? -0.1F : 0.1F);
    }

    private static void poseTwoHanded(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        AnimationUtils.animateCrossbowHold(model.rightArm, model.leftArm, model.head, arm == HumanoidArm.RIGHT);
        float k = thirdPersonKick(entity);
        model.rightArm.xRot -= k;
        model.leftArm.xRot -= k;
    }
}
