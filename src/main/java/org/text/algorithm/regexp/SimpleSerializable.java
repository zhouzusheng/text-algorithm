/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.function.Supplier;

public interface SimpleSerializable {

    void save(DataOutput out) throws IOException;

    void load(DataInput input) throws IOException;

    static <T extends SimpleSerializable> void saveObjects(DataOutput out, T[] objs) throws IOException {
        if (objs == null) {
            out.writeInt(0);
        } else {
            out.writeInt(objs.length);
            for (T obj : objs) {
                obj.save(out);
            }
        }
    }

    static <T extends SimpleSerializable> T[] loadObjects(Class<T> clz, DataInput input, Supplier<T> supplier) throws IOException {
        int count = input.readInt();
        if (count == 0) {
            return null;
        }
        T[] results = (T[]) java.lang.reflect.Array.newInstance(clz, count);
        for (int i = 0; i < count; i++) {
            T r = results[i] = supplier.get();
            r.load(input);
        }
        return results;
    }

    static void writeTwoDimTables(DataOutput out, int[][] tables) throws IOException {
        if (tables == null) {
            out.writeInt(0);
        } else {
            out.writeInt(tables.length);
            for (int i = 0; i < tables.length; i++) {
                writeOneDimTables(out, tables[i]);
            }
        }
    }

    static void writeOneDimTables(DataOutput out, int[] table) throws IOException {
        if (table == null) {
            out.writeInt(0);
        } else {
            out.writeInt(table.length);
            for (int i = 0; i < table.length; i++) {
                out.writeInt(table[i]);
            }
        }
    }

    static int[][] loadTwoDimTables(DataInput input) throws IOException {
        int count = input.readInt();
        if (count == 0) {
            return null;
        }
        int[][] results = new int[count][];
        for (int i = 0; i < count; i++) {
            results[i] = loadOneDimTables(input);
        }
        return results;
    }

    static int[] loadOneDimTables(DataInput input) throws IOException {
        int count = input.readInt();
        if (count == 0) {
            return null;
        }
        int[] results = new int[count];
        for (int i = 0; i < count; i++) {
            results[i] = input.readInt();
        }
        return results;
    }
}
