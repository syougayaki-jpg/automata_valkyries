package com.automatavalkyries.agentcommon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;

/** エージェントのログ（標準出力と {@code logs/<file>} の両方に、時刻とタグをつけて 1 行ずつ） */
public final class AgentLog {

    private final Path file;
    private final String tag;

    public AgentLog(String fileName, String tag) {
        this.file = Path.of("logs", fileName);
        this.tag = tag;
    }

    public void log(String message) {
        String line = "[" + LocalTime.now().withNano(0) + "] [" + tag + "] " + message;
        System.out.println(line);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
