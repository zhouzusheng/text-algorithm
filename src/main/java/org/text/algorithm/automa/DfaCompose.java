package org.text.algorithm.automa;

import org.text.algorithm.utils.ArrayUtil;

import java.util.Set;
import java.util.TreeSet;

/**
 * 多个DFA 合并为一个DFA
 * 总的DFA的根以 0 开始
 */
public class DfaCompose {

    public static interface HitCallback {
        void hit(int id, int start, int end);
    }

    public static interface HitCallback2 {
        void hit(Object id, int start, int end);
    }

    /**
     * 多个dfa的并集， ids 保存每个dfa 的 id
     */
    private Object[] ids;

    /**
     * 所有单dfa 编译成的整体DFA的状态表
     * 第一维是所有的状态
     * 第二维 代表每个单状态的转换边， 是二元组格式，因此大小是该状态的边个数*2
     * 二元组含义： 边 转换到的目标状态
     **/
    private int[][] dfaTables;

    /**
     * 每个状态匹配完成的词Id
     * 第一维是所有的状态
     * 第二维是该状态匹配到的词id列表
     */
    private int[][] dfaAccepts;

    public Object[] getIds() {
        return ids;
    }

    public void setIds(Object[] ids) {
        this.ids = ids;
    }

    public int[][] getDfaTables() {
        return dfaTables;
    }

    public void setDfaTables(int[][] dfaTables) {
        this.dfaTables = dfaTables;
    }

    public int[][] getDfaAccepts() {
        return dfaAccepts;
    }

    public void setDfaAccepts(int[][] dfaAccepts) {
        this.dfaAccepts = dfaAccepts;
    }

    //完整匹配时的id
    public Set<Integer> matchIds(String text, int start) {
        Set<Integer> r = new TreeSet<>();
        int state = 0;
        for (int i = start; i < text.length(); i++) {
            int[] table = dfaTables[state];
            if (table == null) {
                return r;
            }
            char ch = text.charAt(i);
            state = ArrayUtil.binSearch(table, ch, -1);
            if (state == -1) {
                return r;
            }
        }
        int[] ids = dfaAccepts[state];
        if (ids != null) {
            for (Integer id : ids) {
                r.add(id);
            }
        }
        return r;
    }

    public Set<Object> matchIds2(String text, int start) {
        Set<Object> r = new TreeSet<>();
        int state = 0;
        for (int i = start; i < text.length(); i++) {
            int[] table = dfaTables[state];
            if (table == null) {
                return r;
            }
            char ch = text.charAt(i);
            state = ArrayUtil.binSearch(table, ch, -1);
            if (state == -1) {
                return r;
            }
        }
        int[] ids = dfaAccepts[state];
        if (ids != null) {
            Object[] dfaIds = this.getIds();
            for (Integer id : ids) {
                r.add(dfaIds[id]);
            }
        }
        return r;
    }

    /**
     * 部分匹配
     *
     * @param text
     * @param start
     * @return
     */
    public void search(String text, int start, int end, HitCallback callback) {
        int limit = Math.min(text.length(), end);
        for (int i = start; i < limit; i++) {
            int state = 0;
            for (int j = i; j < limit; j++) {
                int[] ids = dfaAccepts[state];
                if (ids != null) {
                    for (int id : ids) {
                        callback.hit(id, i, j);
                    }
                }

                int[] table = dfaTables[state];
                char ch = text.charAt(j);
                state = ArrayUtil.binSearch(table, ch, -1);
                if (state == -1) {
                    break;
                }
            }
            if (state != -1) {
                int[] ids = dfaAccepts[state];
                if (ids != null) {
                    for (int id : ids) {
                        callback.hit(id, i, limit);
                    }
                }
            }
        }
    }

    /**
     * 部分匹配
     *
     * @param text
     * @param start
     * @return
     */
    public void search2(String text, int start, int end, HitCallback2 callback) {
        int limit = Math.min(text.length(), end);
        Object[] dfaIds = getIds();
        for (int i = start; i < limit; i++) {
            int state = 0;
            for (int j = i; j < limit; j++) {
                int[] ids = dfaAccepts[state];
                if (ids != null) {
                    for (int id : ids) {
                        callback.hit(dfaIds[id], i, j);
                    }
                }

                int[] table = dfaTables[state];
                char ch = text.charAt(j);
                state = ArrayUtil.binSearch(table, ch, -1);
                if (state == -1) {
                    break;
                }
            }
            if (state != -1) {
                int[] ids = dfaAccepts[state];
                if (ids != null) {
                    for (int id : ids) {
                        callback.hit(dfaIds[id], i, limit);
                    }
                }
            }
        }
    }

    void determinize(DfaUnion union) {
        DeterminizeOperation op = new DeterminizeOperation(union,this);
        op.determinize();
    }

    public SparseDfa toSparseDfa() {
        SparseDfaOperation op = new SparseDfaOperation(this);
        return op.sparse();
    }

    public static DfaCompose compose(Dfa... dfas) {
        DfaUnion union = new DfaUnion();
        Dfa.union(union, dfas);

        DfaCompose compose = new DfaCompose();

        compose.determinize(union);
        return compose;
    }
}


