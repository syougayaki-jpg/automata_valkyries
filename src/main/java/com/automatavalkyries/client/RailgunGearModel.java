package com.automatavalkyries.client;

import com.automatavalkyries.weapon.TacticalWeaponItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedItemGeoModel;

/**
 * 極光轟雷『パニッシュメント・レールガン』の背の歯車のモデル。歯車の回転はアニメーションではなく
 * {@link T4BackdropClient#gearAngle}（溜めるほど加速して積み上がる角度）から決める。歯の数の比で速さが違う。
 */
public class RailgunGearModel extends DefaultedItemGeoModel<TacticalWeaponItem> {

    /** 歯車の名前と、大歯車 g0 が 1 回る間に回る量（向きつき。歯の数の比から） */
    private static final String[] NAMES = {"g0", "g1", "g1b", "g2", "g3", "g4", "g5", "g6", "g8", "g7"};
    private static final float[] RATIO = {1F, -668.5714F / 360F, -668.5714F / 360F, 1470.8571F / 360F, -520F / 360F, 1040F / 360F,
            -520F / 360F, 433.3333F / 360F, -1337.1429F / 360F, -90F / 360F};

    public RailgunGearModel(ResourceLocation id) {
        super(id);
    }

    /** 描く直前の骨に回転を入れる（{@link WeaponRenderer#renderRecursively} から。アニメーションの更新が飛ばされるときでも持ち主ごとの角度になる） */
    static void apply(GeoBone bone) {
        String name = bone.getName();
        for (int i = 0; i < NAMES.length; i++) {
            if (!NAMES[i].equals(name)) continue;
            bone.setRotZ((T4BackdropClient.gearAngle * Mth.DEG_TO_RAD * RATIO[i]) % Mth.TWO_PI);
            return;
        }
    }
}
