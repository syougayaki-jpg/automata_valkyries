package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.AutomataValkyries;
import com.google.gson.JsonElement;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.ChainedJsonException;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * Fantasy Ending の起動時の落ち（2026-10-03。このMOD が無くても落ちる、FE 2.7.20 自身の不具合）の回避。
 * <p>
 * FE の {@code MShaderInstance} は uniform を自前の {@code MUniform} で作るため {@code parseUniformNode} を上書きしているが、vanilla では
 * {@code private} なので上書きは呼ばれない（private の呼び出しは仮想呼び出しにならない）。uniform は vanilla の {@code Uniform} のまま作られ、
 * FE の {@code getUniform} が {@code MUniform} へ決め打ちで変換して {@code ClassCastException} になり、シェーダーの登録で起動が止まる。
 * </p>
 * vanilla のコンストラクタの {@code parseUniformNode} の呼び出しを差し替え、下位のクラスが同じ名前・引数の public なメソッドを宣言していれば
 * そちらを呼ぶ（FE が意図した上書きの動き）。宣言していなければ vanilla のまま。FE が無い環境では何も変わらない。
 */
public final class FantasyEndingMixins {

    private FantasyEndingMixins() {}

    /** vanilla の private な {@code parseUniformNode} を呼ぶ */
    @Mixin(ShaderInstance.class)
    public interface ShaderInstanceInvoker {
        @Invoker("parseUniformNode")
        void automata_valkyries$parseUniformNode(JsonElement element) throws ChainedJsonException;
    }

    @Mixin(ShaderInstance.class)
    public abstract static class ShaderUniforms {

        /** 実行時の {@code parseUniformNode} の名前（開発環境では公式名、配布環境では SRG 名） */
        private static final String PARSE_NAME = runtimeName();

        /** クラス → 下位のクラスが宣言した public な上書き（無ければ空） */
        private static final ClassValue<Optional<Method>> OVERRIDES = new ClassValue<>() {
            @Override
            protected Optional<Method> computeValue(Class<?> type) {
                for (Class<?> c = type; c != null && c != ShaderInstance.class; c = c.getSuperclass()) {
                    try {
                        Method m = c.getDeclaredMethod(PARSE_NAME, JsonElement.class);
                        if (Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers())) {
                            m.setAccessible(true);
                            AutomataValkyries.LOGGER.info("[compat] {} overrides the private ShaderInstance.parseUniformNode; dispatching to it",
                                    c.getName());
                            return Optional.of(m);
                        }
                    } catch (NoSuchMethodException ignored) {
                    } catch (Throwable t) {
                        return Optional.empty();
                    }
                }
                return Optional.empty();
            }
        };

        private static String runtimeName() {
            try {
                return ObfuscationReflectionHelper.findMethod(ShaderInstance.class, "m_173354_", JsonElement.class).getName();
            } catch (Throwable t) {
                return "parseUniformNode";
            }
        }

        @Redirect(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V",
                at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/client/renderer/ShaderInstance;parseUniformNode(Lcom/google/gson/JsonElement;)V"),
                require = 0)
        private void automata_valkyries$dispatchUniformNode(ShaderInstance self, JsonElement element) throws ChainedJsonException {
            Optional<Method> override = self.getClass() == ShaderInstance.class ? Optional.empty() : OVERRIDES.get(self.getClass());
            if (override.isEmpty()) {
                ((ShaderInstanceInvoker) self).automata_valkyries$parseUniformNode(element);
                return;
            }
            try {
                override.get().invoke(self, element);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof ChainedJsonException chained) throw chained;
                if (e.getCause() instanceof RuntimeException runtime) throw runtime;
                throw new RuntimeException(e.getCause());
            } catch (IllegalAccessException e) {
                ((ShaderInstanceInvoker) self).automata_valkyries$parseUniformNode(element);
            }
        }
    }
}
