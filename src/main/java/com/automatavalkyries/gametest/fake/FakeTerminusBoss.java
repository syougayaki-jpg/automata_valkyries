package com.automatavalkyries.gametest.fake;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;

public class FakeTerminusBoss extends Zombie {
    public final Core owned;
    public final Core shared;
    public final Core unrelated;

    public FakeTerminusBoss(ServerLevel level, Entity other) {
        super(EntityType.ZOMBIE, level);
        owned = new Core(this, null);
        owned.oldTarget = other;
        if (other != null) {
            owned.entitiesToAttack.add(other);
        }
        shared = new Core(this, other);
        unrelated = new Core(other, null);
    }

    public static class Core {
        public Entity proxy;
        public Entity other;
        public Entity oldTarget;
        public final java.util.List<Entity> entitiesToAttack = new java.util.ArrayList<>();
        public Integer resurrectLives = 3;
        public Long respawnTimer = 200L;
        public Float shieldDelta = 4F;
        public Double healthDelta = 5D;
        public boolean closed;
        public final java.util.List<Object> protectedWhitelist = new java.util.ArrayList<>();

        public Core(Entity proxy, Entity other) {
            this.proxy = proxy;
            this.other = other;
            protectedWhitelist.add(proxy);
            protectedWhitelist.add("unrelated-entry");
        }

        public void close() { closed = true; }
    }
}
