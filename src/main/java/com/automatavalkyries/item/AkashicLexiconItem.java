package com.automatavalkyries.item;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.akashic.AkashicCustomLexicon;
import com.automatavalkyries.entity.akashic.AkashicProbeEntity;
import com.automatavalkyries.entity.akashic.AkashicResistanceAuditor;
import com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.LexiconExecutionReport;
import com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.TechniqueKind;
import com.automatavalkyries.protect.IEprotected;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * プレイヤーもアカシックちゃんも使える「カスタムダメージ辞書（36種）」「カスタム即死辞書（72種）」アイテム
 * （2026-10-25 ユーザー指定: 「カスタムで設定できるダメージ辞書と即死辞書アイテムのも追加してプレイヤーもアカシックちゃんも使える」）。
 *
 * <ul>
 *   <li><b>スニーク＋右クリック</b>: 専用カスタム設定 GUI（{@code AkashicLexiconScreen}）を開き、36種のダメージ技術または72種の即死技術の
 *       個別 ON/OFF・ティア一括切替・威力倍率・連撃回数・範囲・執行順序を自由に設定・保存する。</li>
 *   <li><b>アカシックちゃんへ右クリック（または左クリック）</b>: そのカスタム辞書設定をアカシックちゃんに読み込ませ、
 *       検証モードや直接対決モードでカスタム辞書通りの技術群を行使させる。</li>
 *   <li><b>敵モブへ左クリック（攻撃）または右クリック</b>: プレイヤー自身がカスタム設定通りのダメージ技術群または即死技術群を即時発動する。</li>
 * </ul>
 */
public class AkashicLexiconItem extends Item {

    private final TechniqueKind kind;

