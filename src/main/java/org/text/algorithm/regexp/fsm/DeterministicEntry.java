/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp.fsm;

public class DeterministicEntry<T extends StateT<T>, U> {
    public final StateContainer<T> m;
    public final U s;

    public DeterministicEntry(StateContainer<T> m, U s) {
        this.m = m;
        this.s = s;
    }
}
