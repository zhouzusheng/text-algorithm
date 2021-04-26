/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp;

import org.text.algorithm.regexp.vm.PikeVM;
import org.text.algorithm.regexp.vm.PikeVMOpcodes;
import org.text.algorithm.utils.IntQuad;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * match text with automaton
 *
 * @author zhouzusheng
 */
public class AutomatonMatcher {
    private Automaton automaton;
    private char[] input;
    private MatcherCallback callback;

    private Set<Integer> candidateIds;

    private HookCallbackAdapt hookAdapt;

    private ResultAdapt resultAdapt;

    private HookCallback hookCallback;

    private int stateBucketSize;
    private int singleDFABucketSize;
    private int hookBucketSize;

    public void switchTo(Automaton automaton) {
        this.automaton = automaton;
        if (candidateIds != null) {
            candidateIds.clear();
        }
        evalBuecketSize();
    }

    public interface MatcherCallback {
        /**
         * 报告匹配到一个正则
         *返回 true 才继续调用 hitInfo
         * 使用者可以用来做一些排除逻辑
         * id 是用户自己分配的id
         */
        default boolean hit(int id) {
            return true;
        }

        /**
         * 一个正则匹配的详情
         * 注意：如果一个正则有多次命中， 这里会调用多次，每次传递命中的详情
         *
         * @param id  命中的正则 id
         * @param start 命中的开始位置， 下标为0 表示 整个范围， 否则是第 X 个括号的范围。
         *              对不需要捕获的括号， 值为 -1
         * @param end 命中的结束位置
         */
        void hitInfo(int id, int[] start, int[] end);

        /**
         * 对一个正则，所有命中结束后调用一次
         * @param id
         */
        default void hitEnd(int id) {}
    }

    public interface HookCallback {
        /**
         * hook 发生时的回调
         *hook 是指正则中的 \h{XXX,YYY} 其中 XXX YYY 是两个整数，用来在回调时区分 hook
         * @param type  hook类型 ： 0 DFA ， 1 单条正则（从而是NFA）
         * @param dfaId 当 type=1 代表单个正则的id
         * @param args : 目前四个整数
         *             第一个代表定义hook时 (\h) 指定的第一个整数
         *             第二个代表定义hook时（\h) 指定的第二个整数
         *                例如 \h{11,50}  表示 第一个数 是11 ， 第二个数 是 50
         *             第三个数是hook 开始时的位置
         *             第四个参数是hook 发生时的当前位置
         * @return
         *    <0 或 >2  表示 hook 失败，停止hook
         *    0  继续hook
         *    1 hook成功完成，不需要继续hook
         *    2 hook已经成功， 但还是要继续hook
         *    只有hook成功后后面的字符才可以继续匹配
         */
        int hook(int type, int dfaId, int[] args);
    }

    /**
     * 构造matcher
     * @param automaton
     * @param input
     * @param callback 匹配到结果时调用，不能为空
     * @param hookCallback， hook 是调用，可以为 null
     */
    public AutomatonMatcher(Automaton automaton, char[] input, MatcherCallback callback, HookCallback hookCallback) {
        this.automaton = automaton;
        this.input = input;
        this.callback = callback;
        this.hookCallback = hookCallback;
        this.candidateIds = null;
        evalBuecketSize();
    }

    public AutomatonMatcher(Automaton automaton, char[] input, MatcherCallback callback) {
        this.automaton = automaton;
        this.input = input;
        this.callback = callback;
        this.hookCallback = null;
        this.candidateIds = null;
    }

    public AutomatonMatcher(Automaton automaton, String input, MatcherCallback callback, HookCallback hookCallback) {
        this(automaton, input.toCharArray(), callback, hookCallback);
    }

    public AutomatonMatcher(Automaton automaton, String input, MatcherCallback callback) {
        this(automaton, input.toCharArray(), callback);
    }

