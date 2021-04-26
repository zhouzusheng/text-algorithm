/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp.fsm;

import java.util.Comparator;

public class TransitionT<T extends Idable> implements Comparable<TransitionT<T>> {

    public static final Comparator<TransitionT<? extends Idable>> COMPARATOR =
            Comparator.comparing((TransitionT<? extends Idable> r) -> r.getRange())
                    .thenComparingInt(r -> r.getTo().getId());

    private Range range;
    private T to;

    public TransitionT(Range range, T to) {
        this.range = range;
        this.to = to;
    }

    public Range getRange() {
        return range;
    }

    public void setRange(Range range) {
        this.range = range;
    }

    public T getTo() {
        return to;
    }

    public void setTo(T to) {
        this.to = to;
    }

    @Override
    public int compareTo(TransitionT<T> o) {
        return COMPARATOR.compare(this, o);
    }

    public static void appendCharString(int c, StringBuilder b) {
        if (c >= 0x21 && c <= 0x7e && c != '\\' && c != '"')
            b.append(c);
        else {
            b.append("\\u");
            String s = Integer.toHexString(c);
            if (c < 0x10)
                b.append("000").append(s);
            else if (c < 0x100)
                b.append("00").append(s);
            else if (c < 0x1000)
                b.append("0").append(s);
            else
                b.append(s);
        }
    }

    /**
     * Returns a string describing this state. Normally invoked via
     */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        appendCharString(range.getStart(), b);
        if (range.getStart() != range.getStart()) {
            b.append("-");
            appendCharString(range.getEnd(), b);
        }
        b.append(" -> ").append(to);
        return b.toString();
    }

    void appendDot(StringBuilder b) {
        b.append(" -> ").append(to).append(" [label=\"");
        appendCharString(range.getStart(), b);
        if (range.getStart() != range.getEnd()) {
            b.append("-");
            appendCharString(range.getEnd(), b);
        }
        b.append("\"]\n");
    }
}
