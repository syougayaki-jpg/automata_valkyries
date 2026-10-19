package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.BastionKeeperEntity;
import com.automatavalkyries.entity.chrono.ChronoGuardianEntity;
import com.automatavalkyries.entity.chrono.DelayNodeEntity;
import com.automatavalkyries.entity.chrono.InterceptorEntity;
import com.automatavalkyries.entity.chrono.RulerAnchorEntity;
import com.automatavalkyries.entity.chrono.TimeRulerEntity;
import com.automatavalkyries.entity.chrono.CraneLoaderEntity;
import com.automatavalkyries.entity.chrono.FoundryMasterEntity;
import com.automatavalkyries.entity.chrono.MimicContainerEntity;
import com.automatavalkyries.entity.chrono.OutpostWatcherEntity;
import com.automatavalkyries.entity.chrono.PhaseCondenserEntity;
import com.automatavalkyries.entity.chrono.PulsePhalanxEntity;
import com.automatavalkyries.entity.chrono.SiegeCannonEntity;
import com.automatavalkyries.entity.chrono.SirenEyeEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * 侵食基地の雑魚・中ボス・コンデンサーのモデル（2026-10-15。geo / テクスチャは tools/gen_chrono_base.py）。アニメーションのファイルは持たず、ここで骨を動かす:
 * タレットの砲塔の向き・連射の反動とレーダーの回転、曲射砲の砲身の仰角と反動、警報ドローンの一つ目と回る警告灯、ガーディアンの 4 本脚の歩き・盾で受ける・ロッドを振る、
 * ミミックの化けている間（脚をしまう）と目覚め（蓋が開いて脚が伸び、噛む）、荷役機の履帯・クレーン・フォーク、司令機の歩き・レーダー・機銃、
 * 工場長機の台座の上の胴の向き・4 本の腕の技・胸の核、コンデンサーの核の脈と回る輪
 */
public class ChronoBaseModel<T extends Mob & GeoEntity> extends DefaultedEntityGeoModel<T> {

