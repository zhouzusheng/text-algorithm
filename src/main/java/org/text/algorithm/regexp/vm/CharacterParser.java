package org.text.algorithm.regexp.vm;

import org.text.algorithm.regexp.fsm.NFAState;
import org.text.algorithm.regexp.fsm.Range;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

public class CharacterParser {
    public static boolean OPTIMIZE_RANGE = true;
    public static boolean USE_RANGE_TAG = true;

    private final char[] description;
    private int offset;

    public static CharacterMatcher newClass(int type) {
        switch (type) {
            case BitSetMatcher.TYPE:
                return new BitSetMatcher();
            case SparseMatcher.TYPE:
                return new SparseMatcher();
            case UnicodeTableMatcher.TYPE:
                return new UnicodeTableMatcher();
            default:
                throw new RuntimeException("不支持的 类型" + type);
        }
    }

    public CharacterParser(char[] description) {
        this.description = description;
    }


    public int getEndOffset() {
        return offset;
    }

    /**
     * Parses an escaped character.
     *
     * @param start the offset <u>after</u> the backslash
     * @return the escaped character, or -1 if no character was recognized
     */
    public int parseEscapedCharacter(int start) {
        offset = start;
        return parseEscapedCharacter();
    }

    public CharacterMatcher makeRangeMatcher(String name, boolean inversePattern) {
        return new UnicodeTableMatcher(name, inversePattern);
    }

    private int parseEscapedCharacter() {
        if (offset == description.length) {
            throw new IllegalArgumentException("Short escaped character");
        }
        char c = description[offset++];
        if (c == '0') {
            int len = digits(offset, 3, 8);
            if (len == 3 && description[offset] > '3') {
                --len;
            }
            c = (char) Integer.parseInt(new String(description, offset, len), 8);
            offset += len;
            return c;
        }
        if (c == 'x' || c == 'u') {
            int len = digits(offset, 4, 16);
            c = (char) Integer.parseInt(new String(description, offset, len), 16);
            offset += len;
            return c;
        }
        switch (c) {
            case 'a':
                return 0x0007;
            case 'e':
                return 0x001B;
            case 'f':
                return 0x000C;
            case 'n':
                return 0x000A;
            case 'r':
                return 0x000D;
            case 't':
                return 0x0009;
            case '\\':
            case '.':
            case '*':
            case '+':
            case '?':
            case '|':
            case '[':
            case ']':
            case '{':
            case '}':
            case '(':
            case ')':
            case '^':
            case '$':
                return c;
        }
        return -1;
    }

