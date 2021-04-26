package org.text.algorithm.automa;

class DeterministicEntry {
    protected DeterminizeState m;
    protected Dfa.DfaState s;

    public DeterministicEntry(DeterminizeState m, Dfa.DfaState s) {
        this.m = m;
        this.s = s;
    }
}
