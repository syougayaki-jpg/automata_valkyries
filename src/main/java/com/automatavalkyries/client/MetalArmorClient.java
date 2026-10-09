package com.automatavalkyries.client;

import com.automatavalkyries.item.armor.MetalArmorMaterial;
import com.automatavalkyries.item.armor.MetalArmorSets;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/** 固有金属の防具の説明用（クライアントだけ）: 自分が今着ている一式 */
@OnlyIn(Dist.CLIENT)
public final class MetalArmorClient {

    private MetalArmorClient() {}

    @Nullable
    public static MetalArmorMaterial wornSet() {
        var player = Minecraft.getInstance().player;
        return player != null ? MetalArmorSets.fullSet(player) : null;
    }
}
