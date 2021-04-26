package org.text.algorithm.automa;

import org.text.algorithm.utils.ArrayUtil;

/**
 * 多个dfa 的 并集
 */
public class DfaUnion {
    /**
     * 多个dfa 的 并集， roots 保存每个dfa 的 根
     */
    private int[] roots;

    /**
     * 多个dfa的并集， ids 保存每个dfa 的 id
     */
    private Object[] ids;

    /**
     * 所有单个DFA 的并集， 第一维是所有的状态， 大小是所有单DFA状态的和
     * 第二维 代表每个单状态的转换边， 是二元组格式，因此大小是该状态的边个数*2
     * 二元组含义： 边 与 转换到的目标状态
     */
    private int[][] tables;

    /**
     * 每个状态是否已经accept
     */
    private boolean[] accepts;

    public int[] getRoots() {
        return roots;
    }

    public void setRoots(int[] roots) {
        this.roots = roots;
    }

    public Object[] getIds() {
        return ids;
    }

    public void setIds(Object[] ids) {
        this.ids = ids;
    }

    public int[][] getTables() {
        return tables;
    }

    public void setTables(int[][] tables) {
        this.tables = tables;
    }

    public boolean[] getAccepts() {
        return accepts;
    }

    public void setAccepts(boolean[] accepts) {
        this.accepts = accepts;
    }

    /**
     * 匹配，必须是整体匹配
     *
     * @param index dfa 序号
     * @param text
     * @return
     */
    public boolean matchItem(int index, String text) {
        int state = roots[index];
        int[] table = tables[state];
        for (int i = 0; i < text.length(); i++) {
            if (table == null) {
                return false;
            }
            char ch = text.charAt(i);
            state = ArrayUtil.binSearch(table, ch, -1);
            if (state == -1) {
                return false;
            }
            table = tables[state];
        }
        return state != -1 && accepts[state];
    }
}
