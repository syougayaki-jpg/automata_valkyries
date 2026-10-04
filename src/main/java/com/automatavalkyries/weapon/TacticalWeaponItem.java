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

    /**
     * 説明（2026-10-04 ユーザー指定で整理）: ① ティア・持ち方・重量 ② 今の性能（攻撃力・攻撃速度・チャージ・クールタイム・電力・弾数）
     * ③ カスタム（アップグレード）④ オーバーロード化しているか（していればその効果）⑤ 詳しい説明（Shift を押している間だけ）⑥ フレーバーテキスト。
     * 同じ内容になるバニラの「利き手に持ったとき」の行は出さない（{@link #getDefaultTooltipHideFlags}）
     */
    /**
     * 弾の威力（2026-10-05 ユーザー指定「銃の武器攻撃力を弾の威力で表記、複数発射なら 1 発のダメージ × 弾数」）: {1 発のダメージ, 弾数}。
     * 銃・砲・弓の兵装が上書きする（近接の攻撃力を持たない兵装の説明に出す）。null なら出さない
     */
    @Nullable
    public float[] shotDamage() {
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String id = ForgeRegistries.ITEMS.getKey(this).getPath();
        // ① ティア・持ち方・重量
        int tier = WeaponTiers.of(this);
        String kindKey = id.startsWith("tool_") ? "tool" : id.startsWith("module_") ? "module" : "weapon";
        tooltip.add(Component.translatable("gui.automata_valkyries.tip.tier", tier, Component.translatable("gui.automata_valkyries.tip.kind." + kindKey),
                Component.translatable(twoHanded ? "gui.automata_valkyries.two_handed_tag" : "gui.automata_valkyries.one_handed_tag"), String.valueOf(weight))
                .withStyle(s -> s.withColor(TIER_COLORS[Math.max(1, Math.min(4, tier))])));
        // ② 今の性能
        double damageMult = WeaponUpgrades.mult(stack, WeaponUpgrades.Stat.DAMAGE) * (Overload.is(stack) ? Overload.damageMult(this) : 1.0);
        double attack = attribute(stack, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, 1.0);
        if (attack > 1.0) {
            Component line = Component.translatable("gui.automata_valkyries.tip.attack", number(attack * damageMult));
            if (damageMult != 1.0) line = line.copy().append(Component.translatable("gui.automata_valkyries.tip.mult", number(damageMult)).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(stat(line));
            double speed = attribute(stack, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, 4.0) * WeaponUpgrades.mult(stack, WeaponUpgrades.Stat.SPEED);
            tooltip.add(stat(Component.translatable("gui.automata_valkyries.tip.speed", number(speed))));
        } else if (shotDamage() != null) {
            float[] shot = shotDamage();
            String each = shot[0] >= Integer.MAX_VALUE ? "2,147,483,647" : number(shot[0] * damageMult);
            Component line = shot[1] > 1 ? Component.translatable("gui.automata_valkyries.tip.shot_multi", each, (int) shot[1])
                    : Component.translatable("gui.automata_valkyries.tip.shot", each);
            if (damageMult != 1.0 && shot[0] < Integer.MAX_VALUE) line = line.copy().append(Component.translatable("gui.automata_valkyries.tip.mult", number(damageMult)).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(stat(line));
        } else if (damageMult != 1.0) {
            tooltip.add(stat(Component.translatable("gui.automata_valkyries.tip.damage_mult", number(damageMult))));
        }
        if (this instanceof ChargedWeaponItem charged) {
            tooltip.add(stat(Component.translatable("gui.automata_valkyries.tip.charge", seconds(charged.chargeTicks(stack)))));
        }
        int cooldown = cooldownTicks();
        if (cooldown > 0) tooltip.add(stat(Component.translatable("gui.automata_valkyries.tip.cooldown", seconds(WeaponUpgrades.ticks(stack, cooldown)))));
        if (capacity > 0) tooltip.add(stat(ItemEnergy.tooltip(stack, capacity(stack))));
        List<Component> status = new java.util.ArrayList<>();
        appendStatus(stack, status);
        for (Component c : status) tooltip.add(stat(c));
        // ③ カスタム
        tooltip.add(WeaponUpgrades.summary(stack));
        // ④ オーバーロード
        if (Overload.is(stack)) {
            tooltip.add(Component.translatable("gui.automata_valkyries.tip.overload_on").withStyle(s -> s.withColor(Overload.CRIMSON).withBold(true)));
            tooltip.add(indent(Component.translatable(this instanceof ChargedWeaponItem ? "gui.automata_valkyries.overload_charged" : "gui.automata_valkyries.overload",
                    String.format("%.0f", Overload.damageMult(this)), Math.round((1 - Overload.CHARGE_MULT) * 100)).withStyle(s -> s.withColor(Overload.GOLD))));
            if (Overload.isT4(this)) {
                tooltip.add(indent(Component.translatable("gui.automata_valkyries.overload_t4", String.format("%.1f", Overload.RANGE_MULT)).withStyle(s -> s.withColor(Overload.CRIMSON))));
            }
        } else {
            tooltip.add(Component.translatable("gui.automata_valkyries.tip.overload_off").withStyle(ChatFormatting.DARK_GRAY));
        }
        // ⑤ 詳しい説明（Shift）
        if (shiftDown()) {
            for (String line : wrap(formatted("item.automata_valkyries." + id + ".desc", descArgs()))) {
                tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("gui.automata_valkyries.tip.shift").withStyle(ChatFormatting.DARK_GRAY));
        }
        // ⑥ フレーバーテキスト
        String flavorKey = "item.automata_valkyries." + id + ".flavor";
        if (net.minecraft.locale.Language.getInstance().has(flavorKey)) {
            for (String line : wrap(net.minecraft.locale.Language.getInstance().getOrDefault(flavorKey))) {
                tooltip.add(Component.literal(line).withStyle(s -> s.withColor(0x9A7AC8).withItalic(true)));
            }
        }
    }

    private static final int[] TIER_COLORS = {0xFFFFFF, 0x7FE0FF, 0xFFA040, 0xFF5A9A, 0xFFE070};

    /** 性能の行の頭 */
    private static Component stat(Component line) {
        return Component.literal(" ▸ ").withStyle(ChatFormatting.DARK_GRAY).append(line.copy().withStyle(s -> s.getColor() == null ? s.withColor(ChatFormatting.WHITE) : s));
    }

    private static Component indent(Component line) {
        return Component.literal("   ").append(line);
    }

    private static String number(double v) {
        return v >= 100 || Math.abs(v - Math.rint(v)) < 1.0E-6 ? NumberFormat.getIntegerInstance().format(Math.round(v)) : String.format("%.1f", v);
    }

    private static String seconds(int ticks) {
        return String.format("%.2f", ticks / 20.0).replaceAll("0$", "").replaceAll("\\.0$", "");
    }

    /** 利き手に持ったときの属性の値（プレイヤーの基本値 base を足す） */
    private double attribute(ItemStack stack, net.minecraft.world.entity.ai.attributes.Attribute attribute, double base) {
        double v = base;
        for (net.minecraft.world.entity.ai.attributes.AttributeModifier m : getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND, stack).get(attribute)) {
            if (m.getOperation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION) v += m.getAmount();
        }
        return v;
    }

    /** 技・連射の待ち時間（Tick。無ければ 0）。既定はクラスの定数（COOLDOWN・SLASH_INTERVAL・BLINK_COOLDOWN）から */
    public int cooldownTicks() {
        for (String name : new String[]{"COOLDOWN", "SLASH_INTERVAL", "BLINK_COOLDOWN"}) {
            try {
                java.lang.reflect.Field f = getClass().getField(name);
                if (f.getType() == int.class && java.lang.reflect.Modifier.isStatic(f.getModifiers())) return f.getInt(null);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return 0;
    }

    /** 説明を翻訳して引数を入れる（クライアントの言語） */
    private static String formatted(String key, Object[] args) {
        String pattern = net.minecraft.locale.Language.getInstance().getOrDefault(key);
        try {
            return String.format(pattern, args);
        } catch (java.util.IllegalFormatException e) {
            return pattern;
        }
    }

    /** 読みやすく折り返す（文ごとに行を分け、長い文は日本語なら 30 文字、英語なら 52 文字くらいで折る） */
    static List<String> wrap(String text) {
        List<String> lines = new java.util.ArrayList<>();
        for (String sentence : text.split("(?<=[。！？])|(?<=[.!?])\\s+")) {
            String t = sentence.strip();
            if (t.isEmpty()) continue;
            boolean cjk = t.codePoints().anyMatch(c -> c >= 0x3000);
            if (cjk) {
                while (t.length() > 30) {
                    int cut = 30;
                    for (int k = 30; k > 18; k--) if ("、・（）".indexOf(t.charAt(k - 1)) >= 0) { cut = k; break; }
                    lines.add(t.substring(0, cut));
                    t = t.substring(cut);
                }
                lines.add(t);
            } else {
                StringBuilder line = new StringBuilder();
                for (String word : t.split(" ")) {
                    if (line.length() + word.length() + 1 > 52 && line.length() > 0) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    if (line.length() > 0) line.append(' ');
                    line.append(word);
                }
                if (line.length() > 0) lines.add(line.toString());
            }
        }
        return lines;
    }

    /** Shift を押しているか（説明はクライアントでだけ作る） */
    private static boolean shiftDown() {
        return net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient() && com.automatavalkyries.client.TooltipKeys.shift();
    }

    /** バニラの「利き手に持ったとき: 攻撃力・攻撃速度」の行は、② と同じなので出さない */
    @Override
    public int getDefaultTooltipHideFlags(ItemStack stack) {
        return ItemStack.TooltipPart.MODIFIERS.getMask();
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
