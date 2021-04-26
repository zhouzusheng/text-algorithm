/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp.fsm;

import java.util.Comparator;
import java.util.List;

public interface StateT<T extends Idable> extends Comparable<T>, Idable {
    Comparator<StateT> COMPARATOR = Comparator.comparingInt(StateT::getId);

    List<TransitionT<T>> getTransitions();

    boolean isAccepted();

    int getAcceptId();
}
