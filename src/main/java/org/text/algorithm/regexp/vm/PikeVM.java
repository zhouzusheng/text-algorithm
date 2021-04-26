/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package org.text.algorithm.regexp.vm;

import org.text.algorithm.regexp.SimpleSerializable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import java.util.function.Function;
import java.util.regex.Matcher;

/**
 * A minimal implementation of a regular expression engine.
 *
 * @author Johannes Schindelin
 */
public class PikeVM implements PikeVMOpcodes, Serializable, SimpleSerializable {
    private static final long serialVersionUID = -1L;

    private int[] program;
    private int groupCount;
    private int offsetsCount;
    /*
     * For find(), we do not want to anchor the match at the start offset. Our
     * compiler allows this by prefixing the code with an implicit '(?:.*?)'. For
     * regular matches() calls, we want to skip that code and start at {@code
     * findPrefixLength} instead.
     */
    private int findPrefixLength;
    private CharacterMatcher[] classes;
    private PikeVM[] lookarounds;
    private final static transient CharacterMatcher wordCharacter =
            CharacterMatcher.parse("\\w");

    public interface Result {
        void set(int[] start, int[] end);
    }

    public PikeVM() {

    }

    public PikeVM(int[] program, int findPrefixLength, int groupCount,
                  CharacterMatcher[] classes, PikeVM[] lookarounds) {
        this.program = program;
        this.findPrefixLength = findPrefixLength;
        this.groupCount = groupCount;
        offsetsCount = 2 * groupCount + 2;
        this.classes = classes;
        this.lookarounds = lookarounds;
    }

    public int[] getProgram() {
        return program;
    }

    public int getGroupCount() {
        return groupCount;
    }

    public int getOffsetsCount() {
        return offsetsCount;
    }

    public CharacterMatcher[] getClasses() {
        return classes;
    }

