package com.automatavalkyries.weapon;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.item.ItemEnergy;
import com.automatavalkyries.registry.ModParticles;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 採掘用の兵装（2026-10-03 ユーザー指定。各ティアに 1 つずつ）。耐久は無く、電力で動く（電力が無いと素手並みの速さ）。
 * <ul>
 *   <li>ドリル: つるはしとシャベルの上位互換（両方の道具で掘れる物を掘る。ネザライトの道具と同じ硬さまで）</li>
 *   <li>チェーンソー: 斧の上位互換</li>
 * </ul>
 * ティアが上がるほど速く、強く、手が届く（ブロックと相手への間合いが伸びる）。T1 から範囲で掘れる（2026-10-03 ユーザー指定）:
 * ドリルは右クリックで範囲を切り替える（1×1 → 3×3 → … → T1 は 3×3・T2 は 5×5・T3 は 7×7・T4 は 9×9 まで）、
 * チェーンソーは右クリックで木をまるごと切るかを切り替える（T1 32 本・T2 64 本・T3 128 本・T4 256 本まで）。
 * しゃがむと 1 ブロックだけ。おまけ: 殴った相手の無敵時間を貫く（{@link WeaponEvents} で攻撃の前に無敵時間を消す）。
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: T1〜T3 だけ（T4 は変わらない）。範囲モードのまま掘ると、
 * ドリルは面だけでなく奥へ向かって四角いトンネルを穿つ「紅金の掘削渦」になる（T1 5×5×奥行き 6・T2 7×7×6・T3 7×7×7、最大 343 ブロック）。
 * 手前の面はその場で、奥の層は 1 Tick に 1 層ずつ掘り進み、層ごとに紅と金の光の輪と螺旋の渦が走り、鉱石を砕くと紅金の火花が弾ける。
 * チェーンソーは木をまるごと切るモードのまま丸太を切ると「紅金の伐採旋風」になり、周りの木もまとめて切り倒す
 * （起点から水平 T1 3・T2 4・T3 6 ブロック内の丸太から辿る。T1 128・T2 192・T3 256 本まで）。1 Tick に 24 本ずつ切り、
 * 大きな回る刃の輪・横薙ぎの斬撃・飛び散る木片と葉が舞う。どちらも掘り方・ドロップ・保護（ブロックを壊すイベント・壊せない硬さ・
 * 道具の適性・電力）はふつうに掘るときと同じで、紅金の衝撃波（{@link OverloadArts#flourish}）は始めの一回だけ。
 * しゃがむ・1×1・木を 1 本ずつのモードでは変わらない。</p>
 */
public class MiningToolItem extends TacticalWeaponItem {

    public enum Kind { DRILL, CHAINSAW }

    /** ドリルで掘れるブロック（つるはし＋シャベル） */
    public static final TagKey<Block> MINEABLE_DRILL = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "mineable/drill"));

    private final Kind kind;
    private final int tier;
    private final float speed;
    private final int perBlock;
    private final Multimap<Attribute, AttributeModifier> modifiers;

    public MiningToolItem(Properties properties, Kind kind, int tier) {
        super(properties, capacityOf(tier), 6.0F + tier * 2, false);
        this.kind = kind;
        this.tier = tier;
        this.speed = new float[]{0, 12F, 20F, 32F, 60F}[tier];
        this.perBlock = new int[]{0, 50, 100, 200, 400}[tier];
        double damage = kind == Kind.DRILL ? new double[]{0, 12, 55, 120, 1000}[tier] : new double[]{0, 16, 65, 150, 1200}[tier];
        double attackSpeed = kind == Kind.DRILL ? 1.4 : 1.1;
        this.modifiers = ImmutableMultimap.of(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Tool modifier", damage - 1.0, AttributeModifier.Operation.ADDITION),
                Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Tool modifier", attackSpeed - 4.0, AttributeModifier.Operation.ADDITION),
                ForgeMod.BLOCK_REACH.get(), new AttributeModifier(BLOCK_REACH_UUID, "Tool reach", blockReach(tier), AttributeModifier.Operation.ADDITION),
                ForgeMod.ENTITY_REACH.get(), new AttributeModifier(ENTITY_REACH_UUID, "Tool reach", tier * 0.5, AttributeModifier.Operation.ADDITION));
    }

    private static final java.util.UUID BLOCK_REACH_UUID = java.util.UUID.fromString("3e4f5a6b-7c8d-4e9f-a0b1-c2d3e4f5a6b7");
    private static final java.util.UUID ENTITY_REACH_UUID = java.util.UUID.fromString("4f5a6b7c-8d9e-4fa0-b1c2-d3e4f5a6b7c8");

    /** ブロックへの間合いの伸び（T1 +1.5・T2 +3・T3 +4.5・T4 +6） */
    public static double blockReach(int tier) {
        return tier * 1.5;
    }

    private static int capacityOf(int tier) {
        return new int[]{0, 100_000, 500_000, 2_000_000, 10_000_000}[tier];
    }

    public Kind kind() { return kind; }
    public int tier() { return tier; }
    public int perBlock() { return perBlock; }

    /** ドリルの範囲の最大の半径（T1 1 = 3×3、T2 2 = 5×5、T3 3 = 7×7、T4 4 = 9×9） */
    public int maxRadius() {
        return kind == Kind.DRILL ? tier : 0;
    }

    /** いまの範囲の半径（0 = 1 ブロックだけ。右クリックで切り替え、初めは 3×3） */
    public int areaRadius(ItemStack stack) {
        if (kind != Kind.DRILL) return 0;
        CompoundTag tag = stack.getTag();
        int r = tag == null || !tag.contains("Area") ? 1 : tag.getInt("Area");
        return Math.max(0, Math.min(maxRadius(), r));
    }

    /** チェーンソーで一度に切る丸太の数（T1 32・T2 64・T3 128・T4 256） */
    public int fellLimit() {
        return kind == Kind.CHAINSAW ? new int[]{0, 32, 64, 128, 256}[tier] : 0;
    }

    /** チェーンソーで木をまるごと切るか（右クリックで切り替え、初めは切る） */
    public boolean felling(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return kind == Kind.CHAINSAW && (tag == null || !tag.contains("Fell") || tag.getBoolean("Fell"));
    }

    /** 右クリック: 範囲を切り替える（ドリルは 1×1 → 3×3 → … → 最大 → 1×1、チェーンソーは木をまるごと ⇔ 1 本） */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Component mode;
        if (kind == Kind.DRILL) {
            int next = (areaRadius(stack) + 1) % (maxRadius() + 1);
            stack.getOrCreateTag().putInt("Area", next);
            mode = Component.translatable("gui.automata_valkyries.tool_area", 2 * next + 1, 2 * next + 1);
        } else {
            boolean next = !felling(stack);
            stack.getOrCreateTag().putBoolean("Fell", next);
            mode = Component.translatable(next ? "gui.automata_valkyries.tool_fell_on" : "gui.automata_valkyries.tool_fell_off", fellLimit());
        }
        if (!level.isClientSide()) {
            player.displayClientMessage(mode.copy().withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    protected void appendStatus(ItemStack stack, List<Component> tooltip) {
        int r = areaRadius(stack);
        tooltip.add((kind == Kind.DRILL
                ? Component.translatable("gui.automata_valkyries.tool_area", 2 * r + 1, 2 * r + 1)
                : Component.translatable(felling(stack) ? "gui.automata_valkyries.tool_fell_on" : "gui.automata_valkyries.tool_fell_off", fellLimit()))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("gui.automata_valkyries.tool_reach", String.format("%.1f", blockReach(tier))).withStyle(ChatFormatting.DARK_AQUA));
    }

    private boolean suits(BlockState state) {
        return kind == Kind.DRILL ? state.is(MINEABLE_DRILL) : state.is(BlockTags.MINEABLE_WITH_AXE);
    }

    private boolean powered(ItemStack stack) {
        return ItemEnergy.get(stack) >= perBlock;
    }

    // ---- エンチャント（2026-10-09 ユーザー指定「エンチャントをドリルとチェーンソーにつけられるように」） ----
    // 耐久が無いので普通の道具の判定（耐久があること）では付けられない。採掘（効率強化・幸運・シルクタッチ）と武器（ダメージ増加・ドロップ増加・
    // 火属性・ノックバック等）の系統、消滅の呪いを付けられる。耐久力・修繕は耐久が無いので付けない。範囲掘り・伐採で壊したブロックにも幸運・シルクタッチが効く

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    /** エンチャントのしやすさ（ティアで上がる。ダイヤの道具は 10、金は 22） */
    @Override
    public int getEnchantmentValue() {
        return new int[]{0, 12, 15, 18, 22}[tier];
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, net.minecraft.world.item.enchantment.Enchantment enchantment) {
        var category = enchantment.category;
        if (category == net.minecraft.world.item.enchantment.EnchantmentCategory.BREAKABLE) return false;
        return category == net.minecraft.world.item.enchantment.EnchantmentCategory.DIGGER
                || category == net.minecraft.world.item.enchantment.EnchantmentCategory.WEAPON
                || category == net.minecraft.world.item.enchantment.EnchantmentCategory.VANISHABLE;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (!suits(state)) return 1.0F;
        return powered(stack) ? speed : 1.5F;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return suits(state) && TierSortingRegistry.isCorrectTierForDrops(Tiers.NETHERITE, state);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction action) {
        return kind == Kind.DRILL ? action == ToolActions.PICKAXE_DIG || action == ToolActions.SHOVEL_DIG : action == ToolActions.AXE_DIG;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getAttributeModifiers(slot, stack);
    }

    /** 範囲で掘っている最中（範囲の中のブロックを掘るときに、もう一度範囲を広げないため） */
    private static boolean expanding;

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        if (!(level instanceof ServerLevel server) || !suits(state) || state.getDestroySpeed(level, pos) == 0) return true;
        if (!WeaponPower.use(entity, stack, perBlock)) return true;
        Vec3 at = Vec3.atCenterOf(pos);
        Fx.burst(server, Fx.opt(ModParticles.SPARK.get(), colorOf(tier, server.random.nextInt(6)), 0.2F, 7), at, 3, 0.2);
        if (kind == Kind.CHAINSAW && !expanding) {
            Vec3 face = at.add(entity.getEyePosition().subtract(at).normalize().scale(0.55));
            Fx.saw(server, face, colorOf(tier, server.random.nextInt(6)), 0.55F + tier * 0.08F, 8);
        }
        if (expanding || entity.isShiftKeyDown() || !(entity instanceof ServerPlayer player)) return true;
        expanding = true;
        try {
            boolean art = overloadArt(player);
            if (areaRadius(stack) > 0) {
                if (art) bore(server, player, stack, pos);
                else mineArea(server, player, stack, pos);
            }
            if (felling(stack) && state.is(BlockTags.LOGS)) {
                if (art) whirlwind(server, player, pos);
                else fell(server, player, stack, pos);
            }
        } finally {
            expanding = false;
        }
        playUse(entity, stack);
        return true;
    }

    /** ドリル: 向いている面に平行な正方形をまとめて掘る */
    private void mineArea(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos center) {
        Direction face = Direction.getNearest(player.getLookAngle().x, player.getLookAngle().y, player.getLookAngle().z);
        int r = areaRadius(stack);
        for (int a = -r; a <= r; a++) for (int b = -r; b <= r; b++) {
            if (a == 0 && b == 0) continue;
            BlockPos p = switch (face.getAxis()) {
                case X -> center.offset(0, a, b);
                case Y -> center.offset(a, 0, b);
                case Z -> center.offset(a, b, 0);
            };
            tryBreak(level, player, stack, p);
        }
    }

    /** チェーンソー: つながった丸太をまとめて切る（上下・斜めも含む） */
    private void fell(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        int cut = 0;
        while (!queue.isEmpty() && cut < fellLimit()) {
            BlockPos p = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = p.offset(dx, dy, dz);
                if (!seen.add(n) || !level.getBlockState(n).is(BlockTags.LOGS)) continue;
                queue.add(n);
            }
            if (!p.equals(start) && tryBreak(level, player, stack, p)) cut++;
        }
        if (cut > 0) level.playSound(null, start, SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    // ---- オーバーロード（T1〜T3） ----

    /** オーバーロードの技の変化が起きるか（T1〜T3 だけ。T4 の道具は変わらない） */
    private boolean overloadArt(LivingEntity user) {
        return tier <= 3 && OverloadArts.active(user);
    }

    /** オーバーロードのドリルのトンネルの半径（T1 2 = 5×5・T2 3 = 7×7・T3 3 = 7×7） */
    private int boreRadius() {
        return new int[]{0, 2, 3, 3}[Math.min(3, tier)];
    }

    /** オーバーロードのドリルのトンネルの奥行き（T1 6・T2 6・T3 7。最大 7×7×7 = 343 ブロック） */
    private int boreDepth() {
        return new int[]{0, 6, 6, 7}[Math.min(3, tier)];
    }

    /** オーバーロードのチェーンソーで一度に切る丸太の数（T1 128・T2 192・T3 256） */
    private int whirlLimit() {
        return new int[]{0, 128, 192, 256}[Math.min(3, tier)];
    }

    /** オーバーロードのチェーンソーで周りの木を探す水平の半径（T1 3・T2 4・T3 6） */
    private int whirlRadius() {
        return new int[]{0, 3, 4, 6}[Math.min(3, tier)];
    }

    /** 予約した続きの作業を、まだ同じ道具を持ったまま同じ世界にいるときだけ動かす */
    private boolean stillHolding(ServerLevel level, ServerPlayer player) {
        return !player.isRemoved() && player.isAlive() && player.level() == level && player.getMainHandItem().getItem() == this;
    }

    /** ドリル（オーバーロード）: 向いている方へ四角いトンネルを穿つ。手前の面はすぐ、奥は 1 Tick に 1 層ずつ */
    private void bore(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos center) {
        Direction face = Direction.getNearest(player.getLookAngle().x, player.getLookAngle().y, player.getLookAngle().z);
        int r = boreRadius();
        OverloadArts.flourish(level, player, 4.0F + tier * 2.0F);
        level.playSound(null, center, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 1.6F);
        boreLayer(level, player, stack, center, face, r, 0);
        for (int k = 1; k < boreDepth(); k++) {
            int layer = k;
            WeaponUtil.later(level, k, () -> {
                if (!stillHolding(level, player)) return;
                boolean was = expanding;
                expanding = true;
                try {
                    boreLayer(level, player, player.getMainHandItem(), center, face, r, layer);
                } finally {
                    expanding = was;
                }
            });
        }
    }

    /** トンネルの 1 層（layer = 奥への段。0 は掘った面）を掘り、光の輪と螺旋の渦を走らせる */
    private void boreLayer(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos center, Direction face, int r, int layer) {
        BlockPos base = center.relative(face, layer);
        int ores = 0;
        for (int a = -r; a <= r; a++) for (int b = -r; b <= r; b++) {
            if (layer == 0 && a == 0 && b == 0) continue; // 掘った本体
            BlockPos p = switch (face.getAxis()) {
                case X -> base.offset(0, a, b);
                case Y -> base.offset(a, 0, b);
                case Z -> base.offset(a, b, 0);
            };
            boolean ore = level.getBlockState(p).is(Tags.Blocks.ORES);
            if (tryBreak(level, player, stack, p) && ore && ores++ < 6) {
                Vec3 at = Vec3.atCenterOf(p);
                OverloadArts.impact(level, at);
                Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), colorOf(tier), 0.35F, 12), at, 10, 0.5);
            }
        }
        // 紅と金の光の輪（層ごとに交互）と、3 本の腕の螺旋の渦
        Vec3 c = Vec3.atCenterOf(base);
        Vec3 axis = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Fx.beamRing(level, c, axis, layer % 2 == 0 ? Overload.CRIMSON : Overload.GOLD, r + 0.8F, 10);
        Vec3 u = face.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 v = axis.cross(u);
        for (int arm = 0; arm < 3; arm++) for (int i = 0; i < 6; i++) {
            double ang = layer * 0.9 + arm * (Math.PI * 2 / 3) + i * 0.35;
            double rad = 0.4 + i * (r + 0.5) / 6.0;
            Vec3 at = c.add(u.scale(Math.cos(ang) * rad)).add(v.scale(Math.sin(ang) * rad));
            Vec3 tangent = u.scale(-Math.sin(ang)).add(v.scale(Math.cos(ang))).scale(0.12).add(axis.scale(0.08));
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), arm == 1 ? Overload.GOLD : Overload.CRIMSON, 0.35F, 14), at, tangent);
        }
        Fx.spray(level, Fx.opt(ModParticles.SPARK.get(), Overload.HOT, 0.25F, 8), c, 8, r * 0.4, 0.15);
        level.playSound(null, base, SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.6F, 0.5F + layer * 0.1F);
    }

    /** チェーンソー（オーバーロード）: 周りの木もまとめて切り倒す。丸太を先に探しておき、1 Tick に 24 本ずつ切る */
    private void whirlwind(ServerLevel level, ServerPlayer player, BlockPos start) {
        int limit = whirlLimit(), hr = whirlRadius();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> order = new ArrayList<>();
        seen.add(start);
        // 起点の丸太につながる丸太を先に、続いて周りの木の丸太
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos n = start.offset(dx, dy, dz);
            if (seen.add(n) && level.getBlockState(n).is(BlockTags.LOGS)) queue.add(n);
        }
        for (int dy = -1; dy <= 2; dy++) for (int dx = -hr; dx <= hr; dx++) for (int dz = -hr; dz <= hr; dz++) {
            if (dx * dx + dz * dz > hr * hr) continue;
            BlockPos p = start.offset(dx, dy, dz);
            if (seen.add(p) && level.getBlockState(p).is(BlockTags.LOGS)) queue.add(p);
        }
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState();
        boolean foundLeaf = false;
        while (!queue.isEmpty() && order.size() < limit) {
            BlockPos p = queue.poll();
            order.add(p);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = p.offset(dx, dy, dz);
                if (!seen.add(n)) continue;
                BlockState s = level.getBlockState(n);
                if (s.is(BlockTags.LOGS)) queue.add(n);
                else if (!foundLeaf && s.is(BlockTags.LEAVES)) {
                    leaf = s;
                    foundLeaf = true;
                }
            }
        }
        if (order.isEmpty()) return;
        // 始めの一回: 足元の衝撃波・大きな回る刃の輪・横薙ぎの斬撃
        OverloadArts.flourish(level, player, 3.0F + hr * 1.5F);
        Vec3 c = Vec3.atCenterOf(start);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            Vec3 at = c.add(Math.cos(ang) * hr * 0.7, 0.3, Math.sin(ang) * hr * 0.7);
            Fx.saw(level, at, i % 2 == 0 ? Overload.CRIMSON : Overload.GOLD, 1.6F + tier * 0.5F, 14);
        }
        Fx.slash(level, c, Overload.CRIMSON, 3.0F + hr, 12, 0.0F);
        Fx.slash(level, c.add(0, 0.6, 0), Overload.GOLD, 2.5F + hr, 12, (float) Math.PI);
        level.playSound(null, start, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.5F);
        BlockState leafState = leaf;
        int batch = 24;
        for (int from = 0, step = 0; from < order.size(); from += batch, step++) {
            List<BlockPos> part = order.subList(from, Math.min(order.size(), from + batch));
            Runnable cut = () -> {
                if (!stillHolding(level, player)) return;
                boolean was = expanding;
                expanding = true;
                try {
                    int n = 0;
                    for (BlockPos p : part) {
                        BlockState s = level.getBlockState(p);
                        if (!tryBreak(level, player, player.getMainHandItem(), p)) continue;
                        Vec3 at = Vec3.atCenterOf(p);
                        if (n++ % 4 == 0) {
                            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), at.x, at.y, at.z, 10, 0.4, 0.4, 0.4, 0.3);
                            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, leafState), at.x, at.y + 1.0, at.z, 8, 1.2, 0.8, 1.2, 0.2);
                            Fx.saw(level, at, n % 8 == 1 ? Overload.GOLD : Overload.CRIMSON, 1.0F + tier * 0.3F, 8);
                        }
                    }
                    if (n > 0) {
                        Vec3 mid = Vec3.atCenterOf(part.get(part.size() / 2));
                        OverloadArts.impact(level, mid);
                        level.playSound(null, BlockPos.containing(mid), SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F);
                        level.playSound(null, BlockPos.containing(mid), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
                    }
                } finally {
                    expanding = was;
                }
            };
            if (step == 0) cut.run();
            else WeaponUtil.later(level, step, cut);
        }
    }

    /** 1 ブロック掘る（ドロップ・イベント・保護はふつうに掘ったときと同じ）。掘れたら true */
    private boolean tryBreak(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !suits(state) || state.getDestroySpeed(level, pos) < 0 || !powered(stack)) return false;
        return player.gameMode.destroyBlock(pos);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level) {
            Vec3 at = new Vec3(target.getX(), target.getY(0.6), target.getZ());
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), colorOf(tier, level.random.nextInt(6)), 0.25F, 8), at, 8, 0.35);
            if (overloadArt(attacker)) OverloadArts.impact(level, at);
            if (kind == Kind.CHAINSAW) {
                Fx.saw(level, at.add(attacker.getEyePosition().subtract(at).normalize().scale(target.getBbWidth() * 0.6)), colorOf(tier, level.random.nextInt(6)), 0.8F + tier * 0.1F, 8);
            }
            level.playSound(null, target.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.7F, 1.6F);
            playUse(attacker, stack);
        }
        target.invulnerableTime = 0; // 続けて殴っても毎回入る
        return true;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return true;
    }

    /** ティアの光の色（マシンのブロックと同じ: T1 水色・T2 橙・T3 桃色・T4 淡い金） */
    public static int colorOf(int tier) {
        return new int[]{0xFFFFFF, 0x6AF0FF, 0xFFA040, 0xFF5A9A, 0xFFF0B0}[tier];
    }

    /** k 番目の光の色（T4 は虹を巡る） */
    public static int colorOf(int tier, int k) {
        int[] rainbow = {0xFF7AB0, 0xFFB070, 0xFFF080, 0x90F0A0, 0x70D8FF, 0xB090FF};
        return tier == 4 ? rainbow[Math.floorMod(k, rainbow.length)] : colorOf(tier);
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(perBlock)};
    }
}
