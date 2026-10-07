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

    /** 素体タイプごとの名前（valkyrie_type_v / s / h / c。2026-10-07 他の 3 型を追加。骨の名前は 4 型で共通） */
    private static String typeName(ValkyrieEntity entity) {
        return "valkyrie_type_" + entity.getBody().name().toLowerCase(java.util.Locale.ROOT);
    }
    /** 機能停止で腰を落とす量（px。脚 12.5px を前へ投げ出して地面に座る） */
    private static final float SIT_DROP = 11.0F;
    /** 表情ごとの立体のまつ毛: {上下 px, 鼻側を下げる角度（度）, 見せるか 1/0}（tools/gen_valkyrie_v.py の LASH_POSE） */
    private static final Map<String, float[]> LASH_POSE = new HashMap<>();
    static {
        LASH_POSE.put("normal", new float[]{0F, 0F, 1});
        LASH_POSE.put("shy", new float[]{0F, 0F, 1});
        LASH_POSE.put("surprise", new float[]{0.519F, 0F, 1});
        LASH_POSE.put("sleepy", new float[]{-0.259F, 0F, 1});
        LASH_POSE.put("angry", new float[]{0F, 24.2F, 1});
        LASH_POSE.put("sad", new float[]{0.052F, -21.8F, 1});
        LASH_POSE.put("shutdown", new float[]{-0.519F, 0F, 1});
        LASH_POSE.put("blink", new float[]{-0.519F, 0F, 1});
        LASH_POSE.put("smile", new float[]{0F, 0F, 0});
    }
    private final Map<String, ResourceLocation> textures = new HashMap<>();
    private final Map<String, ResourceLocation> models = new HashMap<>();

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
    public ResourceLocation getModelResource(ValkyrieEntity entity) {
        return models.computeIfAbsent(typeName(entity), n -> ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "geo/entity/" + n + ".geo.json"));
    }

    @Override
    public ResourceLocation getTextureResource(ValkyrieEntity entity) {
        String face = face(entity);
        String type = typeName(entity);
        return textures.computeIfAbsent(type + "/" + face, f -> ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID,
                "textures/entity/" + type + (face.equals("normal") ? "" : "_" + face) + ".png"));
    }

    @Override
    public void setCustomAnimations(ValkyrieEntity entity, long instanceId, AnimationState<ValkyrieEntity> state) {
        super.setCustomAnimations(entity, instanceId, state); // 頭の向き

        float pt = state.getPartialTick();
        float age = entity.tickCount + pt;
        float walk = Math.min(state.getLimbSwingAmount(), 1.0F);
        float limb = state.getLimbSwing();
        float idle = 1.0F - walk;
        // 死亡モーション → 機能停止（0〜1 でなめらかに）: 前半で膝が折れて前へ崩れ、後半でぺたんと座り込んでうなだれる
        float d = Mth.lerp(pt, entity.downAnimO, entity.downAnim);
        float buckle = smooth(d / 0.4F) * (1.0F - smooth((d - 0.4F) / 0.6F)); // 前半だけ立つ山
        float down = smooth((d - 0.25F) / 0.75F);                               // 座り込みの進み
        float up = 1.0F - smooth(d / 0.3F);
        walk *= up;
        idle = 1.0F - walk;

        // ---- 体: 立っている間はゆっくり呼吸（胸がわずかに上下）、歩くと小さく弾む
        CoreGeoBone body = bone("body");
        body.setPosY((Mth.sin(age * 0.08F) * 0.12F * idle + Math.abs(Mth.sin(limb * 0.6662F)) * 0.35F * walk) * up);
        body.setRotX(-0.06F * walk - 0.45F * buckle - 0.22F * down); // 歩くと少し前のめり、崩れるときは前へ、座るとうなだれる
        body.setRotZ(0.08F * buckle);                                   // 崩れるときに少し傾く
        bone("root").setPosY(-SIT_DROP * down - 1.2F * buckle);
        CoreGeoBone head = bone("head");
        head.setRotX(head.getRotX() * up - 0.35F * buckle - 0.5F * down);
        head.setRotY(head.getRotY() * up);

        // ---- 戦いの構え・攻撃・ステップ（2026-10-06 ユーザー指定「ステップと武器を構え攻撃するモーション」）
        float melee = Mth.lerp(pt, entity.meleeReadyO, entity.meleeReady) * up;
        float ranged = Mth.lerp(pt, entity.rangedReadyO, entity.rangedReady) * up;
        float ready = Math.max(melee, ranged);
        float swingP = entity.getAttackAnim(pt);
        boolean twoHanded = entity.getOffhandItem().isEmpty();
        // 溜め撃ち（アイテムを使っている間）は両手で構えて、溜まるほど小刻みに震える
        float charge = entity.isUsingItem() ? Math.min(1.0F, (entity.getTicksUsingItem() + pt) / 20.0F) * up : 0.0F;
        float aim = Math.max(ranged, charge);
        // 近接の攻撃の動き（2026-10-06 ユーザー指定「斬る・突く・叩く・薙ぎ払う」）: サーバーの合図の種類と経過で、構え（腕 0.75・左手 0.55）からの動きを決める。
        // 合図の無い振り（技など）は斬る
        int kind = entity.attackKind;
        float q = kind > 0 ? (age - entity.attackStart) / ValkyrieEntity.ATTACK_TICKS[kind] : -1.0F;
        if (q < 0.0F || q >= 1.0F) {
            kind = swingP > 0.0F ? ValkyrieEntity.ATTACK_SLASH : 0;
            q = swingP;
        }
        float mArm = 0.75F, mArmZ = 0.25F, mLeft = 0.55F, slashTwist = 0.0F, slashLean = 0.0F, dip = 0.0F, mTilt = 0.45F;
        float mLeftZ = -0.25F;
        boolean fists = entity.getMainHandItem().isEmpty();
        if (fists) { // 素手の構え: 両の拳を胸の前に上げる
            mArm = 1.05F; mLeft = 1.05F; mArmZ = -0.12F; mLeftZ = 0.12F;
        }
        switch (kind) {
            case ValkyrieEntity.ATTACK_SLASH -> { // 斬る: 右上へ振りかぶって、左下へ斜めに振り抜く
                float w = seg(q, 0.0F, 0.35F), st = seg(q, 0.35F, 0.7F), r = seg(q, 0.75F, 1.0F);
                mArm = 0.75F + 1.75F * w - 2.4F * st + 0.65F * r;
                mArmZ = 0.25F + 0.35F * w - 0.8F * st + 0.45F * r;
                slashTwist = -0.35F * w + 0.8F * st - 0.45F * r;
                slashLean = 0.05F * w - 0.23F * st + 0.18F * r;
                mTilt = 0.45F * (1.0F - w) + 0.45F * r;
            }
            case ValkyrieEntity.ATTACK_THRUST -> { // 突く: 引いて溜め、体ごとまっすぐ突き出す（刃先は前へ）
                float w = seg(q, 0.0F, 0.3F), st = seg(q, 0.3F, 0.5F), r = seg(q, 0.7F, 1.0F);
                mArm = 0.75F - 0.5F * w + 1.3F * st - 0.8F * r;
                mArmZ = 0.25F - 0.2F * st + 0.2F * r;
                slashTwist = -0.45F * w + 0.8F * st - 0.35F * r;
                slashLean = 0.05F * w - 0.3F * st + 0.25F * r;
                float k = Math.max(w, st) * (1.0F - r);
                mTilt = Mth.lerp(k, 0.45F, mArm);
            }
            case ValkyrieEntity.ATTACK_SMASH -> { // 叩く: 両手で頭の上まで振りかぶり、沈み込んで叩きつける
                float w = seg(q, 0.0F, 0.45F), st = seg(q, 0.45F, 0.62F), r = seg(q, 0.75F, 1.0F);
                mArm = 0.75F + 2.15F * w - 2.55F * st + 0.4F * r;
                mLeft = 0.55F + 2.35F * w - 2.55F * st + 0.2F * r;
                mArmZ = 0.25F - 0.15F * w + 0.15F * r;
                slashLean = 0.15F * w - 0.65F * st + 0.5F * r;
                dip = -1.0F * st * (1.0F - r);
                mTilt = 0.45F * (1.0F - w) + 0.45F * r;
            }
            case ValkyrieEntity.ATTACK_SWEEP -> { // 薙ぎ払う: 右へ大きく引いて、体をひねって水平に振り抜く
                float w = seg(q, 0.0F, 0.35F), st = seg(q, 0.35F, 0.7F), r = seg(q, 0.75F, 1.0F);
                mArm = 0.75F + 0.6F * w * (1.0F - r);
                mArmZ = 0.25F + 0.75F * w - 1.2F * st + 0.45F * r;
                slashTwist = -0.9F * w + 1.8F * st - 0.9F * r;
                slashLean = -0.1F * st * (1.0F - r);
                mTilt = 0.45F + 0.6F * w * (1.0F - r);
            }
            case ValkyrieEntity.ATTACK_PUNCH_RIGHT, ValkyrieEntity.ATTACK_PUNCH_LEFT -> { // 殴る: 拳を引いてからまっすぐ突き出し、体をひねって打つ（右・左の交互）
                boolean right = kind == ValkyrieEntity.ATTACK_PUNCH_RIGHT;
                float w = seg(q, 0.0F, 0.25F), st = seg(q, 0.25F, 0.45F), r = seg(q, 0.6F, 1.0F);
                float base = fists ? 1.05F : 0.75F;
                float punch = base - 0.35F * w + (1.6F - base + 0.35F) * st - (1.6F - base) * r;
                if (right) mArm = punch;
                else mLeft = punch;
                float twist = (-0.25F * w + 0.6F * st - 0.35F * r) * (right ? 1.0F : -1.0F);
                slashTwist = twist;
                slashLean = -0.12F * st * (1.0F - r);
                if (right) mArmZ = Mth.lerp(st * (1.0F - r), mArmZ, -0.05F);
                else mLeftZ = Mth.lerp(st * (1.0F - r), mLeftZ, 0.05F);
            }
            default -> { }
        }
        if (entity.attackLeft && kind != 0 && kind != ValkyrieEntity.ATTACK_PUNCH_RIGHT && kind != ValkyrieEntity.ATTACK_PUNCH_LEFT) {
            // 二刀流の左手の攻撃: 右手で計算した動きを左手へ写し、右手は構えのまま（ひねりは逆向き）
            float base = fists ? 1.05F : 0.75F, baseZ = fists ? -0.12F : 0.25F;
            mLeft = mArm;
            mLeftZ = -mArmZ;
            mArm = base;
            mArmZ = baseZ;
            slashTwist = -slashTwist;
        }
        float recoil = Mth.sin(Math.min(1.0F, swingP * 1.6F) * Mth.PI) * (swingP > 0.0F ? 1.0F : 0.0F); // 撃った反動（速く跳ね上がって戻る）
        float shake = charge > 0.0F ? Mth.sin(age * 2.7F) * 0.03F * charge : 0.0F;
        // ステップ: 前は前のめりで腕を後ろへ流す、後ろは体を起こして腕を前へ、左右は体を傾けて腕を広げる（0.6 秒、山なりに）
        float stepT = entity.stepDir >= 0 ? (age - entity.stepStart) / 12.0F : 1.0F;
        float step = stepT >= 0.0F && stepT < 1.0F ? Mth.sin(stepT * Mth.PI) * up : 0.0F;
        int sd = entity.stepDir;
        // 前後・左右の符号は実機で確認（2026-10-06 ユーザー「前後と左右のモーションが逆」）
        float stepFwd = sd == 1 ? step : 0.0F, stepBack = sd == 0 ? step : 0.0F;
        float stepSide = sd == 2 ? -step : sd == 3 ? step : 0.0F;

        body.setRotX(body.getRotX() - 0.12F * melee + slashLean * melee - 0.5F * stepFwd + 0.25F * stepBack);
        body.setRotY(-0.25F * melee + 0.2F * aim + slashTwist * melee);
        body.setRotZ(body.getRotZ() + 0.35F * stepSide);
        bone("root").setPosY(bone("root").getPosY() + 0.6F * step + dip * melee);

        // ---- 脚: 歩きの振り＋構えの前後の開き＋ステップの蹴り足
        float legSwing = Mth.cos(limb * 0.6662F) * 1.1F * walk;
        float stance = 0.22F * ready;
        // ステップの蹴り足は控えめに（大きいと空中で開脚していた。2026-10-06）。ステップ中は歩きの振りを弱める
        float legStep = 1.0F - step * 0.7F;
        bone("right_leg").setRotX(legSwing * up * legStep - 0.3F * buckle + 1.45F * down + stance + 0.18F * stepFwd - 0.22F * stepBack);
        bone("left_leg").setRotX(-legSwing * up * legStep - 0.2F * buckle + 1.45F * down - stance * 1.3F - 0.28F * stepFwd + 0.14F * stepBack);
        bone("right_leg").setRotZ(0.1F * down + 0.08F * ready + Math.max(0.0F, -stepSide) * 0.18F);
        bone("left_leg").setRotZ(-0.1F * down - 0.08F * ready - Math.max(0.0F, stepSide) * 0.18F);

        // ---- 腕: 歩きの振り＋立ち姿の自然な開き（ゆらぎ）に、構え・斬撃・射撃・溜め・ステップを重ねる
        float armSwing = Mth.cos(limb * 0.6662F + Mth.PI) * 0.7F * walk * (1.0F - ready);
        float armOut = 0.08F + Mth.cos(age * 0.07F) * 0.03F * idle;
        float attack = Mth.sin(swingP * Mth.PI) * (1.0F - ready); // 構えていないときの普通の振り
        CoreGeoBone rightArm = bone("right_arm");
        CoreGeoBone leftArm = bone("left_arm");
        float rX = (armSwing + attack * 1.4F) * up
                + melee * mArm * (1.0F - aim)                                // 近接: 構えて、斬る・突く・叩く・薙ぎ払う
                + aim * (1.45F - 0.4F * recoil * ranged + shake)              // 射撃・溜め: 前へ向けて、撃つと跳ね上がる
                - 0.9F * stepFwd * (1.0F - ready) + 0.6F * stepBack * (1.0F - ready);
        float lX = -armSwing * up
                + melee * mLeft * (1.0F - aim)                               // 近接: 左手は前で守り（叩くときは両手）
                + aim * (twoHanded ? 1.3F - 0.3F * recoil * ranged + shake : 0.55F)  // 射撃: 両手持ちなら添える
                - 0.9F * stepFwd + 0.6F * stepBack;
        rightArm.setRotX(rX + 0.5F * buckle + 0.25F * down);
        leftArm.setRotX(lX + 0.45F * buckle + 0.25F * down);
        rightArm.setRotZ(armOut + 0.15F * down + mArmZ * melee * (1.0F - aim) + 0.6F * Math.abs(stepSide));
        // 手に持つ物の傾きの戻し: 射撃は腕を上げた分だけ戻して銃口を前へ、近接は少し戻して刃を斜め上へ、突くときは刃先を前へ（構えたときに武器が上を向いていた）
        // （射撃は戻さない: 骨の回転が 2 重に掛かっていたのを直したら、戻すと銃口が下を向いた。2026-10-06）
        // 弓だけは引くとき（腕を前へ上げる）に上げた分を戻して、弓を立てたままにする（2026-10-06「弓を持つ位置がおかしい」）
        boolean bow = entity.getMainHandItem().is(com.automatavalkyries.registry.ModWeapons.STARFALL_BOW.get());
        entity.itemTiltRight = melee * (1.0F - aim) * mTilt + aim * (bow ? ValkyrieAimTuner.bowTilt : ValkyrieAimTuner.gunTilt);
        entity.itemTiltLeft = melee * (1.0F - aim) * (entity.attackLeft && kind != 0 ? mTilt : 0.3F);
        leftArm.setRotZ(-armOut - 0.15F * down + mLeftZ * melee * (1.0F - aim) + (twoHanded ? 0.45F : 0.0F) * aim - 0.6F * Math.abs(stepSide));

        // ---- 日常（段階 4）: 腰掛け（足をぶらぶら）・充電台での整備・仕草（喜ぶ・しょんぼり・手を振る・うなずく・覗き込む）
        float sit = Mth.lerp(pt, entity.sitAnimO, entity.sitAnim) * up;
        float maint = Mth.lerp(pt, entity.chargeAnimO, entity.chargeAnim) * up;
        if (sit > 0.0F) {
            float dangle = Mth.sin(age * 0.12F) * 0.22F * sit * (1.0F - maint); // 足ぶらぶら（左右で逆）
            bone("root").setPosY(bone("root").getPosY() - SIT_DROP * sit);
            bone("right_leg").setRotX(bone("right_leg").getRotX() + 1.45F * sit + dangle);
            bone("left_leg").setRotX(bone("left_leg").getRotX() + 1.45F * sit - dangle);
            body.setRotX(body.getRotX() + 0.06F * sit);
            if (ready <= 0.0F) { // 手は膝の上
                rightArm.setRotX(rightArm.getRotX() + 0.45F * sit);
                leftArm.setRotX(leftArm.getRotX() + 0.45F * sit);
            }
        }
        if (maint > 0.0F) { // 整備: 右手を胸のコアに当て、うつむいて確かめる（左手は膝）
            rightArm.setRotX(rightArm.getRotX() + 0.75F * maint + Mth.sin(age * 0.3F) * 0.05F * maint);
            rightArm.setRotZ(rightArm.getRotZ() - 0.55F * maint);
            head.setRotX(head.getRotX() - 0.3F * maint);
        }
        int gk = entity.gestureKind;
        float gq = gk > 0 ? (age - entity.gestureStart) / ValkyrieEntity.GESTURE_TICKS[gk] : 1.0F;
        if (gq >= 0.0F && gq < 1.0F) {
            float env = Mth.sin(gq * Mth.PI) * up;
            switch (gk) {
                case ValkyrieEntity.GESTURE_JOY -> { // 2 回跳ねて両手を上げる
                    bone("root").setPosY(bone("root").getPosY() + 1.8F * Math.abs(Mth.sin(gq * Mth.PI * 2.0F)) * up);
                    rightArm.setRotX(rightArm.getRotX() + 2.4F * env);
                    leftArm.setRotX(leftArm.getRotX() + 2.4F * env);
                    rightArm.setRotZ(rightArm.getRotZ() + 0.3F * env);
                    leftArm.setRotZ(leftArm.getRotZ() - 0.3F * env);
                }
                case ValkyrieEntity.GESTURE_DEJECTED -> { // うなだれて肩を落とす
                    head.setRotX(head.getRotX() - 0.5F * env);
                    body.setRotX(body.getRotX() - 0.15F * env);
                    rightArm.setRotZ(rightArm.getRotZ() - 0.06F * env);
                    leftArm.setRotZ(leftArm.getRotZ() + 0.06F * env);
                }
                case ValkyrieEntity.GESTURE_WAVE -> { // 右手を上げて振る
                    rightArm.setRotX(rightArm.getRotX() + 2.7F * env);
                    rightArm.setRotZ(rightArm.getRotZ() + (0.35F + Mth.sin(age * 0.8F) * 0.35F) * env);
                }
                case ValkyrieEntity.GESTURE_NOD -> head.setRotX(head.getRotX() - 0.3F * Math.max(0.0F, Mth.sin(gq * Mth.PI * 4.0F)) * up); // 2 回うなずく
                case ValkyrieEntity.GESTURE_PEEK -> { // 前へ身を乗り出して覗き込む
                    body.setRotX(body.getRotX() - 0.35F * env);
                    head.setRotX(head.getRotX() - 0.2F * env);
                    rightArm.setRotX(rightArm.getRotX() + 0.3F * env);
                    leftArm.setRotX(leftArm.getRotX() + 0.3F * env);
                }
                default -> { }
            }
        }

        // ---- 髪: エンティティのばね（前へ進むほど後ろへ流れる）を後ろ髪の 3 段へ配る。横髪・リボンも少し
        float swing = Mth.lerp(pt, entity.hairSwingO, entity.hairSwing);
        float sway = Mth.lerp(pt, entity.sideSwayO, entity.sideSway);
        float breeze = Mth.sin(age * 0.06F) * 0.02F;
        // 頭の上下の向きを打ち消して、髪は付け根から真下へ垂れたままにする（2026-10-05 ユーザーの指摘「上を向いた時に髪が下に落ちる」:
        // 首の付け根より下にある後ろ髪の先が、頭が後ろへ傾くと前（体の中）へ振れていた）。横髪は少しだけ頭に付いていく
        float headPitch = head.getRotX();
        bone("hair_upper").setRotX(-(0.04F + swing * 0.45F + breeze) - headPitch);
        bone("hair_mid").setRotX(-(0.03F + swing * 0.3F + breeze));
        bone("hair_tip").setRotX(-(0.03F + swing * 0.25F + breeze * 1.5F));
        bone("hair_upper").setRotZ(sway * 0.4F);
        bone("hair_tip").setRotZ(sway * 0.3F);
        bone("side_lock_right").setRotX(-swing * 0.35F + breeze - headPitch * 0.7F);
        bone("side_lock_left").setRotX(-swing * 0.35F - breeze - headPitch * 0.7F);
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

        // ---- 胸のコア: ゆっくり脈打つ。崩れ落ちる間はちらついて弱まる
        float flicker = buckle > 0.05F ? (Mth.sin(age * 2.7F) > 0 ? 0.8F : 1.0F) : 1.0F;
        float pulse = (1.0F + Mth.sin(age * 0.12F) * 0.05F * up) * (1.0F - 0.15F * smooth(d)) * flicker;
        CoreGeoBone core = bone("core");
        core.setScaleX(pulse);
        core.setScaleY(pulse);

        // ---- Type-C: 体のまわりのビットがゆっくり回り、それぞれ上下に漂う。頭の後ろの光の輪も回る（機能停止では止まって沈む）
        CoreGeoBone bits = bone("bits");
        if (bits != null) {
            bits.setRotY(age * 0.04F * up);
            bits.setPosY(-6.0F * down);
            for (int i = 0; i < 4; i++) {
                CoreGeoBone bit = bone("bit_" + i);
                if (bit != null) bit.setPosY(Mth.sin(age * 0.09F + i * 1.7F) * 0.5F * up);
            }
        }
        CoreGeoBone halo = bone("halo");
        if (halo != null) halo.setRotZ(age * 0.03F * up);
    }

    /** q が a → b の間で 0 → 1（なめらかに） */
    private static float seg(float q, float a, float b) {
        return smooth((q - a) / (b - a));
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private CoreGeoBone bone(String name) {
        return getAnimationProcessor().getBone(name);
    }
}
