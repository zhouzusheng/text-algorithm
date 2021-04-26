package org.text.algorithm.automa;


import org.text.algorithm.utils.ArrayUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 包装多个状态id为一个
 */
class DeterminizeState {
    private final TreeSet<Integer> states;
    private final int hash_code;

    public DeterminizeState(TreeSet<Integer> states) {
        this.states = states;
        this.hash_code = states.hashCode();
    }

    @Override
    public int hashCode() {
        return hash_code;
    }

    public TreeSet<Integer> getStates() {
        return states;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof DeterminizeState))
            return false;
        DeterminizeState c = (DeterminizeState) o;
        if (c.states.size() != states.size())
            return false;

        Iterator<Integer> it1 = states.iterator();
        Iterator<Integer> it2 = c.states.iterator();
        while (it1.hasNext() && it2.hasNext()) {
            if (!it1.next().equals(it2.next())) {
                return false;
            }
        }
        return true;
    }

    public int[] getStartPoints(int[][] tables, Map<Integer, Set<Integer>> cachedPoints) {
        Set<Integer> pointSet = new TreeSet<>();
        for (Integer id : states) {
            Set<Integer> points = cachedPoints.get(id);
            if (points != null) {
                pointSet.addAll(points);
            } else {
                points = new TreeSet<>();
                cachedPoints.put(id, points);
                int[] table = tables[id];
                if (table != null) {
                    for (int i = 0; i < table.length; i += 2) {
                        int ch = table[i];
                        points.add(ch);
                        pointSet.add(ch);
                    }
                }
            }
        }
        int[] points = new int[pointSet.size()];
        int n = 0;
        for (Integer m : pointSet)
            points[n++] = m;
        return points;
    }

    public DeterminizeState step(int[][] tables, int ch) {
        TreeSet<Integer> p = new TreeSet<>();
        for (Integer q : states) {
            int[] table = tables[q];
            if (table != null) {
                int dest = ArrayUtil.binSearch(tables[q], ch, -1);
                if (dest != -1) {
                    p.add(dest);
                }
            }
        }
        return new DeterminizeState(p);
    }

    public boolean isEmpty() {
        return states.isEmpty();
    }
}
