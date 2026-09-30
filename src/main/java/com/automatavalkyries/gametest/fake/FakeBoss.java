package com.automatavalkyries.gametest.fake;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GameTest 用の「架空の MOD のボス」（{@link FakeKillMod} と同じく架空の MOD のクラスとして扱われる）。
 * よくある独自の生命維持を持つ: 保護のフラグ、残り復活回数、本体（コントローラー）、復活待ちの静的な一覧と管理表。
 */
public class FakeBoss extends Zombie {

    /** 復活待ちの一覧（消えたらここから作り直す、という作りの MOD を再現） */
    public static final List<Object> REVIVE_QUEUE = new ArrayList<>();
    /** UUID → 個体の管理表 */
    public static final Map<UUID, FakeBoss> REGISTRY = new HashMap<>();

    public boolean invulnerableToAll = true;
    public boolean canRevive = true;
    public int reviveCount = 3;
    /** 待ち時間は 0 にすると復活が早まるので触られないこと */
    public int respawnDelay = 100;
    public final Controller controller = new Controller(this);

    public FakeBoss(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    /** 管理表と復活待ちの一覧に載せる（ワールドへ入れた後に呼ぶ） */
    public void registerInMod() {
        REGISTRY.put(getUUID(), this);
        REVIVE_QUEUE.add(this);
        REVIVE_QUEUE.add(getUUID());
        REVIVE_QUEUE.add(controller);
    }

    /** 本体。代理（このエンティティ）を指し返す */
    public static final class Controller {
        public final FakeBoss proxy;
        public boolean removed = false;

        Controller(FakeBoss proxy) {
            this.proxy = proxy;
        }

        public void remove() {
            removed = true;
        }
    }

    /** 同じ MOD の手下（ボスに乗る） */
    public static class Minion extends Zombie {
        public Minion(EntityType<? extends Zombie> type, Level level) {
            super(type, level);
        }
    }
}
