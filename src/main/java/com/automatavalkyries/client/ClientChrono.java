package com.automatavalkyries.client;

import com.automatavalkyries.chrono.ChronoMenu;
import com.automatavalkyries.chrono.VoidRepositoryBlockEntity;
import com.automatavalkyries.network.RepositoryContentsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** クロノリンク・マシンのクライアント側の受け取り */
public final class ClientChrono {

    private ClientChrono() {}

    /** 開いているリポジトリの画面の中身を差し替える */
    public static void applyRepository(RepositoryContentsPacket packet) {
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.containerMenu instanceof ChronoMenu menu) || menu.containerId != packet.containerId()) return;
        menu.repository.clear();
        menu.repositoryStock = packet.stock();
        menu.repositoryCompress = packet.compress();
        menu.repositoryPylonPower = packet.pylonPower();
        for (int i = 0; i < packet.keys().size(); i++) {
            ItemStack key = packet.keys().get(i);
            menu.repository.add(key.isEmpty() ? null : new VoidRepositoryBlockEntity.Entry(key, packet.counts()[i])); // 空きは null
        }
    }
}
