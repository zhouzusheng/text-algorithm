/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp.fsm.multi;

import org.text.algorithm.regexp.Automaton;
import org.text.algorithm.regexp.fsm.*;

import java.util.*;

/**
 * convert set of dfa to a big dfa
 */
public class MultiDFAHelper {
    //root id of each dfa
    private DFAState[] rootStates;

    //all dfa state
    private List<DFA> dfaList;

    // all new state
    List<State> fstStates;
    // multiState to new state
    Map<StateContainer<DFAState>, State> statesMap;

    int stateCount;

    public MultiDFAHelper(List<DFA> dfaList) {
        this.dfaList = dfaList;
        this.rootStates = new DFAState[dfaList.size()];
        stateCount = 0;
        int i = 0;
        for (DFA it : dfaList) {
            this.rootStates[i++] = it.getStates()[it.getRoot() - it.getBaseId()];
            stateCount += it.getStates().length;
        }
    }

    public Automaton buildMultiDfa(int stateLimit) {
        LinkedList<DeterministicEntry<DFAState, State>> stack = new LinkedList<>();

        int hintLen = Math.min(stateLimit, stateCount);

        // multiState to new state
        statesMap = new HashMap<>(hintLen);

        // all new state
        fstStates = new ArrayList<>(hintLen);

        //all trans , key is new state id
        Map<Integer, Set<Transition>> allTrans = new HashMap<>(hintLen);

        //the multistate which is exceed limit
        Map<StateContainer<DFAState>, Integer> pendingStates = new HashMap<>(10);

        DeterministicEntry<DFAState, State> entry = new DeterministicEntry(makeInitStates(), makeState());
        statesMap.put(entry.m, entry.s);

        allTrans.put(entry.s.getId(), new TreeSet<>());

        stack.push(entry);

        do {
            entry = stack.pollFirst();
            StateContainer<DFAState> s = entry.m;
            State r = entry.s;

            Set<Integer> ids = s.getFinaIds();
            if (ids != null) {
                r.addFinalIds(ids);
            }

            Set<Transition> trans = allTrans.get(r.getId());
            Map<Range, StateContainer<DFAState>> nexts = s.stepStates();
            for (Map.Entry<Range, StateContainer<DFAState>> next : nexts.entrySet()) {
                Range range = next.getKey();
                StateContainer<DFAState> destState = next.getValue();
                State q = statesMap.get(destState);
                int destId;
                if (q == null) {
                    if (fstStates.size() < stateLimit) {
                        //normal
                        q = makeState();
                        statesMap.put(destState, q);
                        allTrans.put(q.getId(), new TreeSet<>());
                        stack.push(new DeterministicEntry(destState, q));
                        destId = q.getId();
                    } else {
                        Integer old = pendingStates.get(destState);
                        if (old != null) {
                            destId = old;
                        } else {
                            int order = pendingStates.size();
                            pendingStates.put(destState, order);
                            destId = order + stateLimit;
                        }
                    }
                } else {
                    destId = q.getId();
                }
                trans.add(new Transition(range.getStart(), range.getEnd(), destId));
            }
        } while (!stack.isEmpty());

        allTrans.forEach((k, v) -> {
            if (!v.isEmpty()) {
                fstStates.get(k).setTrans(v);
            }
        });

        //还需要保留的单dfa 状态 id
        TreeSet<DFAState> leftStates;
        int stateCount;
        if (pendingStates.isEmpty()) {
            leftStates = null;
            stateCount = fstStates.size();
        } else {
            stateCount = stateLimit;
            leftStates = new TreeSet<>();
            for (Map.Entry<StateContainer<DFAState>, Integer> item : pendingStates.entrySet()) {
                DFAState[] srcStates = item.getKey().getStates();
                for (DFAState s : srcStates) {
                    walkState(s, leftStates);
                }
            }
        }

        Automaton automaton = new Automaton();
        automaton.setDfaStateCount(stateCount);
        automaton.setDfaTables(makeDfaTables(stateCount));
        automaton.setDfaPendingStates(makeDfaPendingStates(pendingStates));
        if (leftStates != null && !leftStates.isEmpty()) {
            makeSingleDfaTables(automaton, leftStates);
        }
        return automaton;
    }

    private void walkState(DFAState initial, Set<DFAState> leftStates) {
        LinkedList<DFAState> workList = new LinkedList<>();
        workList.add(initial);
        leftStates.add(initial);
        while (!workList.isEmpty()) {
            DFAState s = workList.pollFirst();
            List<TransitionT<DFAState>> trans = s.getTransitions();
            if (trans != null) {
                for (TransitionT<DFAState> tr : trans) {
                    DFAState to = tr.getTo();
                    if (leftStates.add(to)) {
                        workList.add(to);
                    }
                }
            }
        }
    }

    private State makeState() {
        int id = fstStates.size();
        State state = new State();
        state.setId(id);
        fstStates.add(state);
        return state;
    }

    private StateContainer makeInitStates() {
        return new StateContainer(rootStates, false);
    }


    private int[][] makeDfaTables(int stateCount) {
        int[][] tables = new int[stateCount][];
        for (int i = 0; i < stateCount; i++) {
            State state = fstStates.get(i);
            Set<Integer> ids = state.getFinalIds();
            Set<Transition> trans = state.getTrans();
            int trsCount;
            if (ids != null) {
                trsCount = 1 + ids.size();
            } else {
                trsCount = 1;
            }
            if (trans != null) {
                trsCount += trans.size() * 3;
            }
            int[] trs = tables[i] = new int[trsCount];
            int index = 0;
            if (ids != null) {
                trs[index++] = ids.size();
                for (int id : ids) {
                    trs[index++] = id;
                }
            } else {
                trs[index++] = 0;
            }
            if (trans != null) {
                for (Transition tr : trans) {
                    trs[index++] = tr.getMin();
                    trs[index++] = tr.getMax();
                    trs[index++] = tr.getDest();
                }
            }
        }
        return tables;
    }

    private int[][] makeDfaPendingStates(Map<StateContainer<DFAState>, Integer> pendingStates) {
        if (pendingStates.isEmpty()) {
            return null;
        }
        int[][] tables = new int[pendingStates.size()][];
        for (Map.Entry<StateContainer<DFAState>, Integer> entry : pendingStates.entrySet()) {
            DFAState[] states = entry.getKey().getStates();
            int[] ids = tables[entry.getValue()] = new int[states.length];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = states[i].getId();
            }
        }
        return tables;
    }

    private void makeSingleDfaTables(Automaton automaton, TreeSet<DFAState> leftStates) {
        int count = leftStates.size();
        int[][] tables = new int[count][];
        int idIndex = 0;
        for (DFAState s : leftStates) {
            List<TransitionT<DFAState>> trans = s.getTransitions();
            int dataCount = (trans == null) ? 2 : (2 + trans.size() * 3);
            int[] trs = tables[idIndex++] = new int[dataCount];
            trs[0] = s.getId();
            int rid = s.getAcceptId();
            trs[1] = rid;
            if (trans != null) {
                int index = 2;
                for (TransitionT tr : trans) {
                    Range r = tr.getRange();
                    trs[index++] = r.getStart();
                    trs[index++] = r.getEnd();
                    trs[index++] = tr.getTo().getId();
                }
            }
        }
        automaton.setTables(tables);
    }
}
