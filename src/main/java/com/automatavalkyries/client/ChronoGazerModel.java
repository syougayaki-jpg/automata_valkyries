package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.HeavyWalkerEntity;
import com.automatavalkyries.entity.chrono.ScoutDroneEntity;
import com.automatavalkyries.entity.chrono.SpiderBotEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * クロノゲイザーの汎用機のモデル（geo / テクスチャは tools/gen_chrono_gazers.py）。アニメーションのファイルは持たず、ここで骨を動かす:
 * ドローンの回転翼と浮遊、クモボットの 6 本脚の歩き（3 本ずつ交互）と導火線でのコアの脈、戦車の逆関節の歩き・砲の向き・撃った反動・踏み鳴らしの沈み込み
 */
public class ChronoGazerModel<T extends Mob & GeoEntity> extends DefaultedEntityGeoModel<T> {

    /**
     * ルクシオンの背のブースターの展開の角度（ラジアン。GeckoLib の向きなので geo の X・Y の回転とは符号が逆）:
     * 本体は後ろへ 85 度倒れて真後ろを向き（geo の rotX +85。3 つに分かれた噴射口が後ろから正三角形に見える）、主翼は 80 度、尾翼は 40 度外へ開く（左は負、右は正）
     */
    static final float BOOSTER_PITCH = -1.484F, BOOSTER_WING = -1.396F, BOOSTER_FIN = -0.698F;
    /** 3 つに分かれるときに開く角度（ラジアン）: 正四面体（底面の外接円の半径 / 高さ = 1/√2 → 約 35.3 度） */
    static final float BOOSTER_SPLIT = 0.6155F;

