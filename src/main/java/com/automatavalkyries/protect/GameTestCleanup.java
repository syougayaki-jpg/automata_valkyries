package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.protect.valkyrie.ValkyrieBackupFiles;
import com.automatavalkyries.protect.valkyrie.ValkyrieBackups;
import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * GameTest の後片付けを保護に断らせない（2026-10-08）。GameTest は試験場を置く前に、その範囲のエンティティを {@code discard()} で片付ける
 * （{@code StructureUtils.clearSpaceForStructure}）。保護対象はこれを不正な消去として断るので、前の試験の残したボス・兵姫・ダミーが片付かずに
 * 試験用のワールド（{@code run/world}）と登録簿に溜まり続け（630 体）、次の実行で作り直されて偽の攻撃の記録（架空の MOD の印の付いたまま）を出していた。
 * 片付けの範囲の保護対象は、片付けの直前に保護を外す。GameTest のサーバーでは、起動時に前の実行の登録簿と兵姫のバックアップも空にする
 */
public final class GameTestCleanup {

    private GameTestCleanup() {}

    public static boolean isGameTestServer(MinecraftServer server) {
        return server instanceof GameTestServer;
    }

    /** 片付けの範囲の保護対象の保護を外す（消すのは GameTest の側） */
    public static void releaseIn(ServerLevel level, AABB area) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, area)) {
            if (e instanceof IEprotected p && p.getEprotect() != null) p.getEprotect().releaseForCleanup();
            else if (e instanceof ValkyrieEntity v) ValkyrieProtect.release(level.getServer(), v.getUUID());
        }
    }

    /**
     * GameTest のサーバーの起動時: 前の実行の Eprotect の登録簿を空にし（作り直しの元を断つ）、もう読み込まれている保護対象
     * （いつも読み込まれている出現地点のチャンクに保存されていた物。試験場の片付けの範囲に入らない）を消す
     */
    public static void resetAtStart(MinecraftServer server) {
        if (!isGameTestServer(server)) return;
        int records = 0, entities = 0;
        for (ServerLevel level : server.getAllLevels()) {
            records += EprotectRegistry.get(level).clear();
            java.util.List<Entity> leftovers = new java.util.ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (e instanceof IEprotected || e instanceof ValkyrieEntity) leftovers.add(e);
            }
            for (Entity e : leftovers) {
                if (e instanceof IEprotected p && p.getEprotect() != null) p.getEprotect().releaseForCleanup();
                if (e instanceof ValkyrieEntity v) v.releaseAndDiscard();
                else e.discard();
            }
            entities += leftovers.size();
        }
        if (records + entities > 0) {
            AutomataValkyries.LOGGER.info("[eprotect] GameTest server: cleared {} leftover registry records and {} loaded protected entities", records, entities);
        }
    }

    /** GameTest のサーバーの起動時: 前の実行の兵姫のバックアップ（セーブと別ファイルの両方）を消す */
    public static void resetValkyrieBackups(MinecraftServer server) {
        if (!isGameTestServer(server)) return;
        Set<UUID> ids = new HashSet<>(ValkyrieBackups.get(server).all().keySet());
        ids.addAll(ValkyrieBackupFiles.readAll(server).keySet());
        for (UUID id : ids) ValkyrieProtect.release(server, id);
        if (!ids.isEmpty()) AutomataValkyries.LOGGER.info("[valkyrie-protect] GameTest server: cleared {} leftover valkyrie backups", ids.size());
    }
}
