/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package org.text.algorithm.regexp.vm;

import org.text.algorithm.regexp.SimpleSerializable;
import org.text.algorithm.regexp.fsm.NFAState;

import java.io.Serializable;

/**
 * A class to match classes of characters.
 * <p>
 * This class is intended to be the working horse behind character classes
 * such as {@code [a-z]}.
 * </p>
 *
 * @author Johannes Schindelin
 */
public interface CharacterMatcher extends Serializable, SimpleSerializable {
    int getType();

    void addTrans(NFAState s1, NFAState s2);

    boolean matches(int c);

    default void postFix() {
    }

    static CharacterMatcher parse(String description) {
        return parse(description.toCharArray());
    }

    static CharacterMatcher parse(char[] description) {
        CharacterParser parser = new CharacterParser(description);
        CharacterMatcher result = parser.parseClass();
        if (parser.getEndOffset() != description.length) {
            throw new RuntimeException("Short character class @"
                    + parser.getEndOffset() + ": " + new String(description));
        }
        return result;
    }


}
