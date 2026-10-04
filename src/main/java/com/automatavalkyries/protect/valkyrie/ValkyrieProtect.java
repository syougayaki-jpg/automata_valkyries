package com.automatavalkyries.protect.valkyrie;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * オートマタヴァルキリー protect（2026-10-04 新設、Eprotect とは別の仕組み。docs/SPEC_VALKYRIE_GUI_AI.md §5）。
 * 戦術兵姫のデータを二重にバックアップして守る。通常のダメージは受け、HP 0 で機能停止する（被弾上限はかけない）。
 * <p>完全な保護は胸のコアがステラ・ジェネシス・オメガのときだけ（全体仕様書 §7.1「即死・ハッキング完全無効化」。2026-10-04 ユーザー指定）:</p>
 * <ul>
 *   <li>オメガコア: 即死（kill / die / HP 0 への書き換え / 桁外れのダメージ）を受け付けない。消去も拒否し、消されたら自動で戻す</li>
 *   <li>オメガコア無し: 即死は死亡モーション（崩れ落ちる）の後に機能停止（直せる）。他 MOD の消去は受けるが、バックアップは残り、
 *       救済通信機（{@code item/RescueCommunicatorItem}）で呼び出せる（{@link #recall}）</li>
 * </ul>
 *
 * <p>ゲーム内の層（常に動く）:</p>
 * <ul>
 *   <li>拒否: 兵姫のクラス自身（{@code remove} / {@code kill} / {@code die} / {@code setHealth}）と Mixin（{@code setRemoved} / {@code discard}、
 *       {@code mixin/ValkyrieProtectMixin}）で、消去（KILLED / DISCARDED）を拒否する。チャンクの読み込み解除・次元移動・解放（{@link #release}）は通す</li>
 *   <li>バックアップ: 1 秒ごとに兵姫のデータ（NBT・次元・位置・持ち主）をワールドのセーブ（{@link ValkyrieBackups}）と、
 *       ワールドのフォルダの兵姫ごとの別ファイルの二重に残す（片方を壊されても戻せる）</li>
 *   <li>見張りと復元: 1 秒ごとに、居るはずの所（チャンクとエンティティが読み込まれている所）に居ない兵姫を、2 回続けて居なかったら戻す。
 *       戻す先は消えた場所、危険（奈落・溶岩・壁の中・その次元が無い）なら持ち主のそば、持ち主もいなければ世界の初期地点</li>
 * </ul>
 * <p>Java エージェントの層（{@code src/valkyrieprotect}、起動時に指定したときだけ）は別に作る。</p>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class ValkyrieProtect {

    /** 見張りとバックアップの間隔（Tick） */
    public static final int INTERVAL = 20;
    /** 何回続けて居なかったら戻すか（エンティティの読み込みの遅れで二重に出さないため） */
    public static final int MISSES_TO_RESTORE = 2;
    /** 次元移動の途中は見張らない Tick 数 */
    private static final int MOVING_GRACE = 100;

    private static final Map<UUID, Integer> MISSES = new HashMap<>();
    private static final Map<UUID, Long> MOVING = new HashMap<>();
    /** ログの間引き（2026-10-05）: 同じ兵姫・同じ操作は LOG_QUIET Tick に 1 行。{最後に出した Tick, その後に黙った数} */
    private static final Map<String, long[]> LOG_GATE = new HashMap<>();
    private static final int LOG_QUIET = 100;
    private static long tick;

    private ValkyrieProtect() {}

    // ------------------------------------------------------------------ 拒否

    /** この理由で消してよいか（ダメなら記録して拒否） */
    public static boolean allowRemoval(ValkyrieEntity v, Entity.RemovalReason reason) {
        if (v.level().isClientSide || v.isReleased()) return true;
        if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) return true;
        if (reason == Entity.RemovalReason.CHANGED_DIMENSION) {
            MOVING.put(v.getUUID(), tick);
            return true;
        }
        // GameTest の区画の片付け（StructureUtils がテストの枠の中の物を消す）は通して、保護も外す（テストの世界に兵姫が残り続けないように）
        if (fromGameTestFramework() && v.level() instanceof ServerLevel level) {
            release(level.getServer(), v.getUUID());
            return true;
        }
        if (v.hasOmegaCore()) {
            String muted = gate(v.getUUID() + "|remove " + reason);
            if (muted != null) AutomataValkyries.LOGGER.warn("[valkyrie-protect] refused removal ({}) of {} by {} (omega core){}", reason, v.getUUID(), caller(), muted);
            return false;
        }
        // オメガコア無し: 消去は通し、バックアップを「失われた」にして救済通信機で呼び出せるようにする
        if (v.level() instanceof ServerLevel level) markLost(level.getServer(), v.getUUID(), "removed (" + reason + ") by " + caller());
        return true;
    }

    /** 失われた（オメガコア無しで消された）印を付ける。救済通信機で呼び出すまで自動では戻さない */
    static void markLost(MinecraftServer server, UUID id, String why) {
        ValkyrieBackups backups = ValkyrieBackups.get(server);
        ValkyrieBackups.Entry entry = backups.get(id);
        if (entry == null || entry.lost()) return;
        ValkyrieBackups.Entry lost = entry.asLost();
        backups.put(id, lost);
        ValkyrieBackupFiles.write(server, id, lost);
        MISSES.remove(id);
        AutomataValkyries.LOGGER.warn("[valkyrie-protect] {} was erased: {} — recall it with a rescue communicator", id, why);
    }

    /** 即死（die / kill / HP 0 への書き換え / 桁外れのダメージ）を受けたことを記録する（どう受けるかは兵姫の側） */
    public static void noteLethal(ValkyrieEntity v, String what) {
        if (v.level().isClientSide) return;
        // 毎 Tick 撃ち込まれる即死（IMDS 等）でログが埋まらないよう、値を除いた操作の名前ごとに間引く
        String muted = gate(v.getUUID() + "|" + what.replaceAll("-?(Infinity|NaN|[0-9][0-9.E+-]*)", "#"));
        if (muted != null) AutomataValkyries.LOGGER.info("[valkyrie-protect] {} on {} -> {} ({}){}", what, v.getUUID(),
                v.hasOmegaCore() ? "ignored (omega core)" : "death motion, then shutdown", caller(), muted);
    }

    /** 出してよいなら黙っていた数の添え書き（無ければ空文字）、まだ黙る間なら null */
    @Nullable
    private static String gate(String key) {
        long[] g = LOG_GATE.get(key);
        if (g != null && tick - g[0] < LOG_QUIET) {
            g[1]++;
            return null;
        }
        String note = g != null && g[1] > 0 ? " (+" + g[1] + " similar in the last " + (tick - g[0]) / 20 + " s)" : "";
        LOG_GATE.put(key, new long[]{tick, 0});
        return note;
    }

    /**
     * 他 MOD の即死・消去の入口（フィールド直書きで消すなど、setRemoved の拒否では止まらない物。{@code mixin/compat/ValkyrieImdsMixins}）で、
     * 止めるべき兵姫か。完全な保護（オメガコア）の兵姫だけ止める。オメガコア無しは受ける（機能停止 → 消去 → 救済通信機）
     */
    public static boolean shieldForeign(@Nullable Entity e, String operation) {
        if (!(e instanceof ValkyrieEntity v) || v.level().isClientSide || v.isReleased() || !v.hasOmegaCore()) return false;
        noteLethal(v, operation);
        return true;
    }

    /**
     * {@link #shieldForeign} のクライアント側も含む版（2026-10-05、ユーザーの報告「エンティティリムーバーでも見えなくなっていた」）。
     * クライアントで直接消す他 MOD（Omni-Mobs の消去アイテムは左クリックでクライアントでも消す）から、兵姫は全部守る。
     * オメガコアかはクライアントには分からないが、オメガコア無しの兵姫はサーバーで消えれば通常の追跡の終わりでクライアントからも消える
     */
    public static boolean shieldForeignOrClient(@Nullable Entity e, String operation) {
        if (e instanceof ValkyrieEntity && e.level().isClientSide) return true;
        return shieldForeign(e, operation);
    }

    /**
     * 見え方の直し（2026-10-05、ユーザーの報告「稼働中だけどプレイヤーからみえていない」）: 他 MOD がクライアントへ「消えた」を直接送ると、
     * サーバーには居るのにプレイヤーから見えなくなる。追跡を作り直して、近くのプレイヤーへ出現を送り直す
     */
    public static void resync(ValkyrieEntity v) {
        if (!(v.level() instanceof ServerLevel level) || DirectRead.isRemoved(v)) return;
        level.getChunkSource().removeEntity(v);
        level.getChunkSource().addEntity(v);
    }

    private static boolean fromGameTestFramework() {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            if (e.getClassName().equals("net.minecraft.gametest.framework.StructureUtils")) return true; // 区画の片付け（clearSpaceForStructure）だけ
        }
        return false;
    }

    /** 呼んだ側（このMODと JDK・Minecraft を除いた最初のクラス） */
    static String caller() {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String c = e.getClassName();
            if (c.startsWith("java.") || c.startsWith("jdk.") || c.startsWith("net.minecraft.") || c.startsWith("com.automatavalkyries.")
                    || c.startsWith("org.spongepowered.") || c.startsWith("net.minecraftforge.") || c.startsWith("cpw.")) continue;
            return c + "#" + e.getMethodName();
        }
        return "vanilla / this mod";
    }

    // ------------------------------------------------------------------ バックアップ

    /** 兵姫の今のデータを残す（兵姫の Tick から INTERVAL ごとに呼ぶ） */
    public static void snapshot(ValkyrieEntity v) {
        if (!(v.level() instanceof ServerLevel level) || v.isReleased()) return;
        if (ValkyrieBackups.get(level.getServer()).isDiscarded(v.getUUID())) {
            // 持ち主がバックアップを破棄した兵姫（読み込まれていなかった個体が戻ってきた）: 保護を外して消える
            AutomataValkyries.LOGGER.info("[valkyrie-protect] {} had its backup discarded by its owner; dismissing it", v.getUUID());
            v.releaseAndDiscard();
            return;
        }
        // IMDS は登録した UUID の getHealth を 0 に書き換えるので、オメガコアの兵姫は登録を毎回外す
        if (v.hasOmegaCore()) com.automatavalkyries.protect.EprotectImdsCounter.counter(v);
        CompoundTag nbt = new CompoundTag();
        v.saveWithoutId(nbt);
        nbt.putString("id", ModEntities.VALKYRIE.getId().toString());
        // 保存の HP は getHealth（他 MOD に書き換えられることがある）なので、実際の値で上書きする
        float hp = DirectRead.health(v);
        if (Float.isFinite(hp) && hp > 0.0F) nbt.putFloat("Health", hp);
        ValkyrieBackups.Entry entry = new ValkyrieBackups.Entry(nbt, level.dimension(), v.position(), v.getOwnerUUID(), level.getGameTime(), v.hasOmegaCore(), false);
        ValkyrieBackups backups = ValkyrieBackups.get(level.getServer());
        backups.put(v.getUUID(), entry);
        MISSES.remove(v.getUUID());
        MOVING.remove(v.getUUID());
        // 別ファイルは 5 秒に 1 回（書き込みの回数を抑える）
        if ((tick / INTERVAL) % 5 == Math.floorMod(v.getUUID().hashCode(), 5)) ValkyrieBackupFiles.write(level.getServer(), v.getUUID(), entry);
    }

    /** 保護を外す（持ち主が手放した・管理者のコマンド）。バックアップも消す */
    public static void release(MinecraftServer server, UUID id) {
        ValkyrieBackups.get(server).remove(id);
        ValkyrieBackupFiles.delete(server, id);
        MISSES.remove(id);
        MOVING.remove(id);
    }

    // ------------------------------------------------------------------ 見張りと復元

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick++;
        if (tick % INTERVAL == 0) check(event.getServer());
    }

    /** 起動時に別ファイルの方が新しければ、そちらでワールドのセーブを直す（セーブの方を壊されていても戻せる） */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // 他 MOD のエージェントは起動の途中で変換器を足すので、ワールドの開始時にもう一度いちばん外側へ並べ直す
        ValkyrieAgentLink.ensureLast();
        if (ValkyrieAgentLink.isInstalled()) AutomataValkyries.LOGGER.info("[valkyrie-protect] {}", ValkyrieAgentLink.status());
        ValkyrieBackups backups = ValkyrieBackups.get(event.getServer());
        for (Map.Entry<UUID, ValkyrieBackups.Entry> file : ValkyrieBackupFiles.readAll(event.getServer()).entrySet()) {
            ValkyrieBackups.Entry saved = backups.get(file.getKey());
            if (saved == null || saved.time() < file.getValue().time()) backups.put(file.getKey(), file.getValue());
        }
    }

    /** 居るはずの所に居ない兵姫を戻す。戻した数を返す（GameTest からも呼ぶ） */
    public static int check(MinecraftServer server) {
        int restored = 0;
        LOG_GATE.values().removeIf(g -> tick - g[0] > 20L * 60);
        ValkyrieBackups backups = ValkyrieBackups.get(server);
        for (Map.Entry<UUID, ValkyrieBackups.Entry> e : new ArrayList<>(backups.all().entrySet())) {
            UUID id = e.getKey();
            ValkyrieBackups.Entry entry = e.getValue();
            if (entry.lost()) continue; // 救済通信機で呼び出すのを待っている
            Long moving = MOVING.get(id);
            if (moving != null && tick - moving < MOVING_GRACE) continue;
            if (findAnywhere(server, id) != null) {
                MISSES.remove(id);
                continue;
            }
            ServerLevel level = server.getLevel(entry.dimension());
            // その場所が読み込まれていない（チャンクの読み込み解除で正しく片付いた）なら、読み込まれたときにチャンクから出てくる
            if (level != null) {
                long chunk = ChunkPos.asLong(BlockPos.containing(entry.pos()));
                if (!level.areEntitiesLoaded(chunk)) continue;
            }
            int misses = MISSES.merge(id, 1, Integer::sum);
            if (misses < MISSES_TO_RESTORE) continue;
            MISSES.remove(id);
            if (!entry.fullProtect()) {
                markLost(server, id, "vanished without passing the removal checks");
                continue;
            }
            if (restore(server, id, entry, null, null) != null) restored++;
        }
        return restored;
    }

    /** バックアップから戻す（nearLevel / nearPos があればそこへ） */
    @Nullable
    public static ValkyrieEntity restore(MinecraftServer server, UUID id, ValkyrieBackups.Entry entry, @Nullable ServerLevel nearLevel, @Nullable Vec3 nearPos) {
        return restore(server, id, entry, nearLevel, nearPos, false);
    }

    /**
     * バックアップから戻す。freshId なら初めから新しい UUID で戻す（救済通信機。2026-10-05: IMDS などが消した UUID を一覧に持ち続け、
     * 同じ UUID で戻すとすぐまた消されるため）。UUID が変わったら記録を新しい UUID へ移す
     */
    @Nullable
    public static ValkyrieEntity restore(MinecraftServer server, UUID id, ValkyrieBackups.Entry entry, @Nullable ServerLevel nearLevel, @Nullable Vec3 nearPos,
                                         boolean freshId) {
        ServerLevel level = server.getLevel(entry.dimension());
        Vec3 pos = entry.pos();
        // 戻す先（2026-10-05 ユーザー指定）: 登録済み（持ち主がいる）なら持ち主のそば、未登録なら消えた場所（危険なら初期地点）
        ServerPlayer owner = entry.owner() == null ? null : server.getPlayerList().getPlayer(entry.owner());
        if (nearLevel != null && nearPos != null) {
            level = nearLevel;
            pos = nearPos;
        } else if (owner != null) {
            level = owner.serverLevel();
            Vec3 beside = owner.position().add(owner.getLookAngle().multiply(1.5, 0, 1.5));
            pos = safe(level, beside) ? beside : owner.position();
        } else if (level == null || !safe(level, pos)) {
            if (owner != null) {
                level = owner.serverLevel();
                pos = owner.position();
            } else {
                level = server.overworld();
                pos = Vec3.atBottomCenterOf(level.getSharedSpawnPos());
            }
        }
        ValkyrieEntity v = build(level, entry, freshId ? UUID.randomUUID() : id, pos);
        if (v == null) return null;
        if (!level.addFreshEntity(v)) {
            // 同じ UUID の参加を拒否されている（抹殺の一覧・UUID の重複など）。拒否した側が体を消していることもあるので、
            // 新しい体を作り直し、新しい UUID でデータを移して戻す
            v = build(level, entry, UUID.randomUUID(), pos);
            if (v == null || !level.addFreshEntity(v)) {
                AutomataValkyries.LOGGER.error("[valkyrie-protect] could not restore {} (the world refused to add it, even with a new UUID)", id);
                return null;
            }
            AutomataValkyries.LOGGER.warn("[valkyrie-protect] {} was refused by the world; restored under a new UUID {}", id, v.getUUID());
        } else if (freshId) {
            AutomataValkyries.LOGGER.info("[valkyrie-protect] {} recalled under a new UUID {}", id, v.getUUID());
        }
        if (!v.getUUID().equals(id)) {
            // 記録を新しい UUID へ移す（古い UUID の記録は消す）
            ValkyrieBackups backups = ValkyrieBackups.get(server);
            backups.remove(id);
            ValkyrieBackupFiles.delete(server, id);
            MISSES.remove(id);
            snapshot(v);
        }
        AutomataValkyries.LOGGER.warn("[valkyrie-protect] restored {} from backup at {} {}", id, level.dimension().location(), BlockPos.containing(pos));
        return v;
    }

    /** バックアップのデータから体を作る（まだワールドには入れない） */
    @Nullable
    private static ValkyrieEntity build(ServerLevel level, ValkyrieBackups.Entry entry, UUID id, Vec3 pos) {
        ValkyrieEntity v = ModEntities.VALKYRIE.get().create(level);
        if (v == null) return null;
        CompoundTag nbt = entry.nbt().copy();
        nbt.remove("Dimension");
        // 壊れた HP（0 以下・無限大・NaN。即死 MOD に書き換えられたまま残ったもの）で戻すと、入った途端に即死の扱いになる（2026-10-05）。
        // オメガコアなら満タンで、無ければ機能停止（HP 1、修理で起きる）で戻す。死亡モーションはもう済んでいるので繰り返さない
        float hp = nbt.getFloat("Health");
        if (!nbt.contains("Health") || !Float.isFinite(hp) || hp <= 0.0F) {
            if (entry.fullProtect()) {
                nbt.remove("Health"); // 作ったときの満タンのまま
                nbt.putBoolean("Shutdown", false);
            } else {
                nbt.putFloat("Health", 1.0F);
                nbt.putBoolean("Shutdown", true);
            }
        }
        v.load(nbt);
        v.setUUID(id);
        v.moveTo(pos.x, pos.y, pos.z, v.getYRot(), v.getXRot());
        v.setDeltaMovement(Vec3.ZERO);
        v.fallDistance = 0;
        return v;
    }

    /** 戻してよい場所か（世界の中・溶岩でない・体が埋まらない） */
    static boolean safe(ServerLevel level, Vec3 pos) {
        BlockPos p = BlockPos.containing(pos);
        if (p.getY() < level.getMinBuildHeight() + 1 || p.getY() > level.getMaxBuildHeight()) return false;
        if (!level.getFluidState(p).isEmpty() && level.getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA)) return false;
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
    }

    /** 1 人が登録（持ち主になる）できる兵姫の数（2026-10-04 ユーザー指定） */
    public static final int MAX_REGISTERED = 100;

    /** その人が持ち主の兵姫の数（バックアップに残っている全部。失われたものも数える） */
    public static int ownedCount(MinecraftServer server, UUID owner) {
        int n = 0;
        for (ValkyrieBackups.Entry entry : ValkyrieBackups.get(server).all().values()) {
            if (owner.equals(entry.owner())) n++;
        }
        return n;
    }

    /** 救済通信機の一覧の 1 行 */
    public record Summary(UUID id, String name, int state, String dimension, BlockPos pos, boolean shutdown, boolean omega,
                          float health, boolean owned) {
        public static final int ACTIVE = 0, LOST = 1, UNLOADED = 2;
    }

    /** 救済通信機に見せる一覧: 自分が持ち主の兵姫と、持ち主のいない失われた兵姫（呼び出すと自分の登録になる） */
    public static List<Summary> summaries(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        List<Summary> out = new ArrayList<>();
        for (Map.Entry<UUID, ValkyrieBackups.Entry> e : ValkyrieBackups.get(server).all().entrySet()) {
            ValkyrieBackups.Entry entry = e.getValue();
            boolean owned = player.getUUID().equals(entry.owner());
            if (!owned && !(entry.owner() == null && entry.lost())) continue;
            ValkyrieEntity live = findAnywhere(server, e.getKey());
            int state = live != null ? Summary.ACTIVE : entry.lost() ? Summary.LOST : Summary.UNLOADED;
            CompoundTag nbt = entry.nbt();
            String name = "";
            if (nbt.contains("CustomName")) {
                net.minecraft.network.chat.Component c = net.minecraft.network.chat.Component.Serializer.fromJson(nbt.getString("CustomName"));
                if (c != null) name = c.getString();
            }
            boolean omega = nbt.contains("Core") && net.minecraft.world.item.ItemStack.of(nbt.getCompound("Core"))
                    .is(com.automatavalkyries.registry.ModWeapons.CORE_STELLA_GENESIS_OMEGA.get());
            Vec3 at = live != null ? live.position() : entry.pos();
            String dim = live != null ? live.level().dimension().location().toString() : entry.dimension().location().toString();
            float health = live != null ? DirectRead.health(live) : nbt.getFloat("Health");
            boolean shutdown = live != null ? live.isShutdown() : nbt.getBoolean("Shutdown");
            out.add(new Summary(e.getKey(), name, state, dim, BlockPos.containing(at), shutdown, omega, health, owned));
        }
        out.sort(java.util.Comparator.comparingInt(Summary::state).reversed().thenComparing(sm -> sm.id().toString()));
        return out;
    }

    /** 救済通信機: 選んだ 1 体を呼び出す。結果の翻訳キーの後ろ（recalled / not_lost / not_yours / too_many / failed）を返す */
    public static String recallOne(ServerPlayer player, UUID id) {
        MinecraftServer server = player.getServer();
        ValkyrieBackups backups = ValkyrieBackups.get(server);
        ValkyrieBackups.Entry entry = backups.get(id);
        if (entry == null) return "failed";
        boolean owned = player.getUUID().equals(entry.owner());
        if (!owned && entry.owner() != null) return "not_yours";
        if (!entry.lost() || findAnywhere(server, id) != null) return "not_lost";
        if (!owned && ownedCount(server, player.getUUID()) >= MAX_REGISTERED) return "too_many";
        Vec3 pos = player.position().add(player.getLookAngle().multiply(1.5, 0, 1.5));
        if (!safe(player.serverLevel(), pos)) pos = player.position();
        ValkyrieEntity v = restore(server, id, entry, player.serverLevel(), pos, true);
        if (v == null) return "failed";
        v.setOwnerUUID(player.getUUID());
        snapshot(v); // 戻した体の今のデータ（新しい UUID・持ち主）で記録を上書き
        return "recalled";
    }

    /**
     * 救済通信機: 不要な兵姫のバックアップを破棄する（2026-10-04 ユーザー指定。画面で警告してから）。二度と呼び出せない。
     * 稼働中なら保護を外して消し、遠くにいて読み込まれていない個体も、読み込まれたら消える。結果の翻訳キーの後ろ（discarded / not_yours / failed）を返す
     */
    public static String discardBackup(ServerPlayer player, UUID id) {
        MinecraftServer server = player.getServer();
        ValkyrieBackups backups = ValkyrieBackups.get(server);
        ValkyrieBackups.Entry entry = backups.get(id);
        if (entry == null) return "failed";
        if (entry.owner() != null && !player.getUUID().equals(entry.owner())) return "not_yours";
        ValkyrieEntity live = findAnywhere(server, id);
        if (live != null) live.releaseAndDiscard();
        release(server, id);
        backups.markDiscarded(id);
        AutomataValkyries.LOGGER.warn("[valkyrie-protect] {} discarded the backup of {}{}", player.getGameProfile().getName(), id, live != null ? " (dismissed the active valkyrie)" : "");
        return "discarded";
    }

    /** 救済通信機: その人の失われた兵姫を、バックアップからそばへ呼び出す。呼び出した数を返す */
    public static int recall(ServerPlayer player) {
        Vec3 pos = player.position().add(player.getLookAngle().multiply(1.5, 0, 1.5));
        if (!safe(player.serverLevel(), pos)) pos = player.position();
        return recall(player.getServer(), player.getUUID(), player.serverLevel(), pos);
    }

    /** 持ち主 owner の失われた兵姫を、level の pos へ呼び出す（GameTest からも呼ぶ） */
    public static int recall(MinecraftServer server, UUID owner, ServerLevel level, Vec3 pos) {
        ValkyrieBackups backups = ValkyrieBackups.get(server);
        int n = 0;
        for (Map.Entry<UUID, ValkyrieBackups.Entry> e : new ArrayList<>(backups.all().entrySet())) {
            ValkyrieBackups.Entry entry = e.getValue();
            if (!entry.lost() || !owner.equals(entry.owner()) || findAnywhere(server, e.getKey()) != null) continue;
            ValkyrieEntity v = restore(server, e.getKey(), entry, level, pos, true);
            if (v != null) {
                snapshot(v);
                n++;
            }
        }
        return n;
    }

    /** その人の失われた兵姫の数 */
    public static int lostCount(ServerPlayer player) {
        int n = 0;
        for (ValkyrieBackups.Entry entry : ValkyrieBackups.get(player.getServer()).all().values()) {
            if (entry.lost() && player.getUUID().equals(entry.owner())) n++;
        }
        return n;
    }

    /** どの次元かに、その UUID の兵姫が居るか */
    @Nullable
    public static ValkyrieEntity findAnywhere(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e instanceof ValkyrieEntity v && !DirectRead.isRemoved(v)) return v;
        }
        return null;
    }

    /** 今バックアップがある兵姫（コマンド用） */
    public static List<UUID> protectedIds(MinecraftServer server) {
        return new ArrayList<>(ValkyrieBackups.get(server).all().keySet());
    }

    public static boolean isKnown(MinecraftServer server, UUID id) {
        return ValkyrieBackups.get(server).get(id) != null;
    }
}
