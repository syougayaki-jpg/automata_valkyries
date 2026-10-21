package com.automatavalkyries.gametest.fake;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Independent core, rejecting registry and native removal that destroys its reverse reference. */
public class FakeProxyBoss extends Zombie {
    public final Core core = new Core(this);
    public final AtomicReference<WrappedCore> wrappedCore = new AtomicReference<>(new WrappedCore(this));

    public FakeProxyBoss(Level level) { super(EntityType.ZOMBIE, level); }

    public static final class Core {
        public final UUID id = UUID.randomUUID();
        public FakeProxyBoss proxy;
        public Entity otherEntity;
        public Registry registry = new Registry();
        public boolean removed;
        public boolean nativeSawProxy;
        public int beamTimer = 12;
        public int lives = 3;
        Core(FakeProxyBoss proxy) { this.proxy = proxy; }
        public void remove() {
            nativeSawProxy = proxy != null;
            proxy = null;
            registry = null;
            LevelManager.touch();
        }
    }

    /** ラッパー（AtomicReference）越しに保持され、自身も WeakReference で対象を指し返す副コントローラー */
    public static final class WrappedCore {
        public final WeakReference<FakeProxyBoss> ownerRef;
        public boolean discarded;
        public boolean invincible = true;

        WrappedCore(FakeProxyBoss owner) {
            this.ownerRef = new WeakReference<>(owner);
        }

        public void discard() {
            discarded = true;
        }
    }

    /**
     * ボス本体のフィールドからは一切参照されず、static リスト内からのみ対象の UUID を指している完全分離型コントローラー。
     * DefenseStructureAnalyzer の static 逆引き（REVERSE_STATIC_CONTROLLER）でのみ発見可能。
     */
    public static final class DetachedStaticCore {
        public UUID targetUuid;
        public boolean terminated;
        public int shieldCount = 5;

        public DetachedStaticCore(UUID targetUuid) {
            this.targetUuid = targetUuid;
        }

        public void terminate(boolean force) {
            if (force) {
                terminated = true;
            }
        }
    }

    public static final class Registry {
        public static Entity activeSingletonBoss;
        public static final Object[] BOSS_SLOTS = new Object[2];
        public static final ArrayList<Object> entries = new ArrayList<>() {
            @Override public boolean remove(Object value) { return false; }
        };
    }

    /**
     * インスタンスフィールドからは参照されず、Core のバイトコード（static 呼び出し）からのみ参照される管理クラス。
     * リストは size() / toArray() / remove() で例外を投げる ArrayList 派生（StackChecker 模擬）。
     */
    public static final class LevelManager {
        public static boolean busUpdated = false;
        public static final ArrayList<Object> levelCores = new ArrayList<>() {
            @Override
            public int size() {
                throw new SecurityException("StackChecker blocked size()");
            }

            @Override
            public Object[] toArray() {
                throw new SecurityException("StackChecker blocked toArray()");
            }

            @Override
            public boolean remove(Object value) {
                throw new SecurityException("StackChecker blocked remove()");
            }
        };

        public static void touch() {}

        public static void addDirect(Object core) {
            com.automatavalkyries.util.UnsafeHelper.removeFromArrayList(levelCores, e -> false);
            try {
                var dataField = ArrayList.class.getDeclaredField("elementData");
                var sizeField = ArrayList.class.getDeclaredField("size");
                long dataOffset = com.automatavalkyries.util.UnsafeHelper.objectFieldOffset(dataField);
                long sizeOffset = com.automatavalkyries.util.UnsafeHelper.objectFieldOffset(sizeField);
                Object[] arr = (Object[]) com.automatavalkyries.util.UnsafeHelper.getObject(levelCores, dataOffset);
                int s = com.automatavalkyries.util.UnsafeHelper.getInt(levelCores, sizeOffset);
                if (arr == null || arr.length <= s) {
                    arr = arr == null ? new Object[8] : java.util.Arrays.copyOf(arr, arr.length + 8);
                    com.automatavalkyries.util.UnsafeHelper.putObject(levelCores, dataOffset, arr);
                }
                arr[s] = core;
                com.automatavalkyries.util.UnsafeHelper.putInt(levelCores, sizeOffset, s + 1);
            } catch (Throwable ignored) {}
        }

        public static int rawSize() {
            try {
                long sizeOffset = com.automatavalkyries.util.UnsafeHelper.objectFieldOffset(ArrayList.class.getDeclaredField("size"));
                return com.automatavalkyries.util.UnsafeHelper.getInt(levelCores, sizeOffset);
            } catch (Throwable t) {
                return -1;
            }
        }

        public static Object rawGet(int index) {
            try {
                long dataOffset = com.automatavalkyries.util.UnsafeHelper.objectFieldOffset(ArrayList.class.getDeclaredField("elementData"));
                Object[] arr = (Object[]) com.automatavalkyries.util.UnsafeHelper.getObject(levelCores, dataOffset);
                return arr[index];
            } catch (Throwable t) {
                return null;
            }
        }

        public static void rawClear() {
            com.automatavalkyries.util.UnsafeHelper.removeFromArrayList(levelCores, e -> true);
        }
    }
}
