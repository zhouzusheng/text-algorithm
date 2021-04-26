/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp.fsm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NFAState implements StateT<NFAState> {
    private int id;
    private boolean accepted;

    private Set<NFAState> directTable;
    private List<TransitionT<NFAState>> transitions;

    public NFAState(int id) {
        directTable = new HashSet<>();
        transitions = new ArrayList<>();
        this.id = id;
    }

    @Override
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    @Override
    public int getAcceptId() {
        throw new IllegalStateException();
    }

    public List<TransitionT<NFAState>> getTransitions() {
        return transitions;
    }

    public void setTransitions(List<TransitionT<NFAState>> transitions) {
        this.transitions = transitions;
    }

    public void transitionState(Range range, NFAState state) {
        transitions.add(new TransitionT<>(range, state));
    }

    public void directState(NFAState state) {
        directTable.add(state);
    }

    public Set<NFAState> getDirectTable() {
        return directTable;
    }

    public void setDirectTable(Set<NFAState> directTable) {
        this.directTable = directTable;
    }

    @Override
    public int compareTo(NFAState o) {
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
        NFAState nfa = (NFAState) o;
        return id == nfa.id;
    }
}
