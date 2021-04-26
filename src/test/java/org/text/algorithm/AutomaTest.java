package org.text.algorithm;

import org.junit.Test;
import org.text.algorithm.automa.Dfa;
import org.text.algorithm.automa.DfaCompose;
import org.text.algorithm.automa.StringDfa;

import java.util.ArrayList;
import java.util.List;

public class AutomaTest {

    static List<String> preparePatterns() {
        List<String> patterns = new ArrayList<>();
        patterns.add("aaa");
        patterns.add("bbb");
        patterns.add("abc");
        patterns.sort((a,b)->a.compareTo(b));
        return patterns;
    }

    static List<String> preparePatternsNum() {
        List<String> patterns = new ArrayList<>();
        patterns.add("123");
        patterns.add("456");
        patterns.add("1423");
        patterns.sort((a,b)->a.compareTo(b));
        return patterns;
    }
    @Test
    public void testStringDfa() {

        Dfa dfa = StringDfa.build(0, 0, preparePatterns());
        System.out.println(dfa.match("bbb", 0));
        System.out.println(dfa.startsWith("bbb is a valid string", 0));
    }

    @Test
    public void testDfaCompose() {

        Dfa dfa1 = StringDfa.build("a", 0, preparePatterns());
        Dfa dfa2 = StringDfa.build("b", dfa1.getRoot() + dfa1.getStateCount(), preparePatternsNum());

        DfaCompose compose = DfaCompose.compose(dfa1, dfa2);
        System.out.println(compose.matchIds("bbb", 0));
        System.out.println(compose.matchIds2("123", 0));
    }
}
