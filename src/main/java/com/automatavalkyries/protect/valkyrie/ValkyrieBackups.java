package com.automatavalkyries.protect.valkyrie;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** オートマタヴァルキリー protect のバックアップ（ワールドのセーブ側。もう片方は {@link ValkyrieBackupFiles}） */
public class ValkyrieBackups extends SavedData {

    private static final String NAME = "automata_valkyries_valkyrie_backups";

    /**
     * 兵姫 1 体の記録: データ（NBT）・次元・位置・持ち主・記録したゲーム時刻・完全な保護（オメガコア）か・失われたか
     * （オメガコア無しで消去され、救済通信機での呼び出しを待っている）
     */
    public record Entry(CompoundTag nbt, ResourceKey<Level> dimension, Vec3 pos, @Nullable UUID owner, long time, boolean fullProtect, boolean lost) {

        public Entry asLost() {
            return new Entry(nbt, dimension, pos, owner, time, fullProtect, true);
        }


        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("Data", nbt);
            tag.putString("Dimension", dimension.location().toString());
            tag.putDouble("X", pos.x);
            tag.putDouble("Y", pos.y);
            tag.putDouble("Z", pos.z);
            if (owner != null) tag.putUUID("Owner", owner);
            tag.putLong("Time", time);
            tag.putBoolean("FullProtect", fullProtect);
            tag.putBoolean("Lost", lost);
            return tag;
        }

        public static Entry load(CompoundTag tag) {
            return new Entry(tag.getCompound("Data"),
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension"))),
                    new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                    tag.hasUUID("Owner") ? tag.getUUID("Owner") : null, tag.getLong("Time"), tag.getBoolean("FullProtect"), tag.getBoolean("Lost"));
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    /** 持ち主がバックアップを破棄した兵姫（遠くにいて読み込まれていなかった個体も、読み込まれたら保護を外して消える） */
    private final java.util.Set<UUID> discarded = new java.util.HashSet<>();

    public static ValkyrieBackups get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ValkyrieBackups::load, ValkyrieBackups::new, NAME);
    }

    public static ValkyrieBackups load(CompoundTag tag) {
        ValkyrieBackups b = new ValkyrieBackups();
        for (Tag t : tag.getList("Valkyries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            b.entries.put(c.getUUID("Id"), Entry.load(c));
        }
        for (Tag t : tag.getList("Discarded", Tag.TAG_INT_ARRAY)) b.discarded.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
        return b;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        entries.forEach((id, e) -> {
            CompoundTag c = e.save();
            c.putUUID("Id", id);
            list.add(c);
        });
        tag.put("Valkyries", list);
        ListTag gone = new ListTag();
        for (UUID id : discarded) gone.add(net.minecraft.nbt.NbtUtils.createUUID(id));
        tag.put("Discarded", gone);
        return tag;
    }

    public void put(UUID id, Entry entry) {
        entries.put(id, entry);
        setDirty();
    }

    @Nullable
    public Entry get(UUID id) {
        return entries.get(id);
    }

    public void remove(UUID id) {
        if (entries.remove(id) != null) setDirty();
    }

    public void markDiscarded(UUID id) {
        if (discarded.add(id)) setDirty();
    }

    public boolean isDiscarded(UUID id) {
        return discarded.contains(id);
    }

    public Map<UUID, Entry> all() {
        return Collections.unmodifiableMap(entries);
    }
}
