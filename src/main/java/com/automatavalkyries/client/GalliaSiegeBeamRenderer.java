package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.GalliaSiegeBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 穿孔破城砲・歪曲破界砲の光の束の描き方（2026-10-10 ユーザー指定「穿孔破城砲も自作して豪華にして」「（歪曲破界砲の）演出を自作して豪華にして」。
 * 光を足し合わせる描き方で、テクスチャは使わない）:
 * <ul>
 *   <li>伸びる: 撃った直後に砲口から先へ伸び切る。先には白と赤の縞のらせんの溝が回る穿孔の弾頭（円錐）</li>
 *   <li>柱: 白い芯・赤い層・紫のにじみの 3 層の 12 角柱。太さは脈打ちながら、伸び切った後は細って消える</li>
 *   <li>らせん: 赤・緑・青の 3 本の帯が柱のまわりを回りながら巻き付く（障壁と同じ三原色）</li>
 *   <li>衝撃の輪: 4 ブロックごとの輪が、撃った所から順に広がって薄れる。砲口には星形の閃光</li>
 *   <li>歪曲破界砲だけ: 柱は紫が濃く太く長く残り、外側に逆へ回るもう 1 組のらせん、光の束に沿って並んで違う軸で回る白い立方体の枠（障壁と同じ形。
 *       辺を三原色の光が走る）、砲口から先へ流れていく輪、砲口には障壁を注ぎ込む立方体の枠</li>
 * </ul>
 * 補助（tube・ring・line・cubeFrame・quad）は着弾地点の目印のレンダラー（{@link GalliaRiftRenderer}）も使う
 */
public class GalliaSiegeBeamRenderer extends EntityRenderer<GalliaSiegeBeamEntity> {

    static final int SIDES = 12;
    static final int[][] PRIMARY = {{255, 60, 60}, {60, 255, 90}, {70, 120, 255}};

    public GalliaSiegeBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(GalliaSiegeBeamEntity beam, Frustum frustum, double x, double y, double z) {
        return true; // 長い光の束は付け根が画面の外でも見える
    }

    @Override
    public void render(GalliaSiegeBeamEntity beam, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = beam.visualAge(partialTick);
        float life = beam.life(), extTicks = beam.extendTicks();
        if (t > life) return;
        boolean rift = beam.rift();
        Vec3 dir = beam.dir();
        float ext = Mth.clamp(t / extTicks, 0.0F, 1.0F);
        float len = beam.length() * (1.0F - (1.0F - ext) * (1.0F - ext));
        float fade = t < extTicks ? 1.0F : 1.0F - smooth((t - extTicks) / (life - extTicks));
        float w = beam.width() * (0.35F + 0.65F * fade) * (1.0F + 0.12F * Mth.sin(t * 2.3F)) * (t < 2.0F ? 1.35F : 1.0F);
        Vec3[] ab = basis(dir);
        Vec3 a = ab[0], b = ab[1];
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        // 3 層の柱（歪曲破界砲は紫が濃く太い）
        if (rift) {
            tube(vc, m, dir, a, b, 0.0F, len, w * 2.3F, 120, 40, 255, (int) (60 * fade));
            tube(vc, m, dir, a, b, 0.0F, len, w * 1.5F, 190, 70, 255, (int) (110 * fade));
        } else {
            tube(vc, m, dir, a, b, 0.0F, len, w * 1.7F, 140, 60, 255, (int) (70 * fade));
        }
        tube(vc, m, dir, a, b, 0.0F, len, w * 1.0F, 255, 50, 60, (int) (150 * fade));
        tube(vc, m, dir, a, b, 0.0F, len, w * 0.42F, 255, 250, 245, (int) (235 * fade));
        // 三原色のらせん（歪曲破界砲は外側に逆へ回るもう 1 組）
        for (int k = 0; k < 3; k++) {
            int[] c = PRIMARY[k];
            float phase = k * Mth.TWO_PI / 3.0F + t * 0.55F;
            helix(vc, m, dir, a, b, len, w * 1.35F, phase, 0.6F, 0.17F * (0.6F + 0.4F * fade), c[0], c[1], c[2], (int) (220 * fade));
            if (rift) helix(vc, m, dir, a, b, len, w * 2.6F, -phase * 0.8F, -0.35F, 0.22F * (0.6F + 0.4F * fade), c[0], c[1], c[2], (int) (170 * fade));
        }
        // 衝撃の輪（撃った所から順に広がる）
        for (float d = 2.0F; d < len; d += 4.0F) {
            float age = t - d / beam.length() * extTicks;
            if (age < 0.0F) continue;
            float g = Mth.clamp(age / 10.0F, 0.0F, 1.0F);
            float rr = w * (1.2F + (rift ? 3.4F : 2.2F) * g);
            ring(vc, m, dir.scale(d), a, b, rr, rr + 0.12F, 255, 230, 220, (int) (200 * (1.0F - g) * fade));
        }
        if (rift) {
            // 砲口から先へ流れていく輪（三原色を順に）
            for (int k = 0; k < 6; k++) {
                float d = (t * 2.4F + k * len / 6.0F) % Math.max(len, 1.0F);
                int[] c = PRIMARY[k % 3];
                float rr = w * 2.0F;
                ring(vc, m, dir.scale(d), a, b, rr, rr + 0.2F, c[0], c[1], c[2], (int) (190 * fade));
            }
            // 光の束に沿って並ぶ、違う軸で回る白い立方体の枠
            int n = 0;
            for (float d = 6.0F; d < len - 2.0F; d += 9.0F, n++) {
                Matrix3f rot = new Matrix3f().rotate(t * 0.09F * (n % 2 == 0 ? 1 : -1), (float) dir.x, (float) dir.y, (float) dir.z)
                        .rotateX(0.6F + n).rotateY(t * 0.05F + n * 0.7F);
                cubeFrame(vc, m, dir.scale(d), rot, w * 1.9F * (0.8F + 0.2F * fade), 0.06F, (int) (200 * fade), t + n * 3.0F);
            }
            // 砲口: 障壁を注ぎ込む立方体の枠（縮みながら回る）
            if (t < 16.0F) {
                float f = 1.0F - t / 16.0F;
                for (int k = 0; k < 3; k++) {
                    Matrix3f rot = new Matrix3f().rotateY(t * 0.3F * (k + 1)).rotateX(0.7F * k).rotateZ(t * 0.2F);
                    cubeFrame(vc, m, Vec3.ZERO, rot, w * (3.5F - k * 0.6F) * (0.4F + 0.6F * f), 0.08F, (int) (230 * f), t + k * 5.0F);
                }
            }
        }
        // 穿孔の弾頭（伸びている間だけ。らせんの溝が回る円錐）
        if (t < extTicks + 2.0F) {
            float drill = 1.0F - Mth.clamp((t - extTicks) / 2.0F, 0.0F, 1.0F);
            drill(vc, m, dir, a, b, len, w * (rift ? 2.0F : 1.5F), rift ? 3.6F : 2.4F, t * 1.4F, (int) (255 * drill));
        }
        // 砲口の星形の閃光
        float flash = rift ? 10.0F : 7.0F;
        if (t < flash) {
            float f = 1.0F - t / flash;
            star(vc, m, a, b, w * (rift ? 6.0F : 4.0F) * (0.6F + 0.4F * f), (int) (255 * f));
        }
        super.render(beam, yaw, partialTick, pose, buffers, light);
    }

