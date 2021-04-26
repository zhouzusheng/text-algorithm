package org.text.algorithm.automa;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * 单个DFA
 */
public class Dfa {
    /**
     * 默认是0;
     */
    int root;

    /**
     * id
     */
    Object id;

    Map<Integer, DfaState> states;

    public int getStateCount() {
        return states == null ? null : states.size();
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public int getRoot() {
        return root;
    }

    public void setRoot(int root) {
        this.root = root;
    }

    public Map<Integer, DfaState> getStates() {
        return states;
    }

    public void setStates(Map<Integer, DfaState> states) {
        this.states = states;
    }

    public static class DfaState {
        int id;
        TreeMap<Integer, Integer> trans;
        boolean accept;

        public DfaState() {
            id = 0;
        }

        public DfaState(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public TreeMap<Integer, Integer> getTrans() {
            return trans;
        }

        public void setTrans(TreeMap<Integer, Integer> trans) {
            this.trans = trans;
        }

        public boolean isAccept() {
            return accept;
        }

        public void addTransition(Integer ch, Integer dest) {
            if (trans == null) {
                trans = new TreeMap<>();
            }
            trans.put(ch, dest);
        }

        public Integer getTransition(int ch) {
            if (trans == null) {
                return null;
            } else {
                return trans.get(ch);
            }
        }

        public void setAccept(boolean accept) {
            this.accept = accept;
        }
    }

    public boolean match(String text, int start) {
        Integer j = root;
        for (int i = start; i < text.length(); i++) {
            DfaState state = states.get(j);
            j = state.getTransition(text.charAt(i));
            if (j == null) {
                return false;
            }
        }
        return states.get(j).isAccept();
    }

    public boolean startsWith(String text, int start) {
        Integer j = root;
        for (int i = start; i < text.length(); i++) {
            DfaState state = states.get(j);
            if (state.isAccept()) {
                return true;
            }
            j = state.getTransition(text.charAt(i));
            if (j == null) {
                return false;
            }
        }
        return states.get(j).isAccept();
    }

    /**
     * 并集： 要求 所有dfa 的状态是连续的， 且从0 开始
     *
     * @param dfas
     * @return
     */
    public static DfaUnion union(Dfa... dfas) {
        return union(new DfaUnion(), dfas);
    }

    public static DfaUnion union(DfaUnion union, Dfa... dfas) {
        int[] roots = new int[dfas.length];
        Object[] ids = new Object[dfas.length];

        int i = 0;
        int stateCount = 0;
        for (Dfa dfa : dfas) {
            roots[i] = dfa.root;
            ids[i] = dfa.id;
            stateCount += dfa.states.size();
            i++;
        }
        int[][] tables = new int[stateCount][];
        boolean[] accepts = new boolean[stateCount];

        i = 0;
        for (Dfa dfa : dfas) {
            dfa.states.values().forEach(state -> {
                accepts[state.getId()] = state.isAccept();
            });
            unionTables(dfa, tables, i++);
        }
        union.setRoots(roots);
        union.setIds(ids);
        union.setTables(tables);
        union.setAccepts(accepts);
        return union;
    }

    /**
     * 并集： 要求 所有dfa 的状态是连续的， 且从0 开始
     *
     * @param dfas
     * @return
     */
    public static DfaUnion union(Collection<Dfa> dfas) {
        return union(new DfaUnion(), dfas);
    }

    public static DfaUnion union(DfaUnion union, Collection<Dfa> dfas) {

        int[] roots = new int[dfas.size()];
        Object[] ids = new Object[dfas.size()];
        int i = 0;
        int stateCount = 0;
        for (Dfa dfa : dfas) {
            roots[i] = dfa.root;
            ids[i] = dfa.id;
            stateCount += dfa.states.size();
            i++;
        }
        int[][] tables = new int[stateCount][];
        boolean[] accepts = new boolean[stateCount];
        i = 0;
        for (Dfa dfa : dfas) {
            dfa.states.values().forEach(state -> {
                accepts[state.getId()] = state.isAccept();
            });
            unionTables(dfa, tables, i++);
        }
        union.setRoots(roots);
        union.setIds(ids);
        union.setTables(tables);
        union.setAccepts(accepts);
        return union;
    }

    private static void unionTables(Dfa dfa, int[][] tables, int index) {
        dfa.states.forEach((id, state) -> {
            if (state.trans != null) {
                int[] table = tables[id] = new int[state.trans.size() * 2];
                int i = 0;
                for (Map.Entry<Integer, Integer> entry : state.trans.entrySet()) {
                    table[i] = entry.getKey();
                    table[i + 1] = entry.getValue();
                    i += 2;
                }
            } else {
                tables[id] = null;
            }
        });
    }
}
