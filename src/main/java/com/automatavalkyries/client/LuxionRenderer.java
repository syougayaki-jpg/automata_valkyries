package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.LuxionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * ルクシオンのレンダラー: 背の推進器から出る光を、カーテンのように垂らし、マントのように大きく広げて描く
 * （2026-10-08 ユーザー指定「スラスターから出る光の演出をカーテン状におろし、さらに大きくマントのように広げて」）。
 * 上は翼の付け根から翼の先へ弧に並び、下は地面近くまで垂れて左右へ大きく開き、後ろへ反る。波打ちながら揺れ、
 * 動くと後ろへなびく。上が明るく下へ消える半透明の光（外の広い層と内の明るい層の 2 重）。第 2 形態は明るく、縁が赤みを帯びる
 */
public class LuxionRenderer extends ChronoGazerRenderer<LuxionEntity> {

    /** 横の帯の数・縦の段の数 */
    private static final int COLS = 16, ROWS = 6;

    public LuxionRenderer(EntityRendererProvider.Context context) {
        super(context, "luxion", 0.6F);
    }

    @Override
    public void render(LuxionEntity lux, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(lux, entityYaw, partialTick, pose, buffers, light);
        if (lux.isInvisible() || lux.deathTime > 0) return;
        float age = lux.tickCount + partialTick;
        float yaw = Mth.rotLerp(partialTick, lux.yBodyRotO, lux.yBodyRot);
        Vec3 v = lux.getDeltaMovement();
        float speed = (float) Math.min(1.0, Math.sqrt(v.x * v.x + v.z * v.z) * 3.0);
        boolean p2 = lux.isPhase2();
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - yaw)); // モデルと同じ向き（正面 = -Z、背中 = +Z）
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        // 外の広い層（淡い）と内の層（明るい）
        curtain(vc, m, age, speed, p2, 1.0F, 0.0F, p2 ? 120 : 90);
        curtain(vc, m, age, speed, p2, 0.7F, 0.06F, p2 ? 170 : 130);
        float dep = Mth.lerp(partialTick, lux.deployO, lux.deploy);
        if (dep > 0.01F) boosterLight(vc, m, age, dep, speed, p2);
        pose.popPose();
    }

    /** 3 つの噴射口の、たたんだときの出口（geo の px）と、ポッドが開く向き（ブースターの中の向き） */
    private static final float[][] NOZZLES = {{0.0F, 13.64F, 9.09F, 0.0F, 1.0F}, {1.633F, 13.64F, 6.26F, 0.866F, -0.5F}, {-1.633F, 13.64F, 6.26F, -0.866F, -0.5F}};
    /** ブースターの付け根・ポッドの根本（geo の px） */
    private static final Vec3 BOOSTER_PIVOT = new Vec3(0, 28, 3.5), POD_APEX = new Vec3(0, 29.9, 7.2);

    /**
     * ブースターから出る光（2026-10-08 ユーザー指定「スラスター消してブースターから光が出るように」）: 戦闘で開いたブースターの 3 つの噴射口から、
     * 白い芯と金のにじみの光の噴射が後ろへ伸びる。モデルと同じ計算（ChronoGazerModel: 後ろへ 85 度倒れ、ポッドが根本を中心に 35.3 度開く）で口の位置と向きを出す。
     * 動くと長く、第 2 形態は赤みを帯びる
     */
    private static void boosterLight(VertexConsumer vc, Matrix4f m, float age, float dep, float speed, boolean p2) {
        float open = smoothstep(dep);
        if (open < 0.2F) return;
        float split = smoothstep((open - 0.35F) / 0.65F) * 0.6155F;
        float pitch = 1.484F * open;
        float k = smoothstep((open - 0.2F) / 0.8F);
        for (int i = 0; i < NOZZLES.length; i++) {
            float[] n = NOZZLES[i];
            Vec3 radial = new Vec3(n[3], 0, n[4]);
            // モデルのポッドは radial と逆の向きへ傾いて描かれる（ChronoGazerModel#pod）。光は描かれているブースターに合わせる（2026-10-08「ブースターからの光が上下逆」）
            Vec3 swing = radial.scale(-1);
            Vec3 exit = pitchBack(splay(new Vec3(n[0], n[1], n[2]).subtract(POD_APEX), swing, split).add(POD_APEX).subtract(BOOSTER_PIVOT), pitch).add(BOOSTER_PIVOT);
            Vec3 dir = pitchBack(splay(radial.scale(0.17).add(0, -0.985, 0), swing, split), pitch).normalize();
            // geo の px → ブロック（GeckoLib は geo の X を反転して描く）
            Vec3 at = new Vec3(-exit.x / 16.0, exit.y / 16.0, exit.z / 16.0);
            Vec3 d = new Vec3(-dir.x, dir.y, dir.z);
            float len = (1.1F + 0.25F * Mth.sin(age * 1.1F + i * 2.1F) + speed * 1.0F) * k;
            int a = (int) (220 * k);
            int g = p2 ? 200 : 240, b = p2 ? 160 : 210;
            jet(vc, m, at, d, len, 0.16F, 255, g, b, a);              // 白い芯
            jet(vc, m, at, d, len * 1.35F, 0.32F, 255, p2 ? 120 : 190, p2 ? 80 : 110, a / 2); // 金のにじみ
        }
    }

    /** v（根本から見た位置）を、根本を中心に「下」から radial の向きへ angle だけ傾ける（ポッドが開く） */
    private static Vec3 splay(Vec3 v, Vec3 radial, float angle) {
        double a = v.dot(radial), b = -v.y; // radial の成分と、下向きの成分
        double c = Math.cos(angle), sn = Math.sin(angle);
        double a2 = a * c + b * sn, b2 = b * c - a * sn;
        return v.add(radial.scale(a2 - a)).add(0, -(b2 - b), 0);
    }

    /** v（付け根から見た位置）を、下が後ろ（+Z）へ向くよう angle だけ倒す */
    private static Vec3 pitchBack(Vec3 v, float angle) {
        double c = Math.cos(angle), sn = Math.sin(angle);
        return new Vec3(v.x, v.y * c + v.z * sn, v.z * c - v.y * sn);
    }

    private static float smoothstep(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** at から dir へ長さ len の光の噴射（3 枚の板を軸のまわりに 60 度ずつ回して重ね、先へ細く消える） */
    private static void jet(VertexConsumer vc, Matrix4f m, Vec3 at, Vec3 dir, float len, float width, int r, int g, int b, int alpha) {
        Vec3 up = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(up).normalize(), w = dir.cross(u).normalize();
        Vec3 mid = at.add(dir.scale(len * 0.35)), end = at.add(dir.scale(len));
        for (int k = 0; k < 3; k++) {
            double ang = Math.PI * k / 3.0;
            Vec3 side = u.scale(Math.cos(ang)).add(w.scale(Math.sin(ang))).scale(width * 0.5);
            quad(vc, m, at.add(side), at.subtract(side), mid.subtract(side.scale(1.2)), mid.add(side.scale(1.2)), r, g, b, alpha, alpha, (int) (alpha * 0.7F), (int) (alpha * 0.7F));
            quad(vc, m, mid.add(side.scale(1.2)), mid.subtract(side.scale(1.2)), end, end, r, g, b, (int) (alpha * 0.7F), (int) (alpha * 0.7F), 0, 0);
        }
    }

    /** 1 枚のカーテン（scale = 広さ、back = 後ろへのずらし、alpha = 上端の明るさ） */
    private static void curtain(VertexConsumer vc, Matrix4f m, float age, float speed, boolean p2, float scale, float back, int alpha) {
        Vec3[][] grid = new Vec3[COLS + 1][ROWS + 1];
        for (int i = 0; i <= COLS; i++) {
            float u = i / (float) COLS * 2.0F - 1.0F;            // -1（左）〜 1（右）
            float au = Math.abs(u);
            // 上端: 翼の付け根（背の中央、高さ 1.85）から翼の先（左右 1.4、高さ 2.35）への弧
            Vec3 top = new Vec3(u * 1.4F * scale, 1.85F + au * au * 0.5F, 0.22F + back + au * 0.1F);
            for (int j = 0; j <= ROWS; j++) {
                float t = j / (float) ROWS;                      // 0（上）〜 1（下）
                float wave = Mth.sin(age * 0.12F + u * 3.0F + t * 2.5F) * 0.12F * t;
                float sway = Mth.sin(age * 0.07F + u * 1.7F) * 0.08F * t;
                // 下へ行くほど左右へ大きく開き（マント）、後ろへ反り、動くと後ろへなびく
                float x = u * (1.4F + 1.3F * t * t) * scale + sway;
                float y = Mth.lerp(t, (float) top.y, 0.05F + au * 0.15F) + speed * 0.5F * t * t;
                float z = (float) top.z + (0.55F + 0.35F * au) * t * t * scale + wave + speed * 1.4F * t * t;
                grid[i][j] = new Vec3(x, y, z);
            }
        }
        for (int i = 0; i < COLS; i++) {
            float edge = Math.abs((i + 0.5F) / COLS * 2.0F - 1.0F); // 0（中央）〜 1（端）
            int r = 255, g = p2 ? (int) (236 - 120 * edge) : 236, b = p2 ? (int) (180 - 110 * edge) : 180;
            for (int j = 0; j < ROWS; j++) {
                float t0 = j / (float) ROWS, t1 = (j + 1) / (float) ROWS;
                // 上が明るく、下へ消える。帯ごとに少しちらつく
                float flicker = 0.85F + 0.15F * Mth.sin(i * 1.7F + j);
                int a0 = (int) (alpha * (1.0F - t0) * (1.0F - t0) * flicker), a1 = (int) (alpha * (1.0F - t1) * (1.0F - t1) * flicker);
                quad(vc, m, grid[i][j], grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1], r, g, b, a0, a0, a1, a1);
            }
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int r, int g, int bl, int aa, int ab, int ac, int ad) {
        vertex(vc, m, a, r, g, bl, aa);
        vertex(vc, m, b, r, g, bl, ab);
        vertex(vc, m, c, r, g, bl, ac);
        vertex(vc, m, d, r, g, bl, ad);
        vertex(vc, m, d, r, g, bl, ad); // 裏から見ても
        vertex(vc, m, c, r, g, bl, ac);
        vertex(vc, m, b, r, g, bl, ab);
        vertex(vc, m, a, r, g, bl, aa);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, int r, int g, int b, int a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, Math.max(0, Math.min(255, a))).endVertex();
    }
}
