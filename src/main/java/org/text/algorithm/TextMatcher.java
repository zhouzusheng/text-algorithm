package org.text.algorithm;

public interface TextMatcher {
    /**
     * 获取精确等于 key的项目
     * @param key
     * @return
     */
    int get(String key);

    /**
     * 获取精确等于 key的项目
     * @param key
     * @param start
     * @param end
     * @return
     */
    int get(char[] key, int start, int end);

    /**
     * 获取支持匹配位置的匹配器
     * @param key
     * @param start
     * @param end
     * @param callback
     * @return
     */
    SubMatcher newMatcher(String key, int start, int end, HitCallback callback);

    /**
     * 获取支持匹配位置的匹配器
     * @param key
     * @param start
     * @param end
     * @param callback
     * @return
     */
    SubMatcher newMatcher(char[] key, int start, int end, HitCallback callback);

    default SubMatcher newMatcher(String key, HitCallback callback) {
        return newMatcher(key, 0, key.length(), callback);
    }

    default SubMatcher newMatcher(char[] key, HitCallback callback) {
        return newMatcher(key, 0, key.length, callback);
    }
}
