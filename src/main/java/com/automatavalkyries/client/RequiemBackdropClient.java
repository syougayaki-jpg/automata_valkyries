package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModWeapons;
import com.automatavalkyries.weapon.LuminousRequiemItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 星葬冠『ルミナス・レクイエム』を持っている者（プレイヤーでも兵姫でも、どの生き物でも）の背に、光輪と 6 振りの剣を展開して描く
 * （2026-10-04 ユーザー指定。手に残るのは聖剣だけ）。体の向きに合わせて背中の少し後ろ・肩の高さに立て、いつも明るく光らせる。
 * <ul>
 *   <li>光輪: ユーザーの絵から切り出したテクスチャを加算で重ねる（黒は見えない）。外の淡い光（大きく薄く）・光輪（前後にずらした 7 枚で厚み、
 *       ゆっくり回る）・中心の円（逆に回る、3 枚）・光の芯（脈打つ）。明るさは少し抑える（2026-10-04 ユーザー指定「少し暗く、何枚か重ねて厚みを」）</li>
 *   <li>6 振りの剣: モデル（{@code requiem_backdrop}）を光輪の外側に</li>
 * </ul>
 * 溜めている間は溜まるほど大きく、明るく、速く回る。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class RequiemBackdropClient {

    private static final ResourceLocation HALO = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/requiem_halo.png");
    private static final ResourceLocation INNER = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/requiem_halo_inner.png");
    private static final ResourceLocation CORE = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/requiem_halo_core.png");
    /** 光輪の半分の大きさ（ブロック、背丈 1.8 のとき）。テクスチャは 600 画素の正方形で、星の先は半径 292 画素 */
    private static final float HALF = 1.0F;
    /** 中心の円の半分の大きさ（テクスチャの切り出し 112 / 300） */
    private static final float INNER_HALF = HALF * 112F / 300F;
    /** 光輪を重ねる枚数と、1 枚ごとの前後のずれ（ブロック）。全部で厚さおよそ 0.18 ブロック */
    private static final int LAYERS = 7;
    /** 6 振りの剣が向く角度（モデルの requiem_backdrop と同じ。左右対称なので、モデルの x の向きが逆でも同じ所に重なる） */
    private static final float[] BIT_DIRS = {30, 0, -30, 150, 180, 210};
    private static final float LAYER_GAP = 0.03F;

    private static ItemStack backdrop;

    private RequiemBackdropClient() {}

    static boolean holds(LivingEntity e) {
        return e.getMainHandItem().getItem() instanceof LuminousRequiemItem || e.getOffhandItem().getItem() instanceof LuminousRequiemItem;
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        render(event.getEntity(), event.getPoseStack(), event.getMultiBufferSource(), event.getPartialTick());
    }

    /**
     * 持ち主の背に飾りを描く。バニラの生き物は RenderLivingEvent から、GeckoLib の戦術兵姫は {@code ValkyrieRenderer#render} から呼ぶ
     * （GeckoLib のレンダラーは LivingEntityRenderer ではないので RenderLivingEvent が来ず、兵姫に飾りが出ていなかった。2026-10-05）。
     * pose はエンティティの位置（体の向きを掛ける前）
     */
    public static void render(LivingEntity e, PoseStack renderPose, MultiBufferSource renderBuffers, float renderPartialTick) {
        if (e.isInvisible() || e.isSleeping() || !holds(e)) return;
        if (backdrop == null) backdrop = new ItemStack(ModWeapons.REQUIEM_BACKDROP.get());
        float pt = renderPartialTick;
        float time = e.tickCount + pt;
        float charge = e.isUsingItem() && e.getUseItem().getItem() instanceof LuminousRequiemItem requiem ? requiem.chargeProgress(e) : 0F;
        float bodyYaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        PoseStack pose = renderPose;
        MultiBufferSource buffers = renderBuffers;
        pose.pushPose();
        // 体の向きに合わせる（この向きでは +Z が背中側）。肩の高さ、背中の少し後ろに立てる。ゆっくり上下に漂う
        pose.mulPose(Axis.YP.rotationDegrees(180F - bodyYaw));
        float height = e.getBbHeight() * (e.getPose() == Pose.CROUCHING ? 0.55F : 0.68F);
        float bob = Mth.sin(time * 0.06F) * 0.05F;
        pose.translate(0, height + bob, 0.6F + 0.15F * charge);
        float s = (e.getBbHeight() / 1.8F) * (1F + 0.3F * charge);
        pose.scale(s, s, s);

        float spin = time * (0.35F + 1.5F * charge);
        float bright = 0.4F + 0.05F * Mth.sin(time * 0.1F) + 0.2F * charge; // 抑えた明るさ（2026-10-04 ユーザー指定で 3 度下げた）
        // 外の淡い光（光輪を大きく薄く重ねて、にじんだ光に）
        quad(pose, buffers, HALO, HALF * 1.12F, spin, -LAYER_GAP * (LAYERS / 2 + 1), 0.12F * bright);
        // 光輪の厚み: 前後に少しずつずらして何枚も重ねる。奥ほど少し大きく暗く、手前の 1 枚だけ明るい（加算なので重ねた分だけ明るくなるのを抑える）
        for (int i = 0; i < LAYERS; i++) {
            float depth = (i - (LAYERS - 1) / 2F) * LAYER_GAP; // 奥（-）から手前（+）へ
            boolean front = i == LAYERS - 1;
            float back = (LAYERS - 1 - i) / (float) (LAYERS - 1); // 0 = 手前、1 = 一番奥
            quad(pose, buffers, HALO, HALF * (1F + 0.025F * back), spin - back * 1.5F, depth, (front ? 0.5F : 0.12F) * bright);
        }
        // 中心の円（逆に回る。3 枚で厚み）
        for (int i = 0; i < 3; i++) {
            float depth = LAYER_GAP * (LAYERS / 2F) + 0.01F + i * LAYER_GAP * 0.6F;
            quad(pose, buffers, INNER, INNER_HALF * (1F - 0.03F * i), -spin * 2.2F + i * 2F, depth, (i == 2 ? 0.5F : 0.18F) * bright);
        }
        // 光の芯（脈打つ）
        float pulse = 1F + 0.18F * Mth.sin(time * 0.25F) + 0.5F * charge;
        float front = LAYER_GAP * (LAYERS / 2F) + 0.05F;
        quad(pose, buffers, CORE, HALF * 0.38F * pulse, time * 0.8F, front, Math.min(1F, 0.36F + 0.35F * charge));
        quad(pose, buffers, CORE, HALF * 0.85F * pulse, -time * 0.3F, front + 0.005F, 0.12F + 0.2F * charge);

        // 6 振りの剣の刃先の薄い光（モデルの刃: 外向きに 23 px から 7.6 px、幅 1.7 → 0.7 px。表示の大きさ 0.85）
        float px = 0.85F / 16F;
        for (int k = 0; k < BIT_DIRS.length; k++) {
            float a = BIT_DIRS[k];
            BladeAura.draw(pose, buffers, Mth.cos(a * Mth.DEG_TO_RAD) * 23F * px, Mth.sin(a * Mth.DEG_TO_RAD) * 23F * px, a, 7.6F * px,
                    1.7F * px, 0.7F * px, time, k * 0.17F, 1.0F, 0xB8F0FF); // 刃先は目立たせる（2026-10-04 ユーザー指定）
        }

        // 6 振りの剣（光輪の外）
        Minecraft.getInstance().getItemRenderer().renderStatic(backdrop, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY,
                pose, buffers, e.level(), e.getId());
        pose.popPose();
    }

    /** 背の平面（xy、z だけずらす）に、中心のまわりに angle 度回した正方形を加算で描く（表からも裏からも見える） */
    static void quad(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float half, float angle, float z, float brightness) {
        pose.pushPose();
        pose.translate(0, 0, z);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(texture));
        float c = Mth.clamp(brightness, 0F, 1F);
        float[][] k = {{-half, -half, 0, 1}, {half, -half, 1, 1}, {half, half, 1, 0}, {-half, half, 0, 0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] v = k[pass == 0 ? i : 3 - i];
                vc.vertex(m, v[0], v[1], 0).color(c, c, c, 1F).uv(v[2], v[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(0xF000F0).normal(n, 0, 0, pass == 0 ? 1 : -1).endVertex();
            }
        }
        pose.popPose();
    }
}
