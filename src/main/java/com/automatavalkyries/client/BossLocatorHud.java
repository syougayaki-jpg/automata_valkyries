package com.automatavalkyries.client;

import com.automatavalkyries.entity.RetreatableBoss;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import org.joml.Vector3f;

/**
 * ボスの居場所の目印（2026-10-26 ユーザー指定「空中でぼすを見失う」）: このMOD のボス（{@link RetreatableBoss}）が {@value #RANGE} m 以内にいる間、
 * 画面の外（上・後ろなど）なら画面の縁に赤い矢印と距離、画面の中でも離れていれば体のまわりに赤い括弧と距離を出す。
 * クライアントの設定 {@code combat.bossLocator} で切れる（2026-10-29）
 */
public final class BossLocatorHud {

    public static final double RANGE = 128.0;
    private static final int RED = 0xFFFF3050, DARK = 0xA0200008;

    private BossLocatorHud() {
    }

    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "boss_locator", BossLocatorHud::render);
    }

    static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || !com.automatavalkyries.config.ValkyrieClientConfig.bossLocator()) return;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 eye = cam.getPosition();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof RetreatableBoss) || !(e instanceof LivingEntity boss) || !boss.isAlive() || boss.isInvisible()) continue;
            Vec3 at = boss.getPosition(pt).add(0, boss.getBbHeight() * 0.6, 0);
            double dist = at.distanceTo(eye);
            if (dist > RANGE) continue;
            mark(mc, g, cam, at.subtract(eye), dist, w, h, boss.getBbHeight());
        }
    }

    private static void mark(Minecraft mc, GuiGraphics g, Camera cam, Vec3 rel, double dist, int w, int h, float height) {
        Vector3f fwd = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
        double cx = -(rel.x * left.x() + rel.y * left.y() + rel.z * left.z());
        double cy = rel.x * up.x() + rel.y * up.y() + rel.z * up.z();
        double cz = rel.x * fwd.x() + rel.y * fwd.y() + rel.z * fwd.z();
        double f = (h / 2.0) / Math.tan(Math.toRadians(mc.options.fov().get() * mc.player.getFieldOfViewModifier()) / 2.0);
        String label = (int) dist + "m";
        int margin = 22;
        if (cz > 0.1) {
            double sx = w / 2.0 + cx / cz * f, sy = h / 2.0 - cy / cz * f;
            if (sx > margin && sx < w - margin && sy > margin && sy < h - margin) {      // 画面の中: 離れていれば括弧で囲む
                if (dist < 14.0) return;
                int half = Mth.clamp((int) (height * 0.5 / cz * f) + 4, 8, 60);
                int x = (int) sx, y = (int) sy, t = (int) (System.currentTimeMillis() / 120 % 4);
                int a = half + t;
                bracket(g, x - a, y - a, 1, 1);
                bracket(g, x + a, y - a, -1, 1);
                bracket(g, x - a, y + a, 1, -1);
                bracket(g, x + a, y + a, -1, -1);
                g.drawCenteredString(mc.font, label, x, y + a + 3, RED);
                return;
            }
        }
        double ang = Math.atan2(-cy, cx);                                                  // 画面の外（後ろも）: 縁へ矢印
        if (cz <= 0.1 && Math.abs(cx) < 1.0e-3 && Math.abs(cy) < 1.0e-3) ang = Math.PI / 2;
        double dx = Math.cos(ang), dy = Math.sin(ang);
        double kx = dx == 0 ? Double.MAX_VALUE : (w / 2.0 - margin) / Math.abs(dx), ky = dy == 0 ? Double.MAX_VALUE : (h / 2.0 - margin) / Math.abs(dy);
        double k = Math.min(kx, ky);
        float x = (float) (w / 2.0 + dx * k), y = (float) (h / 2.0 + dy * k);
        float pulse = 1.0F + 0.15F * Mth.sin((float) (System.currentTimeMillis() % 1000) / 1000.0F * Mth.TWO_PI);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotation((float) ang));
        g.pose().scale(pulse, pulse, 1);
        for (int i = 0; i < 8; i++) {                                                      // 先の尖った矢印（右向きを回す）
            g.fill(-6 + i, -7 + i, -4 + i, 7 - i, DARK);
            g.fill(-5 + i, -6 + i, -3 + i, 6 - i, RED);
        }
        g.fill(-14, -2, -6, 2, RED);
        g.pose().popPose();
        int lx = (int) (x - dx * 18), ly = (int) (y - dy * 18) - 4;
        g.drawCenteredString(mc.font, label, lx, ly, RED);
    }

    /** 角の括弧（sx・sy は内側への向き） */
    private static void bracket(GuiGraphics g, int x, int y, int sx, int sy) {
        int len = 7;
        g.fill(Math.min(x, x + sx * len), Math.min(y, y + sy * 2), Math.max(x, x + sx * len), Math.max(y, y + sy * 2), RED);
        g.fill(Math.min(x, x + sx * 2), Math.min(y, y + sy * len), Math.max(x, x + sx * 2), Math.max(y, y + sy * len), RED);
    }
}
