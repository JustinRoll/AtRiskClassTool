package com.jroll.util;

import java.util.TreeMap;

/**
 * Created by jroll on 4/18/16.
 */
public class MyMap<T, V> extends TreeMap {

    public <T> void incMap(T key) {
        if (this.get(key) == null)
            this.put(key, 0);

        this.put(key, ((Integer) this.get(key)) + 1);
    }
}
