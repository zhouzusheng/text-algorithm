/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package org.text.algorithm.regexp.vm;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Predicate;

/**
 * Compiles regular expressions into {@link PikeVM}s.
 *
 * regexp syntax:
 *  . ? * ^ $ | ( ) {n,m} [abcd] [a-b]
 *  \b  word bound
 *  \B  non word bound
 *  \p  unicode property category
 *  \P  negative unicode property category
 *  \a 0x0007;
 *  \e 0x001B;
 *  \f 0x000C;
 *  \n 0x000A;
 *  \r 0x000D;
 *  \t 0x0009;
 *  \\  \
 *  \.  .
 *  \* *
 *  \+ +
 *  \? ?
 *  \| |
 *  \[ [
 *  \] ]
 * \{ {
 * \} }
 * \( (
 * \) )
 * \^ ^
 * \$ $
 * \xab
 * \u1234
 *
 *  \i{start, end} int range
 *  \h{pa1,pa2}  hook with pa1 and pa2
 *  (?:exp)  non capture group
 *  (?=exp) lookup head
 *  (?!exp) negative lookup head
 *  (?<=exp) lookup behind
 *  (?<!exp) negative lookup behind
 *
 * <start,end> int range, same as \i{start, end}
 * <abc>  a regexp named abc, caller must provide ExpFactory
 *      if abc = all, then <abc> is all regexp excepted name start with _
 *      if abc = _all, then <abc> is all regexp
 *
 * @author Johannes Schindelin
 */
public class PikeCompiler implements PikeVMOpcodes {
    private final static CharacterMatcher regularCharacter =
            CharacterMatcher.parse("[^\\\\.*+?|\\[\\]{}()^$]");

    public static interface ExpFactory {

        /**
         * lookup a regexp named by name
         * @param name
         * @return
         */
        Object lookupExp(String name);

        /**
         * lookup  regexp list
         * @param predicate
         * @return
         */
        List<Object> lookupExpSet(Predicate<String> predicate);

        void putExp(String name, Object exp);
    }

    public static class SimpleBuilder {
        PikeCompiler compiler = new PikeCompiler();

        public void start() {
            compiler.start(false);
        }

        public PikeVM complete() {
            return compiler.makeVm();
        }

        public void beginGroup(boolean capture) {
            compiler.beginGroup(capture);
        }

        public void endGroup() {
            compiler.endGroup();
        }

        public void addString(String word) {
            compiler.addString(word);
        }

        public void addAnyChar() {
            compiler.anyChar();
        }

        public void addHook(int left, int right) {
            compiler.addHook(left, right);
        }

        public void addOr() {
            compiler.or();
        }

        public void addRepeat(int min, int max, boolean greedy) {
            compiler.repeat(min, max, greedy);
        }
    }

    private static class Output {
        private int[] program;
        private int offset;
        private int groupCount = -1;
        private int findPreambleSize;
        private ArrayList<CharacterMatcher> classes;
        private ArrayList<PikeVM> lookarounds;

        public Output(Expression expr) {
            // try-run to determine the code size
            expr.writeCode(this);
            program = new int[offset];
            offset = 0;
            groupCount = -1;
            classes = new ArrayList<>();
            lookarounds = new ArrayList<>();
            // write it out!
            expr.writeCode(this);
        }

        public void add(int opcode) {
            if (program != null) {
                program[offset] = opcode;
            }
            offset++;
        }

        public int markJump() {
            return offset++;
        }

        public void setJump(int mark) {
            if (program != null) {
                program[mark] = offset;
            }
        }

        public void markFindPreambleEnd() {
            findPreambleSize = offset;
        }

        public PikeVM toVM() {
            CharacterMatcher[] classes = new CharacterMatcher[this.classes.size()];
            this.classes.toArray(classes);
            PikeVM[] lookarounds = new PikeVM[this.lookarounds.size()];
            this.lookarounds.toArray(lookarounds);
            return new PikeVM(program, findPreambleSize, groupCount, classes,
                    lookarounds);
        }