    public ChronoGazerModel(String name) {
        super(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, name), false);
    }

    @Override
    public void setCustomAnimations(T entity, long instanceId, AnimationState<T> state) {
        float pt = state.getPartialTick();
        float age = entity.tickCount + pt;
        float walk = Math.min(state.getLimbSwingAmount(), 1.0F);
        float limb = state.getLimbSwing();
        float pitch = Mth.lerp(pt, entity.xRotO, entity.getXRot()) * Mth.DEG_TO_RAD;

        if (entity instanceof ScoutDroneEntity) {
            rotY("rotor_left", age * 1.4F);
            rotY("rotor_right", -age * 1.4F);
            CoreGeoBone body = bone("body");
            if (body != null) {
                body.setPosY(Mth.sin(age * 0.15F) * 0.6F);
                body.setRotX(-pitch * 0.6F - walk * 0.25F);
                body.setRotZ(Mth.sin(age * 0.07F) * 0.05F);
            }
            CoreGeoBone antenna = bone("antenna");
            if (antenna != null) antenna.setRotZ(Mth.sin(age * 0.3F) * 0.15F);
        } else if (entity instanceof SpiderBotEntity bot) {
            float swell = Mth.clamp(bot.getSwelling(pt), 0.0F, 1.0F);
            for (int i = 0; i < 6; i++) {
                CoreGeoBone leg = bone("leg_" + i);
                if (leg == null) continue;
                boolean right = i < 3;
                // 3 本ずつ交互（右の前・後ろと左の真ん中、その逆）
                float phase = ((i % 3) + (right ? 0 : 1)) % 2 == 0 ? 0.0F : Mth.PI;
                float swing = Mth.cos(limb * 1.2F + phase) * 0.45F * walk;
                float lift = Math.max(0.0F, Mth.sin(limb * 1.2F + phase)) * 0.35F * walk;
                leg.setRotY(swing);
                leg.setRotZ((right ? -1 : 1) * lift + (right ? 1 : -1) * Mth.sin(age * 2.5F + i) * 0.04F * swell);
            }
            CoreGeoBone body = bone("body");
            if (body != null) {
                body.setPosY(Math.abs(Mth.sin(limb * 1.2F)) * 0.3F * walk);
                body.setRotZ(swell > 0 ? Mth.sin(age * 3.0F) * 0.04F * swell : 0.0F);
            }
            CoreGeoBone core = bone("core");
            if (core != null) {
                float pulse = 1.0F + swell * 0.45F + (swell > 0 ? Mth.sin(age * (1.0F + swell * 3.0F)) * 0.12F * swell : Mth.sin(age * 0.1F) * 0.04F);
                core.setScaleX(pulse);
                core.setScaleY(pulse);
                core.setScaleZ(pulse);
            }
        } else if (entity instanceof com.automatavalkyries.entity.chrono.MaskDroneEntity mask) {
            // 仮面: ゆっくり首をかしげて漂う（溜めている間はぴたりと止まって相手を見据え、震える）
            boolean busy = mask.getState() != com.automatavalkyries.entity.chrono.MaskDroneEntity.IDLE;
            CoreGeoBone head = bone("head");
            if (head != null) {
                head.setRotX(-pitch);
                head.setRotZ(busy ? Mth.sin(age * 4.0F) * 0.015F : Mth.sin(age * 0.05F) * 0.35F);
                head.setPosY(busy ? 0.0F : Mth.sin(age * 0.09F) * 0.8F);
            }
            CoreGeoBone ring = bone("ring");
            if (ring != null) ring.setRotZ(age * (busy ? 0.6F : 0.08F));
            CoreGeoBone cables = bone("cables");
            if (cables != null) cables.setRotX(Mth.sin(age * 0.11F) * 0.12F + walk * 0.4F);
        } else if (entity instanceof com.automatavalkyries.entity.chrono.TrooperEntity trooper) {
            // 兵士: 歩き・剣の横薙ぎと突き・腹のガトリング（常に露出。撃つと銃身が回る）
            float legSwing = Mth.cos(limb * 0.6662F) * 1.0F * walk;
            rotX("right_leg", legSwing);
            rotX("left_leg", -legSwing);
            rotX("left_arm", -legSwing * 0.8F);
            float s = (age - trooper.swingTick) / 10.0F;
            float swingArm = 0.0F, swingY = 0.0F;
            if (s >= 0.0F && s < 1.0F) {
                float up = Mth.sin(Math.min(1.0F, s * 2.0F) * Mth.PI);
                if (trooper.swingThrust) swingArm = 1.5F * Mth.sin(s * Mth.PI); // まっすぐ突き出す
                else { // 右上から左へ薙ぐ
                    swingArm = 1.2F + 0.4F * up;
                    swingY = 1.0F - 2.0F * s;
                }
            }
            boolean firing = trooper.isFiring();
            float ready = firing ? 0.0F : 0.5F; // 剣を前へ構える
            CoreGeoBone arm = bone("right_arm");
            if (arm != null) {
                arm.setRotX(legSwing * 0.8F * (1.0F - ready) + ready * 0.6F + swingArm);
                arm.setRotY(swingY);
            }
            CoreGeoBone body = bone("body");
            if (body != null) body.setRotY(swingY * 0.3F);
            float spin = Mth.lerp(pt, trooper.spinO, trooper.spin);
            CoreGeoBone gun = bone("belly_gun");
            if (gun != null) gun.setRotZ(spin);
            CoreGeoBone head = bone("head");
            if (head != null) head.setRotX(-pitch * 0.7F);
        } else if (entity instanceof com.automatavalkyries.entity.chrono.LuxionEntity lux) {
            animateLuxion(lux, pt, age, walk, limb, pitch);
        } else if (entity instanceof com.automatavalkyries.entity.chrono.GalliaFortEntity fort) {
            animateGallia(fort, pt, age, walk, limb, pitch);
        } else if (entity instanceof com.automatavalkyries.entity.chrono.MementoMoriEntity mori) {
            animateMemento(mori, pt, age);
        } else if (entity instanceof HeavyWalkerEntity walker) {
            float swing = Mth.cos(limb * 0.55F) * 0.55F * walk;
            rotX("leg_left", swing);
            rotX("leg_right", -swing);
            float stomp = age - walker.stompTick;
            float sink = stomp >= 0 && stomp < 10 ? Mth.sin(stomp / 10.0F * Mth.PI) : 0.0F;
            CoreGeoBone hull = bone("hull");
            if (hull != null) {
                hull.setPosY(Math.abs(Mth.sin(limb * 0.55F)) * 0.6F * walk - 2.0F * sink);
                hull.setRotZ(Mth.sin(limb * 0.55F) * 0.04F * walk);
            }
            CoreGeoBone cannon = bone("cannon");
            if (cannon != null) {
                float fire = age - walker.fireTick;
                float recoil = fire >= 0 && fire < 8 ? 1.0F - fire / 8.0F : 0.0F;
                cannon.setRotX(-pitch);
                cannon.setPosZ(2.0F * recoil);
            }
        }
    }

    // ---- ガリアフォートのモーション（2026-10-10） ----

    /**
     * 人型の逆関節の脚（2026-10-10 ユーザー指定「キャタピラを足先に逆関節で重厚な装甲をつける」。tools/gen_gallia_fort.py の HIP_Y・KNEE_*・前のキャタピラの付け根と合わせる）:
     * 腰の高さ、太ももを前へ傾ける角度（腰からひざ (36, -10) へ）、ひざ・足先（前のキャタピラを寝かせたまま後ろへ 25 px ずらして足にする）の付け根。
     * 前腕（後ろのキャタピラ。腕の子）の姿勢。値は形に合わせてプレビューで決めた
     */
    private static final float G_HIP_Y = 64.0F, G_KNEE_Y = 46.0F, G_KNEE_Z = -11.0F, // 胴を縮めて脚を長く（2026-10-10）
            G_FOOT_Y = 24.0F,
            G_FORE_Y = 16.5F, G_FORE_Z = -6.0F,
            // 組み替え（2026-10-10）: 足先にした後ろのキャタピラを反対側へ移す分・前へずらす分、前腕にした前のキャタピラを後ろのキャタピラの位置へ合わせる分
            G_FOOT_REAR_Z = 10.0F, G_FOOT_STAND_Z = -22.0F, G_FORE_FRONT_SHIFT = 14.0F; // 後ろのキャタピラの付け根の前後と、人型で足の下へ前へずらす分（回さない） // キャタピラを大きくし胴を 5 px 上げた（2026-10-10「胴体をスリムにしてキャタピラを大型化」）
    /**
     * 人型の胴は縦に起こす（2026-10-10 ユーザー指定「胴を縦にしてより人型に近く主砲を背中に背負って」）: X で 90 度回し、甲板（主砲・ミサイルの箱・発生塔）が背中へ、
     * 前（操縦席）が上へ、腹が胸になる。頭・腕・副砲は胴から切り離した骨（root の子）で、縦の胴の上・肩に置く。
     * 主砲は背中から右肩の後ろへ上を向いて背負い（G_CARRY_X）、撃つときは背中から支えのアーム（骨 cannon_arm）がせり上がり、頭上で前へ向けて固定する
     * （G_FIRE_*。胴の向きの中の値。2026-10-10 ユーザー指定「主砲を使うときはアームがせり上がり頭上で固定されるように」）
     */
    private static final float G_HULL_Y = 47.5F, G_HULL_ROT = Mth.HALF_PI, G_ARM_X = 3.0F, G_ARM_Y = 60.9F, G_ARM_Z = -23.7F,
            G_GUN_X = -1.1F, G_GUN_Y = 80.4F, G_GUN_Z = 11.8F, G_HEAD_Y = 62.4F, G_HEAD_Z = -10.7F, G_HEAD_SCALE = 1.2F,
            G_CARRY_X = 0.0F, G_CARRY_Y = -3.0F, G_FIRE_X = 0.0F, G_FIRE_Y = -26.0F, G_FIRE_Z = -80.0F, G_ARM_REACH = 1.6F; // 車体を背負って小さく後ろへ下げた分、上と前へ（2026-10-10） // 背負う所は背の真ん中に密着（2026-10-10「主砲がちゃんと背中についていない」「左右にずれている」）
    private static final float G_DOWN = -Mth.HALF_PI;
    /**
     * 要塞は人型の土下座（2026-10-10 ユーザー指定「出来るだけ人型のパーツを使って戦車状態を改変して土下座のような姿勢から立ち上がるかんじで」）:
     * 車体とキャタピラは要塞の位置のまま、人型だけの部品を隠さず土下座の姿勢でたたむ。上半身（細い胴・胸・腰のスカート・頭・翼）は立った姿勢を
     * 腰（G_DZ_CY, G_DZ_CZ）のまわりに前へ 90 度倒して（G_DZ_TY, G_DZ_TZ）ずらし、頭を前の下へ下げる。脚は正座のようにたたむ（太もも・すねの G_DZ_*）。
     * 変形は体を起こして立ち上がる（倒す角度とずらしを 0 へ）。値はプレビューで決めた。
     * 自然な変形（2026-10-10 ユーザー指定「出来るだけ自然に人型に変身するモーションとあまりパーツの大きさを変えずに」）: 背負った車体・主砲・ミサイルの箱も上半身と
     * 一つの塊で倒す（倒す角度 G_DZ_BOW は立った車体がちょうど水平になる角度、ずらし G_DZ_TY・G_DZ_TZ は立った車体の付け根を要塞の車体の付け根に重ねる量。
     * G_HULL_Y・G_PACK_Z を変えたら計算し直す）。変形は 腕で押して上体を起こす → 腰を上げて膝立ち → 足を体の下へ踏み出して立つ → 腕を肩へ・主砲を背負う → 翼を開く、
     * の順に重ねて進め、脚は腰と足首の位置から 2 本の骨の長さで曲げ方を出す（legIk）。翼は縮めず、たたんだ（G_DZ_WING_FOLD）所から開く
     */
    private static final float G_DZ_CY = 66.0F, G_DZ_CZ = 0.0F, G_DZ_BOW = -(Mth.HALF_PI - 0.2F), G_DZ_TY = -59.01F, G_DZ_TZ = 4.25F,
            G_DZ_WING_SCALE = 0.8F, G_DZ_WING_FOLD = 0.9F, G_STEP_UP = 6.0F;
    /**
     * 履帯の動き（2026-10-10 ユーザー指定「移動モーションで履帯が唸り動くようにして」）: 履板は tools/gen_gallia_fort.py で G_SHOE_SETS 組（骨 leg_X_tilt_s0〜）を
     * 間隔 G_SHOE_STEP の 1/組数 ずつ輪に沿ってずらして作ってあり、進んだ距離に合わせて 1 組ずつ順に見せて輪に沿って回って見せる（下の帯は後ろへ、上の帯は前へ）。
     * 車輪（骨 leg_X_wr0〜4・ws0〜1・wt0〜1）は半径に合わせて回す。G_TRACK_PX は歩きの進み（limbSwing）1 あたりのモデルの px（1 ブロック ≒ 進み 4、
     * モデルは SIZE × MODEL_SCALE 倍）に、唸って見えるよう少し速めた分を掛けた値
     */
    private static final int G_SHOE_SETS = 3;
    private static final float G_SHOE_STEP = 2.2F, G_TRACK_PX = 2.857F * 1.3F;
    /** 太もも（腰からひざ）とすね（ひざから足首）の長さ、すねの形の向き（ひざ (46, -11) → 足首 (24, 12)） */
    private static final float G_ANKLE_Z = 12.0F, G_THIGH_LEN = 21.095F, G_SHIN_LEN = 31.828F, G_SHIN_DIR = (float) Math.atan2(23.0, -22.0);

    /**
     * 上半身の骨 b を、立った姿勢（付け根 (py, pz)、X の回転 rot、ずらし posY・posZ）から、腰のまわりに bow だけ前へ倒して（tY, tZ）ずらした姿勢で置く
     */
    private static void bowed(CoreGeoBone b, float py, float pz, float rot, float posY, float posZ, float bow, float tY, float tZ) {
        float[] q = bowedPos(py, pz, posY, posZ, bow, tY, tZ);
        b.setRotX(rot + bow);
        b.setPosY(q[0]);
        b.setPosZ(q[1]);
    }

    /** {@link #bowed} のずらし（{posY, posZ}）だけを出す */
    private static float[] bowedPos(float py, float pz, float posY, float posZ, float bow, float tY, float tZ) {
        float y = py + posY - G_DZ_CY, z = pz + posZ - G_DZ_CZ;
        float c = Mth.cos(bow), sn = Mth.sin(bow);
        return new float[]{G_DZ_CY + y * c - z * sn + tY - py, G_DZ_CZ + y * sn + z * c + tZ - pz};
    }

    /**
     * 脚の 2 本の骨の曲げ方: 腰 (hy, hz) と足首 (ay, az) から、ひざを前（-Z）へ出す向きで {太ももの X の回転, すねの X の回転, ひざの y, ひざの z} を出す。
     * 太ももの形は腰から真下、すねの形はひざから G_SHIN_DIR の向き
     */
    private static float[] legIk(float hy, float hz, float ay, float az) {
        float dy = ay - hy, dz = az - hz;
        float d = Mth.clamp(Mth.sqrt(dy * dy + dz * dz), G_SHIN_LEN - G_THIGH_LEN + 0.01F, G_THIGH_LEN + G_SHIN_LEN - 0.01F);
        float base = (float) Math.atan2(dz, dy);
        float a = (float) Math.acos(Mth.clamp((G_THIGH_LEN * G_THIGH_LEN + d * d - G_SHIN_LEN * G_SHIN_LEN) / (2.0F * G_THIGH_LEN * d), -1.0F, 1.0F));
        float k1y = hy + G_THIGH_LEN * Mth.cos(base + a), k1z = hz + G_THIGH_LEN * Mth.sin(base + a);
        float k2y = hy + G_THIGH_LEN * Mth.cos(base - a), k2z = hz + G_THIGH_LEN * Mth.sin(base - a);
        float ky = k1z < k2z ? k1y : k2y, kz = Math.min(k1z, k2z);
        float thigh = Mth.wrapDegrees(((float) Math.atan2(kz - hz, ky - hy) - Mth.PI) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        float shin = Mth.wrapDegrees(((float) Math.atan2(az - kz, ay - ky) - G_SHIN_DIR) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        return new float[]{thigh, shin, ky, kz};
    }
    /**
     * 人型の立ち姿（2026-10-10 ユーザー指定「立ち姿を調整」→「足を開いて踏ん張る」「胸を張ってやや前傾」）: 脚を腰から外へ開く角度、つま先を外へ向ける角度、
     * 上半身の前傾、胸を張る（前へ出して少し起こす）、頭を少し下げて相手をにらむ。頭・腕・副砲は前傾に合わせて前へ（G_HEAD_Z・G_ARM_Z・G_GUN_Z に込み）
     */
    /** 肩アーマーを内側が下がるように斜めに付ける角度（2026-10-10 ユーザー指定「肩のアーマーを少し斜めにつけて」→「内側に下がるようにして」） */
    private static final float G_PAULDRON_TILT = 0.3F;
    private static final float G_SPREAD = 0.30F, G_TOE_OUT = 0.30F, G_LEAN = 0.20F, G_CHEST_OUT = 1.0F, G_HEAD_DOWN = 0.12F;
    /** 両脚の付け根を真ん中へ寄せてくっつける分（px。2026-10-10 ユーザー指定「内股になっているので外股にして足を八の字にして両足の付け根をくっつけて」） */
    private static final float G_HIP_IN = 14.0F;
    /**
     * 人型の胴の入れ替え（2026-10-10 ユーザー指定「胴体がごっついと装甲が映えない…大改革」）: 車体（骨 hull）は変形の後半で大きさ 0 へたたんで隠し、
     * 人型専用の細い胴（骨 torso・chest・skirt）を出す。主砲・支えのアーム・ミサイルの箱は車体から切り離した骨（root の子）なので、
     * 車体の向きの中での姿勢を root の向きへ直して置き（hullChild）。腕は細い胴へ寄せ、前腕は G_FORE_SCALE に少しだけ縮める。
     * 車体は隠さず、縦に起こしたまま（大きさは変えない。2026-10-10「あまりパーツの大きさを変えずに」）細い胴の背中へ G_PACK_Z 下げて背負う（2026-10-10 ユーザー指定「人型じゃない胴の部分を人型で背負う形に」。主砲・ミサイルの箱・発生塔は車体の甲板＝背の外側に乗ったまま）。
     * G_HULL_PIVOT_Y と *_PIVOT は tools/gen_gallia_fort.py の形の付け根（車体の倍率と下げ幅を掛けた後）
     */
    private static final float G_HULL_PIVOT_Y = 26.55F, G_PACK_Z = 18.33F, G_FORE_SCALE = 0.9F, G_WING_Z = 4.0F;
    /** 肘の曲げ（前腕を前へ振る角度）と、前腕の付け根の角から腕の芯までの距離（曲げても肘がずれないように位置を直す）。2026-10-10 ユーザー指定「すこし前かがみにして肘を曲げる」 */
    private static final float G_ELBOW = 0.9F, G_ELBOW_R = 7.8F; // 前腕を大きく戻したので少し深く（2026-10-10）
    private static final float[] G_CANNON_PIVOT = {44.75F, 5.2F}, G_CANNON_ARM_PIVOT = {40.2F, 13.0F}, G_POD_PIVOT = {38.25F, 11.05F},
            G_ARM_PIVOT = {28.5F, 18.2F}, G_GUN_PIVOT = {29.15F, -19.5F};

    /**
     * 肩に付く骨（付け根 pivot、立った姿勢のずらし posY・posZ）のずらし {posY, posZ}: 上半身と一緒に倒し、要塞の位置（ずらし 0）との差は w が 1 へ進むにつれて消す
     */
    private static float[] follow(float[] pivot, float posY, float posZ, float bow, float bowY, float bowZ, float w) {
        float[] p = bowedPos(pivot[0], pivot[1], posY, posZ, bow, bowY, bowZ);
        float[] full = bowedPos(pivot[0], pivot[1], posY, posZ, G_DZ_BOW, G_DZ_TY, G_DZ_TZ);
        return new float[]{p[0] - full[0] * (1.0F - w), p[1] - full[1] * (1.0F - w)};
    }

    /**
     * 車体から切り離した骨 b を、車体の向きの中の姿勢（X の回転 rx、ずらし tx・ty・tz）で置く。車体は X で hullRot 回って上へ hullY ずれている。
     * 付け根 (py, pz) を車体の付け根のまわりに回した位置へ動かし、回転は車体と足し合わせる。extraZ は車体の前後のずらし
     */
    private static void hullChild(CoreGeoBone b, float[] pivot, float rx, float tx, float ty, float tz, float hullRot, float hullY, float extraZ) {
        float py = pivot[0], pz = pivot[1];
        float y = ty + py - G_HULL_PIVOT_Y, z = tz + pz;
        float c = Mth.cos(hullRot), sn = Mth.sin(hullRot);
        float ry = y * c - z * sn, rz = y * sn + z * c;
        b.setRotX(hullRot + rx);
        b.setPosX(tx);
        b.setPosY(hullY + G_HULL_PIVOT_Y + ry - py);
        b.setPosZ(rz - pz + extraZ);
    }

    /**
     * 要塞: キャタピラの 4 本脚で滑るように進む（脚は振らず、走ると小刻みに揺れる）、踏みつけ（前脚 2 本を振り上げて胴を反らせ、踏み下ろして沈む）、
     * 主砲と副砲を頭の向きへ回して相手へ上下に向ける（溜めで震え、撃つと砲身が下がり胴がのけぞる）、ミサイルの箱の蓋の開閉、
     * 障壁が砕けている間は発生塔が沈む、撃破で脚が開いて胴が沈む。
     * 人型（第 2 形態。2026-10-10 ユーザー指定「変形して人型」→「副砲を肩に主砲を腹にして前のキャタピラを脚の一部に後ろのキャタピラを腕の一部に」）:
     * 変形の進み form で、前のキャタピラが後ろへずれて足先に（逆関節の太もも・すねが現れる）、胴がせり上がり、肩の装甲が下がって腕に・後ろのキャタピラがその先の前腕に、
     * 胴が縦に起き上がって主砲・ミサイルの箱が背中へ回り、副砲が肩アーマーの上へ、胸の装甲・拳が現れ、背の翼が開き、最後に頭がせり上がる（部品ごとに始まりをずらす）。人型では脚を振って歩き、腕を振る。
     * 技: 踏みつけは右足、重拳は右腕を振りかぶって叩きつけ、なぎ払いは両腕を前へ出して横に払う、突進は前へ傾いて腕を前に、
     * 弾幕は肩の副砲から、主砲と列車砲の全開射撃は背中のアームで砲を頭上に上げて撃つ（全開射撃は腰を落として左腕で支え、砲が震える）
     */
    /** 腰（G_HIP_Y, 0）を中心に、付け根 (y, z) を角度 sw だけ回したときの位置のずれ（{y, z}。前へ振るのが正） */
    private static float[] swingAroundHip(float y, float z, float sw) {
        float dy = y - G_HIP_Y, dz = z;
        float c = Mth.cos(sw), sn = Mth.sin(sw);
        return new float[]{dy * c - dz * sn - dy, dy * sn + dz * c - dz};
    }

    /**
     * 上半身の骨 b（付け根 (px, pz)）を、背骨（x = 0, z = 0 を通る縦の軸）のまわりに yaw だけねじる。位置を回して Y の回転を足す
     * （腕・副砲・頭・翼・背負った車体が胴と一緒に回る。2026-10-10 モーション改良「全身の連動」）
     */
    private static void twist(CoreGeoBone b, float px, float pz, float yaw) {
        if (b == null || yaw == 0.0F) return;
        float x = px + b.getPosX(), z = pz + b.getPosZ();
        float c = Mth.cos(yaw), sn = Mth.sin(yaw);
        b.setPosX(x * c + z * sn - px);
        b.setPosZ(-x * sn + z * c - pz);
        b.setRotY(b.getRotY() + yaw);
    }

    /** 衝撃の重み: 1.5 Tick で 1 へ、その後は減衰しながら揺れ戻す（重い物が着いて沈み、跳ね返る）。decay は揺れの減り方（Tick） */
    private static float impact(float t, float decay) {
        if (t < 0.0F) return 0.0F;
        return t < 1.5F ? t / 1.5F : settle(t - 1.5F, decay);
    }

    /** 1 から減衰しながら揺れて 0 へ（行き過ぎて戻る） */
    private static float settle(float u, float decay) {
        if (u < 0.0F || u > decay * 6.0F) return 0.0F;
        return (float) Math.exp(-u / decay) * Mth.cos(u * 0.55F);
    }

    /** 反動: 1 Tick で 1 へ跳ね、len Tick かけてゆっくり 0 へ戻る */
    private static float kick(float t, float len) {
        if (t < 0.0F || t >= len) return 0.0F;
        return t < 1.0F ? t : 1.0F - smooth((t - 1.0F) / (len - 1.0F));
    }

    private static float easeIn(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * t;
    }

    /** 少し行き過ぎて戻る（機械がガチッと止まる） */
    private static float backOut(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F) - 1.0F;
        return 1.0F + 2.7F * t * t * t + 1.7F * t * t;
    }

    private static void scaleBone(CoreGeoBone b, float s) {
        b.setScaleX(s);
        b.setScaleY(s);
        b.setScaleZ(s);
    }

    private void animateGallia(com.automatavalkyries.entity.chrono.GalliaFortEntity fort, float pt, float age, float walk, float limb, float pitch) {
        float defeat = fort.isDefeatedSynced() ? Mth.clamp((fort.defeatTicks + pt) / 40.0F, 0.0F, 1.0F) : 0.0F;
        if (defeat > 0.0F) {
            walk = 0.0F;
            pitch = 0.0F;
        }
        // ねじれ（twist）は今の位置・Y の回転に足すので、下で毎回は決めない値を先に 0 に戻す（骨は Tick をまたいで・全部のガリアフォートで共有して値を持ち続け、
        // 戻さないとねじれが毎フレーム積み重なって部品がずれていった。2026-10-10「羽とかミサイルがずれている」「第二形態めっちゃずれてる」）
        for (String name : new String[]{"hull", "torso", "chest", "skirt", "head", "cannon", "cannon_arm", "arm_right", "arm_left", "gun_right", "gun_left",
                "pod_right", "pod_left", "wing_right", "wing_left"}) {
            CoreGeoBone b = bone(name);
            if (b != null) {
                b.setPosX(0.0F);
                b.setRotY(0.0F);
            }
        }
        float form = fort.form(pt);
        // 変形の段階（重ねて進める）: 腕で押して上体を起こす → 腰を上げて膝立ち → 足を体の下へ踏み出して立つ → 腕を肩へ・主砲を背負う → 翼を開く
        float rise = smooth((form - 0.05F) / 0.5F), lift = smooth((form - 0.1F) / 0.6F), step = smooth((form - 0.45F) / 0.35F),
                wArm = smooth((form - 0.3F) / 0.45F), wCan = smooth((form - 0.5F) / 0.35F);
        float headYaw = Mth.wrapDegrees(Mth.rotLerp(pt, fort.yHeadRotO, fort.yHeadRot) - Mth.rotLerp(pt, fort.yBodyRotO, fort.yBodyRot)) * Mth.DEG_TO_RAD;
        // ---- 技の重み（2026-10-10 ユーザー指定「全体にモーション改良」→ 重量感・メリハリ・全身の連動・機械らしさ）:
        // 予備動作はゆっくり溜めて一瞬止め、打つ所は 1〜2 Tick で一気に、当たったら全身が沈んで揺れ戻し（impact）、ゆっくり戻る
        // 踏みつけ: 13 Tick かけて振り上げて溜め → 合図で 2 Tick で踏み下ろす → 沈んで揺れ戻す
        float raiseT = age - fort.stompRaiseTick, hitT = age - fort.stompHitTick;
        boolean stompHit = fort.stompHitTick >= fort.stompRaiseTick && hitT >= 0.0F;
        float raise = !stompHit && raiseT >= 0.0F ? smooth(Math.min(1.0F, raiseT / 13.0F)) : stompHit && hitT < 2.0F ? 1.0F - easeIn(hitT / 2.0F) : 0.0F;
        float slam = stompHit ? impact(hitT, 5.0F) : 0.0F;
        // 主砲: 撃つと 1 Tick で砲身が下がってゆっくり戻り、体は衝撃でのけぞって揺れ戻す。溜めの間は全身が細かく震える
        float fireT = age - fort.cannonFireTick;
        float recoil = kick(fireT, 16.0F), fireJolt = impact(fireT, 4.0F);
        float chargeT = age - fort.cannonChargeTick;
        boolean charging = chargeT >= 0.0F && chargeT < 40.0F && fort.cannonChargeTick > fort.cannonFireTick;
        float hum = charging ? Math.min(1.0F, chargeT / 25.0F) : 0.0F;
        // 列車砲の全開射撃: 脚を打ち込んで腰を落とす（打ち込んだ衝撃）→ 溜めで全身が震え翼が開いていく → 撃つと後ろへ滑ってのけぞる
        float ultT = age - fort.ultBraceTick;
        float ult = envelope(ultT, 14.0F, 120.0F, 140.0F);
        float ultAnchor = impact(ultT - 6.0F, 4.0F);
        float ultShake = ultT >= 20.0F && ultT < 100.0F ? (ultT - 20.0F) / 80.0F : 0.0F;
        float ultFire = age - fort.ultFireTick;
        float ultRecoil = kick(ultFire, 30.0F), ultJolt = impact(ultFire, 6.0F);
        // 重拳: 11 Tick で振りかぶり、3 Tick 溜めて止め（少し引き絞る）、2 Tick で振り下ろす（合図で当たる）→ 叩きつけたまま止め → ゆっくり戻す
        float hamT = age - fort.hammerRaiseTick, hamHit = age - fort.hammerHitTick;
        boolean hamAfter = fort.hammerHitTick >= fort.hammerRaiseTick && hamHit >= 0.0F;
        boolean hamUp = !hamAfter && hamT >= 0.0F && hamT < 30.0F;
        float hamRaise = hamUp ? smooth(hamT / 11.0F) + 0.1F * smooth((hamT - 11.0F) / 3.0F) : 0.0F;
        float hamSlam = hamAfter ? (hamHit < 12.0F ? 1.0F : 1.0F - smooth((hamHit - 12.0F) / 14.0F)) : hamUp && hamT >= 14.0F ? easeIn((hamT - 14.0F) / 2.0F) : 0.0F;
        float hamImpact = hamAfter ? impact(hamHit, 4.0F) : 0.0F;
        // ふっとばし（腕のなぎ払い。2026-10-10 ユーザー指定「両手を広げてその場で超信地旋回で一回転するように」）: 7 Tick で両腕を真横へ広げて腰を落とし →
        // 10 Tick でその場で 1 回転（左右の足のキャタピラを逆へ回す超信地旋回。速く回り始めて、最後は少し行き過ぎて戻る）→ 腕を戻す
        float swT = age - fort.sweepTick;
        float swOn = envelope(swT, 5.0F, 18.0F, 25.0F);
        float spinT = Mth.clamp((swT - 7.0F) / 10.0F, 0.0F, 1.0F);
        float sweepSpin = swT >= 0.0F && swT < 25.0F ? Mth.TWO_PI * (spinT * spinT * (3.0F - 2.0F * spinT)) + 0.12F * settle(swT - 17.0F, 2.5F) * (swT > 17.0F ? 1 : 0) : 0.0F;
        // 突進（ロードキル。2026-10-10 ユーザー指定「履帯に火花のエフェクト追加して高速回転させる」）: 低く構えて足のキャタピラを空転させて唸らせ →
        // 脚は振らず、腰を落として足のキャタピラで地面を滑るように突っ込む（キャタピラは高速で回り、火花を散らす）→ 止まるときは後ろへ反って踏ん張る
        float cwT = age - fort.chargeWindupTick, cT = age - fort.chargeTick;
        boolean dashing = fort.chargeTick >= fort.chargeWindupTick && cT >= 0.0F;
        float crouch = !dashing ? envelope(cwT, 12.0F, 20.0F, 26.0F) : 0.0F;
        float run = dashing ? envelope(cT, 2.0F, 13.0F, 18.0F) : 0.0F;
        float brake = dashing ? envelope(cT - 13.0F, 2.0F, 4.0F, 12.0F) : 0.0F;
        float lean = Math.max(crouch * 0.6F, run) - 0.35F * brake;
        // 副砲・ミサイルの反動
        float gunJolt = Math.max(impact(age - fort.gunRightTick, 2.5F), impact(age - fort.gunLeftTick, 2.5F));
        // 歩き（人型）: 脚を振り、前へ振る足を持ち上げ、腕は少し遅れて逆に振る。胴は踏み出すたびに沈み、肩は脚と逆へねじれる
        float phase = limb * 0.35F;
        float a = Mth.sin(phase) * 0.32F * walk * (1.0F - run);                          // 突進中は脚を振らずに滑る
        float legR = a + raise * 0.9F - 0.3F * crouch + 0.15F * run, legL = -a + 0.15F * crouch - 0.25F * run;   // 滑るときは右足を前・左足を後ろに構える
        float swing = Mth.cos(phase) * walk * (1.0F - run);
        float liftR = Math.max(0.0F, swing) * 4.5F, liftL = Math.max(0.0F, -swing) * 4.5F;
        float armSwing = Mth.sin(phase - 0.5F) * 0.32F * walk * (1.0F - run);              // 腕は脚より遅れて振る
        // 足のキャタピラの空転・高速回転の進み（px）: 構えの間に速めていき（最後は 1 Tick に 8 px）、突進中はその速さで回り、止まると 10 Tick で止まる
        float footSpin;
        if (dashing) {
            float u = Math.max(0.0F, cT), d = Mth.clamp(u - 14.0F, 0.0F, 10.0F);
            footSpin = 80.0F + 8.0F * Math.min(u, 14.0F) + 8.0F * d - 0.4F * d * d;
        } else {
            float w = cwT >= 0.0F && cwT < 26.0F ? Math.min(cwT, 20.0F) : 0.0F;
            footSpin = 0.2F * w * w;
        }
        float spreadAll = G_SPREAD + 0.12F * ult + 0.08F * crouch;                     // 大技・突進の構えは足を広げて踏ん張る
        // 要塞の胴: 走りの揺れ、止まっていても機関の細かい震え、踏みつけの振り上げで前を持ち上げ、踏み下ろしで沈む
        float defeatShake = defeat > 0.0F && defeat < 1.0F ? Mth.sin(age * 3.3F) * (1.0F - defeat) : 0.0F; // 崩れる前にがくがく震える
        float hullFortY = Mth.sin(age * 1.3F) * 0.15F * walk + Mth.sin(age * 5.3F) * 0.12F * walk + Mth.sin(age * 2.7F) * 0.05F + 4.0F * raise - 3.0F * slam
                - 1.5F * fireJolt - 0.6F * gunJolt - 9.0F * defeat; // 要塞の胴の上下（前のキャタピラは腕の子なので打ち消す）
        float fortPitch = 0.12F * raise - 0.06F * slam + 0.06F * recoil - 0.025F * walk + 0.04F * defeatShake
                + 0.05F * crouch + Mth.sin(age * 3.9F) * 0.012F * (crouch + run) - 0.04F * run + 0.07F * brake;   // ロードキル: 構えで前を上げて唸り、突っ込むと前へ沈み、止まると前を上げる   // 要塞の胴の前後の傾き（前を上げるのが正）
        // 人型の上半身の前後の傾き（腰を中心に。背負った車体・腕・頭・翼も一緒に）: 突進で前へ、重拳の振りかぶりで反って叩きつけで前へ、撃つとのけぞる
        float bend = (0.3F * lean + 0.38F * hamSlam - 0.12F * hamRaise + 0.1F * hamImpact + 0.08F * ult - 0.15F * ultRecoil - 0.08F * fireJolt
                + 0.06F * swOn) * lift;
        // 人型の上半身の背骨まわりのねじれ: 歩きは前へ出た脚と逆の肩を前へ、重拳は右肩を引いて振りかぶり叩きつけで前へ、なぎ払いは腰から払う
        float twist = (Mth.sin(phase) * 0.1F * walk * (1.0F - run) + 0.35F * hamRaise * (1.0F - hamSlam) - 0.2F * hamSlam) * lift;
        // 上半身（背負った車体・主砲・ミサイルの箱ごと）を腰のまわりに倒す角度とずらし。要塞では胴の上下も写す
        float bow = G_DZ_BOW * (1.0F - rise) - bend, bowY = (G_DZ_TY + hullFortY) * (1.0F - lift), bowZ = G_DZ_TZ * (1.0F - lift);
        float[] hip = bowedPos(G_HIP_Y, 0.0F, 0.0F, 0.0F, bow, bowY, bowZ);
        float hipY = G_HIP_Y + hip[0], hipZ = hip[1];
        float stepUp = G_STEP_UP * Mth.sin(Mth.PI * step);                       // 踏み出す足を少し持ち上げる
        for (String side : new String[]{"r", "l"}) {
            boolean right = side.equals("r");
            float sw = right ? legR : legL;
            // 人型の脚は腰を中心に sw だけ振る（太もも・すね・足をまとめて回す。すねと足は付け根の位置も腰のまわりへ動かす）
            // 足先（後ろのキャタピラ。2026-10-10 ユーザー指定「後ろのキャタピラを足に前のキャタピラを腕にくみかえて」→「回転させず立ち上がるように」）:
            // 要塞では後ろ脚のまま、人型では回さずに脚の下へずらして足に（内の端の踵の装備は足の前に出る）
            CoreGeoBone foot = bone("leg_b" + side);
            if (foot != null) {
                float rumble = Mth.sin(age * 1.9F + (right ? 1 : 3) * 1.7F) * 0.25F * walk;
                float[] d = swingAroundHip(G_FOOT_Y, G_FOOT_REAR_Z + G_FOOT_STAND_Z, sw);
                float depth = G_HIP_Y - G_FOOT_Y;                                       // 腰から開く分だけ外へ・上へ
                foot.setRotX(Mth.lerp(step, 0.0F, sw));
                foot.setRotY(Mth.lerp(step, 0.0F, (right ? -1 : 1) * G_TOE_OUT));      // つま先を外へ（八の字）
                foot.setPosX(Mth.lerp(step, 0.0F, (right ? 1 : -1) * G_HIP_IN + (right ? -1 : 1) * depth * Mth.sin(spreadAll)));
                foot.setPosY(Mth.lerp(step, rumble, d[0] + depth * (1.0F - Mth.cos(spreadAll)) + (right ? liftR : liftL)) + stepUp);
                foot.setPosZ(Mth.lerp(step, 0.0F, G_FOOT_STAND_Z + d[1]));
                foot.setRotZ(Mth.lerp(step, (right ? -1 : 1) * defeat * 0.35F, 0.0F));
            }
            // 太もも・すね（逆関節）: 腰（上半身と一緒に動く）と足首（足先と一緒に動く）から曲げ方を出し、腰を中心に sw だけ振る。
            // 立ち姿ではひざ (G_KNEE_Y, G_KNEE_Z)・太ももの傾き 0.5485 になる。脚を外へ開くのは足を踏み出すのと一緒に
            float[] ik = legIk(hipY, hipZ, G_FOOT_Y + stepUp + (right ? liftR : liftL) * step, G_ANKLE_Z - G_FOOT_STAND_Z * (1.0F - step));
            float kdy = ik[2] - hipY, kdz = ik[3] - hipZ, cs = Mth.cos(sw), sns = Mth.sin(sw);
            float kneeY = hipY + kdy * cs - kdz * sns, kneeZ = hipZ + kdy * sns + kdz * cs;
            float spread = spreadAll * step;
            CoreGeoBone thigh = bone(right ? "thigh_right" : "thigh_left");
            if (thigh != null) {
                thigh.setRotX(ik[0] + sw);
                thigh.setRotZ((right ? 1 : -1) * spread);                             // 脚を外へ開いて踏ん張る
                thigh.setPosX((right ? 1 : -1) * G_HIP_IN);                              // 付け根を真ん中へ寄せる
                thigh.setPosY(hipY - G_HIP_Y);
                thigh.setPosZ(hipZ);
            }
            CoreGeoBone shinH = bone(right ? "shin_right" : "shin_left");
            if (shinH != null) {
                float depth = hipY - kneeY;
                shinH.setRotX(ik[1] + sw);
                shinH.setRotZ((right ? 1 : -1) * spread);
                shinH.setPosX((right ? 1 : -1) * G_HIP_IN + (right ? -1 : 1) * depth * Mth.sin(spread));
                shinH.setPosY(kneeY - G_KNEE_Y + depth * (1.0F - Mth.cos(spread)));
                shinH.setPosZ(kneeZ - G_KNEE_Z);
            }
            // 前腕（前のキャタピラ。腕の子）: 要塞では前脚のまま（踏みつけで持ち上がる。胴の上下を打ち消す）、人型では腕の先へ伸びる（半回転しない）
            CoreGeoBone fore = bone("leg_f" + side);
            if (fore != null) {
                float rumble = Mth.sin(age * 1.9F + (right ? 2 : 0) * 1.7F) * 0.25F * walk;
                fore.setRotY(0.0F);
                // 肘: 歩きでは腕より遅れて揺れ、重拳は振りかぶりで深く曲げて叩きつけで伸ばす、なぎ払い・突進では伸ばす、全開射撃の左腕は支える
                float elbow = G_ELBOW + Mth.sin(phase - 1.0F) * 0.12F * walk * (right ? 1 : -1)
                        + (right ? 2.1F * Math.min(1.0F, hamRaise) * (1.0F - hamSlam) - 0.65F * hamSlam : 0.25F * ult)   // 重拳: 深く曲げて拳を前に → 伸ばして殴る
                        - (G_ELBOW - 0.05F) * swOn - 0.35F * Math.max(0.0F, lean);                                          // ふっとばし: 肘を伸ばして広げる
                fore.setRotX(Mth.lerp(wArm, raise * 0.45F, elbow));                    // 肘を曲げて拳を前へ（要塞では踏みつけで前を持ち上げる）
                fore.setPosY(Mth.lerp(wArm, rumble + raise * 12.0F - hullFortY, G_FORE_Y - G_ELBOW_R * (1.0F - Mth.cos(elbow))));
                fore.setPosZ(Mth.lerp(wArm, 0.0F, G_FORE_Z + G_FORE_FRONT_SHIFT + G_ELBOW_R * Mth.sin(elbow)));
                fore.setRotZ(Mth.lerp(wArm, (right ? -1 : 1) * defeat * 0.35F, 0.0F));
                scaleBone(fore, Mth.lerp(wArm, 1.0F, G_FORE_SCALE));            // 人型では前腕を縮めて脚の装甲を見せる
            }
        }
        CoreGeoBone root = bone("root");
        if (root != null) {
            scaleBone(root, com.automatavalkyries.entity.chrono.GalliaFortEntity.MODEL_SCALE); // 要塞と人型で同じ（2026-10-10「あまりパーツの大きさを変えずに」）
            root.setPosY(Mth.lerp(lift, -3.0F * defeat, -10.0F * defeat));
            root.setPosZ(2.5F * ultRecoil + 0.8F * recoil);                          // 撃った反動で後ろへ滑る
            root.setRotX(-0.45F * defeat * lift); // 人型は前へ崩れる
            root.setRotY(sweepSpin * lift);        // ふっとばし: その場で 1 回転
        }
        // 人型の胴の上下（息づかい・歩き・技で沈む）。切り離した頭・腕・副砲も同じだけ動かす
        // 踏み出すたびに沈む（足を大きく開いた所が一番低い）、技の溜めで腰を落とし、当たった衝撃で沈んで揺れ戻す、溜めの間は細かく震える
        float hBob = (Mth.sin(age * 0.08F) * 0.3F - Math.abs(Mth.sin(phase)) * 1.4F * walk - 3.0F * run + Mth.sin(age * 4.1F) * 0.4F * run
                - 3.5F * slam - 3.0F * ult - 2.5F * ultAnchor + ultJolt - 5.0F * hamSlam - 2.5F * hamImpact - 1.5F * fireJolt - 0.6F * gunJolt
                - 4.0F * crouch - 2.0F * swOn + Mth.sin(age * 2.9F) * 0.25F * hum + Mth.sin(age * 3.7F) * 0.4F * ultShake) * lift;
        // 背負った車体: 立った姿勢（縦に起こして背中へ G_PACK_Z）を上半身と一緒に倒す。要塞ではちょうど要塞の車体の位置・向きになる
        float[] hq = bowedPos(G_HULL_PIVOT_Y, 0.0F, G_HULL_Y + hBob, G_PACK_Z, bow, bowY, bowZ);
        float hullY = hq[0], hullZ = hq[1];
        float hullRot = G_HULL_ROT - G_LEAN + bow + fortPitch * (1.0F - lift);
        CoreGeoBone hull = bone("hull");
        if (hull != null) {
            hull.setPosY(hullY);
            hull.setPosZ(hullZ);
            hull.setRotZ(Mth.lerp(lift, 0.02F * defeatShake, Mth.sin(phase) * 0.03F * walk));
            hull.setRotX(hullRot);
            hull.setRotY(0.0F);
            scaleBone(hull, 1.0F);
        }
        for (String name : new String[]{"torso", "skirt", "chest"}) {          // 人型専用の細い胴・腰のスカート・胸の装甲（土下座でも見せる）
            CoreGeoBone b = bone(name);
            if (b == null) continue;
            scaleBone(b, 1.0F);
            boolean chestPart = name.equals("chest");
            float rot = -(chestPart ? G_LEAN * 0.5F : G_LEAN);                   // やや前傾（胸は少し起こす）
            bowed(b, chestPart ? 84.0F : 66.0F, chestPart ? -7.0F : 0.0F, rot, hBob, chestPart ? -G_CHEST_OUT : 0.0F, bow, bowY, bowZ);  // 胸を張る
            b.setRotY(0.0F);
            b.setRotZ(Mth.sin(phase) * 0.04F * walk * lift);                       // 踏み出した脚へ体重を移す
        }
        // 腕（肩の装甲）: 要塞では胴の横（胴の上下だけ写す）、人型では縦の胴の肩から下がる。技で振りかぶる・前へ出す・払う
        for (String side : new String[]{"right", "left"}) {
            CoreGeoBone arm = bone("arm_" + side);
            if (arm == null) continue;
            boolean right = side.equals("right");
            float ang = G_DOWN + 0.15F + (right ? -armSwing : armSwing) * 0.6F;  // 歩きでは脚と逆に、少し遅れて振る（立ち姿では少し前へ）
            ang = Mth.lerp(swOn, ang, 0.0F);                                // ふっとばし: 水平に（下の yaw で真横へ）
            ang = Mth.lerp(crouch, ang, G_DOWN + 0.6F);                     // 突進の構え: 腕を引いて後ろへ
            ang = Mth.lerp(Mth.clamp(lean, 0.0F, 1.0F) * (1.0F - crouch), ang, -0.5F);  // 突進: 前下へ
            if (right) {
                // 重拳（2026-10-10 ユーザー指定「肩と肘を後ろに引き上げて拳を前に構えたままちゃんと地面を殴って」）: 肩から腕を後ろへ水平近くまで引き上げ
                // （肘は深く曲げて拳は前に構えたまま。溜めでさらに引き絞る）→ 腕を前下へ振り下ろしながら肘を伸ばし、拳で前の地面を殴る
                ang = Mth.lerp(Math.min(1.0F, hamRaise), ang, -3.1F - 0.15F * Math.max(0.0F, hamRaise - 1.0F) * 10.0F);   // プレビューで決めた（肩を後ろへ水平、拳は胸の前）
                ang = Mth.lerp(hamSlam, ang, -1.0F);                       // 叩きつけ（前下へ）
            } else {
                ang = Mth.lerp(hamRaise * (1.0F - hamSlam), ang, -0.9F);   // 重拳: 左腕を前へ出して釣り合いを取る
                ang = Mth.lerp(ult, ang, -0.7F);                           // 大技: 左腕で支える
            }
            float yaw = (right ? 1.0F : -1.0F) * Mth.HALF_PI * swOn;          // ふっとばし: 両腕を真横へ広げる
            // 肩は上半身と一緒に動き、要塞の腕の位置との差は腕を肩へ回すのに合わせて消す（前のキャタピラで地面を押して起き上がる）
            float[] at = follow(G_ARM_PIVOT, G_ARM_Y + hBob, G_ARM_Z, bow, bowY, bowZ, wArm);
            arm.setRotX(Mth.lerp(wArm, 0.0F, ang - 0.3F * defeat + bow));
            arm.setRotY(Mth.lerp(wArm, 0.0F, yaw));
            arm.setPosX(Mth.lerp(wArm, 0.0F, right ? -G_ARM_X : G_ARM_X));
            arm.setPosY(at[0]);
            arm.setPosZ(at[1]);
        }
        // 主砲: 要塞では砲塔が頭の向きへ回る。人型では背中から右肩の後ろへ背負い、主砲の溜め・大技の間はアームで頭上へ上げて前へ向ける（胴の向きの中で回す）
        CoreGeoBone cannon = bone("cannon");
        if (cannon != null) {
            float shake = Mth.sin(age * 3.1F) * 0.012F * hum + Mth.sin(age * 4.3F) * 0.02F * ultShake;
            // 構えの重みはエンティティの側でなめらかに上げ下げする（2026-10-10「撃つ時にがくがく」: 溜めのたびに 0 へ戻っていた）
            float aimW = smooth(Mth.lerp(pt, fort.cannonAimO, fort.cannonAim));
            // 機械らしく順に: 先にアームが伸びて砲を頭上へ持ち上げ（extW）、その後で砲が前へ倒れてガチッと止まる（rotW。少し行き過ぎて戻る）
            float extW = smooth(aimW / 0.55F), rotW = aimW <= 0.35F ? 0.0F : backOut((aimW - 0.35F) / 0.65F);
            // 構えた砲は体の傾き（前傾・技の傾き）を打ち消して、ちょうど相手の上下の向きに（2026-10-10「ちゃんと相手のほうを向いて撃つ」: 前傾の分だけ下を向いていた）
            float hRotX = Mth.lerp(rotW, 0.0F, Mth.clamp(-pitch, -0.4F, 1.1F) - hullRot);   // 対空で上へ 63 度まで（2026-10-10）
            // 車体の向きの中での姿勢（撃った後の後退: 要塞では砲身の後ろ（+Z）、人型で構えているときは車体の向きの中で砲身の後ろ（+Y））
            float rx = Mth.lerp(wCan, Mth.clamp(-pitch, -0.5F, 1.1F) + 0.05F * recoil, hRotX) - defeat * 0.3F;
            float tx = Mth.lerp(wCan, 0.0F, Mth.lerp(extW, G_CARRY_X, G_FIRE_X));
            float ty = Mth.lerp(wCan, 0.0F, Mth.lerp(extW, G_CARRY_Y, G_FIRE_Y) + (7.0F * recoil + 12.0F * ultRecoil) * aimW);
            float tz = Mth.lerp(wCan, 7.0F * recoil, extW * G_FIRE_Z);
            hullChild(cannon, G_CANNON_PIVOT, rx, tx, ty, tz, hullRot, hullY, hullZ);
            cannon.setRotY(headYaw * Mth.lerp(wCan, 1.0F, aimW) - twist * aimW);   // 構えた砲も頭の向き＝相手の方へ（胴のねじれは打ち消す）
            cannon.setRotZ(shake);
            // 支えのアーム: 構えるときに背中から伸び（長さの向きの大きさ 0 → 1）、受け台で砲を頭上に固定する。ふだんは隠れる
            CoreGeoBone arm = bone("cannon_arm");
            if (arm != null) {
                hullChild(arm, G_CANNON_ARM_PIVOT, 0.0F, 0.0F, 0.0F, 0.0F, hullRot, hullY, hullZ);
                float ext = extW * wCan;
                arm.setScaleZ(ext * G_ARM_REACH); // 頭上の高い所まで伸ばす（2026-10-10「頭上に主砲を出すときもう少し上に」）
                arm.setScaleX(Math.min(1.0F, ext * 4.0F));
                arm.setScaleY(Math.min(1.0F, ext * 4.0F));
            }
        }
        for (String side : new String[]{"right", "left"}) {               // ミサイルの箱（車体から切り離した骨。人型では細い胴の背中）
            CoreGeoBone pod = bone("pod_" + side);
            float podKick = kick(age - (side.equals("right") ? fort.missileRightTick : fort.missileLeftTick), 5.0F);   // 撃ち上げた反動で沈む
            if (pod != null) hullChild(pod, G_POD_PIVOT, 0.04F * podKick, 0.0F, -1.2F * podKick, 0.0F, hullRot, hullY, hullZ);
        }
        for (String side : new String[]{"right", "left"}) {
            CoreGeoBone gun = bone("gun_" + side);
            if (gun == null) continue;
            float shot = age - (side.equals("right") ? fort.gunRightTick : fort.gunLeftTick);
            boolean right = side.equals("right");
            gun.setRotY(Mth.clamp(headYaw, -0.6F, 0.6F));
            gun.setRotX(Mth.clamp(-pitch, -0.4F, 1.2F));                    // 対空で上へ 69 度まで（2026-10-10）
            float[] gt = follow(G_GUN_PIVOT, G_GUN_Y + hBob, G_GUN_Z, bow, bowY, bowZ, wArm);   // 肩の上（上半身と一緒に）
            gun.setPosX(Mth.lerp(wArm, 0.0F, right ? -G_GUN_X : G_GUN_X));
            gun.setPosY(gt[0]);
            gun.setPosZ(gt[1] + 2.5F * kick(shot, 6.0F));                       // 撃つと砲身が下がる
            gun.setRotX(gun.getRotX() - 0.12F * kick(shot, 6.0F));                 // 跳ね上がる
        }
        // 人型だけの飾り（要塞では大きさ 0）: 肩アーマー・拳は腕と一緒に、胸・翼は胴の上下に合わせて。翼はゆっくりはばたき、変形の最後に開く
        for (String side : new String[]{"right", "left"}) {
            CoreGeoBone pd = bone("pauldron_" + side);
            if (pd != null) {
                scaleBone(pd, 1.0F);                                                // 要塞でも見せる（土下座の肩）
                pd.setRotY((side.equals("right") ? -1 : 1) * G_PAULDRON_TILT * wArm);   // 内側が下がるように少し斜めに
            }
            CoreGeoBone fist = bone("fist_" + side);
            if (fist != null) scaleBone(fist, 1.0F);                               // 要塞でも見せる（前のキャタピラの先）
            CoreGeoBone heel = bone("heel_" + side);                            // 足先のキャタピラの後ろの踵の装備（2026-10-10「踵の部分が寂しい」）
            if (heel != null) scaleBone(heel, 1.2F);                               // 要塞でも見せる（後ろのキャタピラの内の端）。形を大きく複雑にしたので 1.35 → 1.2
            CoreGeoBone wing = bone("wing_" + side);
            if (wing != null) {                                                 // 土下座では体と一緒に倒れて内へたたみ、立つと開く
                float open = smooth((form - 0.65F) / 0.35F);
                scaleBone(wing, Mth.lerp(open, G_DZ_WING_SCALE, 1.0F));
                bowed(wing, 88.0F, 17.0F, 0.0F, hBob + 2.0F, G_WING_Z, bow, bowY, bowZ);   // 背負った車体の後ろへ
                wing.setRotZ((side.equals("right") ? 1 : -1) * (G_DZ_WING_FOLD * (1.0F - open) + Mth.sin(age * 0.06F) * 0.04F + 0.15F * ult
                        + 0.25F * ultShake + 0.3F * ultJolt + 0.15F * hamImpact + 0.1F * slam * lift + 0.04F * Math.abs(Mth.sin(phase - 0.6F)) * walk));
                wing.setRotX(wing.getRotX() + (0.12F * run + 0.1F * crouch) * lift);    // 突進では後ろへ寝かせる
            }
        }
        CoreGeoBone head = bone("head");
        if (head != null) { // 土下座では前の下で頭を下げ、体を起こすと縦の胴の上へ
            scaleBone(head, G_HEAD_SCALE);
            float rot = (Mth.clamp(-pitch, -0.4F, 0.4F) * 0.5F - G_HEAD_DOWN - 0.4F * defeat) * rise + 0.6F * bend; // 少し下げて相手をにらむ（体の傾きは打ち消す）
            bowed(head, 38.25F, 2.6F, rot, G_HEAD_Y + hBob, G_HEAD_Z, bow, bowY, bowZ);
            head.setRotY(headYaw * 0.7F * rise - 0.6F * twist);                    // 胴をねじっても相手から目を離さない
        }
        // 履帯: 進んだ距離だけ履板が輪に沿って回り（組を順に見せる）、車輪が回る。要塞の間だけ（人型の足・前腕では止める）
        float trackPx = limb * G_TRACK_PX * (1.0F - lift);
        for (String leg : new String[]{"leg_fr", "leg_fl", "leg_br", "leg_bl"}) {
            // 突進（ロードキル）で空転・高速回転する: 人型は足（後ろのキャタピラ）だけ、戦車は 4 つとも（2026-10-10「戦車状態でも…ロードキル」）
            float px = trackPx + footSpin * (leg.startsWith("leg_b") ? 1.0F : 1.0F - lift)
                    + (leg.startsWith("leg_b") ? (leg.endsWith("r") ? 1.0F : -1.0F) * sweepSpin * 19.0F * 1.6F * lift : 0.0F);   // 超信地旋回: 左右の足を逆へ回す
            int set = Math.floorMod((int) Math.floor(px * G_SHOE_SETS / G_SHOE_STEP), G_SHOE_SETS);
            for (int k = 0; k < G_SHOE_SETS; k++) {
                CoreGeoBone sb = bone(leg + "_tilt_s" + k);
                if (sb != null) scaleBone(sb, k == set ? 1.0F : 0.0F);
            }
            for (int i = 0; i < 5; i++) rotX(leg + "_wr" + i, -px / 2.6F);   // 下が後ろへ回る向き
            for (int i = 0; i < 2; i++) {
                rotX(leg + "_ws" + i, -px / 3.9F);
                rotX(leg + "_wt" + i, -px / 1.5F);
            }
        }
        // 上半身を背骨のまわりにねじる（付け根は tools/gen_gallia_fort.py の骨の付け根）
        if (twist != 0.0F) {
            twist(bone("hull"), 0.0F, 0.0F, twist);
            twist(bone("torso"), 0.0F, 0.0F, twist);
            twist(bone("chest"), 0.0F, -7.0F, twist);
            twist(bone("skirt"), 0.0F, 0.0F, twist * 0.4F);
            twist(bone("head"), 0.0F, 2.6F, twist);
            twist(bone("cannon"), 0.0F, 5.2F, twist);
            twist(bone("cannon_arm"), 0.0F, 13.0F, twist);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                String side = sgn < 0 ? "right" : "left";
                twist(bone("arm_" + side), sgn * 20.8F, 18.2F, twist);
                twist(bone("gun_" + side), sgn * 22.1F, -19.5F, twist);
                twist(bone("pod_" + side), sgn * 17.55F, 11.05F, twist);
                twist(bone("wing_" + side), sgn * 9.0F, 17.0F, twist);
            }
        }
        float hatch = smooth(Mth.lerp(pt, fort.hatchO, fort.hatch));
        rotX("hatch_right", -1.7F * hatch);
        rotX("hatch_left", -1.7F * hatch);
        CoreGeoBone emitters = bone("emitters");
        if (emitters != null) {
            float shield = Mth.lerp(pt, fort.shieldO, fort.shield);
            float hit = 1.0F - Mth.clamp((age - fort.barrierHitTick) / 6.0F, 0.0F, 1.0F);
            emitters.setPosY(-3.0F * (1.0F - shield) + 0.4F * hit);
        }
        CoreGeoBone pilot = bone("pilot");
        if (pilot != null) {
            pilot.setRotZ(Mth.sin(age * 0.05F) * 0.04F);
            pilot.setRotX(raise * -0.1F + slam * 0.15F);
        }
    }

    // ---- メメントモリのモーション（2026-10-10） ----

    /**
     * 巨大な機械の黒い蝶の動き: いつも羽ばたき（後翅は少し遅れる）、羽ばたきに合わせて体が上下する。
     * 乱舞は速く強く羽ばたき、弾幕は翅を V 字に上げて小刻みに震わせ、檻は前翅を前へ振って構え、合図で一気に打ち下ろす。
     * 黒死の月は翅を大きく水平に広げて止め、納める瞬間に打ち下ろす。現れるときは閉じた翅を開き、撃破では翅が垂れて弱く震える。
     * 第 2 形態は翅が 1.2 倍に大きく開き、紋が赤い翅に入れ替わる（目覚めの途中で）。
     * 翅の X の回転の向き: 左（+X）の翅は負で先が上がる、右（-X）は正で上がる（プレビューで確かめた）
     */
    private void animateMemento(com.automatavalkyries.entity.chrono.MementoMoriEntity mori, float pt, float age) {
        boolean defeated = mori.isDefeatedSynced();
        float defeat = defeated ? Mth.clamp((mori.defeatTicks + pt) / com.automatavalkyries.entity.chrono.MementoMoriEntity.DEFEAT_TICKS, 0.0F, 1.0F) : 0.0F;
        byte stage = mori.stage();
        float st = age - mori.stageStart;
        float swarm = envelope(age - mori.swarmTick, 4.0F, 30.0F, 40.0F);
        float barrage = envelope(age - mori.barrageTick, 6.0F, 40.0F, 50.0F);
        float cage = envelope(age - mori.cageTick, 8.0F, 60.0F, 75.0F);
        float strike = envelope(age - mori.cageReleaseTick, 1.0F, 4.0F, 14.0F) + impact(age - mori.moonFallTick, 4.0F);
        float moon = envelope(age - mori.moonTick, 15.0F, 120.0F, 140.0F);
        float awaken = stage == com.automatavalkyries.entity.chrono.MementoMoriEntity.STAGE_AWAKEN ? envelope(st, 6.0F, 30.0F, 40.0F) : 0.0F;
        float intro = stage == com.automatavalkyries.entity.chrono.MementoMoriEntity.STAGE_INTRO ? 1.0F - smooth(st / 30.0F) : 0.0F;
        // 羽ばたき（ふだんはゆっくり大きく、乱舞は速く強く）。弾幕・月・撃破では小さく
        float calm = (1.0F - 0.7F * barrage) * (1.0F - 0.85F * moon) * (1.0F - 0.6F * defeat) * (1.0F - cage * 0.5F);
        float beat = (Mth.sin(age * 0.28F) * 0.55F * (1.0F - swarm) + Mth.sin(age * 0.8F) * 0.85F * swarm) * calm;
        float beatH = (Mth.sin(age * 0.28F - 0.5F) * 0.5F * (1.0F - swarm) + Mth.sin(age * 0.8F - 0.6F) * 0.8F * swarm) * calm;
        float quiver = Mth.sin(age * 2.3F) * 0.05F * barrage;
        float lift = 0.12F + 0.6F * barrage + 0.1F * moon + 1.3F * intro + 0.4F * awaken - 0.5F * defeat - 0.9F * strike + quiver
                + Mth.sin(age * 1.7F) * 0.08F * defeat;                                    // 撃破で垂れて弱く震える
        float sweep = 0.45F * cage - 0.15F * moon;                                          // 檻: 前翅を前へ振る（左は正）
        boolean p2 = mori.isPhase2();
        boolean red = p2 && (stage != com.automatavalkyries.entity.chrono.MementoMoriEntity.STAGE_AWAKEN || st >= 10.0F);
        float big = p2 ? 1.0F + 0.2F * (stage == com.automatavalkyries.entity.chrono.MementoMoriEntity.STAGE_AWAKEN ? smooth((st - 6.0F) / 20.0F) : 1.0F) : 1.0F;
        for (String side : new String[]{"l", "r"}) {
            float s = side.equals("l") ? -1.0F : 1.0F;                                     // 左は負で上がる
            for (String kind : new String[]{"f", "h"}) {
                float a = (kind.equals("f") ? beat : beatH) + lift + (kind.equals("h") ? -0.08F : 0.0F);
                for (String suffix : new String[]{"", "_red"}) {
                    CoreGeoBone w = bone("wing_" + kind + side + suffix);
                    if (w == null) continue;
                    w.setRotZ(s * a);
                    w.setRotY(kind.equals("f") ? -s * sweep : -s * sweep * 0.4F);
                    boolean show = suffix.isEmpty() != red;
                    scaleBone(w, show ? big : 0.0F);
                }
            }
        }
        CoreGeoBone body = bone("body");
        if (body != null) {
            body.setPosY(-Mth.sin(age * 0.28F) * 0.8F * calm + 2.0F * strike);           // 翅を打ち下ろすと体が持ち上がる
            body.setRotX(-0.6F * defeat + 0.15F * cage);
        }
    }

    // ---- ルクシオンのモーション（2026-10-08 ユーザー指定「全部作って」） ----

    /**
     * 骨の姿勢（ラジアン・ピクセル）。a = 右腕（剣）、l = 左腕、b = 胴、r = 全体の上下、h = 頭。
     * 脚は股関節から足先への向き（前へ振るのが正）と、まっすぐからの縮み bend（px。膝を曲げる量。{@link #legIk} で太ももと膝に分ける）
     */
    private static final class Pose {
        float aX, aY, aZ, lX, lY, lZ, bX, bY, bZ, rY, hX, hY, hZ, legR, legL, bendR, bendL;

        Pose arm(float x, float y, float z) { aX = x; aY = y; aZ = z; return this; }
        Pose larm(float x, float y, float z) { lX = x; lY = y; lZ = z; return this; }
        Pose body(float x, float y, float z) { bX = x; bY = y; bZ = z; return this; }
        Pose root(float y) { rY = y; return this; }
        Pose head(float x, float y, float z) { hX = x; hY = y; hZ = z; return this; }
        Pose legs(float r, float l) { legR = r; legL = l; return this; }
        Pose bends(float r, float l) { bendR = r; bendL = l; return this; }

        Pose copy() {
            Pose p = new Pose();
            p.mix(this, 1.0F);
            return p;
        }

        /** this を o へ w だけ寄せる */
        Pose mix(Pose o, float w) {
            if (w <= 0.0F) return this;
            aX = Mth.lerp(w, aX, o.aX); aY = Mth.lerp(w, aY, o.aY); aZ = Mth.lerp(w, aZ, o.aZ);
            lX = Mth.lerp(w, lX, o.lX); lY = Mth.lerp(w, lY, o.lY); lZ = Mth.lerp(w, lZ, o.lZ);
            bX = Mth.lerp(w, bX, o.bX); bY = Mth.lerp(w, bY, o.bY); bZ = Mth.lerp(w, bZ, o.bZ);
            rY = Mth.lerp(w, rY, o.rY);
            hX = Mth.lerp(w, hX, o.hX); hY = Mth.lerp(w, hY, o.hY); hZ = Mth.lerp(w, hZ, o.hZ);
            legR = Mth.lerp(w, legR, o.legR); legL = Mth.lerp(w, legL, o.legL);
            bendR = Mth.lerp(w, bendR, o.bendR); bendL = Mth.lerp(w, bendL, o.bendL);
            return this;
        }

        static Pose between(Pose a, Pose b, float k) {
            return a.copy().mix(b, k);
        }
    }

    private static Pose pose() {
        return new Pose();
    }

    /** 膝をつく（右脚を立てて足を前へ、左膝を地に。剣は前の地面へ突き立てる） */
    private static final Pose KNEEL = pose().root(-12.0F).legs(1.14F, -1.09F).bends(0.8F, 3.0F).body(-0.45F, 0.0F, 0.0F).head(-0.3F, 0.0F, 0.0F)
            .arm(0.75F, 0.0F, 0.05F).larm(0.35F, 0.0F, -0.15F);
    /** 撃破: 剣を地に突き立てて両手で柄を握り、膝をついてうなだれる */
    private static final Pose DEFEAT = pose().root(-12.0F).legs(1.14F, -1.09F).bends(0.8F, 3.0F).body(-0.5F, 0.15F, 0.0F).head(-0.45F, 0.0F, 0.1F)
            .arm(0.8F, -0.15F, 0.0F).larm(0.8F, 0.2F, 0.35F);
    // 斬撃の型（始め → 終わり）。全身で振る（2026-10-09 ユーザー指定）: 振りかぶりで胴を大きくひねって腰を引き、振り抜きで右脚を深く踏み込んで
    // 腰を落とし（膝は脚の縮みから自動で曲がる）、胴を逆へひねって傾け、左腕は振りと逆へ引いて釣り合いを取り、頭は相手へ残す
    private static final Pose KESA_A = pose().arm(2.6F, 0.3F, 0.6F).body(0.15F, 0.6F, 0.08F).legs(0.25F, -0.15F).root(-0.8F).larm(0.5F, 0.0F, -0.4F).head(-0.1F, -0.4F, 0.0F);
    private static final Pose KESA_B = pose().arm(0.5F, -0.9F, -0.5F).body(-0.4F, -0.65F, -0.12F).legs(0.75F, -0.55F).root(-2.6F).larm(-0.3F, 0.0F, -0.8F).head(0.25F, 0.45F, 0.0F);
    private static final Pose RISING_A = pose().arm(0.4F, -0.9F, -0.6F).body(-0.3F, -0.6F, -0.1F).legs(0.35F, -0.3F).root(-2.2F).larm(0.3F, 0.0F, -0.5F).head(0.15F, 0.4F, 0.0F);
    private static final Pose RISING_B = pose().arm(2.4F, 0.4F, 0.6F).body(0.15F, 0.65F, 0.1F).legs(0.7F, -0.5F).root(-0.6F).larm(-0.2F, 0.0F, -0.9F).head(-0.2F, -0.45F, 0.0F);
    private static final Pose FLAT_A = pose().arm(1.5F, 1.2F, 0.2F).body(-0.1F, 0.8F, 0.05F).legs(0.25F, -0.25F).root(-2.0F).larm(0.6F, 0.0F, -0.4F).head(0.0F, -0.6F, 0.0F);
    private static final Pose FLAT_B = pose().arm(1.5F, -1.3F, 0.2F).body(-0.25F, -0.85F, -0.08F).legs(0.8F, -0.6F).root(-2.8F).larm(-0.2F, 0.0F, -1.0F).head(0.1F, 0.65F, 0.0F);
    /** 三連閃の締め: 頭上へ振りかぶる → 唐竹に振り下ろす */
    private static final Pose RAISE = pose().arm(3.0F, 0.0F, 0.05F).larm(2.7F, 0.0F, 0.3F).body(0.25F, 0.0F, 0.0F).head(0.25F, 0.0F, 0.0F).legs(0.15F, -0.1F).root(0.5F);
    private static final Pose DOWN = pose().arm(0.35F, 0.0F, 0.0F).larm(0.4F, 0.0F, 0.3F).body(-0.55F, 0.0F, 0.0F).head(0.35F, 0.0F, 0.0F).legs(0.8F, -0.6F).root(-3.5F);
    /** 瞬動: 消える前に身を反らせる → 現れた直後は居合の振り抜き */
    private static final Pose BLINK_LEAN = pose().body(0.4F, 0.1F, 0.0F).arm(-0.3F, 0.2F, 0.4F).larm(-0.2F, 0.0F, -0.3F).head(-0.2F, 0.0F, 0.0F).legs(0.3F, -0.4F).root(-1.0F);
    private static final Pose IAI = pose().arm(1.5F, -1.3F, -0.1F).larm(0.6F, 0.3F, -0.6F).body(-0.3F, -0.55F, 0.0F).head(0.25F, 0.55F, 0.0F).legs(0.9F, -0.7F).root(-3.0F);
    /** 光刃の波: 大きく引く → 全身で横に振り抜き、残心 */
    private static final Pose WAVE_PULL = pose().arm(1.2F, 1.7F, 0.4F).larm(1.0F, 0.0F, -0.9F).body(0.05F, 0.85F, 0.0F).head(0.0F, -0.7F, 0.0F).legs(-0.6F, 0.7F).root(-2.5F);
    private static final Pose WAVE_SWING = pose().arm(1.4F, -1.6F, 0.2F).larm(0.3F, 0.0F, -1.0F).body(-0.25F, -0.9F, 0.0F).head(0.15F, 0.6F, 0.0F).legs(0.8F, -0.6F).root(-3.0F);
    /** 光速突進: 深く沈み、剣を後ろへ流す → 駆け抜けた後に滑って止まる */
    private static final Pose DASH_CROUCH = pose().arm(-0.9F, 0.2F, 0.35F).larm(-0.5F, 0.0F, -0.3F).body(-0.75F, 0.1F, 0.0F).head(0.55F, 0.0F, 0.0F).legs(-0.8F, 0.9F).root(-5.0F);
    private static final Pose SLIDE_STOP = pose().arm(0.4F, 0.3F, 1.0F).larm(0.3F, 0.0F, -0.9F).body(0.3F, -0.2F, 0.0F).head(-0.2F, 0.2F, 0.0F).legs(1.0F, -0.5F).root(-4.0F);
    /** 第 2 形態: 剣を天へ掲げ、左腕を広げて仰ぐ */
    private static final Pose ASCEND = pose().arm(3.05F, 0.0F, 0.1F).larm(0.3F, 0.0F, -1.0F).body(0.18F, 0.0F, 0.0F).head(0.45F, 0.0F, 0.0F).legs(0.15F, -0.15F);
    /** 退く: 剣を左腰の鞘へ納める */
    private static final Pose SHEATHE = pose().arm(0.7F, -1.0F, -0.6F).larm(0.6F, 0.5F, 0.2F).body(-0.05F, -0.25F, 0.0F).head(-0.15F, 0.2F, 0.0F);
    private static final Pose SHEATHED = pose().arm(0.2F, -0.6F, -0.3F).larm(0.3F, 0.4F, 0.2F).head(-0.3F, 0.0F, 0.0F);
    // 大技ルクシオンアクセラレータ（2026-10-09 ユーザー指定）: 深く沈む → 両腕を広げて跳び上がる → 頂点で身を丸めて左手で狙いを指す →
    // ライダーキック（右脚をまっすぐ蹴り出し、左脚は深くたたみ、両腕は後ろへ流す。体ごと蹴りの向きへ傾けるのはレンダラー）→ 着弾して低く着地
    private static final Pose ACCEL_CROUCH = pose().arm(-0.7F, 0.2F, 0.5F).larm(-0.7F, -0.2F, -0.5F).body(0.45F, 0.0F, 0.0F).head(-0.3F, 0.0F, 0.0F)
            .legs(0.35F, 0.3F).root(-6.0F);
    private static final Pose ACCEL_RISE = pose().arm(-0.4F, 0.3F, 0.9F).larm(-0.4F, -0.3F, -0.9F).body(-0.25F, 0.0F, 0.0F).head(0.2F, 0.0F, 0.0F)
            .legs(-0.1F, -0.25F).bends(1.5F, 3.5F);
    private static final Pose ACCEL_AIM = pose().arm(-0.5F, 0.4F, 0.7F).larm(1.2F, -0.3F, -0.15F).body(-0.35F, 0.25F, 0.0F).head(0.35F, -0.2F, 0.0F)
            .legs(0.9F, 0.7F).bends(8.0F, 9.0F);
    private static final Pose ACCEL_KICK = pose().arm(-1.0F, 0.2F, 0.6F).larm(-0.6F, -0.3F, -0.9F).body(-0.3F, 0.3F, 0.0F).head(0.4F, -0.25F, 0.0F)
            .legs(1.55F, 0.85F).bends(0.0F, 10.0F);
    private static final Pose ACCEL_LAND = pose().arm(0.3F, 0.6F, 1.2F).larm(0.2F, 0.0F, -1.2F).body(0.4F, -0.2F, 0.0F).head(-0.2F, 0.0F, 0.0F)
            .legs(0.55F, -0.45F).root(-7.0F);

    /** 始め a に t の 2 Tick で入り、3 Tick で b へ振り、hold まで留めて len で戻る（斬撃の形） */
    private static void strike(Pose p, Pose a, Pose b, float t, float len, float hold) {
        if (t < 0.0F || t >= len) return;
        Pose target = Pose.between(a, b, smooth((t - 2.0F) / 3.0F));
        float w = t < 2.0F ? smooth(t / 2.0F) : t < hold ? 1.0F : 1.0F - smooth((t - hold) / (len - hold));
        p.mix(target, w);
    }

    /** t の in Tick で入り、hold まで留めて len で戻る */
    private static float envelope(float t, float in, float hold, float len) {
        if (t < 0.0F || t >= len) return 0.0F;
        return t < in ? smooth(t / in) : t < hold ? 1.0F : 1.0F - smooth((t - hold) / (len - hold));
    }

    private void animateLuxion(com.automatavalkyries.entity.chrono.LuxionEntity lux, float pt, float age, float walk, float limb, float pitch) {
        if (lux.isDefeatedSynced()) { // 撃破後は歩く脚の振り・首の向きを止める
            walk = 0.0F;
            pitch = 0.0F;
        }
        if (LuxionRenderer.portrait) { // ボスバーの横顔: いつも同じ姿で止める
            pt = 0.0F;
            age = 0.0F;
            walk = 0.0F;
            pitch = 0.0F;
        }
        boolean hovering = lux.isHovering();
        if (hovering) walk *= 0.2F; // 空中では歩かない
        // ルクシオンアクセラレータで宙にいる間（跳び上がってから着弾まで）
        float at = age - lux.actionTick;
        boolean accelAir = at >= 0.0F && at < 80.0F && ((lux.action == com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_ACCEL_LEAP && at >= 4.0F)
                || lux.action == com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_ACCEL_AIM || lux.action == com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_ACCEL_KICK);
        if (accelAir) walk = 0.0F;
        float legSwing = Mth.cos(limb * 0.7F) * 1.1F * walk;
        float aggro = smooth(Mth.lerp(pt, lux.deployO, lux.deploy));
        float glory = smooth(Mth.lerp(pt, lux.gloryO, lux.glory));
        float breath = Mth.sin(age * 0.1F);
        // 平常待機: 剣を下ろし、時々首をかしげて観察する
        float cyc = (age % 140.0F) / 140.0F;
        float tilt = smooth((cyc - 0.45F) / 0.1F) * (1.0F - smooth((cyc - 0.8F) / 0.1F));
        Pose p = pose().arm(0.15F, 0.0F, 0.1F).larm(0.05F, 0.0F, 0.0F).root(breath * 0.15F).head(-0.08F * tilt, 0.2F * tilt, 0.28F * tilt);
        // 戦闘待機: 低く構えて剣を後ろ斜めへ、半身で呼吸に合わせて上下する
        p.mix(pose().arm(0.55F, -0.45F, 0.35F).larm(0.5F, 0.0F, -0.25F).body(-0.18F, 0.4F, 0.0F).head(0.0F, -0.35F, 0.0F)
                .legs(-0.35F, 0.3F).root(-1.2F + breath * 0.35F), aggro);
        // 歩き
        p.legR = Mth.lerp(walk, p.legR, legSwing);
        p.legL = Mth.lerp(walk, p.legL, -legSwing);
        p.rY *= 1.0F - walk * 0.7F;
        p.bY *= 1.0F - walk * 0.6F;
        p.hY *= 1.0F - walk * 0.6F;
        p.aX += legSwing * 0.3F;
        p.lX -= legSwing * 0.8F;
        p.bX -= 0.15F * walk;
        // 光の翼: 普段はゆっくり羽ばたき（戦闘中はさらにゆっくり大きく）、歩く・構えると後ろへ流れて広がる。第 2 形態はいつも大きく広げて震わせる
        float spread = 0.25F + 0.35F * walk + 0.55F * glory + (lux.isPhase2() ? Mth.sin(age * 0.5F) * 0.05F
                : Mth.sin(age * Mth.lerp(aggro, 0.08F, 0.05F)) * Mth.lerp(aggro, 0.08F, 0.14F));
        float sweep = 0.15F * walk;
        // 空中にとどまる: 脚を垂らして少し後ろへ流し、翼を大きく広げてゆっくり上下する
        if (hovering) {
            p.mix(pose().arm(p.aX, p.aY, p.aZ).larm(p.lX, p.lY, p.lZ).body(-0.1F, p.bY, 0.0F).head(p.hX, p.hY, p.hZ)
                    .legs(-0.15F, -0.4F).root(Mth.sin(age * 0.12F) * 0.8F), 1.0F);
            spread += 0.45F + Mth.sin(age * 0.2F) * 0.08F;
            sweep += 0.2F;
        }
        // 突進の構え: 深く沈み、剣を後ろへ流し、翼を後ろへ
        float w = (age - lux.windupTick) / (lux.isPhase2() ? 12.0F : 20.0F);
        float windup = w >= 0.0F && w < 1.0F ? Mth.sin(Math.min(1.0F, w * 1.5F) * Mth.HALF_PI) : 0.0F;
        p.mix(DASH_CROUCH, windup);
        spread += 0.6F * windup;
        sweep += 0.8F * windup;
        // 技の動き。burst = ブースターの噴射の強まり、flare = 光のマントのはためき（全身で動く。2026-10-09 ユーザー指定「ブースターからの光やマントも含めて全身使う」）
        float burst = 0.9F * windup, flare = 0.7F * windup, pull = 0.0F;
        float t = age - lux.actionTick;
        switch (lux.action) {
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_SLASH -> {
                strike(p, KESA_A, KESA_B, t, 10.0F, 5.0F);
                float e = envelope(t, 2.0F, 5.0F, 10.0F);
                flare += 0.55F * e;
                burst += 0.35F * e;
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_SLASH_RISING -> {
                strike(p, RISING_A, RISING_B, t, 10.0F, 5.0F);
                float e = envelope(t, 2.0F, 5.0F, 10.0F);
                flare += 0.55F * e;
                burst += 0.35F * e;
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_SLASH_FLAT -> {
                strike(p, FLAT_A, FLAT_B, t, 10.0F, 5.0F);
                float e = envelope(t, 2.0F, 5.0F, 10.0F);
                flare += 0.65F * e;
                burst += 0.35F * e;
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_SLASH_DOWN -> {
                strike(p, RAISE, DOWN, t, 16.0F, 9.0F);
                float e = envelope(t - 4.0F, 1.0F, 5.0F, 12.0F);
                flare += 0.8F * e;
                burst += 0.6F * e;
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_FLURRY_RAISE -> {
                float e = envelope(t, 4.0F, 10.0F, 14.0F);
                p.mix(RAISE, e);
                burst += 0.5F * e; // 振りかぶって浮き上がる
                spread += 0.25F * e;
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_BLINK_PREP -> {
                float e = envelope(t, 3.0F, 6.0F, 9.0F);
                p.mix(BLINK_LEAN, e);
                burst += 0.8F * e;
                flare += 0.4F * e;
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_BLINK_ARRIVE -> {
                p.mix(IAI, envelope(t, 0.5F, 6.0F, 14.0F));
                spread += 0.3F * envelope(t, 0.5F, 4.0F, 12.0F);
                float e = envelope(t, 0.5F, 3.0F, 12.0F);
                flare += 0.9F * e; // 現れた勢いでマントが広がる
                burst += 1.0F * e;
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_WAVE_CHARGE -> {
                float e = envelope(t, 6.0F, 10.0F, 14.0F);
                p.mix(WAVE_PULL, e);
                sweep += 0.3F * e;
                spread += 0.2F * e;
                burst += 0.4F * e;
                flare += 0.3F * e;
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_WAVE_RELEASE -> {
                if (t >= 0.0F && t < 22.0F) {
                    Pose target = Pose.between(WAVE_PULL, WAVE_SWING, smooth(t / 4.0F));
                    p.mix(target, t < 14.0F ? 1.0F : 1.0F - smooth((t - 14.0F) / 8.0F)); // 振り抜いて残心
                    spread += 0.35F * envelope(t, 2.0F, 8.0F, 18.0F);
                    float e = envelope(t, 1.0F, 6.0F, 18.0F);
                    flare += 0.9F * e;
                    burst += 0.7F * e;
                }
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_DASH_END -> {
                float e = envelope(t, 0.5F, 4.0F, 14.0F);
                p.mix(SLIDE_STOP, e);
                p.bZ += Mth.sin(age * 2.2F) * 0.04F * e; // 滑る揺れ
                sweep += 0.6F * e;
                flare += 1.0F * e; // 止まった勢いでマントが前へあおられる
                burst += 1.2F * e; // 逆噴射で止まる
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_ACCEL_LEAP -> { // 深く沈んでから、両腕を広げて跳び上がる
                if (t < 4.0F) {
                    p.mix(ACCEL_CROUCH, smooth(t / 2.0F));
                    burst += 0.6F * smooth(t / 4.0F);
                } else {
                    p.mix(Pose.between(ACCEL_CROUCH, ACCEL_RISE, smooth((t - 4.0F) / 4.0F)), 1.0F);
                    burst += 2.0F;
                    flare += 0.8F;
                    pull = 0.8F;
                }
                spread += 0.5F;
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_ACCEL_AIM -> { // 頂点でブースターを全開にして、身を丸めて左手で狙いを指す
                p.mix(Pose.between(ACCEL_RISE, ACCEL_AIM, smooth(t / 6.0F)), 1.0F);
                burst += 1.6F + 0.4F * Mth.sin(age * 0.9F);
                flare += 0.6F;
                spread += 0.8F;
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_ACCEL_KICK -> { // ライダーキック
                p.mix(Pose.between(ACCEL_AIM, ACCEL_KICK, smooth(t / 2.0F)), 1.0F);
                burst += 2.2F;
                flare += 1.6F;
                pull = 1.0F; // マントは後ろへ流れる
                spread += 0.6F;
                sweep += 0.8F;
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_ACCEL_IMPACT -> { // 着弾して低く着地し、身を起こす
                float e = envelope(t, 0.5F, 6.0F, 16.0F);
                p.mix(ACCEL_LAND, e);
                burst += 1.0F * e;
                flare += 1.0F * e;
            }
            default -> {
            }
        }
        // 全身で振る: 胴のひねりに合わせて傾き、勢いのある技では腰を落とす。頭は少し遅れて正面へ残る
        p.bZ -= p.bY * 0.12F;
        p.rY -= 0.6F * Math.min(1.0F, flare);
        p.hY -= p.bY * 0.25F;
        // 被弾ののけぞりは無し（2026-10-09 ユーザー指定「食らいモーションを消して」。赤くなるのはそのまま）
        // 演出の段階
        float fold = 0.0F, close = 0.0F, ascend = 0.0F, kneel = 0.0F;
        float st = age - lux.stageStart;
        switch (lux.stage()) {
            case com.automatavalkyries.entity.chrono.LuxionEntity.STAGE_INTRO -> {
                // 光の柱から膝をついて降り（5 Tick）、立ち上がり（18〜28）、剣を払い（24〜40）、翼を開く（26〜40）
                Pose k = KNEEL.copy();
                if (st < 5.0F) k.rY += 48.0F * (1.0F - st / 5.0F) * (1.0F - st / 5.0F);
                kneel = st < 18.0F ? 1.0F : 1.0F - smooth((st - 18.0F) / 10.0F);
                p.mix(k, kneel);
                float sw = envelope(st - 24.0F, 2.0F, 10.0F, 16.0F);
                float k2 = smooth((st - 26.0F) / 6.0F);
                p.mix(pose().arm(1.5F, Mth.lerp(k2, 1.2F, -1.3F), 0.2F).body(-0.1F, Mth.lerp(k2, 0.5F, -0.5F), 0.0F)
                        .larm(0.3F, 0.0F, -0.6F).head(0.0F, Mth.lerp(k2, -0.4F, 0.4F), 0.0F).legs(0.4F, -0.3F).root(-1.5F), sw);
                fold = 1.0F - smooth((st - 26.0F) / 6.0F);
                spread += 0.5F * envelope(st - 30.0F, 3.0F, 4.0F, 10.0F);
                close = fold;
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.STAGE_TRANSFORM -> {
                ascend = envelope(st, 6.0F, 26.0F, 32.0F);
                Pose a = ASCEND.copy();
                if (st > 10.0F && st < 20.0F) a.bZ += Mth.sin(age * 3.0F) * 0.03F; // 力がみなぎって震える
                p.mix(a, ascend);
                spread += 0.6F * smooth((st - 8.0F) / 12.0F) * ascend;
                burst += 0.9F * ascend; // 浮かび上がる噴射
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.STAGE_RETREAT -> {
                // 剣を納め（0〜14）、翼で身を包み（8〜22）、光に溶けて少し浮かぶ
                p.mix(Pose.between(SHEATHE, SHEATHED, smooth((st - 9.0F) / 5.0F)), smooth(st / 5.0F));
                float wrap = smooth((st - 8.0F) / 12.0F);
                spread = Mth.lerp(wrap, spread, 0.5F);
                sweep = Mth.lerp(wrap, sweep, -1.2F);
                p.hX -= 0.3F * wrap;
                p.rY += 4.0F * smooth((st - 18.0F) / 12.0F);
                close = Math.max(close, wrap);
            }
            default -> {
            }
        }
        // 撃破: 剣を突き立てて膝をつき、光になって崩れていく
        if (lux.defeatTicks > 0) {
            float dt = lux.defeatProgress(pt);
            float dw = smooth(dt / 10.0F);
            Pose d = DEFEAT.copy();
            d.bZ += Mth.sin(age * 1.7F) * 0.02F * Math.min(1.0F, dt / 40.0F);
            p.mix(d, dw);
            kneel = Math.max(kneel, dw);
            spread = Mth.lerp(dw, spread, -0.05F);
            sweep = Mth.lerp(dw, sweep, 0.25F);
            close = Math.max(close, dw);
        }
        if (fold > 0.0F) { // 降りてくる間は翼を畳む
            spread = Mth.lerp(fold, spread, -0.15F);
            sweep = Mth.lerp(fold, sweep, -0.2F);
        }

        if (hovering) burst += 0.35F; // 空中にとどまる噴射
        // 三連閃は両手持ち（2026-10-09 ユーザー指定「三連撃は両手で刀を振るって」）: 始めてから締めの振り下ろしを振り抜くまで、左手も柄を握る
        float twoHand = 0.0F;
        float th = age - lux.twoHandTick;
        if (th >= 0.0F && th < 100.0F && lux.stage() == com.automatavalkyries.entity.chrono.LuxionEntity.STAGE_NONE) {
            twoHand = smooth(th / 3.0F) * (1.0F - smooth((th - 90.0F) / 10.0F));
            if (lux.action == com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_SLASH_DOWN && lux.actionTick >= lux.twoHandTick) {
                twoHand *= 1.0F - smooth((t - 12.0F) / 6.0F); // 締めを振り抜いたら片手に戻す
            }
            twoHand *= 1.0F - kneel;
        }
        if (twoHand > 0.0F) grip(p, twoHand);

        // 立ち姿: 足を肩幅より広く開き、つま先を少し外へ（2026-10-09 ユーザー指定「足を広げて少し外股に」）。戦闘の構えではさらに広く。
        // 歩く・膝をつく・空中では弱める（右脚は +X 側。rotZ が正で足先が外、rotY が負でつま先が外）
        boolean airborne = hovering || accelAir;
        float stance = (1.0F - kneel) * (hovering ? 0.4F : accelAir ? 0.0F : 1.0F);
        float legOut = (STANCE_SPREAD + 0.06F * aggro) * stance * (1.0F - 0.5F * walk);
        float toeOut = STANCE_TOE_OUT * stance * (1.0F - 0.6F * walk);
        // 膝（2026-10-09 ユーザー指定「基本的な動作に膝の関節の動き」「攻撃時に足を地面につけて」）: 両足を地面に着ける。
        // 脚を前後へ振るほど足が下まで届かなくなるので、届く方の低い足に合わせて腰を落とし（最大 6px）、足が地面にちょうど着くよう膝を曲げる。
        // 歩くと前へ出す脚だけ持ち上げる。空中では軽く曲げて垂らす。膝をつく型は型どおり
        float ground = 1.0F - kneel;
        float bendR = hovering && !accelAir ? 1.8F : 0.0F, bendL = hovering && !accelAir ? 2.4F : 0.0F;
        if (!airborne) {
            float hip = LEG + p.rY;                                             // 腰の高さ（px）
            float out = Mth.cos(legOut);
            float cosR = Math.max(0.2F, Mth.cos(p.legR) * out), cosL = Math.max(0.2F, Mth.cos(p.legL) * out);
            float crouch = 1.5F * aggro + 1.2F * Math.max(0.0F, p.legR);           // 構え・踏み込みでは少し沈んで両膝を曲げる
            float plant = Math.max(hip - 7.0F, Math.min(hip, LEG * Math.min(cosR, cosL)) - crouch);
            p.rY += (plant - hip) * ground;                                     // 届かなければ腰を落とす
            float lift = Mth.sin(limb * 0.7F) * 4.5F * walk;
            bendR = LEG - Math.min(LEG, plant / cosR) + Math.max(0.0F, -lift);
            bendL = LEG - Math.min(LEG, plant / cosL) + Math.max(0.0F, lift);
        }
        float[] ikR = legIk(p.legR, p.bendR + bendR * ground);
        float[] ikL = legIk(p.legL, p.bendL + bendL * ground);
        CoreGeoBone root = bone("root");
        if (root != null) root.setPosY(p.rY);
        CoreGeoBone rleg = bone("right_leg");
        if (rleg != null) {
            rleg.setRotX(ikR[0]);
            rleg.setRotZ(legOut);
            rleg.setRotY(-toeOut);
        }
        rotX("right_shin", -ikR[1]);
        CoreGeoBone lleg = bone("left_leg");
        if (lleg != null) {
            lleg.setRotX(ikL[0]);
            lleg.setRotZ(-legOut);
            lleg.setRotY(toeOut);
        }
        rotX("left_shin", -ikL[1]);
        CoreGeoBone arm = bone("right_arm");
        if (arm != null) {
            arm.setRotX(p.aX);
            arm.setRotY(p.aY);
            arm.setRotZ(p.aZ);
        }
        CoreGeoBone larm = bone("left_arm");
        if (larm != null) {
            larm.setRotX(p.lX);
            larm.setRotY(p.lY);
            larm.setRotZ(p.lZ);
        }
        CoreGeoBone body = bone("body");
        if (body != null) {
            body.setRotX(p.bX);
            body.setRotY(p.bY);
            body.setRotZ(p.bZ);
        }
        CoreGeoBone head = bone("head");
        if (head != null) {
            head.setRotX(p.hX - pitch * 0.6F * (1.0F - ascend));
            head.setRotY(p.hY);
            head.setRotZ(p.hZ);
        }
        CoreGeoBone tail = bone("tail");
        if (tail != null) {
            tail.setRotX(0.3F + walk * 0.6F + Mth.sin(age * 0.15F) * 0.08F + 0.5F * windup + 0.4F * ascend);
            tail.setRotZ(Mth.sin(age * 0.11F) * 0.1F - p.bY * 0.35F); // 胴のひねりに遅れてなびく
        }
        float thrustersX = -0.2F * walk - 0.3F * windup - 0.15F * Math.min(1.0F, burst) + (lux.isPhase2() ? Mth.sin(age * 0.8F) * 0.04F : 0.0F);
        CoreGeoBone thrusters = bone("thrusters");
        if (thrusters != null) thrusters.setRotX(thrustersX);
        CoreGeoBone wl = bone("wing_left");
        if (wl != null) {
            wl.setRotZ(-spread);
            wl.setRotY(-sweep);
        }
        CoreGeoBone wr = bone("wing_right");
        if (wr != null) {
            wr.setRotZ(spread);
            wr.setRotY(sweep);
        }
        CoreGeoBone cannon = bone("cannon");
        if (cannon != null) cannon.setRotX(-pitch * 0.5F - 0.2F * windup);
        // 第 2 形態: 背の機構がまるごと大きく豪華に（光の翼の骨組みが大きく広がり、ブースターと推進器も一回り大きく）
        if (glory > 0.0F) {
            scale("wing_left", 1.0F + 0.55F * glory);
            scale("wing_right", 1.0F + 0.55F * glory);
            scale("booster", 1.0F + 0.3F * glory);
            scale("thrusters", 1.0F + 0.15F * glory);
            if (tail != null) tail.setScaleY(1.0F + 0.6F * glory);
        }
        // 背の戦闘機型ブースター: 戦闘中は後ろへ倒れて開き、主翼と尾翼が広がる（突進の構え・変身でさらに開く。降臨・退く・撃破では畳む）
        float open = Math.max(Math.min(1.0F, aggro + 0.3F * windup), Math.max(ascend, hovering || accelAir ? 1.0F : 0.0F)) * (1.0F - close);
        CoreGeoBone booster = bone("booster");
        if (booster != null) booster.setRotX(BOOSTER_PITCH * open);
        CoreGeoBone bwl = bone("booster_wing_left");
        if (bwl != null) bwl.setRotZ(BOOSTER_WING * open);
        CoreGeoBone bwr = bone("booster_wing_right");
        if (bwr != null) bwr.setRotZ(-BOOSTER_WING * open);
        CoreGeoBone bfl = bone("booster_fin_left");
        if (bfl != null) bfl.setRotZ(BOOSTER_FIN * open);
        CoreGeoBone bfr = bone("booster_fin_right");
        if (bfr != null) bfr.setRotZ(-BOOSTER_FIN * open);
        // 3 つに分かれ、機首の先（根本）を 1 点に集めたまま噴射口の側を外へ開く（正四面体: 後ろから見て噴射口が上・左下・右下の正三角形）。開き始めてから少し遅れて分かれる
        float split = smooth((open - 0.35F) / 0.65F) * BOOSTER_SPLIT;
        pod("booster_pod_top", 0.0F, 1.0F, split);
        pod("booster_pod_left", 0.866F, -0.5F, split);
        pod("booster_pod_right", -0.866F, -0.5F, split);
        // 光のマント・ブースターの光・光の翼を体に合わせて描くために、今の姿勢を渡す（LuxionRenderer）
        if (!LuxionRenderer.portrait) {
            lux.animRootY = p.rY;
            lux.animBodyX = p.bX;
            lux.animBodyY = p.bY;
            lux.animBodyZ = p.bZ;
            lux.animThrustersX = thrustersX;
            lux.animSpread = spread;
            lux.animSweep = sweep;
            lux.animClose = close;
            lux.animWindup = Math.max(windup, pull);
            lux.animBurst = burst;
            lux.animFlare = flare;
            lux.animOpen = open;
            lux.animHover = hovering;
        }
    }

    /** 脚の長さ（px）: 股関節から膝（太もも）、膝から足の裏（脛と足）、合わせて */
    private static final float THIGH = 7.5F, SHIN = 12.5F, LEG = THIGH + SHIN;

    /**
     * 2 本の骨の脚: 股関節から足先への向き angle（前へ振るのが正）と、まっすぐからの縮み bend（px）から、太ももの傾きと膝の曲げを出す（膝は前へ出る）。
     * 足先の場所は 1 本の骨だったときと同じなので、今までの型の足の置き場所はそのまま
     *
     * @return {太ももの rotX, 膝の曲げ（正。脛の rotX はこの負）}
     */
    private static float[] legIk(float angle, float bend) {
        float d = Mth.clamp(LEG - bend, 8.0F, LEG);
        float knee = Mth.PI - (float) Math.acos(Mth.clamp((THIGH * THIGH + SHIN * SHIN - d * d) / (2.0F * THIGH * SHIN), -1.0F, 1.0F));
        float hip = (float) Math.acos(Mth.clamp((THIGH * THIGH + d * d - SHIN * SHIN) / (2.0F * THIGH * d), -1.0F, 1.0F));
        return new float[]{angle + hip, knee};
    }

    /** 肩（胴の中の px。GeckoLib は geo の X を反転するので右肩が +X）と、拳までの長さ・両手で柄を握る所までの前への距離 */
    private static final float SHOULDER_X = 5.5F, SHOULDER_Y = 30.0F, ARM = 12.2F, GRIP_REACH = 10.9F;

    /**
     * 両手持ち: 右腕の振りの向きのまま、両肩の真ん中の前（柄）に両手を集める。w で今の姿勢から寄せる。
     * 腕の向き（Z・Y・X の順に回した下向き）から柄の場所を出し、両腕をそこへ向ける（ねじりは無し）
     */
    private static void grip(Pose p, float w) {
        float sx = Mth.sin(p.aX), cx = Mth.cos(p.aX), sy = Mth.sin(p.aY), cy = Mth.cos(p.aY), sz = Mth.sin(p.aZ), cz = Mth.cos(p.aZ);
        float dx0 = -sx * sy, dy0 = -cx, dz = -sx * cy;
        float dx = dx0 * cz - dy0 * sz, dy = dx0 * sz + dy0 * cz;
        float gx = dx * GRIP_REACH, gy = SHOULDER_Y + dy * GRIP_REACH, gz = dz * GRIP_REACH;
        float[] r = aim(gx - SHOULDER_X, gy - SHOULDER_Y, gz);
        float[] l = aim(gx + SHOULDER_X, gy - SHOULDER_Y, gz);
        p.aX = Mth.lerp(w, p.aX, r[0]); p.aY = Mth.lerp(w, p.aY, r[1]); p.aZ = Mth.lerp(w, p.aZ, 0.0F);
        p.lX = Mth.lerp(w, p.lX, l[0]); p.lY = Mth.lerp(w, p.lY, l[1]); p.lZ = Mth.lerp(w, p.lZ, 0.0F);
    }

    /** 下向きの腕を (x, y, z) へ向ける rotX・rotY */
    private static float[] aim(float x, float y, float z) {
        float len = Mth.sqrt(x * x + y * y + z * z);
        if (len < 1.0E-4F) return new float[]{0.0F, 0.0F};
        float ax = (float) Math.acos(Mth.clamp(-y / len, -1.0F, 1.0F));
        float ay = Math.abs(Mth.sin(ax)) < 1.0E-3F ? 0.0F : (float) Mth.atan2(-x, -z);
        return new float[]{ax, ay};
    }

    /** 立ち姿の脚の開き・つま先の外向き（ラジアン） */
    private static final float STANCE_SPREAD = 0.11F, STANCE_TOE_OUT = 0.24F;

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * ブースターの部品を、機首の先（骨のピボット）を中心に angle だけ傾ける。GeckoLib の回転の向きの取り方で、噴射口の側は (dx, dz) と逆へ動き、
     * 3 つの部品が途中で交差して反対側へ開く（後ろから見て上 2・下 1 の三角）。この見た目のままにする（2026-10-08 ユーザー指定「修正しなくていい」）
     */
    private void pod(String name, float dx, float dz, float angle) {
        CoreGeoBone b = bone(name);
        if (b == null) return;
        b.setRotX(angle * dz);
        b.setRotZ(angle * dx);
    }

    private void scale(String name, float v) {
        CoreGeoBone b = bone(name);
        if (b == null) return;
        b.setScaleX(v);
        b.setScaleY(v);
        b.setScaleZ(v);
    }

    private void rotX(String name, float v) {
        CoreGeoBone b = bone(name);
        if (b != null) b.setRotX(v);
    }

    private void rotY(String name, float v) {
        CoreGeoBone b = bone(name);
        if (b != null) b.setRotY(v);
    }

    private CoreGeoBone bone(String name) {
        return getAnimationProcessor().getBone(name);
    }
}
