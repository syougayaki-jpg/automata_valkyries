package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * エレノアの GeckoLib モデル。
 * geo / animation / texture は {@code <namespace>/(geo|animations|textures)/entity/elenor.*}。
 *
 * <p>手足の振り・スカートの広がり・髪のなびきはここで毎フレーム計算する。
 * 回転値は GeckoLib 内部の符号（X: 正で下端が前 (-Z) へ、Z: 正で下端が右腕側へ）。
 * キーフレームで動かすのは hair_tip の Z 回転・skirt の Y 回転・skirt_spread_ctrl だけなので、
 * ここで上書きする軸とは衝突しない。</p>
 */
public class ElenorModel extends DefaultedEntityGeoModel<ElenorEntity> {

    private static final int SKIRT_PANELS = 8;
    /** スカートの基本の開き（ラジアン） */
    private static final float SKIRT_BASE = 0.30F;
    private static final float SKIRT_MAX = 1.3F;
    /** 撃破演出: 座り込みにかける Tick 数・腰を落とす量（px）・床に広がったスカートの角度 */
    private static final float DEATH_SETTLE_TICKS = 12.0F;
    private static final float SIT_DROP = 9.5F;
    private static final float SKIRT_ON_FLOOR = 1.45F;

    public ElenorModel() {
        super(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "elenor"), "head");
    }

    @Override
    public void setCustomAnimations(ElenorEntity entity, long instanceId, AnimationState<ElenorEntity> state) {
        super.setCustomAnimations(entity, instanceId, state); // 頭の向き

        float partialTick = state.getPartialTick();
        float limbSwing = state.getLimbSwing();
        // 撃破演出の進み具合（0 = 通常、1 = 座り込み完了）。通常の動きとの間を補間する
        float death = entity.deathTime > 0 ? smoothstep((entity.deathTime + partialTick) / DEATH_SETTLE_TICKS) : 0.0F;
        float alive = 1.0F - death;
        float walk = Math.min(state.getLimbSwingAmount(), 1.0F) * alive;
        float age = entity.tickCount + partialTick;
        float attackArc = Mth.sin(entity.getAttackAnim(partialTick) * Mth.PI) * alive;

        // ---- 撃破演出: その場にぺたんと座り込み、少し前かがみでうつむく ----
        bone("root").setPosY(-SIT_DROP * death);
        bone("body").setRotX(-0.12F * death); // ピボットが胸の上なので負で前かがみ
        CoreGeoBone head = bone("head");
        head.setRotX(head.getRotX() * alive - 0.4F * death); // GeckoLib は -headPitch を渡すので負でうつむく
        head.setRotY(head.getRotY() * alive);

        // ---- 手足（バニラの二足歩行と同じ振り。座り込みでは脚を前へ投げ出す） ----
        float legSwing = Mth.cos(limbSwing * 0.6662F) * 1.4F * walk;
        bone("right_leg").setRotX(legSwing + 1.45F * death);
        bone("left_leg").setRotX(-legSwing + 1.45F * death);
        bone("right_leg").setRotZ(0.12F * death);
        bone("left_leg").setRotZ(-0.12F * death);

        float armSwing = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 0.8F * walk;
        CoreGeoBone rightArm = bone("right_arm");
        CoreGeoBone leftArm = bone("left_arm");
        rightArm.setRotX(armSwing + attackArc * 1.6F + 0.35F * death);
        leftArm.setRotX(-armSwing + 0.35F * death);

        // ---- スカート: 基本 + 歩行 + 攻撃 + キーフレーム（skirt_spread_ctrl の X 回転量）。座り込むと床に広がる ----
        float keyframed = Math.abs(bone("skirt_spread_ctrl").getRotX());
        float spread = Mth.clamp(SKIRT_BASE + walk * 0.35F + attackArc * 0.45F + keyframed, 0.0F, SKIRT_MAX) * alive
                + SKIRT_ON_FLOOR * death;
        for (int i = 0; i < SKIRT_PANELS; i++) {
            float flutter = Mth.sin(age * 0.25F + i * 0.8F) * 0.03F * (0.3F + walk) * alive;
            bone("skirt_panel_" + i).setRotX(spread + flutter);
        }

        // 腕はスカートに埋まらないよう、広がりに合わせて外へ
        float armOut = (0.12F + Mth.cos(age * 0.09F) * 0.05F + 0.05F) * alive + spread * 0.25F + 0.15F * death;
        rightArm.setRotZ(armOut);
        leftArm.setRotZ(-armOut);

        // ---- 髪: 広がったスカートの外側を沿うように後ろへ流す（座り込むと床に広がる） ----
        // hair_mid（y=16 から 6px）はスカート上端（y=14）の 2px 上から始まるので、
        // スカート後面の外側 z = 3.3 + (14 - y)·tanφ を下回らない角度を選ぶ（geo.json の寸法に依存）
        float skirtSlope = (float) Math.tan(spread);
        float upper = 0.1F + walk * 0.2F; // 厚い後ろ髪がスカート上端（z=3.3）に掛からない角度
        float mid = Math.max(0.1F, (float) Math.atan((0.2F + 4.0F * skirtSlope) / 6.0F)) + 0.03F + walk * 0.1F;
        float tip = Math.max(mid, (float) Math.atan(1.05F * skirtSlope) + 0.03F) + walk * 0.1F;
        bone("hair_upper").setRotX(-upper);
        bone("hair_mid").setRotX(-(mid - upper));
        bone("hair_tip").setRotX(-(tip - mid));
    }

    private static float smoothstep(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private CoreGeoBone bone(String name) {
        return getAnimationProcessor().getBone(name);
    }
}
