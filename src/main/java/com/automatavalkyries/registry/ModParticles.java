package com.automatavalkyries.registry;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.particle.DamageNumberOptions;
import com.automatavalkyries.particle.FxOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 自作エフェクトの粒子（兵装の演出）。絵は白で、出すときに色を掛ける（{@link FxOptions}）。
 * 見た目と動きはクライアントの {@code client/particle/FxParticles}。
 */
public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, AutomataValkyries.MODID);

    /** 4 方向に光が伸びるきらめき（瞬く） */
    public static final RegistryObject<FxOptions.Type> SPARK = PARTICLES.register("fx_spark", FxOptions.Type::new);
    /** 柔らかい光の玉 */
    public static final RegistryObject<FxOptions.Type> GLOW = PARTICLES.register("fx_glow", FxOptions.Type::new);
    /** 地面と平行に広がる衝撃波の輪 */
    public static final RegistryObject<FxOptions.Type> SHOCKWAVE = PARTICLES.register("fx_shockwave", FxOptions.Type::new);
    /** 三日月形の斬撃の光（roll で向き） */
    public static final RegistryObject<FxOptions.Type> SLASH = PARTICLES.register("fx_slash", FxOptions.Type::new);
    /** 時計の数字（ローマ数字）が浮かんで消える */
    public static final RegistryObject<FxOptions.Type> GLYPH = PARTICLES.register("fx_glyph", FxOptions.Type::new);
    /** 六角形の障壁の欠片 */
    public static final RegistryObject<FxOptions.Type> HEX = PARTICLES.register("fx_hex", FxOptions.Type::new);
    /** 黒い空間の破片（縁が光る） */
    public static final RegistryObject<FxOptions.Type> VOID_SHARD = PARTICLES.register("fx_void_shard", FxOptions.Type::new);
    /** チェーンソーの回る光の刃（鉤の歯の輪が高速で回る） */
    public static final RegistryObject<FxOptions.Type> SAW = PARTICLES.register("fx_saw", FxOptions.Type::new);
    /** 星: 長い十字と斜めの光芒が瞬く（ルミナス・レクイエム） */
    public static final RegistryObject<FxOptions.Type> STAR = PARTICLES.register("fx_star", FxOptions.Type::new);
    /** 星の陣: 八芒星の幾何学の陣が広がって回る（roll で向き: 0・1 = 地面と平行、2・3 = こちらを向く。偶数は右回り、奇数は左回り） */
    public static final RegistryObject<FxOptions.Type> MANDALA = PARTICLES.register("fx_mandala", FxOptions.Type::new);
    /** 光線の輪: 光線に垂直な面で広がる電光の輪（向きは速さの向きで渡す。パニッシュメント・レールガン） */
    public static final RegistryObject<FxOptions.Type> BEAM_RING = PARTICLES.register("fx_beam_ring", FxOptions.Type::new);
    /** 浮かび上がるダメージの数字 */
    public static final RegistryObject<DamageNumberOptions.Type> DAMAGE_NUMBER = PARTICLES.register("damage_number", DamageNumberOptions.Type::new);

    private ModParticles() {}
}
