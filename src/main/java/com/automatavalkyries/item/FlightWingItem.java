package com.automatavalkyries.item;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.weapon.Fx;
import com.automatavalkyries.weapon.WeaponPower;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 光翼の飛翔機（2026-10-15 ユーザー指定「手に持っているあいだ電力を消費して空を飛べるアイテム」。質問で: クリエイティブのように飛ぶ・どちらの手でも・T3・満タンで 5 分くらい）。
 * 利き手かオフハンドに持っていて電力があれば、クリエイティブと同じように飛べる（ジャンプ 2 回で飛び、空中で止まれる）。速さはクリエイティブの {@value #FLY_SPEED} / 0.05 倍。
 * 飛んでいる間は毎 Tick {@value #FLY_COST} FE（ほぼ止まって浮いているだけなら {@value #HOVER_COST} FE）。蓄電 {@value #CAPACITY} FE で、
 * インベントリのルミナルコア・電池から兵装と同じように補充される（{@link WeaponPower#topUp}）。
 * 手放す・電力が切れると飛べなくなり、空中だったら 5 秒の低速落下が付く。飛ぶ許可はこのアイテムが与えた分だけを取り上げる（クリエイティブや他の手段の飛行は邪魔しない）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class FlightWingItem extends Item {

    public static final int CAPACITY = 1_200_000, FLY_COST = 200, HOVER_COST = 100;
    public static final float FLY_SPEED = 0.07F, DEFAULT_FLY_SPEED = 0.05F;
    /** このアイテムが飛ぶ許可を与えた印（プレイヤーの永続データ） */
    private static final String GRANTED = AutomataValkyries.MODID + ":flight_wing";
    private static final Map<Player, Vec3> LAST_POS = new WeakHashMap<>();

    public FlightWingItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** 満充電の 1 つ（クリエイティブのタブ用） */
    public ItemStack charged() {
        ItemStack stack = new ItemStack(this);
        ItemEnergy.set(stack, CAPACITY);
        return stack;
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return ItemEnergy.provider(stack, CAPACITY, CAPACITY / 20);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide) WeaponPower.topUp(entity, stack, CAPACITY);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return ItemEnergy.barWidth(stack, CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ItemEnergy.barColor(stack, CAPACITY);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem(); // 電力の NBT が毎 Tick 変わっても持ち直さない
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.tool_flight_wing.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(ItemEnergy.tooltip(stack, CAPACITY));
    }

    /** どちらかの手に持っている、電力の残った飛翔機（無ければ空） */
    static ItemStack held(Player player) {
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof FlightWingItem && ItemEnergy.get(stack) >= HOVER_COST) return stack;
        }
        return ItemStack.EMPTY;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) {
            trail(event.player);
        } else if (event.player instanceof ServerPlayer player) {
            update(player);
        }
    }

    /** 毎 Tick（サーバー）: 持っていれば飛ぶ許可を与えて飛んだ分の電力を使い、手放す・切れたら取り上げる。試験からも呼ぶ */
    public static void update(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        boolean special = player.isCreative() || player.isSpectator();
        CompoundTag data = player.getPersistentData();
        boolean granted = data.getBoolean(GRANTED);
        ItemStack wing = special ? ItemStack.EMPTY : held(player);
        Vec3 pos = player.position(), last = LAST_POS.put(player, pos);
        if (!wing.isEmpty()) {
            if (!granted || !abilities.mayfly || abilities.getFlyingSpeed() != FLY_SPEED) {
                abilities.mayfly = true;
                abilities.setFlyingSpeed(FLY_SPEED);
                player.onUpdateAbilities();
                data.putBoolean(GRANTED, true);
            }
            if (abilities.flying) {
                boolean moving = last != null && pos.distanceToSqr(last) > 0.05 * 0.05;
                int cost = moving ? FLY_COST : HOVER_COST;
                if (!ItemEnergy.use(wing, cost)) ItemEnergy.set(wing, 0); // 次の Tick に取り上げる
                player.fallDistance = 0.0F;
            }
        } else if (granted) {
            data.remove(GRANTED);
            if (!special) {
                if (abilities.flying && !player.onGround()) player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, true)); // 空中で切れたら低速落下
                abilities.mayfly = false;
                abilities.flying = false;
            }
            abilities.setFlyingSpeed(DEFAULT_FLY_SPEED);
            player.onUpdateAbilities();
        }
    }

    /** 飛んでいる間（クライアント）: 背の左右から光の翼の粒を流す */
    private static void trail(Player player) {
        if (!player.getAbilities().flying || player.isCreative() || player.isSpectator() || held(player).isEmpty() || player.tickCount % 2 != 0) return;
        float yaw = player.yBodyRot * net.minecraft.util.Mth.DEG_TO_RAD;
        Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw)).scale(0.35);
        Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        for (int s = -1; s <= 1; s += 2) {
            double out = 0.3 + player.getRandom().nextDouble() * 0.6;
            Vec3 p = player.position().add(0, 1.2 + player.getRandom().nextDouble() * 0.4, 0).add(back).add(side.scale(s * out));
            int color = player.getRandom().nextBoolean() ? 0x8FE6FF : 0xC8A8FF;
            player.level().addParticle(Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), color, 0.18F, 14), p.x, p.y, p.z, 0, -0.02, 0);
        }
    }

    /** 試験用: この飛翔機が飛ぶ許可を与えているか */
    public static boolean grantedTo(Player player) {
        return player.getPersistentData().getBoolean(GRANTED);
    }
}
