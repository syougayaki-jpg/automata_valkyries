package com.automatavalkyries.gametest.fake.mixin;

import com.automatavalkyries.gametest.fake.FakeStatefulMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * GameTest 用: 架空の MOD が Mixin で既存のクラスに足すフィールド（実際の MOD は vanilla の Entity / LivingEntity に足す）。
 * 元のクラスファイルに無いフィールドとして見つかり、架空の MOD のものと分かること・辞書どおりに戻される／外されることを確かめる。
 */
@Mixin(FakeStatefulMob.class)
public abstract class FakeInjectedStateMixin {

    /** 死亡の印（保護対象なら戻す） */
    @Unique
    public boolean fakemod$condemned;

    /** 守り（保護対象では触らない。抹殺では外す） */
    @Unique
    public boolean fakemod$invulnerable;

    /** 状態の入れ物（中の isDead を見つけて戻すこと） */
    @Unique
    public com.automatavalkyries.gametest.fake.FakeExpandedContext fakemod$ecData = new com.automatavalkyries.gametest.fake.FakeExpandedContext();

    /** 同期データの鍵を Mixin の差し込みから作り、補助クラスに置く（鍵の名前を作られた場所から引けること） */
    @org.spongepowered.asm.mixin.injection.Inject(method = "<clinit>", at = @org.spongepowered.asm.mixin.injection.At("TAIL"))
    private static void fakemod$defineKeys(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.automatavalkyries.gametest.fake.FakeHealthKeys.FAKE_GET_HEALTH_DATA = net.minecraft.network.syncher.SynchedEntityData.defineId(
                com.automatavalkyries.gametest.fake.FakeStatefulMob.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "defineSynchedData", at = @org.spongepowered.asm.mixin.injection.At("TAIL"))
    private void fakemod$defineData(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        ((net.minecraft.world.entity.Entity) (Object) this).getEntityData().define(
                com.automatavalkyries.gametest.fake.FakeHealthKeys.FAKE_GET_HEALTH_DATA, 0.0F);
    }
}