        public int addClass(CharacterMatcher characterClass) {
            if (program == null) {
                return -1;
            }
            int result = classes.size();
            classes.add(characterClass);
            return result;
        }

        public int addLookaround(PikeVM lookaround) {
            if (program == null) {
                return -1;
            }
            int result = lookarounds.size();
            lookarounds.add(lookaround);
            return result;
        }
    }

    private abstract class Expression {
        protected abstract void writeCode(Output output);
    }

    private class CharacterRange extends Expression {
        private final CharacterMatcher characterClass;

        public CharacterRange(CharacterMatcher characterClass) {
            this.characterClass = characterClass;
        }

        protected void writeCode(Output output) {
            output.add(CHARACTER_CLASS);
            output.add(output.addClass(characterClass));
        }

        public String toString() {
            return characterClass.toString();
        }
    }


    private class IntRange extends Expression {
        protected int start;
        protected int end;

        public IntRange() {

        }

        public IntRange(int start, int end) {
            this.start = start;// & 0x7FFF;
            this.end = end;// & 0x7FFF;
        }

        @Override
        protected void writeCode(Output output) {
            if (start == end) {
                String value = Integer.toString(start);
                for (int i = 0; i < value.length(); i++) {
                    char ch = value.charAt(i);
                    output.add(ch);
                }
            } else {
                if (start > end) {
                    int tmp = start;
                    start = end;
                    end = tmp;
                }
                output.add(INT_RANGE);
                output.add(start);
                output.add(CHECK_RANGE);
                output.add(end);
            }
        }

        public String toString() {
            return String.format("<%d-%d>", start, end);
        }
    }

    public class Hook extends IntRange {

        public Hook() {
        }

        public Hook(int start, int end) {
            super(start, end);
        }

        @Override
        protected void writeCode(Output output) {
            output.add(HOOK_BEGIN);
            output.add(start);
            output.add(HOOK_PROC);
            output.add(end);
        }

        public String toString() {
            return String.format("\\h{%d,%d}", start, end);
        }
    }

    private class Repeat extends Expression {
        private Expression expr;
        private int minCount, maxCount;
        private boolean greedy;

        public Repeat(Expression expr, int minCount, int maxCount, boolean greedy) {
            if (minCount < 0) {
                throw new RuntimeException("Unexpected min count: " + minCount);
            }
            if (maxCount != -1) {
                if (maxCount == 0) {
                    throw new RuntimeException("Unexpected max count: " + maxCount);
                }
                if (minCount > maxCount) {
                    throw new RuntimeException("Unexpected range: " + minCount + ", " + maxCount);
                }
            }
            this.expr = expr;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.greedy = greedy;
        }

        protected void writeCode(Output output) {
            int start = output.offset;
            int splitJmp = greedy ? SPLIT_JMP : SPLIT;
            int split = greedy ? SPLIT : SPLIT_JMP;
            for (int i = 1; i < minCount; ++i) {
                expr.writeCode(output);
            }
            if (maxCount == -1) {
                if (minCount > 0) {
                    int jump = output.offset;
                    expr.writeCode(output);
                    output.add(splitJmp);
                    output.add(jump);
                } else {
                    output.add(split);
                    int jump = output.markJump();
                    expr.writeCode(output);
                    output.add(splitJmp);
                    output.add(start + 2);
                    output.setJump(jump);
                }
            } else {
                if (minCount > 0) {
                    expr.writeCode(output);
                }
                if (maxCount > minCount) {
                    int[] jumps = new int[maxCount - minCount];
                    for (int i = 0; i < jumps.length; ++i) {
                        output.add(split);
                        jumps[i] = output.markJump();
                        expr.writeCode(output);
                    }
                    for (int jump : jumps) {
                        output.setJump(jump);
                    }
                }
            }
        }

