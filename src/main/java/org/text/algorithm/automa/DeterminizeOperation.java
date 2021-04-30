package org.text.algorithm.automa;

import java.util.*;

class DeterminizeOperation {
    DfaUnion union;
    DfaCompose compose;

    List<Dfa.DfaState> states;
    List<Set<Integer>> dfaAccepts;

    int[][] tables;
    boolean[] accepts;

    Map<DeterminizeState, Dfa.DfaState> newStates;

    public DeterminizeOperation(DfaUnion union, DfaCompose compose) {
        this.union = union;
        this.compose = compose;
        this.compose.setIds(union.getIds());
        this.tables = union.getTables();
        this.accepts = union.getAccepts();
    }

    public void determinize() {
        this.dfaAccepts = new ArrayList<>(100);
        this.states = new ArrayList<>(100);
        this.newStates = new HashMap<>();

        TreeSet<Integer> initialset = new TreeSet<>();
        int[] rootIds = union.getRoots();
        for (int id : rootIds) {
            initialset.add(id);
        }
        DeterminizeState initial = new DeterminizeState(initialset);
        Dfa.DfaState root = new Dfa.DfaState();
        root.setId(0);

        states.add(root);
        dfaAccepts.add(new TreeSet<>());
        newStates.put(initial, root);

        Map<Integer, Integer> statesToDfaId = new HashMap<>();
        for(int i = 0; i < rootIds.length; i++) {
            int rootId = rootIds[i];
            int maxId;
            if( i <  rootIds.length-1) {
                maxId = rootIds[i+1];
            } else {
                maxId = union.getTables().length;
            }

            for(int j = rootId; j < maxId; j++){
                statesToDfaId.put(j, i);
            }

        }

        LinkedList<DeterministicEntry> worklist = new LinkedList<>();
        Map<Integer, Set<Integer>> cachedPoints = new HashMap<>();

        worklist.add(new DeterministicEntry(initial, root));
        while (!worklist.isEmpty()) {
            DeterministicEntry entry = worklist.removeFirst();
            DeterminizeState m = entry.m;
            Dfa.DfaState r = entry.s;
            applyAcceptIds(m, r, statesToDfaId);
            int[] points = m.getStartPoints(tables, cachedPoints);
            for (int n = 0; n < points.length; n++) {
                int ch = points[n];
                DeterminizeState m2 = m.step(tables, ch);
                if (!m2.isEmpty()) {
                    Dfa.DfaState q = newStates.get(m2);
                    if (q == null) {
                        q = makeNewState(m2);
                        worklist.add(new DeterministicEntry(m2, q));
                    }
                    r.addTransition(ch, q.getId());
                }
            }
        }
        removeDeadTransitions();
        applyToCompose();
    }

    private void applyToCompose() {
        int[][] dfaTables = new int[states.size()][];
        int[][] dfaAccepts = new int[states.size()][];
        for (int i = 0; i < this.dfaAccepts.size(); i++) {
            Set<Integer> ids = this.dfaAccepts.get(i);
            if (!ids.isEmpty()) {
                int[] accs = dfaAccepts[i] = new int[ids.size()];
                int j = 0;
                for (Integer id : ids) {
                    accs[j++] = id;
                }
            }
        }

        for (int i = 0; i < this.states.size(); i++) {
            Dfa.DfaState state = this.states.get(i);
            Map<Integer, Integer> trans = state.getTrans();
            if (trans != null) {
                int[] table = dfaTables[i] = new int[trans.size() * 2];
                int j = 0;
                for (Map.Entry<Integer, Integer> entry : trans.entrySet()) {
                    table[j] = entry.getKey();
                    table[j + 1] = entry.getValue();
                    j += 2;
                }
            }
        }
        compose.setDfaTables(dfaTables);
        compose.setDfaAccepts(dfaAccepts);
    }

    private void removeDeadTransitions() {
        //消除一些连接
        Set<Integer> live = getLiveStates();
        for (Dfa.DfaState s : states) {
            Map<Integer, Integer> trans = s.getTrans();
            if (trans == null) {
                continue;
            }
            s.setTrans(new TreeMap<>());
            trans.forEach((ch, to) -> {
                if (live.contains(to)) {
                    s.addTransition(ch, to);
                }
            });
        }
    }

    private Set<Integer> getLiveStates() {
        List<Set<Integer>> map = new ArrayList<>(states.size());
        for (int i = 0; i < states.size(); i++) {
            map.add(new HashSet<>());
        }
        Set<Integer> live = new HashSet<>();
        for (Dfa.DfaState s : states) {
            Map<Integer, Integer> trans = s.getTrans();
            if (trans != null) {
                trans.forEach((ch, t) -> {
                    map.get(t).add(s.getId());
                });
            }
            if (s.isAccept()) {
                live.add(s.getId());
            }
        }

        LinkedList<Integer> worklist = new LinkedList<>(live);
        while (worklist.size() > 0) {
            Integer s = worklist.removeFirst();
            for (Integer p : map.get(s))
                if (!live.contains(p)) {
                    live.add(p);
                    worklist.add(p);
                }
        }
        return live;
    }

    private Dfa.DfaState makeNewState(DeterminizeState m2) {
        Dfa.DfaState q = new Dfa.DfaState();
        q.setId(states.size());
        states.add(q);
        dfaAccepts.add(new TreeSet<>());
        newStates.put(m2, q);
        return q;
    }

    private void applyAcceptIds(DeterminizeState m, Dfa.DfaState r, Map<Integer, Integer> statesToDfaId) {
        Set<Integer> acceptIds = dfaAccepts.get(r.getId());
        for (Integer q : m.getStates()) {
            if (accepts[q]) {
                r.setAccept(true);
                acceptIds.add(statesToDfaId.get(q));
            }
        }
    }
}
