package com.automatavalkyries.entity.akashic;

import com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage;
import com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.TechniqueKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 「カスタムダメージ辞書」および「カスタム即死辞書」の設定データモデル（2026-10-25 ユーザー指定:
 * 「カスタムで設定できるダメージ辞書と即死辞書アイテムのも追加してプレイヤーもアカシックちゃんも使える」）。
 * <p>
 * アイテムの NBT（{@code "AkashicLexicon"}）および {@link AkashicResistanceAuditor} の内部に保持され、
 * 36 種のダメージ技術または 72 種の即死技術の個別 ON/OFF・ティア一括切替・ダメージ倍率・連撃回数・範囲・執行順序を管理する。
 * </p>
 */
public final class AkashicCustomLexicon {

    public static final String NBT_KEY = "AkashicLexicon";

    /** 技術の執行順序モード */
    public enum ExecutionOrder {
        /** 昇順: 低ティア（軽い技術）から順番に段階執行する（耐性測定の基本） */
        ASCENDING("昇順 (T1→高Tier)"),
        /** 降順: 最高ティア（最も凶悪な技術）から逆順に執行する */
        DESCENDING("降順 (高Tier→T1)"),
        /** 一斉収束: 有効化されているすべての技術を 1 回の発動で同時多重執行する */
        SIMULTANEOUS_BURST("一斉収束 (全技術同時発動)");

        public final String label;

        ExecutionOrder(String label) {
            this.label = label;
        }

