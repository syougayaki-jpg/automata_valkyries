package com.automatavalkyries.protect.valkyrie;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * オートマタヴァルキリー protect のバックアップ（別ファイル側）: {@code <ワールド>/automata_valkyries/valkyrie_backups/<UUID>.dat}。
 * ワールドのセーブ（{@link ValkyrieBackups}）とは別に残すので、片方を壊されても戻せる。書くときは一時ファイルに書いてから置き換える
 */
public final class ValkyrieBackupFiles {

    private ValkyrieBackupFiles() {}

    static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(AutomataValkyries.MODID).resolve("valkyrie_backups");
    }

    public static void write(MinecraftServer server, UUID id, ValkyrieBackups.Entry entry) {
        try {
            Path d = dir(server);
            Files.createDirectories(d);
            Path tmp = d.resolve(id + ".tmp");
            NbtIo.writeCompressed(entry.save(), tmp.toFile());
            Files.move(tmp, d.resolve(id + ".dat"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            AutomataValkyries.LOGGER.error("[valkyrie-protect] could not write the backup file of {}", id, e);
        }
    }

    public static void delete(MinecraftServer server, UUID id) {
        try {
            Files.deleteIfExists(dir(server).resolve(id + ".dat"));
        } catch (IOException e) {
            AutomataValkyries.LOGGER.error("[valkyrie-protect] could not delete the backup file of {}", id, e);
        }
    }

    public static Map<UUID, ValkyrieBackups.Entry> readAll(MinecraftServer server) {
        Map<UUID, ValkyrieBackups.Entry> out = new HashMap<>();
        File[] files = dir(server).toFile().listFiles((f, name) -> name.endsWith(".dat"));
        if (files == null) return out;
        for (File f : files) {
            try {
                UUID id = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                CompoundTag tag = NbtIo.readCompressed(f);
                out.put(id, ValkyrieBackups.Entry.load(tag));
            } catch (IOException | IllegalArgumentException e) {
                AutomataValkyries.LOGGER.error("[valkyrie-protect] could not read the backup file {}", f, e);
            }
        }
        return out;
    }
}