    public AkashicLexiconItem(TechniqueKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public static Properties defaultProperties() {
        return new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant();
    }

    public TechniqueKind kind() {
        return kind;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (entity instanceof AkashicProbeEntity probe) {
            probe.loadCustomLexiconFromStack(stack, player);
            return true;
        }
        if (entity instanceof LivingEntity target) {
            if (!player.level().isClientSide && player.level() instanceof ServerLevel serverLevel) {
                executeByPlayerOnTarget(serverLevel, player, stack, target);
            }
            return true;
        }
        return false;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget, InteractionHand usedHand) {
        ItemStack realStack = player.getItemInHand(usedHand);
        if (interactionTarget instanceof AkashicProbeEntity probe) {
            probe.loadCustomLexiconFromStack(realStack, player);
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        if (player.isShiftKeyDown()) {
            if (player.level().isClientSide) {
                openGuiClient(usedHand, realStack, kind);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        if (!player.level().isClientSide && player.level() instanceof ServerLevel serverLevel) {
            executeByPlayerOnTarget(serverLevel, player, realStack, interactionTarget);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                openGuiClient(usedHand, stack, kind);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // 通常右クリック: 視線先のエンティティ（24m以内）または範囲指定（areaRadius > 0）があれば即時発動、なければ GUI を開く
        LivingEntity rayTarget = raycastTarget(player, 24.0D);
        AkashicCustomLexicon lexicon = AkashicCustomLexicon.fromStack(stack, kind);
        if (rayTarget instanceof AkashicProbeEntity probe) {
            probe.loadCustomLexiconFromStack(stack, player);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (rayTarget != null) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                executeByPlayerOnTarget(serverLevel, player, stack, rayTarget);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (lexicon.areaRadius() > 0.0F) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                int count = executeByPlayerInArea(serverLevel, player, lexicon, player.position());
                if (count > 0) {
                    return InteractionResultHolder.success(stack);
                }
            } else {
                return InteractionResultHolder.success(stack);
            }
        }

        if (level.isClientSide) {
            openGuiClient(usedHand, stack, kind);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * プレイヤーが指定ターゲット（および範囲設定がある場合は周囲の対象）に対してカスタム辞書を直接発動する。
     */
    public LexiconExecutionReport executeByPlayerOnTarget(
            ServerLevel level,
            Player player,
            ItemStack stack,
            LivingEntity primaryTarget
    ) {
        AkashicCustomLexicon lexicon = AkashicCustomLexicon.fromStack(stack, kind);
        LexiconExecutionReport report = AkashicResistanceAuditor.executeCustomLexicon(
                level, player, primaryTarget, lexicon, null);

        int extraDefeated = 0;
        if (lexicon.areaRadius() > 0.0F) {
            AABB box = primaryTarget.getBoundingBox().inflate(lexicon.areaRadius());
            List<LivingEntity> others = level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != primaryTarget && isEligibleForPlayerArea(player, e));
            for (LivingEntity other : others) {
                LexiconExecutionReport r = AkashicResistanceAuditor.executeCustomLexicon(
                        level, player, other, lexicon, null);
                if (r.penetrated()) extraDefeated++;
            }
        }

        String prefix = kind == TechniqueKind.DAMAGE ? "[ダメージ辞書] " : "[即死辞書] ";
        ChatFormatting color = kind == TechniqueKind.DAMAGE ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE;
        String dpsSuffix = "";
        if (kind == TechniqueKind.DAMAGE) {
            var prof = com.automatavalkyries.entity.akashic.AkashicDamageFlowchart.getProfileIfPresent(primaryTarget.getUUID());
            if (prof != null) {
                long now = level.getGameTime();
                double effDps = prof.dpsTelemetry.effectiveDealtDps(now);
                double ewmaDps = prof.dpsTelemetry.ewmaInstantDps(now);
                dpsSuffix = String.format(Locale.ROOT, " | 実効DPS %.1f (瞬間 %.1f)", effDps, ewmaDps);
            }
        }
        String detail;
        if (report.penetrated()) {
            String stageCode = report.penetratedStage() != null ? report.penetratedStage().code : "PENETRATED";
            detail = String.format(Locale.ROOT, "貫通撃破! (%s / %d段階執行%s)%s",
                    stageCode, report.executedStages(),
                    extraDefeated > 0 ? " +周囲" + extraDefeated + "体" : "",
                    dpsSuffix);
        } else if (report.postHp() < report.preHp() - 0.01F) {
            detail = String.format(Locale.ROOT, "ダメージ適用: HP %.1f -> %.1f (%d段階執行)%s",
                    report.preHp(), report.postHp(), report.executedStages(), dpsSuffix);
        } else {
            detail = String.format(Locale.ROOT, "耐性維持 (全%d段階を防御: HP %.1f)%s",
                    report.executedStages(), report.postHp(), dpsSuffix);
        }
        player.displayClientMessage(Component.literal(prefix).withStyle(color)
                .append(Component.literal(detail).withStyle(ChatFormatting.WHITE)), true);
        level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.4F);
        return report;
    }

    private int executeByPlayerInArea(ServerLevel level, Player player, AkashicCustomLexicon lexicon, Vec3 center) {
        double r = lexicon.areaRadius();
        AABB box = new AABB(center.x - r, center.y - r, center.z - r, center.x + r, center.y + r, center.z + r);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> isEligibleForPlayerArea(player, e));
        int penetratedCount = 0;
        for (LivingEntity t : targets) {
            LexiconExecutionReport rep = AkashicResistanceAuditor.executeCustomLexicon(level, player, t, lexicon, null);
            if (rep.penetrated()) penetratedCount++;
        }
        if (!targets.isEmpty()) {
            String prefix = kind == TechniqueKind.DAMAGE ? "[ダメージ辞書・範囲] " : "[即死辞書・範囲] ";
            player.displayClientMessage(Component.literal(prefix).withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(String.format(Locale.ROOT, "対象 %d体中 %d体を貫通撃破 (半径%.0fm)",
                            targets.size(), penetratedCount, r)).withStyle(ChatFormatting.WHITE)), true);
        }
        return targets.size();
    }

    private static boolean isEligibleForPlayerArea(Player player, LivingEntity entity) {
        if (entity == null || entity == player || !AkashicResistanceAuditor.isTargetStillPresent(entity)) return false;
        if (entity instanceof Player || entity instanceof AkashicProbeEntity) return false;
        if (entity instanceof IEprotected p && !p.isTerminusTestTarget()) return false;
        if (entity instanceof ValkyrieEntity) return false;
        return true;
    }

    @Nullable
    private static LivingEntity raycastTarget(Player player, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(range));
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.5D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player, start, end, box,
                e -> e instanceof LivingEntity && !e.isSpectator() && e.isPickable(),
                range * range);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static void openGuiClient(InteractionHand hand, ItemStack stack, TechniqueKind kind) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.automatavalkyries.client.AkashicLexiconScreen.open(hand, stack, kind));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        AkashicCustomLexicon lexicon = AkashicCustomLexicon.fromStack(stack, kind);
        ChatFormatting theme = kind == TechniqueKind.DAMAGE ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE;
        tooltip.add(Component.literal(lexicon.summaryText()).withStyle(theme));
        tooltip.add(Component.literal("・スニーク＋右クリック: カスタム設定GUIを開く").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("・敵へ左クリック/右クリック: プレイヤーが直接発動").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("・アカシックへ右クリック: カスタム辞書をアカシックへ登録").withStyle(ChatFormatting.AQUA));
    }
}
