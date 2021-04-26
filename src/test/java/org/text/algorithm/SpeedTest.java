package org.text.algorithm;

import org.junit.BeforeClass;
import org.junit.Test;
import org.text.algorithm.trie.TrieBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

public class SpeedTest {

    static TextMatcher trie;

    @BeforeClass
    public static void init() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("src/test/resources/四十万汉语大词库.txt"));
        lines.sort(Comparator.comparing(t->t));
        System.out.println(lines.get(0));
        TrieBuilder triebuilder = TrieBuilder.defaultBuilder();

        long t1 = System.currentTimeMillis();
        triebuilder.build(lines, null, null, lines.size());

        trie = triebuilder.toTrie();

        long t2 = System.currentTimeMillis();

        System.out.println("t1=" + (t2-t1));

        lines.clear();
    }

    @Test
    public void test() {
        String text = "在人民海军成立72周年之际，海军向全社会公开发布主题宣传片《大海向党旗报告》，庆祝党的百年华诞。通过展现海军一线部队忠诚使命、英勇善战、建功海洋的精神追求，让你领略新时代海军官兵的家国情怀，让你感悟人民海军向海图强的豪情壮志。";
        SubMatcher subMatcher = trie.newMatcher(text, (off, len, id)->{
            //System.out.println(text.substring(off, off+len));
            return true;
        });

        int count;

        count = subMatcher.matchAll(0);
        System.out.println(count);

        time(subMatcher);

    }

    private void time(SubMatcher subMatcher) {
        long times = 100000;
        long start = System.currentTimeMillis();
        int count = 0;
        for(int i = 0; i < times; i++) {
            count += subMatcher.matchAll(0);
        }
        long end = System.currentTimeMillis();
        System.out.println("time=" +(end-start));
    }
}
