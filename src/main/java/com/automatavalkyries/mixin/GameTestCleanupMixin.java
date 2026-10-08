package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.GameTestCleanup;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GameTest が試験場を置く前の片付け（範囲のエンティティを discard する）を、保護対象に断らせない。範囲は片付けと同じ広げ方
 * （{@link GameTestCleanup}）
 */
@Mixin(StructureUtils.class)
public abstract class GameTestCleanupMixin {

    @Inject(method = "clearSpaceForStructure", at = @At("HEAD"))
    private static void automata_valkyries$releaseProtected(BoundingBox box, int y, ServerLevel level, CallbackInfo ci) {
        GameTestCleanup.releaseIn(level, new AABB(box.minX() - 2, box.minY() - 3, box.minZ() - 3,
                box.maxX() + 3, box.maxY() + 20, box.maxZ() + 3));
    }
}
