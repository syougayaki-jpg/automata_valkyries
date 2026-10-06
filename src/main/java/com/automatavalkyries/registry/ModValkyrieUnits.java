package com.automatavalkyries.registry;

import com.automatavalkyries.item.unit.ValkyrieUnits;
import com.automatavalkyries.item.unit.ValkyrieUnits.BackpackKind;
import com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind;
import com.automatavalkyries.item.unit.ValkyrieUnits.FramePart;
import com.automatavalkyries.item.unit.ValkyrieUnits.FrameType;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 戦術兵姫の装備ユニット（外装フレーム・背部ユニット・ロジックチップ。2026-10-06、{@link ValkyrieUnits}）。
 * 名前: 外装 {@code frame_<型>_<部位>}（ガリアは {@code frame_gallia_fortress}）、背部 {@code backpack_<種類>}、チップ {@code chip_<種類>}
 */
public final class ModValkyrieUnits {

    private ModValkyrieUnits() {}

    public static final Map<FrameType, Map<FramePart, RegistryObject<Item>>> FRAMES = new EnumMap<>(FrameType.class);
    public static final Map<BackpackKind, RegistryObject<Item>> BACKPACKS = new EnumMap<>(BackpackKind.class);
    public static final Map<ChipKind, RegistryObject<Item>> CHIPS = new EnumMap<>(ChipKind.class);
    /** 全部（創造タブ用） */
    public static final List<RegistryObject<Item>> ALL = new ArrayList<>();

    static {
        for (FrameType type : FrameType.values()) {
            Map<FramePart, RegistryObject<Item>> parts = new EnumMap<>(FramePart.class);
            if (type == FrameType.GALLIA) {
                parts.put(FramePart.FULL, reg("frame_" + type.id, () -> new ValkyrieUnits.FrameItem(new Item.Properties(), type, FramePart.FULL)));
            } else {
                for (FramePart part : new FramePart[]{FramePart.HEAD, FramePart.CHEST, FramePart.LEGS}) {
                    parts.put(part, reg("frame_" + type.id + "_" + part.id, () -> new ValkyrieUnits.FrameItem(new Item.Properties(), type, part)));
                }
            }
            FRAMES.put(type, parts);
        }
        for (BackpackKind kind : BackpackKind.values()) {
            BACKPACKS.put(kind, reg("backpack_" + kind.id, () -> new ValkyrieUnits.BackpackItem(new Item.Properties(), kind)));
        }
        for (ChipKind kind : ChipKind.values()) {
            CHIPS.put(kind, reg("chip_" + kind.id, () -> new ValkyrieUnits.ChipItem(new Item.Properties(), kind)));
        }
    }

    private static RegistryObject<Item> reg(String name, java.util.function.Supplier<Item> item) {
        RegistryObject<Item> o = ModItems.ITEMS.register(name, item);
        ALL.add(o);
        return o;
    }

    /** クラスを読み込んで登録する（メインクラスから） */
    public static void init() {}
}
