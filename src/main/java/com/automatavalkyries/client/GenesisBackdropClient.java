package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.particle.FxOptions;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.registry.ModWeapons;
import com.automatavalkyries.weapon.GenesisHaloItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ジェネシス・ヘイローの待機形態「光輪モード」（2026-10-04 ユーザー指定）: 持っている者（プレイヤーでも兵姫でも、どの生き物でも）の背に、
 * 多重に展開した巨大な黄金と白銀の浮遊リング（内周に高純度ルミナルクリスタル）と、高精細の光る魔法陣を重ねて描き、光の粒子を放ちながら滞空させる。
 * <ul>
 *   <li>魔法陣: 外の陣（1024 px、ルーンの帯・十二芒星・六芒星・節の円）を前後にずらした 3 枚で厚みを出してゆっくり回し、内の印（512 px）を逆に速く回す。加算で描く</li>
 *   <li>リング: モデル（{@code genesis_backdrop}）。4 重の輪がそれぞれ傾いて逆向きに回る</li>
 *   <li>光の粒子: リングの縁から金・白・水色の星と光がふわりと漂い出る（クライアントだけ）</li>
 * </ul>
 * 溜めている間は溜まるほど大きく、明るく、速く回り、粒子も増える。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class GenesisBackdropClient {

    private static final ResourceLocation CIRCLE = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/genesis_circle.png");
    private static final ResourceLocation INNER = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/genesis_circle_inner.png");
    /** 外の魔法陣の半分の大きさ（ブロック、背丈 1.8 のとき。外のリング（半径 28 px）に合わせる） */
    private static final float CIRCLE_HALF = 1.8F;
    private static final float INNER_HALF = 0.5F;
    /** 背中からの距離と、高さ（背丈に対する割合） */
    private static final float BACK = 0.75F, HEIGHT = 0.7F;

    private static ItemStack rings;

    private GenesisBackdropClient() {}

    static boolean holds(LivingEntity e) {
        return e.getMainHandItem().getItem() instanceof GenesisHaloItem || e.getOffhandItem().getItem() instanceof GenesisHaloItem;
    }

    static float charge(LivingEntity e) {
        return e.isUsingItem() && e.getUseItem().getItem() instanceof GenesisHaloItem halo ? halo.chargeProgress(e) : 0F;
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity e = event.getEntity();
        if (e.isInvisible() || e.isSleeping() || !holds(e)) return;
        if (rings == null) rings = new ItemStack(ModWeapons.GENESIS_BACKDROP.get());
        float pt = event.getPartialTick();
        float time = e.tickCount + pt;
        float charge = charge(e);
        float bodyYaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180F - bodyYaw)); // この向きでは +Z が背中側
        float height = e.getBbHeight() * (e.getPose() == Pose.CROUCHING ? 0.6F : HEIGHT);
        pose.translate(0, height + Mth.sin(time * 0.05F) * 0.06F, BACK + 0.2F * charge);
        float s = (e.getBbHeight() / 1.8F) * (1F + 0.25F * charge);
        pose.scale(s, s, s);

        float spin = time * (0.25F + 1.2F * charge);
        float bright = 0.42F + 0.06F * Mth.sin(time * 0.08F) + 0.25F * charge;
        // 外の魔法陣（リングの奥。前後にずらした 3 枚で厚み。奥ほど少し大きく暗い）
        for (int i = 0; i < 3; i++) {
            float depth = -0.1F - (2 - i) * 0.035F;
            boolean front = i == 2;
            RequiemBackdropClient.quad(pose, buffers, CIRCLE, CIRCLE_HALF * (1F + 0.02F * (2 - i)), spin - (2 - i) * 1.2F, depth, (front ? 0.6F : 0.16F) * bright);
        }
        // 内の印（いちばん内の輪の中、手前。逆に速く回す）
        for (int i = 0; i < 2; i++) {
            RequiemBackdropClient.quad(pose, buffers, INNER, INNER_HALF * (1F - 0.04F * i), -spin * 2.5F + i * 3F, 0.05F + i * 0.03F, (i == 1 ? 0.65F : 0.2F) * bright);
        }
        // 多重のリング（モデル）
        Minecraft.getInstance().getItemRenderer().renderStatic(rings, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY,
                pose, buffers, e.level(), e.getId());
        pose.popPose();
    }

    /** リングの縁から光の粒子を放つ（クライアントだけ。近くの持ち主ごと） */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        RandomSource r = mc.level.random;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity e) || e.isInvisible() || !holds(e) || e.distanceToSqr(mc.player) > 64 * 64) continue;
            float charge = charge(e);
            int count = 1 + (int) (charge * 4) + (r.nextInt(3) == 0 ? 1 : 0);
            float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
            Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw)); // 背中の向き
            Vec3 side = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
            float s = (e.getBbHeight() / 1.8F) * (1F + 0.25F * charge);
            Vec3 center = e.position().add(0, e.getBbHeight() * HEIGHT, 0).add(back.scale(BACK + 0.2F * charge));
            for (int i = 0; i < count; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double rad = (r.nextInt(3) == 0 ? 0.95 : 1.65 + r.nextDouble() * 0.15) * s;
                Vec3 p = center.add(side.scale(Math.cos(a) * rad)).add(0, Math.sin(a) * rad, 0);
                Vec3 out = side.scale(Math.cos(a) * 0.01).add(0, Math.sin(a) * 0.01 + 0.012, 0).add(back.scale(0.004));
                int color = switch (r.nextInt(4)) {
                    case 0 -> 0xFFFFFF;
                    case 1 -> 0xC8F4FF;
                    default -> 0xFFE8A0;
                };
                boolean star = r.nextInt(3) == 0;
                mc.level.addParticle(new FxOptions(star ? ModParticles.STAR.get() : ModParticles.GLOW.get(), color, star ? 0.18F : 0.1F, 30 + r.nextInt(20), 0F),
                        p.x, p.y, p.z, out.x, out.y, out.z);
            }
        }
    }
}
