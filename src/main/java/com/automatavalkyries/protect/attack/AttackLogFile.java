package com.automatavalkyries.protect.attack;

import com.automatavalkyries.AutomataValkyries;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 攻撃の記録をファイル（logs/automata_valkyries-attacks.log）へ追記する。書き込みは専用スレッドで行い、サーバーを止めない */
public final class AttackLogFile {

    private static final long ROTATE_BYTES = 5L * 1024 * 1024;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Nullable
    private static ExecutorService executor;
    @Nullable
    private static BufferedWriter writer;

    private AttackLogFile() {}

    public static Path path() {
        return FMLPaths.GAMEDIR.get().resolve("logs").resolve(AutomataValkyries.MODID + "-attacks.log");
    }

    public static synchronized void open() {
        if (executor != null) return;
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "AutomataValkyries-AttackLog");
            thread.setDaemon(true);
            return thread;
        });
        executor.execute(() -> {
            try {
                Path path = path();
                Files.createDirectories(path.getParent());
                if (Files.exists(path) && Files.size(path) > ROTATE_BYTES) {
                    Files.move(path, path.resolveSibling(path.getFileName() + ".1"), StandardCopyOption.REPLACE_EXISTING);
                }
                writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                writeLine("---- server started ----");
            } catch (IOException e) {
                AutomataValkyries.LOGGER.warn("[eprotect] Could not open the attack log file", e);
            }
        });
    }

    public static synchronized void write(String line) {
        if (executor == null) return;
        executor.execute(() -> writeLine(line));
    }

    public static synchronized void close() {
        if (executor == null) return;
        ExecutorService closing = executor;
        executor = null;
        closing.execute(() -> {
            try {
                if (writer != null) {
                    writeLine("---- server stopped ----");
                    writer.close();
                }
            } catch (IOException ignored) {
            } finally {
                writer = null;
            }
        });
        closing.shutdown();
    }

    private static void writeLine(String line) {
        if (writer == null) return;
        try {
            writer.write("[" + LocalDateTime.now().format(TIME) + "] " + line);
            writer.newLine();
            writer.flush();
        } catch (IOException ignored) {
        }
    }
}