    /** dir に直交する 2 つの向き */
    static Vec3[] basis(Vec3 dir) {
        Vec3 u = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = dir.cross(u).normalize();
        return new Vec3[]{a, dir.cross(a).normalize()};
    }

    static Vec3 around(Vec3 a, Vec3 b, float ang, float r) {
        return a.scale(Mth.cos(ang) * r).add(b.scale(Mth.sin(ang) * r));
    }

    /** dir に沿った s0〜s1 の、半径 r の 12 角柱の側面 */
    static void tube(VertexConsumer vc, Matrix4f m, Vec3 dir, Vec3 a, Vec3 b, float s0, float s1, float r, int cr, int cg, int cb, int al) {
        if (al <= 0) return;
        for (int i = 0; i < SIDES; i++) {
            float a0 = Mth.TWO_PI * i / SIDES, a1 = Mth.TWO_PI * (i + 1) / SIDES;
            Vec3 p0 = around(a, b, a0, r), p1 = around(a, b, a1, r);
            quad(vc, m, p0.add(dir.scale(s0)), p1.add(dir.scale(s0)), p1.add(dir.scale(s1)), p0.add(dir.scale(s1)), cr, cg, cb, al);
        }
    }

    /** 柱のまわりを回るらせんの帯（半径 r、1 ブロックで twist ラジアン回る、幅 bw） */
    static void helix(VertexConsumer vc, Matrix4f m, Vec3 dir, Vec3 a, Vec3 b, float len, float r, float phase, float twist, float bw, int cr, int cg, int cb, int al) {
        if (al <= 0) return;
        float step = 0.5F;
        Vec3 prevIn = null, prevOut = null;
        for (float s = 0.0F; s <= len; s += step) {
            float ang = phase + s * twist;
            Vec3 c = dir.scale(s).add(around(a, b, ang, r));
            Vec3 off = dir.scale(bw);
            Vec3 in = c.subtract(off), out = c.add(off);
            if (prevIn != null) quad(vc, m, prevIn, prevOut, out, in, cr, cg, cb, al);
            prevIn = in;
            prevOut = out;
        }
    }

