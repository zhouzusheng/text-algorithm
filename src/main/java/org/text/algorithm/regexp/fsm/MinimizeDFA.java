/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp.fsm;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;

public class MinimizeDFA {
    private int minId;
    private int rootId;
    private List<DFAState> states;

    //for DEBUG
    private int splitCalls = 0;
    private int successfulSplits = 0;

    private int nextId;

    List<DFAState> newStates;
    int newRootId;

    public MinimizeDFA(int minId, int rootId, List<DFAState> states) {
        this.minId = minId;
        this.rootId = rootId;
        this.states = states;
    }

    public void minimize() {
        nextId = minId;

        newStates = new ArrayList<>(states.size());

        removeDeadTransitions();
        Map<DFAState, Set<DFAState>> partition = createPartition();
        Map<Set<DFAState>, DFAState> newDFAMap = new IdentityHashMap<>();

        for (DFAState original : states) {
            DFAState minimized = getOrCreateMinimizedState(partition, newDFAMap, original);
            if (minimized == null) {
                continue;
            }
            for (TransitionT<DFAState> transition : original.getTransitions()) {
                DFAState next = getOrCreateMinimizedState(partition, newDFAMap, transition.getTo());
                if (next != null) {
                    minimized.addTransition(transition.getRange(), next);
                }
            }
        }
        DFAState minimal = newDFAMap.get(partition.get(states.get(0)));
        newRootId = minimal.getId();

        reduce(newStates);
    }

    private void reduce(List<DFAState> states) {
        for (DFAState state : states) {
            List<TransitionT<DFAState>> old_trans = state.getTransitions();

            Map<DFAState, TreeSet<Range>> group_trans = old_trans.stream().collect(
                    Collectors.groupingBy(TransitionT::getTo, mapping(TransitionT::getRange, toCollection(TreeSet::new))
                    ));

            old_trans.clear();
            group_trans.forEach((k, v) -> {
                List<Range> ranges = Range.compact(v);
                ranges.stream().map(r -> new TransitionT(r, k)).forEach(t -> old_trans.add(t));
            });
            old_trans.sort(Comparator.comparing(it -> it.getRange()));
        }
    }


    private void removeDeadTransitions() {
        Set<DFAState>[] map = new Set[states.size()];
        for (int i = 0; i < states.size(); i++) {
            map[i] = new HashSet<>();
        }
        for (int i = 0; i < states.size(); i++) {
            DFAState s = states.get(i);
            List<TransitionT<DFAState>> transitions = s.getTransitions();
            if (transitions != null) {
                for (TransitionT t : transitions) {
                    map[t.getTo().getId()].add(s);
                }
            }
        }

        LinkedList<DFAState> working = new LinkedList<>();

        DFAState.walkState(states.get(rootId), s -> {
            if (s.getAcceptId() != -1) {
                working.add(s);
            }
        }, null);

        boolean[] live = new boolean[states.size()];
        Set<DFAState> visited = new HashSet<>(working);
        while (!working.isEmpty()) {
            DFAState s = working.pollFirst();
            live[s.getId()] = true;
            for (DFAState from : map[s.getId()]) {
                if (visited.add(from)) {
                    working.add(from);
                }
            }
        }

        for (int i = 0; i < live.length; i++) {
            if (!live[i]) {
                states.get(i).getTransitions().clear();
                Set<DFAState> set = map[i];
                for (DFAState s : set) {
                    List<TransitionT<DFAState>> trans = s.getTransitions();
                    if (!trans.isEmpty()) {
                        List<TransitionT<DFAState>> trans2 = new ArrayList<>(trans.size() - 1);
                        for (TransitionT t : trans) {
                            if (t.getTo().getId() != i) {
                                trans2.add(t);
                            }
                        }
                        s.setTransitions(trans2);
                    }
                }
            }
        }
        reduce(states);
    }

    private DFAState getOrCreateMinimizedState(Map<DFAState, Set<DFAState>> partition, Map<Set<DFAState>, DFAState> newDFAMap, DFAState original) {
        Set<DFAState> set = partition.get(original);
        if (set == null) {
            return null;
        }
        DFAState minimized = newDFAMap.get(set);
        if (minimized == null) {
            minimized = new DFAState(nextId++);
            newStates.add(minimized);
            minimized.setAcceptId(original.getAcceptId());
            newDFAMap.put(set, minimized);
        }
        return minimized;
    }

    private Map<DFAState, Set<DFAState>> createPartition() {
        PartitionState partition = initialPartition();
        boolean changed = true;
        int currentIteration = 0;
        while (changed) {
            currentIteration++;
            changed = false;
            List<DFAGroup> changedItems = new ArrayList<>();
            while (partition.queue.peek() != null) {
                DFAGroup set = partition.queue.poll();
                if (set.size() > 1) {
                    Optional<List<Set<DFAState>>> splitted = split(partition.partition, set.dfas);
                    if (splitted.isPresent()) {
                        changed = true;
                        for (Set<DFAState> part : splitted.get()) {
                            DFAGroup dfaGroup = new DFAGroup(part);
                            changedItems.add(dfaGroup);
                            for (DFAState thing : part) {
                                partition.partition.set(thing.getId(), dfaGroup);
                            }
                        }
                        partition.queue.addAll(changedItems);
                        break;
                    } else {
                        set.lastConsidered = currentIteration;
                        changedItems.add(set);
                    }
                }
            }
        }
        return unwrap(partition.partition);
    }

