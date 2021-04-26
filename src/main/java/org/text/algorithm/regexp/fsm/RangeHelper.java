/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp.fsm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * 生成一定数字范围的自动机
 */
public class RangeHelper {
    int start;
    int end;
    NFAState leftState;
    NFAState rightState;
    Supplier<NFAState> supplier;

    public RangeHelper(int start, int end, NFAState leftState, NFAState rightState, Supplier<NFAState> supplier) {
        this.start = start;
        this.end = end;
        this.leftState = leftState;
        this.rightState = rightState;
        this.supplier = supplier;
    }

    public void make() {

        String x = Integer.toString(start);
        String y = Integer.toString(end);

        int d = y.length();
        StringBuilder bx = new StringBuilder();
        for (int i = x.length(); i < d; i++)
            bx.append('0');
        bx.append(x);
        x = bx.toString();

        Collection<NFAState> initials = new ArrayList<>();
        boolean zeros = x.charAt(0) == '0';
        NFAState init = between(x, y, 0, initials, zeros);
        if (zeros) {
            for (NFAState p : initials) {
                leftState.directState(p);
            }
        } else {
            leftState.directState(init);
        }
    }

    private NFAState between(String x, String y, int n, Collection<NFAState> initials, boolean zeros) {
        NFAState s = supplier.get();
        if (x.length() == n) {
            s.directState(rightState);
        } else {
            if (zeros)
                initials.add(s);
            char cx = x.charAt(n);
            char cy = y.charAt(n);
            if (cx == cy)
                s.transitionState(new Range(cx), between(x, y, n + 1, initials, zeros && cx == '0'));
            else { // cx<cy
                s.transitionState(new Range(cx), atLeast(x, n + 1, initials, zeros && cx == '0'));
                s.transitionState(new Range(cy), atMost(y, n + 1));
                if (cx + 1 < cy) {
                    NFAState target = anyOfRightLength(x, n + 1);
                    s.transitionState(new Range(cx + 1, cy - 1), target);
                }
            }
        }
        return s;
    }

    private NFAState atLeast(String x, int n, Collection<NFAState> initials, boolean zeros) {
        NFAState s = supplier.get();
        if (x.length() == n) {
            s.directState(rightState);
        } else {
            if (zeros) {
                initials.add(s);
            }
            char c = x.charAt(n);
            s.transitionState(new Range(c), atLeast(x, n + 1, initials, zeros && c == '0'));
            if (c < '9') {
                NFAState target = anyOfRightLength(x, n + 1);
                s.transitionState(new Range(c + 1, '9'), target);
            }
        }
        return s;
    }

    private NFAState atMost(String x, int n) {
        NFAState s = supplier.get();
        if (x.length() == n)
            s.directState(rightState);
        else {
            char c = x.charAt(n);
            s.transitionState(new Range(c), atMost(x, n + 1));
            if (c > '0') {
                NFAState target = anyOfRightLength(x, n + 1);
                s.transitionState(new Range('0', c - 1), target);
            }
        }
        return s;
    }

    private NFAState anyOfRightLength(String x, int n) {
        NFAState s = supplier.get();
        if (x.length() == n)
            s.directState(rightState);
        else {
            NFAState target = anyOfRightLength(x, n + 1);
            s.transitionState(new Range('0', '9'), target);
        }
        return s;
    }

}