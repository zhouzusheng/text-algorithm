package org.text.algorithm.regexp;

import java.util.function.IntConsumer;

/**
 * 简单的消除重复数组
 */
public class SimpleIntSet {
    private int[] values;
    private int size;

    private int[][] hash_set;

    public SimpleIntSet(int bucketSize) {
        if (bucketSize < 103) {
            bucketSize = 103;
        }

        hash_set = new int[bucketSize][];
        values = new int[4];
        size = 0;
    }

    public void foreach(IntConsumer consumer) {
        for (int i = 0; i < size; i++) {
            consumer.accept(values[i]);
        }
    }

    public void clear() {
        size = 0;
        for (int i = 0; i < hash_set.length; i++) {
            hash_set[i] = null;
        }
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean add(int value) {
        int key = makeHash(value) % hash_set.length;
        int[] data = hash_set[key];
        if (data == null) {
            data = new int[4];
            data[0] = 1;
            data[1] = value;
            putValue(value);
            hash_set[key] = data;
            return true;
        } else {
            //消除重复
            int count = data[0];
            for (int i = 0; i < count; i++) {
                if (data[i + 1] == value) {
                    return false;
                }
            }
            if (count == data.length - 1) {
                int[] newData = new int[data.length * 2];
                System.arraycopy(data, 0, newData, 0, data.length);
                data = hash_set[key] = newData;
            }
            data[count + 1] = value;
            data[0] = count + 1;
            putValue(value);
            return true;
        }
    }

    public boolean exists(int item) {
        int key = makeHash(item) % hash_set.length;
        int[] data = hash_set[key];
        if (data == null) {
            return false;
        }
        int count = data[0];
        for (int i = 0; i < count; i++) {
            if (data[i + 1] == item) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return size;
    }

    public int get(int pos) {
        return values[pos];
    }

    int putValue(int value) {
        if (size == values.length) {
            int[] newValues = new int[values.length * 2];
            System.arraycopy(values, 0, newValues, 0, values.length);
            values = newValues;
        }
        values[size] = value;
        return size++;
    }

    int makeHash(int value) {
        return value;
    }

    public static void swap(SimpleIntSet a, SimpleIntSet b) {
        int[] values = a.values;
        int size = a.size;

        int[][] hash_set = a.hash_set;

        a.values = b.values;
        a.size = b.size;
        a.hash_set = b.hash_set;

        b.values = values;
        b.size = size;
        b.hash_set = hash_set;
    }
}
