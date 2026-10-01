package com.automatavalkyries.entity.test;

import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * 試験的フラッシュファー（Experimental Flashfur）。
 * <p>
 * Omni-Mobs の Metapotent Flashfur が持つ StackChecker（スタック検閲による外部攻撃拒否）や
 * healthDelta（ダメージ計算外の HP 改ざん）機構を再現したテスト用ボス。
 * 通常の呼び出し元からの攻撃や削除は StackChecker により遮断され即座に回復する。
 * Eprotect+ Java エージェントの特権迂回（TrueKillStick）によってのみ StackChecker を突破・抹殺できる。
 * </p>
 */
public class ExperimentalFlashfurEntity extends Monster {

    /** Omni-Mobs 型の HP 減少デルタ値 */
    private float healthDelta = 0.0F;

    public ExperimentalFlashfurEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setCustomName(Component.literal("§b§l[Experimental] Metapotent Flashfur"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.ATTACK_DAMAGE, 50.0D);
    }

    public float getHealthDelta() {
        return this.healthDelta;
    }

    public void setHealthDelta(float delta) {
        this.healthDelta = delta;
    }

    /**
     * Omni-Mobs / Metapotent Flashfur 特有の StackChecker 防御。
     * 発信元のコールスタックを検査し、許可されたクラスまたは Eprotect+ 特権エージェント空間からのアクセス以外を拒絶する。
     */
    public boolean checkStackAuthorized() {
        if (EprotectPlusLink.isPrivilegedOrBypassing()) {
            return true;
        }

        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement elem : stack) {
            String cn = elem.getClassName();
            if (cn.contains("flashfur") || cn.contains("omnimobs")
                    || cn.contains("EprotectPlusAgent") || cn.contains("TrueAnnihilationEngine")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!checkStackAuthorized()) {
            // StackChecker による拒否: HP を即座に戻す
            this.setHealth(this.getMaxHealth());
            this.healthDelta = 0.0F;
            this.deathTime = 0;
            return false;
        }

        if (this.healthDelta < 0.0F) {
            // healthDelta 負数による異常状態を吸収
            this.setHealth(this.getMaxHealth());
            return false;
        }

        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!checkStackAuthorized()) {
            this.deathTime = 0;
            this.setHealth(this.getMaxHealth());
            return;
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!checkStackAuthorized()) {
            ((EntityAccessor) this).automata_valkyries$setRemovalReason(null);
            this.setHealth(this.getMaxHealth());
            return;
        }
        super.remove(reason);
    }

    @Override
    public void kill() {
        if (!checkStackAuthorized()) {
            this.setHealth(this.getMaxHealth());
            this.deathTime = 0;
            return;
        }
        super.kill();
    }
}