    /** dir に直交する輪（中心 c、内の半径 r0・外の半径 r1） */
    static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 a, Vec3 b, float r0, float r1, int cr, int cg, int cb, int al) {
        if (al <= 0) return;
        int seg = 28;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            quad(vc, m, c.add(around(a, b, a0, r0)), c.add(around(a, b, a1, r0)), c.add(around(a, b, a1, r1)), c.add(around(a, b, a0, r1)), cr, cg, cb, al);
        }
    }

    /** p0 から p1 への光の線（太さ w。どの向きから見ても見えるよう、直交する 2 枚の帯を重ねる） */
    static void line(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, float w, int cr, int cg, int cb, int al) {
        if (al <= 0) return;
        Vec3 d = p1.subtract(p0);
        if (d.lengthSqr() < 1.0e-8) return;
        Vec3[] ab = basis(d.normalize());
        for (Vec3 e : ab) {
            Vec3 o = e.scale(w);
            quad(vc, m, p0.add(o), p1.add(o), p1.subtract(o), p0.subtract(o), cr, cg, cb, al);
        }
    }

    /**
     * 中心 c・半辺 h の立方体の枠を rot で回して描く（障壁と同じ形。12 本の白い辺と、辺の上を走る三原色の光、角の明るい継ぎ目）。t は光の進み
     */
    static void cubeFrame(VertexConsumer vc, Matrix4f m, Vec3 c, Matrix3f rot, float h, float w, int al, float t) {
        if (al <= 0) return;
        int e = 0;
        for (int axis = 0; axis < 3; axis++) {
            for (int sa = -1; sa <= 1; sa += 2) {
                for (int sb = -1; sb <= 1; sb += 2) {
                    float[] p = new float[3], q = new float[3];
                    int u = (axis + 1) % 3, v = (axis + 2) % 3;
                    p[axis] = -h; q[axis] = h;
                    p[u] = q[u] = sa * h; p[v] = q[v] = sb * h;
                    Vec3 p0 = c.add(turn(rot, p)), p1 = c.add(turn(rot, q));
                    line(vc, m, p0, p1, w, 235, 242, 255, al / 3);
                    // 辺を走る光の粒（色は辺ごとに）
                    float tp = (t * 0.06F + e * 0.37F) % 1.0F;
                    if ((e & 1) == 1) tp = 1.0F - tp;
                    Vec3 mid = p0.lerp(p1, tp), span = p1.subtract(p0).scale(0.09F);
                    int[] col = PRIMARY[e % 3];
                    line(vc, m, mid.subtract(span), mid.add(span), w * 1.6F, col[0], col[1], col[2], al);
                    e++;
                }
            }
        }
    }

    static Vec3 turn(Matrix3f rot, float[] v) {
        Vector3f r = rot.transform(new Vector3f(v[0], v[1], v[2]));
        return new Vec3(r.x, r.y, r.z);
    }

    /** 光の束の先の穿孔の弾頭: 底の半径 r・長さ h の円錐を 12 に分け、白と赤の縞をらせんに回す */
    private static void drill(VertexConsumer vc, Matrix4f m, Vec3 dir, Vec3 a, Vec3 b, float len, float r, float h, float spin, int al) {
        if (al <= 0) return;
        Vec3 base = dir.scale(Math.max(0.0F, len - h)), tip = dir.scale(len + 0.4F);
        int rings = 4;
        for (int j = 0; j < rings; j++) {
            float f0 = (float) j / rings, f1 = (float) (j + 1) / rings;
            Vec3 c0 = base.lerp(tip, f0), c1 = base.lerp(tip, f1);
            float r0 = r * (1.0F - f0), r1 = r * (1.0F - f1);
            for (int i = 0; i < SIDES; i++) {
                float tw = spin + j * 0.9F;                                            // 段ごとにずらしてらせんの溝に
                float a0 = Mth.TWO_PI * i / SIDES + tw, a1 = Mth.TWO_PI * (i + 1) / SIDES + tw;
                boolean white = ((i + j) & 1) == 0;
                quad(vc, m, c0.add(around(a, b, a0, r0)), c0.add(around(a, b, a1, r0)), c1.add(around(a, b, a1 + 0.9F, r1)), c1.add(around(a, b, a0 + 0.9F, r1)),
                        255, white ? 245 : 60, white ? 240 : 70, al);
            }
        }
    }

    /** 砲口の星形の閃光（光の束に直交する面に 4 本の光の筋） */
    private static void star(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float size, int al) {
        if (al <= 0) return;
        for (int k = 0; k < 4; k++) {
            float ang = k * Mth.PI / 4.0F;
            Vec3 along = around(a, b, ang, size), across = around(a, b, ang + Mth.HALF_PI, size * 0.08F);
            quad(vc, m, along.add(across), along.subtract(across), along.scale(-1).subtract(across), along.scale(-1).add(across), 255, 240, 230, al);
        }
        quad(vc, m, around(a, b, 0, size * 0.3F), around(a, b, Mth.HALF_PI, size * 0.3F), around(a, b, Mth.PI, size * 0.3F), around(a, b, -Mth.HALF_PI, size * 0.3F), 255, 255, 255, al);
    }

    /** 両面の四角 */
    static void quad(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int r, int g, int b, int al) {
        int alpha = Mth.clamp(al, 0, 255);
        Vec3[] v = {p0, p1, p2, p3};
        for (int[] order : new int[][]{{0, 1, 2, 3}, {3, 2, 1, 0}}) {
            for (int i : order) vc.vertex(m, (float) v[i].x, (float) v[i].y, (float) v[i].z).color(r, g, b, alpha).endVertex();
        }
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    @Override
    public ResourceLocation getTextureLocation(GalliaSiegeBeamEntity beam) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
