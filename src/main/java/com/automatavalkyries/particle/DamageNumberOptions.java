package com.automatavalkyries.particle;

import com.automatavalkyries.registry.ModParticles;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

/**
 * 浮かび上がるダメージの数字（カタストロフ・バスターのカンストダメージなど）。value をそのまま大きく出す。
 * color の上の 8 bit が {@link #SEGMENTS} なら、7 セグの文字で描く（2026-10-26）
 */
public record DamageNumberOptions(long value, int color) implements ParticleOptions {

    /** color に重ねる印: 7 セグの文字で描く */
    public static final int SEGMENTS = 0x01000000;

    public boolean segments() {
        return (color & 0xFF000000) == SEGMENTS;
    }

    public static final Codec<DamageNumberOptions> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("value").forGetter(DamageNumberOptions::value),
            Codec.INT.fieldOf("color").forGetter(DamageNumberOptions::color)
    ).apply(i, DamageNumberOptions::new));

    @SuppressWarnings("deprecation")
    public static final ParticleOptions.Deserializer<DamageNumberOptions> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public DamageNumberOptions fromCommand(ParticleType<DamageNumberOptions> type, StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            long value = reader.readLong();
            return new DamageNumberOptions(value, 0xFFD166);
        }

        @Override
        public DamageNumberOptions fromNetwork(ParticleType<DamageNumberOptions> type, FriendlyByteBuf buf) {
            return new DamageNumberOptions(buf.readLong(), buf.readInt());
        }
    };

    @Override
    public ParticleType<?> getType() {
        return ModParticles.DAMAGE_NUMBER.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeLong(value);
        buf.writeInt(color);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "automata_valkyries:damage_number %d", value);
    }

    public static final class Type extends ParticleType<DamageNumberOptions> {
        @SuppressWarnings("deprecation")
        public Type() {
            super(true, DESERIALIZER);
        }

        @Override
        public Codec<DamageNumberOptions> codec() {
            return CODEC;
        }
    }
}
