package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.IsolationDamageEngine;
import com.automatavalkyries.entity.akashic.AkashicAgentCanary;
import com.automatavalkyries.entity.akashic.AkashicNonEntityProxy;
import com.automatavalkyries.entity.akashic.AkashicProbeEntity;
import com.automatavalkyries.entity.akashic.AkashicRecord;
import com.automatavalkyries.entity.akashic.AkashicShadowCanary;
import com.automatavalkyries.entity.akashic.AkashicSurvey;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.mixin.SynchedEntityDataAccessor;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.util.UnsafeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** 事象観測体アカシック・プローブ（docs/SPEC_AKASHIC_PROBE.md） */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class AkashicProbeGameTests {

    private static final String TEMPLATE = "empty";

    /**
     * 他 MOD からの消去・die の直接の呼び出し・被弾を、MOD・操作・結果ごとに記録し、倒れず・消えず・HP も戻り、頭上の行ができる
     */
    @GameTest(template = TEMPLATE, batch = "akashic", timeoutTicks = 60)
    public static void akashicProbeObservesAndStays(GameTestHelper helper) {
        var probe = helper.spawnWithNoFreeWill(ModEntities.AKASHIC_PROBE.get(), new BlockPos(2, 2, 2));
        CallerAttribution.overrideForTest("com.automatavalkyries.gametest.fake", "fakekiller");
        try {
            com.automatavalkyries.gametest.fake.FakeEntityEraser.forceDrop(probe); // 倒せなかった相手からのドロップ（断られる）
            com.automatavalkyries.gametest.fake.FakeEntityEraser.discardAndDie(probe);
        } finally {
            CallerAttribution.overrideForTest(null, null);
        }
        probe.hurt(helper.getLevel().damageSources().generic(), 30.0F);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!probe.isRemoved() && probe.isAlive(), "the probe was removed or died: " + probe);
            helper.assertTrue(probe.getHealth() >= probe.getMaxHealth(), "the probe did not go back to full health: " + probe.getHealth());
            var keys = AkashicRecord.keysForTest(probe.getUUID());
            // 偽 MOD の名前はクラスごとに覚えられる（先に回った試験の名前のこともある）ので、他 MOD として記録されたかで見る
            helper.assertTrue(foreignRow(keys, "discard"), "a foreign discard was not observed: " + keys);
            helper.assertTrue(foreignRow(keys, "die"), "a foreign die() was not observed: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("damage:minecraft:generic")), "the hit was not observed: " + keys);
            helper.assertTrue(!probe.hologramLines().isEmpty(), "the probe has no hologram lines");
            probe.getEprotect().triggerDespawn();
            helper.succeed();
        });
    }

    /** 他 MOD の名前で、その操作を断った記録があるか */
    private static boolean foreignRow(java.util.Set<String> keys, String op) {
        for (String key : keys) {
            int bar = key.indexOf('|');
            if (bar > 0 && key.endsWith("|" + op + "|REFUSED") && CallerAttribution.isForeign(key.substring(0, bar))) return true;
        }
        return false;
    }

    /** 下調べが jar のクラスを読める（Forge の jar で見る。結果の中身は環境で変わるので見ない）。クラスの定数プールも読める */
    @GameTest(template = TEMPLATE, batch = "akashic", timeoutTicks = 400)
    public static void akashicSurveyReadsAModJar(GameTestHelper helper) {
        CompletableFuture<AkashicSurvey.Report> future = AkashicSurvey.request("forge");
        helper.succeedWhen(() -> {
            helper.assertTrue(future.isDone(), "the survey is still running");
            AkashicSurvey.Report report = future.getNow(null);
            helper.assertTrue(report != null && report.ok() && report.scannedClasses() > 0, "the survey of forge failed: " + report);
        });
    }

    /**
     * フメツウィザー・テッセラクトビースト・フラッシュファー型のシャドウステート、
     * ダミー真ライフ（動的ゼロプロキシ置換を含む）、マッドネス型非エンティティプロキシ、
     * および -0.0F / NaN ダメージや AABB 潰しをすべて個別に感知・自己修復することを確認する
     */
    @GameTest(template = TEMPLATE, batch = "akashic", timeoutTicks = 60)
    public static void akashicShadowStatesTrueLifeAndNonEntityProxy(GameTestHelper helper) {
        var probe = helper.spawnWithNoFreeWill(ModEntities.AKASHIC_PROBE.get(), new BlockPos(2, 2, 2));

        // 1. フメツウィザー・テッセラクト・フラッシュファーのシャドウステート破壊
        probe.immortalShield = false;
        probe.tesseractBarrier = false;
        probe.flashfurShadowHealth = 0.0F;
        probe.getPersistentData().putFloat("omnimobs_health_data", -999.0F);
        probe.getPersistentData().putBoolean("titandeathtag", true);

        // 2. 削除拒否リスト (ProtectedCanaryList) への通常削除試行と Unsafe 直接縮小
        CallerAttribution.overrideForTest("com.automatavalkyries.gametest.fake", "fakekiller");
        try {
            probe.protectedCanaryList.clear();
            probe.nonEntityProxy().dispose();
        } finally {
            CallerAttribution.overrideForTest(null, null);
        }
        UnsafeHelper.removeFromArrayList(probe.protectedCanaryList, x -> true);

        // 3. ダミー真ライフの動的ゼロプロキシ置換（SuperSteve 対策の逆探知）
        probe.trueLifeHealth = 0.0F;
        probe.trueLifeOperator = (AkashicShadowCanary.TrueLifeOperator) UnsafeHelper.createZeroProxy(
                AkashicShadowCanary.TrueLifeOperator.class);

        // 4. 非エンティティプロキシのメモリゼロ化と静的コレクションからのパージ
        probe.nonEntityProxy().controllerHealth = 0.0F;
        probe.nonEntityProxy().disposed = true;
        AkashicNonEntityProxy.ACTIVE_CONTROLLER_LIST.remove(probe.nonEntityProxy());

        // 5. IEEE-754 特殊ビット (-0.0F / NaN) ダメージと AABB フィールド直接ゼロ化
        probe.hurt(helper.getLevel().damageSources().generic(), -0.0F);
        probe.hurt(helper.getLevel().damageSources().generic(), Float.NaN);
        ((EntityAccessor) probe).automata_valkyries$setBoundingBoxField(new AABB(0, 0, 0, 0, 0, 0));

        helper.runAfterDelay(3, () -> {
            var keys = AkashicRecord.keysForTest(probe.getUUID());
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("shadow:immortal_wither_state_broken|REPAIRED")),
                    "Immortal Wither shadow break was not observed: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("shadow:tesseract_beast_state_broken|REPAIRED")),
                    "Tesseract Beast shadow break was not observed: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("shadow:flashfur_health_broken|REPAIRED")),
                    "Flashfur shadow break was not observed: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("shadow:protected_list:clear|REFUSED")),
                    "ProtectedCanaryList.clear() was not intercepted: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("shadow:protected_list:unsafe_eviction|REPAIRED")),
                    "ProtectedCanaryList Unsafe eviction was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("shadow_nbt:flashfur_omnimobs_tampered|REPAIRED")),
                    "Omni-Mobs decoy NBT tamper was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("shadow_nbt:titan_death_tag_injected|REPAIRED")),
                    "Titan death tag injection was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("truelife:zero_proxy_injected|REPAIRED")),
                    "TrueLife zero-proxy replacement was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("non_entity_proxy:call:dispose|NEUTRALIZED")),
                    "Non-entity proxy reflective dispose() was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("non_entity_proxy:memory_tampered|REPAIRED")),
                    "Non-entity proxy memory tamper was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("non_entity_proxy:static_collection_purged|REPAIRED")),
                    "Non-entity proxy static collection purge was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("damage_bit:negative_zero(-0.0F)|DETECTED")),
                    "-0.0F bit damage was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("damage_bit:nan|DETECTED")),
                    "NaN damage was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("spatial_tamper:aabb_corrupted|REPAIRED")),
                    "AABB corruption was not detected: " + keys);

            // すべて自己修復されていること
            helper.assertTrue(probe.immortalShield && probe.tesseractBarrier && probe.flashfurShadowHealth == 100.0F,
                    "Shadow fields were not restored");
            helper.assertTrue(probe.trueLifeOperator instanceof AkashicShadowCanary.TrueLifeCanaryImpl
                    && probe.trueLifeOperator.getTrueHealth() == 100.0F, "TrueLife operator was not restored");
            helper.assertTrue(AkashicNonEntityProxy.ACTIVE_CONTROLLER_LIST.contains(probe.nonEntityProxy()),
                    "Non-entity proxy was not re-registered in static list");

            AkashicNonEntityProxy.unregister(probe.getUUID());
            probe.getEprotect().triggerDespawn();
            helper.succeed();
        });
    }

    /**
     * マニアック即死技術（Klass Word 豚化すり替え、SynchedEntityData DataItem 定数ロック、EntityType.factory null 化、クライアント幽霊ミュート）、
     * ダメージ計算式の隙間（二枚舌カメレオン DamageSource、カウンター自爆誘導、負の耐性 Amplifier 逆増幅、lastHurt 負値、負の ARMOR 増幅）、
     * および対 Java エージェント仕掛け（おとり監視スレッドの無血冷凍・割り込み・優先度低下）を網羅検証する
     */
    @GameTest(template = TEMPLATE, batch = "akashic", timeoutTicks = 60)
    public static void akashicInstantKillDamageExploitsAndAntiAgentCanaries(GameTestHelper helper) {
        var probe = helper.spawnWithNoFreeWill(ModEntities.AKASHIC_PROBE.get(), new BlockPos(2, 2, 2));
        Pig dummyPig = EntityType.PIG.create(helper.getLevel());

        // 1. オブジェクトヘッダ（Klass Word / 8L）の「豚化」すり替え即死カナリア＆即時復元
        if (dummyPig != null) {
            boolean restoredKlass = AkashicAgentCanary.testKlassWordCanaryTripAndRestore(probe, dummyPig);
            helper.assertTrue(restoredKlass && probe.getClass() == AkashicProbeEntity.class,
                    "Klass Word canary failed to detect or restore AkashicProbeEntity header");
        }

        // 2. SynchedEntityData.itemsById の DataItem 乗っ取り（LockedZeroDataItem 定数 0 ロック）
        var healthId = LivingEntityAccessor.automata_valkyries$getDataHealthId();
        var itemsById = ((SynchedEntityDataAccessor) probe.getEntityData()).automata_valkyries$getItemsById();
        itemsById.put(healthId.getId(), new IsolationDamageEngine.LockedZeroDataItem(healthId));

        // 3. EntityType.factory の null 化（「スポーン根絶」攻撃）と即時検問修復（同バッチの他テストの初期スポーンを妨げないよう即検査）
        UnsafeHelper.setFieldValue(ModEntities.AKASHIC_PROBE.get(), "factory", null);
        AkashicAgentCanary.inspectAndRepairEntityTypeFactory(probe.record(), "fakekiller");

        // 4. クライアント幽霊抹消（強制 DYING ポーズ・透明化・消音）
        probe.setPose(Pose.DYING);
        probe.setInvisible(true);
        probe.setSilent(true);

        // 5. ダメージパイプライン悪用（二枚舌カメレオン Holder + オウンゴール誘導 DamageSource + 負の耐性 Amplifier + 負の lastHurt + 負の ARMOR）
        AkashicShadowCanary.setLastHurt(probe, -Float.MAX_VALUE);
        AkashicShadowCanary.setAbsorptionRaw(probe, -500.0F);
        probe.getActiveEffectsMap().put(
                MobEffects.DAMAGE_RESISTANCE,
                new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, -5000, false, false));
        var armorAttr = probe.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.addTransientModifier(new AttributeModifier(UUID.randomUUID(), "neg_armor_poison", -100.0D, AttributeModifier.Operation.ADDITION));
        }
        var chameleonSource = IsolationDamageEngine.createIsolationDamageSource(probe, probe, helper.getLevel().damageSources().generic());
        probe.hurt(chameleonSource, 10.0F);

        // 6. 対 Java エージェント: おとり監視スレッド（Akashic-Watchdog-Canary）への無血冷凍・優先度引き下げ・割り込み攻撃
        Thread watchdog = AkashicAgentCanary.canaryWatchdogThreadForTest();
        if (watchdog != null) {
            watchdog.setPriority(Thread.MIN_PRIORITY);
            watchdog.interrupt();
        }
        AkashicAgentCanary.canaryNextCheckMsForTest().set(Long.MAX_VALUE / 2);

        helper.runAfterDelay(3, () -> {
            var keys = AkashicRecord.keysForTest(probe.getUUID());
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("jvm_header:klass_word_tampered|REPAIRED")),
                    "Klass Word tampering was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("synched_data:data_item_hijacked|REPAIRED")),
                    "SynchedEntityData DataItem hijack was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("registry_tamper:entity_type_factory_hijacked|REPAIRED")),
                    "EntityType.factory hijack was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("visual_tamper:forced_pose:DYING|REPAIRED")),
                    "Forced DYING pose was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("visual_tamper:forced_invisible|REPAIRED")),
                    "Forced invisibility was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("damage_exploit:chameleon_type_holder|DETECTED")),
                    "Chameleon DamageType Holder was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("damage_exploit:self_target_spoof|DETECTED")),
                    "Own-goal self-target DamageSource was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("damage_exploit:negative_last_hurt|REPAIRED")),
                    "Negative lastHurt overflow was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("damage_exploit:absorption_underflow|REPAIRED")),
                    "Absorption underflow was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("damage_exploit:negative_resistance_amplifier|REPAIRED")),
                    "Negative resistance amplifier was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("attr_tamper:negative_armor_amplification|REPAIRED")),
                    "Negative armor amplification was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("agent_canary:watchdog_priority_lowered|REPAIRED")),
                    "Watchdog priority lowering was not detected: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("agent_canary:watchdog_cryo_frozen|REPAIRED")),
                    "Watchdog bloodless cryo-freeze was not detected: " + keys);

            // すべて正常に復元されていること
            helper.assertTrue(ModEntities.AKASHIC_PROBE.get().create(helper.getLevel()) != null,
                    "AKASHIC_PROBE EntityType.factory was not restored");
            helper.assertTrue(probe.getPose() == Pose.STANDING && !probe.isInvisible() && !probe.isSilent(),
                    "Visual mute states were not restored");
            helper.assertTrue(itemsById.get(healthId.getId()).getClass() == net.minecraft.network.syncher.SynchedEntityData.DataItem.class,
                    "DATA_HEALTH_ID DataItem was not restored to vanilla DataItem");

            AkashicNonEntityProxy.unregister(probe.getUUID());
            probe.getEprotect().triggerDespawn();
            helper.succeed();
        });
    }

    /**
     * 超マイナーな深層干渉（Mark Word identityHashCode 破壊、RangedAttribute.maxValue 破壊、RemovalReason Enum 定数改ざん、
     * オートボクシングキャッシュ汚染、WeakReference 強制 enqueue、ThreadLocal カナリア破壊、MutableCallSite ターゲット乗っ取り、
     * Entity.random モック差し替え、トロイの木馬 Goal 注入、および Netty 抹消パケット検問）を網羅検証する
     */
    @GameTest(template = TEMPLATE, batch = "akashic", timeoutTicks = 60)
    public static void akashicUltraObscureInterferencesObservedAndRepaired(GameTestHelper helper) {
        var probe = helper.spawnWithNoFreeWill(ModEntities.AKASHIC_PROBE.get(), new BlockPos(2, 2, 2));

        // 1. Mark Word (0L) の identityHashCode 破壊カナリア＆即時復元
        int originalHash = System.identityHashCode(probe);
        boolean restoredMark = AkashicAgentCanary.testMarkWordCanaryTripAndRestore(probe);
        helper.assertTrue(restoredMark && System.identityHashCode(probe) == originalHash,
                "Mark Word identityHashCode canary failed to detect or restore");

        // 2. バニラ共有定数 Attributes.MAX_HEALTH (RangedAttribute) の maxValue 0.0D 破壊
        var maxHpField = UnsafeHelper.findField(net.minecraft.world.entity.ai.attributes.RangedAttribute.class, "maxValue", "f_22308_");
        if (maxHpField != null) {
            UnsafeHelper.setFieldValue(Attributes.MAX_HEALTH, maxHpField.getName(), 0.0D);
        }

        // 3. Entity.RemovalReason Enum 定数の内部 destroy フラグ改ざん（同期検問で即時復元）
        var destroyField = UnsafeHelper.findField(net.minecraft.world.entity.Entity.RemovalReason.class, "destroy", "f_146958_");
        if (destroyField != null) {
            try {
                UnsafeHelper.setFieldValue(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK, destroyField.getName(), true);
                AkashicAgentCanary.inspectAndRepairRemovalReasonEnum(probe.record(), "fakekiller");
            } finally {
                UnsafeHelper.setFieldValue(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK, destroyField.getName(), false);
            }
        }

        // 4. JVM オートボクシングキャッシュ（Integer.valueOf(77)）の内部値汚染（同期検問で即時復元）
        var intValField = UnsafeHelper.findField(Integer.class, "value");
        var unsafe = UnsafeHelper.getUnsafe();
        long intValOffset = UnsafeHelper.objectFieldOffset(intValField);
        if (unsafe != null && intValOffset >= 0) {
            Integer cached77 = Integer.valueOf(77);
            try {
                unsafe.putInt(cached77, intValOffset, -999);
                AkashicAgentCanary.inspectAndRepairAutoboxingCache(probe.record(), "fakekiller");
            } finally {
                unsafe.putInt(cached77, intValOffset, 77);
            }
        }

        // 5. おとり WeakReference の強制 clear() & enqueue()（疑似 GC 追放攻撃）
        probe.canaryWeakSelfRef.clear();
        probe.canaryWeakSelfRef.enqueue();
        probe.canaryWeakProtectionMap.clear();

        // 6. おとり ThreadLocal 破壊 ＆ おとり MutableCallSite ターゲット乗っ取り
        AkashicAgentCanary.AKASHIC_GUARD_THREAD_LOCAL.set(Boolean.FALSE);
        AkashicAgentCanary.CANARY_CALL_SITE.setTarget(java.lang.invoke.MethodHandles.constant(float.class, 0.0F));

        // 7. Entity.random の定数シードモックすり替え
        var randomField = UnsafeHelper.findField(net.minecraft.world.entity.Entity.class, "random", "f_19796_");
        if (randomField != null) {
            UnsafeHelper.setFieldValue(probe, randomField.getName(), net.minecraft.util.RandomSource.create(0L));
        }

        // 8. goalSelector への「トロイの木馬 Goal（自滅 AI）」注入
        probe.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.Goal() {
            @Override
            public boolean canUse() {
                return true;
            }
        });

        // 9. Netty アウトバウンド抹消パケット（ClientboundRemoveEntitiesPacket）検問遮断
        var removePkt = new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(probe.getId());
        boolean allowed = AkashicShadowCanary.filterOutboundPacket(probe, removePkt, "fakekiller");
        helper.assertTrue(!allowed && removePkt.getEntityIds().isEmpty(),
                "Outbound ClientboundRemoveEntitiesPacket for protected AkashicProbe was not intercepted");

        helper.runAfterDelay(3, () -> {
            var keys = AkashicRecord.keysForTest(probe.getUUID());
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("jvm_header:mark_word_hash_tampered|REPAIRED")),
                    "Mark Word hash tampering was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("attr_tamper:ranged_attribute_bounds_corrupted|REPAIRED")),
                    "RangedAttribute bounds corruption was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("enum_tamper:removal_reason_corrupted|REPAIRED")),
                    "RemovalReason enum corruption was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("jvm_cache:autoboxing_cache_poisoned|REPAIRED")),
                    "Autoboxing cache poisoning was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("gc_tamper:weak_reference_forced_enqueue|REPAIRED")),
                    "WeakReference forced enqueue was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("thread_local:canary_tampered|REPAIRED")),
                    "ThreadLocal canary tampering was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("agent_canary:callsite_target_hijacked|REPAIRED")),
                    "MutableCallSite target hijack was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("random_tamper:entity_random_hijacked|REPAIRED")),
                    "Entity.random hijack was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("ai_tamper:trojan_goal_injected|NEUTRALIZED")),
                    "Trojan Goal injection was not neutralized: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("network_tamper:remove_packet_intercepted|NEUTRALIZED")),
                    "Remove packet interception was not recorded: " + keys);

            // すべて正常値に復元されていること
            helper.assertTrue(((net.minecraft.world.entity.ai.attributes.RangedAttribute) Attributes.MAX_HEALTH).getMaxValue() >= 1024.0D,
                    "Attributes.MAX_HEALTH maxValue was not restored");
            helper.assertTrue(!net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK.shouldDestroy(),
                    "UNLOADED_TO_CHUNK shouldDestroy was not restored");
            helper.assertTrue(Integer.valueOf(77).intValue() == 77,
                    "Integer autoboxing cache for 77 was not restored");
            helper.assertTrue(probe.canaryWeakSelfRef != null && probe.canaryWeakSelfRef.get() == probe,
                    "WeakReference canary was not restored");
            helper.assertTrue(Boolean.TRUE.equals(AkashicAgentCanary.AKASHIC_GUARD_THREAD_LOCAL.get()),
                    "AKASHIC_GUARD_THREAD_LOCAL was not restored");
            helper.assertTrue(AkashicAgentCanary.CANARY_CALL_SITE.getTarget() == AkashicAgentCanary.ORIGINAL_CALL_SITE_TARGET,
                    "CANARY_CALL_SITE target was not restored");

            AkashicNonEntityProxy.unregister(probe.getUUID());
            probe.getEprotect().triggerDespawn();
            helper.succeed();
        });
    }

    /**
     * MOD界最強格の耐性検証:
     * 1. Unsafe による構造参照フィールド（goalSelector, combatTracker, protect 等）の強制 null 化（obliterateObjectMemory 攻撃）
     * 2. The King Sword 型の全参照抹消（FakeEntityEraser.forceRemove + wipeAllReferences: リフレクション 0 引数総当たり、
     *    UUID ゼロ化・ID -1 化、TickList / ChunkMap / VisibleEntityStorage / KnownUuids からの完全追放、NaN 座標飛ばし）
     * 3. 退避防止タグ（eprotect_no_retreat）剥奪、kill() / setHealth(0) / changeDimension() 直呼び
     * これらを同時に受けても NullPointerException を起こさず、1 Tick で元の UUID・ID・構造フィールド・ワールド全層へ無傷復帰することを確認する
     */
    @GameTest(template = TEMPLATE, batch = "akashic", timeoutTicks = 60)
    public static void akashicSurvivesTotalMemoryObliterationAndWorldWipe(GameTestHelper helper) {
        var probe = helper.spawnWithNoFreeWill(ModEntities.AKASHIC_PROBE.get(), new BlockPos(2, 2, 2));
        UUID originalUuid = probe.getUUID();
        int originalId = probe.getId();
        probe.nonEntityProxy().captureCoreFieldSnapshot(probe);

        CallerAttribution.overrideForTest("com.automatavalkyries.gametest.fake", "fakekiller");
        try {
            // 1. 直接 kill() / setHealth(0) / changeDimension() 呼び出しと退避防止タグ剥奪
            probe.getTags().clear();
            probe.kill();
            probe.setHealth(0.0F);
            probe.changeDimension(helper.getLevel());

            // 2. EprotectManager からの強制登録抹消 ＋ The King Sword 型 forceRemove + wipeAllReferences
            com.automatavalkyries.protect.EprotectManager.getInstance().unregister(probe.getEprotect());
            com.automatavalkyries.gametest.fake.FakeEntityEraser.forceRemove(probe);
            com.automatavalkyries.gametest.fake.FakeEntityEraser.wipeAllReferences(probe);

            // 3. Unsafe による必須構造フィールドの null 化（obliterateObjectMemory シミュレート）
            UnsafeHelper.setFieldValue(probe, "protect", null);
            var goalSelField = UnsafeHelper.findField(net.minecraft.world.entity.Mob.class, "goalSelector", "f_21345_");
            if (goalSelField != null) UnsafeHelper.setFieldValue(probe, goalSelField.getName(), null);
            var combatField = UnsafeHelper.findField(net.minecraft.world.entity.LivingEntity.class, "combatTracker", "f_20944_");
            if (combatField != null) UnsafeHelper.setFieldValue(probe, combatField.getName(), null);
        } finally {
            CallerAttribution.overrideForTest(null, null);
        }

        helper.runAfterDelay(3, () -> {
            var keys = AkashicRecord.keysForTest(originalUuid);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("direct_call:kill|REFUSED")),
                    "Direct kill() was not refused: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("direct_call:setHealth_zero|REFUSED")),
                    "Direct setHealth(0) was not refused: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("dimension_banish:changeDimension|REFUSED")),
                    "Direct changeDimension() was not refused: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("unsafe_obliteration:core_fields_restored|REPAIRED")),
                    "Unsafe core field obliteration was not restored: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("identity_tamper:uuid_or_id_corrupted|REPAIRED")),
                    "UUID/ID corruption was not restored: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("world_eviction:world_presence_broken|REPAIRED")),
                    "World presence wipe was not restored: " + keys);

            var currentEntity = helper.getLevel().getEntity(originalUuid);
            helper.assertTrue(currentEntity instanceof AkashicProbeEntity activeProbe
                            && activeProbe.isAlive()
                            && !activeProbe.isRemoved()
                            && activeProbe.getUUID().equals(originalUuid)
                            && activeProbe.getId() == originalId
                            && activeProbe.getHealth() >= activeProbe.getMaxHealth()
                            && activeProbe.goalSelector != null
                            && activeProbe.getCombatTracker() != null
                            && activeProbe.getTags().contains(com.automatavalkyries.protect.Eprotect.NO_RETREAT_TAG),
                    "AkashicProbe did not fully recover from memory obliteration and world wipe: " + currentEntity);

            AkashicNonEntityProxy.unregister(originalUuid);
            if (currentEntity instanceof AkashicProbeEntity activeProbe) {
                activeProbe.getEprotect().triggerDespawn();
            }
            helper.succeed();
        });
    }

    /**
     * 全 6 モード切替＆全 108 種（ダメージ系 36 種 + 即死系 72 種）の段階検証・直接対決（制限解除版 ＆ ペナルティなし最強版）の総合検証:
     * <ul>
     *   <li>技術カタログが全 108 種（ダメージ系 36 種 + 即死系 72 種）で構成されていること</li>
     *   <li>モード切替（{@code cycleMode}）が ①観測待機 → ②ダメージ系統オンリー → ③即死系統オンリー → ④全108種総合 → ⑤直接対決・制限解除 → ⑥直接対決・ペナルティなし最強版 の順で遷移すること</li>
     *   <li>モード②（{@code DAMAGE_ONLY_AUDIT}）ではダメージ系技術（{@code TechniqueKind.DAMAGE}）のみが執行され、アイアンゴーレム（HP 100）が T001 を {@code DAMAGED} で耐え、ダメージ系技術で貫通されること</li>
     *   <li>モード③（{@code INSTANT_KILL_ONLY_AUDIT}）では {@code hurt()} を一切使わず即死系技術（{@code TechniqueKind.INSTANT_KILL}）のみが執行され、アイアンゴーレムが初手 {@code T037_DIRECT_SET_HEALTH_ZERO} で即死貫通されること</li>
     *   <li>モード④（{@code RESISTANCE_AUDIT}）で保護付きダミーゾンビ（{@code PROTECT_DUMMY_ZOMBIE}）を検証すると、ダメージ系・中位フック系を耐え抜き、高段階（Tier 9〜10）で貫通されること</li>
     *   <li>モード⑥（{@code DIRECT_DUEL_NO_PENALTY}）では、アカシック自身が 200 ダメージを受けてもペナルティなしで HP 100 を維持しつつ、保護付きダミーゾンビすら「終焉・極」反撃で撃破すること</li>
     *   <li>モード⑤（{@code DIRECT_DUEL_UNCAPPED}）では、直接 {@code setHealth(0)} や {@code kill()} は拒否し続けつつ、正統な {@code hurt()} ダメージで実 HP が減少し、自律反撃と正統撃破が成立すること</li>
     * </ul>
     */
    @GameTest(template = TEMPLATE, batch = "akashic", timeoutTicks = 60)
    public static void akashicThreeModesAuditAndUncappedDuel(GameTestHelper helper) {
        helper.assertTrue(com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.allStages().length == 108,
                "Total stages must be 108");
        helper.assertTrue(com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.damageOnlyStages().length == 36,
                "Damage-only stages must be 36");
        helper.assertTrue(com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.instantKillOnlyStages().length == 72,
                "Instant-kill-only stages must be 72");

        var probe = helper.spawnWithNoFreeWill(ModEntities.AKASHIC_PROBE.get(), new BlockPos(2, 2, 2));
        UUID probeUuid = probe.getUUID();

        // 1. 初期状態は ① 観測待機（OBSERVE_STANDBY）
        helper.assertTrue(probe.mode() == AkashicProbeEntity.Mode.OBSERVE_STANDBY,
                "Initial mode should be OBSERVE_STANDBY");
        helper.assertTrue(probe.shouldKeepFullHealth(),
                "OBSERVE_STANDBY should keep full health");

        // 2. cycleMode 1回目: ② ダメージ系統オンリー検証（DAMAGE_ONLY_AUDIT）
        probe.cycleMode(null);
        helper.assertTrue(probe.mode() == AkashicProbeEntity.Mode.DAMAGE_ONLY_AUDIT,
                "1st cycleMode should switch to DAMAGE_ONLY_AUDIT");
        var golemDamage = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(3, 2, 2));
        var golemDamageStage = probe.auditor().runFullAuditImmediately(helper.getLevel(), golemDamage);
        helper.assertTrue(golemDamageStage != null
                        && golemDamageStage.kind == com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.TechniqueKind.DAMAGE
                        && golemDamageStage.number > 1,
                "Iron Golem (100 HP) in DAMAGE_ONLY_AUDIT should survive T001(20) and be penetrated by a DAMAGE stage, got: " + golemDamageStage);

        // 3. cycleMode 2回目: ③ 即死系統オンリー検証（INSTANT_KILL_ONLY_AUDIT）
        probe.cycleMode(null);
        helper.assertTrue(probe.mode() == AkashicProbeEntity.Mode.INSTANT_KILL_ONLY_AUDIT,
                "2nd cycleMode should switch to INSTANT_KILL_ONLY_AUDIT");
        var golemInstant = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(3, 2, 2));
        var golemInstantStage = probe.auditor().runFullAuditImmediately(helper.getLevel(), golemInstant);
        helper.assertTrue(golemInstantStage == com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T037_DIRECT_SET_HEALTH_ZERO,
                "Iron Golem in INSTANT_KILL_ONLY_AUDIT should be penetrated at first instant-kill stage T037, got: " + golemInstantStage);

        // 4. cycleMode 3回目: ④ 全108種・総合段階検証（RESISTANCE_AUDIT）
        probe.cycleMode(null);
        helper.assertTrue(probe.mode() == AkashicProbeEntity.Mode.RESISTANCE_AUDIT,
                "3rd cycleMode should switch to RESISTANCE_AUDIT");
        var immortalDummy = helper.spawnWithNoFreeWill(ModEntities.PROTECT_DUMMY_ZOMBIE.get(), new BlockPos(3, 2, 3));
        var dummyStage = probe.auditor().runFullAuditImmediately(helper.getLevel(), immortalDummy);
        helper.assertTrue(dummyStage != null && dummyStage.tier >= 6 && dummyStage.number >= 62,
                "ProtectDummyZombie should resist all 36 damage stages and T037..T061 instant-kill stages, and be penetrated at T062+, got: " + dummyStage);

        var auditKeys = AkashicRecord.keysForTest(probeUuid);
        helper.assertTrue(auditKeys.stream().anyMatch(k -> k.contains("audit:T001_normal_mob_hurt|DAMAGED")),
                "T001 DAMAGED on Iron Golem was not recorded: " + auditKeys);
        helper.assertTrue(auditKeys.stream().anyMatch(k -> k.contains("audit:T037_direct_set_health_zero|PENETRATED")),
                "T037 PENETRATED in INSTANT_KILL_ONLY_AUDIT was not recorded: " + auditKeys);
        helper.assertTrue(auditKeys.stream().anyMatch(k -> k.contains("audit:T037_direct_set_health_zero|RESISTED")),
                "T037 RESISTED on ProtectDummyZombie was not recorded: " + auditKeys);

        // 5. cycleMode 4回目 → 5回目で先に ⑥ 直接対決・ペナルティなし最強版（DIRECT_DUEL_NO_PENALTY）を検証
        probe.cycleMode(null); // ⑤ DIRECT_DUEL_UNCAPPED
        probe.cycleMode(null); // ⑥ DIRECT_DUEL_NO_PENALTY
        helper.assertTrue(probe.mode() == AkashicProbeEntity.Mode.DIRECT_DUEL_NO_PENALTY,
                "5th cycleMode should switch to DIRECT_DUEL_NO_PENALTY");
        helper.assertTrue(probe.shouldKeepFullHealth(),
                "DIRECT_DUEL_NO_PENALTY must keep full health without any penalty");

        var noPenaltyTarget = helper.spawnWithNoFreeWill(ModEntities.PROTECT_DUMMY_ZOMBIE.get(), new BlockPos(3, 2, 3));
        probe.invulnerableTime = 0;
        probe.hurt(helper.getLevel().damageSources().mobAttack(noPenaltyTarget), 200.0F);
        probe.baseTick();
        helper.assertTrue(probe.isAlive() && probe.getHealth() >= probe.getMaxHealth(),
                "DIRECT_DUEL_NO_PENALTY must keep Akashic at 100% HP even after 200 hurt damage");
        // 初撃（overdrive_barrage）→ 2撃目（omega_terminus）で保護付きダミーゾンビすら完全撃破できること
        probe.auditor().executeNoPenaltyFullSpecStrike(helper.getLevel(), noPenaltyTarget);
        if ( com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.isTargetStillPresent(noPenaltyTarget)) {
            probe.auditor().executeNoPenaltyFullSpecStrike(helper.getLevel(), noPenaltyTarget);
        }
        helper.assertTrue(!com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.isTargetStillPresent(noPenaltyTarget),
                "DIRECT_DUEL_NO_PENALTY must defeat even ProtectDummyZombie via Omega Terminus");

        // 6. ⑤ 直接対決・ダメージ制限解除（DIRECT_DUEL_UNCAPPED）へ切替して正統決闘を検証
        probe.setMode(AkashicProbeEntity.Mode.DIRECT_DUEL_UNCAPPED, null);
        helper.assertTrue(probe.mode() == AkashicProbeEntity.Mode.DIRECT_DUEL_UNCAPPED,
                "Mode should be DIRECT_DUEL_UNCAPPED");
        helper.assertTrue(!probe.shouldKeepFullHealth(),
                "DIRECT_DUEL_UNCAPPED should disable per-tick full-health lock");

        // 6a. モード⑤でも hurt() を通さない不正な直接 setHealth(0) や kill() は拒否されること
        probe.setHealth(0.0F);
        probe.kill();
        helper.assertTrue(probe.isAlive() && probe.getHealth() > 0.0F,
                "DIRECT_DUEL_UNCAPPED must still refuse direct setHealth(0) and kill() outside hurt()");

        // 6b. モード⑤では正統な hurt() ダメージが 10% 上限なしで通り、baseTick 後も勝手に 100 HP へ全回復しないこと
        var duelOpponent = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 3));
        probe.invulnerableTime = 0;
        probe.hurt(helper.getLevel().damageSources().mobAttack(duelOpponent), 45.0F);
        probe.baseTick();
        float hpAfterHit = probe.getHealth();
        helper.assertTrue(hpAfterHit <= 60.0F && hpAfterHit > 0.0F,
                "DIRECT_DUEL_UNCAPPED should take uncapped hurt() damage and keep reduced HP across ticks, got: " + hpAfterHit);

        // 6c. モード⑤で対戦相手へ自律反撃（executeDuelCounterStrike）を行い撃破できること
        probe.auditor().executeDuelCounterStrike(helper.getLevel(), duelOpponent);
        helper.assertTrue(!duelOpponent.isAlive() || duelOpponent.getHealth() < duelOpponent.getMaxHealth(),
                "Akashic should counterattack duel opponent in DIRECT_DUEL_UNCAPPED mode");

        // 6d. モード⑤で正統な hurt() により HP が 0 になった場合は正統撃破（die）が成立すること
        probe.invulnerableTime = 0;
        probe.hurt(helper.getLevel().damageSources().mobAttack(duelOpponent), 200.0F);
        var duelKeys = AkashicRecord.keysForTest(probeUuid);
        helper.assertTrue(!probe.isAlive() || probe.getEprotect().isDying(),
                "Akashic in DIRECT_DUEL_UNCAPPED should allow legitimate defeat when hurt() reduces HP to 0");
        helper.assertTrue(duelKeys.stream().anyMatch(k -> k.contains("duel:defeated_legitimately|DEFEATED")),
                "Legitimate duel defeat was not recorded: " + duelKeys);

        AkashicNonEntityProxy.unregister(probeUuid);
        helper.succeed();
    }

    /**
     * カスタムダメージ辞書（{@code terminus_damage_lexicon}）＆カスタム即死辞書（{@code terminus_instant_kill_lexicon}）の検証:
     * <ul>
     *   <li>36種のダメージ系技術・72種の即死系技術を個別ON/OFF・実行順（昇順/降順/一斉）・倍率・連撃数・範囲でカスタマイズし、ItemStack NBT へ保存・復元できること</li>
     *   <li>プレイヤー自身がカスタムダメージ辞書・カスタム即死辞書を使って直接対象（アイアンゴーレムや保護付きダミーゾンビ）に発動できること</li>
     *   <li>アカシックちゃん（{@link AkashicProbeEntity}）に両辞書を読み込ませると、{@code DAMAGE_ONLY_AUDIT} / {@code INSTANT_KILL_ONLY_AUDIT} / {@code RESISTANCE_AUDIT} ならびに直接対決モードでカスタム構成どおりの技リスト・倍率が執行されること</li>
     * </ul>
     */
    @GameTest(template = TEMPLATE, batch = "akashic", timeoutTicks = 60)
    public static void akashicCustomDamageAndInstantKillLexiconsPlayerAndProbe(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        var damageItem = (com.automatavalkyries.item.AkashicLexiconItem) com.automatavalkyries.registry.ModItems.TERMINUS_DAMAGE_LEXICON.get();
        var instantItem = (com.automatavalkyries.item.AkashicLexiconItem) com.automatavalkyries.registry.ModItems.TERMINUS_INSTANT_KILL_LEXICON.get();

        // 1. カスタムダメージ辞書を編集: T001 と T012 の2種のみ ON、降順（T012 → T001）、ダメージ倍率 x10.0
        var damageStack = new net.minecraft.world.item.ItemStack(damageItem);
        var damageLexicon = new com.automatavalkyries.entity.akashic.AkashicCustomLexicon(
                com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.TechniqueKind.DAMAGE);
        damageLexicon.disableAll();
        damageLexicon.setEnabled(com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T001_NORMAL_MOB_HURT, true);
        damageLexicon.setEnabled(com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T012_VOID_BYPASS_HURT, true);
        damageLexicon.setExecutionOrder(com.automatavalkyries.entity.akashic.AkashicCustomLexicon.ExecutionOrder.DESCENDING);
        damageLexicon.setDamageMultiplier(10.0F);
        damageLexicon.setStopOnPenetrate(true);
        damageLexicon.saveToStack(damageStack);

        var reloadedDamage = com.automatavalkyries.entity.akashic.AkashicCustomLexicon.fromStack(
                damageStack, com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.TechniqueKind.DAMAGE);
        var orderedDamageStages = reloadedDamage.resolveOrderedStages();
        helper.assertTrue(orderedDamageStages.size() == 2
                        && orderedDamageStages.get(0) == com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T012_VOID_BYPASS_HURT
                        && orderedDamageStages.get(1) == com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T001_NORMAL_MOB_HURT
                        && Math.abs(reloadedDamage.damageMultiplier() - 10.0F) < 0.001F,
                "Custom damage lexicon NBT roundtrip failed: " + orderedDamageStages);

        // 2. プレイヤーがカスタムダメージ辞書でアイアンゴーレム（HP 100）を直接攻撃 → 降順初手 T012（500 * 10 = 5000 ダメージ）で一撃貫通
        var golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(3, 2, 2));
        var playerDmgReport = damageItem.executeByPlayerOnTarget(helper.getLevel(), player, damageStack, golem);
        helper.assertTrue(playerDmgReport.penetrated()
                        && playerDmgReport.penetratedStage() == com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T012_VOID_BYPASS_HURT,
                "Player custom damage lexicon should penetrate Iron Golem at descending first stage T012, got: " + playerDmgReport);

        // 3. カスタム即死辞書を編集: T037 と T107（TerminusEngine.executeTerminus）の2種のみ ON、降順（T107 → T037）
        var instantStack = new net.minecraft.world.item.ItemStack(instantItem);
        var instantLexicon = new com.automatavalkyries.entity.akashic.AkashicCustomLexicon(
                com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.TechniqueKind.INSTANT_KILL);
        instantLexicon.disableAll();
        instantLexicon.setEnabled(com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T037_DIRECT_SET_HEALTH_ZERO, true);
        instantLexicon.setEnabled(com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T107_TERMINUS_ENGINE_SINGLE_EXECUTION, true);
        instantLexicon.setExecutionOrder(com.automatavalkyries.entity.akashic.AkashicCustomLexicon.ExecutionOrder.DESCENDING);
        instantLexicon.saveToStack(instantStack);

        // 4. プレイヤーがカスタム即死辞書で保護付きダミーゾンビを直接攻撃 → 降順初手 T107 で即座に貫通撃破
        var dummyForPlayer = helper.spawnWithNoFreeWill(ModEntities.PROTECT_DUMMY_ZOMBIE.get(), new BlockPos(3, 2, 3));
        var playerKillReport = instantItem.executeByPlayerOnTarget(helper.getLevel(), player, instantStack, dummyForPlayer);
        helper.assertTrue(playerKillReport.penetrated()
                        && playerKillReport.penetratedStage() == com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T107_TERMINUS_ENGINE_SINGLE_EXECUTION,
                "Player custom instant-kill lexicon should penetrate ProtectDummyZombie at T107, got: " + playerKillReport);

        // 5. アカシックちゃんにカスタムダメージ辞書＆カスタム即死辞書を読み込ませる
        var probe = helper.spawnWithNoFreeWill(ModEntities.AKASHIC_PROBE.get(), new BlockPos(2, 2, 2));
        UUID probeUuid = probe.getUUID();
        helper.assertTrue(probe.loadCustomLexiconFromStack(damageStack, player),
                "Probe should accept custom damage lexicon stack");
        helper.assertTrue(probe.loadCustomLexiconFromStack(instantStack, player),
                "Probe should accept custom instant-kill lexicon stack");

        // 5a. DAMAGE_ONLY_AUDIT モードでの有効ステージが [T012, T001] になっていること
        probe.setMode(AkashicProbeEntity.Mode.DAMAGE_ONLY_AUDIT, null);
        var probeDmgStages = probe.auditor().activeStagesForMode();
        helper.assertTrue(probeDmgStages.length == 2
                        && probeDmgStages[0] == com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T012_VOID_BYPASS_HURT,
                "Probe DAMAGE_ONLY_AUDIT stages did not match custom damage lexicon: " + java.util.Arrays.toString(probeDmgStages));

        // 5b. INSTANT_KILL_ONLY_AUDIT モードで保護付きダミーゾンビを検証すると、カスタム順の初手 T107 で即貫通されること
        probe.setMode(AkashicProbeEntity.Mode.INSTANT_KILL_ONLY_AUDIT, null);
        var dummyForProbe = helper.spawnWithNoFreeWill(ModEntities.PROTECT_DUMMY_ZOMBIE.get(), new BlockPos(2, 2, 3));
        var probeKillStage = probe.auditor().runFullAuditImmediately(helper.getLevel(), dummyForProbe);
        helper.assertTrue(probeKillStage == com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T107_TERMINUS_ENGINE_SINGLE_EXECUTION,
                "Probe with custom instant-kill lexicon should penetrate ProtectDummyZombie immediately at T107, got: " + probeKillStage);

        // 5c. RESISTANCE_AUDIT（総合モード）では両カスタム辞書が結合された計4ステージ（[T012, T001, T107, T037]）になること
        probe.setMode(AkashicProbeEntity.Mode.RESISTANCE_AUDIT, null);
        var combinedStages = probe.auditor().activeStagesForMode();
        helper.assertTrue(combinedStages.length == 4
                        && combinedStages[0] == com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T012_VOID_BYPASS_HURT
                        && combinedStages[2] == com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T107_TERMINUS_ENGINE_SINGLE_EXECUTION,
                "Probe RESISTANCE_AUDIT stages should combine both custom lexicons, got: " + java.util.Arrays.toString(combinedStages));

        AkashicNonEntityProxy.unregister(probeUuid);
        probe.getEprotect().triggerDespawn();
        helper.succeed();
    }

    /**
     * ティア 1（{@code T001_NORMAL_MOB_HURT}）ノーマルダメージ・7段階精密調査フローチャートの検証:
     * <ul>
     *   <li><b>過剰ダメージ拒否上限＋1発上限持ちモブ</b>（12.0 以上の打撃を 0 化し、1 発最大 9.5 にクランプする HP 60 のモブ）に対し、
     *       ティア 1 初手 {@code T001} 単体で上限を下降プローブ特定（{@code flowchart:ceiling_detected}）し、多段刻み連撃で一撃突破すること</li>
     *   <li><b>足切り閾値持ちモブ</b>（45.0 未満の打撃を 0 化するモブ）に対し、{@code T001}（初期値 20.0）が上昇二分プローブで
     *       足切り境界を特定（{@code flowchart:floor_detected}）し、ティア 1 単体で撃破すること</li>
     *   <li><b>攻撃者フィルタ＋物理無効モブ</b>（攻撃者が存在する打撃や物理打撃をすべて無効化するモブ）に対し、
     *       {@code T001} が攻撃者トポロジー（{@code ANONYMOUS_ENV} / {@code SELF_OWN_GOAL}）と 16 チャネルを UCB 探索して
     *       有効属性へ自動切替（{@code flowchart:topology_switched} / {@code flowchart:source_switched}）し、ティア 1 単体で撃破すること</li>
     *   <li><b>不死のトーテム＋耐性IV＋吸収＋1 Tick 後 HP 書き戻しモブ</b>に対し、事前検問でトーテム先消費（{@code flowchart:totem_pop_bait}）と
     *       バフ剥奪を行い、1 Tick 後の書き戻しを検知（{@code flowchart:revert_detected}）してマイクロ連撃を起動すること</li>
     * </ul>
     */
    @GameTest(template = TEMPLATE, batch = "akashic", timeoutTicks = 60)
    public static void akashicTier1NormalDamageFlowchartAdaptation(GameTestHelper helper) {
        var level = helper.getLevel();
        var probe = helper.spawnWithNoFreeWill(ModEntities.AKASHIC_PROBE.get(), new BlockPos(2, 2, 2));
        UUID probeUuid = probe.getUUID();
        BlockPos absPos = helper.absolutePos(new BlockPos(3, 2, 2));

        // 0. 汎用ダメージ逆算式（UniversalDamageFormula）の閉形式2次方程式逆関数 ＆ DPS テレメトリ精度検証
        for (double[] params : new double[][]{{20.0, 12.0, 20.0}, {28.0, 8.0, 5.0}, {10.0, 0.0, 50.0}}) {
            double armor = params[0];
            double toughness = params[1];
            float targetPost = (float) params[2];
            float solvedRaw = com.automatavalkyries.entity.akashic.UniversalDamageFormula.solveExactInverseArmorDamage(
                    targetPost, armor, toughness);
            float forwardCheck = com.automatavalkyries.entity.akashic.UniversalDamageFormula.predictPostArmorDamage(
                    solvedRaw, armor, toughness);
            helper.assertTrue(Math.abs(forwardCheck - targetPost) < 0.05F,
                    "UniversalDamageFormula inverse armor equation mismatch: target=" + targetPost
                            + " solvedRaw=" + solvedRaw + " forward=" + forwardCheck);
        }

        // 1. 過剰ダメージ拒否上限（>12.0 無効）＋1発上限（最大 9.5）モブ（HP 60）を T001 単体で多段刻み撃破
        var ceilingCappedMob = new net.minecraft.world.entity.monster.Husk(EntityType.HUSK, level) {
            @Override
            public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
                if (amount > 12.0F) {
                    return false; // 12 を超える大ダメージは完全拒否
                }
                return super.hurt(source, Math.min(amount, 9.5F));
            }
        };
        ceilingCappedMob.moveTo(absPos.getX() + 0.5, absPos.getY(), absPos.getZ() + 0.5, 0, 0);
        ceilingCappedMob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60.0D);
        ceilingCappedMob.setHealth(60.0F);
        level.addFreshEntity(ceilingCappedMob);

        boolean ceilingPenetrated = com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.executeStageStatic(
                level, probe, ceilingCappedMob,
                com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T001_NORMAL_MOB_HURT,
                1.0F, 1, probe.record());
        var ceilingProf = com.automatavalkyries.entity.akashic.AkashicDamageFlowchart.profileOf(ceilingCappedMob);
        helper.assertTrue(ceilingPenetrated && ceilingProf.ceilingConfirmed
                        && ceilingProf.dpsTelemetry.effectiveDealtDps(level.getGameTime()) > 0.0D
                        && ceilingProf.lastOptimalPlan != null,
                "T001 flowchart should detect over-damage ceiling, compute optimal DPS plan, and multi-slice defeat the 60 HP capped mob, profile="
                        + ceilingProf.diagnosticSummary() + " hp=" + ceilingCappedMob.getHealth());

        // 2. 足切り閾値（<45.0 無効）モブ（HP 40）を T001 単体で上昇プローブ突破
        var floorMob = new net.minecraft.world.entity.monster.Husk(EntityType.HUSK, level) {
            @Override
            public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
                if (amount < 45.0F) {
                    return true; // 受け付けるが 45 未満は足切りで 0 ダメージ
                }
                return super.hurt(source, amount);
            }
        };
        floorMob.moveTo(absPos.getX() + 0.5, absPos.getY(), absPos.getZ() + 0.5, 0, 0);
        floorMob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0D);
        floorMob.setHealth(40.0F);
        level.addFreshEntity(floorMob);

        boolean floorPenetrated = com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.executeStageStatic(
                level, probe, floorMob,
                com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T001_NORMAL_MOB_HURT,
                1.0F, 1, probe.record());
        var floorProf = com.automatavalkyries.entity.akashic.AkashicDamageFlowchart.profileOf(floorMob);
        helper.assertTrue(floorPenetrated && floorProf.floorConfirmed && floorProf.minPassedAboveFloor >= 45.0F,
                "T001 flowchart should binary-search upward past 45.0 floor and defeat floorMob, profile="
                        + floorProf.diagnosticSummary());

        // 3. 攻撃者フィルタ（getEntity() != null 無効）＋物理無効モブを T001 単体でトポロジー＆属性切替突破
        var filterMob = new net.minecraft.world.entity.monster.Husk(EntityType.HUSK, level) {
            @Override
            public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
                // 攻撃者が付いている攻撃、および mob_attack は完全無効
                if (source.getEntity() != null || source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK)) {
                    return false;
                }
                return super.hurt(source, amount);
            }
        };
        filterMob.moveTo(absPos.getX() + 0.5, absPos.getY(), absPos.getZ() + 0.5, 0, 0);
        filterMob.setHealth(20.0F);
        level.addFreshEntity(filterMob);

        boolean filterPenetrated = com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.executeStageStatic(
                level, probe, filterMob,
                com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T001_NORMAL_MOB_HURT,
                1.0F, 1, probe.record());
        var filterProf = com.automatavalkyries.entity.akashic.AkashicDamageFlowchart.profileOf(filterMob);
        helper.assertTrue(filterPenetrated
                        && filterProf.bestTopology == com.automatavalkyries.entity.akashic.AkashicDamageFlowchart.AttackerTopology.ANONYMOUS_ENV,
                "T001 flowchart should switch to ANONYMOUS_ENV and non-physical channel to defeat filterMob, profile="
                        + filterProf.diagnosticSummary());

        // 4a. 不死のトーテム保持モブを予備打撃でトーテム消費させた上で本打撃撃破
        var totemMob = new net.minecraft.world.entity.monster.Husk(EntityType.HUSK, level);
        totemMob.moveTo(absPos.getX() + 0.5, absPos.getY(), absPos.getZ() + 0.5, 0, 0);
        totemMob.setHealth(20.0F);
        totemMob.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
        level.addFreshEntity(totemMob);
        boolean totemPenetrated = com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.executeStageStatic(
                level, probe, totemMob,
                com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T001_NORMAL_MOB_HURT,
                1.0F, 1, probe.record());
        helper.assertTrue(totemPenetrated && totemMob.getOffhandItem().isEmpty(),
                "Step 0 should pop Totem of Undying and finish off totemMob in T001");

        // 4b. 耐性IV＋吸収＋1 Tick 後 HP 書き戻しモブの検問と書き戻し検知
        var revertMob = new net.minecraft.world.entity.monster.Husk(EntityType.HUSK, level);
        revertMob.moveTo(absPos.getX() + 0.5, absPos.getY(), absPos.getZ() + 0.5, 0, 0);
        revertMob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
        revertMob.setHealth(100.0F);
        revertMob.setAbsorptionAmount(50.0F);
        revertMob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 3));
        level.addFreshEntity(revertMob);

        com.automatavalkyries.entity.akashic.AkashicDamageFlowchart.executeSmartNormalDamage(
                level, probe, revertMob, 0, 30.0F, false, probe.record());
        helper.assertTrue(revertMob.getAbsorptionAmount() == 0.0F && !revertMob.hasEffect(MobEffects.DAMAGE_RESISTANCE),
                "Step 0 should strip absorption and DAMAGE_RESISTANCE");
        // 直後に HP を 100 に書き戻す（チート再生の模擬）
        revertMob.setHealth(100.0F);

        helper.runAfterDelay(2, () -> {
            probe.auditor().tickMode();
            var keys = AkashicRecord.keysForTest(probeUuid);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("flowchart:ceiling_detected|ADAPTED")),
                    "Ceiling detection was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("flowchart:floor_detected|ADAPTED")),
                    "Floor detection was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("flowchart:source_switched|ADAPTED")),
                    "Source/topology switch was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("flowchart:totem_pop_bait|ADAPTED")),
                    "Totem pop bait was not recorded: " + keys);
            helper.assertTrue(keys.stream().anyMatch(k -> k.contains("flowchart:revert_detected|ADAPTED")),
                    "Delayed HP revert detection was not recorded: " + keys);

            revertMob.discard();
            AkashicNonEntityProxy.unregister(probeUuid);
            probe.getEprotect().triggerDespawn();
            helper.succeed();
        });
    }

    /**
     * 10. HP データスロット直接抜去（{@code itemsById.remove(DATA_HEALTH_ID)} / {@code itemsById.clear()} / {@code T063}）に対する
     * 無クラッシュ修復・記録と、頭上専用観測 UI（{@link com.automatavalkyries.entity.akashic.AkashicProbeEntity#observationUiData()} /
     * {@link com.automatavalkyries.entity.akashic.AkashicProbeEntity#hologramLines()}）の同期検証。
     */
    @GameTest(template = "empty_3x3x3", timeoutTicks = 120)
    public static void akashicHpDataSlotEvictionResilienceAndOverheadObservationUi(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(1, 2, 1);
        var probe = helper.spawnWithNoFreeWill(ModEntities.AKASHIC_PROBE.get(), pos);
        UUID probeUuid = probe.getUUID();

        // 1. アカシック自身の SynchedEntityData.itemsById から DATA_HEALTH_ID を直接 remove しても getHealth()/setHealth()/baseTick() が落ちない
        var healthAcc = LivingEntityAccessor.automata_valkyries$getDataHealthId();
        var probeItems = ((SynchedEntityDataAccessor) probe.getEntityData()).automata_valkyries$getItemsById();
        probeItems.remove(healthAcc.getId());
        float hpAfterRemove = probe.getHealth();
        probe.setHealth(hpAfterRemove);
        helper.assertTrue(hpAfterRemove == 100.0F && probeItems.get(healthAcc.getId()) != null,
                "Removing DATA_HEALTH_ID from AkashicProbeEntity must not crash and must immediately restore 100.0F, got " + hpAfterRemove);

        // 2. itemsById.clear() で全スロット（DATA_HEALTH_ID, HOLOGRAM, OBSERVATION_UI_DATA, DATA_POSE 等）を丸ごと抜かれても無傷で復元する
        probeItems.clear();
        probe.baseTick();
        helper.assertTrue(probe.getHealth() == 100.0F && probe.isAlive()
                        && probeItems.get(healthAcc.getId()) != null
                        && !probe.observationUiData().isEmpty(),
                "Clearing all SynchedEntityData slots on AkashicProbeEntity must be fully restored without NPE");

        // 3. 非保護モブ（Husk）に対して T063_DATA_ITEM_SLOT_EVICTION を執行しても null スロット放置による NPE クラッシュが起きない
        BlockPos absPos = helper.absolutePos(pos).offset(1, 0, 0);
        var targetMob = new net.minecraft.world.entity.monster.Husk(EntityType.HUSK, level);
        targetMob.moveTo(absPos.getX() + 0.5, absPos.getY(), absPos.getZ() + 0.5, 0, 0);
        targetMob.setHealth(20.0F);
        level.addFreshEntity(targetMob);

        boolean t063Penetrated = com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.executeStageStatic(
                level, probe, targetMob,
                com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage.T063_DATA_ITEM_SLOT_EVICTION,
                1.0F, 1, probe.record());
        var targetItems = ((SynchedEntityDataAccessor) targetMob.getEntityData()).automata_valkyries$getItemsById();
        helper.assertTrue(t063Penetrated && targetItems.get(healthAcc.getId()) != null && targetMob.getHealth() == 0.0F,
                "T063_DATA_ITEM_SLOT_EVICTION must sever the old DataItem, defeat the mob, and leave a valid 0.0F DataItem so Vanilla never NPEs");

        // 4. 頭上専用観測 UI（observationUiData + hologramLines）にモード・HP・カウンタ・検証結果・DPS が同期されているか検証
        var auditVictim = new net.minecraft.world.entity.monster.Husk(EntityType.HUSK, level);
        auditVictim.moveTo(absPos.getX() + 0.5, absPos.getY(), absPos.getZ() + 0.5, 0, 0);
        auditVictim.setHealth(30.0F);
        auditVictim.addTag(com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AUDIT_TARGET_TAG);
        level.addFreshEntity(auditVictim);

        probe.setMode(com.automatavalkyries.entity.akashic.AkashicProbeEntity.Mode.RESISTANCE_AUDIT, null);
        probe.auditor().runNextAuditStep(level, auditVictim);
        probe.baseTick();

        var ui = probe.observationUiData();
        var lines = probe.hologramLines();
        var keys = AkashicRecord.keysForTest(probeUuid);
        helper.assertTrue(keys.stream().anyMatch(k -> k.contains("synched_data:health_slot_evicted|REPAIRED")),
                "Health slot eviction repair was not recorded in AkashicRecord: " + keys);
        helper.assertTrue(ui.getInt("ModeOrdinal") == com.automatavalkyries.entity.akashic.AkashicProbeEntity.Mode.RESISTANCE_AUDIT.ordinal()
                        && ui.getFloat("Hp") == 100.0F
                        && ui.getLong("RepairedCount") > 0L
                        && !ui.getString("TargetName").isEmpty()
                        && ui.getInt("StageTotal") == 108
                        && !ui.getString("LastVerdictRank").isEmpty()
                        && !lines.isEmpty(),
                "Overhead Observation UI telemetry did not sync expected fields: ui=" + ui + " lines=" + lines.size());

        targetMob.discard();
        auditVictim.discard();
        AkashicNonEntityProxy.unregister(probeUuid);
        probe.getEprotect().triggerDespawn();
        helper.succeed();
    }
}
