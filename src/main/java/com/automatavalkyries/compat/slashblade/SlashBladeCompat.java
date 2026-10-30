package com.automatavalkyries.compat.slashblade;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/**
 * 抜刀剣（SlashBlade: Resharped）との連携の入口（2026-10-30 ユーザー指定「抜刀剣連携を強化する」→ 独自の抜刀術・特殊効果・専用の見た目、最上位の刀に隔絶）。
 * 抜刀剣のクラスに触るのはこのパッケージの中だけ。抜刀剣が無い時は {@link #init} から先を読み込まない
 */
public final class SlashBladeCompat {

    public static final boolean LOADED = ModList.get().isLoaded("slashblade");

    private SlashBladeCompat() {}

    /** メインクラスの組み立ての中で呼ぶ（抜刀剣があるときだけ中身を読み込む） */
    public static void init(IEventBus modBus) {
        if (!LOADED) return;
        Impl.init(modBus);
    }

    private static final class Impl {
        static void init(IEventBus modBus) {
            SlashBladeArts.COMBO_STATES.register(modBus);
            SlashBladeArts.SLASH_ARTS.register(modBus);
            SlashBladeEffects.SPECIAL_EFFECTS.register(modBus);
            MinecraftForge.EVENT_BUS.register(SlashBladeEffects.Handlers.class);
        }
    }
}
