package com.automatavalkyries.gametest.fake;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;

/** 個別対策のない MOD の HP 固定とボス後始末を再現する。 */
public class FakeCleanupBoss extends Zombie {
    public static final Map<Object, Object> HEALTH_MAP = new HashMap<>();
    public static final Map<Object, Object> PROTECTED_HEALTH_MAP = new HashMap<>();
    public static final Map<Object, Object> UNRELATED = new HashMap<>();
    public final ServerBossEvent bossEvent = new ServerBossEvent(Component.literal("test"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    public final Controller controller = new Controller(this);
    public Object currentTarget;
    public boolean disconnected;
    public boolean combatStopped;

    public FakeCleanupBoss(Level level) { super(EntityType.ZOMBIE, level); }
    public void disconnect() { disconnected = true; }
    public void stopCombatLoop() { combatStopped = true; }

    public static final class Controller {
        public final FakeCleanupBoss proxy;
        public Object attackTarget;
        public boolean kicked;
        public boolean stopped;
        public boolean removed;
        public Controller(FakeCleanupBoss proxy) { this.proxy = proxy; }
        public void kick(Component reason) { kicked = true; }
        public void stopFightingRemovedEnemy() { stopped = true; }
        public void remove() { removed = true; }
    }
}
