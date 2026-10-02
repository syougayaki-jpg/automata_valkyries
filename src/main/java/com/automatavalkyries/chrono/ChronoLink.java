package com.automatavalkyries.chrono;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * 空間リンク（既存仕様書 §6.2-3・§7.6）: リンク先の座標・ディメンション・面と、向き（搬入 / 搬出 / 両方）。
 */
public record ChronoLink(BlockPos pos, ResourceKey<Level> dimension, Direction face, Mode mode) {

    /** 搬入（リンク先から引く）・搬出（リンク先へ押し込む）・両方 */
    public enum Mode {
        IN, OUT, BOTH;

        public boolean pulls() { return this != OUT; }
        public boolean pushes() { return this != IN; }
        public Mode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public ChronoLink withMode(Mode mode) {
        return new ChronoLink(pos, dimension, face, mode);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.asLong());
        tag.putString("dim", dimension.location().toString());
        tag.putByte("face", (byte) face.get3DDataValue());
        tag.putByte("mode", (byte) mode.ordinal());
        return tag;
    }

    public static ChronoLink load(CompoundTag tag) {
        return new ChronoLink(BlockPos.of(tag.getLong("pos")),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dim"))),
                Direction.from3DDataValue(tag.getByte("face")), Mode.values()[Math.min(2, Math.max(0, tag.getByte("mode")))]);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeResourceLocation(dimension.location());
        buf.writeEnum(face);
        buf.writeEnum(mode);
    }

    public static ChronoLink read(FriendlyByteBuf buf) {
        return new ChronoLink(buf.readBlockPos(), ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation()),
                buf.readEnum(Direction.class), buf.readEnum(Mode.class));
    }
}
