package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.chrono.ChronoLink;
import com.automatavalkyries.chrono.ChronoMachineBlockEntity;
import com.automatavalkyries.item.ChronoTunerItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * クロノ・チューナーを持っている間、近くのクロノリンク・マシンの空間リンクを見せる（2026-10-07 ユーザー指定
 * 「手に持っている間リンクしている証が付き、搬入・搬出・両方で 3 種類色分けして、複数つながっている場合は数字が見えるように」）。
 * <ul>
 *   <li>リンク先のブロックに色の枠（搬入 = 水色、搬出 = 橙、両方・入り混じり = 緑）と、マシンからリンク先への線</li>
 *   <li>同じブロックに 2 本以上つながっていれば、上に「×本数」</li>
 *   <li>マシンには白い薄い枠、チューナーが覚えている先には黄色の枠</li>
 * </ul>
 * リンクはマシンからクライアントへ送られる（{@link ChronoMachineBlockEntity#getUpdateTag}）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class ChronoLinkOverlay {

    private ChronoLinkOverlay() {}

    public static final int IN = 0x40C8FF, OUT = 0xFF9A3C, BOTH = 0x6CFF6C, MACHINE = 0xE6F0FF, PENDING = 0xFFE45A;
    private static final double RANGE = 48.0;

    private static boolean holdingTuner(Player p) {
        return p.getMainHandItem().getItem() instanceof ChronoTunerItem || p.getOffhandItem().getItem() instanceof ChronoTunerItem;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || !holdingTuner(player)) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        // リンク先ごとの本数と向き（ビット: 1 = 搬入、2 = 搬出）
        Map<BlockPos, int[]> targets = new HashMap<>();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (ChronoMachineBlockEntity m : ChronoMachineBlockEntity.CLIENT_LOADED) {
            if (m.isRemoved() || m.getLevel() != mc.level || m.getBlockPos().distToCenterSqr(player.position()) > RANGE * RANGE) continue;
            BlockPos mp = m.getBlockPos();
            box(pose, lines, new AABB(mp).inflate(0.004), MACHINE, 0.35F);
            Vec3 from = Vec3.atCenterOf(mp);
            for (ChronoLink link : m.links()) {
                if (!link.dimension().equals(mc.level.dimension())) continue;
                int bits = link.mode() == ChronoLink.Mode.IN ? 1 : link.mode() == ChronoLink.Mode.OUT ? 2 : 3;
                int[] t = targets.computeIfAbsent(link.pos(), k -> new int[2]);
                t[0]++;
                t[1] |= bits;
                line(pose, lines, from, Vec3.atCenterOf(link.pos()), color(bits), 0.6F);
            }
        }
        for (Map.Entry<BlockPos, int[]> e : targets.entrySet()) {
            box(pose, lines, new AABB(e.getKey()).inflate(0.02), color(e.getValue()[1]), 1.0F);
        }
        // チューナーが覚えている先（まだ登録していない）
        BlockPos pending = pending(player);
        if (pending != null) box(pose, lines, new AABB(pending).inflate(0.035), PENDING, 0.9F);
        pose.popPose();
        buffers.endBatch(RenderType.lines());

        // 本数（2 本以上）
        Font font = mc.font;
        for (Map.Entry<BlockPos, int[]> e : targets.entrySet()) {
            int count = e.getValue()[0];
            if (count < 2) continue;
            Vec3 at = Vec3.atCenterOf(e.getKey()).add(0, 0.85, 0).subtract(cam);
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(camera.rotation());
            pose.scale(-0.035F, -0.035F, 0.035F);
            String text = "×" + count;
            Matrix4f matrix = pose.last().pose();
            font.drawInBatch(text, -font.width(text) / 2.0F, 0, 0xFF000000 | color(e.getValue()[1]), false, matrix, buffers,
                    Font.DisplayMode.SEE_THROUGH, 0x80000000, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static int color(int bits) {
        return bits == 1 ? IN : bits == 2 ? OUT : BOTH;
    }

    private static BlockPos pending(Player p) {
        for (ItemStack s : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
            if (!(s.getItem() instanceof ChronoTunerItem) || s.getTag() == null || !s.getTag().contains("Target")) continue;
            CompoundTag t = s.getTag().getCompound("Target");
            if (!p.level().dimension().location().toString().equals(t.getString("dim"))) continue;
            return BlockPos.of(t.getLong("pos"));
        }
        return null;
    }

    private static void box(PoseStack pose, VertexConsumer lines, AABB box, int rgb, float alpha) {
        LevelRenderer.renderLineBox(pose, lines, box, ((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, alpha);
    }

    private static void line(PoseStack pose, VertexConsumer lines, Vec3 a, Vec3 b, int rgb, float alpha) {
        Vec3 n = b.subtract(a).normalize();
        Matrix4f m = pose.last().pose();
        var normal = pose.last().normal();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255, al = (int) (alpha * 255);
        lines.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, al).normal(normal, (float) n.x, (float) n.y, (float) n.z).endVertex();
        lines.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, al).normal(normal, (float) n.x, (float) n.y, (float) n.z).endVertex();
    }
}
