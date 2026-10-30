package com.automatavalkyries.compat.tconstruct;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * TiCEX の道具（再鍛抜刀剣など）が作れない不具合の回避（2026-10-31 ユーザー報告「再鍛抜刀剣がつくれない」「ほかの素材でも作れない」）。
 * TiCEX は道具の定義（data/ticex/tinkering/tool_definitions）を Tinkers の定義の読み込みより後に登録するので、ワールドを開いた時点では定義の中身が空のまま
 * （部品の並びが空＝作業台のレシピが何にも合わない）。データパックを読み直すと中身が入る。
 * ここでは、ワールドの開始時に TiCEX の定義に中身の無い物があれば、1 度だけデータパックを読み直す。Tinkers・TiCEX のクラスは反射で触る（無ければ何もしない）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class TicexDefinitionFix {

    private TicexDefinitionFix() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!ModList.get().isLoaded("ticex") || !ModList.get().isLoaded("tconstruct")) return;
        int missing = missingTicexDefinitions();
        if (missing <= 0) return;
        MinecraftServer server = event.getServer();
        AutomataValkyries.LOGGER.info("[ticex-fix] {} TiCEX tool definitions have no data (registered after Tinkers loaded them); reloading datapacks once", missing);
        server.reloadResources(server.getPackRepository().getSelectedIds());
    }

    /** 登録されているのに中身の無い TiCEX の道具の定義の数（調べられなければ 0） */
    public static int missingTicexDefinitions() {
        try {
            Class<?> loaderClass = Class.forName("slimeknights.tconstruct.library.tools.definition.ToolDefinitionLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            var defsField = loaderClass.getDeclaredField("definitions");
            var dataField = loaderClass.getDeclaredField("dataMap");
            defsField.setAccessible(true);
            dataField.setAccessible(true);
            Map<?, ?> defs = (Map<?, ?>) defsField.get(loader);
            Map<?, ?> data = (Map<?, ?>) dataField.get(loader);
            int missing = 0;
            for (Object key : defs.keySet()) {
                if (key instanceof ResourceLocation id && id.getNamespace().equals("ticex") && (data == null || !data.containsKey(id))) missing++;
            }
            return missing;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }
}
