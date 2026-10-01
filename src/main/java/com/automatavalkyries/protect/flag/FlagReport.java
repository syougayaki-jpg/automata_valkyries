package com.automatavalkyries.protect.flag;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.CallerAttribution;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * フラグ辞書の点検用の一覧（logs/automata_valkyries-flags.txt）。読み込み完了時に別スレッドで書き出す（読むだけ。何も変えない）。
 * <ol>
 *   <li>vanilla のエンティティのクラスに他 MOD が足したフィールド</li>
 *   <li>vanilla のエンティティのクラスに他 MOD が足したインターフェースの setter（と getter）</li>
 *   <li>他 MOD のエンティティのクラスが自分で宣言したフィールド（jar を ASM で読む。クラスは読み込まない＝コンストラクタ等を動かさない）</li>
 *   <li>他 MOD の状態異常</li>
 * </ol>
 * 各行に、辞書の分類と、保護対象での扱い（戻す）・超絶抹殺の棒での扱い（外す・0 にする・有効にする）を付ける。
 */
public final class FlagReport {

    private static final Set<String> VALUE_DESCRIPTORS = Set.of("Z", "I", "J", "F", "D", "S", "B");
    private static final String ENTITY_PACKAGE = "net/minecraft/world/entity/";

    private FlagReport() {}

    public static Path path() {
        return Paths.get("logs", AutomataValkyries.MODID + "-flags.txt");
    }

    /** 別スレッドで書き出す */
    public static void writeAsync() {
        Thread thread = new Thread(() -> {
            try {
                write();
            } catch (Throwable t) {
                AutomataValkyries.LOGGER.warn("[eprotect] Could not write the flag report", t);
            }
        }, "AutomataValkyries-FlagReport");
        thread.setDaemon(true);
        thread.start();
    }

