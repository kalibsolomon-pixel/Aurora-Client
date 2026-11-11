package com.aurora.client.util.reflex;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ObjectPool<T> {
    private final Queue<T> pool;
    private final ObjectFactory<T> factory;
    private final ObjectResetter<T> resetter;

    public ObjectPool(ObjectFactory<T> factory, ObjectResetter<T> resetter) {
        this.pool = new ConcurrentLinkedQueue<>();
        this.factory = factory;
        this.resetter = resetter;
    }

    public T borrow() {
        T obj = pool.poll();
        if (obj == null) {
            obj = factory.create();
        }
        return obj;
    }

    public void returnObject(T obj) {
        if (obj != null) {
            resetter.reset(obj);
            pool.offer(obj);
        }
    }

    public interface ObjectFactory<T> {
        T create();
    }

    public interface ObjectResetter<T> {
        void reset(T obj);
    }
}
