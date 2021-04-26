package org.text.algorithm.trie;

import org.text.algorithm.trie.impl.DoubleArrayTrie;
import org.text.algorithm.trie.impl.DoubleArrayTrieBuilder;

import java.util.List;
import java.util.SortedMap;

public interface TrieBuilder {
    /**
     * 构造 trie 树
     * @param inputs
     * @return
     */
    int build(SortedMap<String, Integer> inputs);

    /**
     * 构造 trie 树, 要求 _key 排序
     * _length， _value 如果非空， 必须与 _keySize 等长
     * @param _key
     * @param _length
     * @param _value
     * @param _keySize
     * @return
     */
    int build(List<String> _key, int _length[], int _value[],
              int _keySize);

    /**
     * 清除builder， 以便再次构建
     */
    void clear();

    /**
     * 获取构建的trie
     * @return
     */
    DoubleArrayTrie toTrie();

    static TrieBuilder defaultBuilder() {
        return new DoubleArrayTrieBuilder();
    }
}
