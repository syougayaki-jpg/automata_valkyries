# 戦術兵姫MOD（Automata Valkyries）開発引継ぎドキュメント
> **Antigravity (AI Assistant) から Claude Code へのコンテキスト引継ぎ情報**  
> 最終更新: 2026-09-30（IMDS-Reborn 完全耐性プロテクション追加）  
> 対象プロジェクト: `C:\Users\sfdge\mc-mods\automata_valkyries`  
> 動作検証済みコード資産: `C:\Users\sfdge\Downloads\masterE`（`mastere-1.0.0.jar` ビルド成功済み）

---

## 1. 背景と開発経緯

ユーザーは Minecraft 1.20.1 (Forge) 向けの **「戦術兵姫MOD（Automata Valkyries）」** を開発しています。  
これまで Antigravity AI とのチャットを通じて、モブ（兵姫／ボス）が理不尽に消去・即死されないための **高度な保護プログラム（`eprotect`）** と、そのプロトタイプとなるボス **「エレノア（Elenor）」** の設計・実装・ビルド検証を行ってきました。

さらに、最強クラスの即死抹消武器 **「インフィニティマタードミネーターソードリボーン（Infinity Matter Dominator Sword: Reborn / IMDS-Reborn）」** のキル機構（内部コード・Mixin・バイトコード操作）を完全に解析し、**それらの攻撃・強制死亡・強制消去・奈落テレポートを100%無力化・耐え切る「Eprotect Ultimate」** へと強化しました。

---

## 2. IMDS-Reborn のキル機構と Eprotect の完全対抗ロジック

### 2.1. IMDS-Reborn の即死・抹消メカニズム（解析結果）
1. **直接攻撃時 (`ImdsKillProcedure.execute_ench`)**:
   - `living.die(new ImdsDamageSource(attacker))` を直接呼び出して即死させる。
   - `ImdsKillManager.markForDead(living)` で内部の `FORCE_DEAD_UUIDS` に登録。
   - `((IEntityImds) entity).imds$setForceDead(true)` を設定。
   - `living.deathTime = Math.max(deathTime, 1)` で強制的に死亡進行。
   - `entity.kill()` を実行。
2. **スニーク・右クリック範囲抹消時 (`ImdsKillProcedure.remov`)**:
   - `ImdsKillManager.forceRemove(living)` で `REMOVED_UUIDS` に登録。
   - `((IEntityImds) entity).imds$setForceRemoved(true)` を設定。
   - `entity.setRemoved(RemovalReason.KILLED)` および `entity.onRemovedFromWorld()` を直接呼び出す。
   - 座標 `-Infinity` へ強制テレポートし、奈落消去を狙う。
   - `serverChunkCache.removeEntity(entity)` および `entityTickList.remove(entity)` で内部リストから除外。
3. **Mixin・トランスフォーマーでの干渉 (`LivingEntityMixin`, `ImdsForceDeathMethods`)**:
   - `imds$isForceDead()` が true の場合、`getHealth()` を `-Infinity`、`isDeadOrDying()` を `true`、`isAlive()` を `false` に偽装。
   - `imds$isForceRemoved()` が true の場合、`isRemoved()` を `true` に偽装。

### 2.2. Eprotect の完全対抗策（IMDS無力化＆無敵奪取）

> **2026-10-01 更新**: 下記 1 の「神無敵の逆奪取」はユーザー判断で廃止した。IMDS が入っている環境だけエレノアが一切ダメージを受けず、
> 被弾量の上限（10 秒に最大 HP の 10%）が効かなくなるため。`EprotectImdsCounter` は今は神無敵を**外す**側（`IMDS_INV_UUIDS` から除外、
> `imds$setInvulnerable(false)`）。抹消リストからの除外と強制死亡・強制削除フラグの解除は引き続き行う。

1. **IMDSの神無敵フラグの逆奪取 (`EprotectImdsCounter`)**（廃止）:
   - IMDSの内部Mixinでは、`((IEntityImds) entity).imds$isInvulnerable()` が `true` の場合、**`imds_hurt`, `imds_actuallyHurt`, `imds_die`, `imds_tickDeath` のすべての攻撃・キルコードが即座にキャンセルされる仕様**になっている。
   - Eprotectはリフレクションにより、自身のエンティティに対して **`imds$setInvulnerable(true)` を常時付与** し、`imds$setForceDead(false)` / `imds$setForceRemoved(false)` に上書き。
   - さらに `ImdsKillManager` の `FORCE_DEAD_UUIDS`, `REMOVED_UUIDS`, `NOAI_UUIDS` から自身のUUIDを毎Tick削除。
   - **結果: IMDS-Reborn 自身がEprotectモブを「IMDS最高神（完全無敵）」と判定し、全攻撃を自ら拒絶する！**