        public String toString() {
            String qualifier = greedy ? "" : "?";
            if (minCount == 0 && maxCount < 2) {
                return expr.toString() + (minCount < 0 ? "*" : "?") + qualifier;
            }
            if (minCount == 1 && maxCount < 0) {
                return expr.toString() + "+" + qualifier;
            }
            return expr.toString() + "{" + minCount + ","
                    + (maxCount < 0 ? "" : "" + maxCount) + "}" + qualifier;
        }
    }

    private class Group extends Expression {
        private final boolean capturing;

        private ArrayList<Expression> list = new ArrayList<Expression>();
        private ArrayList<Group> alternatives;

        public Group(boolean capturing, ArrayList<Expression> initialList) {
            this.capturing = capturing;
            if (initialList != null) {
                list.addAll(initialList);
            }
        }

        private Group(Group other, boolean capturing) {
            this.capturing = capturing;
            list.addAll(other.list);
            if (other.alternatives != null) {
                alternatives = new ArrayList<>(other.alternatives);
            }
        }

        public Group cloneTo(boolean capturing) {
            return new Group(this, capturing);
        }

        public void push(Expression expr) {
            list.add(expr);
        }

        public void push(final int c) {
            push(new Expression() {
                public void writeCode(Output output) {
                    output.add(c);
                }

                public String toString() {
                    if (c >= 0) {
                        return "" + (char) c;
                    }
                    switch (c) {
                        case DOT:
                            return ".";
                        case WORD_BOUNDARY:
                            return "\\b";
                        case NON_WORD_BOUNDARY:
                            return "\\B";
                        case LINE_START:
                            return "^";
                        case LINE_END:
                            return "$";
                        default:
                            throw new RuntimeException("Unhandled opcode: " + c);
                    }
                }
            });
        }

        public void startAlternative() {
            if (alternatives == null) {
                alternatives = new ArrayList<Group>();
            }
            alternatives.add(new Group(false, list));
            list.clear();
        }

        public Expression pop() {
            Expression result = list.remove(list.size() - 1);
            return result;
        }

        protected void writeCode(Output output) {
            int groupIndex = -1;
            if (capturing) {
                groupIndex = ++output.groupCount;
                output.add(SAVE_OFFSET);
                output.add(2 * groupIndex);
            }
            int[] jumps = null;
            if (alternatives != null) {
                jumps = new int[alternatives.size()];
                int i = 0;
                for (Group alternative : alternatives) {
                    output.add(SPLIT);
                    int jump = output.markJump();
                    alternative.writeCode(output);
                    output.add(JMP);
                    jumps[i++] = output.markJump();
                    output.setJump(jump);
                }
            }
            for (Expression expr : list) {
                expr.writeCode(output);
            }
            if (jumps != null) {
                for (int jump : jumps) {
                    output.setJump(jump);
                }
            }
            if (capturing) {
                output.add(SAVE_OFFSET);
                output.add(2 * groupIndex + 1);
            }
        }

        public String toString() {
            StringBuilder builder = new StringBuilder();
            if (alternatives != null || list.size() > 1) {
                builder.append('(');
                if (!capturing) {
                    builder.append("?:");
                }
            }
            if (alternatives != null) {
                for (Group alternative : alternatives) {
                    builder.append(alternative).append('|');
                }
            }
            for (Expression expr : list) {
                builder.append(expr);
            }
            if (alternatives != null || list.size() > 1) {
                builder.append(')');
            }
            return builder.toString();
        }
    }

    private class Lookaround extends Expression {
        private final Group group = new Group(false, null);
        private final boolean forward, negative;

        public Lookaround(boolean forward, boolean negative) {
            this.forward = forward;
            this.negative = negative;
        }

        @Override
        protected void writeCode(Output output) {
            PikeVM vm = new Output(group).toVM();
            if (!forward) {
                vm.reverse();
            }
            output.add(forward ?
                    (negative ? NEGATIVE_LOOKAHEAD : LOOKAHEAD) :
                    (negative ? NEGATIVE_LOOKAHEAD : LOOKBEHIND));
            output.add(output.addLookaround(vm));
        }

