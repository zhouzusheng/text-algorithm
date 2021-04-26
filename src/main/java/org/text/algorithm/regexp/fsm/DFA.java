/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp.fsm;

public class DFA {
    private int dfaId;
    private int root;
    private int baseId;
    private DFAState[] states;

    public DFA(int dfaId, int root, int baseId, DFAState[] states) {
        this.dfaId = dfaId;
        this.root = root;
        this.baseId = baseId;
        this.states = states;
    }

    public int getDfaId() {
        return dfaId;
    }

    public void setDfaId(int dfaId) {
        this.dfaId = dfaId;
    }

    public int getRoot() {
        return root;
    }

    public int getBaseId() {
        return baseId;
    }

    public DFAState[] getStates() {
        return states;
    }
}
