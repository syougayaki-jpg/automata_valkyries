package com.automatavalkyries.entity.ai;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieBody;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.weapon.WeaponUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 戦闘以外の仕草とセリフ（2026-10-29 ユーザー指定「戦術兵姫の戦闘以外のモーションやセリフを充実させる」。
 * 質問で: 待機中の仕草・場面のセリフ・兵姫どうしの会話。セリフの文は lang の valkyrie.automata_valkyries.speech.*。頻度は【仮】）。
 * <ul>
 *   <li>待機中の仕草: 立ち止まって暇なとき、ときどき（平均 30 秒に 1 回）伸び・あくび・きょろきょろ・髪を直す・鼻歌。
 *       個性（体の型）で選びやすい仕草が違う（S 元気は鼻歌・伸び、H 寡黙は髪・あくび、C 知的はきょろきょろ・髪）。3 回に 1 回くらい一言添える</li>
 *   <li>場面のセリフ: 雷・雪・夕暮れ、持ち主のいる場所（洞窟・ネザー・エンド・水の中・高い所）が変わったとき、
 *       持ち主の行動（たくさん掘る・たくさん置く・食べる・寝る・釣る。同日「他のアクションも見直して」で、作る・焼いた物を取り出す・
 *       レベルが上がる・敵を倒す・村人と取引するを足し、掘る・置くの数えを 10 秒に 8 回へ緩めた）</li>
 *   <li>兵姫どうしの会話: 同じ持ち主の兵姫どうしが 6 ブロック以内で暇なとき、ときどき（1 体あたり平均 10 分に 1 回）2 行の掛け合い。向き合って仕草も</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class ValkyrieChatter {

    /** 会話の話題の数（lang の speech.chat.<n>.a / .b） */
    public static final int CHAT_TOPICS = 10;
    /** 持ち主の行動を「たくさん」と数える数と時間（Tick） */
    static final int BUSY_COUNT = 8, BUSY_WINDOW = 200;

    /** 兵姫ごとの場面の覚え（最後に居た場所の種類・天気） */
    private static final Map<ValkyrieEntity, State> STATES = new WeakHashMap<>();
    /** 持ち主の行動の数え（置く: 数え始めた時刻と数） */
    private static final Map<UUID, long[]> BUILDING = new HashMap<>();

    private static final class State {
        String place = "";
        boolean thunder, snow, dusk;
    }

    private ValkyrieChatter() {}

    /** {@link ValkyrieDaily.Life#tick} から（20 Tick ごと、暇なときだけ） */
    static void tick(ServerLevel level, ValkyrieEntity v, Player owner, boolean ownerNear) {
        State st = STATES.computeIfAbsent(v, k -> new State());
        idleGesture(v);
        if (ownerNear) scenes(level, v, owner, st);
        if (v.getRandom().nextInt(300) == 0) chat(level, v);
    }

    // ------------------------------------------------------------------ 待機中の仕草

    static void idleGesture(ValkyrieEntity v) {
        if (v.getDeltaMovement().horizontalDistanceSqr() > 0.002 || v.isSitting() || v.getRandom().nextInt(30) != 0) return;
        int[] pool = switch (v.getBody()) {
            case S -> new int[]{ValkyrieEntity.GESTURE_HUM, ValkyrieEntity.GESTURE_HUM, ValkyrieEntity.GESTURE_STRETCH, ValkyrieEntity.GESTURE_LOOK_AROUND};
            case H -> new int[]{ValkyrieEntity.GESTURE_FIX_HAIR, ValkyrieEntity.GESTURE_YAWN, ValkyrieEntity.GESTURE_LOOK_AROUND, ValkyrieEntity.GESTURE_FIX_HAIR};
            case C -> new int[]{ValkyrieEntity.GESTURE_LOOK_AROUND, ValkyrieEntity.GESTURE_FIX_HAIR, ValkyrieEntity.GESTURE_LOOK_AROUND, ValkyrieEntity.GESTURE_STRETCH};
            case Z -> new int[]{ValkyrieEntity.GESTURE_LOOK_AROUND, ValkyrieEntity.GESTURE_STRETCH};
            default -> new int[]{ValkyrieEntity.GESTURE_STRETCH, ValkyrieEntity.GESTURE_YAWN, ValkyrieEntity.GESTURE_LOOK_AROUND,
                    ValkyrieEntity.GESTURE_FIX_HAIR, ValkyrieEntity.GESTURE_HUM};
        };
        int g = pool[v.getRandom().nextInt(pool.length)];
        if (g == ValkyrieEntity.GESTURE_YAWN && v.getBody() == ValkyrieBody.S && v.getRandom().nextBoolean()) g = ValkyrieEntity.GESTURE_STRETCH;
        v.gesture(g);
        if (g == ValkyrieEntity.GESTURE_HUM) v.emote(ValkyrieEntity.Emote.NOTE);
        if (g == ValkyrieEntity.GESTURE_YAWN) v.emote(ValkyrieEntity.Emote.SLEEP);
        if (v.getRandom().nextBoolean()) {
            v.talk(switch (g) {
                case ValkyrieEntity.GESTURE_STRETCH -> "idle_stretch";
                case ValkyrieEntity.GESTURE_YAWN -> "idle_yawn";
                case ValkyrieEntity.GESTURE_LOOK_AROUND -> "idle_look";
                case ValkyrieEntity.GESTURE_FIX_HAIR -> "idle_hair";
                default -> "idle_hum";
            });
        }
    }

    // ------------------------------------------------------------------ 場面のセリフ

    static void scenes(ServerLevel level, ValkyrieEntity v, Player owner, State st) {
        boolean thunder = level.isThundering();
        if (thunder && !st.thunder) v.talk("thunder");
        st.thunder = thunder;
        BlockPos at = owner.blockPosition();
        boolean snow = level.isRaining() && level.canSeeSky(at) && level.getBiome(at).value().coldEnoughToSnow(at);
        if (snow && !st.snow) v.talk("snow");
        st.snow = snow;
        long time = level.getDayTime() % 24000L;
        boolean dusk = time >= 12000 && time < 13000;
        if (dusk && !st.dusk && level.dimensionType().hasSkyLight()) v.talk("sunset");
        st.dusk = dusk;
        String place = place(level, owner);
        if (!place.equals(st.place)) {
            if (!st.place.isEmpty() && !place.equals("surface")) v.talk("place_" + place);
            st.place = place;
        }
    }

    /** 持ち主のいる場所の種類 */
    static String place(ServerLevel level, Player owner) {
        if (level.dimension() == Level.NETHER) return "nether";
        if (level.dimension() == Level.END) return "end";
        if (owner.isUnderWater() || owner.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat) return "ocean";
        BlockPos at = owner.blockPosition();
        if (at.getY() > 180) return "high";
        if (at.getY() < 50 && !level.canSeeSky(at)) return "cave";
        return "surface";
    }

    /** 持ち主のそば（16 ブロック）の、暇な自分の兵姫に一言ずつ（2 体目からは半分の確率） */
    static void ownerReaction(Player owner, String situation) {
        ownerReaction(owner, situation, true);
    }

    /** idle が false なら、戦っている兵姫も言う（敵を倒したときなど。機能停止・休眠中は言わない） */
    static void ownerReaction(Player owner, String situation, boolean idle) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        int n = 0;
        for (ValkyrieEntity v : level.getEntitiesOfClass(ValkyrieEntity.class, owner.getBoundingBox().inflate(16.0),
                v -> owner.getUUID().equals(v.getOwnerUUID()) && (idle ? ValkyrieDaily.idle(v) : !v.isShutdown() && !v.isAsleep()))) {
            if (n++ > 0 && v.getRandom().nextBoolean()) continue;
            if (v.talk(situation)) v.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        }
    }

    private static void countBusy(Map<UUID, long[]> map, Player player, String situation) {
        if (player.level().isClientSide || player.isCreative() && situation.equals("owner_building")) return;
        long now = player.level().getGameTime();
        long[] c = map.computeIfAbsent(player.getUUID(), k -> new long[]{now, 0});
        if (now - c[0] > BUSY_WINDOW) {
            c[0] = now;
            c[1] = 0;
        }
        if (++c[1] == BUSY_COUNT) ownerReaction(player, situation);
    }

    /**
     * 掘る・伐る・穫る（2026-10-29 ユーザー指定「採掘・伐採・農耕も釣りと同じようにして」）: 数えずに、1 回ごとに一言（同じ場面は 15 秒あける）。
     * 丸太は伐採、作物（実ったもの）は農耕、つるはしで掘る物・鉱石は採掘
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player p = event.getPlayer();
        if (p == null || p.level().isClientSide) return;
        net.minecraft.world.level.block.state.BlockState s = event.getState();
        if (s.is(net.minecraft.tags.BlockTags.LOGS)) ownerReaction(p, "owner_lumber");
        else if (s.getBlock() instanceof net.minecraft.world.level.block.CropBlock || s.is(net.minecraft.tags.BlockTags.CROPS)
                || s.getBlock() instanceof net.minecraft.world.level.block.NetherWartBlock || s.is(net.minecraft.world.level.block.Blocks.PUMPKIN)
                || s.is(net.minecraft.world.level.block.Blocks.MELON)) ownerReaction(p, "owner_farming");
        else if (s.is(net.minecraftforge.common.Tags.Blocks.ORES) || s.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)) ownerReaction(p, "owner_mining");
    }

    /** 置く: 種・苗を植えたら農耕（1 回ごと）、ほかは建築（10 秒に {@value #BUSY_COUNT} 回で一言） */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player p) || p.level().isClientSide) return;
        net.minecraft.world.level.block.Block b = event.getPlacedBlock().getBlock();
        if (b instanceof net.minecraft.world.level.block.CropBlock || b instanceof net.minecraft.world.level.block.StemBlock
                || b instanceof net.minecraft.world.level.block.NetherWartBlock) ownerReaction(p, "owner_farming");
        else countBusy(BUILDING, p, "owner_building");
    }

    /** クワで耕したら農耕 */
    @SubscribeEvent
    public static void onTill(BlockEvent.BlockToolModificationEvent event) {
        if (event.getPlayer() != null && !event.isSimulated() && event.getToolAction() == net.minecraftforge.common.ToolActions.HOE_TILL
                && !event.getPlayer().level().isClientSide) ownerReaction(event.getPlayer(), "owner_farming");
    }

    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof Player p && !p.level().isClientSide && event.getItem().isEdible()) ownerReaction(p, "owner_eating");
    }

    @SubscribeEvent
    public static void onSleep(PlayerSleepInBedEvent event) {
        if (!event.getEntity().level().isClientSide) ownerReaction(event.getEntity(), "owner_sleep");
    }

    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        if (!event.getEntity().level().isClientSide) ownerReaction(event.getEntity(), "owner_fishing");
    }

    @SubscribeEvent
    public static void onCraft(net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
        if (!event.getEntity().level().isClientSide && !event.getCrafting().isEmpty()) ownerReaction(event.getEntity(), "owner_craft");
    }

    @SubscribeEvent
    public static void onSmelt(net.minecraftforge.event.entity.player.PlayerEvent.ItemSmeltedEvent event) {
        if (!event.getEntity().level().isClientSide) ownerReaction(event.getEntity(), "owner_smelt");
    }

    @SubscribeEvent
    public static void onLevelUp(net.minecraftforge.event.entity.player.PlayerXpEvent.LevelChange event) {
        if (!event.getEntity().level().isClientSide && event.getLevels() > 0) ownerReaction(event.getEntity(), "owner_levelup");
    }

    @SubscribeEvent
    public static void onTrade(net.minecraftforge.event.entity.player.TradeWithVillagerEvent event) {
        if (!event.getEntity().level().isClientSide) ownerReaction(event.getEntity(), "owner_trade");
    }

    /** 持ち主が敵対モブを倒した（このMOD の兵姫・ボスが倒したときは別のセリフ） */
    @SubscribeEvent
    public static void onKill(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof Player p && !p.level().isClientSide && event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy) {
            ownerReaction(p, "owner_kill", false);
        }
    }

    // ------------------------------------------------------------------ 兵姫どうしの会話

    /** 同じ持ち主の暇な兵姫が 6 ブロック以内にいれば、話題を 1 つ選んで掛け合う（話しかけた側が .a、答える側が 2.5 秒後に .b） */
    static void chat(ServerLevel level, ValkyrieEntity a) {
        if (a.getOwnerUUID() == null || a.getDeltaMovement().horizontalDistanceSqr() > 0.01) return;
        ValkyrieEntity b = null;
        for (ValkyrieEntity o : level.getEntitiesOfClass(ValkyrieEntity.class, a.getBoundingBox().inflate(6.0),
                o -> o != a && a.getOwnerUUID().equals(o.getOwnerUUID()) && ValkyrieDaily.idle(o) && !o.isSitting())) {
            b = o;
            break;
        }
        if (b == null || !a.life().mayTalk("chat") || !b.life().mayTalk("chat")) return;
        int topic = a.getRandom().nextInt(CHAT_TOPICS);
        ValkyrieEntity answer = b;
        a.getLookControl().setLookAt(b, 30.0F, 30.0F);
        b.getLookControl().setLookAt(a, 30.0F, 30.0F);
        a.say("chat." + topic + ".a");
        a.gesture(ValkyrieEntity.GESTURE_WAVE);
        WeaponUtil.later(level, 50, () -> {
            if (!answer.isAlive() || answer.isShutdown() || answer.isAsleep() || !a.isAlive()) return;
            answer.getLookControl().setLookAt(a, 30.0F, 30.0F);
            answer.say("chat." + topic + ".b");
            answer.gesture(topic % 2 == 0 ? ValkyrieEntity.GESTURE_NOD : ValkyrieEntity.GESTURE_JOY);
        });
    }
}
