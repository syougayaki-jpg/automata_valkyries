package com.automatavalkyries.gametest.fake;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * GameTest 用: 時間停止の免除の状態を持つ個体（EndingLibrary の {@code TimeStopEntityData.TIME_STOP_COUNT} の再現）。
 * 同期データ {@link #FAKE_TIME_STOP_COUNT} が正の個体だけ、{@link #STOP_ACTIVE} 中も Tick が回る（{@code FakeTimeStopMixin} の門）。
 * 門が止めるのは {@link #SCOPE_TAG} の付いた個体だけ（同じワールドで並んで走る他のテストを止めない）。
 * 保護対象の模擬は {@link FakeFrozenBoss}、免除を持って動き続ける個体はこのクラスをそのまま使う
 */
public class FakeStopSubject extends Zombie {

    /** 正なら時間停止中も動ける */
    public static final EntityDataAccessor<Integer> FAKE_TIME_STOP_COUNT =
            SynchedEntityData.defineId(FakeStopSubject.class, EntityDataSerializers.INT);
    /** 時間停止の最中か（EndingLibrary の TimeStopUtils.isTimeStop） */
    public static volatile boolean STOP_ACTIVE;
    /** この印の付いた個体だけが時間停止の対象 */
    public static final String SCOPE_TAG = "automata_valkyries_test_scoped_stop";

    public FakeStopSubject(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(FAKE_TIME_STOP_COUNT, 0);
    }
}
