package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.akashic.AkashicProbeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 事象観測体アカシック・プローブの描画（2026-10-25 / 2026-10-26 頭上専用観測 UI コンソール追加）。モデルは Tripo で作られた三角形のメッシュ（{@code meshes/akashic_probe.obj}）を
 * そのまま描く（{@link ObjMesh}。骨が無いので、体全体を浮かせて揺らし、向きだけ変える）。周りに 3 つの多面体の結晶（正二十面体）を回し、
 * 頭上に専用の「事象観測 UI コンソール（モード・HP/不死ロック・防御/検死カウンタ・108種検証プログレス・Universal DPS 解析・リアルタイム観測ログ）」をホログラム表示する
 */
public class AkashicProbeRenderer extends EntityRenderer<AkashicProbeEntity> {

    public static final ResourceLocation MESH = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "meshes/akashic_probe.obj");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/akashic_probe.png");
    private static final ResourceLocation CRYSTAL = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/akashic_crystal.png");
    /** メッシュの高さ（OBJ の単位で 0〜0.9775）を当たり判定の高さ（1.75 ブロック）に合わせる倍率 */
    private static final float SCALE = 1.75F / 0.9775F;
    /** 結晶の色（立ち絵の桃・薄紫・水色）と、回る半径・高さ・大きさ */
    private static final int[] CRYSTAL_COLORS = {0xFF4FD8, 0xB07CFF, 0x4FE3FF};
    private static final float[][] CRYSTAL_ORBITS = {{0.78F, 1.55F, 0.17F}, {0.92F, 1.85F, 0.21F}, {0.70F, 1.30F, 0.13F}};
    /** 頭上の専用観測 UI を出す距離 */
    private static final double HOLOGRAM_RANGE = 36.0;
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    public AkashicProbeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.35F;
    }

    @Override
    public ResourceLocation getTextureLocation(AkashicProbeEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(AkashicProbeEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float time = entity.tickCount + partialTick;
        float bob = 0.12F + Mth.sin(time * 0.08F) * 0.06F; // 少し浮いて、ゆっくり上下する
        float yaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);

        poseStack.pushPose();
        poseStack.translate(0.0, bob, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.05F) * 1.5F)); // わずかに揺れる
        ObjMesh mesh = ObjMesh.get(MESH);
        if (mesh != null) {
            poseStack.pushPose();
            poseStack.scale(SCALE, SCALE, SCALE);
            mesh.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light,
                    LivingEntityRenderer.getOverlayCoords(entity, 0.0F), 0xFFFFFFFF);
            poseStack.popPose();
        }
        renderCrystals(time, poseStack, buffers);
        poseStack.popPose();

        if (this.entityRenderDispatcher.distanceToSqr(entity) <= HOLOGRAM_RANGE * HOLOGRAM_RANGE) {
            renderHologram(entity, bob, poseStack, buffers);
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffers, light);
    }

    // ---- 結晶 ----------------------------------------------------------------

    private static final float PHI = (1.0F + Mth.sqrt(5.0F)) / 2.0F;
    private static final float[][] ICO_VERTS = {
            {-1, PHI, 0}, {1, PHI, 0}, {-1, -PHI, 0}, {1, -PHI, 0}, {0, -1, PHI}, {0, 1, PHI},
            {0, -1, -PHI}, {0, 1, -PHI}, {PHI, 0, -1}, {PHI, 0, 1}, {-PHI, 0, -1}, {-PHI, 0, 1}};
    private static final int[][] ICO_FACES = {
            {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
            {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};

    private static void renderCrystals(float time, PoseStack poseStack, MultiBufferSource buffers) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(CRYSTAL));
        for (int i = 0; i < CRYSTAL_COLORS.length; i++) {
            float[] orbit = CRYSTAL_ORBITS[i];
            float angle = time * (0.025F + i * 0.008F) + i * Mth.TWO_PI / 3.0F;
            poseStack.pushPose();
            poseStack.translate(Mth.cos(angle) * orbit[0], orbit[1] + Mth.sin(time * 0.06F + i) * 0.08F, Mth.sin(angle) * orbit[0]);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * (2.0F + i)));
            poseStack.mulPose(Axis.XP.rotationDegrees(time * (1.3F + i * 0.4F)));
            float s = orbit[2] / Mth.sqrt(1.0F + PHI * PHI);
            poseStack.scale(s, s, s);
            icosahedron(poseStack, consumer, CRYSTAL_COLORS[i], 0xB0);
            poseStack.popPose();
        }
    }

    private static void icosahedron(PoseStack poseStack, VertexConsumer consumer, int rgb, int alpha) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        for (int f = 0; f < ICO_FACES.length; f++) {
            float[] a = ICO_VERTS[ICO_FACES[f][0]], c = ICO_VERTS[ICO_FACES[f][1]], d = ICO_VERTS[ICO_FACES[f][2]];
            float nx = a[0] + c[0] + d[0], ny = a[1] + c[1] + d[1], nz = a[2] + c[2] + d[2];
            float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= len;
            ny /= len;
            nz /= len;
            int shade = f % 2 == 0 ? 0 : 28; // 面ごとに少し明暗を付けて、結晶の切子に見せる
            float[][] quad = {a, c, d, d};
            for (float[] v : quad) {
                consumer.vertex(pose, v[0], v[1], v[2]).color(Math.max(0, r - shade), Math.max(0, g - shade), Math.max(0, b - shade), alpha)
                        .uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(normal, nx, ny, nz).endVertex();
            }
        }
    }

    // ---- 頭上専用観測 UI コンソール (Overhead Observation Console) ----------------

    private void renderHologram(AkashicProbeEntity entity, float bob, PoseStack poseStack, MultiBufferSource buffers) {
        Font font = getFont();
        CompoundTag ui = entity.observationUiData();
        List<Component> feedLines = entity.hologramLines();

        int modeOrdinal = ui.contains("ModeOrdinal") ? ui.getInt("ModeOrdinal") : 0;
        String modeLabel = ui.getString("ModeLabel");
        if (modeLabel.isEmpty()) {
            modeLabel = AkashicProbeEntity.Mode.OBSERVE_STANDBY.displayLabel;
        }
        String lexiconSummary = ui.getString("LexiconSummary");
        float hp = ui.contains("Hp") ? ui.getFloat("Hp") : 100.0F;
        float maxHp = ui.contains("MaxHp") ? Math.max(1.0F, ui.getFloat("MaxHp")) : 100.0F;
        boolean keepFullHp = !ui.contains("KeepFullHp") || ui.getBoolean("KeepFullHp");

        long refusedCount = ui.getLong("RefusedCount");
        long repairedCount = ui.getLong("RepairedCount");
        long detectedCount = ui.getLong("DetectedCount");
        long dmgReceivedCount = ui.getLong("DamageReceivedCount");
        long totalEvents = ui.getLong("TotalEvents");
        String suspectMod = ui.getString("SuspectMod");
        if (suspectMod.isEmpty()) suspectMod = "none";

        String targetName = ui.getString("TargetName");
        String targetMod = ui.getString("TargetMod");
        float targetHp = ui.getFloat("TargetHp");
        float targetMaxHp = Math.max(1.0F, ui.getFloat("TargetMaxHp"));
        int stageProgress = ui.getInt("StageProgress");
        int stageTotal = ui.getInt("StageTotal");
        String lastStageCode = ui.getString("LastStageCode");
        String lastStageOutcome = ui.getString("LastStageOutcome");
        String lastVerdictRank = ui.getString("LastVerdictRank");

        double dpsEff = ui.getDouble("DpsEffective");
        double dpsEwma = ui.getDouble("DpsEwma");
        double dpsEttk = ui.getDouble("DpsEttk");
        float detectedFloor = ui.getFloat("DetectedFloor");
        float detectedCap = ui.getFloat("DetectedCap");
        String bestChannel = ui.getString("BestChannel");

        int accentColor = modeAccentColor(modeOrdinal);
        Component titleComp = Component.literal("◈ ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.translatable("akashic.automata_valkyries.header").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" ◈").withStyle(ChatFormatting.AQUA));

        String modeText = "MODE: " + modeLabel + (lexiconSummary.isEmpty() ? "" : "  [辞書: " + lexiconSummary + "]");
        String hpBadge = keepFullHp ? " [完全不死ロック: ON]" : " [上限解除・実戦]";
        String hpText = String.format(Locale.ROOT, "HP: %.1f / %.1f%s", hp, maxHp, hpBadge);

        Component countersComp = Component.literal("遮断:")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(refusedCount)).withStyle(ChatFormatting.RED))
                .append(Component.literal(" | 修復:").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.valueOf(repairedCount)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" | 検知:").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.valueOf(detectedCount)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | 被弾:").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.valueOf(dmgReceivedCount)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" | 干渉MOD:").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(suspectMod).withStyle(ChatFormatting.LIGHT_PURPLE));

        boolean showTargetSection = !targetName.isEmpty() || modeOrdinal > 0 || !lastVerdictRank.isEmpty();
        String targetLine = "";
        String auditLine = "";
        String verdictLine = "";
        String dpsLine = "";
        if (showTargetSection) {
            if (!targetName.isEmpty()) {
                targetLine = String.format(Locale.ROOT, "対象: %s [%s]  HP: %.1f / %.1f",
                        targetName, targetMod.isEmpty() ? "vanilla" : targetMod, targetHp, targetMaxHp);
            } else {
                targetLine = "対象: 索敵・待機中 (半径16m内の検証対象または攻撃者を自動捕捉)";
            }
            if (stageTotal > 0 && (stageProgress > 0 || modeOrdinal == 1 || modeOrdinal == 2 || modeOrdinal == 3)) {
                auditLine = String.format(Locale.ROOT, "段階検証: [%d / %d] %s",
                        stageProgress, stageTotal,
                        lastStageCode.isEmpty() ? "待機中" : (lastStageCode + " -> " + lastStageOutcome));
            }
            if (!lastVerdictRank.isEmpty()) {
                verdictLine = "★ 耐性判定ランク: 【" + lastVerdictRank + "】";
            }
            if (dpsEff > 0.0D || detectedFloor > 0.0F || detectedCap > 0.0F) {
                String ettkStr = dpsEttk <= 0.0D ? (targetHp <= 0.0F ? "0.0s" : "∞")
                        : String.format(Locale.ROOT, "%.1fs", dpsEttk);
                String extra = "";
                if (detectedFloor > 0.0F) extra += String.format(Locale.ROOT, " Floor:%.1f", detectedFloor);
                if (detectedCap > 0.0F) extra += String.format(Locale.ROOT, " Cap:%.1f", detectedCap);
                dpsLine = String.format(Locale.ROOT, "DPS解析: 実効 %.1f/s | 瞬間 %.1f/s | ETTK:%s | 経路:%s%s",
                        dpsEff, dpsEwma, ettkStr, bestChannel.isEmpty() ? "auto" : bestChannel, extra);
            }
        }

        String feedHeader = String.format(Locale.ROOT, "▼ リアルタイム事象観測ログ (累計観測: %d件)", totalEvents);
        List<Component> displayFeed = new ArrayList<>(feedLines);
        if (displayFeed.isEmpty()) {
            displayFeed.add(Component.literal("待機中 — 攻撃・即死技術・メモリ改ざんを自動観測します").withStyle(ChatFormatting.GRAY));
        }

        // パネル幅と高さの算出
        int contentWidth = 228;
        contentWidth = Math.max(contentWidth, font.width(titleComp));
        contentWidth = Math.max(contentWidth, font.width(modeText));
        contentWidth = Math.max(contentWidth, font.width(countersComp));
        if (!targetLine.isEmpty()) contentWidth = Math.max(contentWidth, font.width(targetLine));
        if (!auditLine.isEmpty()) contentWidth = Math.max(contentWidth, font.width(auditLine));
        if (!verdictLine.isEmpty()) contentWidth = Math.max(contentWidth, font.width(verdictLine));
        if (!dpsLine.isEmpty()) contentWidth = Math.max(contentWidth, font.width(dpsLine));
        for (Component c : displayFeed) {
            contentWidth = Math.max(contentWidth, font.width(c) + 6);
        }

        float padX = 7.0F;
        float halfW = contentWidth / 2.0F + padX;
        float leftX = -halfW + padX;
        float barWidth = halfW * 2.0F - padX * 2.0F;

        int rowH = 10;
        float totalHeight = 6.0F // 上下余白
                + 13.0F          // タイトルリボン
                + 11.0F          // モード表示行
                + 12.0F          // HP バー＋数値行
                + 11.0F          // 防御・検死カウンタ行
                + (!targetLine.isEmpty() ? 12.0F : 0.0F)
                + (!auditLine.isEmpty() ? 12.0F : 0.0F)
                + (!verdictLine.isEmpty() ? 11.0F : 0.0F)
                + (!dpsLine.isEmpty() ? 11.0F : 0.0F)
                + 12.0F          // ログ見出し行
                + displayFeed.size() * rowH;

        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() + 0.62F + bob, 0.0);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(-0.0175F, -0.0175F, 0.0175F);
        Matrix4f m = poseStack.last().pose();

        float yTop = -totalHeight;
        float yBot = 0.0F;

        // 1. パネル背景・発光外枠・四隅のサイバーブラケット
        drawPanelFrame(m, buffers, -halfW, yTop, halfW, yBot, accentColor);

        float curY = yTop + 3.0F;

        // 2. タイトルヘッダーリボン
        quad(m, buffers, -halfW + 1.5F, curY - 1.0F, halfW - 1.5F, curY + 10.5F, 0.015F, 0xE00C213B);
        quad(m, buffers, -halfW + 1.5F, curY - 1.0F, -halfW + 4.0F, curY + 10.5F, 0.008F, accentColor);
        quad(m, buffers, halfW - 4.0F, curY - 1.0F, halfW - 1.5F, curY + 10.5F, 0.008F, accentColor);
        drawCenteredText(font, titleComp.getVisualOrderText(), 0.0F, curY + 1.0F, 0xFFFFFFFF, m, buffers);
        curY += 13.0F;

        // 3. 稼働モード＆カスタム辞書ステータス行
        drawLeftText(font, Component.literal(modeText).getVisualOrderText(), leftX, curY, accentColor, m, buffers);
        curY += 11.0F;

        // 4. HP ゲージバー ＆ 不死ロック状態
        float hpRatio = Mth.clamp(hp / maxHp, 0.0F, 1.0F);
        int hpBarColor = keepFullHp ? 0xFF28E09E : (hpRatio > 0.35F ? 0xFF42B8FF : 0xFFFF5252);
        drawBar(m, buffers, leftX, curY, barWidth, 9.5F, hpRatio, 0xCC121E30, (hpBarColor & 0x00FFFFFF) | 0x88000000);
        drawCenteredText(font, Component.literal(hpText).getVisualOrderText(), 0.0F, curY + 1.0F, 0xFFFFFFFF, m, buffers);
        curY += 12.0F;

        // 5. 防御・検死カウンタ行
        drawLeftText(font, countersComp.getVisualOrderText(), leftX, curY, 0xFFFFFFFF, m, buffers);
        curY += 11.0F;

        // 6. 対峙ターゲット・段階検証プログレス・判定ランク・Universal DPS 解析セクション
        if (showTargetSection) {
            quad(m, buffers, -halfW + 3.0F, curY - 1.5F, halfW - 3.0F, curY - 1.0F, 0.012F, (accentColor & 0x00FFFFFF) | 0x60000000);
            if (!targetLine.isEmpty()) {
                float tRatio = !targetName.isEmpty() ? Mth.clamp(targetHp / targetMaxHp, 0.0F, 1.0F) : 0.0F;
                drawBar(m, buffers, leftX, curY, barWidth, 9.5F, tRatio, 0xAA18122B, 0x66D94862);
                drawLeftText(font, Component.literal(targetLine).getVisualOrderText(), leftX + 2.0F, curY + 1.0F, 0xFFFFE6F0, m, buffers);
                curY += 12.0F;
            }
            if (!auditLine.isEmpty()) {
                float sRatio = stageTotal > 0 ? Mth.clamp((float) stageProgress / (float) stageTotal, 0.0F, 1.0F) : 0.0F;
                drawBar(m, buffers, leftX, curY, barWidth, 9.5F, sRatio, 0xAA142030, 0x77D488FF);
                drawLeftText(font, Component.literal(auditLine).getVisualOrderText(), leftX + 2.0F, curY + 1.0F, 0xFFF2E6FF, m, buffers);
                curY += 12.0F;
            }
            if (!verdictLine.isEmpty()) {
                quad(m, buffers, leftX, curY - 0.5F, leftX + barWidth, curY + 9.0F, 0.014F, 0xCC2C1642);
                drawLeftText(font, Component.literal(verdictLine).getVisualOrderText(), leftX + 2.0F, curY + 0.5F, 0xFFFFD84F, m, buffers);
                curY += 11.0F;
            }
            if (!dpsLine.isEmpty()) {
                drawLeftText(font, Component.literal(dpsLine).getVisualOrderText(), leftX, curY, 0xFF7CF5FF, m, buffers);
                curY += 11.0F;
            }
        }

        // 7. リアルタイム事象観測ログ見出し＋フィード一覧
        quad(m, buffers, -halfW + 2.0F, curY - 0.5F, halfW - 2.0F, curY + 9.5F, 0.015F, 0xD00D1D33);
        drawLeftText(font, Component.literal(feedHeader).getVisualOrderText(), leftX, curY + 1.0F, 0xFF7CE8FF, m, buffers);
        curY += 12.0F;

        for (int i = 0; i < displayFeed.size(); i++) {
            if (i % 2 == 0) {
                quad(m, buffers, -halfW + 2.5F, curY - 0.5F, halfW - 2.5F, curY + rowH - 1.0F, 0.018F, 0x480E1E36);
            }
            Component line = displayFeed.get(i);
            drawLeftText(font, line.getVisualOrderText(), leftX + 1.0F, curY, 0xFFFFFFFF, m, buffers);
            curY += rowH;
        }

        poseStack.popPose();
    }

    private static int modeAccentColor(int modeOrdinal) {
        return switch (modeOrdinal) {
            case 1 -> 0xFFFFD84F; // ② ダメージ検証: ゴールドイエロー
            case 2 -> 0xFFE07CFF; // ③ 即死検証: ライトパープル
            case 3 -> 0xFFFF9E3D; // ④ 全108種検証: アンバーゴールド
            case 4 -> 0xFFFF4F5E; // ⑤ 直接対決（上限解除）: クリムゾンレッド
            case 5 -> 0xFFC84FFF; // ⑥ 直接対決（ペナルティなし最強）: ネオンバイオレット
            default -> 0xFF4FE3FF; // ① 観測待機: アクアシアン
        };
    }

    private static void drawPanelFrame(Matrix4f m, MultiBufferSource buffers, float x0, float y0, float x1, float y1, int accent) {
        // 半透明ダークガラス背景
        quad(m, buffers, x0, y0, x1, y1, 0.025F, 0xCC060C18);
        // 外周の細い発光ライン
        int border = (accent & 0x00FFFFFF) | 0x80000000;
        quad(m, buffers, x0, y0, x1, y0 + 0.7F, 0.010F, border);
        quad(m, buffers, x0, y1 - 0.7F, x1, y1, 0.010F, border);
        quad(m, buffers, x0, y0, x0 + 0.7F, y1, 0.010F, border);
        quad(m, buffers, x1 - 0.7F, y0, x1, y1, 0.010F, border);
        // 四隅のサイバーブラケット強調
        float c = 6.0F, t = 1.4F;
        quad(m, buffers, x0, y0, x0 + c, y0 + t, 0.004F, accent);
        quad(m, buffers, x0, y0, x0 + t, y0 + c, 0.004F, accent);
        quad(m, buffers, x1 - c, y0, x1, y0 + t, 0.004F, accent);
        quad(m, buffers, x1 - t, y0, x1, y0 + c, 0.004F, accent);
        quad(m, buffers, x0, y1 - t, x0 + c, y1, 0.004F, accent);
        quad(m, buffers, x0, y1 - c, x0 + t, y1, 0.004F, accent);
        quad(m, buffers, x1 - c, y1 - t, x1, y1, 0.004F, accent);
        quad(m, buffers, x1 - t, y1 - c, x1, y1, 0.004F, accent);
    }

    private static void drawBar(Matrix4f m, MultiBufferSource buffers, float x, float y, float w, float h, float ratio, int trackColor, int fillColor) {
        quad(m, buffers, x, y, x + w, y + h, 0.016F, trackColor);
        float fill = w * Mth.clamp(ratio, 0.0F, 1.0F);
        if (fill > 0.0F) {
            quad(m, buffers, x, y, x + fill, y + h, 0.012F, fillColor);
            quad(m, buffers, x + Math.max(0.0F, fill - 1.0F), y, x + fill, y + h, 0.008F, 0xCCFFFFFF);
        }
    }

    private static void quad(Matrix4f m, MultiBufferSource buffers, float x0, float y0, float x1, float y1, float z, int argb) {
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        int a = argb >>> 24, r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(LIGHT).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(LIGHT).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(LIGHT).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(LIGHT).endVertex();
    }

    private static void drawCenteredText(Font font, FormattedCharSequence text, float cx, float y, int color, Matrix4f m, MultiBufferSource buffers) {
        font.drawInBatch(text, cx - font.width(text) / 2.0F, y, color, false, m, buffers, Font.DisplayMode.NORMAL, 0, LIGHT);
    }

    private static void drawLeftText(Font font, FormattedCharSequence text, float x, float y, int color, Matrix4f m, MultiBufferSource buffers) {
        font.drawInBatch(text, x, y, color, false, m, buffers, Font.DisplayMode.NORMAL, 0, LIGHT);
    }
}
