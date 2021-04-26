/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp;

import org.text.algorithm.regexp.fsm.*;
import org.text.algorithm.regexp.fsm.multi.MultiDFAHelper;
import org.text.algorithm.regexp.vm.PikeCompiler;
import org.text.algorithm.regexp.vm.PikeVM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class AutomatonBuilder {
    List<PikeVM> vms;
    List<DFA> dfaList;
    NFAHelper nfaHelper;
    PikeCompiler compiler;

    List<String> errorPatterns;

    /**
     * 编译正则， 调用者保证每个正则开头没有 .*, 结尾没有 .*
     *
     * @param stateLimit
     * @param patterns
     * @return
     */
    public Automaton build(int stateLimit, String... patterns) {
        vms = new ArrayList<>(patterns.length);
        dfaList = new ArrayList<>(patterns.length);
        errorPatterns = new ArrayList<>();
        nfaHelper = new NFAHelper();
        compiler = new PikeCompiler();

        int dfaBaseId = 0;
        int dfaId = 0;
        List<Integer> dfaIdList = new ArrayList(patterns.length);
        int size = 0;
        for (String regex : patterns) {
            dfaBaseId = buildPattern(regex, null, dfaBaseId);
            if (dfaList.size() > size) {
                dfaIdList.add(dfaId);
            }
            size = dfaList.size();
            dfaId++;
        }
        return buildAutomaton(stateLimit, dfaBaseId, dfaIdList);
    }

    /**
     *
     * @param stateLimit
     * @param patterns 要编译的正则
     * @param ids, 要编译正则的ids， 可以为null
     * @param names  正则的名称， 可以在编译的正则中用<name>引用名字，可以为null
     * @return
     */
    public Automaton build(int stateLimit, String[] patterns, int[] ids, String[] names) {
        vms = new ArrayList<>(patterns.length);
        dfaList = new ArrayList<>(patterns.length);
        errorPatterns = new ArrayList<>();
        nfaHelper = new NFAHelper();
        compiler = new PikeCompiler();

        Map<String, Object> allNames = new HashMap<>();
        compiler.setVMFactory(new PikeCompiler.ExpFactory() {
            @Override
            public Object lookupExp(String name) {
                return allNames.get(name);
            }

            @Override
            public List<Object> lookupExpSet(Predicate<String> predicate) {
                List<Object> result = new ArrayList<>();
                allNames.forEach((k, v) -> {
                    if (predicate.test(k)) {
                        result.add(v);
                    }
                });
                return result;

            }

            @Override
            public void putExp(String name, Object exp) {
                allNames.put(name, exp);
            }
        });

        int dfaBaseId = 0;
        int dfaId = 0;
        List<Integer> dfaIdList = new ArrayList(patterns.length);
        int size = 0;
        for (String regex : patterns) {
            int regId = ids == null ? dfaId : ids[dfaId];
            String name = names == null ? null : names[dfaId];
            dfaBaseId = buildPattern(regex, name, dfaBaseId);
            if (dfaList.size() > size) {
                dfaIdList.add(regId);
            }
            size = dfaList.size();
            dfaId++;
        }
        allNames.clear();
        return buildAutomaton(stateLimit, dfaBaseId, dfaIdList);
    }

    public Automaton build(int stateLimit, Map<Integer, String> patterns) {
        vms = new ArrayList<>(patterns.size());
        dfaList = new ArrayList<>(patterns.size());
        errorPatterns = new ArrayList<>();
        nfaHelper = new NFAHelper();
        compiler = new PikeCompiler();

        int dfaBaseId = 0;
        List<Integer> dfaIdList = new ArrayList(patterns.size());
        int size = 0;
        for (Map.Entry<Integer, String> entry : patterns.entrySet()) {
            dfaBaseId = buildPattern(entry.getValue(), null, dfaBaseId);
            if (dfaList.size() > size) {
                dfaIdList.add(entry.getKey());
            }
            size = dfaList.size();
        }
        return buildAutomaton(stateLimit, dfaBaseId, dfaIdList);
    }

    public List<String> getErrorPatterns() {
        return errorPatterns;
    }

    private Automaton buildAutomaton(int stateLimit, int dfaBaseId, List<Integer> dfaIdList) {
        MultiDFAHelper dfaHelper = new MultiDFAHelper(dfaList);
        if (stateLimit <= 0) {
            stateLimit = (dfaBaseId + dfaList.get(dfaList.size() - 1).getStates().length) * 2;
        }
        Automaton automaton = dfaHelper.buildMultiDfa(stateLimit);
        automaton.setVms(vms.toArray(new PikeVM[vms.size()]));
        automaton.setIds(buildIds(dfaIdList));
        return automaton;
    }

    private int[] buildIds(List<Integer> dfaIdList) {
        int[] ids = new int[dfaIdList.size()];
        int i = 0;
        for (int id : dfaIdList) {
            ids[i++] = id;
        }
        return ids;
    }
    private int buildPattern(String pattern, String name, int dfaBaseId) {
        PikeVM vm;
        try {
            vm = compiler.compileVM(pattern, name, name == null || !name.startsWith("_"));
        } catch (Exception e) {
            System.err.println(pattern);
            e.printStackTrace();
            errorPatterns.add(pattern);
            return dfaBaseId;
        }
        if (name != null && name.startsWith("_")) {
            return dfaBaseId;
        }

        DFA dfa;

        String str = vm.isPlainString();
        if (str != null) {
            dfa = buildPlainStringDfa(str, dfaBaseId);
        } else {
            PikeNFA nfa = new PikeNFA(vm);
            NFAState[] states = nfa.getStateList();
            nfaHelper.resetTo(states, 0);

            int dfaId = dfaList.size();
            nfaHelper.makeDfa(dfaId, dfaBaseId);
            dfa = nfaHelper.getDFA();
        }
        vms.add(vm);
        dfaList.add(dfa);
        dfaBaseId += dfa.getStates().length;
        return dfaBaseId;
    }

    private DFA buildPlainStringDfa(String str, int dfaBaseId) {
        DFAState[] dfaStates = new DFAState[str.length() + 1];
        DFAState root = dfaStates[0] = new DFAState(dfaBaseId);
        root.addTransition(new Range(-1), root);
        DFAState prev = root;
        for (int i = 0; i < str.length(); i++) {
            DFAState s = dfaStates[i + 1] = new DFAState(1 + i + dfaBaseId);
            prev.addTransition(new Range(str.charAt(i)), s);
            prev = s;
        }
        int dfaId = dfaList.size();
        dfaStates[dfaStates.length - 1].setAcceptId(dfaId);

        return new DFA(dfaId, root.getId(), dfaBaseId, dfaStates);
    }
}
