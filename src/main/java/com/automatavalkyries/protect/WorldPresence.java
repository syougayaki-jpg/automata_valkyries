package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.mixin.ChunkMapAccessor;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.EntityManagerAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;

/**
 * 保護対象がワールドに正しく載っているかの点検と、壊されていた場合の「入り直し」。
 * <p>
 * 即死 MOD は removalReason・levelCallback・座標などのフィールドや、ワールドの各一覧（UUID 登録・検索表・区画・Tick 一覧・追跡）を
 * 直接書き換えて消す。壊された箇所を 1 つずつ直すと、直す順番や直し漏れで中途半端な状態が残る（「already tracked」での
 * クラッシュや再登録の失敗が実際に起きた）。そこで、点検で 1 つでも崩れていたら、古い参照をすべて外してフィールドを
 * 「ワールドに未追加」の状態へ戻し、バニラの {@code addFreshEntity} で入り直させる。新しい接続・一覧への登録・追跡は
 * バニラが整合の取れた形で作り直す。
 * </p>
 * <p>
 * 点検は他 MOD の ASM に偽装されうる getter（isRemoved() 等）を使わず、フィールドと一覧を直接読む。
 * </p>
 */
public final class WorldPresence {

    /** 崩れていた条件（ログと GameTest の診断用） */
    public enum Issue {
        REMOVAL_FLAG, UNSAFE_POSITION, FOREIGN_CALLBACK,
        MISSING_UUID, MISSING_LOOKUP, MISSING_SECTION, MISSING_TICK_LIST, MISSING_TRACKER
    }

    /** 入り直しの結果 */
    enum Outcome { REJOINED, REPLACED, FAILED }

    private WorldPresence() {}