    public boolean find() {
        this.candidateIds = new LinkedHashSet<>();

        int[][] dfaTables = automaton.getDfaTables();
        int stateCount = automaton.getDfaStateCount();

        int[][] dfaPendingStates = automaton.getDfaPendingStates();
        int[][] tables = automaton.getTables();
        SimpleIntSet states = new SimpleIntSet(stateBucketSize);
        SimpleIntSet next = new SimpleIntSet(stateBucketSize);

        SimpleSet<IntQuad> hooks = new SimpleSet<>(IntQuad.class, hookBucketSize);
        SimpleSet<IntQuad> nextHooks = new SimpleSet<>(IntQuad.class, hookBucketSize);

        states.add(0);

        //注意， 我们的DFA 是完全的， 因此主循环只需要这样扫描一次文本
        //这也意味着主DFA无法知道匹配的开始位置，交给单个正则匹配处理吧。
        for (int i = 0; i < input.length; i++) {
            //本来正常的DFA 需要一个 state 即可
            //但是我们的DFA 为了避免状态膨胀，强行将 . 配置成 特殊的边， 这样就在实质上造成
            //了不确定性， 所以我们要用类似 NFA的方法查询
            //这里不计算匹配位置，不计算括号之类的，所以只保留了可能的状态
            int pos = i;
            hooks.foreach(hookEntry -> {
                processHookEntry(hookEntry, pos, nextHooks, next);
            });
            states.foreach(id -> {
                if (id >= stateCount) {
                    //this is pending;
                    doMatchPendingItem(id, pos, dfaPendingStates, stateCount, tables);
                    return;
                }
                int[] table = dfaTables[id];
                //开始是一个计数，表示有几个dfa id
                int count = table[0];
                for (int j = 0; j < count; j++) {
                    //已经匹配上， table[j+1] 就是匹配到的dfa id
                    addCandidate(table[j + 1]);
                }
                //后面是转换边
                int transIndex = count + 1;
                if (transIndex < table.length) {
                    //跳到下一个状态
                    matchPosition(pos, table, transIndex, next);
                    //检查hook 状态
                    hookBegin(pos, table, transIndex, nextHooks, next);
                }
            });

            states.clear();
            SimpleIntSet.swap(states, next);

            hooks.clear();
            SimpleSet.swap(hooks, nextHooks);

            if (states.isEmpty() && hooks.isEmpty()) {
                break;
            }
        }
        if (!states.isEmpty()) {
            states.foreach(id -> {
                if (id >= stateCount) {
                    //this is pending;
                    acceptPendingItem(id, dfaPendingStates, stateCount, tables);
                    return;
                }
                int[] table = dfaTables[id];
                int count = table[0];
                for (int j = 0; j < count; j++) {
                    addCandidate(table[j + 1]);
                }
            });
            states.clear();
        }

        boolean ret = false;
        resultAdapt = new ResultAdapt(0, callback);
        if (hookCallback != null) {
            hookAdapt = new HookCallbackAdapt(0, hookCallback);
        } else {
            hookAdapt = null;
        }
        for (int id : candidateIds) {
            if (doMatchItem(id)){
                ret = true;
            }
        }
        return ret;
    }

    private void evalBuecketSize() {
        stateBucketSize = guestSize(automaton.getDfaStateCount());
        singleDFABucketSize = automaton.getTables() == null ? 0 : guestSize(automaton.getTables().length);
        hookBucketSize = 23;
    }

    private int guestSize(int size) {
        if (size > 8192) {
            return 401;
        } else if (size > 2048) {
            return 103;
        } else if (size > 256) {
            return 37;
        } else if (size > 64) {
            return 11;
        } else if (size > 16) {
            return 3;
        } else {
            return 1;
        }
    }

    private void hookBegin(int charPos, int[] table, int transIndex, SimpleSet<IntQuad> nextHooks, SimpleIntSet next) {
        int index = binSearchKey(table, transIndex, PikeVMOpcodes.HOOK_BEGIN);
        if (index != -1) {
            //
            List<IntQuad> candiates = getHookCandidate(table[index + 2], charPos);
            if (candiates != null) {
                for (IntQuad quad : candiates) {
                    processHookEntry(quad, charPos, nextHooks, next);
                }
            }
        }

    }

    private void processHookEntry(IntQuad quad, int offset, SimpleSet<IntQuad> nextHooks, SimpleIntSet next) {
        int code = callDFAHookback(quad, offset);
        if (code == 0) {
            nextHooks.add(quad);
        } else if (code == 1) {
            next.add(quad.first);//????
        } else if (code == 2) {
            next.add(quad.first);//????
            nextHooks.add(quad);
        }
    }

    private int callDFAHookback(IntQuad quad, int offset) {
        if (hookCallback == null) {
            return -1;
        }
        return hookCallback.hook(0, 0, new int[]{quad.second, quad.third, quad.fourth, offset});
    }