    public static void write() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# フラグ辞書の点検（保護対象: 戻す=毎 Tick 害の無い値へ / 抹殺: 外す・0・有効）");
        lines.add("# 書式: 場所 | 名前 : 種類 | 分類 | 保護対象 | 抹殺");
        lines.add("# 3 の他 MOD のエンティティは、辞書に当たったフィールドだけを載せる。1 の同期データは、保護対象を悪化させる書き込みを入口でも拒否する");
        lines.add("");
        lines.add("## 1. vanilla のエンティティのクラスに足されたフィールド");
        for (Class<?> target : new Class<?>[]{Entity.class, LivingEntity.class, Mob.class, Player.class}) {
            for (InjectedMembers.InjectedField injected : InjectedMembers.fields(target)) {
                if (CallerAttribution.SELF.equals(injected.mod())) continue;
                lines.add(line(injected.mod() + " " + target.getSimpleName(), injected.field().getName(),
                        kindOf(injected.field().getType(), injected.field().getModifiers())));
            }
        }
        lines.add("");
        lines.add("## 2. vanilla のエンティティのクラスに足されたインターフェースの setter");
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> target : new Class<?>[]{Entity.class, LivingEntity.class, Mob.class, Player.class}) collectForeign(target, interfaces);
        for (Class<?> api : interfaces) {
            String mod = CallerAttribution.modOf(api);
            for (Method method : api.getMethods()) {
                if (method.getParameterCount() != 1) continue;
                String bare = bare(method.getName());
                if (bare.length() <= 3 || !bare.toLowerCase(Locale.ROOT).startsWith("set")) continue;
                lines.add(line(mod + " " + api.getSimpleName(), bare.substring(3), kindOf(method.getParameterTypes()[0], 0)));
            }
        }
        lines.add("");
        lines.add("## 3. 他 MOD のエンティティのクラスのフィールド（抹殺の対象側）");
        for (IModFileInfo file : ModList.get().getModFiles()) {
            String modId = file.getMods().isEmpty() ? "?" : file.getMods().get(0).getModId();
            if (!CallerAttribution.isForeign(modId)) continue;
            try {
                scanJar(file.getFile().getFilePath(), modId, lines);
            } catch (Throwable t) {
                lines.add("  (" + modId + " を読めなかった: " + t + ")");
            }
        }
        lines.add("");
        lines.add("## 4. 他 MOD の状態異常（保護対象: 有害か名前が死亡・停止なら外す / 抹殺: 名前が守りなら外す）");
        for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
            ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (key == null || !CallerAttribution.isForeign(key.getNamespace())) continue;
            boolean removedFromProtected = effect.getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL
                    || FlagDictionary.harmfulOnProtected(key.getPath());
            lines.add(String.format("%s | %s | %s | %s | %s", key, effect.getCategory(), categories(key.getPath()),
                    removedFromProtected ? "外す" : "-", FlagDictionary.protectiveOnTarget(key.getPath()) ? "外す" : "-"));
        }

        Path path = path();
        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
        AutomataValkyries.LOGGER.info("[eprotect] Wrote the flag report to {} ({} lines)", path.toAbsolutePath(), lines.size());
    }

    // ---- jar の走査 ------------------------------------------------------------

    private record ClassData(String name, String superName, List<String[]> fields) {}

    private static void scanJar(Path path, String modId, List<String> lines) throws IOException {
        if (!Files.isRegularFile(path)) return;
        Map<String, ClassData> classes = new HashMap<>();
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/")) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    ClassData data = read(in);
                    if (data != null) classes.put(data.name(), data);
                } catch (Throwable ignored) {}
            }
        }
        Map<String, List<String>> byClass = new TreeMap<>();
        for (ClassData data : classes.values()) {
            if (!isEntity(data, classes, 0)) continue;
            for (String[] field : data.fields()) {
                String name = field[0];
                String descriptor = field[1];
                if (FlagDictionary.classify(name).isEmpty()) continue; // 辞書に当たらないフィールドは載せない（量が多い）
                Kind kind;
                if (descriptor.equals("Lnet/minecraft/network/syncher/EntityDataAccessor;") && "static".equals(field[2])) kind = Kind.SYNCHED;
                else if (field[2] != null || !VALUE_DESCRIPTORS.contains(descriptor)) kind = Kind.OTHER;
                else kind = descriptor.equals("Z") ? Kind.BOOLEAN : Kind.NUMBER;
                byClass.computeIfAbsent(data.name().replace('/', '.'), k -> new ArrayList<>()).add(line("  ", name, kind));
            }
        }
        if (byClass.isEmpty()) return;
        lines.add("### " + modId);
        byClass.forEach((name, fieldLines) -> {
            lines.add(name.substring(name.lastIndexOf('.') + 1));
            lines.addAll(fieldLines);
        });
    }

    private static ClassData read(InputStream in) throws IOException {
        List<String[]> fields = new ArrayList<>();
        String[] names = new String[2];
        new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                names[0] = name;
                names[1] = superName;
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                String mods = (access & Opcodes.ACC_STATIC) != 0 ? "static" : (access & Opcodes.ACC_FINAL) != 0 ? "final" : null;
                fields.add(new String[]{name, descriptor, mods});
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return names[0] == null ? null : new ClassData(names[0], names[1], fields);
    }

    /** エンティティのクラスか（jar 内の親をたどり、jar の外の親は vanilla のクラスとして読み込んで確かめる。AI の Goal 等は除く） */
    private static boolean isEntity(ClassData data, Map<String, ClassData> classes, int depth) {
        if (data.superName() == null || depth > 32) return false;
        ClassData parent = classes.get(data.superName());
        if (parent != null) return isEntity(parent, classes, depth + 1);
        if (!data.superName().startsWith(ENTITY_PACKAGE)) return false;
        try {
            return Entity.class.isAssignableFrom(Class.forName(data.superName().replace('/', '.'), false, Entity.class.getClassLoader()));
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- 共通 --------------------------------------------------------------

    /** 準汎用の処理から見た値の種類 */
    private enum Kind {
        BOOLEAN("boolean"), NUMBER("数値"), SYNCHED("同期データ"), OTHER("対象外の型");

        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private static Kind kindOf(Class<?> type, int modifiers) {
        if (type == net.minecraft.network.syncher.EntityDataAccessor.class && java.lang.reflect.Modifier.isStatic(modifiers)) return Kind.SYNCHED;
        if (java.lang.reflect.Modifier.isStatic(modifiers) || java.lang.reflect.Modifier.isFinal(modifiers)) return Kind.OTHER;
        if (type == boolean.class) return Kind.BOOLEAN;
        return type.isPrimitive() && type != char.class && type != void.class ? Kind.NUMBER : Kind.OTHER;
    }

    /**
     * 1 行。扱いは {@link StateScanner#CLEAN_PROTECTED} / {@link StateScanner#BREAK_TARGET} と同じ判定
     * （同期データは中身の型が実行時まで分からないので、真偽なら・数値ならの両方を書く）
     */
    private static String line(String where, String name, Kind kind) {
        if (kind == Kind.OTHER) return String.format("%s | %s : %s | %s | - | -", where, name, kind.label, categories(name));
        String onProtected = FlagDictionary.harmfulOnProtected(name) ? "戻す" : "-";
        String asBoolean = FlagDictionary.is(name, FlagDictionary.Category.BYPASS) ? "有効にする"
                : FlagDictionary.protectiveOnTarget(name) ? "外す" : "-";
        String asNumber = FlagDictionary.livesCounter(name) ? "0 にする" : "-";
        String onTarget = switch (kind) {
            case BOOLEAN -> asBoolean;
            case NUMBER -> asNumber;
            default -> asBoolean.equals(asNumber) ? asBoolean : "真偽なら " + asBoolean + " / 数値なら " + asNumber;
        };
        return String.format("%s | %s : %s | %s | %s | %s", where, name, kind.label, categories(name), onProtected, onTarget);
    }

    private static String categories(String name) {
        Set<FlagDictionary.Category> c = FlagDictionary.classify(name);
        return c.isEmpty() ? "-" : c.stream().map(x -> x.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(","));
    }

    private static void collectForeign(Class<?> type, Set<Class<?>> out) {
        for (Class<?> api : type.getInterfaces()) {
            if (CallerAttribution.isForeign(CallerAttribution.modOf(api)) && out.add(api)) collectForeign(api, out);
        }
    }

    private static String bare(String name) {
        int dollar = name.lastIndexOf('$');
        return dollar >= 0 ? name.substring(dollar + 1) : name;
    }
}
