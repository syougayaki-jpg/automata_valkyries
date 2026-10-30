package com.automatavalkyries.item.armor;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.menu.StellaTuneMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * ステラ・クロノダイムの防具（2026-10-26 ユーザー指定「ステラクロノダイム防具は一番強いがバニラを逸脱しない範囲で利便性でカスタム出来るように」。
 * 質問で: 画面で入切・頭は暗視と水中呼吸・胴は採掘速度上昇と耐火・脚は移動速度と跳躍力・足は段差と落下軽減と水中の移動）。
 * 強さはネザライトと同じ（{@link MetalArmorMaterial#STELLACHRONODYME}）。持ってスニーク＋右クリックで機能を選ぶ画面（{@link StellaTuneMenu}）。
 * 機能はその部位を着ているだけで効き（一式は要らない）、どれもバニラの効果・エンチャントの強さまで:
 * <ul>
 *   <li>頭: 暗視・水中呼吸（ポーションと同じ）</li>
 *   <li>胴: 採掘速度上昇 I・II（ビーコンと同じ）・火炎耐性</li>
 *   <li>脚: 移動速度上昇 I・II・跳躍力上昇 I・II（ポーションと同じ）</li>
 *   <li>足: 1 ブロックの段差を歩いて上る（馬と同じ）・落下ダメージ -48%（落下耐性 IV と同じ）・水中を地上と同じ速さで歩く（水中歩行 III と同じ）</li>
 * </ul>
 * 効果は粒を出さない一時のもので、切る・脱ぐとすぐ消える
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class StellaArmorItem extends MetalArmorItem {

    /** 選べる機能（部位・タグの名前・段の数（1 なら入切）・効果） */
    public enum Tune {
        NIGHT_VISION(Type.HELMET, 1, MobEffects.NIGHT_VISION),
        WATER_BREATHING(Type.HELMET, 1, MobEffects.WATER_BREATHING),
        HASTE(Type.CHESTPLATE, 2, MobEffects.DIG_SPEED),
        FIRE_RESISTANCE(Type.CHESTPLATE, 1, MobEffects.FIRE_RESISTANCE),
        SPEED(Type.LEGGINGS, 2, MobEffects.MOVEMENT_SPEED),
        JUMP(Type.LEGGINGS, 2, MobEffects.JUMP),
        STEP(Type.BOOTS, 1, null),
        FEATHER_FALL(Type.BOOTS, 1, null),
        DEPTH_STRIDE(Type.BOOTS, 1, null);

        public final Type part;
        public final int max;
        @Nullable
        public final MobEffect effect;

        Tune(Type part, int max, @Nullable MobEffect effect) {
            this.part = part;
            this.max = max;
            this.effect = effect;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static List<Tune> of(Type part) {
            List<Tune> out = new ArrayList<>();
            for (Tune t : values()) if (t.part == part) out.add(t);
            return out;
        }
    }

    /** 落下耐性 IV と同じだけ減らす */
    public static final float FEATHER_FALL_KEEP = 0.52F;
    private static final UUID STEP_ID = UUID.fromString("f3a2b0c1-d2e3-44f5-a607-8192a3b4c5d6");
    private static final UUID STRIDE_ID = UUID.fromString("04b3c1d2-e3f4-4506-b718-92a3b4c5d6e7");

    public StellaArmorItem(Type type, Properties properties) {
        super(MetalArmorMaterial.STELLACHRONODYME, type, properties);
    }

    /** その機能の今の段（0 は切） */
    public static int level(ItemStack stack, Tune tune) {
        CompoundTag tag = stack.getTagElement("Tune");
        return tag == null ? 0 : Math.min(tune.max, tag.getInt(tune.key()));
    }

    /** 次の段へ（最後の次は切） */
    public static void cycle(ItemStack stack, Tune tune) {
        stack.getOrCreateTagElement("Tune").putInt(tune.key(), (level(stack, tune) + 1) % (tune.max + 1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return super.use(level, player, hand);                       // ふつうの右クリックは着る
        if (player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new StellaTuneMenu(id, inv, hand), stack.getHoverName()), buf -> buf.writeEnum(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (Tune t : Tune.of(getType())) {
            int lv = level(stack, t);
            Component value = lv == 0 ? Component.literal("OFF") : t.max == 1 ? Component.literal("ON") : Component.translatable("enchantment.level." + lv);
            tooltip.add(Component.translatable("gui.automata_valkyries.stella_tune." + t.key()).append(": ").append(value)
                    .withStyle(lv == 0 ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip.automata_valkyries.stella_armor.tune").withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ 効かせる

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        apply(event.player);
    }

    /** 着ている部位の機能を効かせ、切った・脱いだ機能を外す */
    public static void apply(Player player) {
        modifier(player, ForgeMod.STEP_HEIGHT_ADDITION.get(), STEP_ID, "stella step", 0.5, worn(player, Tune.STEP) > 0);
        modifier(player, ForgeMod.SWIM_SPEED.get(), STRIDE_ID, "stella depth strider", 2.0, worn(player, Tune.DEPTH_STRIDE) > 0 && player.isInWater());   // 水の中の進みを 3 倍（水中歩行 III くらい）
        if (player.tickCount % 10 != 0) return;
        // この防具が付けた効果を名前で覚えておき、覚えている物だけを外す（2026-10-31 ユーザー指定「ティア４兵装を持った時に一瞬火炎耐性がでたりきえたりする」:
        // 見分けを「粒の出ない環境の効果で 300 Tick 以下」にしていたので、T4 兵装の加護（同じ形で 60 Tick）まで防具の物と見て 10 Tick ごとに外していた）
        net.minecraft.nbt.CompoundTag mine = player.getPersistentData().getCompound(APPLIED_KEY);
        for (Tune t : Tune.values()) {
            if (t.effect == null) continue;
            String key = String.valueOf(net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(t.effect));
            int lv = worn(player, t);
            MobEffectInstance cur = player.getEffect(t.effect);
            boolean ours = mine.getBoolean(key) && cur != null && cur.isAmbient() && !cur.isVisible();
            if (cur == null) mine.remove(key);            // 切れた・ほかで消えた
            if (lv > 0) {
                if (cur == null || ours && (cur.getDuration() < 220 || cur.getAmplifier() != lv - 1)) {
                    if (ours && cur.getAmplifier() != lv - 1) player.removeEffect(t.effect);
                    player.addEffect(new MobEffectInstance(t.effect, 300, lv - 1, true, false, true));
                    mine.putBoolean(key, true);
                }
            } else if (ours) {
                player.removeEffect(t.effect);
                mine.remove(key);
            }
        }
        player.getPersistentData().put(APPLIED_KEY, mine);
    }

    /** この防具が付けた効果の名前（プレイヤーの永続データ） */
    public static final String APPLIED_KEY = "automata_valkyries:stella_armor_effects";

    /** 着ている部位のその機能の段（着ていなければ 0） */
    public static int worn(Player player, Tune tune) {
        ItemStack piece = player.getItemBySlot(tune.part.getSlot());
        return piece.getItem() instanceof StellaArmorItem ? level(piece, tune) : 0;
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && worn(player, Tune.FEATHER_FALL) > 0) event.setDamageMultiplier(event.getDamageMultiplier() * FEATHER_FALL_KEEP);
    }

    private static void modifier(Player player, Attribute attribute, UUID id, String name, double amount, boolean on) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        boolean has = instance.getModifier(id) != null;
        if (on && !has) instance.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
        else if (!on && has) instance.removeModifier(id);
    }
}