    public ChronoBaseModel(String name) {
        super(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, name), false);
    }

    @Override
    public void setCustomAnimations(T entity, long instanceId, AnimationState<T> state) {
        float pt = state.getPartialTick();
        float age = entity.tickCount + pt;
        float walk = Math.min(state.getLimbSwingAmount(), 1.0F);
        float limb = state.getLimbSwing();
        float pitch = Mth.lerp(pt, entity.xRotO, entity.getXRot()) * Mth.DEG_TO_RAD;
        float yaw = Mth.wrapDegrees(Mth.lerp(pt, entity.yHeadRotO, entity.yHeadRot) - Mth.lerp(pt, entity.yBodyRotO, entity.yBodyRot)) * Mth.DEG_TO_RAD;

        if (entity instanceof PulsePhalanxEntity turret) {
            rotY("turret", yaw);
            rotX("guns", -pitch);
            float fire = age - turret.fireTick;
            CoreGeoBone barrels = bone("barrels");
            if (barrels != null) barrels.setPosZ(fire >= 0 && fire < 3 ? 0.8F * (1.0F - fire / 3.0F) : 0.0F);
            rotY("dish", age * 0.15F);
        } else if (entity instanceof SiegeCannonEntity cannon) {
            rotY("mount", yaw);
            float fire = age - cannon.fireTick;
            float recoil = fire >= 0 && fire < 10 ? 1.0F - fire / 10.0F : 0.0F;
            CoreGeoBone tube = bone("tube");
            if (tube != null) {
                tube.setRotX(Mth.clamp(-pitch, 0.2F, 1.1F)); // いつも上を向く（曲射）
                tube.setPosZ(3.0F * recoil);
            }
        } else if (entity instanceof SirenEyeEntity eye) {
            CoreGeoBone body = bone("body");
            if (body != null) {
                body.setPosY(Mth.sin(age * 0.12F) * 0.8F);
                body.setRotX(-pitch * 0.8F);
            }
            rotY("siren", age * (eye.isSounding() ? 0.6F : 0.05F));
            CoreGeoBone ball = bone("eye");
            if (ball != null) {
                float s = eye.isSounding() ? 1.0F + 0.08F * Mth.sin(age * 1.2F) : 1.0F;
                ball.setScaleX(s);
                ball.setScaleY(s);
            }
            rotZ("fins", Mth.sin(age * 0.3F) * 0.15F);
        } else if (entity instanceof ChronoGuardianEntity guardian) {
            for (int i = 0; i < 4; i++) { // 対角の 2 本ずつ交互に
                float phase = (i == 0 || i == 3) ? 0.0F : Mth.PI;
                rotX("leg_" + i, Mth.cos(limb * 0.7F + phase) * 0.5F * walk);
            }
            CoreGeoBone body = bone("body");
            if (body != null) body.setPosY(Math.abs(Mth.sin(limb * 0.7F)) * 0.6F * walk);
            float block = age - guardian.blockTick;
            CoreGeoBone shield = bone("shield");
            if (shield != null) shield.setPosZ(block >= 0 && block < 6 ? -1.2F * (1.0F - block / 6.0F) : 0.0F); // 盾を押し出して受ける
            float swing = (age - guardian.swingTick) / 8.0F;
            rotX("rod", swing >= 0 && swing < 1 ? 1.6F * Mth.sin(swing * Mth.PI) : 0.25F);
        } else if (entity instanceof MimicContainerEntity mimic) {
            boolean dormant = mimic.isDormant();
            float open = dormant ? 0.0F : Math.min(1.0F, (age - mimic.wakeTick) / 6.0F);
            float chew = dormant ? 0.0F : 0.35F + 0.25F * Mth.sin(age * 0.6F);
            rotX("lid", -open * (0.6F + chew));                              // 蓋が後ろの蝶番で開く
            for (int i = 0; i < 4; i++) {
                CoreGeoBone leg = bone("leg_" + i);
                if (leg == null) continue;
                float out = open;                                             // 化けている間は脚をしまう
                leg.setScaleY(Math.max(0.01F, out));
                leg.setPosY((1.0F - out) * 6.0F);
                float phase = (i == 0 || i == 3) ? 0.0F : Mth.PI;
                leg.setRotX(Mth.cos(limb * 1.1F + phase) * 0.6F * walk * out);
            }
            CoreGeoBone crate = bone("crate");
            if (crate != null) crate.setPosY(open * 6.0F + Math.abs(Mth.sin(limb * 1.1F)) * walk * 1.0F);
        } else if (entity instanceof CraneLoaderEntity loader) {
            float charge = age - loader.chargeTick;
            float thrown = (age - loader.throwTick) / 12.0F;
            float drop = (age - loader.dropTick) / 16.0F;
            CoreGeoBone body = bone("body");
            if (body != null) body.setPosY(Mth.sin(age * 0.5F) * 0.15F * walk);
            rotY("boom", yaw + (thrown >= 0 && thrown < 1 ? -1.2F * Mth.sin(thrown * Mth.PI) : 0.0F)); // クレーンを振って投げる
            rotX("arm", drop >= 0 && drop < 1 ? -0.35F * Mth.sin(drop * Mth.PI) : 0.0F);
            CoreGeoBone forks = bone("forks");
            if (forks != null) forks.setPosY(charge >= 0 && charge < 40 ? -4.0F : 0.0F); // 突進ではフォークを下げる
            CoreGeoBone hook = bone("hook");
            if (hook != null) hook.setRotX(Mth.sin(age * 0.1F) * 0.15F + walk * 0.2F);
        } else if (entity instanceof OutpostWatcherEntity watcher) {
            float swing = Mth.cos(limb * 0.5F) * 0.5F * walk;
            rotX("leg_left", swing);
            rotX("leg_right", -swing);
            CoreGeoBone body = bone("body");
            if (body != null) body.setPosY(Math.abs(Mth.sin(limb * 0.5F)) * 0.8F * walk);
            CoreGeoBone head = bone("head");
            if (head != null) {
                head.setRotY(yaw);
                head.setRotX(-pitch * 0.6F);
            }
            float radar = age - watcher.radarTick;
            rotY("dish", age * (radar >= 0 && radar < 40 ? 0.8F : 0.08F));
            float gun = age - watcher.gunTick;
            float kick = gun >= 0 && gun < 3 ? 0.6F : 0.0F;
            for (String g : new String[]{"gun_left", "gun_right"}) {
                CoreGeoBone b = bone(g);
                if (b == null) continue;
                b.setRotX(-pitch * 0.8F);
                b.setPosZ(kick);
            }
        } else if (entity instanceof FoundryMasterEntity master) {
            rotY("torso", yaw);
            CoreGeoBone core = bone("core");
            if (core != null) {
                float s = master.isShielded() ? 1.0F + 0.1F * Mth.sin(age * 0.3F) : 0.8F;
                core.setScaleX(s);
                core.setScaleY(s);
            }
            float spin = (age - master.spinTick) / 12.0F;
            CoreGeoBone torso = bone("torso");
            if (torso != null && spin >= 0 && spin < 1) torso.setRotY(yaw + spin * Mth.TWO_PI); // 回転アーム
            float arc = (age - master.arcTick) / 10.0F;
            rotX("arm_0", arc >= 0 && arc < 1 ? -0.3F : Mth.sin(age * 0.07F) * 0.1F);
            float press = (age - master.pressTick) / 10.0F;
            rotX("arm_1", press >= 0 && press < 1 ? -1.2F + 1.2F * press : -0.4F + Mth.sin(age * 0.05F) * 0.05F); // 振り上げた槌を下ろす
            float build = (age - master.buildTick) / 30.0F;
            float busy = build >= 0 && build < 1 ? Mth.sin(age * 0.8F) * 0.4F : Mth.sin(age * 0.09F + 1.0F) * 0.1F;
            rotX("arm_2", busy);
            rotX("arm_3", -busy * 0.8F); // ドリルの腕は掴み爪と逆へ
        } else if (entity instanceof BastionKeeperEntity keeper) { // 浮遊の守護機: 体が上下に揺れ、光輪が回り（錨では速く）、4 枚の盾板が周りを回る（弾幕では開く）
            CoreGeoBone body = bone("body");
            if (body != null) body.setPosY(Mth.sin(age * 0.08F) * 1.2F);
            float anchor = age - keeper.anchorTick;
            rotZ("halo", age * (anchor >= 0 && anchor < 32 ? 0.5F : 0.04F));
            float volley = age - keeper.volleyTick;
            float open = volley >= 0 && volley < 30 ? 1.0F : 0.0F;
            for (int i = 0; i < 4; i++) {
                CoreGeoBone plate = bone("plate_" + i);
                if (plate == null) continue;
                plate.setRotY(age * 0.03F + i * Mth.HALF_PI);
                plate.setPosY(Mth.sin(age * 0.1F + i) * 0.8F + open * 2.0F);
            }
            rotY("head", yaw);
            CoreGeoBone eye = bone("eye");
            if (eye != null) {
                float stasis = age - keeper.stasisTick, lance = age - keeper.lanceTick;
                float s = (stasis >= 0 && stasis < 20) || (lance >= 0 && lance < 20) ? 1.35F : 1.0F + 0.05F * Mth.sin(age * 0.2F);
                eye.setScaleX(s);
                eye.setScaleY(s);
            }
        } else if (entity instanceof InterceptorEntity jet) { // 三角翼: 旋回で傾き、急降下で翼をたたむ
            CoreGeoBone body = bone("body");
            float dive = age - jet.diveTick;
            boolean diving = dive >= 0 && dive < InterceptorEntity.DIVE_TICKS;
            if (body != null) {
                body.setRotZ(Mth.sin(age * 0.07F) * 0.35F);
                body.setRotX(diving ? -0.35F : -pitch * 0.5F);
            }
            rotZ("wing_left", diving ? -0.3F : 0.0F);
            rotZ("wing_right", diving ? 0.3F : 0.0F);
            CoreGeoBone engine = bone("engine");
            if (engine != null) {
                float s = diving ? 1.4F : 1.0F + 0.1F * Mth.sin(age * 1.3F);
                engine.setScaleX(s);
                engine.setScaleY(s);
            }
        } else if (entity instanceof TimeRulerEntity ruler) { // 騎士の姿の時計の機械神（動きは animateRuler）
            animateRuler(ruler, age, pt, pitch);
        } else if (entity instanceof RulerAnchorEntity) { // 因果の歯車: 回り、脈打つ
            rotZ("gear", age * 0.1F);
            CoreGeoBone core = bone("core");
            if (core != null) {
                float s = 1.0F + 0.15F * Mth.sin(age * 0.3F);
                core.setScaleX(s);
                core.setScaleY(s);
                core.setScaleZ(s);
            }
            CoreGeoBone root = bone("gear");
            if (root != null) root.setPosY(Mth.sin(age * 0.08F) * 1.0F);
        } else if (entity instanceof DelayNodeEntity) { // 砂時計: 核の脈と、逆へ回る 2 つの輪
            CoreGeoBone core = bone("core");
            if (core != null) {
                core.setPosY(Mth.sin(age * 0.06F) * 0.6F);
                core.setRotY(age * 0.02F);
            }
            rotY("ring_a", age * 0.06F);
            rotY("ring_b", -age * 0.045F);
            CoreGeoBone ringB = bone("ring_b");
            if (ringB != null) ringB.setRotX(0.4F);
        } else if (entity instanceof PhaseCondenserEntity) {
            CoreGeoBone core = bone("core");
            if (core != null) {
                float s = 1.0F + 0.12F * Mth.sin(age * 0.25F);
                core.setScaleX(s);
                core.setScaleY(s);
                core.setScaleZ(s);
            }
            rotY("ring", age * 0.08F);
        }
    }

    /** 0〜1 の、t の間の山なりの重み（立ち上がり in、保ち、戻り out の Tick） */
    private static float pulse(float t, float in, float hold, float out) {
        if (t < 0) return 0.0F;
        if (t < in) return Mth.sin(t / in * Mth.HALF_PI);
        if (t < in + hold) return 1.0F;
        if (t < in + hold + out) return Mth.cos((t - in - hold) / out * Mth.HALF_PI);
        return 0.0F;
    }

    private void set(String name, float rx, float ry, float rz) {
        CoreGeoBone b = bone(name);
        if (b == null) return;
        b.setRotX(rx);
        b.setRotY(ry);
        b.setRotZ(rz);
    }

    /**
     * タイムルーラーの動き（2026-10-19 ユーザー指定「モーションの全体的に改良して」）。
     * 呼吸・腕の揺れ・首の傾げ・ぶら下がる脚・2 つの波で揺れるマント。動く向きへの前傾と横傾き（脚とマントがなびく）。
     * 技ごとの構え: 針（左腕で突く）・一閃（右腕を振りかぶって振り下ろす）・掃射（両腕を広げて反る）・消去（右腕で指す）・
     * 時間停止の予告（両腕を天へ、光輪が広がる）・因果逆転（腕を前で交差して身を丸める）・刻の終焉（両腕を大きく広げる）。
     * 被弾でのけぞり、撃破で前へ崩れて腕が垂れ、光輪が落ちる。形態が進むほど速く大きく。前後（rotX）はゲームで逆だったので、
     * 下の式の符号のまま最後に裏返して入れる（2026-10-19 ユーザー指定「モーションは全体的に前後が逆でした」。この骨では rotX が正で前・上へ）。
     * rotZ は右腕が正・左腕が負で外へ（いままでの動きと同じ向き）。
     * 肘（forearm_*）と膝（shin_*）を曲げる（2026-10-19 ユーザー指定「肘と膝の動きを追加して立ち姿は少し前に膝を曲げ手を引きゆうゆうとたたずむように」）:
     * 技をしていない間は、膝を少し前へ出して曲げ、腕を後ろへ引いて肘を軽く曲げ、胸を張ってあごを上げ、悠然とたたずむ
     * さらに（2026-10-19 ユーザー指定「更にモーションの全体改良して」）: ときどき左の掌を差し出して見下ろす・辺りを見回すしぐさ、マントと光輪が体に遅れて揺れる、
     * 動くと腕がなびく、被弾で肩をすくめる、針は腕を引いて溜めてから突く、座標が消える瞬間の反動、時が止まる瞬間に腕を広げて胸を反らし、止まった時の中では右腕で指揮をする
     */
    private void animateRuler(TimeRulerEntity r, float age, float pt, float pitch) {
        int phase = r.phase();
        float pace = phase == 3 ? 1.5F : phase == 2 ? 1.2F : 1.0F;
        boolean back = r.isRewinding();
        float speed = back ? -0.25F : 1.0F;
        float dying = r.deathTime > 0 ? Math.min(1.0F, (r.deathTime + pt) / 40.0F) : 0.0F;
        float hurt = r.hurtTime > 0 ? (r.hurtTime - pt) / 10.0F : 0.0F;

        // ---- 動く向きへの傾き（体の向きで見た前・横の速さ）
        net.minecraft.world.phys.Vec3 v = r.getDeltaMovement();
        float yawRad = Mth.lerp(pt, r.yBodyRotO, r.yBodyRot) * Mth.DEG_TO_RAD;
        float fwd = (float) (-v.x * Mth.sin(yawRad) + v.z * Mth.cos(yawRad));
        float side = (float) (v.x * Mth.cos(yawRad) + v.z * Mth.sin(yawRad));
        float lean = Mth.clamp(fwd * 2.2F, -0.35F, 0.35F), roll = Mth.clamp(side * 2.2F, -0.3F, 0.3F);
        float moving = Mth.clamp((float) Math.sqrt(fwd * fwd + side * side) * 4.0F, 0.0F, 1.0F);

        // ---- 技の重み（それぞれ 0〜1）
        float needleT = age - r.needleTick;
        float needleWind = pulse(needleT, 2, 2, 3);                                                // 針: 一度腕を引いて肘を曲げ（溜め）
        float needle = pulse(needleT - 4, 2, 6, 8);                                                // それから突き出す
        float slashT = age - r.slashTick;
        float windup = slashT >= 0 && slashT < 15 ? Mth.sin(slashT / 15.0F * Mth.HALF_PI) : 0.0F;   // 振りかぶる（技の 0.75 秒の構え）
        float strike = pulse(slashT - 15, 2, 4, 10);                                                // 振り下ろす
        float sweep = pulse(age - r.sweepTick, 8, 64, 10);
        float erase = pulse(age - r.eraseTick, 6, 28, 8);
        float cast = pulse(age - r.stopTick, 10, 30, 12);                                          // 時間停止の予告
        float rewind = back ? 1.0F : 0.0F;
        float finale = pulse(age - r.finaleTick, 20, 260, 20);
        float eraseKick = pulse(age - r.eraseTick - 40, 1, 2, 8);                                  // 座標が消える瞬間の反動
        float snap = pulse(age - r.stopTick - 40, 2, 6, 14);                                       // 時が止まる瞬間に腕を広げて胸を反らす
        float conduct = ClientTimeStop.running() ? 1.0F : 0.0F;                                   // 止まった時の中で指揮をする
        // 落ち着き: 技をしていない度合い（2026-10-19 ユーザー指定「立ち姿は少し前に膝を曲げ手を引きゆうゆうとたたずむように」）
        float calm = 1.0F - Mth.clamp(needleWind + needle + windup + strike + sweep + erase + eraseKick + cast + snap + conduct + rewind + finale + dying, 0.0F, 1.0F);
        // ふだんのしぐさ（落ち着いている間だけ）: ときどき左の掌を上に向けて差し出し見下ろす・首を巡らせて辺りを見回す
        float present = pulse((age % 320.0F) - 200.0F, 25, 40, 25) * calm;
        float lookAround = pulse((age % 460.0F) - 300.0F, 20, 50, 20) * calm;

        // ---- 体: 呼吸と揺れ、傾き、のけぞり、構え、崩れ
        float breath = Mth.sin(age * 0.06F * pace);
        float bob = Mth.sin(age * 0.05F * pace) * 1.5F + finale * 2.0F + cast * 1.2F - dying * 6.0F;
        float bodyX = 0.04F * breath + lean - hurt * 0.25F - cast * 0.18F - sweep * 0.12F + rewind * 0.25F + needle * 0.08F - needleWind * 0.05F + dying * 0.7F
                - eraseKick * 0.15F - snap * 0.22F;
        float bodyY = windup * 0.35F - strike * 0.55F;
        float bodyZ = -roll + Mth.sin(age * 0.031F) * 0.03F;
        CoreGeoBone body = bone("body");
        if (body != null) {
            body.setPosY(bob);
            body.setRotX(-bodyX - 0.06F * calm);                                           // 前後はゲームで逆だったので裏返す。たたずむ間は胸を張る
            body.setRotY(bodyY);
            body.setRotZ(bodyZ);
        }

        // ---- 頭: 相手を見上げ・見下ろし、首をわずかに傾げる。崩れると垂れる
        set("head", -(-pitch * 0.5F + rewind * 0.3F + dying * 0.6F - cast * 0.3F) - 0.1F * calm + 0.18F * present - 0.2F * hurt - 0.15F * snap - 0.1F * conduct,
                Mth.sin(age * 0.023F) * 0.08F + 0.3F * present + Mth.sin(age * 0.04F) * 0.5F * lookAround, Mth.sin(age * 0.037F) * 0.06F + 0.08F * present);
        CoreGeoBone visor = bone("visor");
        if (visor != null) {
            float vs = 1.0F + erase * 0.15F + cast * 0.1F;
            visor.setScaleX(vs);
            visor.setScaleY(vs);
        }

        // ---- 腕（右: 一閃・消去で指す。左: 針で突く。両方: 掃射で広げる・予告で天へ・逆転で交差・終焉で大きく広げる。崩れると垂れる）
        float idleR = Mth.sin(age * 0.045F * pace) * 0.07F, idleL = Mth.sin(age * 0.045F * pace + 1.7F) * 0.07F;
        float rx = idleR + 0.8F * windup - 1.9F * strike - 1.55F * erase - 0.4F * eraseKick - 2.7F * cast - 1.0F * rewind - 0.4F * finale - 0.9F * conduct + dying * 0.3F;
        float ry = -0.5F * windup + 0.4F * strike - 0.6F * rewind;
        float rz = 0.12F + 0.5F * windup + 1.2F * sweep + 0.25F * cast + 1.0F * snap - 0.7F * rewind + 1.35F * finale - lean * 0.3F
                + Mth.sin(age * 0.15F) * 0.5F * conduct + 0.25F * hurt;
        float lx = idleL - 1.5F * needle + 0.35F * needleWind - 2.7F * cast - 1.0F * rewind - 0.4F * finale - 0.7F * present + dying * 0.3F;
        float ly = 0.3F * needle + 0.6F * rewind + 0.3F * present;
        float lz = -0.12F - 1.2F * sweep - 0.25F * cast - 1.0F * snap + 0.7F * rewind - 1.35F * finale - lean * -0.3F + 0.2F * present - 0.25F * hurt;
        float limp = 1.0F - dying;
        set("arm_right", -rx * limp - 0.3F * calm - 0.3F * moving, ry * limp, rz * limp + dying * 0.1F + 0.08F * calm);   // たたずむ: 手を後ろへ引く
        set("arm_left", -lx * limp - 0.3F * calm - 0.3F * moving, ly * limp, lz * limp - dying * 0.1F - 0.08F * calm);
        // 肘（rotX が正で前腕が前・上へ曲がる）: たたずむ間は軽く曲げ、振りかぶり・交差で深く、突く・振り下ろす・指す・掲げる・広げるで伸ばす
        float elbowR = 0.55F * calm + 1.2F * windup + 0.15F * strike + 1.4F * rewind + 0.2F * sweep + 0.4F * conduct + 0.5F * dying + Mth.sin(age * 0.04F) * 0.04F * calm;
        float elbowL = 0.55F * calm + 0.25F * needle + 1.1F * needleWind + 1.4F * rewind + 0.2F * sweep + 0.6F * present + 0.5F * dying + Mth.sin(age * 0.04F + 1.3F) * 0.04F * calm;
        rotX("forearm_right", elbowR);
        rotX("forearm_left", elbowL);

        // ---- 脚: 左右の位相をずらしてぶら下がり、動くと後ろへなびく
        for (String leg : new String[]{"leg_right", "leg_left"}) {
            CoreGeoBone b = bone(leg);
            if (b == null) continue;
            float ph = leg.equals("leg_left") ? 1.3F : 0.0F;
            b.setPosY(bob);
            // 太もも（rotX が正で前へ）: たたずむ間は膝を少し前へ出し、動くと後ろへなびく
            b.setRotX(0.24F * calm + Mth.sin(age * 0.05F * pace + ph) * 0.05F - moving * 0.35F - lean * 0.5F + finale * 0.1F + dying * 0.25F);
            b.setRotZ((leg.equals("leg_left") ? -1 : 1) * (0.03F + sweep * 0.08F + finale * 0.12F) - roll * 0.5F);
            // 膝（脛から下。rotX が負で後ろへ曲がる）: たたずむ間は軽く曲げ、動くとさらに曲げ、崩れると深く
            rotX(leg.replace("leg", "shin"), -(0.5F * calm + 0.25F + moving * 0.3F + finale * 0.1F + dying * 0.6F + Mth.sin(age * 0.05F * pace + ph + 0.8F) * 0.05F));
        }

        // ---- マント: 2 つの波、動くと後ろへなびく、掃射・終焉で広がる
        set("cape", -(0.08F + Mth.sin(age * 0.09F) * 0.05F + Mth.sin(age * 0.23F + 0.6F) * 0.025F - Mth.cos(age * 0.05F * pace) * 0.04F + moving * 0.45F + sweep * 0.15F + finale * 0.25F - dying * 0.05F),
                0.0F, Mth.sin(age * 0.05F) * 0.03F);

        // ---- 光輪: 回り、上下し、予告・終焉で広がり速く回る。崩れると落ちる
        float haloSpin = 0.04F * pace + cast * 0.25F + finale * 0.2F;
        CoreGeoBone halo = bone("halo_top");
        if (halo != null) {
            halo.setRotY(age * haloSpin);
            halo.setPosY(Mth.sin(age * 0.05F * pace - 0.7F) * 1.0F + Mth.sin(age * 0.08F) * 0.4F + cast * 1.5F + snap * 1.0F - dying * 8.0F);   // 体の上下に少し遅れて揺れる
            float hs = 1.0F + cast * 0.35F + finale * 0.25F;
            halo.setScaleX(hs);
            halo.setScaleZ(hs);
        }
        rotX("halo_left", age * (0.05F + haloSpin * 0.5F));
        rotX("halo_right", -age * (0.05F + haloSpin * 0.5F));

        // ---- 背の文字盤: 針・内輪・歯車。予告で脈打ち、終焉で大きく、崩れると傾く
        rotZ("hand_minute", -age * 0.08F * speed * pace);
        rotZ("hand_hour", -age * 0.008F * speed * pace);
        float stop = age - r.stopTick;
        if (stop >= 0 && stop < 40) rotZ("hand_minute", -Mth.PI * 2 * (stop / 40.0F));                    // 予告の鐘: 長針がひと回り
        CoreGeoBone dial = bone("dial");
        if (dial != null) {
            dial.setRotZ(Mth.sin(age * 0.02F) * 0.05F + dying * 0.4F);
            dial.setRotX(-dying * 0.3F);
            float ds = 1.0F + cast * 0.08F * (1.0F + Mth.sin(age * 0.8F)) + finale * 0.12F;
            dial.setScaleX(ds);
            dial.setScaleY(ds);
        }
        float gs = age * 0.03F * speed * pace;                                                             // 歯車: かみ合う向きに逆へ、歯の数の比で
        rotZ("gear_back", gs);
        rotZ("gear_back_small", -gs * 7.4F / 3.6F);
        rotZ("gear_back_low", -gs * 7.4F / 4.4F);
        rotZ("gear_chest", gs * 1.5F);
        rotX("gear_hip_left", gs * 2.0F);
        rotX("gear_hip_right", -gs * 2.0F);
        rotZ("dial_inner", -age * (0.012F + sweep * 0.05F + finale * 0.03F) * speed - rewind * age * 0.04F);
        // ---- 出現・撤退・撃破の演出（2026-10-19 ユーザー指定「撃破演出出現演出退場演出まで自作して作りこんで」。時間割は TimeRulerEntity の各 tick*）
        scale("root", 1.0F);                                                                              // 骨は同じ種類の個体どうしで共有なので、毎回戻してから演出を重ねる
        CoreGeoBone root0 = bone("root");
        if (root0 != null) root0.setRotY(0.0F);
        scale("halo_left", 1.0F);
        scale("halo_right", 1.0F);
        CoreGeoBone haloY = bone("halo_top");
        if (haloY != null) haloY.setScaleY(1.0F);
        CoreGeoBone visorZ = bone("visor");
        if (visorZ != null) visorZ.setScaleZ(1.0F);
        CoreGeoBone dialZ = bone("dial");
        if (dialZ != null) dialZ.setScaleZ(1.0F);
        float introT = age - r.introTick;
        if (introT >= 0 && introT < TimeRulerEntity.INTRO_TICKS + 10) rulerIntro(introT);
        float retreatT = age - r.retreatTick;
        if (retreatT >= 0 && retreatT < TimeRulerEntity.RETREAT_TICKS + 10) rulerRetreat(retreatT);
        if (r.deathTime > 0) rulerDeath(r.deathTime + pt);
    }

    /** 0〜1 へなめらかに（t が a から b の間） */
    private static float smooth(float t, float a, float b) {
        float k = Mth.clamp((t - a) / (b - a), 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    private void scale(String name, float s) {
        CoreGeoBone b = bone(name);
        if (b == null) return;
        b.setScaleX(s);
        b.setScaleY(s);
        b.setScaleZ(s);
    }

    /** いまの向きから (x, y, z) へ w だけ寄せる */
    private void blend(String name, float x, float y, float z, float w) {
        CoreGeoBone b = bone(name);
        if (b == null || w <= 0) return;
        b.setRotX(Mth.lerp(w, b.getRotX(), x));
        b.setRotY(Mth.lerp(w, b.getRotY(), y));
        b.setRotZ(Mth.lerp(w, b.getRotZ(), z));
    }

    /**
     * 出現: 20〜55 で足元から組み上がる（root を大きくする）。腕は前で交差したまま 90〜105 で広げて構える。50〜75 文字盤が針を速く回しながら展開する。
     * 60〜80 マントがほどける（上へたたまれていたのが下りる）。70〜80 面頬が灯る。75〜85 光輪がはまる（少し大きくなって戻る）
     */
    private void rulerIntro(float t) {
        scale("root", Math.max(0.001F, smooth(t, 20, 55)));
        float crossed = 1.0F - smooth(t, 90, 105);
        blend("arm_right", 0.9F, -0.6F, -0.7F, crossed);
        blend("arm_left", 0.9F, 0.6F, 0.7F, crossed);
        blend("forearm_right", 1.4F, 0, 0, crossed);
        blend("forearm_left", 1.4F, 0, 0, crossed);
        float unfold = smooth(t, 50, 72);
        scale("dial", Math.max(0.001F, unfold * (1.0F + 0.12F * Mth.sin(unfold * Mth.PI))));
        CoreGeoBone dial = bone("dial");
        if (dial != null) dial.setRotZ(dial.getRotZ() + (1.0F - unfold) * Mth.TWO_PI * 2.0F);
        if (t < 80) rotZ("hand_minute", -t * 0.6F);
        blend("cape", 1.4F, 0, 0, 1.0F - smooth(t, 60, 80));
        scale("visor", Math.max(0.001F, smooth(t, 70, 78)));
        float halo = smooth(t, 75, 82) * (1.0F + 0.25F * Mth.sin(smooth(t, 75, 88) * Mth.PI));
        scale("halo_top", Math.max(0.001F, halo));
        scale("halo_left", Math.max(0.001F, halo));
        scale("halo_right", Math.max(0.001F, halo));
    }

    /** 撤退: 右腕を掲げ、文字盤の針と内輪を逆へ速く回し、30〜52 で回りながら縮んで点になる */
    private void rulerRetreat(float t) {
        float raise = smooth(t, 0, 12);
        blend("arm_right", 2.7F, 0, 0.3F, raise);
        blend("forearm_right", 0.1F, 0, 0, raise);
        rotZ("hand_minute", t * 0.8F);
        rotZ("hand_hour", t * 0.2F);
        rotZ("dial_inner", t * 0.5F);
        float gone = smooth(t, 30, 52);
        scale("root", Math.max(0.001F, 1.0F - gone));
        CoreGeoBone root = bone("root");
        if (root != null) root.setRotY(gone * Mth.TWO_PI * 3.0F);
    }

    /**
     * 撃破: 20〜100 文字盤の針が狂ったように逆回り、30〜45 光輪が明滅して落ちる。60〜100 膝をついて崩れる（太ももは前、膝は深く、体は前へ、頭は垂れる）。
     * 100〜120 文字盤が膨らみ、120〜124 で砕けて消える。145〜160 体が小さくなって消える
     */
    private void rulerDeath(float d) {
        if (d > 20) {
            rotZ("hand_minute", (d - 20) * 0.55F);
            rotZ("hand_hour", (d - 20) * 0.12F);
        }
        if (d > 30 && d < 45) scale("halo_top", ((int) d % 4 < 2) ? 1.0F : 0.4F);
        float kneel = smooth(d, 60, 100);
        blend("leg_right", 1.2F, 0, 0.1F, kneel);
        blend("leg_left", 1.0F, 0, -0.1F, kneel);
        blend("shin_right", -1.9F, 0, 0, kneel);
        blend("shin_left", -1.7F, 0, 0, kneel);
        CoreGeoBone body = bone("body");
        if (body != null) body.setRotX(body.getRotX() + kneel * 0.45F);
        blend("head", 0.6F, 0, 0, kneel);
        float swell = smooth(d, 100, 118);
        float shatter = smooth(d, 120, 124);
        scale("dial", Math.max(0.001F, (1.0F + swell * 0.3F) * (1.0F - shatter)));
        if (d > 145) scale("root", Math.max(0.001F, 1.0F - smooth(d, 145, 160)));
    }

    private void rotX(String name, float v) {
        CoreGeoBone b = bone(name);
        if (b != null) b.setRotX(v);
    }

    private void rotY(String name, float v) {
        CoreGeoBone b = bone(name);
        if (b != null) b.setRotY(v);
    }

    private void rotZ(String name, float v) {
        CoreGeoBone b = bone(name);
        if (b != null) b.setRotZ(v);
    }

    private CoreGeoBone bone(String name) {
        return getAnimationProcessor().getBone(name);
    }
}
