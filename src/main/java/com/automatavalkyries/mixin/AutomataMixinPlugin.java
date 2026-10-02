package com.automatavalkyries.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mixin の設定のプラグイン: 起動時のシステムプロパティ {@code automata_valkyries.disableMixins}（カンマ区切り）に
 * 名前が入った Mixin を当てない（干渉の切り分け用。{@code ./gradlew runClient -PdisableMixins=compat.HyperlinkMixins}）。
 * 名前はパッケージ {@code com.automatavalkyries.mixin} からの相対で、前方一致（{@code compat.HyperlinkMixins} なら
 * その中の入れ子のクラスもすべて）。
 */
public class AutomataMixinPlugin implements IMixinConfigPlugin {

    private static final String PACKAGE = "com.automatavalkyries.mixin.";
    private Set<String> disabled = Set.of();

    @Override
    public void onLoad(String mixinPackage) {
        String value = System.getProperty("automata_valkyries.disableMixins", "");
        disabled = Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        if (!disabled.isEmpty()) System.out.println("[automata_valkyries] Mixins disabled for this launch: " + disabled);
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (disabled.isEmpty() || !mixinClassName.startsWith(PACKAGE)) return true;
        String name = mixinClassName.substring(PACKAGE.length());
        for (String off : disabled) {
            if (name.equals(off) || name.startsWith(off + "$") || name.startsWith(off + ".")) return false;
        }
        return true;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
