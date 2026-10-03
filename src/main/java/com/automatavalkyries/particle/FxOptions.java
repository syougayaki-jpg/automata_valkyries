package com.automatavalkyries.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * このMOD の自作エフェクトの粒子の設定: 色（0xRRGGBB）・大きさ・寿命（Tick）・回転（ラジアン、斬撃の向きなど）。
 * 色は白い絵に掛けるので、どの粒子も好きな色で出せる。
 */
public record FxOptions(ParticleType<FxOptions> type, int color, float scale, int lifetime, float roll) implements ParticleOptions {

    public static Codec<FxOptions> codec(ParticleType<FxOptions> type) {
        return RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("color").forGetter(FxOptions::color),
                Codec.FLOAT.fieldOf("scale").forGetter(FxOptions::scale),
                Codec.INT.fieldOf("lifetime").forGetter(FxOptions::lifetime),
                Codec.FLOAT.optionalFieldOf("roll", 0F).forGetter(FxOptions::roll)
        ).apply(i, (color, scale, lifetime, roll) -> new FxOptions(type, color, scale, lifetime, roll)));
    }

    @SuppressWarnings("deprecation")
    public static final ParticleOptions.Deserializer<FxOptions> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public FxOptions fromCommand(ParticleType<FxOptions> type, StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            int color = (int) Long.parseLong(reader.readUnquotedString(), 16);
            reader.expect(' ');
            float scale = reader.readFloat();
            reader.expect(' ');
            int lifetime = reader.readInt();
            return new FxOptions(type, color, scale, lifetime, 0F);
        }

        @Override
        public FxOptions fromNetwork(ParticleType<FxOptions> type, FriendlyByteBuf buf) {
            return new FxOptions(type, buf.readInt(), buf.readFloat(), buf.readVarInt(), buf.readFloat());
        }
    };

    @Override
    public ParticleType<?> getType() {
        return type;
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeInt(color);
        buf.writeFloat(scale);
        buf.writeVarInt(lifetime);
        buf.writeFloat(roll);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %06x %.2f %d", ForgeRegistries.PARTICLE_TYPES.getKey(type), color, scale, lifetime);
    }

    /** 色・大きさ・寿命を持つ粒子の種類 */
    public static final class Type extends ParticleType<FxOptions> {
        private final Codec<FxOptions> codec = FxOptions.codec(this);

        @SuppressWarnings("deprecation")
        public Type() {
            super(true, DESERIALIZER);
        }

        @Override
        public Codec<FxOptions> codec() {
            return codec;
        }
    }
}
