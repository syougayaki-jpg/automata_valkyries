package com.automatavalkyries.registry;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.world.CorruptedBases;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 構造物の種類と部品（侵食基地のダンジョン。2026-10-15）。置く所と頻度は data/automata_valkyries/worldgen/structure・structure_set、
 * 生えるバイオームは tags/worldgen/biome/has_structure
 */
public final class ModStructures {

    private ModStructures() {}

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, AutomataValkyries.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, AutomataValkyries.MODID);

    /** 侵食基地（物資倉庫・クロノ・アウトリポスト・ディメンション・ファウンドリ。種類は json の "kind"） */
    public static final RegistryObject<StructureType<CorruptedBases.BaseStructure>> CORRUPTED_BASE =
            STRUCTURE_TYPES.register("corrupted_base", () -> () -> CorruptedBases.BaseStructure.CODEC);
    /** 侵食基地の部屋 1 つ */
    public static final RegistryObject<StructurePieceType> BASE_ROOM =
            PIECES.register("corrupted_base_room", () -> (StructurePieceType.ContextlessType) CorruptedBases.Room::new);
    /** 侵食基地の部屋の間のガラス屋根の通路（2026-10-15） */
    public static final RegistryObject<StructurePieceType> BASE_CORRIDOR =
            PIECES.register("corrupted_base_corridor", () -> (StructurePieceType.ContextlessType) CorruptedBases.Corridor::new);
    /** T3 の空中庭園要塞（クロノ・バスティオン。2026-10-19。設計した 1 つの形を 1 つの部品で描く） */
    public static final RegistryObject<StructurePieceType> SKY_GARDEN =
            PIECES.register("chrono_bastion_garden", () -> (StructurePieceType.ContextlessType) com.automatavalkyries.world.SkyGardenFortress.Piece::new);
}