    private List<IntQuad> getHookCandidate(int dfsState, int offset) {
        int[][] dfaTables = automaton.getDfaTables();
        int[] table = dfaTables[dfsState];
        //这个节点下所有边都是候选
        //寻找这个节点下所有的边
        int transIndex = table[0] + 1;
        assert (transIndex < table.length);
        List<IntQuad> ret = null;
        for (int i = transIndex; i < table.length; i += 3) {
            //边：
            int edge = table[i];
            int dest = table[i +2];

            int[] destTable = dfaTables[dest];

            IntQuad quad = new IntQuad();
            quad.first = destTable[destTable[0] + 1 +2];
            quad.second = edge;
            quad.third = destTable[destTable[0] +1];
            quad.fourth = offset;

            if (ret == null) {
                ret = new ArrayList<>((table.length - transIndex) /3);
            }
            ret.add(quad);

        }
        return ret;
    }

    private void acceptPendingItem(int id, int[][] dfaPendingStates, int stateCount, int[][] tables) {
        //这个节点是不是可以接受
        int[] stateIds = dfaPendingStates[id - stateCount];
        for (int stateId : stateIds) {
            int index = binSearchId(tables, stateId);
            if (index != -1){
                acceptPendingStateId(tables[index]);
            }
        }
    }

    private void acceptPendingStateId(int[] tables) {
        //这个节点是不是可以接受
        int dfaId = tables[1];
        if (dfaId != -1) {
            addCandidate(dfaId);
        }
    }

    private void doMatchPendingItem(int id, int pos, int[][] dfaPendingStates, int stateCount, int[][] tables) {
        int[] stateIds = dfaPendingStates[id - stateCount];
        //每个状态执行一下dfa
        //如果 stateIds 很大就不太好了
        //能否运行时再次生成 dfa 并 cache？？？
        for (int stateId : stateIds) {
            doMatchPendingStateId(stateId, pos, tables);
        }
    }

    private void doMatchPendingStateId(int stateId, int pos, int[][] tables) {
        SimpleIntSet states = new SimpleIntSet(singleDFABucketSize);
        SimpleIntSet next = new SimpleIntSet(singleDFABucketSize);
        states.add(stateId);

        SimpleSet<IntQuad> hooks = new SimpleSet<>(IntQuad.class, hookBucketSize);
        SimpleSet<IntQuad> nextHooks = new SimpleSet<>(IntQuad.class, hookBucketSize);

        //这个地方也需要弄 hook
        for (int i = pos; i < input.length; i++) {
            int index = i;
            hooks.foreach(hookEntry -> {
                processHookEntry(hookEntry, index, nextHooks, next);
            });

            states.foreach(id -> {
                int index2 = binSearchId(tables, id);
                if (index2 == -1) {
                    return;
                }
                int[] table = tables[index2];
                acceptPendingStateId(table);
                matchPosition(index, table, 2, next);
                hookPendingStateBegin(index, table, 2, hooks, next);
            });

            states.clear();
            SimpleIntSet.swap(states, next);

            hooks.clear();
            SimpleSet.swap(hooks, nextHooks);

            if (states.isEmpty() && hooks.isEmpty()) {
                break;
            }
        }
        if (!states.isEmpty()) {
            states.foreach(id -> {
                int index = binSearchId(tables, id);
                if (index == -1) {
                    return;
                }
                int[] table = tables[index];
                acceptPendingStateId(table);
            });
        }
    }

    private void hookPendingStateBegin(int charPos, int[] table, int transIndex, SimpleSet<IntQuad> nextHooks, SimpleIntSet next) {
        int index = binSearchKey(table, transIndex, PikeVMOpcodes.HOOK_BEGIN);
        if (index != -1) {
            //
            List<IntQuad> candidates = getPengdingStateHookCandidate(table[index + 2], charPos);
            if (candidates != null) {
                for (IntQuad quad : candidates) {
                    processHookEntry(quad, charPos, nextHooks, next);
                }
            }
        }

    }

    private List<IntQuad> getPengdingStateHookCandidate(int dfsState, int offset) {
        List<IntQuad> ret = null;

        int[][] dfaTables = automaton.getTables();
        int index = binSearchId(dfaTables, dfsState);
        if (index == -1) {
            return ret;
        }

        int[] table = dfaTables[index];

        //这个节点下所有边都是候选
        //寻找这个节点下所有的边
        int transIndex = 2;
        assert (transIndex < table.length);
        for (int i = transIndex; i < table.length; i += 3) {
            //边：
            int edge = table[i];
            int dest = table[i +2];

            int index2 = binSearchId(dfaTables, dest);
            int[] destTable = dfaTables[index2];

            IntQuad quad = new IntQuad();
            quad.first = destTable[2 +2];
            quad.second = edge;
            quad.third = destTable[2 +1];
            quad.fourth = offset;

            if (ret == null) {
                ret = new ArrayList<>((table.length - transIndex) /3);
            }
            ret.add(quad);

        }
        return ret;
    }

