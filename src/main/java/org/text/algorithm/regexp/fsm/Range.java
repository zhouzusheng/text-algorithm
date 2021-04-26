/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp.fsm;

import org.text.algorithm.regexp.vm.PikeVMOpcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class Range implements Comparable<Range> {
    private final int start;
    private final int end;

    public Range(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public Range(int start) {
        this.start = start;
        this.end = start;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public boolean inRange(int c) {
        return c >= start && c <= end;
    }

    public boolean isSingleRange() {
        return start == end;
    }

    @Override
    public String toString() {
        return "CharRange{" +
                "start=" + start +
                ", end=" + end +
                '}';
    }

    public static List<Range> minimalCovering(List<Range> ranges) {
        List<Range> minimized = new ArrayList<>();
        ranges = new TreeSet<>(ranges).stream().collect(Collectors.toList());
        if (ranges.size() < 2) {
            return ranges;
        }

        int lastStart = Integer.MIN_VALUE;
        int lastEnd = Integer.MIN_VALUE;
        for (int i = 0; i < ranges.size(); i++) {
            Range current = ranges.get(i);
            while (lastEnd < current.getEnd()) {
                int start = current.getStart();
                int end = current.getEnd();
                if (lastStart >= start) {
                    start = (lastStart + 1);
                }
                if (lastEnd >= start) {
                    start = (lastEnd + 1);
                }
                for (int j = i + 1; j < ranges.size(); j++) {
                    Range next = ranges.get(j);
                    if (next.getStart() > end) {
                        break;
                    }
                    if (next.getStart() > start && next.getStart() < end) {
                        end = (next.getStart() - 1);
                    }
                    if (next.getEnd() >= start && next.getEnd() <= end) {
                        end = next.getEnd();
                    }
                }

                lastStart = start;
                lastEnd = end;
                minimized.add(new Range(start, end));
            }
        }
        return minimized;
    }

    public static List<Range> compact(TreeSet<Range> charRanges) {
        List<Range> ranges = new ArrayList<>();
        boolean hasAnly = false;
        int lastStart = -1;
        int lastEnd = -1;
        for (Range r : charRanges) {
            if (r.getStart() < 0) {
                ranges.add(r);
                if (r.getStart() <= PikeVMOpcodes.DOT && r.getEnd() >= PikeVMOpcodes.DOT) {
                    hasAnly = true;
                }
                lastStart = r.getEnd() + 1;
                lastEnd = lastStart;
            } else if (hasAnly) {
                break;
            } else {
                if (lastEnd < 0) {
                    lastStart = r.getStart();
                    lastEnd = r.getEnd();
                } else {
                    if (incr(lastEnd) == r.getStart()) {
                        lastEnd = r.getEnd();
                    } else {
                        ranges.add(new Range(lastStart, lastEnd));
                        lastStart = r.getStart();
                        lastEnd = r.getEnd();
                    }
                }
            }
        }
        if (hasAnly == false && lastStart >= 0) {
            ranges.add(new Range(lastStart, lastEnd));
        }
        return ranges;
    }

    private static int incr(int c) {
        return c + 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Range charRange = (Range) o;
        return start == charRange.start &&
                end == charRange.end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public int compareTo(Range o) {
        int code = Integer.compare(start, o.start);
        if (code == 0) {
            code = Integer.compare(end, o.end);
        }
        return code;
    }

    public static void main(String[] args) {
        List<Range> ranges = new ArrayList<>();
        ranges.add(new Range(-1, -1));
        ranges.add(new Range(0, 100));
        ranges.add(new Range(2, 200));
        ranges.add(new Range(50, 80));
        ranges.add(new Range(60, 120));
        ranges.add(new Range(121, 122));
        System.out.println(minimalCovering(ranges));

    }
}