        public String toString() {
            String inner = group.toString();
            if (inner.startsWith("(?:")) {
                inner = inner.substring(3);
            } else {
                inner += ")";
            }
            return "(?=" + inner;
        }
    }

    private class Group0 extends Expression {
        private final Group group;
        private final boolean anchor;

        public Group0(boolean anchor) {
            this.anchor = anchor;
            group = new Group(true, null);
        }

        public void writeCode(Output output) {
            // find() preamble
            if (!anchor) {
                int start = output.offset;
                output.add(SPLIT_JMP);
                output.add(start + 5);
                output.add(DOTALL);
                output.add(SPLIT);
                output.add(start + 2);
            }
            output.markFindPreambleEnd();
            group.writeCode(output);
        }

        public String toString() {
            String inner = group.toString();
            return inner.startsWith("(?:") && inner.endsWith(")") ?
                    inner.substring(1, inner.length() - 1) : inner;
        }
    }

    private Group0 root;
    private Stack<Group> groups;
    private ExpFactory expFactory;

    public PikeCompiler() {

    }

    public void setVMFactory(ExpFactory expFactory) {
        this.expFactory = expFactory;
    }
    public Group0 compileRegExp(String regex, String regexpName, boolean useCapture) {
        start(regex.startsWith("^"));

        char[] array = regex.toCharArray();
        CharacterParser characterClassParser =
                new CharacterParser(array);
        for (int index = 0; index < array.length; ++index) {
            char c = array[index];
            Group current = groups.peek();
            if (c == '<') {
                if (!CharacterParser.USE_RANGE_TAG) {
                    current.push(c);
                    continue;
                }
            } else if (regularCharacter.matches(c)) {
                current.push(c);
                continue;
            }
            switch (c) {
                case '.':
                    current.push(DOT);
                    continue;
                case '\\':
                    int unescaped = characterClassParser.parseEscapedCharacter(index + 1);
                    if (unescaped >= 0) {
                        index = characterClassParser.getEndOffset() - 1;
                        current.push((char) unescaped);
                        continue;
                    }
                    CharacterMatcher characterClass = characterClassParser.parseClass(index);
                    if (characterClass != null) {
                        index = characterClassParser.getEndOffset() - 1;
                        current.push(new CharacterRange(characterClass));
                        continue;
                    }
                    char next = array[index + 1];
                    switch (next) {
                        case 'b':
                            index++;
                            current.push(WORD_BOUNDARY);
                            continue;
                        case 'B':
                            index++;
                            current.push(NON_WORD_BOUNDARY);
                            continue;
                        case 'p':
                        case 'P':
                            //unicode property category
                            int old = index;
                            if (array[index + 2] == '{') {
                                index += 2;
                                String name = characterClassParser.propertyName(index, 16);
                                if (name == null || name.isEmpty()) {
                                    throw new RuntimeException("Parse error @" + index + ": " + regex);
                                }
                                int[][] data = UnicodeTables.unicodeTable(name);
                                if (data == null) {
                                    throw new RuntimeException("Invalid unicode name:" + name);
                                }
                                CharacterMatcher matcher = characterClassParser.makeRangeMatcher(name, next == 'P');
                                current.push(new CharacterRange(matcher));
                                index += name.length() + 1;
                                continue;
                            }
                            index = old;
                            break;
                        case 'i': {
                            //int range
                            old = index;
                            if (array[index + 2] == '{') {
                                index += 3;
                                IntRange range = new IntRange();
                                index = parseIntRange(range, characterClassParser, regex, array, index, '-', '}');
                                current.push(range);
                                index++;
                                continue;
                            }
                            index = old;
                            break;
                        }
                        case 'h': {
                            //hook
                            old = index;
                            if (array[index + 2] == '{') {
                                index += 3;
                                IntRange range = new IntRange();
                                index = parseIntRange(range, characterClassParser, regex, array, index, ',', '}');
                                current.push(new Hook(range.start, range.end));
                                index++;
                                continue;
                            }
                            index = old;
                            break;
                        }
                    }
                    throw new RuntimeException("Parse error @" + index + ": " + regex);
                case '?':
                case '*':
                case '+': {
                    boolean greedy = true;
                    if (index + 1 < array.length && array[index + 1] == '?') {
                        greedy = false;
                        ++index;
                    }
                    current.push(new Repeat(current.pop(),
                            c == '+' ? 1 : 0, c == '?' ? 1 : -1, greedy));
                    continue;
                }
                case '<': {
                    // int range
                    ++index;
                    if (array[index] == '_' || Character.isAlphabetic(array[index])) {
                        int v = characterClassParser.subName(index, 20);
                        if (array[index + v] == '>') {
                            String name = regex.substring(index, index + v);
                            index = index + v - 1;
                            if (name.equals("all")) {
                                List<Group0> gps = lookupRegexpSet(n -> !n.startsWith("_"));
                                if (gps == null || gps.isEmpty()) {
                                    throw new IllegalStateException("invalid name:" + name);
                                }
                                current.push(makeAlternativeGroup(gps));

                            } else if (name.equals("_all")) {
                                List<Group0> gps = lookupRegexpSet(n -> true);
                                if (gps == null || gps.isEmpty()) {
                                    throw new IllegalStateException("invalid name:" + name);
                                }
                                current.push(makeAlternativeGroup(gps));
                            } else {
                                Group0 subGroup = lookupRegexp(name);
                                if (subGroup == null) {
                                    throw new IllegalStateException("invalid name:" + name);
                                }
                                current.push(subGroup.group.cloneTo(false));
                            }
                        } else {
                            throw new RuntimeException("Short pattern @" + index + ": "
                                    + regex);
                        }
                    } else {
                        IntRange range = new IntRange();
                        index = parseIntRange(range, characterClassParser, regex, array, index, '-', '>');
                        current.push(range);
                    }
                    index++;
                    break;
                }
                case '{': {
                    ++index;
                    IntRange range = new IntRange();
                    index = parseIntRange(range, characterClassParser, regex, array, index, ',', '}');
                    ++index;
                    boolean greedy = true;
                    if (index + 1 < array.length && array[index + 1] == '?') {
                        ++index;
                        greedy = false;
                    }
                    current.push(new Repeat(current.pop(), range.start, range.end, greedy));
                    continue;
                }
                case '(': {
                    boolean capturing = useCapture;
                    if (index + 1 < array.length && array[index + 1] == '?') {
                        index += 2;
                        if (index >= array.length) {
                            throw new RuntimeException("Short pattern @" + index + ": "
                                    + regex);
                        }
                        c = array[index];
                        boolean lookAhead = true;
                        if (c == '<') {
                            if (++index >= array.length) {
                                throw new RuntimeException("Short pattern @" + index + ": "
                                        + regex);
                            }
                            lookAhead = false;
                            c = array[index];
                            if (c != '=' && c != '!') {
                                throw new IllegalArgumentException("Named groups not supported @"
                                        + index + ": " + regex);
                            }
                        }
                        switch (c) {
                            case ':':
                                capturing = false;
                                break;
                            case '!':
                            case '=': {
                                capturing = false;
                                Lookaround lookaround = new Lookaround(lookAhead, c == '!');
                                current.push(lookaround);
                                groups.push(lookaround.group);
                                continue;
                            }
                            default:
                                throw new UnsupportedOperationException("Not yet supported: "
                                        + regex.substring(index));
                        }
                    }
                    current.push(groups.push(new Group(capturing, null)));
                    continue;
                }
                case ')':
                    if (groups.size() < 2) {
                        throw new RuntimeException("Invalid group close @" + index + ": "
                                + regex);
                    }
                    groups.pop();
                    continue;
                case '[': {
                    CharacterMatcher matcher = characterClassParser.parseClass(index);
                    if (matcher == null) {
                        throw new RuntimeException("Invalid range @" + index + ": " + regex);
                    }
                    current.push(new CharacterRange(matcher));
                    index = characterClassParser.getEndOffset() - 1;
                    continue;
                }
                case '|':
                    current.startAlternative();
                    continue;
                case '^':
                    current.push(LINE_START);
                    continue;
                case '$':
                    current.push(LINE_END);
                    continue;
                default:
                    throw new RuntimeException("Parse error @" + index + ": " + regex);
            }
        }
        if (groups.size() != 1) {
            throw new IllegalArgumentException("Unclosed groups: ("
                    + (groups.size() - 1) + "): " + regex);
        }
        if (regexpName != null && expFactory != null) {
            expFactory.putExp(regexpName, root);
        }
        return root;
    }