    public String propertyName(int offset, int maxLength) {
        if (description[offset] != '{') {
            return null;
        }
        offset++;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; ; ++i) {
            if (i == maxLength || offset + i >= description.length) {
                return null;
            }
            char ch = description[offset + i];
            if (ch == '}') {
                break;
            }
            if (!Character.isLetter(ch)) {
                throw new IllegalArgumentException("Invalid property character @" + (offset + i));
            } else {
                builder.append(ch);
                break;
            }
        }
        return builder.toString();
    }

    public int subName(int offset, int maxLength) {
        for (int i = 0; ; ++i) {
            if (i == maxLength || offset + i >= description.length) {
                return i;
            }
            char ch = description[offset + i];
            if ((ch == '_') || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                continue;
            }
            return i;
        }
    }
    public int digits(int offset, int maxLength, int base) {
        for (int i = 0; ; ++i) {
            if (i == maxLength || offset + i >= description.length) {
                return i;
            }
            int value = description[offset + i] - '0';
            if (value < 0) {
                return i;
            }
            if (base > 10 && value >= 10) {
                value += 10 - (value >= 'a' - '0' ? 'a' - '0' : 'A' - '0');
            }
            if (value >= base) {
                return i;
            }
        }
    }

    public CharacterMatcher parseClass(int start) {
        offset = start;
        return parseClass();
    }

    public CharacterMatcher parseClass() {
        SimpleMatcher simple = parseSimpleClass();
        return simple == null ? null : simple.optimize();
    }

    SimpleMatcher parseSimpleClass(int start) {
        offset = start;
        return parseSimpleClass();
    }

    SimpleMatcher parseSimpleClass() {
        if (description[offset] != '[') {
            if (description[offset] == '\\') {
                String range = specialClass(description[++offset]);
                if (range != null) {
                    ++offset;
                    CharacterParser parser = new CharacterParser(range.toCharArray());
                    return parser.parseSimpleClass();
                }
            }
            return null;
        }
        SimpleMatcher matcher = new SimpleMatcher(new boolean[0],
                description[++offset] == '^');
        if (matcher.inversePattern) {
            ++offset;
        }

        int previous = -1;
        boolean firstCharacter = true;
        for (; ; ) {
            if (offset >= description.length) {
                unsupported("short regex");
            }
            char c = description[offset++];
            if (c == '-' && !firstCharacter && description[offset] != ']') {
                if (previous < 0) {
                    unsupported("invalid range");
                }
                int rangeEnd = description[offset];
                if ('\\' == rangeEnd) {
                    rangeEnd = parseEscapedCharacter();
                    if (rangeEnd < 0) {
                        unsupported("invalid range");
                    }
                }
                matcher.ensureCapacity(rangeEnd + 1);
                for (int j = previous + 1; j <= rangeEnd; j++) {
                    matcher.map[j] = true;
                }
            } else if (c == '\\') {
                int saved = offset;
                previous = parseEscapedCharacter();
                if (previous < 0) {
                    offset = saved - 1;
                    SimpleMatcher clazz = parseSimpleClass();
                    if (clazz == null) {
                        unsupported("escape");
                    }
                    matcher.merge(clazz);
                } else {
                    matcher.setMatch(previous);
                }
            } else if (c == '[') {
                CharacterParser parser = new CharacterParser(description);
                SimpleMatcher other = parser.parseSimpleClass(offset - 1);
                if (other == null) {
                    unsupported("invalid merge");
                }
                matcher.merge(other);
                offset = parser.getEndOffset();
                previous = -1;
            } else if (c == '&') {
                if (offset + 2 > description.length || description[offset] != '&'
                        || description[offset + 1] != '[') {
                    unsupported("operation");
                }
                CharacterParser parser = new CharacterParser(description);
                SimpleMatcher other = parser.parseSimpleClass(offset + 1);
                if (other == null) {
                    unsupported("invalid intersection");
                }
                matcher.intersect(other);
                offset = parser.getEndOffset();
                previous = -1;
            } else if (c == ']') {
                break;
            } else {
                previous = c;
                matcher.setMatch(previous);
            }
            firstCharacter = false;
        }

        return matcher;
    }

    private void unsupported(String msg) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Unsupported " + msg + " @"
                + offset + ": "
                + new String(description, 0, description.length));
    }

    private static String specialClass(int c) {
        if ('d' == c) {
            return "[0-9]";
        }
        if ('D' == c) {
            return "[^0-9]";
        }
        if ('s' == c) {
            return "[ \\t\\n\\x0B\\f\\r]";
        }
        if ('S' == c) {
            return "[^ \\t\\n\\x0B\\f\\r]";
        }
        if ('w' == c) {
            return "[a-zA-Z_0-9]";
        }
        if ('W' == c) {
            return "[^a-zA-Z_0-9]";
        }
        return null;
    }

    static abstract class AbsMatcher implements CharacterMatcher {
        protected boolean inversePattern;

        @Override
        public void save(DataOutput out) throws IOException {
            out.writeInt(getType());
            out.writeInt(inversePattern ? 1 : 0);
        }

        @Override
        public void load(DataInput input) throws IOException {
            //不load type ， 已经偷偷读取了
            inversePattern = input.readInt() == 1;
        }

        @Override
        public void addTrans(NFAState s1, NFAState s2) {
            List<Integer> data = getSubData();
            if (inversePattern) {
                addInverseRanges(s1, s2, data);
            } else {
                addRanges(s1, s2, data);
            }
        }

        protected void addInverseRanges(NFAState s1, NFAState s2, List<Integer> data) {
            if (OPTIMIZE_RANGE && data.size() <= 30000) {
                s1.transitionState(new Range(-1), s2);
                return;
            }
            int start = data.get(0);
            if (start > 0) {
                s1.transitionState(new Range(0, start - 1), s2);
            }
            for (int i = 1; i < data.size(); i++) {
                int v = data.get(i);
                if (v > (start + 1)) {
                    s1.transitionState(new Range(start + 1, v - 1), s2);
                }
                start = v;
            }
            if (start < Character.MAX_VALUE) {
                s1.transitionState(new Range(start + 1, Character.MAX_VALUE), s2);
            }
        }

        protected void addRanges(NFAState s1, NFAState s2, List<Integer> data) {
            if (OPTIMIZE_RANGE && data.size() >= 30000) {
                s1.transitionState(new Range(-1), s2);
                return;
            }
            int start = data.get(0);
            int end = start;
            for (int i = 1; i < data.size(); i++) {
                int v = data.get(i);
                if (v == (end + 1)) {
                    end = v;
                } else {
                    s1.transitionState(new Range(start, end), s2);
                    start = end = v;
                }
            }
            s1.transitionState(new Range(start, end), s2);
        }

        protected abstract List<Integer> getSubData();
    }

    public static class SimpleMatcher implements CharacterMatcher {

        private static final long serialVersionUID = -1L;
        public static final int TYPE = 0;

        private boolean[] map;
        private boolean inversePattern;

        public SimpleMatcher() {

        }

        public SimpleMatcher(boolean[] map, boolean inversePattern) {
            this.map = map;
            this.inversePattern = inversePattern;
        }

        @Override
        public int getType() {
            return TYPE;
        }

        @Override
        public boolean matches(int c) {
            int index = c;
            return (map.length > index && map[index]) ^ inversePattern;
        }

        @Override
        public void addTrans(NFAState s1, NFAState s2) {
            //Nothing

        }

        @Override
        public void save(DataOutput out) throws IOException {
            //nothing
        }

        @Override
        public void load(DataInput input) throws IOException {

        }

        private CharacterMatcher optimize() {
            if (map.length > 64) {
                List<Integer> dest = new ArrayList<>(map.length);
                for (int i = 0; i < map.length; i++) {
                    if (map[i]) {
                        dest.add(i);
                    }
                }
                if ((map.length > 1000 && dest.size() * 500 < map.length)
                        || (map.length > 64 && dest.size() * 8 < map.length)) {
                    //数据太稀疏了
                    char[] charArray = new char[dest.size()];
                    int i = 0;
                    for (int ch : dest) {
                        charArray[i++] = (char) ch;
                    }
                    return new SparseMatcher(inversePattern, charArray);
                }
            }
            BitSet bitSet = new BitSet();
            for (int i = 0; i < map.length; i++) {
                if (map[i]) {
                    bitSet.set(i);
                }
            }
            return new BitSetMatcher(inversePattern, bitSet);
        }

        private void setMatch(int c) {
            ensureCapacity(c + 1);
            map[c] = true;
        }

        private void ensureCapacity(int length) {
            if (map.length >= length) {
                return;
            }
            int size = map.length;
            if (size < 32) {
                size = 32;
            }
            while (size < length) {
                size <<= 1;
            }
            map = Arrays.copyOf(map, size);
        }

        private void merge(SimpleMatcher other) {
            boolean inversePattern = this.inversePattern || other.inversePattern;
            if ((map.length < other.map.length) ^ inversePattern) {
                map = Arrays.copyOf(map, other.map.length);
            }
            for (int i = 0; i < map.length; ++i) {
                map[i] = (matches((char) i) || other.matches((char) i)) ^ inversePattern;
            }
            this.inversePattern = inversePattern;
        }

        private void intersect(SimpleMatcher other) {
            boolean inversePattern = this.inversePattern && other.inversePattern;
            if ((map.length > other.map.length) ^ inversePattern) {
                map = Arrays.copyOf(map, other.map.length);
            }
            for (int i = 0; i < map.length; ++i) {
                map[i] = (matches((char) i) && other.matches((char) i)) ^ inversePattern;
            }
            this.inversePattern = inversePattern;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("[");
            if (inversePattern) {
                builder.append("^");
            }
            for (int i = 0; i < map.length; ++i) {
                if (!map[i]) {
                    continue;
                }
                builder.append(i >= ' ' && i <= 0x7f ?
                        "" + (char) i : ("\\x" + Integer.toHexString(i)));
                int j = i + 1;
                while (j < map.length && map[j]) {
                    ++j;
                }
                --j;
                if (j > i) {
                    if (j > i + 1) {
                        builder.append('-');
                    }
                    builder.append(j >= ' ' && j <= 0x7f ?
                            "" + (char) j : ("\\x" + Integer.toHexString(j)));
                    i = j;
                }
            }
            builder.append("]");
            return builder.toString();
        }
    }


    public static class BitSetMatcher extends AbsMatcher {
        private static final long serialVersionUID = -1L;
        public static final int TYPE = 1;

        private BitSet bitset;

        public BitSetMatcher() {

        }

        public BitSetMatcher(boolean inversePattern, BitSet bitset) {
            this.inversePattern = inversePattern;
            this.bitset = bitset;
        }

        @Override
        public void save(DataOutput output) throws IOException {
            super.save(output);
            byte[] bytes = bitset.toByteArray();
            output.writeInt(bytes.length);
            output.write(bitset.toByteArray());
        }

        @Override
        public void load(DataInput input) throws IOException {
            super.load(input);
            int count = input.readInt();
            if (count == 0) {
                bitset = new BitSet();
            } else {
                byte[] bytes = new byte[count];
                input.readFully(bytes);
                bitset = BitSet.valueOf(bytes);
            }
        }

        @Override
        public int getType() {
            return TYPE;
        }


        @Override
        protected List<Integer> getSubData() {
            List<Integer> subData = new ArrayList<>();
            int length = bitset.length();
            for (int i = 0; i < length; i++) {
                if (bitset.get(i)) {
                    subData.add(i);
                }
            }
            return subData;
        }

        @Override
        public boolean matches(int c) {
            int index = c;
            return bitset.get(index) ^ inversePattern;
        }
    }

    public static class SparseMatcher extends AbsMatcher {
        private static final long serialVersionUID = -1L;
        public static final int TYPE = 2;
        private char[] charData;

        public SparseMatcher() {

        }

        public SparseMatcher(boolean inversePattern, char[] charData) {
            this.inversePattern = inversePattern;
            this.charData = charData;
        }

        @Override
        public void save(DataOutput out) throws IOException {
            //nothing
            super.save(out);
            out.writeInt(charData.length);
            for (char ch : charData) {
                out.writeChar(ch);
            }
        }

        @Override
        public void load(DataInput input) throws IOException {
            super.load(input);
            int count = input.readInt();
            charData = new char[count];
            for (int i = 0; i < count; i++) {
                charData[i] = input.readChar();
            }
        }

        @Override
        public int getType() {
            return TYPE;
        }

        @Override
        protected List<Integer> getSubData() {
            List<Integer> subData = new ArrayList<>(charData.length);
            for (int i = 0; i < charData.length; i++) {
                subData.add((int) charData[i]);
            }
            return subData;
        }

        @Override
        public boolean matches(int c) {
            boolean exists;
            if (c < 0 || c > Character.MAX_VALUE) {
                exists = false;
            } else {
                exists = Arrays.binarySearch(charData, (char) c) >= 0;
            }
            return exists ^ inversePattern;
        }
    }

    public static class UnicodeTableMatcher implements CharacterMatcher {
        private static final long serialVersionUID = -1L;
        public static final int TYPE = 3;

        private boolean inversePattern;
        private transient int[][] range;
        private String name;

        public UnicodeTableMatcher() {

        }

        public UnicodeTableMatcher(String name, boolean inversePattern) {
            this.name = name;
            this.inversePattern = inversePattern;
            postFix();
        }

        @Override
        public void save(DataOutput out) throws IOException {
            out.writeInt(getType());
            out.writeInt(inversePattern ? 1 : 0);
            out.writeUTF(name);
        }

        @Override
        public void load(DataInput input) throws IOException {
            //TYPE 提前读取
            inversePattern = input.readInt() == 1;
            name = input.readUTF();
            postFix();
        }

        @Override
        public void postFix() {
            this.range = UnicodeTables.unicodeTable(name);
        }

        @Override
        public void addTrans(NFAState s1, NFAState s2) {
            if (inversePattern) {
                s1.transitionState(new Range(-1, -1), s2);
            } else {
                for (int[] r : range) {
                    s1.transitionState(new Range(r[0], r[1]), s2);
                }
            }
        }

        @Override
        public int getType() {
            return TYPE;
        }

        @Override
        public boolean matches(int c) {
            return Unicode.is(range, c) ^ inversePattern;
        }
    }
}
