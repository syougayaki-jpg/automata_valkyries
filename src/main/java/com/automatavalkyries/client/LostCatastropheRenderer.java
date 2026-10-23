package com.automatavalkyries.client;

import com.automatavalkyries.entity.zero.LostCatastropheEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * 零号機のレンダラー（2026-10-20）: モデル（約 2 ブロック）を 1.25 倍にして約 2.5 ブロックで描く（{@link LostCatastropheEntity#renderScale}。2026-10-20 ユーザー指定「人と同じくらい」、形態で大きさは変えない）。
 * 発光部（面頬・胸の核・光輪・翼の赤・紫の羽の縁）は lost_catastrophe_glowmask.png で光らせ、第 3 形態は少し脈打つ。
 * 撃破では倒れて回らず、赤くもならない（ほかの AEC 級と同じ）。詠唱の間は足元から天へ赤い光の柱、同期過電圧ショックが溜まる間は水色の輪。
 * 第 2 形態から、空に赤黒い裂け目（中は赤と紫の星雲、縁は赤く光る）が開き、まわりに建物の残骸が浮かび上がってそのまま残る（第 3 形態で増える。
 * 2026-10-20 ユーザー指定「裂け目と残骸を形態変化にとっておく」「第 2 形態からずっと」。中心は {@link LostCatastropheEntity#riftAnchor}）
 * 体（首から下・翼・スカート）は OBJ の面のまま骨に載せて描く（{@link ObjBoneMesh}。2026-10-21 ユーザー指定「オススメのやりかたで全部かえてみて」）。
 * 頭（目・まばたき・面頬）と大剣・黒い渦は geo の箱。光る所は箱と同じ光の絵で光らせる
 */
public class LostCatastropheRenderer extends GeoEntityRenderer<LostCatastropheEntity> {

    private static final ObjBoneMesh MESH = new ObjBoneMesh("lost_catastrophe");

    public LostCatastropheRenderer(EntityRendererProvider.Context context) {
        super(context, new LostCatastropheModel());
        this.shadowRadius = 0.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public void render(PoseStack pose, LostCatastropheEntity e, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                               float partialTick, int light, int overlay) {
                float age = e.tickCount + partialTick;
                // 光る所は本体の絵から消されて光の層だけで描かれるので、脈打ちや撃破の消え方は不透明度でなく明るさで付ける（不透明度を下げると透ける。2026-10-21）
                float k = e.phase() == 3 ? 0.8F + 0.2F * Mth.sin(age * 0.3F) : 1.0F;
                if (e.deathTime > 0) k *= Math.max(0.15F, 1.0F - e.deathTime / 120.0F);
                RenderType glow = getRenderType(e);
                getRenderer().reRender(model, pose, buffers, e, glow, buffers.getBuffer(glow), partialTick, LightTexture.FULL_SKY, OverlayTexture.NO_OVERLAY, k, k, k, 1.0F);
            }
        });
    }

    @Override
    public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
        super.renderCubesOfBone(pose, bone, buffer, light, overlay, r, g, b, a);
        if (!bone.isHidden()) MESH.render(bone.getName(), pose, buffer, light, overlay, r, g, b, a);
    }

    @Override
    protected float getDeathMaxRotation(LostCatastropheEntity e) {
        return 0.0F;
    }

    @Override
    public int getPackedOverlay(LostCatastropheEntity e, float u) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(e.hurtTime > 0 && e.deathTime == 0));
    }

    @Override
    public boolean shouldRender(LostCatastropheEntity e, Frustum frustum, double x, double y, double z) {
        if (e.riftAnchor() != null && e.phase() >= 2) return true;                          // 空の裂け目と残骸は本体が見えなくても描く
        return frustum.isVisible(e.getBoundingBox().inflate(e.chantLeft() > 0 ? 40.0 : 3.5));   // 横へ広げた翼の分
    }

    @Override
    public void render(LostCatastropheEntity e, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float s = e.renderScale();
        pose.pushPose();
        pose.scale(s, s, s);
        super.render(e, entityYaw, partialTick, pose, buffers, light);
        pose.popPose();
        renderChant(e, partialTick, pose, buffers);
        renderRifts(e, partialTick, pose, buffers, light);
    }

    private static final ResourceLocation RIFT_TEX = ResourceLocation.fromNamespaceAndPath("automata_valkyries", "textures/entity/zero_rift.png");
    /** 裂け目の中心からのずれ（ブロック）: 第 2 形態は 2 つ、第 3 形態は 4 つ */
    private static final double[][] RIFTS = {{-15, 4, -16}, {15, 5, -15}, {-26, 9, 2}, {26, 8, 3}};
    private static final BlockState[] RUIN_BLOCKS = {
            Blocks.STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
            Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
            Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
            Blocks.STONE_BRICK_SLAB.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState()};
    private static final int RIFT_SEGS = 28;
    private static final float RIFT_H = 30.0F, RIFT_W = 4.8F;

    /** 第 2 形態からの空の裂け目と浮かぶ残骸（現れるときは 3 秒かけて開き、浮かび上がる） */
    private static void renderRifts(LostCatastropheEntity e, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        BlockPos anchor = e.riftAnchor();
        if (anchor == null || e.phase() < 2 || e.isDefeated()) return;
        float age = e.tickCount + pt;
        float open = e.phaseChangedTick < -1000 ? 1.0F : Mth.clamp((age - e.phaseChangedTick) / 60.0F, 0.0F, 1.0F);   // 読み直したときは開いたまま
        open = open * open * (3 - 2 * open);
        Vec3 here = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        Vec3 base = Vec3.atBottomCenterOf(anchor).subtract(here);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(here);
        int n = e.phase() >= 3 ? 4 : 2;
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        VertexConsumer tex = buffers.getBuffer(RenderType.entityTranslucentEmissive(RIFT_TEX));
        float w = RIFT_W * open;
        for (int i = 0; i < n; i++) {
            Vec3 c = base.add(RIFTS[i][0], RIFTS[i][1], RIFTS[i][2]);
            Vec3 side = facing(cam, c);
            int al = (int) (235 * Math.min(1.0F, open * 1.5F));
            for (int k = 0; k < RIFT_SEGS; k++) {
                float t0 = k / (float) RIFT_SEGS, t1 = (k + 1) / (float) RIFT_SEGS;
                float[] a = riftEdge(i, t0, age), b = riftEdge(i, t1, age);
                riftVertex(tex, m, nm, at(c, side, a[0] - a[1] * w, t0), 0.0F, t0, al);
                riftVertex(tex, m, nm, at(c, side, a[0] + a[1] * w, t0), 1.0F, t0, al);
                riftVertex(tex, m, nm, at(c, side, b[0] + b[1] * w, t1), 1.0F, t1, al);
                riftVertex(tex, m, nm, at(c, side, b[0] - b[1] * w, t1), 0.0F, t1, al);
            }
        }
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());                       // 縁の赤い光
        for (int i = 0; i < n; i++) {
            Vec3 c = base.add(RIFTS[i][0], RIFTS[i][1], RIFTS[i][2]);
            Vec3 side = facing(cam, c);
            Vec3 fwd = cam.subtract(c).multiply(1, 0, 1).normalize().scale(0.05);
            for (int k = 0; k < RIFT_SEGS; k++) {
                float t0 = k / (float) RIFT_SEGS, t1 = (k + 1) / (float) RIFT_SEGS;
                float[] a = riftEdge(i, t0, age), b = riftEdge(i, t1, age);
                int al = (int) (150 * open * (0.75F + 0.25F * Mth.sin(age * 0.2F + k)));
                for (int sd = -1; sd <= 1; sd += 2) {
                    Vec3 i0 = at(c, side, a[0] + sd * a[1] * w * 0.9F, t0).add(fwd), o0 = at(c, side, a[0] + sd * (a[1] * w + 0.55F), t0).add(fwd);
                    Vec3 i1 = at(c, side, b[0] + sd * b[1] * w * 0.9F, t1).add(fwd), o1 = at(c, side, b[0] + sd * (b[1] * w + 0.55F), t1).add(fwd);
                    glow.vertex(m, (float) i0.x, (float) i0.y, (float) i0.z).color(255, 40, 70, al).endVertex();
                    glow.vertex(m, (float) o0.x, (float) o0.y, (float) o0.z).color(140, 0, 30, 0).endVertex();
                    glow.vertex(m, (float) o1.x, (float) o1.y, (float) o1.z).color(140, 0, 30, 0).endVertex();
                    glow.vertex(m, (float) i1.x, (float) i1.y, (float) i1.z).color(255, 40, 70, al).endVertex();
                }
            }
        }
        // 浮かぶ残骸: 石の塊が中心のまわりにゆっくり浮かんで回る（現れるときは下から浮かび上がる）
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        int clusters = e.phase() >= 3 ? 16 : 9;
        for (int i = 0; i < clusters; i++) {
            RandomSource r = RandomSource.create(9173L * (i + 1));
            double ang = r.nextDouble() * Math.PI * 2, rad = 8.0 + r.nextDouble() * 10.0, hy = 1.0 + r.nextDouble() * 10.0;
            float rise = (1.0F - open) * 8.0F;
            double bob = Math.sin(age * 0.03 + i) * 0.4;
            float spin = (0.3F + r.nextFloat() * 0.5F) * (r.nextBoolean() ? 1 : -1), yaw0 = r.nextFloat() * 360, tilt = r.nextFloat() * 40 - 20;
            pose.pushPose();
            pose.translate(base.x + Math.cos(ang) * rad, base.y + hy + bob - rise, base.z + Math.sin(ang) * rad);
            pose.mulPose(Axis.YP.rotationDegrees(age * spin + yaw0));
            pose.mulPose(Axis.XP.rotationDegrees(tilt));
            int count = 2 + r.nextInt(5);
            for (int k = 0; k < count; k++) {
                pose.pushPose();
                pose.translate(r.nextInt(3) - 1.0, r.nextInt(2), r.nextInt(2) - 0.5);
                float sc = 0.6F + r.nextFloat() * 0.4F;
                pose.scale(sc, sc, sc);
                blocks.renderSingleBlock(RUIN_BLOCKS[r.nextInt(RUIN_BLOCKS.length)], pose, buffers, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            pose.popPose();
        }
    }

    /** 板の横の向き（板がいつも見る人の方を向くよう、縦の軸のまわりに回す） */
    private static Vec3 facing(Vec3 cam, Vec3 c) {
        Vec3 toCam = cam.subtract(c).multiply(1, 0, 1);
        if (toCam.lengthSqr() < 1.0e-4) toCam = new Vec3(0, 0, 1);
        return new Vec3(toCam.z, 0, -toCam.x).normalize();
    }

    private static Vec3 at(Vec3 c, Vec3 side, float x, float t) {
        return c.add(side.scale(x)).add(0, t * RIFT_H - RIFT_H / 2, 0);
    }

    /** 裂け目の形: 高さの割合 t での {真ん中の横のずれ, 幅の割合}（上下はとがり、縁はぎざぎざ。ゆっくり揺らぐ） */
    private static float[] riftEdge(int i, float t, float age) {
        float jag = 0.62F + 0.38F * Mth.sin(t * 37.0F + i * 5.1F) * Mth.sin(t * 13.0F + i);
        float width = (float) Math.pow(Math.max(0.0, Math.sin(Math.PI * t)), 0.6) * jag * (0.92F + 0.08F * Mth.sin(age * 0.05F + t * 9));
        float center = 1.6F * Mth.sin(t * 4.5F + i * 2.0F) + 0.6F * Mth.sin(t * 11.0F + i);
        return new float[]{center, width};
    }

    private static void riftVertex(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 p, float u, float v, int alpha) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(255, 255, 255, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(nm, 0, 1, 0).endVertex();
    }

    /** 詠唱の光の柱（赤い外・白い芯。残りが少ないほど太く）と、同期過電圧ショックの水色の輪（溜まるほど縮んで明るく） */
    private static void renderChant(LostCatastropheEntity e, float pt, PoseStack pose, MultiBufferSource buffers) {
        int left = e.chantLeft();
        if (left <= 0 || e.isDefeated()) return;
        float age = e.tickCount + pt;
        float k = 1.0F - left / (float) LostCatastropheEntity.CHANT_TICKS;
        Matrix4f m = pose.last().pose();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3[] ab = GalliaSiegeBeamRenderer.basis(up);
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        float pulse = 0.85F + 0.15F * Mth.sin(age * 0.6F);
        GalliaSiegeBeamRenderer.tube(glow, m, up, ab[0], ab[1], -2.0F, 90.0F, (1.2F + 1.6F * k) * pulse, 255, 40, 70, (int) (70 + 60 * k));
        GalliaSiegeBeamRenderer.tube(glow, m, up, ab[0], ab[1], -2.0F, 90.0F, 0.35F + 0.4F * k, 255, 235, 235, 170);
        for (int i = 0; i < 3; i++) {                                                       // 天へ昇る輪
            float u = ((age * 0.02F + i / 3.0F) % 1.0F);
            float r = 4.0F + 2.0F * Mth.sin(age * 0.05F + i);
            GalliaSiegeBeamRenderer.ring(glow, m, up.scale(u * 30.0F), ab[0], ab[1], r - 0.2F, r, 255, 60, 150, (int) (160 * (1.0F - u)));
        }
        int shock = e.shockCharge();
        if (shock > 0) {
            float q = shock / (float) LostCatastropheEntity.SHOCK_TICKS;
            float r = 8.0F * (1.0F - q) + 2.5F;
            Vec3 c = up.scale(1.6);
            GalliaSiegeBeamRenderer.ring(glow, m, c, ab[0], ab[1], r - 0.3F, r, 127, 228, 255, (int) (120 + 120 * q));
            GalliaSiegeBeamRenderer.ring(glow, m, c, ab[0], ab[1], r * 0.7F - 0.2F, r * 0.7F, 255, 255, 255, (int) (80 + 150 * q));
        }
    }
}