    private Map<DFAState, Set<DFAState>> unwrap(List<DFAGroup> partition) {
        Map<DFAState, Set<DFAState>> unwrapped = new HashMap<>();
        for (DFAState dfaState : states) {
            DFAGroup group = partition.get(dfaState.getId());
            if (group != null) {
                unwrapped.put(dfaState, group.dfas);
            }
        }
        return unwrapped;
    }

    private Optional<List<Set<DFAState>>> split(List<DFAGroup> partition, Set<DFAState> set) {
        splitCalls++;
        Iterator<DFAState> dfa = set.iterator();
        DFAState first = dfa.next();
        Set<DFAState> other = new HashSet<>(set.size());
        for (DFAState second : set) {
            if (second != first && !equivalent(partition, first, second)) {
                other.add(second);
            }
        }
        if (!other.isEmpty()) {
            List<Set<DFAState>> splitted = new ArrayList<>();
            splitted.add(other);
            set.removeAll(other);
            splitted.add(set);
            successfulSplits++;
            return Optional.of(splitted);
        }
        return Optional.empty();
    }

    private boolean equivalent(List<DFAGroup> partition, DFAState first, DFAState second) {
        Iterator<TransitionT<DFAState>> firstIter = first.getTransitions().iterator();
        Iterator<TransitionT<DFAState>> secondIter = second.getTransitions().iterator();
        while (firstIter.hasNext()) {
            TransitionT firstTransition = firstIter.next();
            TransitionT secondTransition = secondIter.next();
            if (!firstTransition.getRange().equals(secondTransition.getRange())) {
                return false;
            } else if (firstTransition.getTo() != secondTransition.getTo()) {
                if (partition.get(firstTransition.getTo().getId()) != partition.get(secondTransition.getTo().getId())) {
                    return false;
                }
            }
        }
        return true;
    }

    private PartitionState initialPartition() {
        List<DFAGroup> partition = new ArrayList<>(states.size());
        for (int i = 0; i < states.size(); i++) {
            partition.add(null);
        }

        Set<DFAState> accepting = new HashSet<>();
        Set<DFAState> nonAccepting = new HashSet<>(states.size());
        acceptingStates(accepting, nonAccepting);

        List<DFAGroup> dfaGroups = new ArrayList<>();
        if (!accepting.isEmpty()) {
            dfaGroups.addAll(partitionStates(partition, accepting));
        }
        if (!nonAccepting.isEmpty()) {
            dfaGroups.addAll(partitionStates(partition, nonAccepting));
        }
        return new PartitionState(partition, new PriorityQueue<>(dfaGroups));
    }

    private List<DFAGroup> partitionStates(List<DFAGroup> partition, Set<DFAState> input) {
        Map<Integer, Set<DFAState>> acceptingByTransitionCount = new HashMap<>();
        List<DFAGroup> dfaGroups = new ArrayList<>();
        for (DFAState acceptingDFA : input) {
            Set<DFAState> set = acceptingByTransitionCount.computeIfAbsent(acceptingDFA.getTransitions().size(), (s) -> new HashSet<>());
            set.add(acceptingDFA);
        }
        for (Map.Entry<Integer, Set<DFAState>> e : acceptingByTransitionCount.entrySet()) {
            DFAGroup group = new DFAGroup(e.getValue());
            dfaGroups.add(group);
            for (DFAState acceptingDFA : e.getValue()) {
                partition.set(acceptingDFA.getId(), group);
            }
        }
        return dfaGroups;
    }

    private void acceptingStates(Set<DFAState> accepting, Set<DFAState> nonAccepting) {
        DFAState.walkState(states.get(rootId), s -> {
            if (s.getAcceptId() == -1) {
                nonAccepting.add(s);
            } else {
                accepting.add(s);
            }
        }, null);
    }

    static class DFAGroup implements Comparable<DFAGroup> {

        Set<DFAState> dfas;
        int lastConsidered = 0;

        DFAGroup(Set<DFAState> dfas) {
            this.dfas = dfas;
        }

        public int size() {
            return dfas.size();
        }

        public int compareTo(DFAGroup other) {
            // Without these size checks, we'd experience a mild-slowdown where long series of very small sets were
            // considered, despite the low probability that they'll provide successful splits. It seems like there
            // should be a lot of room to improve on here
            if (this.lastConsidered > other.lastConsidered && other.size() > 2) {
                return 1;
            } else if (this.lastConsidered < other.lastConsidered && this.size() > 2) {
                return -1;
            } else if (this.size() > other.size()) {
                return -1;
            } else if (this.size() < other.size()) {
                return 1;
            }
            return 0;
        }

        @Override
        public String toString() {
            return "DFAGroup{" +
                    "dfasSize=" + dfas.size() +
                    ", lastConsidered=" + lastConsidered +
                    '}';
        }
    }

    static class PartitionState {
        List<DFAGroup> partition;
        PriorityQueue<DFAGroup> queue;

        PartitionState(List<DFAGroup> partition, PriorityQueue<DFAGroup> queue) {
            this.partition = partition;
            this.queue = queue;
        }
    }
}
