package com.automatavalkyries.registry;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.joml.Vector3f;

import java.util.List;

/** 上位合金ごとの粒子（アイテムとブロックで共通） */
public final class ModEffects {

    private ModEffects() {}

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    /** ルミナル・スターリング: 白い光の粒と金の粒 */
    public static final List<ParticleOptions> LUMINAL_STERLING = List.of(ParticleTypes.END_ROD, dust(0xFFD166, 0.6F));
    /** クロノ・フォートレス: 紫の時空のゆらぎ */
    public static final List<ParticleOptions> CHRONO_FORTRESS = List.of(ParticleTypes.REVERSE_PORTAL, dust(0x8070A6, 0.7F));
    /** タキオン・アクセル: 電光と小さな炎 */
    public static final List<ParticleOptions> TACHYON_ACCEL = List.of(ParticleTypes.ELECTRIC_SPARK, ParticleTypes.SMALL_FLAME);
    /** エクリプス・バイオメタル: 緑の光と暗い胞子 */
    public static final List<ParticleOptions> ECLIPSE_BIOMETAL = List.of(ParticleTypes.GLOW, dust(0x3A6A44, 0.8F));
    /** 量子TAC集積回路: 電光とシアンの粒 */
    public static final List<ParticleOptions> QUANTUM_CIRCUIT = List.of(ParticleTypes.ELECTRIC_SPARK, dust(0x4CC9F0, 0.5F));
    /** ステラ・クロノダイム: 星の光・金・紫の時空・きらめき */
    public static final List<ParticleOptions> STELLACHRONODYME = List.of(ParticleTypes.END_ROD, dust(0xFFE6A0, 0.7F),
            ParticleTypes.REVERSE_PORTAL, ParticleTypes.WAX_ON);

    /** クロノ系（クロノスクラップ・歪んだ破片・オーブ・スター）: 逆さの時空の粒と淡い紫 */
    public static final List<ParticleOptions> CHRONO = List.of(ParticleTypes.REVERSE_PORTAL, dust(0x9A8CC8, 0.6F));
    /** ナノ系（リアナノマシン・オーブ・スター）: 緑の光と小さな機械の粒 */
    public static final List<ParticleOptions> NANO = List.of(ParticleTypes.GLOW, dust(0x6FD1A0, 0.5F));
    /** ルミナル系のスター: 白い光と金の粒 */
    public static final List<ParticleOptions> LUMINAL = List.of(ParticleTypes.END_ROD, dust(0xFFD166, 0.6F));
}
