package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.EprotectConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 守る側の Java エージェント（観察者・LifeGuard・オートマタヴァルキリー protect）を、起動の引数（-javaagent）を付けずに設定で読み込む
 * （2026-10-09 ユーザー指定）。設定 {@code agents.*.enabled} が true で、まだ読み込まれていないエージェントだけ:
 * <ol>
 *   <li>MOD の jar に入っているエージェントの jar（と呼び出し先の jar）を {@code <ゲームのフォルダ>/automata_valkyries/agents/} へ取り出す
 *       （呼び出し先はエージェントの Boot-Class-Path に同じフォルダの名前で書いてあるので、同じフォルダに置く）</li>
 *   <li>同じ Java で別のプロセスを起こし（{@code agentcommon.Attacher}）、このゲームのプロセスへ付ける。Java は同じプロセスから自分へ付けるのを
 *       既定で禁じているので、別のプロセスから付ける</li>
 * </ol>
 * MOD の初期化（共通の準備）の始めに、接続（{@code LifeGuardLink.install} 等）より前に呼ぶ。付けられなかったら警告を出して、起動の引数で付ける方法を案内する。
 * Eprotect+ とフラグ辞書のエージェントは CODEX の担当なので、ここには書かない。CODEX は自分の担当のクラス {@value #EXTENSION} に
 * {@code public static void register()} を作り、その中で {@link #register(BundledAgent)} を呼べば、同じしくみで読み込まれる
 * （docs/HANDOFF_AGENT_CONFIG_FOR_CODEX.md）
 */
public final class AgentSelfAttach {

    /** jar の中のエージェントの置き場 */
    private static final String RESOURCE_DIR = "/META-INF/automata_valkyries/agents/";
    private static final String MODID = AutomataValkyries.MODID;
    private static final String ATTACHER = "com.automatavalkyries.agentcommon.Attacher";
    private static final long TIMEOUT_SECONDS = 30;
    /** 他の担当が自分のエージェントを足す入口（あれば {@code register()} を呼ぶ） */
    public static final String EXTENSION = "com.automatavalkyries.protect.plus.BundledAgents";

    /**
     * 他の担当が足すエージェント 1 つ。
     *
     * @param label     ログに出す名前
     * @param jar       jar の名前（MOD の jar の {@code META-INF/automata_valkyries/agents/} と build/libs にある名前）
     * @param hooks     ブートストラップに置く呼び出し先の jar の名前（Boot-Class-Path に書いた名前。無ければ null）
     * @param marker    読み込まれたかを見るクラスの名前（エージェントの jar か呼び出し先にあるもの）
     * @param bootstrap marker がブートストラップにあるか（呼び出し先にあるなら true、エージェントの jar にあるなら false）
     * @param enabled   読み込むか（自分の設定を読む。共通の準備のときに呼ばれる）
     * @param options   エージェントへ渡す引数（無ければ null を返す）
     * @param after     付けた後に呼ぶ（MOD 側の接続など。無ければ null）
     */
    public record BundledAgent(String label, String jar, String hooks, String marker, boolean bootstrap,
                               java.util.function.BooleanSupplier enabled, java.util.function.Supplier<String> options, Runnable after) {}

    private static final List<BundledAgent> EXTRA = new ArrayList<>();

    /** 他の担当のエージェントを足す（{@value #EXTENSION} の register() から） */
    public static synchronized void register(BundledAgent agent) {
        EXTRA.add(agent);
    }

    /** エージェント 1 つ: jar の名前・呼び出し先の jar の名前（無ければ null）・読み込まれているかの目印のクラス・ブートストラップにあるか */
    private record Agent(String label, String jar, String hooks, String marker, boolean bootstrap) {}

    private static final Agent OBSERVER = new Agent("observer", MODID + "-observer.jar", null, "com.automatavalkyries.observer.ObserverBridge", false);
    private static final Agent LIFEGUARD = new Agent("LifeGuard", MODID + "-lifeguard.jar", MODID + "-lifeguard-hooks.jar",
            "com.automatavalkyries.lifeguard.hooks.LifeHooks", true);
    private static final Agent VALKYRIE_PROTECT = new Agent("valkyrie protect", MODID + "-valkyrie-protect.jar", MODID + "-valkyrie-protect-hooks.jar",
            "com.automatavalkyries.valkyrieprotect.hooks.ValkyrieHooks", true);

    private AgentSelfAttach() {}

    /** 設定で有効なエージェントを読み込む（読み込み済み・無効なものは飛ばす） */
    public static void attachConfigured() {
        List<Agent> wanted = new ArrayList<>();
        if (EprotectConfig.get(EprotectConfig.AGENT_OBSERVER)) wanted.add(OBSERVER);
        if (EprotectConfig.get(EprotectConfig.AGENT_LIFEGUARD)) wanted.add(LIFEGUARD);
        if (EprotectConfig.get(EprotectConfig.AGENT_VALKYRIE_PROTECT)) wanted.add(VALKYRIE_PROTECT);
        for (Agent agent : wanted) {
            if (present(agent)) {
                AutomataValkyries.LOGGER.info("[agents] {} is already loaded (startup argument); nothing to attach", agent.label());
                continue;
            }
            String options = agent == OBSERVER && !EprotectConfig.get(EprotectConfig.AGENT_OBSERVER_GUARD) ? "noguard" : null;
            attach(agent, options);
        }
        if (wanted.contains(OBSERVER)) ObserverLink.reset();
        attachExtensions();
    }

    /** 他の担当が {@value #EXTENSION} で足したエージェントを読み込む */
    private static void attachExtensions() {
        try {
            Class.forName(EXTENSION).getMethod("register").invoke(null);
        } catch (ClassNotFoundException e) {
            return; // 足されていない
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[agents] {}.register() failed: {}", EXTENSION, t.toString());
        }
        List<BundledAgent> extra;
        synchronized (AgentSelfAttach.class) {
            extra = List.copyOf(EXTRA);
            EXTRA.clear();
        }
        for (BundledAgent b : extra) {
            try {
                if (!b.enabled().getAsBoolean()) continue;
                Agent agent = new Agent(b.label(), b.jar(), b.hooks(), b.marker(), b.bootstrap());
                if (present(agent)) {
                    AutomataValkyries.LOGGER.info("[agents] {} is already loaded (startup argument); nothing to attach", b.label());
                } else {
                    attach(agent, b.options() != null ? b.options().get() : null);
                }
                if (b.after() != null && present(agent)) b.after().run();
            } catch (Throwable t) {
                AutomataValkyries.LOGGER.warn("[agents] Could not handle the {} agent: {}", b.label(), t.toString());
            }
        }
    }

    /**
     * エージェントが読み込まれているか。目印のクラスがあるだけでは足りない（同じ名前のクラスを置かれると、付いたと誤認して付けるのを飛ばす）。
     * 呼び出し先の jar に {@code status} があるものは、エージェントが動き出して状態の表示を入れた後でなければ、付いたとは数えない（2026-10-21）
     */
    private static boolean present(Agent agent) {
        try {
            Class<?> marker = Class.forName(agent.marker(), false, agent.bootstrap() ? null : ClassLoader.getSystemClassLoader());
            if (!agent.bootstrap()) return true;
            try {
                return marker.getField("status").get(null) != null;
            } catch (NoSuchFieldException noStatus) {
                return true; // 状態の表示を持たない呼び出し先は、目印のクラスだけで見る
            }
        } catch (Throwable t) {
            return false;
        }
    }

    private static void attach(Agent agent, String options) {
        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve(MODID).resolve("agents");
            Files.createDirectories(dir);
            Extracted agentFile = extract(agent.jar(), dir);
            Extracted hooksFile = agent.hooks() != null ? extract(agent.hooks(), dir) : null;
            agentFile.verify();
            if (hooksFile != null) hooksFile.verify();
            Path jar = agentFile.path();
            String java = ProcessHandle.current().info().command()
                    .orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            List<String> command = new ArrayList<>(Arrays.asList(java, "-cp", jar.toString(), ATTACHER,
                    Long.toString(ProcessHandle.current().pid()), jar.toString()));
            if (options != null) command.add(options);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("timed out after " + TIMEOUT_SECONDS + " s");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0 || !present(agent)) throw new IOException(output.isEmpty() ? "exit " + process.exitValue() : output);
            AutomataValkyries.LOGGER.info("[agents] Attached the {} agent from the config ({})", agent.label(), jar);
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[agents] Could not attach the {} agent from the config: {}. Add it as a startup argument instead: "
                    + "-javaagent:<path to {}> (the hooks jar, if any, must sit in the same folder)", agent.label(), t.toString(), agent.jar());
        }
    }

    /** jar の中のエージェントを取り出す（同じ中身なら書き直さない。使用中で書けなければ今あるものを使う） */
    private static Extracted extract(String name, Path dir) throws IOException {
        Path target = dir.resolve(name);
        byte[] bytes;
        try (InputStream in = AgentSelfAttach.class.getResourceAsStream(RESOURCE_DIR + name)) {
            if (in != null) {
                bytes = in.readAllBytes();
            } else { // 開発環境（runClient 等）は jar ではなくクラスのフォルダから動くので、ビルドした build/libs から読む
                Path built = FMLPaths.GAMEDIR.get().resolve("../build/libs").resolve(name).normalize();
                if (!Files.exists(built)) throw new IOException(name + " is not bundled in the mod jar (nor at " + built + ")");
                bytes = Files.readAllBytes(built);
            }
        }
        if (Files.exists(target) && Arrays.equals(Files.readAllBytes(target), bytes)) return new Extracted(target, bytes);
        try {
            Path tmp = Files.createTempFile(dir, name, ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // 使用中で書き直せなかった。中身が同梱のものと違うなら使わない（取り出し先を書き換えられても、確かめずに付けない。2026-10-21）
            if (!Files.exists(target) || !Arrays.equals(Files.readAllBytes(target), bytes)) {
                throw new IOException(target + " could not be updated and differs from the bundled copy; refusing to attach it", e);
            }
        }
        return new Extracted(target, bytes);
    }

    /** 取り出したファイルと、同梱の中身（付ける直前に照合する） */
    private record Extracted(Path path, byte[] bytes) {
        /** 付ける直前の照合。取り出してから付けるまでの間に差し替えられていないか */
        void verify() throws IOException {
            if (!Files.exists(path) || !Arrays.equals(Files.readAllBytes(path), bytes)) {
                throw new IOException(path + " no longer matches the bundled copy; refusing to attach it");
            }
        }
    }
}
