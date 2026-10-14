package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.MementoMoriEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * メメントモリの光の蝶の群れ（2026-10-14 ユーザー指定の演出「登場は蝶の群れが集まって形になる」「撃破は光の蝶になって散る」）。見た目だけ（時間から計算）。
 * <ul>
 *   <li>登場: 光の蝶が上空の遠くから渦を巻いて降り、体と翅の 1 点ずつへ集まって溶ける（{@link MementoShow} の 0〜50 Tick）</li>
 *   <li>撃破・退場: 体と翅の 1 点ずつから光の蝶が生まれ、羽ばたきながら外へ・空へ舞い上がって消える</li>
 * </ul>
 * 色は紫・桃色・水色（1 匹ずつ決まる）。描き方は {@link MementoFx#butterfly}（光の縁と淡い光の面）
 */
final class MementoSwarm {
    private static final int COUNT = 72;
    private static final int[] COLORS = {0xA45CFF, 0xFF8CEB, 0x8FE6FF, 0xE8D0FF};

    private MementoSwarm() {
    }

    private static float hash(int i, int salt) {
        int h = i * 73856093 ^ salt * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535.0F;
    }

    /** 蝶 i の体の上の点（足元からの位置。体の向き yaw で回す）: 3 割は体、7 割は翅の面 */
    private static Vec3 bodyPoint(int i, float yaw) {
        double x, y, z;
        if (hash(i, 1) < 0.3F) {
            x = (hash(i, 2) - 0.5) * 1.2;
            y = 0.4 + hash(i, 3) * 5.0;
            z = (hash(i, 4) - 0.5) * 0.8;
        } else {
            double side = hash(i, 2) < 0.5 ? -1.0 : 1.0;
            x = side * (0.6 + hash(i, 3) * 4.2);
            y = 2.0 + hash(i, 4) * 4.4 - (Math.abs(x) > 2.5 ? hash(i, 5) * 2.0 : 0.0);
            z = -0.9;                                                           // 背の後ろ（下の回し方で -z が後ろ）
        }
        float r = -yaw * Mth.DEG_TO_RAD;
        double c = Math.cos(r), s = Math.sin(r);
        return new Vec3(x * c + z * s, y, -x * s + z * c);
    }

    static void render(MementoMoriEntity mori, float pt, PoseStack pose, MultiBufferSource buffers) {
        float in = MementoShow.stageT(mori, MementoMoriEntity.STAGE_INTRO, pt);
        float re = MementoShow.stageT(mori, MementoMoriEntity.STAGE_RETREAT, pt);
        float d = MementoShow.defeatT(mori, pt);
        if (in < 0.0F && re < 0.0F && d < 0.0F) return;
        float yaw = Mth.lerp(pt, mori.yBodyRotO, mori.yBodyRot);
        float age = mori.tickCount + pt;
        Matrix4f m = pose.last().pose();
        Vec3 up = new Vec3(0, 1, 0);
        for (int i = 0; i < COUNT; i++) {
            Vec3 target = bodyPoint(i, yaw);
            int color = COLORS[(int) (hash(i, 6) * COLORS.length) % COLORS.length];
            float size = 0.28F + hash(i, 7) * 0.22F;
            float flap = Mth.sin(age * (0.9F + hash(i, 8) * 0.5F) + i) * 0.9F;
            Vec3 pos, vel;
            float alpha;
            if (in >= 0.0F) { // 集まる: 上空の遠くから渦を巻いて降りる
                float delay = hash(i, 9) * 14.0F;
                float u = Mth.clamp((in - delay) / 34.0F, 0.0F, 1.0F);
                if (u >= 1.0F && in - delay - 34.0F > 6.0F) continue;
                float e = 1.0F - (1.0F - u) * (1.0F - u);
                float ang = hash(i, 10) * Mth.TWO_PI + (1.0F - e) * 5.0F;
                float rad = (10.0F + hash(i, 11) * 6.0F) * (1.0F - e);
                pos = target.add(Math.cos(ang) * rad, (8.0F + hash(i, 12) * 6.0F) * (1.0F - e), Math.sin(ang) * rad);
                vel = new Vec3(-Math.sin(ang), -0.4, Math.cos(ang));
                alpha = Mth.clamp((in - delay) / 5.0F, 0.0F, 1.0F) * (u >= 1.0F ? 1.0F - (in - delay - 34.0F) / 6.0F : 1.0F);
            } else { // 散る: 体の 1 点から生まれ、外へ・空へ舞い上がる
                float born = re >= 0.0F ? 10.0F + hash(i, 9) * 30.0F : 45.0F + hash(i, 9) * 35.0F;
                float t = (re >= 0.0F ? re : d) - born;
                if (t < 0.0F || t > 40.0F) continue;
                Vec3 out = new Vec3(target.x, 0, target.z);
                out = out.lengthSqr() < 1.0e-4 ? new Vec3(hash(i, 13) - 0.5, 0, hash(i, 14) - 0.5).normalize() : out.normalize();
                float sway = Mth.sin(t * 0.25F + i) * 0.6F;
                pos = target.add(out.scale(t * 0.09F)).add(0, t * (0.06F + hash(i, 15) * 0.05F), 0).add(new Vec3(-out.z, 0, out.x).scale(sway));
                vel = out.add(0, 0.6, 0);
                alpha = Mth.clamp(t / 4.0F, 0.0F, 1.0F) * (1.0F - Mth.clamp((t - 25.0F) / 15.0F, 0.0F, 1.0F));
            }
            if (alpha <= 0.01F) continue;
            MementoFx.butterfly(buffers, m, pos, vel, up, size, flap, color, 0xFFFFFF, 0.25F, 0xFFFFFF, alpha);
        }
    }
}