    /**
     * Executes the Pike VM defined by the program.
     * <p>
     * The idea is to execute threads in parallel, at each step executing them
     * from the highest priority thread to the lowest one. In contrast to most
     * regular expression engines, the Thompson/Pike one gets away with linear
     * complexity because the string is matched from left to right, at each step
     * executing a number of threads bounded by the length of the program: if two
     * threads would execute at the same instruction pointer of the program, we
     * need only consider the higher-priority one.
     * </p>
     * <p>
     * This implementation is based on the description of <a
     * href="http://swtch.com/%7Ersc/regexp/regexp2.html">Russ Cox</a>.
     * </p>
     *
     * @param characters  the {@link String} to match
     * @param start       the start offset where to match
     * @param anchorStart whether the match must start at {@code start}
     * @param anchorEnd   whether the match must end at {@code end}
     * @param result      the {@link Matcher} to store the groups' offsets in, if successful
     * @return whether a match was found
     */
    public boolean matches(char[] characters, int start, int end,
                           boolean anchorStart, boolean anchorEnd, Function<int[], Integer> hookBack, Result result) {
        PikeThread.ThreadQueue current = new PikeThread.ThreadQueue(this);
        PikeThread.ThreadQueue next = new PikeThread.ThreadQueue(this);

        // initialize the first thread
        int startPC = anchorStart ? findPrefixLength : 0;
        PikeThread.ThreadQueue queued = new PikeThread.ThreadQueue(this, startPC);

        PikeThread.HookContainerThread hook = new PikeThread.HookContainerThread(characters, hookBack);

        boolean foundMatch = false;
        int step = end > start ? +1 : -1;
        for (int i = start; i != end + step; i += step) {
            if (queued.isEmpty()) {
                // no threads left
                return foundMatch;
            }
            char c = i != end ? characters[i] : 0;
            int pc = -1;
            for (; ; ) {
                pc = current.next(pc);
                if (pc < 0) {
                    pc = queued.queueOneImmediately(current);
                }
                if (pc < 0) {
                    break;
                }

                // pc == program.length is a match!
                if (pc == program.length) {
                    if (anchorEnd && i != end) {
                        continue;
                    }
                    if (result == null) {
                        // only interested in a match, no need to go on
                        return true;
                    }
                    current.setResult(result);

                    // now that we found a match, even higher-priority matches must match
                    // at the same start offset
                    if (!anchorStart) {
                        next.mustStartMatchAt(current.startOffset(pc));
                    }
                    foundMatch = true;
                    break;
                }

                int opcode = program[pc];
                switch (opcode) {
                    case DOT:
                        if (c != '\0' && c != '\r' && c != '\n') {
                            current.queueNext(pc, pc + 1, next);
                        }
                        break;
                    case DOTALL:
                        current.queueNext(pc, pc + 1, next);
                        break;
                    case WORD_BOUNDARY:
                    case NON_WORD_BOUNDARY: {
                        int i2 = i - step;
                        int c2 = i2 < 0 || i2 >= characters.length ? -1 : characters[i2];
                        switch (opcode) {
                            case WORD_BOUNDARY:
                                if ((c2 < 0 || !wordCharacter.matches((char) c2))) {
                                    if (wordCharacter.matches(c)) {
                                        current.queueImmediately(pc, pc + 1, false);
                                    }
                                } else if (i >= 0 && i < characters.length &&
                                        !wordCharacter.matches(c)) {
                                    current.queueImmediately(pc, pc + 1, false);
                                }
                                break;
                            case NON_WORD_BOUNDARY:
                                if ((c2 < 0 || !wordCharacter.matches((char) c2))) {
                                    if (i >= 0 && i < characters.length &&
                                            !wordCharacter.matches(c)) {
                                        current.queueImmediately(pc, pc + 1, false);
                                    }
                                } else if (wordCharacter.matches(c)) {
                                    current.queueImmediately(pc, pc + 1, false);
                                }
                                break;
                        }
                        break;
                    }
                    case LINE_START:
                        if (i == 0 || (anchorStart && i == start)) {
                            current.queueImmediately(pc, pc + 1, false);
                        }
                        break;
                    case LINE_END:
                        if (i == characters.length || (anchorEnd && i == end)) {
                            current.queueImmediately(pc, pc + 1, false);
                        }
                        break;
                    case CHARACTER_CLASS:
                        if (classes[program[pc + 1]].matches(c)) {
                            current.queueNext(pc, pc + 2, next);
                        }
                        break;
                    case LOOKAHEAD:
                        if (lookarounds[program[pc + 1]].matches(characters,
                                i, characters.length, true, false, null, null)) {
                            current.queueImmediately(pc, pc + 2, false);
                        }
                        break;
                    case LOOKBEHIND:
                        if (lookarounds[program[pc + 1]].matches(characters,
                                i - 1, -1, true, false, null, null)) {
                            current.queueImmediately(pc, pc + 2, false);
                        }
                        break;
                    case NEGATIVE_LOOKAHEAD:
                        if (!lookarounds[program[pc + 1]].matches(characters,
                                i, characters.length, true, false, null, null)) {
                            current.queueImmediately(pc, pc + 2, false);
                        }
                        break;
                    case NEGATIVE_LOOKBEHIND:
                        if (!lookarounds[program[pc + 1]].matches(characters,
                                i - 1, -1, true, false, null, null)) {
                            current.queueImmediately(pc, pc + 2, false);
                        }
                        break;
                    case HOOK_BEGIN: {
                        int left = program[pc + 1];
                        int right = program[pc + 3];
                        hook.addHook(pc, pc + 2, i, left, right);
                        current.queueImmediately(pc, pc + 2, false);
                        break;
                    }

                    case HOOK_PROC: {
                        //int value = program[pc + 1];
                        hook.hookProc(pc, pc + 2, i, current, next);
                        break;
                    }

                    case INT_RANGE: {
                        int min = program[pc + 1];
                        int max = program[pc + 3];
                        hook.addIntRange(pc, pc + 2, i, min, max);
                        current.queueImmediately(pc, pc + 2, false);
                        break;
                    }
                    case CHECK_RANGE: {
                        //int value = program[pc + 1];
                        hook.hookProc(pc, pc + 2, i, current, next);
                        break;
                    }

                    /* immediate opcodes, i.e. thread continues within the same step */
                    case SAVE_OFFSET:
                        if (result != null) {
                            int index = program[pc + 1];
                            current.saveOffset(pc, index, i);
                        }
                        current.queueImmediately(pc, pc + 2, false);
                        break;
                    case SPLIT:
                        current.queueImmediately(pc, program[pc + 1], true);
                        current.queueImmediately(pc, pc + 2, false);
                        break;
                    case SPLIT_JMP:
                        current.queueImmediately(pc, pc + 2, true);
                        current.queueImmediately(pc, program[pc + 1], false);
                        break;
                    case JMP:
                        current.queueImmediately(pc, program[pc + 1], false);
                        break;
                    default:
                        if (program[pc] >= 0 && program[pc] <= 0xffff) {
                            if (c == (char) program[pc]) {
                                current.queueNext(pc, pc + 1, next);
                            }
                            break;
                        }
                        throw new RuntimeException("Invalid opcode: " + opcode
                                + " at pc " + pc);
                }
            }
            // clean linked thread list (and states)
            current.clean();

            // prepare for next step
            PikeThread.ThreadQueue swap = queued;
            queued = next;
            next = swap;
        }
        return foundMatch;
    }

