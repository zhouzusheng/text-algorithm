/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp.fsm;

import java.util.*;
import java.util.function.Function;

public class StateContainer<T extends StateT<T>> {
    private final T[] states;
    private final int hash_code;

    public StateContainer(T[] src, boolean shouldSort) {
        states = src;
        if (shouldSort) {
            Arrays.sort(states, StateT.COMPARATOR);
        }
        this.hash_code = makeHashCode();
    }

    public StateContainer(Collection<T> src, boolean shouldStrip, boolean shouldSort) {
        if (shouldStrip) {
            //消重
            List<T> data = new ArrayList<>(src.size());
            HashSet<T> tmp = new HashSet<>();
            for (T t : src) {
                if (tmp.add(t)) {
                    data.add(t);
                }
            }
            src = data;
        }
        T first = src.iterator().next();
        T[] r = (T[]) java.lang.reflect.Array
                .newInstance(first.getClass(), src.size());
        states = src.toArray(r);

        if (shouldSort) {
            Arrays.sort(states, StateT.COMPARATOR);
        }
        this.hash_code = makeHashCode();
    }

    @Override
    public int hashCode() {
        return hash_code;
    }

    public T[] getStates() {
        return states;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof StateContainer))
            return false;
        StateContainer c = (StateContainer) o;
        if (c.hash_code != hash_code) {
            return false;
        }
        if (c.states.length != states.length)
            return false;

        for (int i = 0; i < states.length; i++) {
            if (states[i].getId() != c.states[i].getId()) {
                return false;
            }
        }
        return true;
    }

    private int makeHashCode() {
        int code = 0;
        for (T s : states) {
            code += HashUtils.hash32shift(s.getId());
        }
        return code;
    }

    public Map<Range, StateContainer<T>> stepStates() {
        return stepObjects(t -> new StateContainer<>(t, true, true));
    }

    public Map<Range, Collection<T>> steps() {
        return stepObjects(t -> t);
    }

    public <U> Map<Range, U> stepObjects(Function<Collection<T>, U> converter) {
        List<TransitionT<T>> dest = new ArrayList<>(states.length * 2);

        for (T s : states) {
            List<TransitionT<T>> transitions = s.getTransitions();
            if (transitions != null) {
                dest.addAll(transitions);
            }
        }
        dest.sort(TransitionT.COMPARATOR);

        Map<Range, U> results = new HashMap<>();

        int lastStart = Integer.MIN_VALUE;
        int lastEnd = Integer.MIN_VALUE;
        for (int i = 0; i < dest.size(); i++) {
            TransitionT<T> tr = dest.get(i);
            Range current = tr.getRange();

            while (lastEnd < current.getEnd()) {
                int start = current.getStart();
                int end = current.getEnd();
                if (lastStart >= start) {
                    start = (lastStart + 1);
                }
                if (lastEnd >= start) {
                    start = (lastEnd + 1);
                }
                int j = i + 1;
                for (; j < dest.size(); j++) {
                    TransitionT<T> tr2 = dest.get(j);
                    Range next = tr2.getRange();
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
                Range newRange = new Range(start, end);
                List<T> values = new ArrayList<>();

                for (int k = i; k < j; k++) {
                    TransitionT<T> tr2 = dest.get(k);
                    Range current2 = tr.getRange();
                    if (current2.getStart() <= start && current2.getEnd() >= start) {
                        values.add(tr2.getTo());
                    }
                }
                if (!values.isEmpty()) {
                    results.put(newRange, converter.apply(values));
                }
            }

        }
        return results;
    }

    public Set<Integer> getFinaIds() {
        Set<Integer> ret = null;
        for (StateT<T> s : states) {
            int ruleId = s.getAcceptId();
            if (ruleId != -1) {
                if (ret == null) {
                    ret = new TreeSet<>();
                }
                ret.add(ruleId);
            }
        }
        return ret;
    }
}
