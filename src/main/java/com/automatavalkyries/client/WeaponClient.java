package com.automatavalkyries.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
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

    // ---- 振り下ろし（終の黄昏のチャージ攻撃。2026-10-03 ユーザー指定「画面中心に思いっきり振り落とす」） ----

    /** 振り下ろしの長さ（Tick）と、刃が画面の中心へ落ちきる時刻 */
    public static final int SLAM_TICKS = 16;
    public static final float SLAM_IMPACT = 4.5F;
    private static long slamTick = Long.MIN_VALUE / 2;
    private static Item slamItem;

    /** 振り下ろしを始める（クライアントの releaseUsing から） */
    public static void slam(Item item) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        slamTick = player.level().getGameTime();
        slamItem = item;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3 - 2 * x);
    }

    /**
     * 一人称の振り下ろし: 肩の上へ大きく振りかぶり（〜2.5 Tick）、画面の中心へ一気に叩き下ろし（〜4.5 Tick）、
     * 落ちきった所で震えながら少し止まり（〜9 Tick）、ゆっくり構えへ戻る（〜16 Tick）。振り下ろし中でなければ false
     */
    public static boolean slamTransform(PoseStack pose, HumanoidArm arm, ItemStack stack, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || stack.getItem() != slamItem) return false;
        float t = player.level().getGameTime() - slamTick + partialTick;
        if (t < 0 || t > SLAM_TICKS) return false;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        // 構え → 振りかぶり → 叩き下ろし（画面の中心、手前やや下）の 3 つの姿勢: {x, y, z, 縦の回転, 横の傾き}
        float[] rest = {0, 0, 0, 0, 0};
        float[] wind = {-0.15F * side, 0.55F, 0.2F, 55F, 10F * side};
        float[] impact = {-0.56F * side, -0.05F, -0.35F, -105F, -35F * side};
        float[] cur;
        if (t < 2.5F) cur = lerp(rest, wind, smooth(t / 2.5F));
        else if (t < SLAM_IMPACT) { float f = (t - 2.5F) / (SLAM_IMPACT - 2.5F); cur = lerp(wind, impact, f * f); }
        else if (t < 9F) cur = impact;
        else cur = lerp(impact, rest, smooth((t - 9F) / (SLAM_TICKS - 9F)));
        pose.translate(side * 0.56F + cur[0], -0.52F + cur[1], -0.72F + cur[2]);
        if (t >= SLAM_IMPACT && t < 9F) { // 叩きつけた震え（だんだん収まる）
            float shake = 0.035F * (1 - (t - SLAM_IMPACT) / (9F - SLAM_IMPACT));
            pose.translate(Math.sin(t * 9.0) * shake, Math.cos(t * 11.0) * shake, 0);
        }
        pose.mulPose(Axis.XP.rotationDegrees(cur[3]));
        pose.mulPose(Axis.ZP.rotationDegrees(cur[4]));
        return true;
    }

    private static float[] lerp(float[] a, float[] b, float f) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] + (b[i] - a[i]) * f;
        return out;
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
