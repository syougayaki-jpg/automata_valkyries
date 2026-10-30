package com.automatavalkyries.chrono;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.ValkyrieConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Set;

/**
 * T4（ネバーラスト）のクロノリンク・マシンの解禁（2026-10-31 ユーザー指定「タクティカルアーモリー以外のティア４マシンレシピはタイムルーラー撃破後に解禁で」→
 * 同日「ティア４タクティカルアーモリーもタイムルーラー後に」で全マシン）。
 * タイム・ルーラーを倒した進捗（{@code battle/time_ruler}）を持つプレイヤーだけが、T4 のマシンを作業台で作れ、T4 の強化キットをマシンに使え、
 * 設計図カードに T4 のマシンのレシピを書ける。アイテムポケットの T4 はマシンではないので前のまま作れる。
 * クリエイティブは解禁済み扱い。設定 {@code machines.t4RequiresTimeRuler} で切れる
 */
public final class T4Unlock {

    public static final ResourceLocation ADVANCEMENT = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "battle/time_ruler");

    /** 解禁まで作れない T4 のマシンのレシピ（アイテムポケットは含めない） */
    public static final Set<ResourceLocation> GATED_RECIPES = Set.of(
            id("charging_dock_t4"), id("chrono_accelerator_t4"), id("chrono_fabricator_t4"), id("chrono_rewinder_t4"), id("chrono_smelter_t4"),
            id("luminal_adapter_t4"), id("luminal_reactor_t4"), id("tactical_armory_t4"), id("tether_pylon_t4"), id("void_repository_t4"));

    private T4Unlock() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, path);
    }

    public static boolean enabled() {
        try {
            return ValkyrieConfig.T4_REQUIRES_TIME_RULER.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** そのプレイヤーが T4 のマシンを解禁しているか（サーバーでだけ正しく分かる。クライアントでは true） */
    public static boolean unlocked(Player player) {
        if (!enabled() || player.getAbilities().instabuild) return true;
        if (!(player instanceof ServerPlayer sp)) return true;
        var advancement = sp.server.getAdvancements().getAdvancement(ADVANCEMENT);
        return advancement == null || sp.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    public static boolean gated(Recipe<?> recipe) {
        return recipe != null && GATED_RECIPES.contains(recipe.getId());
    }

    /** 作れない・使えない時に出す文 */
    public static Component lockedMessage() {
        return Component.translatable("message.automata_valkyries.t4_locked").withStyle(ChatFormatting.RED);
    }
}
