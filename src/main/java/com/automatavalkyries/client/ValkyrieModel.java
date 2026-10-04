package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

import java.util.HashMap;
import java.util.Map;

/**
 * 戦術兵姫 Type-V の GeckoLib モデル（仮。2026-10-04）。geo / テクスチャは {@code tools/gen_valkyrie_v.py} が作る。
 * <p>動きは毎フレームここで計算する: 歩きの手足の振り、立っている間の呼吸、頭の向き、髪のなびき（エンティティのばね）、
 * 表情ごとの立体のまつ毛の位置（{@link #LASH_POSE}、生成スクリプトの LASH_POSE と同じ値）、瞬き（テクスチャを閉じた目に差し替え）、胸のコアの脈。
 * 回転の符号は GeckoLib 内部のもの（X: 正で下端が前へ、Z: 正で正面から見て時計回り）。</p>
 */
public class ValkyrieModel extends DefaultedEntityGeoModel<ValkyrieEntity> {

    private static final String TEXTURE = "textures/entity/valkyrie_type_v";
    /** 表情ごとの立体のまつ毛: {上下 px, 鼻側を下げる角度（度）, 見せるか 1/0}（tools/gen_valkyrie_v.py の LASH_POSE） */
    private static final Map<String, float[]> LASH_POSE = new HashMap<>();
    static {
        LASH_POSE.put("normal", new float[]{0F, 0F, 1});
        LASH_POSE.put("shy", new float[]{0F, 0F, 1});
        LASH_POSE.put("surprise", new float[]{0.438F, 0F, 1});
        LASH_POSE.put("sleepy", new float[]{-0.219F, 0F, 1});
        LASH_POSE.put("angry", new float[]{0F, 24.2F, 1});
        LASH_POSE.put("sad", new float[]{0.044F, -21.8F, 1});
        LASH_POSE.put("shutdown", new float[]{-0.438F, 0F, 1});
        LASH_POSE.put("blink", new float[]{-0.438F, 0F, 1});
        LASH_POSE.put("smile", new float[]{0F, 0F, 0});
    }
    private final Map<String, ResourceLocation> textures = new HashMap<>();

    public ValkyrieModel() {
        super(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "valkyrie_type_v"), "head");
    }

    /** 今の顔（瞬きの間は目を閉じた顔。目を閉じている表情はそのまま） */
    static String face(ValkyrieEntity entity) {
        String expr = entity.expressionName();
        boolean openEyes = !expr.equals("smile") && !expr.equals("shutdown");
        return entity.blinkTicks > 0 && openEyes ? "blink" : expr;
    }

    @Override
    public ResourceLocation getTextureResource(ValkyrieEntity entity) {
        String face = face(entity);
        return textures.computeIfAbsent(face, f -> ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID,
                TEXTURE + (f.equals("normal") ? "" : "_" + f) + ".png"));
    }

    @Override
    public void setCustomAnimations(ValkyrieEntity entity, long instanceId, AnimationState<ValkyrieEntity> state) {
        super.setCustomAnimations(entity, instanceId, state); // 頭の向き

        float pt = state.getPartialTick();
        float age = entity.tickCount + pt;
        float walk = Math.min(state.getLimbSwingAmount(), 1.0F);
        float limb = state.getLimbSwing();
        float idle = 1.0F - walk;
        float attack = Mth.sin(entity.getAttackAnim(pt) * Mth.PI);

        // ---- 体: 立っている間はゆっくり呼吸（胸がわずかに上下）、歩くと小さく弾む
        CoreGeoBone body = bone("body");
        body.setPosY(Mth.sin(age * 0.08F) * 0.12F * idle + Math.abs(Mth.sin(limb * 0.6662F)) * 0.35F * walk);
        body.setRotX(-0.06F * walk); // 歩くと少し前のめり

        // ---- 脚
        float legSwing = Mth.cos(limb * 0.6662F) * 1.1F * walk;
        bone("right_leg").setRotX(legSwing);
        bone("left_leg").setRotX(-legSwing);

        // ---- 腕: 歩きの振り＋立ち姿の自然な開き（ゆらぎ）
        float armSwing = Mth.cos(limb * 0.6662F + Mth.PI) * 0.7F * walk;
        float armOut = 0.08F + Mth.cos(age * 0.07F) * 0.03F * idle;
        CoreGeoBone rightArm = bone("right_arm");
        CoreGeoBone leftArm = bone("left_arm");
        rightArm.setRotX(armSwing + attack * 1.4F);
        leftArm.setRotX(-armSwing);
        rightArm.setRotZ(armOut);
        leftArm.setRotZ(-armOut);

        // ---- 髪: エンティティのばね（前へ進むほど後ろへ流れる）を後ろ髪の 3 段へ配る。横髪・リボンも少し
        float swing = Mth.lerp(pt, entity.hairSwingO, entity.hairSwing);
        float sway = Mth.lerp(pt, entity.sideSwayO, entity.sideSway);
        float breeze = Mth.sin(age * 0.06F) * 0.02F;
        bone("hair_upper").setRotX(-(0.04F + swing * 0.45F + breeze));
        bone("hair_mid").setRotX(-(0.03F + swing * 0.3F + breeze));
        bone("hair_tip").setRotX(-(0.03F + swing * 0.25F + breeze * 1.5F));
        bone("hair_upper").setRotZ(sway * 0.4F);
        bone("hair_tip").setRotZ(sway * 0.3F);
        bone("side_lock_right").setRotX(-swing * 0.35F + breeze);
        bone("side_lock_left").setRotX(-swing * 0.35F - breeze);
        bone("side_lock_right").setRotZ(sway * 0.5F);
        bone("side_lock_left").setRotZ(sway * 0.5F);
        bone("ribbon").setRotX(-swing * 0.3F);

        // ---- 表情ごとの立体のまつ毛（目の高さと傾きに合わせる）
        float[] lash = LASH_POSE.getOrDefault(face(entity), LASH_POSE.get("normal"));
        for (int side = -1; side <= 1; side += 2) {
            CoreGeoBone b = bone(side < 0 ? "lash_right" : "lash_left");
            b.setHidden(lash[2] < 0.5F);
            b.setPosY(lash[0]);
            b.setRotZ(lash[1] * Mth.DEG_TO_RAD * (side < 0 ? 1 : -1)); // 鼻側を下げる向き: 右目は時計回り、左目は反時計回り
        }

        // ---- 胸のコア: ゆっくり脈打つ
        float pulse = 1.0F + Mth.sin(age * 0.12F) * 0.05F;
        CoreGeoBone core = bone("core");
        core.setScaleX(pulse);
        core.setScaleY(pulse);
    }

    private CoreGeoBone bone(String name) {
        return getAnimationProcessor().getBone(name);
    }
}
