package com.automatavalkyries.entity;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 戦術兵姫のダミーちゃん（2026-10-08 ユーザー指定「敵に狙わせる身代わり」）。Type-V の姿をした動かない練習台で、クロノゲイザー（雑魚・ルクシオン）は
 * 兵姫と同じに狙う（{@code ChronoGazer#addTargets}）。倒れない（HP は減らない）・押されない。受けたダメージを頭上の名前に出す
 * （最後の 1 撃と合計。{@value #IDLE_TICKS} Tick 当たらなければ合計を戻す）。片付けは退去願い（右クリックでその 1 体、空中で右クリックで半径 64 の全部）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class ValkyrieDummyEntity extends PathfinderMob implements GeoEntity {

    public static final int IDLE_TICKS = 100;
    public static final double EVICT_RADIUS = 64.0;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private float last, total;
    private int sinceHit = IDLE_TICKS;

    public ValkyrieDummyEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
        updateName();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    /** 当たった量を数えるだけで、HP は減らさない（倒れない練習台） */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || !Float.isFinite(amount)) return;
        last = amount;
        total += amount;
        sinceHit = 0;
        updateName();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && sinceHit < IDLE_TICKS && ++sinceHit == IDLE_TICKS) {
            total = 0;
            last = 0;
            updateName();
        }
    }

    private void updateName() {
        Component name = Component.translatable("entity.automata_valkyries.valkyrie_dummy");
        if (total > 0) {
            name = name.copy().append(Component.translatable("entity.automata_valkyries.valkyrie_dummy.hits",
                    String.format("%,.1f", last), String.format("%,.1f", total)).withStyle(ChatFormatting.GOLD));
        }
        setCustomName(name);
        setCustomNameVisible(true);
    }

    /** 片付ける（光って消える） */
    public void evict() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY(0.5), getZ(), 20, 0.3, 0.6, 0.3, 0.05);
            server.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.6F, 1.6F);
        }
        discard();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // ---- 退去願い（アイテムは Antigravity 側なので触らず、ここで足す） ----

    /** 退去願いで右クリック: その 1 体 */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof ValkyrieDummyEntity dummy && event.getItemStack().is(ModItems.EVICTION_NOTICE.get())) {
            if (!event.getLevel().isClientSide) dummy.evict();
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    /** 退去願いを空中で右クリック: 半径 {@value #EVICT_RADIUS} の全部 */
    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide || !event.getItemStack().is(ModItems.EVICTION_NOTICE.get())) return;
        Player player = event.getEntity();
        var dummies = event.getLevel().getEntitiesOfClass(ValkyrieDummyEntity.class, player.getBoundingBox().inflate(EVICT_RADIUS));
        dummies.forEach(ValkyrieDummyEntity::evict);
        if (!dummies.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie_dummy.evicted", dummies.size()).withStyle(ChatFormatting.AQUA), true);
        }
    }
}
