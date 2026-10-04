package com.automatavalkyries.weapon;

import com.automatavalkyries.item.ItemEnergy;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.text.NumberFormat;
import java.util.List;
import java.util.function.Consumer;

/**
 * 兵装モジュール（既存仕様書 §7.2、兵器ユニット設計書 docs/SPEC_WEAPON_UNITS.md）の共通部分。
 * <ul>
 *   <li>電力: 内蔵バッテリー（{@link #capacity}）＋持ち物のルミナルコア（{@link WeaponPower}）。マシンの充電スロットでも充電できる</li>
 *   <li>見た目: GeckoLib の 3D モデル（{@code geo/item/<id>.geo.json}・{@code animations/item/<id>.animation.json}・
 *       {@code textures/item/<id>.png}）。アニメーションは常に流す {@code animation.weapon.idle} と、使ったときの {@code animation.weapon.use}</li>
 *   <li>両手兵装（{@link #twoHanded}）はオフハンドが空のときだけ使える</li>
 *   <li>重量は兵姫に持たせたときだけ効く（兵姫の実装後）。今はツールチップに出すだけ</li>
 * </ul>
 */
public abstract class TacticalWeaponItem extends Item implements GeoItem {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.weapon.idle");

    protected final int capacity;
    protected final float weight;
    protected final boolean twoHanded;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected TacticalWeaponItem(Properties properties, int capacity, float weight, boolean twoHanded) {
        super(properties.stacksTo(1));
        this.capacity = capacity;
        this.weight = weight;
        this.twoHanded = twoHanded;
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public int capacity() { return capacity; }

    /** その兵装の容量（エネルギー容量のアップグレードで増える。{@link WeaponUpgrades}） */
    public int capacity(ItemStack stack) {
        return (int) Math.min(Integer.MAX_VALUE, Math.round(capacity * WeaponUpgrades.mult(stack, WeaponUpgrades.Stat.ENERGY)));
    }
    public float weight() { return weight; }
    public boolean twoHanded() { return twoHanded; }

    /** 満充電の兵装（クリエイティブのタブ用） */
    public ItemStack charged() {
        ItemStack stack = new ItemStack(this);
        if (capacity > 0) ItemEnergy.set(stack, capacity);
        return stack;
    }

    /** 両手兵装なら、利き手で持っていてオフハンドが空か。だめならプレイヤーに知らせる */
    protected boolean canWield(LivingEntity user, InteractionHand hand) {
        if (!twoHanded) return true;
        if (hand == InteractionHand.MAIN_HAND && user.getOffhandItem().isEmpty()) return true;
        if (user instanceof Player player && !user.level().isClientSide()) {
            player.displayClientMessage(Component.translatable("gui.automata_valkyries.two_handed").withStyle(ChatFormatting.RED), true);
        }
        return false;
    }

    protected void notEnoughPower(LivingEntity user) {
        if (user instanceof Player player && !user.level().isClientSide()) {
            player.displayClientMessage(Component.translatable("gui.automata_valkyries.no_power").withStyle(ChatFormatting.RED), true);
        }
    }

    /** 使ったときのアニメーション（サーバーから、見ている全員に流す） */
    protected void playUse(LivingEntity user, ItemStack stack, String animation) {
        if (user.level() instanceof ServerLevel server) {
            triggerAnim(user, GeoItem.getOrAssignId(stack, server), "main", animation);
        }
    }

    protected void playUse(LivingEntity user, ItemStack stack) {
        playUse(user, stack, "use");
    }

    /** 銃の反動の大きさ（0 なら銃ではない: 構え・反動の動きを付けない） */
    public float recoil() {
        return 0;
    }

    /** 撃った側のクライアントで、反動の動きと視点の跳ね上がりを起こす */
    protected void clientKick(LivingEntity user) {
        if (user.level().isClientSide() && recoil() > 0) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.automatavalkyries.client.WeaponClient.kick(this, recoil()));
        }
    }

    /** 追加のトリガー用アニメーション（ディフレクターの展開など）。名前は animation.weapon.<名前> */
    protected String[] extraAnimations() {
        return new String[0];
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide() && capacity > 0) WeaponPower.topUp(entity, stack, capacity(stack));
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return capacity > 0 ? ItemEnergy.provider(stack, () -> capacity(stack), Math.max(1, capacity / 20)) : null;
    }

    @Override public boolean isBarVisible(ItemStack stack) { return capacity > 0; }
    @Override public int getBarWidth(ItemStack stack) { return ItemEnergy.barWidth(stack, capacity(stack)); }
    @Override public int getBarColor(ItemStack stack) { return ItemEnergy.barColor(stack, capacity(stack)); }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem(); // 電力・弾数の NBT が変わっても持ち直さない
    }

    /** オーバーロードの兵装は輝く（{@link Overload}） */
    @Override
    public boolean isFoil(ItemStack stack) {
        return Overload.is(stack) || super.isFoil(stack);
    }

    /** オーバーロードの兵装は名前の後ろに「オーバーロード」（紅） */
    @Override
    public Component getName(ItemStack stack) {
        Component name = super.getName(stack);
        if (!Overload.is(stack)) return name;
        return Component.empty().append(name).append(Component.translatable("item.automata_valkyries.overload_suffix")
                .withStyle(s -> s.withColor(Overload.CRIMSON).withBold(true)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (Overload.is(stack)) {
            boolean t4 = Overload.isT4(this);
            tooltip.add(Component.translatable(this instanceof ChargedWeaponItem ? "gui.automata_valkyries.overload_charged" : "gui.automata_valkyries.overload",
                    String.format("%.0f", Overload.damageMult(this)), Math.round((1 - Overload.CHARGE_MULT) * 100)).withStyle(s -> s.withColor(Overload.GOLD)));
            if (t4) tooltip.add(Component.translatable("gui.automata_valkyries.overload_t4", String.format("%.1f", Overload.RANGE_MULT)).withStyle(s -> s.withColor(Overload.CRIMSON)));
        }
        if (capacity > 0) tooltip.add(ItemEnergy.tooltip(stack, capacity(stack)));
        WeaponUpgrades.appendTooltip(stack, tooltip);
        appendStatus(stack, tooltip);
        String id = ForgeRegistries.ITEMS.getKey(this).getPath();
        tooltip.add(Component.translatable("item.automata_valkyries." + id + ".desc", descArgs()).withStyle(ChatFormatting.GRAY));
        Component kind = Component.translatable(twoHanded ? "gui.automata_valkyries.two_handed_tag" : "gui.automata_valkyries.one_handed_tag");
        tooltip.add(Component.translatable("gui.automata_valkyries.weight", String.valueOf(weight)).append(" / ").append(kind)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 弾数などの追加の行 */
    protected void appendStatus(ItemStack stack, List<Component> tooltip) {}

    protected Object[] descArgs() {
        return new Object[0];
    }

    protected static String format(int value) {
        return NumberFormat.getIntegerInstance().format(value);
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE)));
        AnimationController<TacticalWeaponItem> main = new AnimationController<>(this, "main", 0, state -> PlayState.STOP);
        main.triggerableAnim("use", RawAnimation.begin().thenPlay("animation.weapon.use"));
        for (String name : extraAnimations()) {
            main.triggerableAnim(name, RawAnimation.begin().thenPlayAndHold("animation.weapon." + name));
        }
        controllers.add(main);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = com.automatavalkyries.client.WeaponRenderer.create(TacticalWeaponItem.this);
                return renderer;
            }

            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                if (recoil() <= 0) return null;
                return twoHanded ? com.automatavalkyries.client.WeaponClient.RIFLE_POSE : com.automatavalkyries.client.WeaponClient.GUN_POSE;
            }

            @Override
            public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.player.LocalPlayer player,
                                                   net.minecraft.world.entity.HumanoidArm arm, ItemStack stack, float partialTick, float equipProcess, float swingProcess) {
                if (com.automatavalkyries.client.WeaponClient.slamTransform(pose, arm, stack, partialTick)) return true;
                if (recoil() <= 0) return false;
                return com.automatavalkyries.client.WeaponClient.handTransform(pose, arm, stack, partialTick, equipProcess);
            }
        });
    }
}
