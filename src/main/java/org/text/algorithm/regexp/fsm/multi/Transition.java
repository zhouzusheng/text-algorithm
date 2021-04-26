/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp.fsm.multi;

import org.text.algorithm.regexp.fsm.TransitionT;

import java.util.Comparator;

public class Transition implements Comparable<Transition> {

    public static final Comparator<Transition> DEFAULTCOMP = Comparator.comparingInt((Transition it) -> it.min)
            .thenComparingInt(it -> it.max).thenComparingInt(it -> it.getDest());

    int min;
    int max;
    int to;

    public Transition(int c, int to) {
        min = max = c;
        this.to = to;
    }

    public Transition(int min, int max, int to) {
        if (max < min) {
            int t = max;
            max = min;
            min = t;
        }
        this.min = min;
        this.max = max;
        this.to = to;
    }

    /**
     * Returns minimum of this transition interval.
     */
    public int getMin() {
        return min;
    }

    /**
     * Returns maximum of this transition interval.
     */
    public int getMax() {
        return max;
    }

    /**
     * Returns destination of this transition.
     */
    public int getDest() {
        return to;
    }

    @Override
    public int compareTo(Transition o) {
        return DEFAULTCOMP.compare(this, o);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof Transition)) {
            Transition t = (Transition) obj;
            return t.min == min && t.max == max && t.to == to;
        } else
            return false;
    }

    /**
     * Returns hash code.
     * The hash code is based on the character interval (not the destination state).
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return min * 2 + max * 3;
    }


    /**
     * Returns a string describing this state. Normally invoked via
     */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        TransitionT.appendCharString(min, b);
        if (min != max) {
            b.append("-");
            TransitionT.appendCharString(max, b);
        }
        b.append(" -> ").append(to);
        return b.toString();
    }

    void appendDot(StringBuilder b) {
        b.append(" -> ").append(to).append(" [label=\"");
        TransitionT.appendCharString(min, b);
        if (min != max) {
            b.append("-");
            TransitionT.appendCharString(max, b);
        }
        b.append("\"]\n");
    }
}
