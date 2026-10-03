package com.automatavalkyries.client;

import com.automatavalkyries.weapon.TacticalWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 刃物の兵装の刃に、薄い光の刃先のレイヤー（{@link BladeAura}）を重ねる（2026-10-04 ユーザー指定「剣の兵装に」→「他の刃物系にも」）。
 * 刃の形はモデルに合わせて表に持つ（単位は px）。GeckoLib はモデルの x を鏡に映して描くので、左右対称でない形（曲がった刃など）は x を逆にして置く。
 * <ul>
 *   <li>まっすぐな刃（剣・槍の穂先・斧の刃・チェーンソーの刃・扇の刃）: 根元・向き・長さ・幅</li>
 *   <li>曲がった刃（大鎌・鎌）: モデルと同じつなぎ方で点を並べ、線に沿わせる</li>
 *   <li>輪の刃（チャクラム）: 円に沿った光の帯</li>
 * </ul>
 */
public class BladeAuraLayer extends GeoRenderLayer<TacticalWeaponItem> {

    /** 刃の光を描く物（モデルの根元の姿勢で呼ばれる。単位は px を 1/16 にしたブロック） */
    @FunctionalInterface
    public interface Shape {
        void draw(PoseStack pose, MultiBufferSource buffers, float time, float phase);
    }

    /** モデルの根元（root）の z の回転（度）と、刃の光の一覧 */
    public record Spec(float rootZ, List<Shape> shapes) {}

    private static final float PX = 1F / 16F;
    private static final Map<String, Spec> SPECS = new HashMap<>();

    /** まっすぐな刃（x・y は根元、ang は向き（度）、長さ・根元の幅・先の幅は px） */
    static Shape straight(float x, float y, float ang, float len, float w0, float w1, int color) {
        return (pose, buffers, time, phase) -> BladeAura.draw(pose, buffers, x * PX, y * PX, ang, len * PX, w0 * PX, w1 * PX, time, phase, 1.0F, color);
    }

    /** 剣（根元 -45 度、刃は y の向き） */
    static void sword(String id, float base, float len, float w0, float w1, int color) {
        SPECS.put(id, new Spec(-45F, List.of(straight(0, base, 90F, len, w0, w1, color))));
    }

    /** モデルと同じつなぎ方の曲がった刃（始点 (x0, y0)、count 節、1 節の長さ seg、k 節目の角度 = step × k、k 節目の高さ = h0 + dh × k）。x は鏡に映す */
    static Shape curved(float x0, float y0, int count, float seg, float step, float h0, float dh, float extra, int color) {
        float[] xs = new float[count + 1], ys = new float[count + 1], ws = new float[count + 1];
        float cx = x0, cy = y0;
        for (int k = 0; k <= count; k++) {
            xs[k] = -cx * PX;
            ys[k] = cy * PX;
            ws[k] = Math.max(0.4F, h0 + dh * Math.min(k, count - 1)) * PX;
            if (k == count) break;
            float a = (float) Math.toRadians(step * k);
            float l = k == count - 1 ? seg + extra : seg;
            cx += l * (float) Math.cos(a);
            cy += l * (float) Math.sin(a);
        }
        return (pose, buffers, time, phase) -> BladeAura.drawPath(pose, buffers, xs, ys, ws, true, false, time, phase, 1.0F, color);
    }

