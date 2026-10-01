package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * 保護対象の状態をエンティティとは別にワールドデータへ持つ登録簿（ディメンションごと）。
 * <p>
 * エンティティが丸ごと失われたり（ワールドの一覧から外されて保存もされない）、エンティティ自身のデータを壊されたりしても、
 * ここに残した「最後に正常だった写し」から同じ UUID の個体を作り直せる。テッセラクトビースト（The Trial Monolith）と同じく、
 * 状態の正本をワールドの外側に置く考え方。
 * </p>
 * <ul>
 *   <li>多重化: 同じ内容を 2 つのワールドデータ（{@link #PRIMARY_NAME} と {@link #BACKUP_NAME}）に書く</li>
 *   <li>改ざん検知: 記録ごとに CRC32 を付け、読み込み時に合わなければその記録を捨ててもう一方を使う。
 *       暗号ではなく「壊れていないか」の確認（鍵は MOD 内に置くしかないので、秘匿には意味がない）</li>
 * </ul>
 */
public final class EprotectRegistry {

    static final String PRIMARY_NAME = AutomataValkyries.MODID + "_eprotect";
    static final String BACKUP_NAME = AutomataValkyries.MODID + "_eprotect_backup";

    /** 記録の状態。ACTIVE の記録だけが復元の対象 */
    public enum State { ACTIVE, DEFEATED }

    /** 1 体分の記録 */
    public static final class Entry {
        final UUID uuid;
        State state;
        CompoundTag snapshot;
        float health;
        Vec3 safePos;
        /** 復元の連発を抑える（メモリ上だけ。保存しない） */
        long lastRestoreTick = Long.MIN_VALUE;
        int restoresInWindow = 0;
        long restoreWindowStart = Long.MIN_VALUE;

        Entry(UUID uuid, State state, CompoundTag snapshot, float health, Vec3 safePos) {
            this.uuid = uuid;
            this.state = state;
            this.snapshot = snapshot;
            this.health = health;
            this.safePos = safePos;
        }

        public UUID uuid() { return uuid; }
        public State state() { return state; }
        public CompoundTag snapshot() { return snapshot.copy(); }
        public Vec3 safePos() { return safePos; }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Store primary;
    private final Store backup;

    private EprotectRegistry(Store primary, Store backup) {
        this.primary = primary;
        this.backup = backup;
        // 正本が壊れていたら予備を使う（記録ごと）
        entries.putAll(backup.loaded);
        entries.putAll(primary.loaded);
        if (!primary.loaded.keySet().containsAll(backup.loaded.keySet()) || primary.rejected > 0 || backup.rejected > 0) {
            AutomataValkyries.LOGGER.warn("[eprotect] Registry integrity: primary {} ok / {} rejected, backup {} ok / {} rejected",
                    primary.loaded.size(), primary.rejected, backup.loaded.size(), backup.rejected);
            markDirty(); // 壊れていた側を正しい内容で書き直す
        }
        primary.owner = this;
        backup.owner = this;
    }

    /** そのディメンションの登録簿（無ければ作る） */
    public static EprotectRegistry get(ServerLevel level) {
        Store primary = level.getDataStorage().computeIfAbsent(Store::load, Store::new, PRIMARY_NAME);
        Store backup = level.getDataStorage().computeIfAbsent(Store::load, Store::new, BACKUP_NAME);
        if (primary.owner != null && primary.owner == backup.owner) return primary.owner;
        return new EprotectRegistry(primary, backup);
    }

    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    @Nullable
    public Entry find(UUID uuid) {
        return entries.get(uuid);
    }

    /** 最後に正常だった状態を写し取る（ワールドに正しく載っている時だけ呼ぶこと） */
    public void record(LivingEntity entity, Vec3 safePos) {
        CompoundTag snapshot = new CompoundTag();
        if (!entity.saveAsPassenger(snapshot)) return; // 消去中など保存できない状態なら前回の記録を残す
        Entry entry = entries.get(entity.getUUID());
        if (entry == null) {
            entries.put(entity.getUUID(), new Entry(entity.getUUID(), State.ACTIVE, snapshot, DirectRead.health(entity), safePos));
        } else {
            entry.state = State.ACTIVE;
            entry.snapshot = snapshot;
            entry.health = DirectRead.health(entity);
            entry.safePos = safePos;
        }
        markDirty();
    }

    /** 正規に倒された（撃破演出中）。以後は復元しない */
    public void markDefeated(UUID uuid) {
        Entry entry = entries.get(uuid);
        if (entry != null && entry.state != State.DEFEATED) {
            entry.state = State.DEFEATED;
            markDirty();
        }
    }

    /** 正規の撃破完了・正規退場・次元移動の元側。記録を消す */
    public void forget(UUID uuid) {
        if (entries.remove(uuid) != null) markDirty();
    }

    private void markDirty() {
        primary.setDirty();
        backup.setDirty();
    }

    // ---- 保存形式 ------------------------------------------------------------

    private static final String ENTRIES = "Entries";
    private static final String CHECKSUM = "Checksum";

    public static CompoundTag encode(Entry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("UUID", entry.uuid);
        tag.putString("State", entry.state.name());
        tag.put("Snapshot", entry.snapshot.copy());
        tag.putFloat("Health", entry.health);
        tag.putDouble("X", entry.safePos.x);
        tag.putDouble("Y", entry.safePos.y);
        tag.putDouble("Z", entry.safePos.z);
        tag.putLong(CHECKSUM, checksum(tag));
        return tag;
    }

    /** 記録を読み戻す。チェックサムが合わない・欠けている記録は null（改ざん・破損） */
    @Nullable
    public static Entry decode(CompoundTag tag) {
        if (!tag.contains(CHECKSUM, Tag.TAG_LONG)) return null;
        long stored = tag.getLong(CHECKSUM);
        CompoundTag body = tag.copy();
        body.remove(CHECKSUM);
        if (checksum(body) != stored) return null;
        try {
            return new Entry(tag.getUUID("UUID"), State.valueOf(tag.getString("State")), tag.getCompound("Snapshot"),
                    tag.getFloat("Health"), new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** キー順に整列した文字列表現の CRC32（CompoundTag の内部順序に左右されない） */
    static long checksum(CompoundTag body) {
        CompoundTag copy = body.copy();
        copy.remove(CHECKSUM);
        CRC32 crc = new CRC32();
        crc.update(copy.getAsString().getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }

    /** ワールドデータ 1 本分。正本と予備で同じクラスを使い、内容は登録簿から書き出す */
    static final class Store extends SavedData {
        final Map<UUID, Entry> loaded = new LinkedHashMap<>();
        int rejected = 0;
        @Nullable
        EprotectRegistry owner;

        Store() {}

        static Store load(CompoundTag tag) {
            Store store = new Store();
            ListTag list = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                Entry entry = decode(list.getCompound(i));
                if (entry == null) {
                    store.rejected++;
                } else {
                    store.loaded.put(entry.uuid, entry);
                }
            }
            return store;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            if (owner != null) {
                for (Entry entry : owner.entries.values()) list.add(encode(entry));
            }
            tag.put(ENTRIES, list);
            return tag;
        }
    }

    /** 記録から同じ UUID の個体を作る（ワールドにはまだ入れない） */
    @Nullable
    static Entity recreate(ServerLevel level, Entry entry) {
        return net.minecraft.world.entity.EntityType.create(entry.snapshot(), level)
                .map(e -> {
                    e.moveTo(entry.safePos.x, entry.safePos.y, entry.safePos.z, e.getYRot(), e.getXRot());
                    e.setDeltaMovement(Vec3.ZERO);
                    return e;
                })
                .orElse(null);
    }
}
