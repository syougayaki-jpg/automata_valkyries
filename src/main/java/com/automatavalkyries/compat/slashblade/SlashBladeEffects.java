package com.automatavalkyries.compat.slashblade;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.compat.IsolationStrike;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * このMODの刀の特殊効果（SE。2026-10-30。【仮】で私が決めた）。抜刀剣の決まりどおり、プレイヤーは経験値のレベルが要る（{@link SpecialEffect#isEffective}）。
 * 刀の写し（刀掛けでの移し替え）はできない
 */
public final class SlashBladeEffects {

    public static final DeferredRegister<SpecialEffect> SPECIAL_EFFECTS = DeferredRegister.create(SpecialEffect.REGISTRY_KEY, AutomataValkyries.MODID);

    /** 星刀「アストライト」: 星明かり — 斬った相手が 10 秒光る。手に持つと暗視 */
    public static final RegistryObject<SpecialEffect> STARLIGHT = SPECIAL_EFFECTS.register("starlight", () -> new SpecialEffect(0, false, false));
    /** 響刀「フォノニウム」: 共振 — 同じ相手に続けて当てるほど追加の傷（最大 +6） */
    public static final RegistryObject<SpecialEffect> RESONANCE = SPECIAL_EFFECTS.register("resonance", () -> new SpecialEffect(5, false, false));
    /** 蒼刀「セルリアス」: 蒼の守り — 手に持つと耐性 I */
    public static final RegistryObject<SpecialEffect> AZURE_GUARD = SPECIAL_EFFECTS.register("azure_guard", () -> new SpecialEffect(5, false, false));
    /** 光刃「ルミナル・スターリング」: 澄光 — 斬った相手が光り、使い手が少し癒える */
    public static final RegistryObject<SpecialEffect> CLEAR_LIGHT = SPECIAL_EFFECTS.register("clear_light", () -> new SpecialEffect(15, false, false));
    /** 城刀「クロノ・フォートレス」: 不動 — 手に持つと耐性 II */
    public static final RegistryObject<SpecialEffect> IMMOVABLE = SPECIAL_EFFECTS.register("immovable", () -> new SpecialEffect(15, false, false));
    /** 迅刀「タキオン・アクセル」: 加速 — 手に持つと採掘速度上昇 II・移動速度上昇 I */
    public static final RegistryObject<SpecialEffect> ACCELERATION = SPECIAL_EFFECTS.register("acceleration", () -> new SpecialEffect(15, false, false));
    /** 蝕刀「エクリプス・バイオメタル」: 吸命 — 斬った相手を枯らし（ウィザー II）、使い手が癒える */
    public static final RegistryObject<SpecialEffect> LIFE_DRAIN = SPECIAL_EFFECTS.register("life_drain", () -> new SpecialEffect(15, false, false));
    /** 星刻神刀「ステラ・クロノダイム」: 星刻の隔絶 — 刀の一撃と抜刀術の一撃が隔絶ダメージになる */
    public static final RegistryObject<SpecialEffect> STELLAR_ISOLATION = SPECIAL_EFFECTS.register("stellar_isolation", () -> new SpecialEffect(30, false, false));

    private SlashBladeEffects() {}

    static boolean active(ISlashBladeState state, RegistryObject<SpecialEffect> effect, Entity user) {
        if (!state.hasSpecialEffect(effect.getId())) return false;
        return !(user instanceof Player player) || SpecialEffect.isEffective(effect.get(), player.experienceLevel);
    }

    static boolean active(ItemStack stack, RegistryObject<SpecialEffect> effect, Entity user) {
        if (!(stack.getItem() instanceof ItemSlashBlade)) return false;
        return stack.getCapability(ItemSlashBlade.BLADESTATE).map(state -> active(state, effect, user)).orElse(false);
    }

    /** 抜刀剣の催しに付ける受け手（{@link SlashBladeCompat} が抜刀剣があるときだけ登録する） */
    public static final class Handlers {
        private Handlers() {}

        @SubscribeEvent
        public static void onHit(SlashBladeEvent.HitEvent event) {
            LivingEntity user = event.getUser(), target = event.getTarget();
            if (user == null || target == null || user.level().isClientSide) return;
            ISlashBladeState state = event.getSlashBladeState();
            if (active(state, STARLIGHT, user)) target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
            if (active(state, CLEAR_LIGHT, user)) {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
                user.heal(1.0F);
            }
            if (active(state, LIFE_DRAIN, user)) {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                user.heal(2.0F);
            }
            if (active(state, RESONANCE, user)) resonate(user, target);
        }

        @SubscribeEvent
        public static void onUpdate(SlashBladeEvent.UpdateEvent event) {
            if (!event.isSelected() || event.getLevel().isClientSide || !(event.getEntity() instanceof LivingEntity user)) return;
            if (user.tickCount % 20 != 0) return;
            ISlashBladeState state = event.getSlashBladeState();
            if (active(state, STARLIGHT, user)) refresh(user, MobEffects.NIGHT_VISION, 300, 0);
            if (active(state, AZURE_GUARD, user)) refresh(user, MobEffects.DAMAGE_RESISTANCE, 60, 0);
            if (active(state, IMMOVABLE, user)) refresh(user, MobEffects.DAMAGE_RESISTANCE, 60, 1);
            if (active(state, ACCELERATION, user)) {
                refresh(user, MobEffects.DIG_SPEED, 60, 1);
                refresh(user, MobEffects.MOVEMENT_SPEED, 60, 0);
            }
        }

        /** 星刻の隔絶: 刀を主の手に持った使い手の近接の一撃、または使い手の抜刀術の飛び物（次元斬・ドライブなど）の一撃を隔絶ダメージにする */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onHurt(LivingHurtEvent event) {
            if (IsolationStrike.applying()) return;
            var source = event.getSource();
            if (!(source.getEntity() instanceof LivingEntity attacker)) return;
            Entity direct = source.getDirectEntity();
            boolean melee = direct == attacker;
            boolean art = direct instanceof mods.flammpfeil.slashblade.entity.Projectile projectile && projectile.getOwner() == attacker;
            if (!melee && !art) return;
            if (!active(attacker.getMainHandItem(), STELLAR_ISOLATION, attacker)) return;
            IsolationStrike.replace(event, attacker);
        }

        private static void refresh(LivingEntity user, MobEffect effect, int ticks, int amplifier) {
            MobEffectInstance current = user.getEffect(effect);
            if (current == null || current.getDuration() < ticks / 2 || current.getAmplifier() < amplifier) {
                user.addEffect(new MobEffectInstance(effect, ticks, amplifier, true, false, true));
            }
        }

        /** 共振: 3 秒以内に同じ相手へ当て続けると段が上がり（最大 6）、段の数だけ魔法の追加の傷 */
        private static void resonate(LivingEntity user, LivingEntity target) {
            CompoundTag data = target.getPersistentData();
            String key = AutomataValkyries.MODID + ":resonance";
            CompoundTag tag = data.getCompound(key);
            long now = target.level().getGameTime();
            int stacks = (now - tag.getLong("time") <= 60 && user.getUUID().equals(tag.hasUUID("by") ? tag.getUUID("by") : null)) ? Math.min(6, tag.getInt("stacks") + 1) : 1;
            tag.putLong("time", now);
            tag.putInt("stacks", stacks);
            tag.putUUID("by", user.getUUID());
            data.put(key, tag);
            if (stacks > 1) {
                target.invulnerableTime = 0;
                target.hurt(user.damageSources().indirectMagic(user, user), stacks - 1);
            }
        }
    }

}
