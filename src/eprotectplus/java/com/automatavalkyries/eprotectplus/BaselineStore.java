package com.automatavalkyries.eprotectplus;

import java.util.HashMap;
import java.util.Map;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/** Loader identities are weak; stored bytes never retain a loader or its classes. */
public final class BaselineStore {
    private final Map<LoaderKey, Map<String, byte[]>> loaders = new HashMap<>();
    private final ReferenceQueue<ClassLoader> collected = new ReferenceQueue<>();
    private final Map<String, byte[]> bootstrap = new HashMap<>();

    public synchronized void capture(ClassLoader loader, String name, byte[] bytes) {
        bucket(loader).putIfAbsent(name, bytes.clone());
    }

    public synchronized byte[] get(ClassLoader loader, String name) {
        expunge();
        Map<String, byte[]> entries = loader == null ? bootstrap : loaders.get(new LoaderKey(loader, null));
        byte[] bytes = entries == null ? null : entries.get(name);
        return bytes == null ? null : bytes.clone();
    }

    public synchronized boolean contains(ClassLoader loader, String name) {
        expunge();
        Map<String, byte[]> entries = loader == null ? bootstrap : loaders.get(new LoaderKey(loader, null));
        return entries != null && entries.containsKey(name);
    }

    private Map<String, byte[]> bucket(ClassLoader loader) {
        expunge();
        return loader == null ? bootstrap : loaders.computeIfAbsent(new LoaderKey(loader, collected), ignored -> new HashMap<>());
    }

    private void expunge() {
        LoaderKey key;
        while ((key = (LoaderKey) collected.poll()) != null) loaders.remove(key);
    }

    private static final class LoaderKey extends WeakReference<ClassLoader> {
        private final int hash;
        LoaderKey(ClassLoader loader, ReferenceQueue<ClassLoader> queue) {
            super(loader, queue);
            hash = System.identityHashCode(loader);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof LoaderKey key && get() != null && get() == key.get();
        }
    }
}
