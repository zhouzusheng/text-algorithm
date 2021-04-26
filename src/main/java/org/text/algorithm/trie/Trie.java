package org.text.algorithm.trie;

import org.text.algorithm.TextMatcher;

import java.io.IOException;
import java.util.List;

public interface Trie extends TextMatcher {
    public static class  TrieResult {
        public String word;
        public int value;
        public int offset;
        public int len;
        public void copyFrom(TrieResult other) {
            this.word = other.word;
            this.value = other.value;
            this.offset = other.offset;
            this.len = other.len;
        }
        public String toString() {
            return word + "," + value + "," + offset + "," + len;
        }
    }

    /**
     * 精确匹配key， 即返回树中等于 key 的 节点
     */
    public int exactMatchSearch(String key);

    /**
     * 精确匹配key， 即返回树中等于 key 的 节点， pos， end 是只考虑key的范围 nodePos 含义待定， 应该为 0
     * @param key
     * @param pos
     * @param end
     * @param nodePos
     * @return
     */
    public int exactMatchSearch(char[] key, int pos, int end, int nodePos);

    /**
     * 搜索key开头部分在树中的所有节点
     * @param key
     * @return
     */
    //
    public List<TrieResult> commonPrefixSearch(String key);

    /**
     * 搜索key开头部分在树中的所有节点
     * @param key
     * @param pos
     * @param end
     * @param nodePos
     * @return
     */
    public List<TrieResult> commonPrefixSearch(char[] key, int pos, int end, int nodePos);

    /**
     * 在text 中找第一个树中的节点
     * @param text
     * @return
     */
    public TrieResult matchOne(String text);

    /**
     * 在text 中找第一个树中的节点
     * @param code
     * @param end
     * @param len
     * @return
     */
    public TrieResult matchOne(char[] code, int end, int len);

    /**
     * 在text 中寻找在树中所有的节点
     * @param text
     * @return
     */
    public List<TrieResult> matchAll(String text);

    /**
     * 在text 中寻找在树中所有的节点
     * @param code
     * @param offset
     * @param end
     * @return
     */
    public List<TrieResult> matchAll(char[] code, int offset, int end);

    /**
     * 保存状态机
     * @param fileName
     * @throws IOException
     */
    public void save(String fileName) throws IOException;

    /**
     * 加载状态机
     * @param fileName
     * @throws IOException
     */
    public void open(String fileName) throws IOException;
}
