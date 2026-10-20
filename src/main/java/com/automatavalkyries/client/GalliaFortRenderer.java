package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.GalliaFortEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.world.phys.Vec3;

/**
 * ガリアフォートのレンダラー: モデルを {@link GalliaFortEntity#SIZE} 倍にし、まわりに歪曲障壁を描く（2026-10-10）。
 * 障壁は白い正立方体の枠（2026-10-10 ユーザー指定「歪曲障壁はわかりやすく白いフレームに包まれているように」→「歪曲炉のように正立方体フレームで」→
 * 「球状のバリアを無くして立方体フレームをいくつか追加して違う軸方向に回転させる」）: 歪曲力場発生炉の絵と同じ、12 本の白く光る棒と角の 8 つの明るい継ぎ目の立方体を
 * 大きさを変えて {@link #CUBES} つ重ね、それぞれ違う軸（縦・横・前後）のまわりに違う速さで回す。棒は空間の歪みで少し波打つ（受けたとき・残りが少ないときは大きく）。
 * 地面より下は描かない。受けると光って回りが一瞬速まり、残りが少ないと明滅する。砕けると消え、張り直すと小さい所からふくらむ。
 * 見やすく（2026-10-10 ユーザー指定「見やすくするため半透明にして三原色の光を立方体フレームの線上を走らせて」）: 枠は薄い半透明（{@link #FRAME_ALPHA}）にし、
 * 棒ごとに赤・緑・青の光の粒が 2 つずつ線の上を走る（歪曲力場発生炉の三つの光。棒ごとに色・向き・始まりをずらす）
 */
public class GalliaFortRenderer extends ChronoGazerRenderer<GalliaFortEntity> {

    /** 重ねる立方体の数、それぞれの半辺（障壁の半径に対する割合）・回す軸（0 = 縦 Y、1 = 横 X、2 = 前後 Z）・回る速さ（ラジアン / Tick）・はじめの傾き（度） */
    private static final int CUBES = 3;
    private static final float[] CUBE_HALF = {0.78F, 0.64F, 0.52F}, CUBE_SPEED = {0.012F, -0.018F, 0.026F}, CUBE_TILT = {0.0F, 45.0F, 35.0F};
    private static final int[] CUBE_AXIS = {0, 1, 2};
    /** 白い正立方体の枠の棒の太さ・角の継ぎ目の大きさ（ブロック）と、棒を波打たせる分け方 */
    private static final float BAR_W = 0.13F, NODE_W = 0.34F;
    private static final int BAR_SEG = 32;
    /** 枠の濃さ（半透明）、走る光の長さ（棒に対する割合）・速さ（棒の端から端まで / Tick）、三原色 */
    private static final float FRAME_ALPHA = 0.32F, PULSE_LEN = 0.13F, PULSE_SPEED = 0.025F;
    private static final int[][] PRIMARY = {{255, 50, 50}, {50, 255, 80}, {60, 110, 255}};

    public GalliaFortRenderer(EntityRendererProvider.Context context) {
        super(context, "galliafort", 2.0F);
    }

    /** 撃破では横に倒れず、脚が崩れて沈む（ChronoGazerModel のモーション） */
    @Override
    protected float getDeathMaxRotation(GalliaFortEntity fort) {
        return 0.0F;
    }

    /** 被弾の間だけ赤く光らせ、撃破の演出の間は赤くしない（2026-10-20 ユーザー指定「他の AEC 級のボスも撃破時赤くならないように」） */
    @Override
    public int getPackedOverlay(GalliaFortEntity fort, float u) {
        return net.minecraft.client.renderer.texture.OverlayTexture.pack(net.minecraft.client.renderer.texture.OverlayTexture.u(u),
                net.minecraft.client.renderer.texture.OverlayTexture.v(fort.hurtTime > 0 && fort.deathTime == 0));
    }

    @Override
    public boolean shouldRender(GalliaFortEntity fort, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        // 召喚・退場の門は上空にあるので、体が画面の外でも描く
        byte stage = fort.stage();
        return stage == GalliaFortEntity.STAGE_INTRO || stage == GalliaFortEntity.STAGE_RETREAT || super.shouldRender(fort, frustum, x, y, z);
    }

