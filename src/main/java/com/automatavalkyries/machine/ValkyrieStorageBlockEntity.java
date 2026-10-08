package com.automatavalkyries.machine;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.network.StorageListPacket;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 兵姫の収納テレポーターの中身: しまった兵姫を丸ごとの NBT で持つ（{@link #CAPACITY} 体まで）。
 * しまうときは兵姫の保護を外して（{@code ValkyrieEntity#releaseAndDiscard}。バックアップも消すので、勝手に戻されたり「失われた」にされたりしない）
 * ワールドから消し、出すときは作り直して保護に載せ直す（{@code ValkyrieProtect#snapshot}）
 */
public class ValkyrieStorageBlockEntity extends BlockEntity {

    public static final int CAPACITY = 9;
    /** しまえる兵姫の、テレポーターからの距離（ブロック） */
    public static final double RANGE = 16.0;
    /** 操作できるプレイヤーの、テレポーターからの距離（ブロック） */
    public static final double REACH = 8.0;
    static final String TAG = "Stored";

    /** しまった兵姫（兵姫の NBT・名前・持ち主） */
    private final List<CompoundTag> stored = new ArrayList<>();

    public ValkyrieStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.VALKYRIE_STORAGE_ENTITY.get(), pos, state);
    }

    public int storedCount() {
        return stored.size();
    }

    /** 操作できる距離にいるか */
    public boolean inReach(ServerPlayer player) {
        return player.level() == level && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= REACH * REACH;
    }

    /** 一覧: 近くの自分の兵姫（しまえる）と、しまった自分の兵姫（出せる） */
    public List<StorageListPacket.Row> rows(ServerPlayer player) {
        List<StorageListPacket.Row> rows = new ArrayList<>();
        for (ValkyrieEntity v : nearby(player)) {
            rows.add(new StorageListPacket.Row(v.getUUID(), v.getDisplayName().getString(), false, DirectRead.health(v), v.getMaxHealth(), v.isShutdown(), v.hasOmegaCore()));
        }
        for (CompoundTag entry : stored) {
            if (!player.getUUID().equals(owner(entry))) continue;
            CompoundTag nbt = entry.getCompound("Entity");
            rows.add(new StorageListPacket.Row(nbt.getUUID("UUID"), entry.getString("Name"), true, nbt.getFloat("Health"), entry.getFloat("MaxHealth"),
                    nbt.getBoolean("Shutdown"), entry.getBoolean("Omega")));
        }
        return rows;
    }

    private List<ValkyrieEntity> nearby(ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) return List.of();
        return server.getEntitiesOfClass(ValkyrieEntity.class, new AABB(worldPosition).inflate(RANGE),
                v -> v.isAlive() && !v.isRemoved() && player.getUUID().equals(v.getOwnerUUID()) && v.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= RANGE * RANGE);
    }

    /** 近くの自分の兵姫 id をしまう。結果の翻訳キーの末尾を返す */
    public String store(ServerPlayer player, UUID id) {
        if (stored.size() >= CAPACITY) return "full";
        ValkyrieEntity v = null;
        for (ValkyrieEntity near : nearby(player)) if (near.getUUID().equals(id)) v = near;
        if (v == null) return "not_found";
        v.stopRiding();
        v.ejectPassengers();
        CompoundTag nbt = new CompoundTag();
        v.saveWithoutId(nbt);
        nbt.putString("id", ModEntities.VALKYRIE.getId().toString());
        float hp = DirectRead.health(v);
        if (Float.isFinite(hp) && hp > 0.0F) nbt.putFloat("Health", hp);
        CompoundTag entry = new CompoundTag();
        entry.put("Entity", nbt);
        entry.putString("Name", v.getDisplayName().getString());
        entry.putUUID("Owner", player.getUUID());
        entry.putFloat("MaxHealth", v.getMaxHealth());
        entry.putBoolean("Omega", v.hasOmegaCore());
        ServerLevel server = (ServerLevel) level;
        effect(server, v.position(), v.getBbHeight());
        v.releaseAndDiscard(); // 保護とバックアップを外して消す（勝手に戻されない）
        stored.add(entry);
        setChanged();
        effect(server, Vec3.atCenterOf(worldPosition), 1.0F);
        return "stored";
    }

    /** しまった自分の兵姫 id を前へ出す。結果の翻訳キーの末尾を返す */
    public String release(ServerPlayer player, UUID id) {
        if (!(level instanceof ServerLevel server)) return "failed";
        int index = -1;
        for (int i = 0; i < stored.size(); i++) {
            CompoundTag entry = stored.get(i);
            if (player.getUUID().equals(owner(entry)) && id.equals(entry.getCompound("Entity").getUUID("UUID"))) index = i;
        }
        if (index < 0) return "not_found";
        if (ValkyrieProtect.ownedCount(server.getServer(), player.getUUID()) >= ValkyrieProtect.MAX_REGISTERED) return "too_many";
        ValkyrieEntity v = ModEntities.VALKYRIE.get().create(server);
        if (v == null) return "failed";
        CompoundTag nbt = stored.get(index).getCompound("Entity").copy();
        nbt.remove("Dimension");
        v.load(nbt);
        if (ValkyrieProtect.findAnywhere(server.getServer(), v.getUUID()) != null) v.setUUID(UUID.randomUUID()); // 同じ兵姫がもういる（念のため）
        Direction facing = getBlockState().getValue(ValkyrieStorageBlock.FACING);
        Vec3 at = Vec3.atBottomCenterOf(worldPosition.relative(facing));
        v.moveTo(at.x, at.y, at.z, facing.toYRot(), 0.0F);
        v.setDeltaMovement(Vec3.ZERO);
        v.fallDistance = 0.0F;
        if (!server.addFreshEntity(v)) return "failed";
        ValkyrieProtect.snapshot(v); // 保護に載せ直す
        stored.remove(index);
        setChanged();
        effect(server, Vec3.atCenterOf(worldPosition), 1.0F);
        effect(server, at, v.getBbHeight());
        return "released";
    }

    private static UUID owner(CompoundTag entry) {
        return entry.hasUUID("Owner") ? entry.getUUID("Owner") : null;
    }

    /** 転送の光と音 */
    private static void effect(ServerLevel level, Vec3 at, float height) {
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + height * 0.5, at.z, 40, 0.4, height * 0.4, 0.4, 0.4);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + height * 0.5, at.z, 12, 0.3, height * 0.3, 0.3, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.8F, 1.3F);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        list.addAll(stored);
        tag.put(TAG, list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stored.clear();
        for (Tag t : tag.getList(TAG, Tag.TAG_COMPOUND)) stored.add((CompoundTag) t);
    }
}
