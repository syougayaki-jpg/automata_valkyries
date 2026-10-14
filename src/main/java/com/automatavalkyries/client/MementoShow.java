package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.MementoMoriEntity;
import net.minecraft.util.Mth;

/**
 * メメントモリの演出の時間割（2026-10-14 ユーザー指定「見た目は十分なので次に演出にうつる」。質問で: 登場は蝶の群れが集まって形になる・
 * 第 2 形態は時間をかけた昇華・技は構えの魔法陣と予兆・浮く装甲が技に加わる・光の軌跡と粒・音・撃破は光の蝶になって散る）。
 * モデル（{@link ChronoGazerModel}）・レンダラー（{@link MementoMoriRenderer}）・蝶の群れ（{@link MementoSwarm}）が同じ時間で動くよう、ここで決める。
 * <ul>
 *   <li>登場（{@link MementoMoriEntity#INTRO_TICKS} = 80）: 0〜45 で光の蝶が渦を巻いて集まり、30〜55 で体が光として現れて実体になる。光輪は 20〜50 で 1 層ずつ組み上がり、
 *       浮く装甲は 45〜68 で遠くから 1 枚ずつ集まる。翅は 40〜75 で開き、45 から舞い上がる</li>
 *   <li>昇華（{@link MementoMoriEntity#AWAKEN_TICKS} = 100）: 0〜60 で胸の核に光が集まり、20〜55 で浮く装甲が外へ弾かれる。30〜75 は空への光の柱。
 *       60 の白い閃光で翅が光体の翅に替わり、光体の光が一気に満ちて 80 までに落ち着く。装甲は 60〜80 で戻る</li>
 *   <li>撃破（{@link MementoMoriEntity#DEFEAT_TICKS} = 90）: 0〜45 は光が乱れてまたたき、浮く装甲が 1 枚ずつ光を失って落ちる。30〜45 で光輪が広がって割れる。
 *       45〜85 で体が光になってほどけ（光体の光が強まって体が透けていく）、光の蝶になって空へ舞い上がる</li>
 *   <li>退場（{@link MementoMoriEntity#RETREAT_TICKS} = 50）: 舞い上がりながら 10〜45 で透けて、光の蝶になって散る</li>
 * </ul>
 */
final class MementoShow {

    private MementoShow() {
    }

    static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** 今の段階の経過 Tick（その段階でなければ -1） */
    static float stageT(MementoMoriEntity mori, byte stage, float pt) {
        return mori.stage() == stage && !mori.isDefeatedSynced() ? mori.tickCount + pt - mori.stageStart : -1.0F;
    }

    /** 撃破の経過 Tick（撃破でなければ -1） */
    static float defeatT(MementoMoriEntity mori, float pt) {
        return mori.isDefeatedSynced() ? mori.defeatTicks + pt : -1.0F;
    }

    /** 体の不透明度（登場で現れ、撃破・退場で透けて消える） */
    static float bodyAlpha(MementoMoriEntity mori, float pt) {
        float in = stageT(mori, MementoMoriEntity.STAGE_INTRO, pt);
        if (in >= 0.0F) return smooth((in - 30.0F) / 25.0F);
        float re = stageT(mori, MementoMoriEntity.STAGE_RETREAT, pt);
        if (re >= 0.0F) return 1.0F - smooth((re - 10.0F) / 35.0F);
        float d = defeatT(mori, pt);
        if (d >= 0.0F) return 1.0F - smooth((d - 45.0F) / 35.0F);
        return 1.0F;
    }