    private void matchPosition(int charPos, int[] table, int transIndex, SimpleIntSet next) {
        //检查这个位置的字符
        int index = binSearchKey(table, transIndex, input[charPos]);
        if (index != -1) {
            next.add(table[index + 2]);
        }
        //我们特殊处理了任意文本，现在将状态加回来
        index = binSearchKey(table, transIndex, PikeVMOpcodes.DOT);
        if (index != -1) {
            next.add(table[index + 2]);
        }
    }

    private void addCandidate(int id) {
        candidateIds.add(id);
    }

    boolean doMatchItem(int id) {
        int dfaId = automaton.getIds()[id];
        PikeVM vm = automaton.getVms()[id];

        resultAdapt.reset(dfaId);

        Function<int[], Integer> nfaCallback = getSingleNFACallback(dfaId);

        int offset = resultAdapt.end + ((resultAdapt.end == resultAdapt.start) ? 1 : 0);
        boolean ret = vm.matches(input, offset, input.length, false, false, nfaCallback, resultAdapt);
        if (ret && resultAdapt.ok) {
            resultAdapt.flush();
            while (ret && resultAdapt.end < input.length) {
                offset = resultAdapt.end + ((resultAdapt.end == resultAdapt.start) ? 1 : 0);
                ret = vm.matches(input, offset, input.length, false, false, nfaCallback, resultAdapt);
                if (ret) {
                    resultAdapt.flush();
                }
            }
            callback.hitEnd(dfaId);
            ret = true;
        }
        return ret;
    }

    Function<int[], Integer> getSingleNFACallback(int dfaId) {
        if (hookAdapt == null) {
            return null;
        }
        hookAdapt.reset(dfaId);
        return hookAdapt;
    }

    public static int binSearchId(int[][] table, int key) {
        int low = 0;
        int high = table.length - 1;
        int left = table[low][0], right = table[high][0];
        //边界优化
        if (key < left || key > right) {
            return -1;
        } else if (key == left) {
            return 0;
        } else if (key == right) {
            return high;
        }
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int id = table[mid][0];
            if (id == key) {
                return mid;
            } else if (id < key) {
                low = mid +1;
            } else {
                high = mid - 1;
            }
        }
        return -1;
    }

    public static int binSearchKey(int[] table, int beginIndex, int key) {
        int low = 0;
        int high = (table.length - beginIndex) / 3 - 1;

        //边界优化
        if (key < table[beginIndex] || key > table[table.length - 2]) {
            return -1;
        }
        if (key <= table[beginIndex + 1]) {
            return beginIndex;
        }

        if (key >= table[table.length - 3]) {
            return table.length - 3;
        }

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int pos = beginIndex + mid * 3;
            int lowVal = table[pos];
            int hiVal = table[pos +1];
            if (hiVal < key) {
                low = mid + 1;
            } else if (lowVal > key) {
                high = mid - 1;
            } else {
                return pos; // key found
            }
        }
        return -1;  // key not found.
    }

    static class HookCallbackAdapt implements Function<int[], Integer> {
        int dfaId;
        HookCallback callback;

        public HookCallbackAdapt(int dfaId, HookCallback callback) {
            this.dfaId = dfaId;
            this.callback = callback;
        }

        public void reset(int dfaId) {
            this.dfaId = dfaId;
        }

        @Override
        public Integer apply(int[] args) {
            //NFA
            return callback.hook(1, dfaId, args);
        }
    }

    static class ResultAdapt implements PikeVM.Result {
        int dfaId;

        MatcherCallback callback;
        boolean first = true;
        boolean ok = false;
        int start = -1;
        int end = -1;

        int[] start_group;
        int[] end_group;

        public ResultAdapt(int dfaId, MatcherCallback callback) {
            this.dfaId = dfaId;
            this.callback = callback;
        }

        public void reset(int dfaId) {
            this.dfaId = dfaId;
            first = true;
            ok = false;
            start = -1;
            end = -1;
            start_group = null;
            end_group = null;
        }

        @Override
        public void set(int[] start, int[] end) {
            this.start = start[0];
            this.end = end[0];
            this.start_group = start;
            this.end_group = end;
            if (first) {
                first = false;
                ok = callback.hit(dfaId);
            }
        }

        public void flush() {
            if (start_group != null && end_group != null) {
                callback.hitInfo(dfaId, start_group, end_group);
                start_group = null;
                end_group = null;
            }
        }
    }
}

