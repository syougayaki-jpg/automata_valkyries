package com.automatavalkyries.entity;

import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 終焉機構（超絶抹殺・Terminus）と Eprotect+ の対決検証用「protect+ ダミーゾンビ」。
 * <p>
 * {@link IEprotected} を実装し Eprotect+ の全防御層（被ダメージ制限、死亡・消去・HP0の遮断、修復、10秒耐性強化等）を保持する一方、
 * {@link #isTerminusTestTarget()} を {@code true} にすることで、終焉機構（TerminusEngine / TrueAnnihilationEngine / Overload / AnnihilationExecutionAgent）
 * の「味方保護ホワイトリストによるスキップ」から除外されている。
 * これにより、終焉機構の全力の抹殺パイプライン（JVMエージェント解体、Unsafeメモリ消去、属性破壊、バニラ即死、Tick排除等）を
 * 真正面から受け止めて検証することができる。
 * </p>
 */
public class ProtectDummyZombieEntity extends Zombie implements IEprotected {

    private Eprotect protect;

    public ProtectDummyZombieEntity(EntityType<? extends ProtectDummyZombieEntity> type, Level level) {
        super(type, level);
        this.protect = new Eprotect(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected boolean isSunSensitive() {
        return false; // 日光で燃え尽きない
    }

    @Override
    public boolean convertsInWater() {
        return false; // ドラウンド化しない
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        getEprotect().onAddedToWorld();
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        getEprotect().onRemovedFromWorld();
    }

    @Override
    public void baseTick() {
        super.baseTick();
        getEprotect().tick();
    }

    @Override
    public Eprotect getEprotect() {
        // Unsafe によるフィールドのゼロクリア・null化が行われても即座に再初期化
        if (this.protect == null) {
            this.protect = new Eprotect(this);
        }
        return this.protect;
    }

    /**
     * 終焉機構の味方保護ホワイトリストから除外。
     * 終焉機構はこのエンティティを味方としてスキップせず、全力で超絶抹殺を執行する。
     */
    @Override
    public boolean isTerminusTestTarget() {
        return true;
    }

    @Override
    public Component getTypeName() {
        return Component.translatable("entity.automata_valkyries.protect_dummy_zombie");
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!player.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            float hp = this.getHealth();
            float maxHp = this.getMaxHealth();
            boolean alive = this.isAlive();
            boolean removed = this.isRemoved();
            boolean protectActive = this.getEprotect() != null && this.getEprotect().isProtectionActive();
            boolean reinforced = EprotectPlusLink.isReinforcedActive()
                    || (this.getEprotect() != null && this.getEprotect().isReinforcedActive());
            long remainingMs = EprotectPlusLink.getReinforcedRemainingMs();

            var prof = com.automatavalkyries.entity.akashic.AkashicDamageFlowchart.getProfileIfPresent(this.getUUID());
            String formulaLine = "";
            if (prof != null) {
                long now = this.level().getGameTime();
                formulaLine = "\n§eUniversal DPS: §b" + prof.dpsTelemetry.formatSummary(now, hp, prof.healRatePerSec, 0.0F)
                        + (prof.lastOptimalPlan != null ? "\n§eOptimal Formula: §d" + prof.lastOptimalPlan.formulaSummary() : "");
            }

            player.sendSystemMessage(Component.literal("§6[DummyZombie Diagnostic]§r\n"
                    + "§eHP: §a" + hp + " / " + maxHp + "\n"
                    + "§eAlive: §a" + alive + " (removed: " + removed + ")\n"
                    + "§eEprotect Active: §a" + protectActive + "\n"
                    + "§eReinforced Mode: §d" + reinforced + (reinforced ? " (" + remainingMs + "ms remaining)" : "") + "\n"
                    + "§eTerminus Test Target (終焉保護対象外): §c" + isTerminusTestTarget()
                    + formulaLine));
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
}
