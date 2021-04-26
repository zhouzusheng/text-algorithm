/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.utils;

public class IntTuple implements Comparable<IntTuple> {
    public int first;
    public int second;
    public int third;

    public IntTuple() {

    }

    public IntTuple(int first, int second, int third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    @Override
    public int compareTo(IntTuple pair) {
        int cmp = Integer.compare(first, pair.first);
        if (cmp == 0) {
            cmp = Integer.compare(second, pair.second);
            if (cmp == 0) {
                cmp = Integer.compare(third, pair.third);
            }
        }
        return cmp;
    }

    @Override
    public int hashCode() {
        return first * (163 * 163) + second * 163 + third;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj.getClass() == getClass()) {
            IntTuple ip = (IntTuple) obj;
            return first == ip.first && second == ip.second && third == ip.third;
        }
        return false;
    }
}
