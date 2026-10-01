package com.automatavalkyries.gametest.fake;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/** GameTest 用: {@link FakeKillState} を持つモブ */
public class FakeStatefulMob extends Zombie implements FakeKillState {

    /** 同期データの死亡の印・守り・残り復活回数（実際の MOD は IMDS の神無敵・Trial Monolith の魂ダメージ等をここに持つ） */
    public static final EntityDataAccessor<Boolean> DATA_CONDEMNED = SynchedEntityData.defineId(FakeStatefulMob.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> DATA_INVULNERABLE = SynchedEntityData.defineId(FakeStatefulMob.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> DATA_REVIVE_COUNT = SynchedEntityData.defineId(FakeStatefulMob.class, EntityDataSerializers.INT);

    private boolean markedForDeath = true;
    private float doomLevel = 5.0F;
    private boolean protectedFlag = true;
    /** 辞書に無い名前（辞書補助の監視の検証用）: 事件の直前にだけ変わる印と、普段から動く値 */
    public boolean hexed;
    public int swayPhase;
    /** 読み出しの偽装（Java エージェントが呼び出し箇所ごと isAlive を書き換えた状態の再現） */
    public boolean spoofDead;

    public FakeStatefulMob(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CONDEMNED, true);
        this.entityData.define(DATA_INVULNERABLE, true);
        this.entityData.define(DATA_REVIVE_COUNT, 3);
    }

    @Override
    public boolean isAlive() {
        return !spoofDead && super.isAlive();
    }

    @Override public boolean fakemod$isMarkedForDeath() { return markedForDeath; }
    @Override public void fakemod$setMarkedForDeath(boolean marked) { markedForDeath = marked; }
    @Override public float fakemod$getDoomLevel() { return doomLevel; }
    @Override public void fakemod$setDoomLevel(float level) { doomLevel = level; }
    @Override public boolean fakemod$isProtected() { return protectedFlag; }
    @Override public void fakemod$setProtected(boolean value) { protectedFlag = value; }
}
