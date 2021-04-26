/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp.fsm.multi;

import java.util.Set;

public class State {
    private int id;
    private Set<Integer> finalIds;
    private Set<Transition> trans;

    public int getId() {
        return id;
    }

    public Set<Integer> getFinalIds() {
        return finalIds;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Set<Transition> getTrans() {
        return trans;
    }

    public void setTrans(Set<Transition> trans) {
        this.trans = trans;
    }

    public void addFinalIds(Set<Integer> ids) {
        if (this.finalIds == null) {
            this.finalIds = ids;
        } else {
            this.finalIds.addAll(ids);
        }
    }

    public void setFinalIds(Set<Integer> finalIds) {
        this.finalIds = finalIds;
    }
}
