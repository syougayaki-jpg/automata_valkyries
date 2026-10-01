package com.automatavalkyries.entity.test;

import com.automatavalkyries.mixin.EntityAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * 試験的ウィザージラ（Experimental Witherzilla）。
 * <p>
 * Witherzilla の凶悪な生命維持機構（witherzillaLife、HP 自動全快、remove 拒否、die 阻止）を再現したテスト用ボス。
 * 通常の攻撃・/kill コマンド・discard() は一切通用せず、Eprotect+ Java エージェントを搭載した「真抹殺の棒」によってのみ抹殺できる。
 * </p>
 */
public class ExperimentalWitherzillaEntity extends Monster {

    /** Witherzilla の生命維持フラグ（getWitherzillaLife() で参照される） */
    private boolean witherzillaLife = true;

    public ExperimentalWitherzillaEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setCustomName(Component.literal("§5§l[Experimental] Witherzilla"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 50.0D);
    }

    public boolean getWitherzillaLife() {
        return this.witherzillaLife;
    }

    public void setWitherzillaLife(boolean life) {
        this.witherzillaLife = life;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (this.witherzillaLife) return true;
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.witherzillaLife) {
            // 被弾しても即時 HP 全快
            this.setHealth(this.getMaxHealth());
            this.deathTime = 0;
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (this.witherzillaLife) {
            // 外部からの直接 die() 呼び出しをブロック
            this.deathTime = 0;
            this.setHealth(this.getMaxHealth());
            return;
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.witherzillaLife) {
            // 外部からの直接削除をブロック
            ((EntityAccessor) this).automata_valkyries$setRemovalReason(null);
            this.setHealth(this.getMaxHealth());
            return;
        }
        super.remove(reason);
    }

    @Override
    public void kill() {
        if (this.witherzillaLife) {
            // /kill コマンドの無力化
            this.setHealth(this.getMaxHealth());
            this.deathTime = 0;
            return;
        }
        super.kill();
    }
}
