/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp.fsm;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DFAState implements StateT<DFAState> {
    private int id;
    private int acceptId;
    private List<TransitionT<DFAState>> transitions = new ArrayList<>();

    public DFAState() {
        acceptId = -1;
    }

    public DFAState(int id) {
        this.id = id;
        acceptId = -1;
    }

    public int getId() {
        return id;
    }

    public int getAcceptId() {
        return acceptId;
    }

    public void setAcceptId(int acceptId) {
        this.acceptId = acceptId;
    }

    @Override
    public boolean isAccepted() {
        return acceptId != -1;
    }

    public List<TransitionT<DFAState>> getTransitions() {
        return transitions;
    }

    public void setTransitions(List<TransitionT<DFAState>> transitions) {
        this.transitions = transitions;
    }

    public void addTransition(Range range, DFAState targetDfa) {
        transitions.add(new TransitionT(range, targetDfa));
    }

    public void removeTransition(Range range, int checkId) {
        for (int i = 0; i < transitions.size(); i++) {
            TransitionT<DFAState> t = transitions.get(i);
            if (t.getRange().equals(range)) {
                if (t.getTo().getId() == checkId) {
                    transitions.remove(i);
                }
                break;
            }
        }
    }

    @Override
    public int compareTo(DFAState o) {
        return COMPARATOR.compare(this, o);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DFAState dfa = (DFAState) o;
        return id == dfa.id;
    }

    public static Set<DFAState> walkState(DFAState initial, Consumer<DFAState> stateConsumer,
                                          BiConsumer<DFAState, TransitionT> transConsumer) {

        Set<DFAState> visited = new HashSet<>();
        LinkedList<DFAState> workList = new LinkedList<>();
        workList.add(initial);
        visited.add(initial);
        while (workList.size() > 0) {
            DFAState s = workList.removeFirst();
            if (stateConsumer != null) {
                stateConsumer.accept(s);
            }
            List<TransitionT<DFAState>> tr = s.getTransitions();
            if (tr != null) {
                for (TransitionT<DFAState> t : tr) {
                    if (transConsumer != null) {
                        transConsumer.accept(s, t);
                    }
                    if (!visited.contains(t.getTo())) {
                        visited.add(t.getTo());
                        workList.add(t.getTo());
                    }
                }
            }
        }
        return visited;

    }


}
