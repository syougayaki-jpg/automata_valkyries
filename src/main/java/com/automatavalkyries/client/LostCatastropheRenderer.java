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
 * 頭（目・まばたき・面頬）と大剣・黒い渦は geo の箱。光る所は箱と同じ光の絵で光らせる。
 * 演出（2026-10-26 ユーザー指定「演出を改良する」）: 出現は真上の裂け目からデータが降り、体がちらつきながら組み上がる。撤退は真上の裂け目へ崩れて消える
 * （{@link #glitchJitter}）。撃破では裂け目が閉じ、残骸が沈む
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
        if (introAge(e, e.tickCount) >= 0 || retreatAge(e, e.tickCount) >= 0) return true;  // 出現・撤退の真上の裂け目
        if (e.horizonRadius() > 0 || e.finaleTicks() > 0 || e.tickCount - e.animTick[LostCatastropheEntity.EV_ECHOES - 90] < LostCatastropheEntity.ECHO_TICKS) return true;   // 終末の技は闘技場じゅうに描く
        return frustum.isVisible(e.getBoundingBox().inflate(e.chantLeft() > 0 ? 40.0 : 3.5));   // 横へ広げた翼の分
    }

    @Override
    public void render(LostCatastropheEntity e, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float s = e.renderScale();
        float age = e.tickCount + partialTick;
        float jitter = glitchJitter(e, age);
        if (jitter >= 0.0F) {                                                                  // 出現・撤退で組み上がる・崩れる間はちらつき、横にずれる
            pose.pushPose();
            if (jitter > 0.0F) {
                int f = (int) (age * 2.0F);
                pose.translate((hash(e.getId(), f) - 0.5F) * jitter, 0.0, (hash(e.getId(), f + 977) - 0.5F) * jitter);
            }
            pose.scale(s, s, s);
            super.render(e, entityYaw, partialTick, pose, buffers, light);
            pose.popPose();
        }
        renderChant(e, partialTick, pose, buffers);
        renderRifts(e, partialTick, pose, buffers, light);
        renderHorizon(e, partialTick, pose, buffers);
        renderFinale(e, partialTick, pose, buffers);
        renderEchoes(e, partialTick, pose, buffers);
    }

    /** 終末の技の中心（本体からのずれ。無ければ null） */
    private static Vec3 fieldOffset(LostCatastropheEntity e, float pt) {
        BlockPos c = e.fieldCenter();
        if (c == null) return null;
        Vec3 here = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        return Vec3.atBottomCenterOf(c).subtract(here);
    }

    /** 事象の地平: 縮んでくる輪の赤紫に光る幕（下は濃く、上へ薄れる）と、床の縁の輪 */
    private static void renderHorizon(LostCatastropheEntity e, float pt, PoseStack pose, MultiBufferSource buffers) {
        float r = e.horizonRadius();
        Vec3 off = fieldOffset(e, pt);
        if (r <= 0.0F || off == null) return;
        float age = e.tickCount + pt;
        pose.pushPose();
        pose.translate(off.x, off.y, off.z);
        Matrix4f m = pose.last().pose();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3[] ab = GalliaSiegeBeamRenderer.basis(up);
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        float pulse = 0.8F + 0.2F * Mth.sin(age * 0.4F);
        for (int k = 0; k < 6; k++) {                                                         // 幕は段に分けて上ほど薄く
            GalliaSiegeBeamRenderer.tube(glow, m, up, ab[0], ab[1], k * 1.5F, (k + 1) * 1.5F, r, 200, 30, 90, (int) (110 * pulse * (1.0F - k / 6.0F)));
        }
        GalliaSiegeBeamRenderer.ring(glow, m, up.scale(0.08), ab[0], ab[1], r - 0.4F, r, 255, 40, 70, (int) (200 * pulse));
        GalliaSiegeBeamRenderer.ring(glow, m, up.scale(0.06), ab[0], ab[1], r, r + 2.5F, 120, 0, 60, 90);
        pose.popPose();
    }

    /** 終幕: 床じゅうが崩れて赤く燃える（描くだけ）中に、楽章の安全な足場が白く光る。鎮魂の光が降る間は床が強く光る */
    private static void renderFinale(LostCatastropheEntity e, float pt, PoseStack pose, MultiBufferSource buffers) {
        int f = e.finaleTicks();
        BlockPos center = e.fieldCenter();
        Vec3 off = fieldOffset(e, pt);
        if (f <= 0 || off == null) return;
        float age = e.tickCount + pt;
        int m = LostCatastropheEntity.movement(f);
        pose.pushPose();
        pose.translate(off.x, off.y, off.z);
        Matrix4f mat = pose.last().pose();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3[] ab = GalliaSiegeBeamRenderer.basis(up);
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        float in = Mth.clamp(f / 30.0F, 0.0F, 1.0F);
        int s = LostCatastropheEntity.movementTick(f);
        boolean pulsing = m >= 0 && m < 2 && s >= 60;
        // 楽章の色: 第 1 は赤、第 2 は青、終楽章は白（2026-10-26 声なき葬送）
        int cr = m == 1 ? 40 : m == 2 ? 230 : 160, cg = m == 1 ? 80 : m == 2 ? 220 : 10, cb = m == 1 ? 180 : m == 2 ? 235 : 30;
        float finalK = m == 2 ? Mth.clamp(s / (float) LostCatastropheEntity.FINAL_BLOW, 0.0F, 1.0F) : 0.0F;
        int floorA = (int) ((pulsing ? 90 : 45 + 15 * Mth.sin(age * 0.2F)) * in * (m == 2 ? 0.4F + 0.6F * finalK : 1.0F));
        GalliaSiegeBeamRenderer.ring(glow, mat, up.scale(0.05), ab[0], ab[1], 0.0F, 34.0F, cr, cg, cb, floorA);          // 崩れて燃える床（描くだけ）
        for (int i = 0; i < 5; i++) {                                                              // 床に走るひび（同心の輪）
            float rr = 6.0F + i * 6.0F + Mth.sin(age * 0.05F + i) * 0.5F;
            GalliaSiegeBeamRenderer.ring(glow, mat, up.scale(0.07), ab[0], ab[1], rr - 0.12F, rr, Math.min(255, cr + 95), Math.min(255, cg + 30), Math.min(255, cb + 40), (int) (140 * in));
        }
        // 空に回る五線（文字の代わりの楽譜）: 5 本の輪が高さをずらして回り、波打つ。終楽章は零号機へ縮んでいく
        float staffR = 26.0F * (1.0F - 0.85F * finalK);
        for (int i = 0; i < 5; i++) {
            float y = 13.0F + i * 0.7F + Mth.sin(age * 0.04F + i * 0.9F) * 0.4F;
            float r = staffR + Mth.sin(age * 0.07F + i) * 0.6F;
            GalliaSiegeBeamRenderer.ring(glow, mat, up.scale(y * (1.0F - finalK) + (float) (e.getY() - e.fieldCenter().getY() + LostCatastropheEntity.CHEST_H) * finalK), ab[0], ab[1], r - 0.08F, r, Math.min(255, cr + 95), Math.min(255, cg + 60), Math.min(255, cb + 60), (int) (170 * in));
        }
        if (m >= 0 && center != null) {
            Vec3 base = Vec3.atBottomCenterOf(center);
            float a = Mth.clamp(s / 10.0F, 0.0F, 1.0F);
            for (int i = 0; i < LostCatastropheEntity.platformCount(m); i++) {
                Vec3 p = LostCatastropheEntity.platform(center, m, i).subtract(base).add(0, 0.1, 0);
                float r = (float) LostCatastropheEntity.PLATFORM_R;
                GalliaSiegeBeamRenderer.ring(glow, mat, p, ab[0], ab[1], 0.0F, r, 255, 255, 255, (int) (70 * a));             // 光る足場
                GalliaSiegeBeamRenderer.ring(glow, mat, p.add(0, 0.02, 0), ab[0], ab[1], r - 0.2F, r, 255, 255, 255, (int) (220 * a));
                Vec3 q = p.subtract(0, 0.1, 0);
                pose.pushPose();
                pose.translate(q.x, q.y, q.z);
                GalliaSiegeBeamRenderer.tube(glow, pose.last().pose(), up, ab[0], ab[1], 0.0F, 6.0F, r * 0.92F, 255, 240, 245, (int) (40 * a * (0.7F + 0.3F * Mth.sin(age * 0.3F + i))));   // 足場から立つ光
                pose.popPose();
            }
        }
        pose.popPose();
    }

    /** 葬送曲の残像に使う、世界に置かない描くだけのボス（種類ごとに 1 体。ワールドが変わったら作り直す） */
    private final net.minecraft.world.entity.Entity[] puppets = new net.minecraft.world.entity.Entity[4];

    @SuppressWarnings("unchecked")
    private static final java.util.function.Supplier<net.minecraft.world.entity.EntityType<?>>[] ECHO_TYPES = new java.util.function.Supplier[]{
            () -> com.automatavalkyries.registry.ModEntities.LUXION.get(), () -> com.automatavalkyries.registry.ModEntities.GALLIAFORT.get(),
            () -> com.automatavalkyries.registry.ModEntities.MEMENTO_MORI.get(), () -> com.automatavalkyries.registry.ModEntities.TIME_RULER.get()};

    /**
     * 零号の葬送曲の残像（2026-10-26）: ルクシオン・ガリアフォート・メメントモリ・タイムルーラーの姿を、零号機と同じくらいの大きさに縮め、
     * それぞれの色で赤黒く染めて描く（タイムルーラーの残像の分身 {@link RulerEchoRenderer} と同じく、世界に置かない描くだけのボス）。
     * 現れる時と消える時はちらつく
     */
    private void renderEchoes(LostCatastropheEntity e, float pt, PoseStack pose, MultiBufferSource buffers) {
        float age = e.tickCount + pt, t = age - e.animTick[LostCatastropheEntity.EV_ECHOES - 90];
        if (t < 0 || t > LostCatastropheEntity.ECHO_TICKS || e.echoAt[0] == null) return;
        Vec3 here = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        float vis = Mth.clamp(t / 12.0F, 0.0F, 1.0F) * Mth.clamp((LostCatastropheEntity.ECHO_TICKS - t) / 16.0F, 0.0F, 1.0F);
        for (int i = 0; i < 4; i++) {
            if (hash(e.getId() + i, (int) (age * 2.0F)) > 0.25F + 0.75F * vis) continue;      // 現れる・消える間はちらつく
            net.minecraft.world.entity.Entity p = puppets[i];
            if (p == null || p.level() != e.level()) {
                p = puppets[i] = ECHO_TYPES[i].get().create(e.level());
                if (p == null) continue;
            }
            Vec3 at = e.echoAt[i];
            p.setPos(at.x, at.y, at.z);
            p.tickCount = e.tickCount;
            p.setYRot(e.echoYaw);
            p.yRotO = e.echoYaw;
            if (p instanceof net.minecraft.world.entity.LivingEntity le) le.yBodyRot = le.yBodyRotO = le.yHeadRot = le.yHeadRotO = e.echoYaw;
            float k = 2.6F / Math.max(0.5F, p.getBbHeight());                                // 零号機と同じくらいの大きさに
            int col = LostCatastropheEntity.ECHO_COLORS[i];
            float jitter = (1.0F - vis) * 0.4F;
            pose.pushPose();
            pose.translate(at.x - here.x + (hash(i, (int) (age * 2.0F)) - 0.5F) * jitter, at.y - here.y + Mth.sin(age * 0.1F + i) * 0.15F, at.z - here.z);
            pose.scale(k, k, k);
            MultiBufferSource tinted = rt -> new Tinted(buffers.getBuffer(rt), ((col >> 16) & 255) / 255.0F, ((col >> 8) & 255) / 255.0F * 0.55F, (col & 255) / 255.0F * 0.55F);
            try {
                dispatcher.getRenderer(p).render(p, e.echoYaw, pt, pose, tinted, LightTexture.FULL_BRIGHT);
            } catch (RuntimeException ex) {
                puppets[i] = null;                                                           // 描けない残像は諦める（次の技で作り直す）
            }
            pose.popPose();
        }
    }

    /** 色を掛けて赤黒く染める（残像用） */
    private record Tinted(VertexConsumer d, float r, float g, float b) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            d.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int cr, int cg, int cb, int ca) {
            d.color((int) (cr * r), (int) (cg * g), (int) (cb * b), ca);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            d.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            d.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            d.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            d.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            d.endVertex();
        }

        @Override
        public void defaultColor(int cr, int cg, int cb, int ca) {
            d.defaultColor((int) (cr * r), (int) (cg * g), (int) (cb * b), ca);
        }

        @Override
        public void unsetDefaultColor() {
            d.unsetDefaultColor();
        }
    }

    /** 出現の経過（Tick。出現していなければ負） */
    static float introAge(LostCatastropheEntity e, float age) {
        float t = age - e.introTick;
        return t >= 0 && t < LostCatastropheEntity.INTRO_TICKS + 40 ? t : -1.0F;
    }

    /** 撤退の経過（Tick。退いていなければ負） */
    static float retreatAge(LostCatastropheEntity e, float age) {
        float t = age - e.animTick[LostCatastropheEntity.EV_RETREAT - 90];
        return t >= 0 && t < LostCatastropheEntity.RETREAT_TICKS + 20 ? t : -1.0F;
    }

    /**
     * 体を描くか・どれだけ横にずらすか（2026-10-26 演出の改良）: -1 は描かない、0 はふつう、正はちらつきの横ずれ（ブロック）。
     * 出現は 50 Tick まで見えず、50〜72 でデータが組み上がるようにちらついて現れる。撤退は 25〜55 で崩れるようにちらつき、55 から見えない。
     * 撃破の初め（固まった直後）も時々ずれる
     */
    static float glitchJitter(LostCatastropheEntity e, float age) {
        int f = (int) (age * 2.0F);
        float in = introAge(e, age);
        if (in >= 0) {
            if (in < 50) return -1.0F;
            if (in < 72) {
                float k = (in - 50) / 22.0F;
                return hash(e.getId(), f + 31) < 0.55F * (1.0F - k) ? -1.0F : 0.5F * (1.0F - k);
            }
        }
        float re = retreatAge(e, age);
        if (re >= 0) {
            if (re >= 55) return -1.0F;
            if (re >= 25) {
                float k = (re - 25) / 30.0F;
                return hash(e.getId(), f + 57) < 0.15F + 0.6F * k ? -1.0F : 0.15F + 0.5F * k;
            }
        }
        if (e.deathTime > 0 && e.deathTime < 40 && hash(e.getId(), f + 91) < 0.18F) return 0.3F;
        return 0.0F;
    }

    static float hash(int id, int f) {
        int h = id * 0x9E3779B1 + f * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFF) / 65535.0F;
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

    /** 描く裂け目 1 つ（中心は本体からのずれ・形の番号・開き具合） */
    private record Rift(Vec3 c, int shape, float open) {}

    /**
     * 第 2 形態からの空の裂け目と浮かぶ残骸（現れるときは 3 秒かけて開き、浮かび上がる。撃破では 100〜140 Tick で閉じ、残骸は沈む）。
     * 出現・撤退の間は本体の真上にもう 1 つ開く（出現は 0〜20 で開いて 100〜130 で閉じ、撤退は 20〜40 で開いて 55〜80 で閉じる。2026-10-26 演出の改良）
     */
    private static void renderRifts(LostCatastropheEntity e, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.tickCount + pt;
        Vec3 here = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(here);
        java.util.List<Rift> rifts = new java.util.ArrayList<>();
        float in = introAge(e, age), re = retreatAge(e, age);
        if (in >= 0) rifts.add(new Rift(new Vec3(0, 10.0, 0), 1, smoothstep(in / 20.0F) * (1.0F - smoothstep((in - 100) / 30.0F))));
        if (re >= 0) rifts.add(new Rift(new Vec3(0, 9.0, 0), 2, smoothstep((re - 20) / 20.0F) * (1.0F - smoothstep((re - 55) / 25.0F))));
        BlockPos anchor = e.riftAnchor();
        float open = 0.0F;
        Vec3 base = Vec3.ZERO;
        int n = 0;
        if (anchor != null && e.phase() >= 2) {
            open = e.phaseChangedTick < -1000 ? 1.0F : Mth.clamp((age - e.phaseChangedTick) / 60.0F, 0.0F, 1.0F);   // 読み直したときは開いたまま
            open = smoothstep(open);
            if (e.deathTime > 0) open *= 1.0F - smoothstep((e.deathTime + pt - 100) / 40.0F);   // 撃破: 閉じていく
            base = Vec3.atBottomCenterOf(anchor).subtract(here);
            n = e.phase() >= 3 ? 4 : 2;
            for (int i = 0; i < n && open > 0.0F; i++) rifts.add(new Rift(base.add(RIFTS[i][0], RIFTS[i][1], RIFTS[i][2]), i, open));
        }
        if (rifts.isEmpty()) return;
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        VertexConsumer tex = buffers.getBuffer(RenderType.entityTranslucentEmissive(RIFT_TEX));
        for (Rift r : rifts) {
            if (r.open() <= 0.0F) continue;
            Vec3 side = facing(cam, r.c());
            float w = RIFT_W * r.open();
            int al = (int) (235 * Math.min(1.0F, r.open() * 1.5F));
            for (int k = 0; k < RIFT_SEGS; k++) {
                float t0 = k / (float) RIFT_SEGS, t1 = (k + 1) / (float) RIFT_SEGS;
                float[] a = riftEdge(r.shape(), t0, age), b = riftEdge(r.shape(), t1, age);
                riftVertex(tex, m, nm, at(r.c(), side, a[0] - a[1] * w, t0), 0.0F, t0, al);
                riftVertex(tex, m, nm, at(r.c(), side, a[0] + a[1] * w, t0), 1.0F, t0, al);
                riftVertex(tex, m, nm, at(r.c(), side, b[0] + b[1] * w, t1), 1.0F, t1, al);
                riftVertex(tex, m, nm, at(r.c(), side, b[0] - b[1] * w, t1), 0.0F, t1, al);
            }
        }
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());                       // 縁の赤い光
        for (Rift r : rifts) {
            if (r.open() <= 0.0F) continue;
            Vec3 side = facing(cam, r.c());
            Vec3 fwd = cam.subtract(r.c()).multiply(1, 0, 1).normalize().scale(0.05);
            float w = RIFT_W * r.open();
            for (int k = 0; k < RIFT_SEGS; k++) {
                float t0 = k / (float) RIFT_SEGS, t1 = (k + 1) / (float) RIFT_SEGS;
                float[] a = riftEdge(r.shape(), t0, age), b = riftEdge(r.shape(), t1, age);
                int al = (int) (150 * r.open() * (0.75F + 0.25F * Mth.sin(age * 0.2F + k)));
                for (int sd = -1; sd <= 1; sd += 2) {
                    Vec3 i0 = at(r.c(), side, a[0] + sd * a[1] * w * 0.9F, t0).add(fwd), o0 = at(r.c(), side, a[0] + sd * (a[1] * w + 0.55F), t0).add(fwd);
                    Vec3 i1 = at(r.c(), side, b[0] + sd * b[1] * w * 0.9F, t1).add(fwd), o1 = at(r.c(), side, b[0] + sd * (b[1] * w + 0.55F), t1).add(fwd);
                    glow.vertex(m, (float) i0.x, (float) i0.y, (float) i0.z).color(255, 40, 70, al).endVertex();
                    glow.vertex(m, (float) o0.x, (float) o0.y, (float) o0.z).color(140, 0, 30, 0).endVertex();
                    glow.vertex(m, (float) o1.x, (float) o1.y, (float) o1.z).color(140, 0, 30, 0).endVertex();
                    glow.vertex(m, (float) i1.x, (float) i1.y, (float) i1.z).color(255, 40, 70, al).endVertex();
                }
            }
        }
        if (n == 0 || open <= 0.0F) return;
        // 浮かぶ残骸: 石の塊が中心のまわりにゆっくり浮かんで回る（現れるときは下から浮かび上がり、撃破では沈む）
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

    static float smoothstep(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3 - 2 * x);
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
