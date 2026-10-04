package com.automatavalkyries.client;

import net.minecraft.client.gui.screens.Screen;

/** 説明の表示に使うキーの状態（クライアントだけ） */
public final class TooltipKeys {

    private TooltipKeys() {}

    public static boolean shift() {
        return Screen.hasShiftDown();
    }
}