        public ExecutionOrder next() {
            ExecutionOrder[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }

    private final TechniqueKind kind;
    private final Set<String> enabledCodes = new LinkedHashSet<>();
    private float damageMultiplier = 1.0F;
    private int repeatCount = 1;
    private float areaRadius = 0.0F;
    private ExecutionOrder executionOrder = ExecutionOrder.ASCENDING;
    private boolean stopOnPenetrate = true;

    public AkashicCustomLexicon(TechniqueKind kind) {
        this.kind = kind;
        enableAll();
    }

    public TechniqueKind kind() {
        return kind;
    }

    public AuditStage[] availableStages() {
        return kind == TechniqueKind.DAMAGE
                ? AkashicResistanceAuditor.damageOnlyStages()
                : AkashicResistanceAuditor.instantKillOnlyStages();
    }

    public void enableAll() {
        enabledCodes.clear();
        for (AuditStage stage : availableStages()) {
            enabledCodes.add(stage.code);
        }
    }

    public void disableAll() {
        enabledCodes.clear();
    }

    public void invertSelection() {
        for (AuditStage stage : availableStages()) {
            if (enabledCodes.contains(stage.code)) {
                enabledCodes.remove(stage.code);
            } else {
                enabledCodes.add(stage.code);
            }
        }
    }

    public void setTierEnabled(int tier, boolean enabled) {
        for (AuditStage stage : availableStages()) {
            if (stage.tier == tier) {
                if (enabled) enabledCodes.add(stage.code);
                else enabledCodes.remove(stage.code);
            }
        }
    }

    public boolean isTierAllEnabled(int tier) {
        boolean foundAny = false;
        for (AuditStage stage : availableStages()) {
            if (stage.tier == tier) {
                foundAny = true;
                if (!enabledCodes.contains(stage.code)) return false;
            }
        }
        return foundAny;
    }

    public void toggleTier(int tier) {
        setTierEnabled(tier, !isTierAllEnabled(tier));
    }

    public boolean isEnabled(AuditStage stage) {
        return stage != null && enabledCodes.contains(stage.code);
    }

    public void setEnabled(AuditStage stage, boolean enabled) {
        if (stage == null || stage.kind != this.kind) return;
        if (enabled) enabledCodes.add(stage.code);
        else enabledCodes.remove(stage.code);
    }

    public void toggleStage(AuditStage stage) {
        if (stage == null || stage.kind != this.kind) return;
        setEnabled(stage, !isEnabled(stage));
    }

    public int enabledCount() {
        return enabledCodes.size();
    }

    public int totalAvailableCount() {
        return availableStages().length;
    }

    /**
     * 現在の ON/OFF 設定と {@link ExecutionOrder}（昇順・降順）に従った有効ステージ配列を返す。
     * 万一 0 件の場合はフォールバックとして先頭ステージ 1 件を返す。
     */
    public List<AuditStage> resolveOrderedStages() {
        List<AuditStage> list = new ArrayList<>();
        for (AuditStage stage : availableStages()) {
            if (enabledCodes.contains(stage.code)) {
                list.add(stage);
            }
        }
        if (list.isEmpty()) {
            AuditStage[] all = availableStages();
            if (all.length > 0) list.add(all[0]);
        }
        if (executionOrder == ExecutionOrder.DESCENDING) {
            Collections.reverse(list);
        }
        return list;
    }

    public float damageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(float damageMultiplier) {
        if (!Float.isFinite(damageMultiplier) || damageMultiplier <= 0.0F) {
            this.damageMultiplier = 1.0F;
        } else {
            this.damageMultiplier = Math.min(1.0E12F, damageMultiplier);
        }
    }

    public void cycleDamageMultiplier() {
        float[] presets = {0.5F, 1.0F, 2.0F, 5.0F, 10.0F, 100.0F, 10000.0F, 1.0E9F};
        for (int i = 0; i < presets.length; i++) {
            if (Math.abs(damageMultiplier - presets[i]) < 0.01F * presets[i]) {
                damageMultiplier = presets[(i + 1) % presets.length];
                return;
            }
        }
        damageMultiplier = 1.0F;
    }

    public int repeatCount() {
        return repeatCount;
    }

    public void setRepeatCount(int repeatCount) {
        this.repeatCount = Math.max(1, Math.min(10, repeatCount));
    }

    public void cycleRepeatCount() {
        int[] presets = {1, 2, 3, 5, 10};
        for (int i = 0; i < presets.length; i++) {
            if (repeatCount == presets[i]) {
                repeatCount = presets[(i + 1) % presets.length];
                return;
            }
        }
        repeatCount = 1;
    }

    public float areaRadius() {
        return areaRadius;
    }

    public void setAreaRadius(float areaRadius) {
        if (!Float.isFinite(areaRadius) || areaRadius < 0.0F) {
            this.areaRadius = 0.0F;
        } else {
            this.areaRadius = Math.min(64.0F, areaRadius);
        }
    }

    public void cycleAreaRadius() {
        float[] presets = {0.0F, 4.0F, 8.0F, 16.0F, 32.0F};
        for (int i = 0; i < presets.length; i++) {
            if (Math.abs(areaRadius - presets[i]) < 0.1F) {
                areaRadius = presets[(i + 1) % presets.length];
                return;
            }
        }
        areaRadius = 0.0F;
    }

    public ExecutionOrder executionOrder() {
        return executionOrder;
    }

    public void setExecutionOrder(ExecutionOrder executionOrder) {
        this.executionOrder = executionOrder != null ? executionOrder : ExecutionOrder.ASCENDING;
    }

    public void cycleExecutionOrder() {
        this.executionOrder = this.executionOrder.next();
    }

    public boolean stopOnPenetrate() {
        return stopOnPenetrate;
    }

    public void setStopOnPenetrate(boolean stopOnPenetrate) {
        this.stopOnPenetrate = stopOnPenetrate;
    }

    public AkashicCustomLexicon copy() {
        AkashicCustomLexicon c = new AkashicCustomLexicon(this.kind);
        c.enabledCodes.clear();
        c.enabledCodes.addAll(this.enabledCodes);
        c.damageMultiplier = this.damageMultiplier;
        c.repeatCount = this.repeatCount;
        c.areaRadius = this.areaRadius;
        c.executionOrder = this.executionOrder;
        c.stopOnPenetrate = this.stopOnPenetrate;
        return c;
    }

    // ---- NBT シリアライズ --------------------------------------------------

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", kind.name());
        tag.putFloat("DamageMultiplier", damageMultiplier);
        tag.putInt("RepeatCount", repeatCount);
        tag.putFloat("AreaRadius", areaRadius);
        tag.putString("ExecutionOrder", executionOrder.name());
        tag.putBoolean("StopOnPenetrate", stopOnPenetrate);

        ListTag list = new ListTag();
        for (String code : enabledCodes) {
            list.add(StringTag.valueOf(code));
        }
        tag.put("EnabledCodes", list);
        return tag;
    }