    /**
     * Determines whether this machine recognizes a pattern without special
     * operators.
     * <p>
     *
     * @return the string to match, or null if the machine recognizes a
     * non-trivial pattern
     */
    public String isPlainString() {
        // we expect the machine to start with the find preamble and SAVE_OFFSET 0
        // end with SAVE_OFFSET 1
        int start = findPrefixLength;
        if (start + 1 < program.length &&
                program[start] == SAVE_OFFSET && program[start + 1] == 0) {
            start += 2;
        }
        int end = program.length;
        if (end > start + 1 &&
                program[end - 2] == SAVE_OFFSET && program[end - 1] == 1) {
            end -= 2;
        }
        for (int i = start; i < end; ++i) {
            if (program[i] < 0) {
                return null;
            }
        }
        char[] array = new char[end - start];
        for (int i = start; i < end; ++i) {
            array[i - start] = (char) program[i];
        }
        return new String(array);
    }

    private static int length(int opcode) {
        return opcode <= SINGLE_ARG_START && opcode >= SINGLE_ARG_END ? 2 : 1;
    }

    private static boolean isJump(int opcode) {
        return opcode <= SPLIT && opcode >= JMP;
    }

    /**
     * Reverses the program (effectively matching the reverse pattern).
     * <p>
     * It is a well-known fact that any regular expression can be reordered
     * trivially into an equivalent regular expression to be applied in backward
     * direction (coming in real handy for look-behind expressions).
     * </p>
     * <p>
     * Example: instead of matching the sequence "aaaabb" with the pattern "a+b+",
     * we can match the reverse sequence "bbaaaa" with the pattern "b+a+".
     * </p>
     * <p>
     * One caveat: while the reverse pattern is equivalent in the sense that it
     * matches if, and only if, the original pattern matches the forward
     * direction, the same is not true for submatches. Consider the input "a" and
     * the pattern "(a?)a?": when matching in forward direction the captured group
     * is "a", while the backward direction will yield the empty string. For that
     * reason, Java dictates that capturing groups in look-behind patterns are
     * ignored.
     * </p>
     */
    public void reverse() {
        reverse(findPrefixLength, program.length);
    }

