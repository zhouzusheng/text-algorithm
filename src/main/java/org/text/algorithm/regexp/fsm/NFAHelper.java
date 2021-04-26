/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp.fsm;

import java.util.*;

public class NFAHelper {
    NFAState[] nfaStates;
    int start;

    int dfaId;
    int dfaRootId;
    List<DFAState> dfaStates;

    int nextId;
    int dfaBaseId;

    public NFAHelper() {

    }

    public NFAHelper(NFAState[] nfaStates, int start) {
        resetTo(nfaStates, start);
    }

    public void resetTo(NFAState[] nfaStates, int start) {
        this.nfaStates = nfaStates;
        this.start = start;
        this.dfaStates = null;
        this.dfaRootId = 0;
        this.dfaBaseId = 0;
    }

    public void makeDfa(int dfaId, int baseId) {
        this.dfaId = dfaId;
        this.dfaBaseId = baseId;

        nextId = 0;

        Map<NFAState, Set<NFAState>> closureMap = new HashMap<>();
        calculateClosure(closureMap, nfaStates);

        Map<StateContainer<NFAState>, DFAState> statesMap = new HashMap<>();

        dfaStates = new ArrayList<>();

        Set<NFAState> initStates = closureMap.get(nfaStates[start]);

        DFAState state = makeState();
        if (hasAcceptingState(initStates)) {
            state.setAcceptId(dfaId);
        }

        StateContainer<NFAState> wrapper = new StateContainer<>(initStates, false, true);
        statesMap.put(wrapper, state);

        DeterministicEntry<NFAState, DFAState> entry = new DeterministicEntry(wrapper, state);
        LinkedList<DeterministicEntry> pending = new LinkedList<>();
        pending.add(entry);

        while (!pending.isEmpty()) {
            entry = pending.pollFirst();
            Map<Range, Collection<NFAState>> nexts = entry.m.steps();
            for (Map.Entry<Range, Collection<NFAState>> next : nexts.entrySet()) {

                Set<NFAState> moves = makeClosure(closureMap, next.getValue());
                wrapper = new StateContainer(moves, false, true);
                DFAState targetDfa = statesMap.get(wrapper);
                if (targetDfa == null) {
                    targetDfa = makeState();
                    if (hasAcceptingState(moves)) {
                        targetDfa.setAcceptId(dfaId);
                    }
                    statesMap.put(wrapper, targetDfa);
                    DeterministicEntry entry2 = new DeterministicEntry(wrapper, targetDfa);
                    pending.add(entry2);
                }
                entry.s.addTransition(next.getKey(), targetDfa);
            }
        }
        statesMap.clear();
        closureMap.clear();

        statesMap = null;
        closureMap = null;

        MinimizeDFA minimizeDFA = new MinimizeDFA(baseId, 0, dfaStates);
        minimizeDFA.minimize();

        dfaStates = minimizeDFA.newStates;
        dfaRootId = minimizeDFA.newRootId;
    }

    public DFA getDFA() {
        return new DFA(dfaId, dfaRootId, dfaBaseId, dfaStates.toArray(new DFAState[dfaStates.size()]));
    }

    protected Set<NFAState> makeClosure(Map<NFAState, Set<NFAState>> closureMap, Collection<NFAState> nfaStates) {
        Set<NFAState> results = new HashSet<>();
        for (NFAState s : nfaStates) {
            results.addAll(closureMap.get(s));
        }
        return results;
    }

    private void calculateClosure(Map<NFAState, Set<NFAState>> map, NFAState[] nfaStateList) {
        for (NFAState state : nfaStateList) {
            if (!map.containsKey(state)) {
                Set<NFAState> closure = new HashSet<>();
                dfsClosure(map, state, closure);
            }
        }
    }

    private void dfsClosure(Map<NFAState, Set<NFAState>> map, NFAState state, Set<NFAState> closure) {
        closure.add(state);
        for (NFAState next : state.getDirectTable()) {
            Set<NFAState> sub = map.get(next);
            if (sub == null) {
                sub = new HashSet<>();
                dfsClosure(map, next, sub);
            }
            closure.addAll(sub);
        }
        map.put(state, closure);
    }

    private DFAState makeState() {
        DFAState s = new DFAState(nextId++);
        dfaStates.add(s);
        return s;
    }

    private boolean hasAcceptingState(Collection<NFAState> indices) {
        for (NFAState s : indices) {
            if (s.isAccepted()) {
                return true;
            }
        }
        return false;
    }

}
