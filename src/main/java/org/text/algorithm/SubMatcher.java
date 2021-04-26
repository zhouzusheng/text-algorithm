package org.text.algorithm;

public interface SubMatcher {
    /**
     * 从制定位置开始前缀匹配
     * @param pos
     * @return
     */
    int matchPrefix(int pos);

    /**
     * 从制定位置开始全部匹配
     * @param pos
     * @return
     */
    int matchAll(int pos);
}