    public static AkashicCustomLexicon fromTag(TechniqueKind expectedKind, @Nullable CompoundTag tag) {
        AkashicCustomLexicon lexicon = new AkashicCustomLexicon(expectedKind);
        if (tag == null || tag.isEmpty()) {
            return lexicon;
        }
        if (tag.contains("DamageMultiplier", Tag.TAG_ANY_NUMERIC)) {
            lexicon.setDamageMultiplier(tag.getFloat("DamageMultiplier"));
        }
        if (tag.contains("RepeatCount", Tag.TAG_ANY_NUMERIC)) {
            lexicon.setRepeatCount(tag.getInt("RepeatCount"));
        }
        if (tag.contains("AreaRadius", Tag.TAG_ANY_NUMERIC)) {
            lexicon.setAreaRadius(tag.getFloat("AreaRadius"));
        }
        if (tag.contains("ExecutionOrder", Tag.TAG_STRING)) {
            try {
                lexicon.setExecutionOrder(ExecutionOrder.valueOf(tag.getString("ExecutionOrder")));
            } catch (IllegalArgumentException ignored) {}
        }
        if (tag.contains("StopOnPenetrate", Tag.TAG_BYTE)) {
            lexicon.setStopOnPenetrate(tag.getBoolean("StopOnPenetrate"));
        }
        if (tag.contains("EnabledCodes", Tag.TAG_LIST)) {
            lexicon.enabledCodes.clear();
            ListTag list = tag.getList("EnabledCodes", Tag.TAG_STRING);
            Set<String> validCodes = new LinkedHashSet<>();
            for (AuditStage s : lexicon.availableStages()) validCodes.add(s.code);
            for (int i = 0; i < list.size(); i++) {
                String code = list.getString(i);
                if (validCodes.contains(code)) {
                    lexicon.enabledCodes.add(code);
                }
            }
        }
        return lexicon;
    }

    public static AkashicCustomLexicon fromStack(ItemStack stack, TechniqueKind expectedKind) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return new AkashicCustomLexicon(expectedKind);
        }
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(NBT_KEY, Tag.TAG_COMPOUND)) {
            return new AkashicCustomLexicon(expectedKind);
        }
        return fromTag(expectedKind, root.getCompound(NBT_KEY));
    }

    public void saveToStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        stack.getOrCreateTag().put(NBT_KEY, toTag());
    }

    public String summaryText() {
        StringBuilder sb = new StringBuilder();
        sb.append(kind == TechniqueKind.DAMAGE ? "ダメージ辞書" : "即死辞書");
        sb.append(" [有効 ").append(enabledCount()).append("/").append(totalAvailableCount()).append("]");
        sb.append(" 順序=").append(executionOrder.label);
        if (kind == TechniqueKind.DAMAGE) {
            sb.append(" 倍率=x").append(damageMultiplier >= 1e6F ? String.format(Locale.ROOT, "%.0e", damageMultiplier) : String.format(Locale.ROOT, "%.1f", damageMultiplier));
        }
        if (repeatCount > 1) {
            sb.append(" 連撃=").append(repeatCount);
        }
        if (areaRadius > 0.0F) {
            sb.append(" 範囲=").append(String.format(Locale.ROOT, "%.0fm", areaRadius));
        }
        return sb.toString();
    }
}