    static {
        // ---- まっすぐな剣 ----
        // 星葬冠は虹の刃の光（2026-10-04 ユーザー指定）
        SPECS.put("weapon_luminous_requiem", new Spec(-45F, List.of((pose, buffers, time, phase) ->
                BladeAura.drawRainbow(pose, buffers, 0, -0.4F * PX, 90F, 22.6F * PX, 3.0F * PX, 1.3F * PX, time, phase, 1.0F))));
        sword("weapon_photon_edge", -2.4F, 14.3F, 2.4F, 1.6F, 0x7FE0FF);
        sword("weapon_luxion_saber", -2.6F, 16.3F, 3.1F, 2.4F, 0xFFE8A0);
        sword("weapon_quantum_blade", -2.6F, 15.7F, 3.6F, 3.0F, 0xA898FF);
        sword("weapon_venom_stinger", 0F, 14.3F, 0.9F, 0.6F, 0x9AF070);
        sword("weapon_mirage_dagger", 0F, 7.9F, 1.8F, 1.5F, 0xD0A0FF);
        sword("weapon_eclipse_blade", -0.4F, 16.3F, 3.0F, 2.6F, 0xFFD060);
        sword("weapon_reflector_blade", -0.4F, 16.0F, 3.6F, 3.2F, 0xB8E8FF);
        sword("weapon_ragnarok_edge", -0.4F, 24.3F, 5.8F, 5.0F, 0xFF7048);
        sword("weapon_time_eliminator", -4.0F, 17.3F, 6.0F, 5.0F, 0xC0B0FF);
        // ---- 槍の穂先 ----
        sword("weapon_arc_lancer", 5.0F, 7.9F, 1.4F, 1.0F, 0x7FD8FF);
        sword("weapon_thunder_javelin", 4.2F, 7.3F, 1.8F, 1.3F, 0xFFF080);
        sword("weapon_nova_lance", 4.8F, 7.8F, 2.6F, 0.7F, 0xFFE060);
        // ---- 曲がった刃（モデルの大鎌・鎌と同じつなぎ方） ----
        SPECS.put("weapon_void_scythe", new Spec(-45F, List.of(curved(0.4F, 8.6F, 11, 2.5F, -9.5F, 3.8F, -0.3F, 0.35F, 0xD8A8FF))));
        SPECS.put("weapon_crimson_sickle", new Spec(-45F, List.of(curved(0.3F, 4.6F, 7, 1.6F, -14F, 1.8F, -0.18F, 0.25F, 0xFF4060))));
        // ---- 斧の両刃（刃の縁 x = ±6.8〜7.15、y 3〜9） ----
        SPECS.put("weapon_berserk_axe", new Spec(-45F, List.of(
                straight(6.97F, 3.0F, 90F, 6.0F, 0.9F, 0.9F, 0xFF5030),
                straight(-6.97F, 3.0F, 90F, 6.0F, 0.9F, 0.9F, 0xFF5030))));
        // ---- 黒蓮扇の 9 枚の花弁の刃（要 (0, -6) から外へ。左右対称なので鏡に映しても同じ） ----
        List<Shape> petals = new ArrayList<>();
        for (int k = 0; k < 9; k++) {
            float ang = 90F - (-64F + k * 16F);
            float rad = (float) Math.toRadians(ang);
            petals.add(straight((float) Math.cos(rad) * 4.5F, -6F + (float) Math.sin(rad) * 4.5F, ang, 5.0F, 2.0F, 1.5F, 0xFF8AC8));
        }
        SPECS.put("weapon_black_lotus", new Spec(0F, petals));
        // ---- チャクラムの輪の刃（寝かせた輪: 半径 6 の縁に沿った光の帯。xz 平面へ倒して描く） ----
        int ringPoints = 32;
        float[] rx = new float[ringPoints], ry = new float[ringPoints], rw = new float[ringPoints];
        for (int k = 0; k < ringPoints; k++) {
            double a = k * Math.PI * 2 / ringPoints;
            rx[k] = (float) Math.cos(a) * 6.0F * PX;
            ry[k] = (float) Math.sin(a) * 6.0F * PX;
            rw[k] = 1.4F * PX;
        }
        SPECS.put("weapon_plasma_chakram", new Spec(0F, List.of((pose, buffers, time, phase) -> {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(90F));
            BladeAura.drawPath(pose, buffers, rx, ry, rw, false, true, time, phase, 1.0F, 0xFF70E0);
            pose.popPose();
        })));
        // ---- チェーンソーの刃（手元から先へ。モデルの +X は描くと -X なので向きは 180 度） ----
        int[] sawGlow = {0, 0x6AF0FF, 0xFFA040, 0xFF5A9A, 0xFFF0B0};
        for (int t = 1; t <= 4; t++) {
            float s = 1F + (t - 1) * 0.12F;
            float len = 13F * s, h = 1.6F * s, cy = -0.5F * s;
            SPECS.put("tool_chainsaw_t" + t, new Spec(0F, List.of(straight(0, cy, 180F, len + h + 0.3F, 2 * h + 0.6F, 2 * h + 0.6F, sawGlow[t]))));
        }
    }

    /** その兵装の刃の光（刃物でなければ null） */
    public static @Nullable Spec specFor(String id) {
        return SPECS.get(id);
    }

    private final Spec spec;
    private final float phase;
    private final @Nullable String id;

    public BladeAuraLayer(GeoRenderer<TacticalWeaponItem> renderer, Spec spec, String id) {
        super(renderer);
        this.spec = spec;
        this.id = id;
        this.phase = (Math.abs(id.hashCode()) % 97) / 97F; // 兵装ごとに光の帯の走り出しをずらす
    }

    @Override
    public void render(PoseStack poseStack, TacticalWeaponItem animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        var level = Minecraft.getInstance().level;
        float time = level == null ? 0 : level.getGameTime() + partialTick;
        poseStack.pushPose();
        if (spec.rootZ() != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(spec.rootZ()));
        boolean overload = getRenderer() instanceof software.bernie.geckolib.renderer.GeoItemRenderer<TacticalWeaponItem> item
                && com.automatavalkyries.weapon.Overload.is(item.getCurrentItemStack());
        try {
            if (overload) { // オーバーロード: 紅の刃の光に、金の光をずらして重ねる
                BladeAura.tint = com.automatavalkyries.weapon.Overload.CRIMSON;
                BladeAura.tintBrightness = 0.85F + 0.15F * net.minecraft.util.Mth.sin(time * 0.5F);
            }
            int i = 0;
            for (Shape shape : spec.shapes()) shape.draw(poseStack, bufferSource, time, phase + i++ * 0.11F);
            if (overload) {
                BladeAura.tint = com.automatavalkyries.weapon.Overload.GOLD;
                BladeAura.tintBrightness = 0.45F;
                i = 0;
                for (Shape shape : spec.shapes()) shape.draw(poseStack, bufferSource, time, phase + 0.5F + i++ * 0.11F);
            }
        } finally {
            BladeAura.tint = -1;
            BladeAura.tintBrightness = 1F;
        }
        poseStack.popPose();
    }
}
