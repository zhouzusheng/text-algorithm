/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.utils;

public class IntPair implements Comparable<IntPair> {
    public int first;
    public int second;

    public IntPair() {

    }

    public IntPair(int first, int second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public int compareTo(IntPair pair) {
        int cmp = Integer.compare(first, pair.first);
        if (cmp == 0) {
            cmp = Integer.compare(second, pair.second);
        }
        return cmp;
    }

    @Override
    public int hashCode() {
        return first * 163 + second;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj.getClass() == getClass()) {
            IntPair ip = (IntPair) obj;
            return first == ip.first && second == ip.second;
        }
        return false;
    }
}