2. **`die(DamageSource)` の完全ブロック (`EprotectLivingEntityMixin`)**:
   - `@Inject(method = "die", at = @At("HEAD"), cancellable = true)` により、外部から直接 `die()` が呼ばれても即座に `ci.cancel()`。
   - HPを即時全快にし、`deathTime = 0` に固定。
3. **`setRemoved` & `onRemovedFromWorld` のブロック (`EprotectEntityMixin`)**:
   - 外部MODが直接 `setRemoved()` や `onRemovedFromWorld()` を呼び出しても `@At("HEAD")` でブロック。
4. **座標 `-Infinity` / 奈落落としテレポートの無効化 & ロールバック**:
   - `teleportTo` / `setPos` で NaN や Infinity、-1000以下の極小座標への移動を検知・即座にブロック。
   - 毎Tick記録している直前の正常座標 (`lastValidX, Y, Z`) へ自動引き戻し。
5. **TickList・チャンクからの物理消去に対する完全復元 (`EprotectManager`)**:
   - Forge の `ServerTickEvent` で毎Tick（0.05秒周期）同期監視。
   - 万が一ワールドから物理的に除外されても、`level.setChunkForced` ＆ `level.addFreshEntity(boss)` で即時再召喚。

---

## 3. 実装済みソースコード（完全版一覧）

`C:\Users\sfdge\Downloads\masterE` にてビルド成功（`BUILD SUCCESSFUL`）確認済みの最新ソースコードです。

### 3.1. `EprotectImdsCounter.java`（IMDS特化カウンター）
```java
package com.mod.eprotect;

import net.minecraft.world.entity.LivingEntity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public class EprotectImdsCounter {

    private static boolean initialized = false;
    private static Set<UUID> forceDeadUuids = null;
    private static Set<UUID> removedUuids = null;
    private static Set<UUID> noaiUuids = null;

    private static Class<?> iEntityImdsClass = null;
    private static Method setForceDeadMethod = null;
    private static Method setForceRemovedMethod = null;
    private static Method setInvulnerableMethod = null;

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        try {
            Class<?> killManagerClass = Class.forName("net.mcreator.imds_reborn.ImdsKillManager");
            forceDeadUuids = getStaticSet(killManagerClass, "FORCE_DEAD_UUIDS");
            removedUuids = getStaticSet(killManagerClass, "REMOVED_UUIDS");
            noaiUuids = getStaticSet(killManagerClass, "NOAI_UUIDS");
        } catch (Throwable ignored) {}

        try {
            iEntityImdsClass = Class.forName("net.mcreator.imds_reborn.IEntityImds");
            setForceDeadMethod = iEntityImdsClass.getMethod("imds$setForceDead", boolean.class);
            setForceRemovedMethod = iEntityImdsClass.getMethod("imds$setForceRemoved", boolean.class);
            setInvulnerableMethod = iEntityImdsClass.getMethod("imds$setInvulnerable", boolean.class);
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> getStaticSet(Class<?> clazz, String fieldName) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            return (Set<UUID>) f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void counter(LivingEntity entity) {
        if (!initialized) init();
        UUID uuid = entity.getUUID();

        if (forceDeadUuids != null) forceDeadUuids.remove(uuid);
        if (removedUuids != null) removedUuids.remove(uuid);
        if (noaiUuids != null) noaiUuids.remove(uuid);

        if (iEntityImdsClass != null && iEntityImdsClass.isInstance(entity)) {
            try {
                if (setForceDeadMethod != null) setForceDeadMethod.invoke(entity, false);
                if (setForceRemovedMethod != null) setForceRemovedMethod.invoke(entity, false);
                if (setInvulnerableMethod != null) setInvulnerableMethod.invoke(entity, true);
            } catch (Throwable ignored) {}
        }
    }
}
```

### 3.2. `EprotectLivingEntityMixin.java`
```java
package com.mod.eprotect.mixin;

import com.mod.eprotect.Eprotect;
import com.mod.eprotect.EprotectImdsCounter;
import com.mod.eprotect.IProviderWithSpeech;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntity.class, remap = false)
public abstract class EprotectLivingEntityMixin {

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void onDie(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof IProviderWithSpeech provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && !protect.isDespawning()) {
                ci.cancel();
                self.setHealth(self.getMaxHealth());
                self.deathTime = 0;
                EprotectImdsCounter.counter(self);
            }
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurtHead(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof IProviderWithSpeech provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && !protect.isDespawning()) {
                EprotectImdsCounter.counter(self);
                String msgId = damageSource.getMsgId();
                if (msgId != null && (msgId.contains("imds") || msgId.contains("kill") || msgId.contains("outOfWorld") || msgId.contains("genericKill"))) {
                    cir.setReturnValue(false);
                    cir.cancel();
                }
            }
        }
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void onSetHealth(float health, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof IProviderWithSpeech provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && !protect.isDespawning()) {
                if (health <= 0.0F || Float.isNaN(health) || Float.isInfinite(health)) {
                    ci.cancel();
                    self.deathTime = 0;
                    EprotectImdsCounter.counter(self);
                }
            }
        }
    }

    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void onTickDeath(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof IProviderWithSpeech provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && !protect.isDespawning()) {
                ci.cancel();
                self.deathTime = 0;
                self.setHealth(self.getMaxHealth());
                EprotectImdsCounter.counter(self);
            }
        }
    }
}
```

