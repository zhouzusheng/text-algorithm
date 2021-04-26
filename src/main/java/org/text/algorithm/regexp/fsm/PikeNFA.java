/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp.fsm;

import org.text.algorithm.regexp.vm.CharacterMatcher;
import org.text.algorithm.regexp.vm.PikeVM;
import org.text.algorithm.regexp.vm.PikeVMOpcodes;

import java.util.*;
import java.util.function.Supplier;

public class PikeNFA implements PikeVMOpcodes {
    private NFAState[] stateList;

    private final PikeVM vm;
    /**
     * 用来将指令映射为连续的数字
     * 指令中只有部分对应到NFA 状态， 它们是不连续的
     */
    private Map<Integer, Integer> stateidMap;

    public PikeNFA(PikeVM vm) {
        this.vm = vm;
        stateidMap = makeStateIds();
        stateList = new NFAState[stateidMap.size()];
        for (int i = 0; i < stateList.length; i++) {
            stateList[i] = new NFAState(i);
        }
        makeStates();
    }

    public NFAState[] getStateList() {
        return stateList;
    }

    public PikeVM getVm() {
        return vm;
    }

    private Map<Integer, Integer> makeStateIds() {
        Set<Integer> ids = new HashSet();
        int[] program = vm.getProgram();

        LinkedList<Integer> stack = new LinkedList<>();
        ids.add(0);
        stack.push(0);

        while (!stack.isEmpty()) {
            int pc = stack.pollFirst();
            if (pc == program.length) {
                //end;
                continue;
            }
            int opcode = program[pc];
            switch (opcode) {
                case DOT:
                case DOTALL:
                case WORD_BOUNDARY:
                case NON_WORD_BOUNDARY:
                case LINE_START:
                case LINE_END: {
                    int next = pc + 1;
                    if (ids.add(next)) {
                        stack.add(next);
                    }
                    break;
                }
                case CHARACTER_CLASS:
                case LOOKAHEAD:
                case LOOKBEHIND:
                case NEGATIVE_LOOKAHEAD:
                case NEGATIVE_LOOKBEHIND:
                case HOOK_BEGIN:
                case HOOK_PROC:
                case INT_RANGE:
                case CHECK_RANGE:
                case SAVE_OFFSET: {
                    int next = pc + 2;
                    if (ids.add(next)) {
                        stack.add(next);
                    }
                    break;
                }
                case SPLIT:
                case SPLIT_JMP: {
                    int next = program[pc + 1];
                    if (ids.add(next)) {
                        stack.add(next);
                    }
                    next = pc + 2;
                    if (ids.add(next)) {
                        stack.add(next);
                    }
                    break;
                }
                case JMP: {
                    int next = pc + 1;
                    if (ids.add(next)) {
                        stack.add(next);
                    }
                    break;
                }
                default:
                    if (opcode >= 0 && opcode <= 0xffff) {
                        int next = pc + 1;
                        if (ids.add(next)) {
                            stack.add(next);
                        }
                    }
                    break;
            }
        }
        int[] idArray = new int[ids.size()];
        int i = 0;
        for (int id : ids) {
            idArray[i++] = id;
        }
        Arrays.sort(idArray);

        Map<Integer, Integer> map = new HashMap<Integer, Integer>(idArray.length);
        for (i = 0; i < idArray.length; i++) {
            map.put(idArray[i], i);
        }
        return map;
    }

