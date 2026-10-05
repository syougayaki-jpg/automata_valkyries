package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.particle.FxOptions;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.registry.ModWeapons;
import com.automatavalkyries.weapon.BlackHoleEntity;
import com.automatavalkyries.weapon.CatastropheBusterItem;
import com.automatavalkyries.weapon.ChargedWeaponItem;
import com.automatavalkyries.weapon.PunishmentRailgunItem;
import com.automatavalkyries.weapon.RagnarokEdgeItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * T4 兵装の背の飾り（2026-10-04 ユーザー指定。ジェネシス・ヘイローとルミナス・レクイエムは別のクラス）。持っている者（プレイヤーでも兵姫でも）の背に描く。
 * <ul>
 *   <li>極光轟雷『パニッシュメント・レールガン』: 噛み合って回る漆黒と白銀の歯車（モデル {@code railgun_backdrop}）と、時計仕掛けの陣。
 *       溜めるほど歯車が加速し（{@link RailgunGearModel}）、噛み合う所の火花が激しく白熱する</li>
 *   <li>特異点砲『カタストロフ・バスター』: 禍々しい渦（黒い芯・暗い紅と紫の渦の腕を半透明で重ね、腕の縁と降着円盤を光らせる）。空間の破片が吸い込まれる。
 *       溜めるほど急速に縮んで一点へ潰れていき、芯が閃く</li>
 *   <li>終の黄昏『ラグナロク』: 沈む夕焼け（地平で下が切れた夕陽・揺らめく光芒・地平線の光と夕焼けの雲の帯）。雲が流れながら現れては消える。火の粉が昇る。
 *       溜めるほど夕陽が燃え上がって輝く</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class T4BackdropClient {

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/" + name + ".png");
    }

    private static final ResourceLocation CLOCKWORK = tex("railgun_clockwork"), VORTEX = tex("buster_vortex"), VORTEX_GLOW = tex("buster_vortex_glow"),
            SUN = tex("ragnarok_sun"), RAYS = tex("ragnarok_rays"), HORIZON = tex("ragnarok_horizon"), CLOUD = tex("ragnarok_cloud");
    private static final float BACK = 0.7F, HEIGHT = 0.68F;
    private static ItemStack gears, vortex, sunset;
    /** 歯車の大歯車の角度（度）。描く直前に持ち主ごとの値を入れる（{@link RailgunGearModel} が読む） */
    static float gearAngle;
    /** 持ち主ごとの歯車の角度と、前に描いた時刻（溜めると加速するので、時刻から求めずに積み上げる） */
    private static final java.util.Map<Integer, float[]> GEAR_STATE = new java.util.HashMap<>();

    private T4BackdropClient() {}

    enum Kind { RAILGUN, BUSTER, RAGNAROK }

    static @Nullable Kind kindOf(Item item) {
        if (item instanceof PunishmentRailgunItem) return Kind.RAILGUN;
        if (item instanceof CatastropheBusterItem) return Kind.BUSTER;
        if (item instanceof RagnarokEdgeItem) return Kind.RAGNAROK;
        return null;
    }

    static @Nullable Kind held(LivingEntity e) {
        Kind k = kindOf(e.getMainHandItem().getItem());
        return k != null ? k : kindOf(e.getOffhandItem().getItem());
    }

    /** 飾りの大きさ（体の大きさに合わせる）。バスターだけ溜めるほど急速に縮み、ほかは溜めるほど大きくなる */
    static float scale(LivingEntity e, Kind kind, float charge) {
        float base = e.getBbHeight() / 1.8F;
        if (kind == Kind.BUSTER) {
            float k = 1F - (1F - charge) * (1F - charge) * (1F - charge);
            return base * (1F - 0.82F * k);
        }
        return base * (1F + 0.25F * charge);
    }

    /** 歯車の回る速さ（大歯車、度/Tick）。溜めるほど加速する */
    static float gearSpeed(float charge) {
        return 0.375F * (1F + 14F * charge * charge);
    }

    static float charge(LivingEntity e) {
        return e.isUsingItem() && e.getUseItem().getItem() instanceof ChargedWeaponItem c ? c.chargeProgress(e) : 0F;
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
        if (e.isInvisible() || e.isSleeping()) return;
        Kind kind = held(e);
        if (kind == null) return;
        float pt = renderPartialTick;
        float time = e.tickCount + pt;
        float charge = charge(e);
        float bodyYaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        PoseStack pose = renderPose;
        MultiBufferSource buffers = renderBuffers;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180F - bodyYaw)); // この向きでは +Z が背中側
        float height = e.getBbHeight() * (e.getPose() == Pose.CROUCHING ? 0.58F : HEIGHT);
        pose.translate(0, height + Mth.sin(time * 0.05F) * 0.05F, BACK + 0.15F * charge);
        float s = scale(e, kind, charge);
        pose.scale(s, s, s);
        switch (kind) {
            case RAILGUN -> railgun(pose, buffers, e, time, charge);
            case BUSTER -> buster(pose, buffers, e, time, charge);
            case RAGNAROK -> ragnarok(pose, buffers, e, time, charge);
        }
        pose.popPose();
    }

    /** 歯車と時計仕掛けの陣（陣は前・中・奥の 3 段。奥の段は背景の大歯車の後ろ） */
    private static void railgun(PoseStack pose, MultiBufferSource buffers, LivingEntity e, float time, float charge) {
        float[] st = GEAR_STATE.computeIfAbsent(e.getId(), id -> new float[]{time, 0F});
        float dt = time - st[0];
        if (dt < 0F || dt > 5F) dt = 0F;
        st[0] = time;
        st[1] = (st[1] + dt * gearSpeed(charge)) % 360000F;
        if (GEAR_STATE.size() > 256) GEAR_STATE.clear();
        gearAngle = st[1];
        float bright = 0.45F + 0.4F * charge;
        float spin = st[1] * 0.5F;
        rect(pose, buffers, RenderType.eyes(CLOCKWORK), 1.5F, 1.5F, spin * 0.4F, 0, 0, 0.5F, 0.3F * bright);
        rect(pose, buffers, RenderType.eyes(CLOCKWORK), 1.1F, 1.1F, -spin, 0, 0, 0.08F, 0.5F * bright);
        rect(pose, buffers, RenderType.eyes(CLOCKWORK), 0.55F, 0.55F, spin * 2F, 0.72F, 0.42F, -0.1F, 0.45F * bright);
        if (gears == null) gears = new ItemStack(ModWeapons.RAILGUN_BACKDROP.get());
        Minecraft.getInstance().getItemRenderer().renderStatic(gears, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY, pose, buffers, e.level(), e.getId());
    }

    /** 禍々しい渦: 奥へ沈む漏斗（半透明の暗い腕を、奥ほど小さく速く 4 段に重ねる）、縁と降着円盤の光、立体の破片の腕と円盤 */
    private static void buster(PoseStack pose, MultiBufferSource buffers, LivingEntity e, float time, float charge) {
        float spin = time * (2.2F + 4F * charge);
        float a = Math.min(1F, 0.85F + 0.15F * charge);
        float[][] funnel = {{1.7F, 0.0F, 1F}, {1.25F, 0.18F, 1.5F}, {0.85F, 0.34F, 2.1F}, {0.5F, 0.48F, 2.9F}};
        for (int i = funnel.length - 1; i >= 0; i--) {
            float[] f = funnel[i];
            rect(pose, buffers, RenderType.entityTranslucent(VORTEX), f[0], f[0], spin * f[2] + i * 33F, 0, 0, f[1], a * (i == 0 ? 0.9F : 0.8F), 1F, 1F, 1F);
        }
        float glow = 0.5F + 0.4F * charge + 0.1F * Mth.sin(time * 0.3F);
        rect(pose, buffers, RenderType.eyes(VORTEX_GLOW), 1.7F, 1.7F, spin, 0, 0, -0.01F, glow);
        rect(pose, buffers, RenderType.eyes(VORTEX_GLOW), 0.9F, 0.9F, spin * 2.1F, 0, 0, 0.3F, glow * 0.8F);
        rect(pose, buffers, RenderType.eyes(VORTEX_GLOW), 0.45F, 0.45F, spin * 3F, 0, 0, 0.46F, glow);
        if (charge > 0F) { // 潰れていく芯の閃き（縮むほど強く、脈打つ）
            float flare = charge * (0.7F + 0.3F * Mth.sin(time * 1.3F));
            rect(pose, buffers, RenderType.eyes(VORTEX_GLOW), 0.35F + 0.25F * charge, 0.35F + 0.25F * charge, -spin * 4F, 0, 0, -0.03F, flare);
            rect(pose, buffers, RenderType.eyes(VORTEX_GLOW), 0.2F, 0.2F, spin * 5F, 0, 0, -0.05F, flare);
        }
        if (vortex == null) vortex = new ItemStack(ModWeapons.BUSTER_BACKDROP.get());
        Minecraft.getInstance().getItemRenderer().renderStatic(vortex, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY, pose, buffers, e.level(), e.getId());
    }

    /** 沈む夕焼け: 奥から光芒・夕陽と暈・地平線の帯 2 段、立体の雲・光冠・水面の照り返し */
    private static void ragnarok(PoseStack pose, MultiBufferSource buffers, LivingEntity e, float time, float charge) {
        float H = 1.25F;
        float horizonY = -0.12F * H;
        float bright = 0.55F + 0.45F * charge + 0.06F * Mth.sin(time * 0.07F);
        float sway = Mth.sin(time * 0.02F) * 6F;
        rect(pose, buffers, RenderType.eyes(RAYS), H * 1.35F, H * 1.35F, sway, 0, 0.0F, 0.42F, 0.45F * bright);
        rect(pose, buffers, RenderType.eyes(RAYS), H * 1.15F, H * 1.15F, -sway * 0.6F, 0, 0.0F, 0.3F, 0.35F * bright);
        rect(pose, buffers, RenderType.eyes(SUN), H * 1.1F, H * 1.1F, 0, 0, -0.01F, 0.2F, 0.25F * bright);
        rect(pose, buffers, RenderType.eyes(SUN), H, H, 0, 0, 0, 0.12F, bright);
        float bw = 2.1F, bh = 0.52F;
        float bandCenter = horizonY + (0.62F - 0.5F) * 2 * bh;
        rect(pose, buffers, RenderType.eyes(HORIZON), bw * 1.15F, bh * 1.2F, 0, 0, bandCenter, 0.3F, 0.5F * bright);
        rect(pose, buffers, RenderType.eyes(HORIZON), bw, bh, 0, 0, bandCenter, 0.0F, bright);
        if (charge > 0F) { // 溜めるほど夕陽が燃え上がる: 大きな暈・強い光芒・地平線の閃き・手前の白金の芯
            float pulse = 0.85F + 0.15F * Mth.sin(time * 0.6F);
            rect(pose, buffers, RenderType.eyes(SUN), H * (1.3F + 0.9F * charge), H * (1.3F + 0.9F * charge), 0, 0, -0.01F, 0.08F, 0.7F * charge * pulse);
            rect(pose, buffers, RenderType.eyes(RAYS), H * (1.4F + 0.5F * charge), H * (1.4F + 0.5F * charge), time * 0.4F, 0, 0, 0.36F, 0.8F * charge);
            rect(pose, buffers, RenderType.eyes(RAYS), H * (1.2F + 0.4F * charge), H * (1.2F + 0.4F * charge), -time * 0.25F, 0, 0, 0.26F, 0.6F * charge);
            rect(pose, buffers, RenderType.eyes(HORIZON), bw * (1F + 0.3F * charge), bh, 0, 0, bandCenter, -0.03F, charge * pulse);
            rect(pose, buffers, RenderType.eyes(SUN), H * 0.75F, H * 0.75F, 0, 0, -0.05F, -0.06F, charge * charge);
        }
        // 流れながら現れては消える薄い雲のたなびき（周期と位相と高さと奥行きを変えて 6 枚）
        float[][] wisps = {{0.62F, 0.42F, 37F, 0.0F, 1.1F}, {0.48F, -0.18F, 29F, 0.35F, 0.9F}, {0.3F, 0.7F, 43F, 0.6F, 1.3F},
                {0.12F, 0.2F, 23F, 0.15F, 0.8F}, {-0.08F, 0.55F, 31F, 0.8F, 1.0F}, {-0.15F, -0.05F, 19F, 0.5F, 0.7F}};
        for (int i = 0; i < wisps.length; i++) {
            float[] w = wisps[i];
            float u = ((time / (w[2] * 20F) + w[3]) % 1F + 1F) % 1F;
            float env = Mth.sin(u * Mth.PI);
            float dir = i % 2 == 0 ? 1F : -1F;
            float alpha = env * env * (0.75F - 0.3F * charge);
            if (alpha < 0.01F) continue;
            float tint = 1F + 0.25F * charge;
            rect(pose, buffers, RenderType.entityTranslucent(CLOUD), w[4] * (0.7F + 0.3F * env), 0.22F * w[4] * (0.6F + 0.4F * env), 0,
                    dir * (u - 0.5F) * 1.6F, w[1], w[0], alpha, tint, tint * (0.95F + 0.1F * charge), 1F);
        }
        if (sunset == null) sunset = new ItemStack(ModWeapons.RAGNAROK_BACKDROP.get());
        Minecraft.getInstance().getItemRenderer().renderStatic(sunset, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY, pose, buffers, e.level(), e.getId());
    }

    /** 加算の板（中心 (cx, cy)、横の半分 hw・縦の半分 hh、angle 度回す） */
    private static void rect(PoseStack pose, MultiBufferSource buffers, RenderType type, float hw, float hh, float angle, float cx, float cy, float z, float bright) {
        float c = Mth.clamp(bright, 0F, 1F);
        rect(pose, buffers, type, hw, hh, angle, cx, cy, z, 1F, c, c, c);
    }

    /** 板（色と透明度つき）。表からも裏からも見える */
    private static void rect(PoseStack pose, MultiBufferSource buffers, RenderType type, float hw, float hh, float angle, float cx, float cy, float z,
                             float alpha, float r, float g, float b) {
        pose.pushPose();
        pose.translate(cx, cy, z);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        VertexConsumer vc = buffers.getBuffer(type);
        float[][] k = {{-hw, -hh, 0, 1}, {hw, -hh, 1, 1}, {hw, hh, 1, 0}, {-hw, hh, 0, 0}};
        float a = Mth.clamp(alpha, 0F, 1F);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] v = k[pass == 0 ? i : 3 - i];
                vc.vertex(m, v[0], v[1], 0).color(Mth.clamp(r, 0F, 1F), Mth.clamp(g, 0F, 1F), Mth.clamp(b, 0F, 1F), a).uv(v[2], v[3])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 0, pass == 0 ? 1 : -1).endVertex();
            }
        }
        pose.popPose();
    }

    /** 歯車の噛み合う所（モデルの px。x は持ち主の横、y は上、z は奥） */
    private static final double[][] MESH = {{11.78, 5.49, 0}, {20.6, 16.06, 3.2}, {-11.97, -5.08, 0}, {-23.4, -2.64, 0}, {-17.84, -13.54, -2.6}, {10.24, -8.0, 0}};

    /** p0 から p1 へ、ぎざぎざに折れる電光（光の粒を並べる） */
    private static void arc(Minecraft mc, Vec3 p0, Vec3 p1, int color, RandomSource r) {
        int segs = 6;
        Vec3 prev = p0;
        for (int i = 1; i <= segs; i++) {
            Vec3 p = p0.add(p1.subtract(p0).scale(i / (double) segs));
            if (i < segs) p = p.add((r.nextDouble() - 0.5) * 0.18, (r.nextDouble() - 0.5) * 0.18, (r.nextDouble() - 0.5) * 0.18);
            Vec3 d = p.subtract(prev);
            int steps = Math.max(2, (int) (d.length() / 0.06));
            for (int k = 0; k < steps; k++) {
                Vec3 q = prev.add(d.scale(k / (double) steps));
                mc.level.addParticle(new FxOptions(ModParticles.GLOW.get(), k % 3 == 0 ? 0xFFFFFF : color, 0.05F, 4, 0F), q.x, q.y, q.z, 0, 0, 0);
            }
            prev = p;
        }
    }

    /** 粒子（クライアントだけ、近くの持ち主ごと）: 歯の火花・吸い込まれる空間の破片・昇る火の粉 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        RandomSource r = mc.level.random;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity e) || e.isInvisible() || e.distanceToSqr(mc.player) > 64 * 64) continue;
            Kind kind = held(e);
            if (kind == null) continue;
            float charge = charge(e);
            float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
            Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw)), side = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
            float s = scale(e, kind, charge);
            float base = e.getBbHeight() / 1.8F;
            Vec3 c = e.position().add(0, e.getBbHeight() * HEIGHT, 0).add(back.scale(BACK + 0.15F * charge));
            int count = 1 + (int) (charge * (kind == Kind.RAILGUN ? 10 : 3));
            long tick = e.tickCount;
            switch (kind) {
                case RAILGUN -> {
                    // 歯車の噛み合う所から散る火花（溜めて速く回るほど激しく、速く、白熱する）
                    double px = 0.62 / 16 * s;
                    for (double[] m : MESH) {
                        if (r.nextFloat() > 0.12F + 0.88F * charge) continue;
                        Vec3 at = c.add(side.scale(m[0] * px)).add(0, m[1] * px, 0).add(back.scale(m[2] * px - 0.05));
                        int burst = 1 + (int) (charge * charge * 6);
                        for (int k = 0; k < burst; k++) {
                            double sp = 0.04 + 0.22 * charge * r.nextDouble();
                            Vec3 v = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.3, r.nextDouble() - 0.5).normalize().scale(sp).subtract(back.scale(0.04 * charge));
                            int col = charge > 0.5F && r.nextInt(3) == 0 ? 0xFFF4D0 : (r.nextBoolean() ? 0x40E0FF : 0xA050FF);
                            mc.level.addParticle(new FxOptions(ModParticles.SPARK.get(), col, 0.1F + 0.08F * charge, 6 + r.nextInt(6), 0F), at.x, at.y, at.z, v.x, v.y, v.z);
                        }
                        if (charge > 0.6F && r.nextInt(6) == 0) {
                            mc.level.addParticle(new FxOptions(ModParticles.GLOW.get(), 0xC8F8FF, 0.25F * s, 4, 0F), at.x, at.y, at.z, 0, 0, 0);
                        }
                    }
                    if (tick % 7 == 0 || (charge > 0 && tick % Math.max(1, 3 - (int) (charge * 2.5F)) == 0)) {
                        double a0 = r.nextDouble() * Math.PI * 2, a1 = a0 + 1.2 + r.nextDouble() * 2;
                        Vec3 p0 = c.add(side.scale(Math.cos(a0) * 0.85 * s)).add(0, Math.sin(a0) * 0.85 * s, 0);
                        Vec3 p1 = c.add(side.scale(Math.cos(a1) * 1.3 * s)).add(0, Math.sin(a1) * 1.3 * s, 0);
                        arc(mc, p0, p1, r.nextBoolean() ? 0x40E0FF : 0xC8F8FF, r);
                    }
                }
                case BUSTER -> {
                    if (tick % 11 == 0 || (charge > 0 && tick % 4 == 0)) {
                        double a0 = r.nextDouble() * Math.PI * 2;
                        Vec3 p0 = c.add(side.scale(Math.cos(a0) * 1.6 * s)).add(0, Math.sin(a0) * 1.6 * s, 0);
                        arc(mc, p0, c.add(back.scale(0.3)), r.nextBoolean() ? 0xC050FF : 0xFF5070, r);
                    }
                    if (tick % 30 == 0) {
                        mc.level.addParticle(new FxOptions(ModParticles.BEAM_RING.get(), 0xC02040, 1.9F * s, 26, 0F), c.x, c.y, c.z, back.x, back.y, back.z);
                    }
                }
                case RAGNAROK -> {
                    if (tick % (charge > 0 ? 2 : 5) == 0) {
                        double a0 = Math.PI * (0.08 + r.nextDouble() * 0.84);
                        Vec3 dir = side.scale(Math.cos(a0)).add(0, Math.sin(a0), 0);
                        Vec3 p0 = c.add(0, -0.15 * s, 0).add(dir.scale(0.55 * s)).subtract(back.scale(0.05));
                        mc.level.addParticle(new FxOptions(ModParticles.STAR.get(), r.nextBoolean() ? 0xFFD070 : 0xFF8A40, 0.15F, 22, 0F), p0.x, p0.y, p0.z, dir.x * 0.05, dir.y * 0.05, dir.z * 0.05);
                    }
                    if (tick % 40 == 0) {
                        Vec3 p0 = c.add(0, -0.05 * s, 0).subtract(back.scale(0.1));
                        mc.level.addParticle(new FxOptions(ModParticles.STAR.get(), 0xFFF0C0, 0.9F * s, 26, 0F), p0.x, p0.y, p0.z, 0, 0, 0);
                    }
                }
            }
            for (int i = 0; i < count; i++) {
                switch (kind) {
                    case RAILGUN -> { // 歯車の歯から散る火花（シアン・紫）
                        if (charge == 0F && r.nextInt(2) != 0) continue;
                        double a = r.nextDouble() * Math.PI * 2, rad = (r.nextBoolean() ? 0.95 : 0.65) * s;
                        double ox = r.nextBoolean() ? 0 : (r.nextBoolean() ? 1 : -1) * 1.25 * s;
                        Vec3 p = c.add(side.scale(ox + Math.cos(a) * rad)).add(0, Math.sin(a) * rad + (ox != 0 ? 0.45 * s : 0), 0);
                        mc.level.addParticle(new FxOptions(ModParticles.SPARK.get(), r.nextBoolean() ? 0x40E0FF : 0xA050FF, 0.12F, 8, 0F),
                                p.x, p.y, p.z, (r.nextDouble() - 0.5) * 0.05, 0.02, (r.nextDouble() - 0.5) * 0.05);
                    }
                    case BUSTER -> { // 外から渦を巻いて吸い込まれる空間の破片と暗い光
                        double a = r.nextDouble() * Math.PI * 2, rad = (1.4 + r.nextDouble() * 0.4) * Math.max(s, base * (1 + 0.3 * charge));
                        Vec3 p = c.add(side.scale(Math.cos(a) * rad)).add(0, Math.sin(a) * rad, 0);
                        Vec3 tangent = side.scale(-Math.sin(a)).add(0, Math.cos(a), 0);
                        Vec3 v = c.subtract(p).scale(0.05 + 0.07 * charge).add(tangent.scale(0.05 * (1 - 0.5 * charge)));
                        boolean shard = r.nextBoolean();
                        mc.level.addParticle(new FxOptions(shard ? ModParticles.VOID_SHARD.get() : ModParticles.GLOW.get(), shard ? 0xFF6A30 : 0x601030, shard ? 0.12F : 0.3F, 20, 0F),
                                p.x, p.y, p.z, v.x, v.y, v.z);
                    }
                    case RAGNAROK -> { // 地平線から昇る火の粉
                        if (charge == 0F && r.nextInt(2) != 0) continue;
                        double ox = (r.nextDouble() - 0.5) * 3.6 * s;
                        Vec3 p = c.add(side.scale(ox)).add(0, -0.15 * s, 0);
                        mc.level.addParticle(new FxOptions(ModParticles.GLOW.get(), r.nextBoolean() ? 0xFF8A30 : 0xFFD070, 0.08F + r.nextFloat() * 0.06F, 40, 0F),
                                p.x, p.y, p.z, (r.nextDouble() - 0.5) * 0.01, 0.015 + r.nextDouble() * 0.01, (r.nextDouble() - 0.5) * 0.01);
                    }
                }
            }
        }
    }
}
