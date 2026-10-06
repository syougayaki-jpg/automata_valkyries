package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.network.TacticalFxPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 戦術級コードの演出（自作の描画。2026-10-07 ユーザー指定「戦術級らしく、半径 50 ブロックの赤黒い螺旋状の光で落ちてくるど派手な演出」）。
 * GUNGNIR:
 * <ol>
 *   <li>降下（最初の {@link #DESCENT} Tick）: 空の高み（半径 50）から、赤い光と黒い帯の螺旋 8 本が回りながら狙った所へ収束して落ちてくる</li>
 *   <li>照射: 中心に赤い光の柱（白い芯）、そのまわりを赤と黒の螺旋が高速で巻き上がり、地面には半径 50 の赤黒い輪と、外から内へ縮む衝撃の輪</li>
 *   <li>最後の 10 Tick で消えていく</li>
 * </ol>
 * 赤い光は足し算の描き方（{@link RenderType#lightning}）、黒い帯は半透明（{@link RenderType#debugQuads}）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class TacticalFx {

    private TacticalFx() {}

    public static final int DESCENT = TacticalFxPacket.GUNGNIR_DESCENT;
    private static final double HEIGHT = 140.0;

    private record Effect(int kind, Vec3 pos, float radius, int duration, long start, long seed,
                          java.util.List<com.automatavalkyries.tactical.TacticalPatterns.Bomb> bombs, java.util.Map<Integer, Double> groundY) {}

    private static final List<Effect> ACTIVE = new ArrayList<>();

    public static void start(TacticalFxPacket p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        ACTIVE.add(new Effect(p.kind(), new Vec3(p.x(), p.y(), p.z()), p.radius(), p.duration(), level.getGameTime(), p.seed(),
                p.kind() == TacticalFxPacket.CARPET ? com.automatavalkyries.tactical.TacticalPatterns.carpetBombs(p.seed()) : java.util.List.of(), new java.util.HashMap<>()));
    }

    /** 腕の先から土砂・溶岩・煙を噴き上げる（えぐり取る様子。地形が壊れない設定でも見た目は出す） */
    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        if (level == null || Minecraft.getInstance().isPaused()) return;
        var rand = level.random;
        for (Effect e : ACTIVE) {
            if (e.kind == TacticalFxPacket.CARPET) {
                carpetEmbers(level, e);
                continue;
            }
            if (e.kind != TacticalFxPacket.GUNGNIR) continue;
            float st = level.getGameTime() - e.start - DESCENT;
            if (st < 0 || st > TacticalFxPacket.GUNGNIR_STRIKE) continue;
            for (int arm = 0; arm < TacticalFxPacket.GUNGNIR_ARMS; arm++) {
                double[] g = TacticalFxPacket.gungnirArm(arm, st, e.radius);
                int x = Mth.floor(e.pos.x + g[0]), z = Mth.floor(e.pos.z + g[1]);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                var below = level.getBlockState(new net.minecraft.core.BlockPos(x, y - 1, z));
                var debris = below.isAir() ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : below;
                for (int i = 0; i < 10; i++) {
                    level.addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, debris),
                            x + rand.nextDouble(), y + 0.2, z + rand.nextDouble(), (rand.nextDouble() - 0.5) * 1.2, 0.6 + rand.nextDouble() * 1.4, (rand.nextDouble() - 0.5) * 1.2);
                }
                level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA, x + 0.5, y + 0.3, z + 0.5, 0, 0, 0);
                level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, x + rand.nextDouble(), y + 0.5, z + rand.nextDouble(), 0, 0.25, 0);
                if (((int) st + arm) % 6 == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION, x + 0.5, y + 1, z + 0.5, 0, 0, 0);
            }
        }
    }

    /**
     * 演出専用の入れ物（種類ごとに別の BufferBuilder）。ゲームの共通の入れ物では、別の種類を取り出すたびに前の種類の描きかけが
     * 締められて同じ入れ物を使い回すので、光（位置・色）と素材（位置・色・UV…）が混ざって落ちた（2026-10-07「Not filled all elements of the vertex」）
     */
    private static MultiBufferSource.BufferSource fxBuffers;

    private static MultiBufferSource.BufferSource buffers() {
        if (fxBuffers == null) {
            java.util.Map<RenderType, com.mojang.blaze3d.vertex.BufferBuilder> map = new java.util.LinkedHashMap<>();
            map.put(RenderType.debugQuads(), new com.mojang.blaze3d.vertex.BufferBuilder(1 << 18));
            map.put(RenderType.lightning(), new com.mojang.blaze3d.vertex.BufferBuilder(1 << 18));
            for (RenderType type : TacticalSprites.types()) map.put(type, new com.mojang.blaze3d.vertex.BufferBuilder(1 << 18));
            fxBuffers = MultiBufferSource.immediateWithBuffers(map, new com.mojang.blaze3d.vertex.BufferBuilder(1 << 12));
        }
        return fxBuffers;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        float partial = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = buffers();
        Iterator<Effect> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Effect e = it.next();
            float t = mc.level.getGameTime() - e.start + partial;
            if (t > e.duration) {
                it.remove();
                continue;
            }
            pose.pushPose();
            pose.translate(e.pos.x - cam.x, e.pos.y - cam.y, e.pos.z - cam.z);
            float fade = Mth.clamp((e.duration - t) / 10F, 0F, 1F);
            Matrix4f m = pose.last().pose();
            VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
            VertexConsumer dark = buffers.getBuffer(RenderType.debugQuads());
            switch (e.kind) {
                case TacticalFxPacket.CARPET -> carpet(m, glow, dark, e, t, fade);
                case TacticalFxPacket.IRIS -> iris(m, glow, dark, e, t, fade);
                case TacticalFxPacket.ABYSS -> abyss(m, glow, dark, e, t);
                case TacticalFxPacket.COMET -> { } // TacticalComet（下の素材の段でまとめて描く）
                default -> {
                    if (t < DESCENT) descent(m, glow, dark, e.radius, t / DESCENT, t);
                    else strike(m, glow, dark, e.radius, t - DESCENT, fade);
                }
            }
            // 素材を貼った光・炎・煙・花びら（TacticalSprites）を重ねる
            TacticalSprites.Ctx k = new TacticalSprites.Ctx(m, event.getCamera().getLeftVector(), event.getCamera().getUpVector(), buffers);
            switch (e.kind) {
                case TacticalFxPacket.CARPET -> TacticalSprites.carpet(k, e.seed, e.bombs, (x, z) -> groundY(e, x, z), t, fade);
                case TacticalFxPacket.IRIS -> TacticalSprites.iris(k, e.seed, e.radius, t, fade);
                case TacticalFxPacket.ABYSS -> TacticalSprites.abyss(k, e.seed, t, e.duration);
                case TacticalFxPacket.COMET -> TacticalComet.render(m, glow, dark, k, e.seed, e.radius, t, e.duration,
                        new Vec3(cam.x - e.pos.x, cam.y - e.pos.y, cam.z - e.pos.z));
                default -> TacticalSprites.gungnir(k, e.radius, t, fade);
            }
            pose.popPose();
        }
        buffers.endBatch();
    }

    /** 降下: 螺旋 8 本の頭が空から地面へ。半径は高さに比例（上で radius、下で 3） */
    private static void descent(Matrix4f m, VertexConsumer glow, VertexConsumer dark, float radius, float k, float t) {
        double head = HEIGHT * (1 - k * k); // だんだん速く
        for (int arm = 0; arm < 8; arm++) {
            double base = arm * Math.PI / 4;
            double prevX = 0, prevY = 0, prevZ = 0;
            for (int s = 0; s <= 40; s++) {
                double u = s / 40.0;
                double y = head + u * 50.0;                       // 頭から上へ 50 の尾
                double r = Math.max(3.0, radius * Math.min(1.0, y / HEIGHT));
                double a = base + u * Math.PI * 3 + t * 0.25;
                double x = Math.cos(a) * r, z = Math.sin(a) * r;
                if (s > 0) {
                    float alpha = (float) (1 - u);                // 尾ほど薄く
                    ribbon(glow, m, prevX, prevY, prevZ, x, y, z, 0.9, 255, 40, 50, (int) (230 * alpha));
                    ribbon(dark, m, prevX * 1.04, prevY - 0.6, prevZ * 1.04, x * 1.04, y - 0.6, z * 1.04, 1.6, 15, 0, 5, (int) (170 * alpha));
                }
                prevX = x;
                prevY = y;
                prevZ = z;
            }
        }
    }

    /** 照射: 柱・巻き上がる螺旋・地面の輪 */
    private static void strike(Matrix4f m, VertexConsumer glow, VertexConsumer dark, float radius, float t, float fade) {
        // 光の柱（赤、芯は白っぽく）
        int a = (int) (220 * fade);
        cylinder(glow, m, 4.5, 0, HEIGHT, 24, 255, 30, 40, a);
        cylinder(glow, m, 1.6, 0, HEIGHT, 16, 255, 220, 220, a);
        // 巻き上がる赤と黒の螺旋（中心から半径いっぱいまで広がりながら）
        for (int arm = 0; arm < 8; arm++) {
            double base = arm * Math.PI / 4 + t * 0.35;
            double px = 0, py = 0, pz = 0;
            for (int s = 0; s <= 48; s++) {
                double u = s / 48.0, y = u * 60.0;
                double r = 3 + (radius - 3) * Math.pow(u, 1.6) * (0.6 + 0.4 * Math.sin(t * 0.08 + arm));
                double ang = base + u * Math.PI * 4;
                double x = Math.cos(ang) * r, z = Math.sin(ang) * r;
                if (s > 0) {
                    boolean red = arm % 2 == 0;
                    if (red) ribbon(glow, m, px, py, pz, x, y, z, 1.0, 255, 30, 40, (int) (200 * fade * (1 - u * 0.6)));
                    else ribbon(dark, m, px, py, pz, x, y, z, 1.8, 10, 0, 4, (int) (160 * fade * (1 - u * 0.5)));
                }
                px = x;
                py = y;
                pz = z;
            }
        }
        // 空から地面をえぐる螺旋の腕（腕の先がサーバーで地形を削る所。TacticalFxPacket#gungnirArm）
        for (int arm = 0; arm < TacticalFxPacket.GUNGNIR_ARMS; arm++) {
            double[] g = TacticalFxPacket.gungnirArm(arm, t, radius);
            double baseA = Math.atan2(g[1], g[0]), gr = Math.hypot(g[0], g[1]);
            double px = 0, py = 0, pz = 0;
            for (int s = 0; s <= 40; s++) {
                double u = s / 40.0;                         // 0 = 空、1 = 地面
                double y = HEIGHT * (1 - u);
                double r = radius + (gr - radius) * u;
                double ang = baseA - (1 - u) * Math.PI * 1.5;
                double x = Math.cos(ang) * r, z = Math.sin(ang) * r;
                if (s > 0) {
                    ribbon(glow, m, px, py, pz, x, y, z, 2.2, 255, 25, 35, (int) (230 * fade * (0.4 + 0.6 * u)));
                    ribbon(dark, m, px * 1.03, py + 1.2, pz * 1.03, x * 1.03, y + 1.2, z * 1.03, 2.6, 8, 0, 2, (int) (180 * fade));
                }
                px = x;
                py = y;
                pz = z;
            }
            // 腕の先の地面に赤い渦
            ringAt(glow, m, g[0], g[1], 3.5 + Math.sin(t * 0.6 + arm) * 0.8, 0.3, 1.4, 255, 60, 40, (int) (220 * fade));
        }
        // 地面の輪（半径いっぱいの赤黒い輪と、外から内へ縮む衝撃の輪）
        ring(glow, m, radius, 0.15, 1.2, 255, 20, 30, (int) (220 * fade));
        ring(dark, m, radius + 1.5, 0.12, 1.6, 0, 0, 0, (int) (170 * fade));
        for (int w = 0; w < 3; w++) {
            float phase = ((t + w * 13) % 40) / 40F;
            ring(glow, m, radius * (1 - phase), 0.2, 1.0 + 2 * phase, 255, 60, 60, (int) (200 * fade * (1 - phase)));
        }
    }

    // ------------------------------------------------------------------ CARPET（異空間から現れる無人爆撃機・焼夷弾・残る炎）

    private static double groundY(Effect e, double x, double z) {
        int bx = Mth.floor(e.pos.x + x), bz = Mth.floor(e.pos.z + z);
        int key = (bx * 73856093) ^ (bz * 19349663);
        return e.groundY.computeIfAbsent(key, k -> {
            var level = Minecraft.getInstance().level;
            return (double) level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, bx, bz) - e.pos.y;
        });
    }

    private static void carpet(Matrix4f m, VertexConsumer glow, VertexConsumer dark, Effect e, float t, float fade) {
        Vec3 dir = com.automatavalkyries.tactical.TacticalPatterns.carpetDir(e.seed), perp = new Vec3(-dir.z, 0, dir.x);
        int portal = com.automatavalkyries.tactical.TacticalPatterns.CARPET_PORTAL, pass = com.automatavalkyries.tactical.TacticalPatterns.CARPET_PASS;
        double r = com.automatavalkyries.tactical.TacticalPatterns.CARPET_R, alt = com.automatavalkyries.tactical.TacticalPatterns.CARPET_ALT + 2;
        // 異空間の裂け目（入口: 開いて爆撃機を吐き出し閉じる、出口: 最後に開いて飲み込む）
        float in = t < portal ? t / portal : t < portal + 30 ? 1F : Math.max(0F, 1F - (t - portal - 30) / 15F);
        float out = t < portal + pass - 30 ? 0F : t < portal + pass + 5 ? Math.min(1F, (t - (portal + pass - 30)) / 15F) : Math.max(0F, 1F - (t - portal - pass - 5) / 15F);
        rift(m, glow, dark, dir.scale(-r - 30).add(0, alt, 0), perp, in, t);
        rift(m, glow, dark, dir.scale(r + 30).add(0, alt, 0), perp, out, t);
        // 爆撃機
        if (t >= portal && t <= portal + pass) {
            for (int i = 0; i < com.automatavalkyries.tactical.TacticalPatterns.CARPET_BOMBERS; i++) bomberModel(m, glow, dark, com.automatavalkyries.tactical.TacticalPatterns.bomber(i, t, e.seed), dir, perp, t + i);
        }
        // 焼夷弾（落ちる間は光る筋）と残る炎
        for (com.automatavalkyries.tactical.TacticalPatterns.Bomb b : e.bombs) {
            if (t >= b.dropTick() && t < b.landTick()) {
                float k = (t - b.dropTick()) / (b.landTick() - b.dropTick());
                double gy = groundY(e, b.x(), b.z());
                double y = b.dropPos().y + (gy - b.dropPos().y) * k * k;
                double x = b.dropPos().x + (b.x() - b.dropPos().x) * k, z = b.dropPos().z + (b.z() - b.dropPos().z) * k;
                ribbon(glow, m, x, y + 3.5, z, x, y, z, 0.35, 255, 140, 40, 220);
                cube(glow, m, x, y, z, 0.35, 255, 220, 120, 255);
            } else if (t >= b.landTick() && t < b.landTick() + com.automatavalkyries.tactical.TacticalPatterns.CARPET_FIRE) {
                float age = t - b.landTick();
                float life = Math.min(1F, (com.automatavalkyries.tactical.TacticalPatterns.CARPET_FIRE - age) / 30F) * fade;
                flames(glow, m, b.x(), groundY(e, b.x(), b.z()), b.z(), t + b.dropTick() * 3, life * 0.35F, age < 6 ? 1.0F + (6 - age) * 0.4F : 1.0F);
            }
        }
    }

    /** 裂け目（縦長の楕円の渦。黒い縁と紫の光） */
    private static void rift(Matrix4f m, VertexConsumer glow, VertexConsumer dark, Vec3 c, Vec3 side, float open, float t) {
        if (open <= 0.01F) return;
        int seg = 40;
        for (int ring = 0; ring < 4; ring++) {
            double w = (16 - ring * 3) * open, h = (9 - ring * 1.6) * open;
            double rot = t * (0.05 + ring * 0.03) * (ring % 2 == 0 ? 1 : -1);
            for (int i = 0; i < seg; i++) {
                double a0 = i * Math.PI * 2 / seg + rot, a1 = (i + 1) * Math.PI * 2 / seg + rot;
                Vec3 p0 = c.add(side.scale(Math.cos(a0) * w)).add(0, Math.sin(a0) * h, 0);
                Vec3 p1 = c.add(side.scale(Math.cos(a1) * w)).add(0, Math.sin(a1) * h, 0);
                if (ring == 0) ribbon(dark, m, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, 2.2, 5, 0, 12, (int) (220 * open));
                else ribbon(glow, m, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, 0.6, 170 + ring * 20, 60, 255, (int) (200 * open));
            }
        }
    }

    /** 無人爆撃機（黒い胴・三角の翼・赤い噴射口） */
    private static void bomberModel(Matrix4f m, VertexConsumer glow, VertexConsumer dark, Vec3 p, Vec3 dir, Vec3 perp, float t) {
        Vec3 nose = p.add(dir.scale(4.5)), tail = p.add(dir.scale(-3.5));
        Vec3 wl = p.add(perp.scale(-6)).add(dir.scale(-2.5)), wr = p.add(perp.scale(6)).add(dir.scale(-2.5));
        int[] hull = {28, 26, 34, 255};
        // 翼（上下 2 枚で厚みを出す）
        for (double dy : new double[]{0.0, 0.25}) {
            quadPts(dark, m, nose.add(0, dy, 0), wl.add(0, dy, 0), tail.add(0, dy, 0), wr.add(0, dy, 0), hull);
        }
        // 胴（尖った箱）
        Vec3 up = new Vec3(0, 0.6, 0);
        quadPts(dark, m, nose, tail.add(perp.scale(0.8)), tail.add(perp.scale(0.8)).add(up), nose.add(up.scale(0.5)), hull);
        quadPts(dark, m, nose, tail.add(perp.scale(-0.8)), tail.add(perp.scale(-0.8)).add(up), nose.add(up.scale(0.5)), hull);
        // 赤い光の筋と噴射口
        int[] red = {255, 30, 40, 230};
        ribbon(glow, m, wl.x, wl.y + 0.3, wl.z, nose.x, nose.y + 0.3, nose.z, 0.12, red[0], red[1], red[2], red[3]);
        ribbon(glow, m, wr.x, wr.y + 0.3, wr.z, nose.x, nose.y + 0.3, nose.z, 0.12, red[0], red[1], red[2], red[3]);
        float flick = 0.8F + 0.2F * Mth.sin(t * 1.7F);
        Vec3 exhaust = tail.add(dir.scale(-3.0 * flick));
        ribbon(glow, m, tail.x, tail.y + 0.3, tail.z, exhaust.x, exhaust.y + 0.3, exhaust.z, 0.9, 255, 120, 60, 220);
    }

    /** 独自の炎（黄白の芯から橙・紫へ移る揺らめく炎を、十字に 2 枚） */
    private static void flames(VertexConsumer c, Matrix4f m, double x, double y, double z, float t, float life, float burst) {
        if (life <= 0.01F) return;
        for (int k = 0; k < 3; k++) {
            double a = k * Math.PI / 3 + t * 0.03;
            double dx = Math.cos(a), dz = Math.sin(a);
            double h = (1.8 + 0.6 * Math.sin(t * 0.45 + k * 2)) * burst;
            double w = 0.9 * burst;
            int al = (int) (220 * life);
            // 下: 黄白、中: 橙、上: 紫（足し算の描き方なので重なるほど明るい）
            quad(c, m, new float[]{(float) (x - dx * w), (float) y, (float) (z - dz * w), (float) (x + dx * w), (float) y, (float) (z + dz * w),
                            (float) (x + dx * w * 0.6), (float) (y + h * 0.5), (float) (z + dz * w * 0.6), (float) (x - dx * w * 0.6), (float) (y + h * 0.5), (float) (z - dz * w * 0.6)},
                    new int[]{255, 240, 170, al, 255, 240, 170, al, 255, 120, 30, al, 255, 120, 30, al});
            quad(c, m, new float[]{(float) (x - dx * w * 0.6), (float) (y + h * 0.5), (float) (z - dz * w * 0.6), (float) (x + dx * w * 0.6), (float) (y + h * 0.5), (float) (z + dz * w * 0.6),
                            (float) (x + dx * 0.05), (float) (y + h), (float) (z + dz * 0.05), (float) (x - dx * 0.05), (float) (y + h), (float) (z - dz * 0.05)},
                    new int[]{255, 120, 30, al, 255, 120, 30, al, 190, 40, 255, 0, 190, 40, 255, 0});
        }
    }

    /** 炎から火の粉と煙（クライアントの Tick） */
    private static void carpetEmbers(net.minecraft.client.multiplayer.ClientLevel level, Effect e) {
        float t = level.getGameTime() - e.start;
        var rand = level.random;
        for (com.automatavalkyries.tactical.TacticalPatterns.Bomb b : e.bombs) {
            if (t < b.landTick() || t >= b.landTick() + com.automatavalkyries.tactical.TacticalPatterns.CARPET_FIRE || rand.nextFloat() > 0.25F) continue;
            double x = e.pos.x + b.x(), z = e.pos.z + b.z(), y = e.pos.y + groundY(e, b.x(), b.z());
            level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME, x + rand.nextGaussian() * 0.4, y + 0.3, z + rand.nextGaussian() * 0.4, 0, 0.06, 0);
            if (rand.nextFloat() < 0.3F) level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, x, y + 1.5, z, 0, 0.08, 0);
            if (t == b.landTick()) level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA, x, y + 0.5, z, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ IRIS（舞い落ちるカラフルな花びら）


    private static void iris(Matrix4f m, VertexConsumer glow, VertexConsumer dark, Effect e, float t, float fade) {
        double r = e.radius;
        // 花びらは TacticalSprites#iris（素材を貼った面）で描く
        // うっすら光るドームの縁と、地面の花の輪
        ring(glow, m, r, 0.15, 0.8, 255, 160, 210, (int) (140 * fade));
        ring(glow, m, r * 0.5 + Math.sin(t * 0.05) * 1.5, 0.12, 0.5, 140, 230, 255, (int) (90 * fade));
    }

    // ------------------------------------------------------------------ ABYSS（ひび割れた球体から漏れる光・広がる吸引）

    private static void abyss(Matrix4f m, VertexConsumer glow, VertexConsumer dark, Effect e, float t) {
        float life = e.duration;
        float end = Math.max(0F, (t - (life - 12)) / 12F);                 // 最後の 0.6 秒で潰れる
        double sr = 2.6 * (1 + t / life * 0.6) * (1 - end * 0.9);
        // 黒い球（緯度・経度で分けた面）
        int lat = 24, lon = 48; // 解像度を上げた球
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int k = 0; k < lon; k++) {
                double p0 = Math.PI * 2 * k / lon, p1 = Math.PI * 2 * (k + 1) / lon;
                quadPts(dark, m, sph(t0, p0, sr), sph(t0, p1, sr), sph(t1, p1, sr), sph(t1, p0, sr), new int[]{8, 0, 14, 255});
            }
        }
        // ひび（光る割れ目）と漏れ出す光の筋
        java.util.Random rnd = new java.util.Random(e.seed);
        float pulse = 0.7F + 0.3F * Mth.sin(t * 0.5F);
        int cracks = 12;
        for (int c = 0; c < cracks; c++) {
            double th = rnd.nextDouble() * Math.PI, ph = rnd.nextDouble() * Math.PI * 2;
            Vec3 prev = sph(th, ph, sr * 1.01);
            int steps = 5 + rnd.nextInt(5);
            for (int s = 0; s < steps; s++) {
                th += (rnd.nextDouble() - 0.5) * 0.5;
                ph += (rnd.nextDouble() - 0.5) * 0.6;
                Vec3 next = sph(th, ph, sr * 1.01);
                if ((float) s / steps < t / (life * 0.5F)) { // ひびは時間とともに伸びる
                    ribbon(glow, m, prev.x, prev.y, prev.z, next.x, next.y, next.z, 0.18, 230, 180, 255, (int) (255 * pulse));
                    if (s % 2 == 0) { // 光が漏れ出す
                        Vec3 ray = next.normalize().scale(sr + 4 + 8 * pulse * (0.5 + 0.5 * Mth.sin(t * 0.3F + c + s)));
                        ribbon(glow, m, next.x, next.y, next.z, ray.x, ray.y, ray.z, 0.5, 200, 120, 255, (int) (120 * pulse));
                    }
                }
                prev = next;
            }
        }
        // 吸い込む範囲（広がる輪）と、外から中心へ流れ込む螺旋の筋
        double pr = com.automatavalkyries.tactical.TacticalPatterns.abyssRadius(t);
        ring(glow, m, pr, -1.4, 1.0, 150, 60, 255, (int) (200 * (1 - end)));
        ring(dark, m, pr + 1.2, -1.45, 1.4, 0, 0, 0, (int) (160 * (1 - end)));
        for (int k = 0; k < 10; k++) {
            float ph = ((t * 2 + k * 9) % 45) / 45F;                       // 0 = 外、1 = 中心
            double rr = pr * (1 - ph), a0 = k * Math.PI / 5 + ph * Math.PI * 2;
            double x0 = Math.cos(a0) * rr, z0 = Math.sin(a0) * rr;
            double rr2 = pr * (1 - Math.min(1, ph + 0.08)), a1 = a0 + 0.5;
            double x1 = Math.cos(a1) * rr2, z1 = Math.sin(a1) * rr2;
            ribbon(glow, m, x0, -1.0 + ph, z0, x1, -1.0 + ph, z1, 0.6, 180, 90, 255, (int) (200 * (1 - ph)));
        }
        if (end > 0) ring(glow, m, 6 + end * 30, 0, 2.5, 255, 230, 255, (int) (255 * (1 - end))); // 爆縮の閃き
    }

    private static Vec3 sph(double theta, double phi, double r) {
        return new Vec3(Math.sin(theta) * Math.cos(phi) * r, Math.cos(theta) * r, Math.sin(theta) * Math.sin(phi) * r);
    }

    // ------------------------------------------------------------------ 形

    /** 4 点の面（両面） */
    static void quadPts(VertexConsumer c, Matrix4f m, Vec3 a, Vec3 b, Vec3 cc, Vec3 d, int[] rgba) {
        quad(c, m, new float[]{(float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, (float) cc.x, (float) cc.y, (float) cc.z, (float) d.x, (float) d.y, (float) d.z},
                new int[]{rgba[0], rgba[1], rgba[2], rgba[3], rgba[0], rgba[1], rgba[2], rgba[3], rgba[0], rgba[1], rgba[2], rgba[3], rgba[0], rgba[1], rgba[2], rgba[3]});
    }

    /** 小さな立方体（焼夷弾） */
    private static void cube(VertexConsumer c, Matrix4f m, double x, double y, double z, double h, int r, int g, int b, int a) {
        Vec3 o = new Vec3(x, y, z);
        int[] col = {r, g, b, a};
        Vec3[] v = new Vec3[8];
        for (int i = 0; i < 8; i++) v[i] = o.add((i & 1) == 0 ? -h : h, (i & 2) == 0 ? -h : h, (i & 4) == 0 ? -h : h);
        quadPts(c, m, v[0], v[1], v[3], v[2], col);
        quadPts(c, m, v[4], v[5], v[7], v[6], col);
        quadPts(c, m, v[0], v[1], v[5], v[4], col);
        quadPts(c, m, v[2], v[3], v[7], v[6], col);
        quadPts(c, m, v[0], v[2], v[6], v[4], col);
        quadPts(c, m, v[1], v[3], v[7], v[5], col);
    }

    /** 4 点の面を表と裏の両方（裏から見ても消えないよう）。xyz は 4 点分、rgba は 4 点分 */
    static void quad(VertexConsumer c, Matrix4f m, float[] p, int[] col) {
        for (int i = 0; i < 4; i++) c.vertex(m, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).color(col[i * 4], col[i * 4 + 1], col[i * 4 + 2], col[i * 4 + 3]).endVertex();
        for (int i = 3; i >= 0; i--) c.vertex(m, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).color(col[i * 4], col[i * 4 + 1], col[i * 4 + 2], col[i * 4 + 3]).endVertex();
    }

    /** 2 点の間の帯（上下に width の厚み） */
    static void ribbon(VertexConsumer c, Matrix4f m, double x1, double y1, double z1, double x2, double y2, double z2,
                               double width, int r, int g, int b, int a) {
        if (a <= 0) return;
        float h = (float) (width / 2);
        float fx1 = (float) x1, fy1 = (float) y1, fz1 = (float) z1, fx2 = (float) x2, fy2 = (float) y2, fz2 = (float) z2;
        quad(c, m, new float[]{fx1, fy1 - h, fz1, fx1, fy1 + h, fz1, fx2, fy2 + h, fz2, fx2, fy2 - h, fz2},
                new int[]{r, g, b, a, r, g, b, a, r, g, b, a, r, g, b, a});
    }

    /** 中心の柱（側面だけ） */
    static void cylinder(VertexConsumer c, Matrix4f m, double r, double y0, double y1, int seg, int cr, int cg, int cb, int a) {
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float x0 = (float) (Math.cos(a0) * r), z0 = (float) (Math.sin(a0) * r), x1 = (float) (Math.cos(a1) * r), z1 = (float) (Math.sin(a1) * r);
            quad(c, m, new float[]{x0, (float) y0, z0, x0, (float) y1, z0, x1, (float) y1, z1, x1, (float) y0, z1},
                    new int[]{cr, cg, cb, a, cr, cg, cb, a / 4, cr, cg, cb, a / 4, cr, cg, cb, a});
        }
    }

    /** 中心から (cx, cz) ずれた所の輪 */
    static void ringAt(VertexConsumer c, Matrix4f m, double cx, double cz, double r, double y, double width, int cr, int cg, int cb, int a) {
        Matrix4f shifted = new Matrix4f(m).translate((float) cx, 0, (float) cz);
        ring(c, shifted, r, y, width, cr, cg, cb, a);
    }

    /** 地面の輪（高さ y の水平な帯、幅 width） */
    static void ring(VertexConsumer c, Matrix4f m, double r, double y, double width, int cr, int cg, int cb, int a) {
        if (a <= 0 || r <= 0.1) return;
        int seg = 96;
        double ri = Math.max(0, r - width / 2), ro = r + width / 2;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float fy = (float) y;
            quad(c, m, new float[]{(float) (Math.cos(a0) * ri), fy, (float) (Math.sin(a0) * ri), (float) (Math.cos(a0) * ro), fy, (float) (Math.sin(a0) * ro),
                    (float) (Math.cos(a1) * ro), fy, (float) (Math.sin(a1) * ro), (float) (Math.cos(a1) * ri), fy, (float) (Math.sin(a1) * ri)},
                    new int[]{cr, cg, cb, a, cr, cg, cb, a, cr, cg, cb, a, cr, cg, cb, a});
        }
    }
}