    @Override
    public void render(GalliaFortEntity fort, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        // 召喚・退場の演出（2026-10-10 ユーザー指定「豪華な召喚演出と退場演出を自作して」）: 歪みの門と、門から落ちてくる・門へ吸い上げられる体
        // 撃破の演出（2026-10-10 ユーザー指定「撃破演出も作って」）: 壊れていく → 核の暴走 → 大爆発で体が消える
        if (fort.isDefeatedSynced()) {
            float dt = fort.defeatTicks + partialTick;
            defeatFx(fort, buffers, pose, dt, partialTick);
            if (dt >= GalliaFortEntity.DEFEAT_TICKS - 1.0F) return;     // 閃光とともに体が消える
        }
        float st = fort.tickCount - fort.stageStart + partialTick;
        float lift = 0.0F, shrink = 1.0F;
        byte stage = fort.stage();
        if (stage == GalliaFortEntity.STAGE_INTRO) {
            float h = GalliaFortEntity.PORTAL_INTRO_H;
            portal(buffers, pose, h, open(st, 0.0F, GalliaFortEntity.INTRO_OPEN, GalliaFortEntity.INTRO_LAND, GalliaFortEntity.INTRO_LAND + 12.0F), st, true);
            if (st < GalliaFortEntity.INTRO_OPEN) {                       // 門の中から現れる
                lift = h;
                shrink = st / GalliaFortEntity.INTRO_OPEN;
            } else if (st < GalliaFortEntity.INTRO_LAND) {                // 速くなりながら落ちる
                float u = (st - GalliaFortEntity.INTRO_OPEN) / (GalliaFortEntity.INTRO_LAND - GalliaFortEntity.INTRO_OPEN);
                lift = h * (1.0F - u * u);
            }
        } else if (stage == GalliaFortEntity.STAGE_RETREAT) {
            float h = GalliaFortEntity.PORTAL_RETREAT_H;
            portal(buffers, pose, h, open(st, 0.0F, 12.0F, GalliaFortEntity.RETREAT_GONE, GalliaFortEntity.RETREAT_OUT_TICKS), st, false);
            wrap(fort, buffers, pose, st, h);
            if (st >= GalliaFortEntity.RETREAT_RISE) {                    // 速くなりながら門へ吸い上げられ、門の近くで縮んで消える
                float u = Mth.clamp((st - GalliaFortEntity.RETREAT_RISE) / (GalliaFortEntity.RETREAT_GONE - GalliaFortEntity.RETREAT_RISE), 0.0F, 1.0F);
                lift = h * u * u;
                shrink = u < 0.7F ? 1.0F : 1.0F - (u - 0.7F) / 0.3F;
            }
            if (st >= GalliaFortEntity.RETREAT_GONE) return;
        }
        if (shrink <= 0.001F) return;
        pose.pushPose();
        pose.translate(0.0F, lift, 0.0F);
        pose.pushPose();
        float half = fort.getBbHeight() * 0.5F;
        pose.translate(0.0F, half, 0.0F);                                  // 縮むのは体の真ん中を中心に
        pose.scale(shrink, shrink, shrink);
        pose.translate(0.0F, -half, 0.0F);
        pose.scale(GalliaFortEntity.SIZE, GalliaFortEntity.SIZE, GalliaFortEntity.SIZE);
        super.render(fort, entityYaw, partialTick, pose, buffers, light);
        pose.popPose();
        float shield = Mth.lerp(partialTick, fort.shieldO, fort.shield);
        if (shield > 0.01F && !fort.isInvisible()) barrier(fort, shield, partialTick, pose, buffers);
        // 穿孔破城砲の溜め（溜めの合図から撃つまで、約 2 秒）と、列車砲の全開射撃の溜め（脚を打ち込んだ後の 4 秒）: 砲口に集まる光
        float age = fort.tickCount + partialTick;
        float chargeT = age - fort.cannonChargeTick, ultT = age - fort.ultBraceTick;
        if (fort.cannonChargeTick > fort.cannonFireTick && chargeT >= 0.0F && chargeT < 45.0F) {
            chargeOrb(fort, Mth.clamp(chargeT / 40.0F, 0.0F, 1.0F), 1.0F, age, partialTick, pose, buffers);
        } else if (fort.ultBraceTick > fort.ultFireTick && ultT >= 20.0F && ultT < 105.0F) {
            chargeOrb(fort, Mth.clamp((ultT - 20.0F) / 80.0F, 0.0F, 1.0F), 1.8F, age, partialTick, pose, buffers);
        }
        pose.popPose();
    }

