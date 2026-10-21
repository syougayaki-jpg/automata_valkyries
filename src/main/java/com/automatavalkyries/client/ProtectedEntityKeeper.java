package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.client.Minecraft;
import com.automatavalkyries.mixin.EntityAccessor;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import com.automatavalkyries.mixin.EntityLookupAccessor;
import com.automatavalkyries.mixin.client.ClientLevelAccessor;
import com.automatavalkyries.mixin.client.TransientEntitySectionManagerAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * 手元（クライアント）の世界から、このMOD のエンティティが通知なしに消されたとき（他 MOD のクライアント側の消去。サーバーでは生きているのに
 * 当たり判定ごと消えて見える。2026-10-22）、サーバーから除去の知らせが来るまで、最後の出現の知らせから作り直して入れ直す。
 * <p>
 * サーバーの正規の除去（撃破・退場・遠く離れた・次元移動）は ClientboundRemoveEntitiesPacket で来るので、それが来たら記録を捨てる。
 * ワールドが変わったとき（次元移動・ログアウト）も捨てる。入れ直しは 5 Tick に 1 回、同じ個体につき 400 回まで。
 * 手元の個体が消去の抜け殻（除去の印・体積 0 の当たり判定・外されたコールバック）になっていたら、誰がやったかを問わず外して作り直す（準汎用）
 * </p>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class ProtectedEntityKeeper {

    private static final int RETRY_TICKS = 5;
    private static final int MAX_RETRIES = 400;

    private ProtectedEntityKeeper() {}

    private static final class Record {
        final ClientLevel level;
        final ClientboundAddEntityPacket packet;
        long lastTry;
        int tries;

        Record(ClientLevel level, ClientboundAddEntityPacket packet) {
            this.level = level;
            this.packet = packet;
        }
    }

    private static final Map<Integer, Record> RECORDS = new HashMap<>();
    private static long clock;

    /** このMOD のエンティティの出現の知らせを受けた（受けた後の手元の世界の状態は問わない） */
    public static void onAdded(ClientLevel level, ClientboundAddEntityPacket packet) {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(packet.getType());
        if (key == null || !AutomataValkyries.MODID.equals(key.getNamespace())) return;
        RECORDS.put(packet.getId(), new Record(level, packet));
    }

    /** サーバーから除去の知らせが来た: 正規の除去なので、入れ直さない */
    public static void onRemoved(Iterable<Integer> ids) {
        for (int id : ids) RECORDS.remove(id);
    }

    /**
     * 検索表の UUID 側（描画の一覧に使われる）だけから外されると、ID 側（getEntity）には残ったまま描画も当たり判定も消える。
     * 外されていたら戻す（2026-10-22。入れ直した個体が、ID では引けるのに一覧に出なかった）
     */
    private static void restoreUuidEntry(ClientLevel level, Entity entity) {
        try {
            @SuppressWarnings("unchecked")
            TransientEntitySectionManagerAccessor<Entity> manager =
                    (TransientEntitySectionManagerAccessor<Entity>) (Object) ((ClientLevelAccessor) level).automata_valkyries$getEntityStorage();
            @SuppressWarnings("unchecked")
            EntityLookupAccessor<Entity> lookup = (EntityLookupAccessor<Entity>) manager.automata_valkyries$getEntityStorage();
            if (lookup.automata_valkyries$getByUuid().get(entity.getUUID()) == entity) return;
            lookup.automata_valkyries$getByUuid().put(entity.getUUID(), entity);
            AutomataValkyries.LOGGER.warn("[client] {} (id {}) was missing from the local world's UUID table (so it was not drawn); put it back",
                    ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()), entity.getId());
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[client] Could not restore the UUID entry of entity id {}: {}", entity.getId(), t.toString());
        }
    }

    /** 生きている個体ならありえない状態（他 MOD の消去の後に残る抜け殻）の理由。普通の個体なら null */
    private static String tamperedReason(Entity entity) {
        EntityAccessor accessor = (EntityAccessor) entity;
        if (accessor.automata_valkyries$getRemovalReason() != null) return "removal reason set";
        if (accessor.automata_valkyries$getLevelCallback() == EntityInLevelCallback.NULL) return "level callback detached";
        AABB box = entity.getBoundingBox();
        if (box.getXsize() < 1.0E-3 && box.getYsize() < 1.0E-3 && box.getZsize() < 1.0E-3) return "bounding box collapsed to a point";
        return null;
    }

    /** 抜け殻を手元の検索表・区画・Tick 一覧から外す（除去の印が付いているので、バニラの除去は何もしない） */
    private static void evict(ClientLevel level, Entity entity) {
        try {
            ClientLevelAccessor clientLevel = (ClientLevelAccessor) level;
            @SuppressWarnings("unchecked")
            TransientEntitySectionManagerAccessor<Entity> manager =
                    (TransientEntitySectionManagerAccessor<Entity>) (Object) clientLevel.automata_valkyries$getEntityStorage();
            @SuppressWarnings("unchecked")
            EntityLookupAccessor<Entity> lookup = (EntityLookupAccessor<Entity>) manager.automata_valkyries$getEntityStorage();
            if (lookup.automata_valkyries$getById().get(entity.getId()) == entity) lookup.automata_valkyries$getById().remove(entity.getId());
            if (lookup.automata_valkyries$getByUuid().get(entity.getUUID()) == entity) lookup.automata_valkyries$getByUuid().remove(entity.getUUID());
            EntitySectionStorage<Entity> sections = manager.automata_valkyries$getSectionStorage();
            for (long chunk : sections.getAllChunksWithExistingSections().toLongArray()) {
                for (EntitySection<Entity> section : sections.getExistingSectionsInChunk(chunk).toList()) section.remove(entity);
            }
            com.automatavalkyries.annihilate.TickListEviction.remove(clientLevel.automata_valkyries$getTickingEntities(), entity.getId());
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[client] Could not evict the shell of entity id {}: {}", entity.getId(), t.toString());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RECORDS.isEmpty()) return;
        clock++;
        ClientLevel current = Minecraft.getInstance().level;
        RECORDS.entrySet().removeIf(entry -> {
            Record record = entry.getValue();
            if (record.level != current || record.tries >= MAX_RETRIES) return true; // ワールドが変わった / 諦める
            Entity present = current.getEntity(entry.getKey());
            if (present != null) {
                String tamper = tamperedReason(present);
                if (tamper == null) {
                    restoreUuidEntry(current, present);
                    return false;
                }
                // 消された後の抜け殻（除去の印・体積 0 の当たり判定・外されたコールバック）。誰がやったかは問わず、外して作り直す
                AutomataValkyries.LOGGER.warn("[client] {} (id {}) in the local world is a shell left by a foreign removal ({}); evicting it and adding it again",
                        ForgeRegistries.ENTITY_TYPES.getKey(present.getType()), entry.getKey(), tamper);
                evict(current, present);
            }
            if (clock - record.lastTry < RETRY_TICKS) return false;
            record.lastTry = clock;
            record.tries++;
            try {
                Entity entity = record.packet.getType().create(current);
                if (entity == null) return false;
                entity.recreateFromPacket(record.packet);
                current.putNonPlayerEntity(record.packet.getId(), entity);
                if (record.tries <= 3 || record.tries % 40 == 0) {
                    AutomataValkyries.LOGGER.warn("[client] {} (id {}) vanished from the local world without a removal packet; added it again (attempt {}, now present = {})",
                            ForgeRegistries.ENTITY_TYPES.getKey(record.packet.getType()), entry.getKey(), record.tries, current.getEntity(entry.getKey()) != null);
                }
            } catch (Throwable t) {
                AutomataValkyries.LOGGER.warn("[client] Could not add entity id {} again: {}", entry.getKey(), t.toString());
            }
            return false;
        });
    }
}