    public PikeVM compileVM(String regex, String name, boolean useCapture) {
        compileRegExp(regex, name, useCapture);
        return makeVm();
    }

    public PikeVM compileVM(String regex, String name) {
        compileRegExp(regex, name, true);
        return makeVm();
    }

    private void start(boolean anchor) {
        root = new Group0(anchor);
        groups = new Stack<>();
        groups.add(root.group);
    }

    private void addString(String word) {
        Group current = groups.peek();
        for (int j = 0; j < word.length(); j++) {
            current.push(word.charAt(j));
        }
    }

    private void or() {
        Group current = groups.peek();
        current.startAlternative();
    }

    private void repeat(int min, int max, boolean greedy) {
        Group current = groups.peek();
        current.push(new Repeat(current.pop(), min, max, greedy));
    }


    private void anyChar() {
        Group current = groups.peek();
        current.push(DOT);
    }

    private void addHook(int left, int right) {
        Group current = groups.peek();
        current.push(new Hook(left, right));
    }

    private PikeVM makeVm() {
        return new Output(root).toVM();
    }

    private void beginGroup(boolean capture) {
        Group current = groups.peek();
        current.push(groups.push(new Group(capture, null)));
    }

    private void endGroup() {
        if (groups.size() < 2) {
            throw new RuntimeException("Invalid group close");
        }
        groups.pop();
    }

