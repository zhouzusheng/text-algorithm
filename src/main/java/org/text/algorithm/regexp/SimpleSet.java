package org.text.algorithm.regexp;

import java.lang.reflect.Array;
import java.util.function.Consumer;

/**
 * 简单的消除重复的数组
 *
 * @param <T>
 */
public class SimpleSet<T> {
    private T[] values;
    private int size;

    /**
     * hash 数据
     * 第一维的下表是hash code
     * 第二维的第一个数字是这个code 下存在的数据个数count
     * 第二维的 1 ~ count 是 真实数据， 后面是还没有使用的数据
     */
    private int[][] hash_set;

    public SimpleSet(Class<T> clz, int bucketSize) {
        if (bucketSize < 103) {
            bucketSize = 103;
        }

        hash_set = new int[bucketSize][];

        values = (T[]) Array.newInstance(clz, 4);
        size = 0;
    }

    public void foreach(Consumer<T> consumer) {
        for (int i = 0; i < size; i++) {
            consumer.accept(values[i]);
        }
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            values[i] = null;
        }
        size = 0;
        for (int i = 0; i < hash_set.length; i++) {
            hash_set[i] = null;
        }
    }

    public boolean add(T value) {
        int key = makeHash(value) % hash_set.length;
        int[] data = hash_set[key];
        if (data == null) {
            data = new int[4];
            data[0] = 1;
            data[1] = putValue(value);
            hash_set[key] = data;
            return true;
        } else {
            //检查是否有重复
            int count = data[0];
            for (int i = 0; i < count; i++) {
                int pos = data[i + 1];
                if (values[pos].equals(value)) {
                    return false;
                }
            }
            if (count == data.length - 1) {
                int[] newData = new int[data.length * 2];
                System.arraycopy(data, 0, newData, 0, data.length);
                data = hash_set[key] = newData;
            }
            data[count + 1] = putValue(value);
            data[0] = count + 1;
            return true;
        }
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    public T get(int pos) {
        return values[pos];
    }

    public boolean exists(T item) {
        int key = makeHash(item) % hash_set.length;
        int[] data = hash_set[key];
        if (data == null) {
            return false;
        }
        int count = data[0];
        for (int i = 0; i < count; i++) {
            int pos = data[i + 1];
            if (values[pos].equals(item)) {
                return true;
            }
        }
        return false;
    }

    int putValue(T value) {
        if (size == values.length) {
            T[] newValues = (T[]) Array.newInstance(value.getClass(), values.length * 2);
            System.arraycopy(values, 0, newValues, 0, values.length);
            values = newValues;
        }
        values[size] = value;
        return size++;
    }

    int makeHash(T value) {
        return value.hashCode();
    }

    public static <T> void swap(SimpleSet<T> a, SimpleSet<T> b) {

        T[] values = a.values;
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