    /** ワールドに正しく載っているかを点検する（読み取りのみ） */
    public static EnumSet<Issue> inspect(ServerLevel level, LivingEntity entity) {
        EnumSet<Issue> issues = EnumSet.noneOf(Issue.class);
        EntityAccessor fields = (EntityAccessor) entity;
        Entity.RemovalReason reason = fields.automata_valkyries$getRemovalReason();
        if (reason != null && reason.shouldDestroy()) issues.add(Issue.REMOVAL_FLAG);
        if (!Eprotect.isSafePosition(entity, entity.getX(), entity.getY(), entity.getZ())) issues.add(Issue.UNSAFE_POSITION);
        if (!isVanillaCallback(fields.automata_valkyries$getLevelCallback())) issues.add(Issue.FOREIGN_CALLBACK);

        EntityManagerAccessor<Entity> manager = manager(level);
        if (!manager.automata_valkyries$getKnownUuids().contains(entity.getUUID())) issues.add(Issue.MISSING_UUID);
        var lookup = manager.automata_valkyries$getVisibleEntityStorage();
        if (lookup.getEntity(entity.getUUID()) != entity || lookup.getEntity(entity.getId()) != entity) {
            issues.add(Issue.MISSING_LOOKUP);
        }
        EntitySection<Entity> section = manager.automata_valkyries$getSectionStorage()
                .getSection(SectionPos.asLong(entity.blockPosition()));
        if (section == null || section.getEntities().noneMatch(e -> e == entity)) issues.add(Issue.MISSING_SECTION);
        if (level.isPositionEntityTicking(entity.blockPosition())
                && !((ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList().contains(entity)) {
            issues.add(Issue.MISSING_TICK_LIST);
        }
        if (!((ChunkMapAccessor) level.getChunkSource().chunkMap).automata_valkyries$getEntityMap().containsKey(entity.getId())) {
            issues.add(Issue.MISSING_TRACKER);
        }
        return issues;
    }

    /**
     * バニラの正規ルートで入り直させる。他 MOD に入り直し自体を拒否された場合は、同じ UUID・同じデータの新しい個体と差し替える。
     *
     * @param safePos 座標が壊されていたときに戻す先（最後に確認した正常な座標）
     */
    static Outcome rejoin(ServerLevel level, LivingEntity entity, @Nullable Vec3 safePos) {
        try {
            detach(level, entity, safePos);
            if (level.addFreshEntity(entity)) return Outcome.REJOINED;
        } catch (RuntimeException e) {
            // 他 MOD が一覧を中途半端に壊していると入り直し自体が失敗しうる。サーバーを落とさず差し替えを試す
            AutomataValkyries.LOGGER.error("[eprotect] Rejoin of {} threw", entity, e);
        }
        return replaceWithClone(level, entity) ? Outcome.REPLACED : Outcome.FAILED;
    }

    /**
     * 自分への古い参照をワールドのすべての一覧・区画・追跡から外し、フィールドを「ワールドに未追加」の状態へ戻す。
     * 追跡を残したまま入り直すと「Entity is already tracked!」でサーバーが落ちるので、追跡も必ず外す。
     */
    private static void detach(ServerLevel level, LivingEntity entity, @Nullable Vec3 safePos) {
        EntityManagerAccessor<Entity> manager = manager(level);
        level.getChunkSource().removeEntity(entity); // 追跡（未追跡なら何もしない）とクライアントへの消去通知
        ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList().remove(entity);
        manager.automata_valkyries$getVisibleEntityStorage().remove(entity);
        manager.automata_valkyries$getKnownUuids().remove(entity.getUUID());
        EntitySectionStorage<Entity> sections = manager.automata_valkyries$getSectionStorage();
        removeFromSection(sections, SectionPos.asLong(entity.blockPosition()), entity);
        if (safePos != null) removeFromSection(sections, SectionPos.asLong(net.minecraft.core.BlockPos.containing(safePos)), entity);

        EntityAccessor fields = (EntityAccessor) entity;
        fields.automata_valkyries$setRemovalReason(null);
        fields.automata_valkyries$setLevelCallback(EntityInLevelCallback.NULL);
        fields.automata_valkyries$setAddedToWorld(false);
        ((LivingEntityAccessor) entity).automata_valkyries$setDead(false);
        entity.reviveCaps();
        if (safePos != null && !Eprotect.isSafePosition(entity, entity.getX(), entity.getY(), entity.getZ())) {
            entity.setPos(safePos.x, safePos.y, safePos.z);
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static void removeFromSection(EntitySectionStorage<Entity> sections, long key, Entity entity) {
        EntitySection<Entity> section = sections.getSection(key);
        if (section != null) section.remove(entity);
    }

    /**
     * 最後の手段: 同じ UUID・同じ保存データの新しい個体を生成して差し替える。
     * 古い個体はワールドのどこにも載っていない状態で保護を解き、以後は新しい個体の保護に任せる。
     */
    private static boolean replaceWithClone(ServerLevel level, LivingEntity entity) {
        // まだどこかに同じ UUID が残っているなら差し替えると複製になる
        if (level.getEntity(entity.getUUID()) != null || manager(level).automata_valkyries$getKnownUuids().contains(entity.getUUID())) {
            return false;
        }
        // 写しの元: ワールドデータの登録簿にある「最後に正常だった写し」を優先する。
        // 古い個体のデータ自体が壊されていることがあるので、生きている個体から取るのは登録簿に無い時だけ
        Entity clone = null;
        EprotectRegistry.Entry entry = EprotectRegistry.get(level).find(entity.getUUID());
        if (entry != null && entry.state() == EprotectRegistry.State.ACTIVE) {
            clone = EprotectRegistry.recreate(level, entry);
        }
        if (clone == null) {
            CompoundTag tag = new CompoundTag();
            if (!entity.saveAsPassenger(tag)) return false;
            Optional<Entity> created = EntityType.create(tag, level);
            if (created.isEmpty()) return false;
            clone = created.get();
        }
        try {
            if (!level.addFreshEntity(clone)) return false;
        } catch (RuntimeException e) {
            AutomataValkyries.LOGGER.error("[eprotect] Replacement of {} threw", entity, e);
            return false;
        }
        if (entity instanceof IEprotected old && old.getEprotect() != null) old.getEprotect().retireReplaced();
        return true;
    }

    /**
     * バニラのエンティティ管理が渡す接続か。NULL は除外する。
     * MoreAvaritia のように「何もしない独自の実装」へ差し替えてくる MOD があるので、NULL かどうかだけでは判定しない。
     */
    static boolean isVanillaCallback(EntityInLevelCallback callback) {
        return callback != EntityInLevelCallback.NULL && callback.getClass().getName().startsWith("net.minecraft.");
    }

    @SuppressWarnings("unchecked")
    private static EntityManagerAccessor<Entity> manager(ServerLevel level) {
        return (EntityManagerAccessor<Entity>) (Object) ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityManager();
    }
}
