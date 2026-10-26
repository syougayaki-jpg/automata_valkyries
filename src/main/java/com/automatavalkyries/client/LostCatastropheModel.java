package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.zero.LostCatastropheEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * 零号機【マリスライズ・ロストカタストロフィー】のモデル（geo / テクスチャは tools/gen_lost_catastrophe.py。2026-10-20）。アニメーションのファイルは持たず、ここで骨を動かす:
 * 浮かんだまま上下に漂い、右の背の刃の翼と左の背の瓦礫の翼はゆっくりはばたき、頭上の赤い光輪は回り、両肩の上の赤と紫の光輪は揺れ、
 * 髪・スカート・鎖は揺れる（2026-10-20 ゲームの中の絵に合わせて作り直し）。
 * ふだんはゲームの中の絵の構え（右腕を横へ差し出して手の先に大きな黒い渦、左腕は下げる。2026-10-20 ユーザー指定「絵に合わせる」）。
 * 渦（骨 vortex・vortex_in）は回り、両脇の大剣は漂う。
 * かわいさ（2026-10-20 ユーザー指定。docs/RESEARCH_CUTE_MODELS.md の DokiMobs・YSM の調べから「頭身と体型」と「待機の仕草」だけ）:
 * 体（首から下・翼・スカート）は OBJ の面のまま骨に載せる（2026-10-21 ユーザー指定「オススメのやりかたで全部かえてみて」。tools/lc_objmesh.py・
 * {@link ObjBoneMesh}）。同日「顔を同じように変換して」で頭（顔・髪・冠・下ろした面頬）と頭上の光輪も OBJ の面にした。
 * 下の目・まばたき・視線・面頬・口の動きは、箱の頭（tools/gen_lost_catastrophe.py の FACE_MESH = False）のときだけ見える（今は空の骨を動かすだけ）。
 * OBJ の腕は少し外へ開き、左脚は少し前へ出た形なので、腕の Z と脚の X の回しはその分だけ小さい。
 * 目（2026-10-21 ユーザー指定「イエススティーブモデルのかわいい要素を全力で参考に」「普段は面頬を上げて目を見せる」、同日「トキやエレン・ジョーを参考に」
 * 「目が独立してアニメーションのある可愛さの秘訣」。docs/RESEARCH_CUTE_MODELS.md §7）: 4.5〜6.75 秒ごとのまばたき（白目と虹彩の層をつぶし、
 * 立体のまつげを下ろす。眉も少し下がる）、ふだんはジト目、技の間は見開いて眉を寄せ口を開く（ギザ歯）、動けない間は半目と困り眉、
 * 約 23 秒に 1 度ウインク、視線は虹彩をバネで遅れて追わせ、まつげ・眉は半分だけずらす（層ごとの視差）、ハイライトは時々きらめく。
 * 面頬はふだん額の上へ上げてゴーグルのように載せ、詠唱・特異点・大剣・時間停止の間は目の前へ下ろす。アホ毛は揺れる。
 * 待機の仕草は、呼吸（胸がゆっくり膨らむ）・重心の移動（腰が左右へゆっくり揺れ、体が少し傾く）・時々の首かしげ・
 * 近くのプレイヤーを目で追う（頭だけ ±55 度・上下 ±30 度まで、なめらかに）
 * 技ごとの構え（{@link LostCatastropheEntity#animTick}）、詠唱の間は両腕を広げて天を仰ぎ、
 * 動けない間はうなだれて震え、出現は体を丸めた所から開き、撃破は膝をついてうなだれる。
 * 2026-10-26 演出の改良: 出現は 70 まで丸まり 100 で腕と翼を広げて構え、光輪は 70〜80 で灯る。形態変化は一瞬丸まってから広げ、撤退は自分を抱いて丸まる。
 * 撃破では光輪が 36〜40 で膨らみ、40 で砕けて消える。
 * 腕・翼の Z の回転は右が正で外へ（メメントモリと同じ）
 */
public class LostCatastropheModel extends DefaultedEntityGeoModel<LostCatastropheEntity> {
    /** 頭上の光輪の傾き（後ろを少し上げる。tools/lc_objmesh.py の HALO_TILT。光輪は平らな輪で作ってある。2026-10-21 ユーザー指定「光輪は修正して頭の上に」） */
    static final float HALO_TILT_X = -0.25F, HALO_TILT_Z = 0.0F;


    public LostCatastropheModel() {
        super(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "lost_catastrophe"), false);
    }

    /** 構えの強さ: 技を出した時刻から rise で 1 へ上がり、hold の間保って、fall で 0 へ戻る */
    static float pose(float since, float rise, float hold, float fall) {
        if (since < 0 || since > rise + hold + fall) return 0.0F;
        if (since < rise) return smooth(since / rise);
        if (since < rise + hold) return 1.0F;
        return 1.0F - smooth((since - rise - hold) / fall);
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3 - 2 * x);
    }

    @Override
    public void setCustomAnimations(LostCatastropheEntity e, long instanceId, AnimationState<LostCatastropheEntity> state) {
        float pt = state.getPartialTick();
        float age = e.tickCount + pt;
        float pitch = Mth.lerp(pt, e.xRotO, e.getXRot()) * Mth.DEG_TO_RAD;
        float move = Math.min(state.getLimbSwingAmount(), 1.0F);

        float slash = pose(age - e.animTick[0], 4, 10, 10), beam = pose(age - e.animTick[1], 8, 24, 10), spiral = pose(age - e.animTick[2], 5, 25, 10);
        float blade = pose(age - e.animTick[3], 10, 60, 14), rain = pose(age - e.animTick[4], 6, 50, 12), hole = pose(age - e.animTick[5], 6, 80, 14);
        float hack = pose(age - e.animTick[6], 4, 16, 10), stop = pose(age - e.animTick[7], 6, 26, 10);
        float chant = e.chantLeft() > 0 || e.finaleTicks() > 0 ? 1.0F : 0.0F;              // 終幕も詠唱と同じく両腕を広げて天を仰ぐ（2026-10-26）
        float stun = e.stunLeft() > 0 ? 1.0F : 0.0F;
        // 出現（2026-10-26 演出の改良）: 組み上がる間（〜70）は丸まり、70〜100 でほどけ、100 で腕を広げて構える（flare）
        float sinceIntro = age - e.introTick;
        float intro = sinceIntro > LostCatastropheEntity.INTRO_TICKS ? 0.0F : 1.0F - smooth((sinceIntro - 70) / 30.0F);
        float flare = pose(sinceIntro - 100, 3, 14, 16);
        // 形態変化: 一瞬丸まってから、怨嗟を噴き出すように広げる
        float sincePhase = age - e.phaseChangedTick;
        float curl = pose(sincePhase, 4, 8, 6);
        flare = Math.max(flare, pose(sincePhase - 12, 3, 16, 14));
        // 撤退: 腕で自分を抱いて丸まり、崩れていく
        float retreat = pose(age - e.animTick[LostCatastropheEntity.EV_RETREAT - 90], 20, 80, 1);
        intro = Math.max(intro, Math.max(curl, retreat));
        float defeat = e.deathTime > 0 ? smooth((e.deathTime + pt - 40) / 60.0F) : 0.0F;
        float calm = 1.0F - Math.max(stun, defeat);

        // 体: 漂う。出現・撃破では丸まる・うなだれる
        float shift = Mth.sin(age * 0.026F + e.getId()) * calm;                    // 重心の移動（左右へゆっくり）
        CoreGeoBone body = bone("body");
        if (body != null) {
            body.setPosY(Mth.sin(age * 0.06F) * 0.6F * calm - 2.0F * defeat);
            body.setPosX(shift * 0.35F);
            body.setRotX(-0.12F * move - 0.25F * slash + 0.35F * intro + 0.3F * defeat + 0.15F * stun - 0.12F * chant - 0.15F * flare);
            body.setRotZ(shift * 0.045F + (stun > 0 ? Mth.sin(age * 3.1F) * 0.03F : 0.0F));
        }
        CoreGeoBone chest = bone("chest");
        if (chest != null) {
            chest.setRotX(0.2F * intro + 0.15F * defeat - 0.1F * chant - 0.08F * beam);
            chest.setRotZ(-shift * 0.03F);
            float breath = Mth.sin(age * (Mth.TWO_PI / 60.0F)) * calm;               // 呼吸（3 秒で 1 回、胸が少し膨らむ）
            chest.setScaleX(1.0F + 0.008F * breath);
            chest.setScaleY(1.0F + 0.015F * breath);
            chest.setScaleZ(1.0F + 0.012F * breath);
        }
        CoreGeoBone head = bone("head");
        if (head != null) {
            head.setScaleX(LostCatastropheEntity.HEAD_SCALE);                    // 頭の大きさ（OBJ の体に合わせて少し小さく）
            head.setScaleY(LostCatastropheEntity.HEAD_SCALE);
            head.setScaleZ(LostCatastropheEntity.HEAD_SCALE);
            look(e, pt);
            float tilt = tiltPose(e, age) * calm;                                // 時々の首かしげ
            // 上下はおさえる（OBJ の頭は首の付け根で回るので、大きく下を向くとあごが襟に入る。2026-10-21 ユーザー指定「あごのあたりが貫通している」）
            head.setRotX((e.lookPitch * Mth.DEG_TO_RAD) * 0.5F * calm + 0.3F * defeat + 0.2F * stun - 0.3F * chant + 0.2F * intro - 0.25F * flare);
            head.setRotY((e.lookYaw * Mth.DEG_TO_RAD + Mth.sin(age * 0.03F) * 0.05F) * calm);
            head.setRotZ(0.15F * stun * Mth.sin(age * 0.4F) + 0.12F * defeat + tilt);
        }
        // 目（2026-10-21 YSM のかわいいモデルの目の作り。docs/RESEARCH_CUTE_MODELS.md §7）: 白目と虹彩の層（eye_）を縦につぶし、同時に立体のまつげ（lash_）を
        // 下ろしてまばたき。ふだんは少しつぶしたジト目。視線は虹彩（iris_）をバネで遅れて追わせ、まつげ・眉は半分だけずらす（層ごとの視差）。面頬は上げ下げ
        float visorWant = Math.max(chant, Math.max(hole, Math.max(stop, blade)));
        e.visorDown += (visorWant - e.visorDown) * 0.15F;
        CoreGeoBone visor = bone("visor");
        if (visor != null) visor.setPosY(3.0F * (1.0F - e.visorDown));
        float angry = Math.max(slash, Math.max(beam, Math.max(spiral, Math.max(hack, rain))));
        float cycle = 90 + (e.getId() & 3) * 15;                                       // 4.5〜6.75 秒ごと
        float bt = (age + e.getId() * 17) % cycle;
        float blink = bt < 1.7F ? smooth(bt / 1.7F) : bt < 4.2F ? 1.0F : 1.0F - smooth((bt - 4.2F) / 1.8F);   // 0.08 秒で閉じ、0.13 秒閉じたまま、0.3 秒で開き終わる
        float lid = 0.24F * (1.0F - Math.max(angry, chant)) + 0.5F * stun;             // ふだんはジト目、技の間は見開く、動けない間は半分閉じる
        float wt = (age + e.getId() * 31) % 460;                                        // 約 23 秒に 1 度、左目でウインク
        float wink = angry == 0 && calm > 0.99F ? pose(wt - 400, 3, 10, 4) : 0.0F;
        springIris(e, age, -Mth.clamp(e.lookYaw / 55.0F, -1.0F, 1.0F) * 0.14F, -Mth.clamp(e.lookPitch / 30.0F, -1.0F, 1.0F) * 0.1F);
        float tw = (age + e.getId() * 7) % 70;                                          // ハイライトが時々きらめく
        float twinkle = tw < 6 ? Mth.sin(tw / 6 * Mth.PI) : 0.0F;
        for (String side : new String[]{"r", "l"}) {
            float s = side.equals("r") ? 1.0F : -1.0F;
            float c = Math.max(Math.max(lid, blink), Math.max(defeat, side.equals("l") ? wink : 0.0F));
            CoreGeoBone eye = bone("eye_" + side);
            if (eye != null) eye.setScaleY(Math.max(0.03F, 1.0F - 0.97F * c));        // 縦の大きさ 0 は面の向きが壊れるので少し残す
            CoreGeoBone lash = bone("lash_" + side);
            if (lash != null) {
                lash.setPosY(-1.1F * c);
                lash.setPosX(e.irisX * 0.5F);
            }
            CoreGeoBone iris = bone("iris_" + side);
            if (iris != null) {
                iris.setPosX(e.irisX);
                iris.setPosY(e.irisY);
            }
            CoreGeoBone hl = bone("hl_" + side);
            if (hl != null) {
                hl.setScaleX(1.0F + 0.35F * twinkle);
                hl.setScaleY(1.0F + 0.35F * twinkle);
            }
            CoreGeoBone brow = bone("brow_" + side);
            if (brow != null) {
                brow.setPosY(-0.35F * blink - 0.1F * lid + 0.15F * Math.max(chant, stun));
                brow.setPosX(e.irisX * 0.35F);
                brow.setRotZ(s * (0.3F * angry - 0.15F * stun));                          // 技の間は眉を寄せる（目頭の側を下げる）、動けない間は困り眉
            }
        }
        boolean open = angry > 0.3F || chant > 0 || stun > 0;                         // 口: 技・詠唱・動けない間は開いた口（ギザ歯）、出現・撃破は笑み
        boolean smile = !open && (intro > 0.2F || defeat > 0.2F);
        hide(bone("mouth_o"), !open);
        hide(bone("mouth_s"), !smile);
        hide(bone("mouth_n"), open || smile);
        CoreGeoBone ahoge = bone("ahoge");
        if (ahoge != null) {
            ahoge.setRotZ(Mth.sin(age * 0.13F) * 0.15F);
            ahoge.setRotX(-0.3F * move + Mth.sin(age * 0.09F + 1.0F) * 0.08F);
        }
        CoreGeoBone hairBack = bone("hair_back");
        if (hairBack != null) hairBack.setRotX(-0.08F - Mth.sin(age * 0.11F) * 0.06F - 0.2F * move);

        // 腕。右は横へ差し出して手の先に黒い渦（ゲームの中の絵。構えでは突き出す・振り上げる）、左は下げる
        for (String side : new String[]{"r", "l"}) {
            boolean right = side.equals("r");
            float s = right ? 1.0F : -1.0F;
            float idle = Mth.sin(age * 0.07F + (right ? 0.0F : 1.3F)) * 0.05F;
            float rx, rz, ry = 0.0F, fx;
            if (right) {
                rx = 0.06F + idle;                                          // 渦を外したので左と同じく下げる（2026-10-21 ユーザー指定「剣も渦もなくして」）
                rz = 0.1F;                                                  // OBJ の腕は初めから外へ約 18 度開いている
                fx = 0.18F;
                rz -= 1.0F * Math.max(blade, rain);
                rx += 0.75F * hole + 0.6F * hack + 0.55F * beam - 0.3F * spiral;
                rz += 0.2F * spiral;
                fx -= 0.15F * (hole + hack + beam);
                rx += 2.3F * blade + 1.0F * rain;                           // 大剣・データの雨は天へ振り上げる
                fx -= 0.5F * blade;
                rx += -0.6F * slash;
                rz += 0.9F * slash;
            } else {
                rx = 0.06F + idle;
                rz = 0.1F;                                                  // OBJ の腕は初めから外へ開いているので少しだけ
                fx = 0.18F;
                rx += 1.6F * slash - 0.2F * spiral;                          // 光速の斬撃は左の大剣で前へ薙ぐ
                ry = -0.6F * slash;
                rz += 0.5F * spiral + 0.6F * stop;
                rx += 1.4F * stop;
            }
            rx = rx * (1.0F - chant) + chant * (0.4F + Mth.sin(age * 0.1F) * 0.05F);   // 詠唱: 両腕を広げて天を仰ぐ
            rz = rz * (1.0F - chant) + chant * 1.5F;
            fx *= 1.0F - chant;
            rx = rx * (1.0F - Math.max(stun, defeat)) + Math.max(stun, defeat) * 0.15F;  // 動けない・撃破: だらりと下げる
            rz = rz * (1.0F - Math.max(stun, defeat)) + Math.max(stun, defeat) * 0.12F;
            rx = rx * (1.0F - intro) + intro * 1.2F;                         // 出現: 胸の前で抱えた所から
            rz = rz * (1.0F - intro) - intro * 0.4F;
            rx = rx * (1.0F - flare) + flare * 0.5F;                         // 広げて構える（出現の終わり・形態変化）
            rz += 1.1F * flare;
            CoreGeoBone arm = bone("arm_" + side);
            if (arm != null) {
                arm.setRotX(rx);
                arm.setRotZ(s * rz);
                arm.setRotY(s * ry);
            }
            CoreGeoBone fore = bone("forearm_" + side);
            if (fore != null) fore.setRotX(Math.max(0.0F, fx + 0.9F * intro));
        }

        // 翼: 右の刃の翼と左の瓦礫の翼をはばたかせる。詠唱・特異点・時間停止では少し広げ、撃破ではたたむ（向きがはっきりしないので動きは小さく）
        float spread = 0.06F + 0.2F * chant + 0.15F * hole + 0.12F * stop + 0.1F * slash - 0.2F * intro - 0.2F * defeat + 0.25F * flare;
        for (String side : new String[]{"r", "l"}) {
            boolean right = side.equals("r");
            float s = right ? 1.0F : -1.0F;
            float flap = Mth.sin(age * (0.12F + 0.1F * chant) + (right ? 0.0F : 0.9F)) * (right ? 0.08F : 0.14F) * calm;
            CoreGeoBone w = bone("wing_" + side);
            if (w != null) {
                w.setRotY(s * (spread + flap));
                w.setRotZ(s * (0.1F + flap * 0.6F + 0.2F * chant));
                w.setRotX(0.1F * slash + 0.2F * defeat);
            }
        }

        // 光輪: それぞれの向きに回る（詠唱の間は速く、動けない間は止まる）。撃破では崩れて傾く
        float spin = (1.0F - stun) * (1.0F - defeat) * (1.0F + 3.0F * chant + 1.5F * stop + 4.0F * flare);
        // 光輪の大きさ（2026-10-26 演出の改良）: 出現の 70〜80 で灯って広がり、撃破の 36〜40 で膨らんで 40 で砕ける（破片はサーバーの粒子）
        float haloSize = sinceIntro >= 0 && sinceIntro < 80 ? smooth((sinceIntro - 70) / 10.0F) : 1.0F;
        if (e.deathTime > 0) haloSize = e.deathTime + pt < 40 ? 1.0F + 0.3F * smooth((e.deathTime + pt - 36) / 4.0F) : 0.0F;
        haloSize *= 1.0F + 0.15F * flare;
        CoreGeoBone top = bone("halo_top"), topSpin = bone("halo_spin");          // 頭上の光輪: 傾きは halo_top、回すのは子の halo_spin（傾いたまま自分の軸で回る）
        if (top != null) {
            top.setRotX(HALO_TILT_X + 0.4F * defeat);
            top.setRotZ(HALO_TILT_Z);
            top.setPosY(Mth.sin(age * 0.09F) * 0.3F - 3.0F * defeat);
            top.setHidden(haloSize <= 0.01F);
            float hs = Math.max(0.01F, haloSize);
            top.setScaleX(hs);
            top.setScaleY(hs);
            top.setScaleZ(hs);
        }
        if (topSpin != null) topSpin.setRotY(age * 0.03F * spin);
        hide(topSpin, haloSize <= 0.01F);                                          // 光輪の面は子の halo_spin にある（親を隠しても子は描かれる）
        hide(bone("halo_r"), haloSize <= 0.01F);
        hide(bone("halo_l"), haloSize <= 0.01F);
        CoreGeoBone hr = bone("halo_r");
        if (hr != null) {
            hr.setRotZ(Mth.sin(age * 0.03F) * 0.12F * spin);                     // 傾いた輪なので回さず揺らす
            hr.setPosY(Mth.sin(age * 0.07F + 1.0F) * 0.4F - 4.0F * defeat);
            hr.setRotX(0.6F * defeat);
        }
        CoreGeoBone hl = bone("halo_l");
        if (hl != null) {
            hl.setRotZ(-Mth.sin(age * 0.03F + 1.4F) * 0.12F * spin);
            hl.setPosY(Mth.sin(age * 0.07F + 2.5F) * 0.4F - 4.0F * defeat);
            hl.setRotX(-0.6F * defeat);
        }

        // スカート・後ろ垂れ・鎖・剣: 揺れる。進むと後ろへなびく
        float sway = Mth.sin(age * 0.09F) * 0.05F;
        rotX("skirt_f", -0.04F - sway - 0.15F * move);
        CoreGeoBone skirt = bone("skirt_b");                                     // 長いスカートは面でつないだ 1 枚（2026-10-21）なので骨 1 つで揺らす
        if (skirt != null) {
            skirt.setRotX(0.02F + sway * 0.5F + 0.15F * move);
            skirt.setRotZ(Mth.sin(age * 0.07F + 0.8F) * 0.025F);
        }
        rotX("tails", 0.12F + Mth.sin(age * 0.08F + 0.6F) * 0.1F + 0.4F * move);
        rotX("chain_r", Mth.sin(age * 0.1F) * 0.12F + 0.2F * move);
        rotX("chain_l", Mth.sin(age * 0.1F + 1.6F) * 0.12F + 0.2F * move);
        rotZ("sword_r", 0.05F + Mth.sin(age * 0.05F) * 0.03F);                     // 両脇に浮かぶ大剣（ゆっくり上下に漂う）
        rotZ("sword_l", -0.05F - Mth.sin(age * 0.05F + 1.0F) * 0.03F);
        CoreGeoBone sr = bone("sword_r"), sl = bone("sword_l");
        if (sr != null) sr.setPosY(Mth.sin(age * 0.07F) * 0.5F);
        if (sl != null) sl.setPosY(Mth.sin(age * 0.07F + 2.0F) * 0.5F);
        CoreGeoBone vortex = bone("vortex"), vortexIn = bone("vortex_in");     // 右手の黒い渦（外は遅く、内は速く回る。詠唱・特異点ではさらに速く）
        float vs = 1.0F + 2.0F * chant + 1.5F * hole;
        if (vortex != null) vortex.setRotZ(-age * 0.06F * vs);
        if (vortexIn != null) vortexIn.setRotZ(-age * 0.1F * vs);

        // 脚: 浮かんでいるのでつま先を下げてそろえ、少しずらして揺らす。撃破では膝をつく
        float kneel = defeat;
        CoreGeoBone legR = bone("leg_r"), legL = bone("leg_l");
        if (legR != null) legR.setRotZ(-shift * 0.04F);                               // 重心の移動に合わせて脚も少し
        if (legL != null) legL.setRotZ(-shift * 0.04F);
        if (legR != null) legR.setRotX(0.03F + Mth.sin(age * 0.06F) * 0.03F + 0.2F * move + 1.3F * kneel + 0.5F * intro);   // OBJ の左脚は初めから少し前
        if (legL != null) legL.setRotX(-0.02F + Mth.sin(age * 0.06F + 1.2F) * 0.03F + 0.1F * move + 0.1F * kneel + 0.6F * intro);
        rotX("shin_r", -(0.05F + 1.4F * kneel + 0.4F * intro));
        rotX("shin_l", -(0.05F + 1.8F * kneel + 0.5F * intro));
    }

    /** 近くのプレイヤーを頭で追う（体の向きとの差。±55 度・上下 ±30 度まで、なめらかに。いなければ前へ戻す） */
    static void look(LostCatastropheEntity e, float pt) {
        float yaw = 0.0F, pitch = 0.0F;
        net.minecraft.world.entity.player.Player p = e.isDefeated() ? null : e.level().getNearestPlayer(e, 20.0);
        if (p != null) {
            double eyeY = e.getY() + 2.3;
            double dx = p.getX() - e.getX(), dz = p.getZ() - e.getZ(), dy = p.getEyeY() - eyeY;
            float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            float body = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
            yaw = Mth.clamp(Mth.wrapDegrees(want - body), -55.0F, 55.0F);
            pitch = Mth.clamp((float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG), -30.0F, 30.0F);
        }
        e.lookYaw += (yaw - e.lookYaw) * 0.08F;
        e.lookPitch += (pitch - e.lookPitch) * 0.08F;
    }

    /** 首かしげ: 約 10 秒ごとに 2 秒ほど、左右交互に首をかしげる */
    static float tiltPose(LostCatastropheEntity e, float age) {
        float cycle = 200.0F;
        float t = (age + e.getId() * 37) % cycle;
        int n = (int) ((age + e.getId() * 37) / cycle);
        return pose(t - 140.0F, 10.0F, 26.0F, 14.0F) * 0.22F * (n % 2 == 0 ? 1.0F : -1.0F);
    }

    private CoreGeoBone bone(String name) {
        return getAnimationProcessor().getBone(name);
    }

    /** 虹彩の視線のバネ（YSM の ysm.second_order と同じ考え。少し遅れて追い、わずかに行き過ぎる。Tick で積分するので描く速さによらない） */
    static void springIris(LostCatastropheEntity e, float age, float tx, float ty) {
        float dt = Mth.clamp(age - e.eyeAge, 0.0F, 4.0F);
        e.eyeAge = age;
        while (dt > 0) {
            float h = Math.min(dt, 1.0F);
            e.irisVX += ((tx - e.irisX) * 0.3F - e.irisVX * 0.45F) * h;
            e.irisVY += ((ty - e.irisY) * 0.3F - e.irisVY * 0.45F) * h;
            e.irisX += e.irisVX * h;
            e.irisY += e.irisVY * h;
            dt -= h;
        }
    }

    private static void hide(CoreGeoBone b, boolean hidden) {
        if (b != null) b.setHidden(hidden);
    }

    private void rotX(String name, float v) {
        CoreGeoBone b = bone(name);
        if (b != null) b.setRotX(v);
    }

    private void rotZ(String name, float v) {
        CoreGeoBone b = bone(name);
        if (b != null) b.setRotZ(v);
    }
}