    private void makeStates() {
        int[] program = vm.getProgram();
        CharacterMatcher[] ccc = vm.getClasses();
        Set ids = new HashSet();

        List<NFAState> dynaStates = new ArrayList<>();
        int dynaBaseId = stateList.length;

        Supplier<NFAState> dynaSupplier = () -> {
            NFAState state = new NFAState(dynaBaseId + dynaStates.size());
            dynaStates.add(state);
            return state;
        };

        LinkedList<Integer> stack = new LinkedList<>();
        ids.add(0);
        stack.push(0);
        while (!stack.isEmpty()) {
            int pc = stack.pollFirst();
            if (pc == program.length) {
                //end;
                stateList[stateidMap.get(pc)].setAccepted(true);
                continue;
            }
            int opcode = program[pc];
            NFAState current = stateList[stateidMap.get(pc)];
            switch (opcode) {
                case DOT:
                case DOTALL: {
                    //后面DFA计算时只考虑DOT
                    //为了控制状态数膨胀，我们不将DOT 展开，效果相当于最终的DFA查询必须特殊处理而与普通的DFA不同
                    int next = pc + 1;
                    current.transitionState(new Range(DOT), stateList[stateidMap.get(next)]);
                    if (ids.add(next)) {
                        stack.push(next);
                    }
                    break;
                }

                case WORD_BOUNDARY:
                case NON_WORD_BOUNDARY: {
                    //这两个虚拟字符我们直接忽略，这样DFA匹配会多召回。
                    // 但是DFA匹配完后得到的结果我们还会用具体的正则再次匹配，
                    // 单条匹配时会考虑，就不会出现错误
                    int next = pc + 1;
                    current.directState(stateList[stateidMap.get(next)]);
                    if (ids.add(next)) {
                        stack.push(next);
                    }
                    break;
                }
                case LINE_START: {
                    //这个虚拟字符我们直接忽略，这样DFA匹配会多召回。
                    // 留到单条匹配时会考虑
                    int next = pc + 1;
                    current.directState(stateList[stateidMap.get(next)]);
                    if (ids.add(next)) {
                        stack.push(next);
                    }
                    break;
                }
                case LINE_END: {
                    //这个虚拟字符我们直接忽略，留到单条匹配时会考虑
                    int next = pc + 1;
                    current.directState(stateList[stateidMap.get(next)]);
                    if (ids.add(next)) {
                        stack.push(next);
                    }
                    break;
                }
                case CHARACTER_CLASS: {
                    int next = pc + 2;
                    CharacterMatcher cm = ccc[program[pc + 1]];
                    //字符类别，这里将所有字符都展开了
                    //后续某步会将连续字符合并为区间
                    addTrans(current, stateList[stateidMap.get(next)], cm);
                    if (ids.add(next)) {
                        stack.push(next);
                    }
                    break;
                }
                case LOOKAHEAD: {
                    //前向匹配直接忽略
                    // 单条匹配时会考虑
                    int next = pc + 2;
                    current.directState(stateList[stateidMap.get(next)]);
                    if (ids.add(next)) {
                        stack.push(next);
                    }
                    break;
                }
                case LOOKBEHIND: {
                    int next = pc + 2;
                    current.directState(stateList[stateidMap.get(next)]);
                    if (ids.add(next)) {
                        stack.push(next);
                    }
                    break;
                }
                case NEGATIVE_LOOKAHEAD: {
                    int next = pc + 2;
                    current.directState(stateList[stateidMap.get(next)]);
                    if (ids.add(next)) {
                        stack.push(next);
                    }
                    break;
                }
                case NEGATIVE_LOOKBEHIND: {
                    int next = pc + 2;
                    current.directState(stateList[stateidMap.get(next)]);
                    if (ids.add(next)) {
                        stack.push(next);
                    }
                    break;
                }
                case HOOK_BEGIN: {
                    int begin = program[pc + 1];
                    int next = pc + 2;

                    int end = program[next + 1];
                    int next2 = next + 2;

                    NFAState right = stateList[stateidMap.get(next)];
                    NFAState middleState = dynaSupplier.get();

                    NFAState nextState = stateList[stateidMap.get(next2)];

                    current.transitionState(new Range(HOOK_BEGIN), middleState);

                    middleState.transitionState(new Range(begin), right);
                    right.transitionState(new Range(end), nextState);

                    if (ids.add(next2)) {
                        stack.push(next2);
                    }
                    break;
                }
                case INT_RANGE: {
                    int begin = program[pc + 1];
                    int next = pc + 2;

                    int end = program[next + 1];
                    int next2 = next + 2;
                    NFAState right = stateList[stateidMap.get(next)];
                    RangeHelper helper = new RangeHelper(begin, end, current, right, dynaSupplier);
                    helper.make();
                    right.directState(stateList[stateidMap.get(next2)]);
                    if (ids.add(next2)) {
                        stack.push(next2);
                    }
                    break;
                }
                case SAVE_OFFSET: {
                    //分组指令直接忽略
                    int next = pc + 2;
                    current.directState(stateList[stateidMap.get(next)]);
                    if (ids.add(next)) {
                        stack.push(next);
                    }
                    break;
                }

                case SPLIT: {
                    //分支指令就是跳转到两个指令
                    int next = program[pc + 1];
                    current.directState(stateList[stateidMap.get(next)]);
                    if (ids.add(next)) {
                        stack.push(next);
                    }
                    next = pc + 2;
                    current.directState(stateList[stateidMap.get(next)]);
                    if (ids.add(next)) {
                        stack.push(next);
                    }
                    break;
                }
                case SPLIT_JMP: {
                    //分支JMP指令就是跳转到两个指令， 在普通匹配中，第二个分支优先考虑。
                    // 但是DFA中不考虑优先级（贪心匹配之类的）
                    //到单个匹配时会考虑
                    int next = pc + 2;
                    current.directState(stateList[stateidMap.get(next)]);
                    if (ids.add(next)) {
                        stack.push(next);
                    }
                    next = program[pc + 1];
                    current.directState(stateList[stateidMap.get(next)]);
                    if (ids.add(next)) {
                        stack.push(next);
                    }
                    break;
                }
                case JMP: {
                    //硬跳转
                    int next = program[pc + 1];
                    current.directState(stateList[stateidMap.get(next)]);
                    if (ids.add(next)) {
                        stack.push(next);
                    }
                    break;
                }
                default:
                    if (opcode >= 0 && opcode <= 0xffff) {
                        //字符转换
                        int next = pc + 1;
                        current.transitionState(new Range(opcode), stateList[stateidMap.get(next)]);
                        if (ids.add(next)) {
                            stack.push(next);
                        }
                    }
                    break;
            }
        }
        if (!dynaStates.isEmpty()) {
            NFAState[] newStates = new NFAState[stateList.length + dynaStates.size()];
            for (NFAState s : stateList) {
                newStates[s.getId()] = s;
            }
            for (NFAState s : dynaStates) {
                newStates[s.getId()] = s;
            }
            stateList = newStates;
        }
    }

    private void addTrans(NFAState s1, NFAState s2, CharacterMatcher cm) {
        cm.addTrans(s1, s2);
    }

}