    private int parseIntRange(IntRange range, CharacterParser characterClassParser, String regex, char[] array, int index, char sep, char dim) {
        int length = characterClassParser.digits(index, 10, 10);
        int min = Integer.parseInt(regex.substring(index, index + length));
        int max = min;
        index += length - 1;
        char c = index + 1 < array.length ? array[index + 1] : 0;
        if (c == sep) {
            ++index;
            length = characterClassParser.digits(index + 1, 10, 10);
            max = length == 0 ? -1 :
                    Integer.parseInt(regex.substring(index + 1, index + 1 + length));
            index += length;
            c = index + 1 < array.length ? array[index + 1] : 0;
        }
        if (c != dim) {
            throw new RuntimeException("Invalid quantifier @" + index + ": "
                    + regex);
        }
        range.start = min;
        range.end = max;
        return index;
    }

    private Group0 lookupRegexp(String name) {
        if (this.expFactory == null) {
            return null;
        }
        return (Group0) expFactory.lookupExp(name);
    }

    private List<Group0> lookupRegexpSet(Predicate<String> predicate) {
        if (this.expFactory == null) {
            return null;
        }
        return (List) expFactory.lookupExpSet(predicate);
    }

    private Group makeAlternativeGroup(List<Group0> gps) {
        Group g = new Group(false, null);
        int i = 0;
        for (Group0 g0 : gps) {
            g.push(g0.group.cloneTo(false));
            if (i < (gps.size() - 1)) {
                g.startAlternative();
            }
            i++;
        }
        return g;
    }
}
