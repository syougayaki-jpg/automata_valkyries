package com.automatavalkyries.registry;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * コードから解除する進捗（条件が {@code minecraft:impossible} の物。表は {@code tools/gen_advancements.py}）。
 * バニラの条件では見られない出来事（兵姫の休眠など）が起きた時に呼ぶ
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class ModAdvancements {

    /** 兵姫が電力切れで休眠した（持ち主に。2026-10-30 ユーザー指定「休眠状態で解除される進捗アンドロイドは電気羊の夢を見ないを作って」） */
    public static final String ANDROID_DREAM = "story/android_dream";
    /** 兵姫にタキオンドライブを装備させた（持ち主に。同日ユーザー指定「タキオンドライブを装備する進捗あたらなければどうということはないを作って」） */
    public static final String TACHYON_DRIVE = "story/tachyon_drive";
    /** 兵姫を追従させず一人で戦術兵姫零号機を倒した（隠し。同日ユーザー指定「戦術兵姫を追従させず一人でマリスライズロストカタストロフィーを倒す誰が為の闘争を作って」） */
    public static final String SOLO_ZERO = "battle/solo_zero";
    /** 「一人で」の範囲: 零号機が倒れた時、この範囲にいるプレイヤーがちょうど 1 人 */
    public static final double SOLO_RANGE = 96.0;

    private ModAdvancements() {}

    /**
     * 零号機が倒れた時: まわり {@value #SOLO_RANGE} ブロックにいるプレイヤーがちょうど 1 人で、その人の兵姫がそのディメンションに 1 体も追従していなければ解除する
     * （止めを刺した人を見ないのは、終幕の後に倒れる時は止めの持ち主がはっきりしないため）
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof com.automatavalkyries.entity.zero.LostCatastropheEntity zero)
                || !(zero.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var players = level.getEntitiesOfClass(ServerPlayer.class, zero.getBoundingBox().inflate(SOLO_RANGE), p -> p.isAlive() && !p.isSpectator());
        if (players.size() != 1) return;
        ServerPlayer player = players.get(0);
        for (var e : level.getAllEntities()) {
            if (e instanceof com.automatavalkyries.entity.ValkyrieEntity v && player.getUUID().equals(v.getOwnerUUID())
                    && v.getMode() == com.automatavalkyries.entity.ValkyrieEntity.Mode.FOLLOW) return;
        }
        award(player, SOLO_ZERO);
    }

    /** 進捗を解除する（無い・もう解除済みなら何もしない） */
    public static void award(ServerPlayer player, String path) {
        var advancement = player.server.getAdvancements().getAdvancement(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, path));
        if (advancement == null) return;
        var progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) return;
        for (String criterion : progress.getRemainingCriteria()) player.getAdvancements().award(advancement, criterion);
    }
}