    /**
     * 撃破の演出（dt は撃破からの Tick）:
     * <ul>
     *   <li>障壁の枠が砕けて 12 のかけら（小さな立方体の枠）になって回りながら飛び散り、落ちる（0〜30 Tick）</li>
     *   <li>体の裂け目から漏れる光の筋（白い芯・赤いにじみ。時間とともに増えて太くなり、ちらつく）</li>
     *   <li>体を走る青白い電気（毎 Tick 形が変わる、ぎざぎざの線 3 本）</li>
     *   <li>核の暴走（{@link GalliaFortEntity#DEFEAT_CORE} Tick から）: 胸の白熱の核がふくらみ、三原色の輪と立方体の枠が違う軸で速く回る</li>
     * </ul>
     */
    private static void defeatFx(GalliaFortEntity fort, MultiBufferSource buffers, PoseStack pose, float dt, float partialTick) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        net.minecraft.util.RandomSource rnd = net.minecraft.util.RandomSource.create(fort.getUUID().getMostSignificantBits());
        float half = fort.getBbHeight() * 0.45F;
        Vec3 c = new Vec3(0, half, 0);
        // 障壁の枠のかけら
        if (dt < 30.0F) {
            float f = 1.0F - dt / 30.0F;
            float br = fort.barrierRadius(partialTick) * 0.7F, by = fort.barrierCenterY(partialTick);
            for (int k = 0; k < 12; k++) {
                Vec3 d = new Vec3(rnd.nextGaussian(), Math.abs(rnd.nextGaussian()) * 0.6 + 0.2, rnd.nextGaussian()).normalize();
                Vec3 p = new Vec3(0, by, 0).add(d.scale(br + dt * 0.3F)).add(0, -0.02 * dt * dt, 0);
                Matrix3f rot = new Matrix3f().rotateY(dt * 0.3F + k).rotateX(dt * 0.21F + k * 0.7F);
                GalliaSiegeBeamRenderer.cubeFrame(vc, m, p, rot, 0.55F, 0.05F, (int) (230 * f), dt + k * 3.0F);
            }
        }
        // 裂け目から漏れる光の筋
        float core = Mth.clamp((dt - GalliaFortEntity.DEFEAT_CORE) / (GalliaFortEntity.DEFEAT_TICKS - GalliaFortEntity.DEFEAT_CORE), 0.0F, 1.0F);
        int rays = Math.min(16, 2 + (int) (dt / 4.0F));
        for (int k = 0; k < 16; k++) {
            Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.7 + 0.3, rnd.nextGaussian()).normalize();
            float len = 3.0F + 4.0F * rnd.nextFloat();
            float seed = rnd.nextFloat() * 10.0F;
            if (k >= rays) continue;
            float flick = 0.6F + 0.4F * Mth.sin(dt * 1.7F + seed * 3.0F);
            float w = 0.08F + 0.12F * Math.min(1.0F, dt / 50.0F) + 0.15F * core;
            Vec3 from = c.add(d.scale(1.2)), to = c.add(d.scale(1.2 + len * flick * (1.0F + core)));
            GalliaSiegeBeamRenderer.line(vc, m, from, to, w * 2.2F, 255, 60, 60, (int) (90 * flick));
            GalliaSiegeBeamRenderer.line(vc, m, from, to, w, 255, 245, 235, (int) (220 * flick));
        }
        // 体を走る電気（毎 Tick 形が変わる）
        if (dt < GalliaFortEntity.DEFEAT_TICKS - 2.0F) {
            net.minecraft.util.RandomSource arc = net.minecraft.util.RandomSource.create((long) dt * 31L + fort.getId());
            for (int k = 0; k < 3; k++) {
                Vec3 a = c.add(arc.nextGaussian() * 1.6, arc.nextGaussian() * 1.6, arc.nextGaussian() * 1.6);
                Vec3 b = c.add(arc.nextGaussian() * 1.6, arc.nextGaussian() * 1.6, arc.nextGaussian() * 1.6);
                Vec3 prev = a;
                for (int i = 1; i <= 5; i++) {
                    Vec3 p = a.lerp(b, i / 5.0).add(i < 5 ? new Vec3(arc.nextGaussian() * 0.35, arc.nextGaussian() * 0.35, arc.nextGaussian() * 0.35) : Vec3.ZERO);
                    GalliaSiegeBeamRenderer.line(vc, m, prev, p, 0.05F, 160, 210, 255, 230);
                    prev = p;
                }
            }
        }
        // 核の暴走
        if (core > 0.0F) {
            float r = 0.4F + 4.6F * core * core;
            float age = fort.tickCount + partialTick;
            cube(vc, m, (float) c.x, (float) c.y, (float) c.z, r * 0.35F, 255);
            cube(vc, m, (float) c.x, (float) c.y, (float) c.z, r * 0.6F, 90);
            pose.pushPose();
            pose.translate(c.x, c.y, c.z);
            Matrix4f mc = pose.last().pose();
            for (int k = 0; k < 3; k++) {
                Matrix3f rot = new Matrix3f();
                float spin = age * (0.3F + 0.3F * core) * (k == 1 ? -1 : 1);
                switch (k) {
                    case 0 -> rot.rotateY(spin).rotateX(Mth.HALF_PI);
                    case 1 -> rot.rotateX(spin).rotateY(Mth.HALF_PI * 0.5F);
                    default -> rot.rotateZ(spin).rotateX(0.6F);
                }
                colorRing(vc, mc, rot, r * (1.0F - 0.1F * k), 0.12F, PRIMARY[k], 230);
                GalliaSiegeBeamRenderer.cubeFrame(vc, mc, Vec3.ZERO, rot, r * 0.55F, 0.07F, 220, age + k * 4.0F);
            }
            pose.popPose();
        }
    }

    /** 門の開き具合: a〜b で開き（少し大きく開いて戻る）、c〜d で閉じる */
    private static float open(float t, float a, float b, float c, float d) {
        if (t < a || t > d) return 0.0F;
        if (t < b) {
            float u = (t - a) / (b - a);
            return u * (1.0F + 0.25F * Mth.sin(u * Mth.PI));
        }
        if (t < c) return 1.0F;
        float u = (t - c) / (d - c);
        return 1.0F - u * u;
    }

    /**
     * 歪みの門（体の真上 h ブロック、半径 6 × 開き具合 o）: 少しずつ違う向きに傾いて回る赤・緑・青の 3 重の輪、渦を巻く白と紫の 8 本の腕、
     * 真ん中の光の板、縁をめぐって回る 4 つの立方体の枠（障壁と同じ形）、地面まで下りる光の柱（召喚は落ちてくる間、退場は吸い上げる間）
     */
    private static void portal(MultiBufferSource buffers, PoseStack pose, float h, float o, float t, boolean summon) {
        if (o <= 0.001F) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        float rad = 6.0F * o;
        Vec3 c = new Vec3(0, h, 0);
        for (int k = 0; k < 3; k++) {                                       // 3 重の輪（それぞれ違う向きに少し傾けて回す）
            float tilt = 0.18F + 0.08F * k, spin = t * (0.05F + 0.03F * k) * (k == 1 ? -1 : 1);
            Vec3 axis = new Vec3(Mth.sin(tilt) * Mth.cos(spin), Mth.cos(tilt), Mth.sin(tilt) * Mth.sin(spin));
            Vec3[] ab = GalliaSiegeBeamRenderer.basis(axis);
            int[] col = GalliaSiegeBeamRenderer.PRIMARY[k];
            float rr = rad * (1.0F - 0.1F * k);
            GalliaSiegeBeamRenderer.ring(vc, m, c, ab[0], ab[1], rr, rr + 0.35F, col[0], col[1], col[2], (int) (220 * Math.min(1.0F, o)));
        }
        Vec3[] flat = GalliaSiegeBeamRenderer.basis(new Vec3(0, 1, 0));
        GalliaSiegeBeamRenderer.ring(vc, m, c, flat[0], flat[1], 0.0F, rad * 0.85F, 170, 90, 255, (int) (70 * Math.min(1.0F, o)));   // 真ん中の光の板
        for (int k = 0; k < 8; k++) {                                       // 渦を巻く 8 本の腕
            Vec3 prev = c;
            for (int i = 1; i <= 10; i++) {
                float f = i / 10.0F;
                float ang = Mth.TWO_PI * k / 8.0F + t * (summon ? 0.12F : -0.15F) + f * 2.4F;
                Vec3 p = c.add(Mth.cos(ang) * rad * 0.9F * f, 0.0, Mth.sin(ang) * rad * 0.9F * f);
                GalliaSiegeBeamRenderer.line(vc, m, prev, p, 0.08F + 0.1F * f, k % 2 == 0 ? 255 : 200, k % 2 == 0 ? 250 : 110, 255, (int) (200 * Math.min(1.0F, o) * (1.0F - 0.5F * f)));
                prev = p;
            }
        }
        for (int k = 0; k < 4; k++) {                                       // 縁をめぐる立方体の枠
            float ang = Mth.TWO_PI * k / 4.0F + t * 0.07F;
            Vec3 p = c.add(Mth.cos(ang) * rad * 1.18F, Mth.sin(t * 0.2F + k) * 0.5F, Mth.sin(ang) * rad * 1.18F);
            Matrix3f rot = new Matrix3f().rotateY(t * 0.2F + k).rotateX(t * 0.13F + k);
            GalliaSiegeBeamRenderer.cubeFrame(vc, m, p, rot, 0.7F * o, 0.05F, (int) (220 * Math.min(1.0F, o)), t + k * 5.0F);
        }
        // 地面まで下りる光の柱
        Vec3 up = new Vec3(0, 1, 0);
        float col = 1.4F * o * (1.0F + 0.1F * Mth.sin(t * 1.7F));
        GalliaSiegeBeamRenderer.tube(vc, m, up, flat[0], flat[1], 0.0F, h, col * 1.8F, 150, 70, 255, (int) (50 * Math.min(1.0F, o)));
        GalliaSiegeBeamRenderer.tube(vc, m, up, flat[0], flat[1], 0.0F, h, col * 0.6F, 255, 245, 255, (int) (120 * Math.min(1.0F, o)));
    }

    /**
     * 退場で身を包む障壁の枠: 3 つの立方体の枠が違う軸で回りながら体のまわりへ縮み（8〜28 Tick）、吸い上げられる体について上がり、門の近くで消える
     */
    private static void wrap(GalliaFortEntity fort, MultiBufferSource buffers, PoseStack pose, float t, float h) {
        if (t < 8.0F || t >= GalliaFortEntity.RETREAT_GONE) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        float u = Mth.clamp((t - 8.0F) / (GalliaFortEntity.RETREAT_RISE - 8.0F), 0.0F, 1.0F);
        float rise = Mth.clamp((t - GalliaFortEntity.RETREAT_RISE) / (GalliaFortEntity.RETREAT_GONE - GalliaFortEntity.RETREAT_RISE), 0.0F, 1.0F);
        float half = fort.getBbHeight() * 0.5F;
        Vec3 c = new Vec3(0, half + h * rise * rise, 0);
        float size = Mth.lerp(u * u * (3.0F - 2.0F * u), 8.0F, half * 0.75F + 0.6F) * (rise < 0.7F ? 1.0F : 1.0F - (rise - 0.7F) / 0.3F);
        int al = (int) (230 * Math.min(1.0F, (t - 8.0F) / 4.0F));
        for (int k = 0; k < 3; k++) {
            Matrix3f rot = new Matrix3f();
            float spin = t * (0.08F + 0.05F * k) * (k == 1 ? -1 : 1);
            switch (k) {
                case 0 -> rot.rotateY(spin);
                case 1 -> rot.rotateX(spin).rotateY(0.785F);
                default -> rot.rotateZ(spin).rotateX(0.6F);
            }
            GalliaSiegeBeamRenderer.cubeFrame(vc, m, c, rot, size * (1.0F - 0.1F * k), 0.09F, al, t + k * 6.0F);
        }
    }

    /**
     * 砲口に集まる光（2026-10-10「穿孔破城砲も自作して豪華にして」）: 赤・緑・青の 3 つの輪が違う軸で回りながら砲口へ縮み、白い光の芯がふくらむ。
     * charge は溜まり具合（0〜1）、size は大きさの倍率（全開射撃は大きく）
     */
    private static void chargeOrb(GalliaFortEntity fort, float charge, float size, float age, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Vec3 at = fort.muzzle().subtract(fort.position());
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        int[][] colors = {{255, 60, 60}, {60, 255, 90}, {70, 120, 255}};
        float r = size * (1.9F - 1.2F * charge) * (1.0F + 0.06F * Mth.sin(age * 1.9F));
        int al = (int) (90 + 140 * charge);
        for (int k = 0; k < 3; k++) {
            Matrix3f rot = new Matrix3f();
            float spin = age * (0.18F + 0.25F * charge) * (k == 1 ? -1.0F : 1.0F);
            switch (k) {
                case 0 -> rot.rotateY(spin).rotateX(Mth.HALF_PI);
                case 1 -> rot.rotateX(spin).rotateY(Mth.HALF_PI * 0.5F);
                default -> rot.rotateZ(spin).rotateX(0.6F);
            }
            colorRing(vc, m, rot, r * (1.0F - 0.12F * k), 0.07F * size, colors[k], al);
        }
        float core = size * (0.12F + 0.45F * charge) * (1.0F + 0.15F * Mth.sin(age * 3.1F));
        cube(vc, m, 0, 0, 0, core, (int) (120 + 135 * charge));
        cube(vc, m, 0, 0, 0, core * 1.8F, (int) (40 + 60 * charge));
        pose.popPose();
    }

    /** rot で回した平らな色の輪（xz 平面、半径 r、太さ w） */
    private static void colorRing(VertexConsumer vc, Matrix4f m, Matrix3f rot, float r, float w, int[] c, int al) {
        int seg = 32;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float[] p0 = turn(rot, new float[]{Mth.cos(a0) * (r - w), 0, Mth.sin(a0) * (r - w)});
            float[] p1 = turn(rot, new float[]{Mth.cos(a1) * (r - w), 0, Mth.sin(a1) * (r - w)});
            float[] p2 = turn(rot, new float[]{Mth.cos(a1) * (r + w), 0, Mth.sin(a1) * (r + w)});
            float[] p3 = turn(rot, new float[]{Mth.cos(a0) * (r + w), 0, Mth.sin(a0) * (r + w)});
            strip(vc, m, p0, p1, p2, p3, c[0], c[1], c[2], al);
        }
    }

    private void barrier(GalliaFortEntity fort, float shield, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        float age = fort.tickCount + partialTick;
        float ratio = fort.barrierRatio();
        float hit = 1.0F - Mth.clamp((age - fort.barrierHitTick) / 8.0F, 0.0F, 1.0F);
        float grow = 1.0F - (1.0F - shield) * (1.0F - shield); // 張り直すとふくらむ
        float radius = fort.barrierRadius(partialTick) * (0.35F + 0.65F * grow) * (1.0F + 0.03F * hit); // 人型では大きく高く
        // 残りが少ないと揺らいで明滅する
        float weak = Mth.clamp(1.0F - ratio / 0.3F, 0.0F, 1.0F);
        float flicker = 1.0F - weak * 0.6F * (0.5F + 0.5F * Mth.sin(age * 1.7F) * Mth.sin(age * 0.53F));
        float cy = fort.barrierCenterY(partialTick);
        pose.pushPose();
        pose.translate(0.0F, cy, 0.0F);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        float yaw = -Mth.rotLerp(partialTick, fort.yBodyRotO, fort.yBodyRot) * Mth.DEG_TO_RAD;   // ボスの向きに合わせる
        float spin = age + 6.0F * hit;                                                          // 受けると回りが一瞬速まる
        for (int i = 0; i < CUBES; i++) {
            Matrix3f rot = new Matrix3f().rotateY(yaw);
            float ang = spin * CUBE_SPEED[i];
            float tilt = CUBE_TILT[i] * Mth.DEG_TO_RAD;
            switch (CUBE_AXIS[i]) {
                case 0 -> rot.rotateY(ang + tilt);                       // 縦の軸
                case 1 -> rot.rotateX(ang).rotateY(tilt);                // 横の軸（45 度ずらして菱形に見せる）
                default -> rot.rotateZ(ang).rotateX(tilt);               // 前後の軸
            }
            frame(vc, m, rot, radius * CUBE_HALF[i], -cy, (0.6F + 0.4F * hit) * shield * flicker * (1.0F - 0.15F * i), 0.02F + 0.06F * hit + 0.05F * weak, age, i * 12);
        }
        pose.popPose();
    }

    /**
     * 白い正立方体の枠: 半辺 half の 12 本の棒と 8 つの角の継ぎ目を、rot で回して描く。ground は地面の高さ（中心から。これより下は描かない）。
     * wobble は棒を波打たせる強さ（半辺に対する割合）、seed は棒ごとの波の始まりをずらす数
     */
    private static void frame(VertexConsumer vc, Matrix4f m, Matrix3f rot, float half, float ground, float alpha, float wobble, float age, int seed) {
        int lightAl = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255);              // 走る光の濃さ
        int al = (int) (lightAl * FRAME_ALPHA);                               // 枠は半透明
        if (lightAl <= 0) return;
        int e = seed;
        for (int axis = 0; axis < 3; axis++) {                           // 各軸の向きに 4 本ずつ
            for (int sa = -1; sa <= 1; sa += 2) {
                for (int sb = -1; sb <= 1; sb += 2) {
                    float[] a = new float[3], b = new float[3];
                    int u = (axis + 1) % 3, v = (axis + 2) % 3;
                    a[axis] = -half; b[axis] = half;
                    a[u] = b[u] = sa * half; a[v] = b[v] = sb * half;
                    bar(vc, m, turn(rot, a), turn(rot, b), half * wobble, e++, ground, al, lightAl, age);
                }
            }
        }
        for (int sx = -1; sx <= 1; sx += 2)                               // 角の継ぎ目（明るく）
            for (int sy = -1; sy <= 1; sy += 2)
                for (int sz = -1; sz <= 1; sz += 2) {
                    float[] c = turn(rot, new float[]{sx * half, sy * half, sz * half});
                    if (c[1] >= ground) cube(vc, m, c[0], c[1], c[2], NODE_W * 0.5F, Math.min(255, al + 40));
                }
    }

    private static float[] turn(Matrix3f rot, float[] v) {
        Vector3f r = rot.transform(new Vector3f(v[0], v[1], v[2]));
        return new float[]{r.x, r.y, r.z};
    }

    /**
     * a から b への半透明の白い棒（細い四角の柱を BAR_SEG に分け、真ん中ほど大きく波打たせる）。棒の上を三原色の光の粒が 2 つ走る（濃さ lightAl）。地面より下の分は描かない
     */
    private static void bar(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float amp, int index, float ground, int al, int lightAl, float age) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        float[] d = {dx / len, dy / len, dz / len};
        float[] p = Math.abs(d[1]) > 0.9F ? new float[]{1, 0, 0} : new float[]{0, 1, 0};     // 棒に直角な 2 つの向き
        float[] q = {d[1] * p[2] - d[2] * p[1], d[2] * p[0] - d[0] * p[2], d[0] * p[1] - d[1] * p[0]};
        p = new float[]{q[1] * d[2] - q[2] * d[1], q[2] * d[0] - q[0] * d[2], q[0] * d[1] - q[1] * d[0]};
        float w = BAR_W * 0.5F;
        float[][] prev = null;
        for (int i = 0; i <= BAR_SEG; i++) {
            float t = (float) i / BAR_SEG;
            float bend = amp * Mth.sin(Mth.PI * t) * Mth.sin(age * 0.23F + index * 1.7F + t * 7.0F);  // 空間の歪みで波打つ
            float bend2 = amp * 0.6F * Mth.sin(Mth.PI * t) * Mth.cos(age * 0.17F + index * 2.3F + t * 5.0F);
            float cx = a[0] + dx * t + p[0] * bend + q[0] * bend2, cyy = a[1] + dy * t + p[1] * bend + q[1] * bend2, cz = a[2] + dz * t + p[2] * bend + q[2] * bend2;
            float[][] ring = new float[4][];
            for (int k = 0; k < 4; k++) {
                float su = (k == 0 || k == 3) ? -w : w, sv = (k < 2) ? -w : w;
                ring[k] = new float[]{cx + p[0] * su + q[0] * sv, cyy + p[1] * su + q[1] * sv, cz + p[2] * su + q[2] * sv};
            }
            if (prev != null && Math.max(cyy, prev[0][1]) >= ground) {
                // この区切りの真ん中に、走る光の粒がどれだけ重なるか（いちばん強い粒の色にする）
                float tm = (i - 0.5F) / BAR_SEG, best = 0.0F;
                int[] col = null;
                for (int k = 0; k < 2; k++) {
                    float tp = (age * PULSE_SPEED + index * 0.37F + k * 0.5F) % 1.0F;
                    if ((index & 1) == 1) tp = 1.0F - tp;                        // 棒ごとに走る向きを変える
                    float pw = 1.0F - Math.abs(tm - tp) / PULSE_LEN;
                    if (pw > best) {
                        best = pw;
                        col = PRIMARY[(index + k) % 3];
                    }
                }
                int r = 235, g = 242, bl = 255, alpha = al;
                if (col != null) {
                    float f = best * best * (3.0F - 2.0F * best);
                    r = (int) Mth.lerp(f, r, col[0]);
                    g = (int) Mth.lerp(f, g, col[1]);
                    bl = (int) Mth.lerp(f, bl, col[2]);
                    alpha = (int) Mth.lerp(f, al, lightAl);
                }
                for (int k = 0; k < 4; k++) {
                    int k2 = (k + 1) % 4;
                    strip(vc, m, lift(prev[k], ground), lift(prev[k2], ground), lift(ring[k2], ground), lift(ring[k], ground), r, g, bl, alpha);
                }
            }
            prev = ring;
        }
    }

    /** 地面より下の点を地面まで上げる（棒の地面より下の分を潰す） */
    private static float[] lift(float[] v, float ground) {
        return v[1] >= ground ? v : new float[]{v[0], ground, v[2]};
    }

    /** 中心 (x, y, z)・半辺 h の白く光る小さな立方体 */
    private static void cube(VertexConsumer vc, Matrix4f m, float x, float y, float z, float h, int al) {
        float[][] c = new float[8][];
        for (int i = 0; i < 8; i++) c[i] = new float[]{x + ((i & 1) == 0 ? -h : h), y + ((i & 2) == 0 ? -h : h), z + ((i & 4) == 0 ? -h : h)};
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        for (int[] f : faces) strip(vc, m, c[f[0]], c[f[1]], c[f[2]], c[f[3]], 235, 242, 255, al);
    }

    /** 色付きの四角の面（両面） */
    private static void strip(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d, int r, int g, int bl, int al) {
        float[][] v = {a, b, c, d};
        for (int[] order : new int[][]{{0, 1, 2, 3}, {3, 2, 1, 0}}) {
            for (int i : order) vc.vertex(m, v[i][0], v[i][1], v[i][2]).color(r, g, bl, al).endVertex();
        }
    }
}