    /**
     * 体に重ねる光（光体の絵の明るさ。第 2 形態のふだんの脈打つ光は除く）: 登場では体が光として現れ、昇華では核の充填から閃光、
     * 撃破では乱れてまたたいてから光になってほどけ、退場では透けながら光る
     */
    static float showLight(MementoMoriEntity mori, float pt) {
        float age = mori.tickCount + pt;
        float in = stageT(mori, MementoMoriEntity.STAGE_INTRO, pt);
        if (in >= 0.0F) return 1.3F * smooth((in - 22.0F) / 15.0F) * (1.0F - smooth((in - 52.0F) / 20.0F));
        float aw = stageT(mori, MementoMoriEntity.STAGE_AWAKEN, pt);
        if (aw >= 0.0F) return 0.35F * smooth(aw / 60.0F) * (0.8F + 0.2F * Mth.sin(age * 0.9F)) + (aw >= 60.0F ? 1.6F * (1.0F - smooth((aw - 60.0F) / 20.0F)) : 0.0F);
        float re = stageT(mori, MementoMoriEntity.STAGE_RETREAT, pt);
        if (re >= 0.0F) return 0.9F * smooth(re / 12.0F) * (1.0F - smooth((re - 35.0F) / 12.0F));
        float d = defeatT(mori, pt);
        if (d >= 0.0F) {
            float flicker = d < 45.0F ? 0.35F + 0.35F * Mth.sin(d * 1.7F) * Mth.sin(d * 0.53F + 1.0F) : 0.0F;  // 光が乱れてまたたく
            return Math.max(0.0F, flicker) + 1.2F * smooth((d - 40.0F) / 15.0F) * (1.0F - smooth((d - 70.0F) / 15.0F));
        }
        return 0.0F;
    }

    /** 第 2 形態のふだんの光体の光の強さ（昇華では閃光から満ちる。撃破・退場では消えていく） */
    static float radiantPower(MementoMoriEntity mori, float pt) {
        if (!mori.isPhase2()) return 0.0F;
        float aw = stageT(mori, MementoMoriEntity.STAGE_AWAKEN, pt);
        if (aw >= 0.0F) return smooth((aw - 60.0F) / 10.0F);
        float re = stageT(mori, MementoMoriEntity.STAGE_RETREAT, pt);
        if (re >= 0.0F) return 1.0F - smooth((re - 10.0F) / 35.0F);
        float d = defeatT(mori, pt);
        if (d >= 0.0F) return 1.0F - smooth(d / 45.0F);
        return 1.0F;
    }

    /** 光体の翅に替わっているか（昇華の閃光から） */
    static boolean radiantWings(MementoMoriEntity mori, float pt) {
        if (!mori.isPhase2()) return false;
        float aw = stageT(mori, MementoMoriEntity.STAGE_AWAKEN, pt);
        return aw < 0.0F || aw >= 60.0F;
    }

    /** 光輪の層 layer（0 = 外・1 = 六芒星・2 = 内）の大きさ: 登場で外から 1 層ずつ組み上がり、撃破で広がって割れる */
    static float haloLayer(MementoMoriEntity mori, float pt, int layer) {
        float in = stageT(mori, MementoMoriEntity.STAGE_INTRO, pt);
        if (in >= 0.0F) {
            float u = (in - 20.0F - layer * 8.0F) / 14.0F;
            return u <= 0.0F ? 0.0F : u >= 1.0F ? 1.0F : smooth(u) * (1.0F + 0.25F * Mth.sin(u * Mth.PI));
        }
        float d = defeatT(mori, pt);
        if (d >= 0.0F) return d < 45.0F ? 1.0F + 0.6F * smooth((d - 30.0F) / 15.0F) : 0.0F;
        return 1.0F;
    }

    /**
     * 浮く装甲 i（全 n 枚）の登場の遠さ（px。遠くから 1 枚ずつ集まる。登場でなければ 0）
     */
    static float armorGather(MementoMoriEntity mori, float pt, int i, int n) {
        float in = stageT(mori, MementoMoriEntity.STAGE_INTRO, pt);
        if (in < 0.0F) return 0.0F;
        float start = 45.0F + 15.0F * i / Math.max(1, n - 1);
        return 14.0F * (1.0F - smooth((in - start) / 8.0F));
    }

    /** 浮く装甲の昇華での弾き（px。20〜55 で外へ、60〜80 で戻る） */
    static float armorBurst(MementoMoriEntity mori, float pt) {
        float aw = stageT(mori, MementoMoriEntity.STAGE_AWAKEN, pt);
        if (aw < 0.0F) return 0.0F;
        return 6.0F * smooth((aw - 20.0F) / 12.0F) * (1.0F - smooth((aw - 60.0F) / 20.0F));
    }

    /** 浮く装甲 i（全 n 枚）の撃破での落ち（0〜1。1 枚ずつ光を失って落ちる） */
    static float armorFall(MementoMoriEntity mori, float pt, int i, int n) {
        float d = defeatT(mori, pt);
        if (d < 0.0F) return 0.0F;
        float start = 3.0F + 35.0F * i / Math.max(1, n - 1);
        return Mth.clamp((d - start) / 14.0F, 0.0F, 1.0F);
    }
}