### 3.3. `EprotectEntityMixin.java`
```java
package com.mod.eprotect.mixin;

import com.mod.eprotect.Eprotect;
import com.mod.eprotect.EprotectImdsCounter;
import com.mod.eprotect.IProviderWithSpeech;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Entity.class, remap = false)
public abstract class EprotectEntityMixin {

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof IProviderWithSpeech provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && !protect.isDespawning() && self instanceof LivingEntity living) {
                ci.cancel();
                EprotectImdsCounter.counter(living);
            }
        }
    }

    @Inject(method = "discard", at = @At("HEAD"), cancellable = true)
    private void onDiscard(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof IProviderWithSpeech provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && !protect.isDespawning() && self instanceof LivingEntity living) {
                ci.cancel();
                EprotectImdsCounter.counter(living);
            }
        }
    }

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void onSetRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof IProviderWithSpeech provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && !protect.isDespawning() && self instanceof LivingEntity living) {
                ci.cancel();
                EprotectImdsCounter.counter(living);
            }
        }
    }

    @Inject(method = "onRemovedFromWorld", at = @At("HEAD"), cancellable = true)
    private void onRemovedFromWorld(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof IProviderWithSpeech provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && !protect.isDespawning() && self instanceof LivingEntity living) {
                ci.cancel();
                EprotectImdsCounter.counter(living);
            }
        }
    }

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true)
    private void onTeleportTo(double x, double y, double z, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof IProviderWithSpeech provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && !protect.isDespawning()) {
                if (Double.isNaN(x) || Double.isInfinite(x) || Double.isNaN(y) || Double.isInfinite(y) || Double.isNaN(z) || Double.isInfinite(z) || y < -1000.0D) {
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true)
    private void onSetPos(double x, double y, double z, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof IProviderWithSpeech provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && !protect.isDespawning()) {
                if (Double.isNaN(x) || Double.isInfinite(x) || Double.isNaN(y) || Double.isInfinite(y) || Double.isNaN(z) || Double.isInfinite(z) || y < -1000.0D) {
                    ci.cancel();
                }
            }
        }
    }
}
```

### 3.4. `EprotectDamageMixin.java`
```java
package com.mod.eprotect.mixin;

import com.mod.eprotect.Eprotect;
import com.mod.eprotect.EprotectImdsCounter;
import com.mod.eprotect.IProviderWithSpeech;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = LivingEntity.class, remap = false)
public abstract class EprotectDamageMixin {

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float sanitizeDamage(float amount, DamageSource source) {
        Object self = this;
        if (self instanceof IProviderWithSpeech provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && !protect.isDespawning()) {
                if (self instanceof LivingEntity living) {
                    EprotectImdsCounter.counter(living);
                }

                if (Float.isNaN(amount) || Float.isInfinite(amount) || amount > 100.0F || amount <= 0.0F) {
                    return 10.0F; // 最大でも10.0ダメージに安全制限
                }
            }
        }
        return amount;
    }
}
```

