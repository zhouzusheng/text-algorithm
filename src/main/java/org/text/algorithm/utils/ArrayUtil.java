package org.text.algorithm.utils;

public class ArrayUtil {
    public static int binSearch(int[] table, int key, int def) {
        return binSearch(table, key, 0, table.length, def);
    }

    public static int binSearch(int[] table, int key, int from, int to, int def) {

        int low = 0;
        int high = ((to - from) >>> 1) - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int pos = (mid << 1) + from;
            int val = table[pos];
            if (val == key) {
                return table[pos + 1];
            } else if (val < key) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return def;  // key not found.

    }
}