    /**
     * Reverses a specific part of the program (to match in reverse direction).
     * <p>
     * This is the work-horse of {@link #reverse()}.
     * </p>
     * <p>
     * To visualize the process of reversing a program, let's look at it as a
     * directed graph (each jump is represented by an "<tt>X</tt>
     * ", non-jumping steps are represented by a "<tt>o</tt>"s, arrows show the
     * direction of the flow, <code>SPLIT</code>s spawn two arrows):
     *
     * <pre>
     * o -> X -> X -> o -> X    o -> o
     * ^    |     \         \___^____^
     *  \__/       \____________|
     * </pre>
     * <p>
     * The concept of reversing the program is easiest explained as following: if
     * we insert auxiliary nodes "<tt>Y</tt>" for jump targets, the graph looks
     * like this instead:
     *
     * <pre>
     * Y -> o -> X -> X -> o -> X    Y -> o -> Y -> o
     * ^         |     \         \___^_________^
     *  \_______/       \____________|
     * </pre>
     * <p>
     * It is now obvious that reversing the program is equivalent to reversing all
     * arrows, simply deleting all <tt>X</tt>s and substituting each <tt>Y</tt>
     * with a jump. Note that the reverse program will have the same number of
     * <tt>JMP</tt>, but they will not be associated with the same arrows!:
     *
     * <pre>
     * X <- o <- o    X <- o <- X <- o
     * |    ^    ^____|________/
     *  \__/ \_______/
     * </pre>
     *
     * </p>
     *
     * @param start start reversing the program with this instruction
     * @param end   stop reversing at this instruction (this must be either an index
     *              aligned exactly with an instruction, or exactly
     *              {@code program.length}.
     */
    private void reverse(int start, int end) {
        // Pass 1: build the list of jump targets
        int[] newJumps = new int[end + 1];
        boolean[] brokenArrows = new boolean[end + 1];
        for (int pc = start; pc < end; pc += length(program[pc])) {
            if (isJump(program[pc])) {
                int target = program[pc + 1];
                newJumps[pc + 1] = newJumps[target];
                newJumps[target] = pc + 1;
                if (program[pc] == JMP) {
                    brokenArrows[pc + 2] = true;
                }
            }
        }

        // Pass 2: determine mapped program counters
        int[] mapping = new int[end];
        for (int pc = start, mappedPC = end; mappedPC > 0
                && pc < end; pc += length(program[pc])) {
            for (int jump = newJumps[pc]; jump > 0; jump = newJumps[jump]) {
                mappedPC -= 2;
            }
            if (!isJump(program[pc])) {
                mappedPC -= length(program[pc]);
            }
            mapping[pc] = mappedPC;
        }

        // Pass 3: write the new program
        int[] reverse = new int[end];
        for (int pc = start, mappedPC = end; mappedPC > 0;
             pc += length(program[pc])) {
            boolean brokenArrow = brokenArrows[pc];
            for (int jump = newJumps[pc]; jump > 0; jump = newJumps[jump]) {
                reverse[--mappedPC] = mapping[jump - 1];
                if (brokenArrow) {
                    reverse[--mappedPC] = JMP;
                    brokenArrow = false;
                } else {
                    reverse[--mappedPC] =
                            program[jump - 1] == SPLIT_JMP ? SPLIT_JMP : SPLIT;
                }
            }
            if (pc == end) {
                break;
            }
            if (!isJump(program[pc])) {
                for (int i = length(program[pc]); i-- > 0; ) {
                    reverse[--mappedPC] = program[pc + i];
                }
            }
        }
        System.arraycopy(reverse, start, program, start, end - start);
    }

    public void save(DataOutput out) throws IOException {
        SimpleSerializable.writeOneDimTables(out, program);
        out.writeInt(groupCount);
        out.writeInt(offsetsCount);
        out.writeInt(findPrefixLength);
        SimpleSerializable.saveObjects(out, classes);
        SimpleSerializable.saveObjects(out, lookarounds);
    }

    @Override
    public void load(DataInput input) throws IOException {
        program = SimpleSerializable.loadOneDimTables(input);
        groupCount = input.readInt();
        offsetsCount = input.readInt();
        findPrefixLength = input.readInt();
        classes = SimpleSerializable.loadObjects(CharacterMatcher.class, input, () -> {
            try {
                int type = input.readInt();
                return CharacterParser.newClass(type);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        lookarounds = SimpleSerializable.loadObjects(PikeVM.class, input, () -> new PikeVM());
    }
}