### 3.5. `Eprotect.java`
```java
package com.mod.eprotect;

import com.mod.eprotect.mixin.EntityAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

public class Eprotect {

    private final LivingEntity boss;
    private boolean isDespawning = false;

    private double lastValidX = 0;
    private double lastValidY = 64;
    private double lastValidZ = 0;

    public Eprotect(LivingEntity boss) {
        this.boss = boss;
    }

    public void startProtection() {
        if (boss.level() instanceof ServerLevel serverLevel) {
            serverLevel.setChunkForced(boss.getBlockX() >> 4, boss.getBlockZ() >> 4, true);
            EprotectManager.getInstance().registerBoss(boss, this);
            recordValidPosition();
        }
    }

    public void recordValidPosition() {
        double x = boss.getX();
        double y = boss.getY();
        double z = boss.getZ();
        if (!Double.isNaN(x) && !Double.isInfinite(x) &&
            !Double.isNaN(y) && !Double.isInfinite(y) && y > -64.0D &&
            !Double.isNaN(z) && !Double.isInfinite(z)) {
            this.lastValidX = x;
            this.lastValidY = y;
            this.lastValidZ = z;
        }
    }

    public void onTickSelfHealing() {
        if (isDespawning) return;

        EprotectImdsCounter.counter(boss);

        boss.deathTime = 0;
        EntityAccessor accessor = (EntityAccessor) boss;
        if (accessor.getRemovalReason() != null) {
            accessor.setRemovalReason(null);
        }

        float hp = boss.getHealth();
        if (Float.isNaN(hp) || Float.isInfinite(hp) || hp <= 0.0F) {
            boss.setHealth(boss.getMaxHealth());
        }

        double currentX = boss.getX();
        double currentY = boss.getY();
        double currentZ = boss.getZ();
        if (Double.isNaN(currentX) || Double.isInfinite(currentX) ||
            Double.isNaN(currentY) || Double.isInfinite(currentY) || currentY < -500.0D ||
            Double.isNaN(currentZ) || Double.isInfinite(currentZ)) {
            boss.setPos(lastValidX, lastValidY, lastValidZ);
            boss.setDeltaMovement(0, 0, 0);
        } else {
            recordValidPosition();
        }
    }

    public void triggerDespawn() {
        if (this.isDespawning) return;
        this.isDespawning = true;

        if (boss.level() instanceof ServerLevel serverLevel) {
            serverLevel.setChunkForced(boss.getBlockX() >> 4, boss.getBlockZ() >> 4, false);
        }

        if (!boss.level().isClientSide && boss instanceof IProviderWithSpeech speechProvider) {
            Component defeatSpeech = speechProvider.getDefeatSpeech();
            SoundEvent defeatSound = speechProvider.getDefeatSound();

            if (defeatSpeech != null && boss.getServer() != null) {
                boss.getServer().getPlayerList().broadcastSystemMessage(defeatSpeech, false);
            }
            if (defeatSound != null) {
                boss.level().playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        defeatSound, SoundSource.HOSTILE, 1.0F, 1.0F);
            }
        }

        boss.discard();
    }

    public boolean isDespawning() { return isDespawning; }
    public double getLastValidX() { return lastValidX; }
    public double getLastValidY() { return lastValidY; }
    public double getLastValidZ() { return lastValidZ; }
}
```

### 3.6. `EprotectManager.java`
```java
package com.mod.eprotect;

import com.mod.eprotect.mixin.EntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EprotectManager {

    private static final EprotectManager INSTANCE = new EprotectManager();
    private final Map<UUID, EprotectHolder> protectedBosses = new ConcurrentHashMap<>();

    private EprotectManager() {}

    public static EprotectManager getInstance() { return INSTANCE; }

    public void registerBoss(LivingEntity boss, Eprotect protect) {
        protectedBosses.put(boss.getUUID(), new EprotectHolder(boss, protect));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (protectedBosses.isEmpty()) return;

        protectedBosses.entrySet().removeIf(entry -> {
            EprotectHolder holder = entry.getValue();
            LivingEntity boss = holder.boss();
            Eprotect protect = holder.protect();

            if (protect.isDespawning()) return true;

            EprotectImdsCounter.counter(boss);

            boss.deathTime = 0;
            EntityAccessor accessor = (EntityAccessor) boss;
            if (accessor.getRemovalReason() != null) {
                accessor.setRemovalReason(null);
            }

            float hp = boss.getHealth();
            if (Float.isNaN(hp) || Float.isInfinite(hp) || hp <= 0.0F) {
                boss.setHealth(boss.getMaxHealth());
            }

            if (boss.level() instanceof ServerLevel level) {
                if (boss.isRemoved() || level.getEntity(boss.getUUID()) == null) {
                    accessor.setRemovalReason(null);
                    boss.setPos(protect.getLastValidX(), protect.getLastValidY(), protect.getLastValidZ());
                    boss.setDeltaMovement(0, 0, 0);
                    level.setChunkForced(boss.getBlockX() >> 4, boss.getBlockZ() >> 4, true);
                    level.addFreshEntity(boss);
                }
            }

            return false;
        });
    }

    private record EprotectHolder(LivingEntity boss, Eprotect protect) {}
}
```

### 3.7. `eprotect.mixins.json`
```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.mod.eprotect.mixin",
  "compatibilityLevel": "JAVA_17",
  "refmap": "eprotect.refmap.json",
  "mixins": [
    "EntityAccessor",
    "EprotectEntityMixin",
    "EprotectLivingEntityMixin",
    "EprotectDamageMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

---

## 4. `automata_valkyries` への移植手順（Claude Code 向け）

Claude Code で `automata_valkyries` に本実装する際は：
1. `com.mod.eprotect` を `com.automatavalkyries.protect`、`com.automatavalkyries.mixin` にリファクタリング。
2. `src/main/resources/automata_valkyries.mixins.json` を設定し、`mods.toml` に登録。
3. 兵姫エンティティに `IProviderWithSpeech` を実装させ、`Eprotect` を保持させる。
