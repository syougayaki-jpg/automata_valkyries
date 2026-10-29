package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModCreativeTabs;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** クリエイティブのタブ（2026-10-29 整理）: このMODのアイテムは描くためだけの物を除いてどれかのタブに 1 回だけ並ぶ。中身を logs/creative-tabs.txt に書き出す */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class CreativeTabGameTests {

    @GameTest(template = "empty")
    public static void everyItemInExactlyOneTab(GameTestHelper helper) {
        var params = new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, true, helper.getLevel().registryAccess());
        Set<Item> seen = new HashSet<>();
        StringBuilder dump = new StringBuilder();
        for (RegistryObject<CreativeModeTab> tab : ModCreativeTabs.CREATIVE_MODE_TABS.getEntries()) {
            tab.get().buildContents(params);
            dump.append("== ").append(tab.getId().getPath()).append('\n');
            Set<Item> inTab = new HashSet<>();
            for (ItemStack stack : tab.get().getDisplayItems()) {
                dump.append(ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath()).append(stack.hasTag() ? " " + stack.getTag() : "").append('\n');
                if (!inTab.add(stack.getItem())) continue; // 同じアイテムの別の形（マシンの T2〜T4）は同じタブの中ならよい
                helper.assertTrue(seen.add(stack.getItem()), "in two tabs: " + stack.getItem());
            }
        }
        try {
            Files.writeString(Path.of("logs", "creative-tabs.txt"), dump, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        List<String> missing = ModItems.ITEMS.getEntries().stream().map(RegistryObject::get)
                .filter(i -> !seen.contains(i) && ModCreativeTabs.group(i) != ModCreativeTabs.Group.HIDDEN)
                .map(i -> ForgeRegistries.ITEMS.getKey(i).getPath()).toList();
        helper.assertTrue(missing.isEmpty(), "not in any tab: " + missing);
        helper.succeed();
    }
}
