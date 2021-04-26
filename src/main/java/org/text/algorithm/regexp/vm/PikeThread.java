package org.text.algorithm.regexp.vm;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Function;

public class PikeThread {

    /**
     * The current thread states.
     * <p>
     * The threads are identified by their program counter. The rationale: as all
     * threads are executed in lock-step, i.e. for the same character in the
     * string to be matched, it does not make sense for two threads to be at the
     * same program counter -- they would both do exactly the same for the rest of
     * the execution.
     * </p>
     * <p>
     * For efficiency, the threads are kept in a linked list that actually lives
     * in an array indexed by the program counter, pointing to the next thread's
     * program counter, in the order of high to low priority.
     * </p>
     * <p>
     * Program counters which have no thread associated thread are marked as -1.
     * The program counter associated with the least-priority thread (the last one
     * in the linked list) is marked as -2 to be able to tell it apart from
     * unscheduled threads.
     * </p>
     * <p>
     * We actually never need to have an explicit value for the priority, the
     * ordering is sufficient: whenever a new thread is to be scheduled and it is
     * found to be scheduled already, it was already scheduled by a
     * higher-priority thread.
     * </p>
     */
    public static class ThreadQueue {
        private int[] program;
        private int groupCount;
        private int offsetsCount;

        private int head, tail;
        // next[pc] is 1 + the next thread's pc
        private int[] next;
        // offsets[pc][2 * group] is 1 + start offset
        private int[][] offsets;

        public ThreadQueue(PikeVM vm) {
            program = vm.getProgram();
            groupCount = vm.getGroupCount();
            offsetsCount = vm.getOffsetsCount();

            head = tail = -1;
            next = new int[program.length + 1];
            offsets = new int[program.length + 1][];
        }

        public ThreadQueue(PikeVM vm, int startPC) {
            program = vm.getProgram();
            groupCount = vm.getGroupCount();
            offsetsCount = vm.getOffsetsCount();

            head = tail = startPC;
            next = new int[program.length + 1];
            offsets = new int[program.length + 1][];
            offsets[head] = new int[offsetsCount];
        }

        public int queueOneImmediately(ThreadQueue into) {
            for (; ; ) {
                if (head < 0) {
                    return -1;
                }
                boolean wasQueued = queueNext(head, head, into);
                int pc = head;
                if (head == tail) {
                    head = tail = -1;
                } else {
                    head = next[pc] - 1;
                    next[pc] = 0;
                }
                offsets[pc] = null;
                if (wasQueued) {
                    into.tail = pc;
                    return pc;
                }
            }
        }

        public boolean queueImmediately(int currentPC, int nextPC,
                                        boolean copyThreadState) {
            if (isScheduled(nextPC)) {
                return false;
            }
            int[] offsets = this.offsets[currentPC];
            if (copyThreadState) {
                offsets = java.util.Arrays.copyOf(offsets, offsetsCount);
            }
            if (currentPC == tail) {
                tail = nextPC;
            } else {
                next[nextPC] = next[currentPC];
            }
            this.offsets[nextPC] = offsets;
            next[currentPC] = nextPC + 1;
            return true;
        }

        public void copyThreadState(int nextPC) {
            int[] offsets = this.offsets[nextPC];
            if (offsets != null) {
                offsets = java.util.Arrays.copyOf(offsets, offsetsCount);
                this.offsets[nextPC] = offsets;
            }
        }

        public boolean queueNext(int currentPC, int nextPC, ThreadQueue next) {
            if (next.tail < 0) {
                next.head = nextPC;
            } else if (next.isScheduled(nextPC)) {
                return false;
            } else {
                next.next[next.tail] = nextPC + 1;
            }
            next.offsets[nextPC] = offsets[currentPC];
            next.tail = nextPC;
            return true;
        }

        public void saveOffset(int pc, int index, int offset) {
            offsets[pc][index] = offset + 1;
        }

        public void setResult(PikeVM.Result result) {
            // copy offsets
            int[] offsets = this.offsets[program.length];
            int[] groupStart = new int[groupCount + 1];
            int[] groupEnd = new int[groupCount + 1];
            for (int j = 0; j <= groupCount; ++j) {
                groupStart[j] = offsets[2 * j] - 1;
                groupEnd[j] = offsets[2 * j + 1] - 1;
            }
            result.set(groupStart, groupEnd);
        }

        void mustStartMatchAt(int start) {
            int previous = -1;
            for (int pc = head; pc >= 0; ) {
                int nextPC = next[pc] - 1;
                if (start + 1 == offsets[pc][0]) {
                    previous = pc;
                } else {
                    next[pc] = 0;
                    offsets[pc] = null;
                    if (pc == tail) {
                        head = tail = -1;
                    } else if (previous < 0) {
                        head = nextPC;
                    } else {
                        next[previous] = 1 + nextPC;
                    }
                }
                pc = nextPC;
            }
        }

        int startOffset(int pc) {
            return offsets[pc][0] - 1;
        }

        public boolean isEmpty() {
            return head < 0;
        }

        public boolean isScheduled(int pc) {
            return pc == tail || next[pc] > 0;
        }

        public int next(int pc) {
            return pc < 0 ? head : next[pc] - 1;
        }

        public void clean() {
            for (int pc = head; pc >= 0; ) {
                int nextPC = next[pc] - 1;
                next[pc] = 0;
                offsets[pc] = null;
                pc = nextPC;
            }
            head = tail = -1;
        }
    }

    private interface Value<T> {
        T get();

        void set(T v);

        void remove();
    }

    private static class ListValue<T> implements Value<T> {
        private ListIterator<T> it;
        private T value;

        public ListValue(ListIterator<T> it) {
            this.it = it;
        }

        public void reset(T v) {
            value = v;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public void set(T v) {
            value = v;
            it.set(v);
        }

        @Override
        public void remove() {
            it.remove();
        }
    }

    private static abstract class AbstractHookData<T> {
        protected int para1;
        protected int para2;
        protected LinkedList<T> values;

        public AbstractHookData(int para1, int para2) {
            this.para1 = para1;
            this.para2 = para2;
            this.values = new LinkedList<>();
        }

        public void addData(T value) {
            values.add(value);
        }

        public void clear() {
            values.clear();
        }

        public int getPara1() {
            return para1;
        }

        public int getPara2() {
            return para2;
        }

        public abstract int execute(Value<T> value, char[] characters, int offset);
    }

    private static class IntRangeData extends AbstractHookData<Integer> {
        public IntRangeData(int min, int max) {
            super(min, max);
        }

        public int getMin() {
            return para1;
        }

        public int getMax() {
            return para2;
        }

        @Override
        public int execute(Value<Integer> value, char[] characters, int offset) {
            char ch = characters[offset];
            if (ch < '0' || ch > '9') {
                return -1;
            }
            int v = ch - '0';
            v = value.get() * 10 + v;
            if (v < para1) {
                value.set(v);
                return 0;
            }
            if (v < para2) {
                value.set(v);
                return 2;
            }
            if (v == para2) {
                value.set(v);
                return 1;
            }
            //太大
            return -1;
        }
    }

    private static class HookProcData extends AbstractHookData<Integer> {
        private Function<int[], Integer> hookBack;

        public HookProcData(int left, int right, Function<int[], Integer> hookBack) {
            super(left, right);
            this.hookBack = hookBack;
        }

        public int getLeft() {
            return para1;
        }

        public int getRight() {
            return para2;
        }

        @Override
        public int execute(Value<Integer> value, char[] characters, int offset) {
            int[] data = {getLeft(), getRight(), value.get(), offset};
            return hookBack.apply(data);
        }
    }

    public static class HookContainerThread {
        protected char[] characters;
        protected Map<Integer, AbstractHookData<Integer>> stack;
        private Function<int[], Integer> hookBack;

        public HookContainerThread(char[] characters, Function<int[], Integer> hookBack) {
            this.characters = characters;
            this.hookBack = hookBack;
            stack = new HashMap<>();
        }

        public void addIntRange(int pc, int nextPC, int offset, int begin, int end) {
            AbstractHookData<Integer> data = stack.get(nextPC);
            if (data != null) {
                if (data.getPara1() != begin || data.getPara2() != end) {
                    throw new IllegalStateException("int range error");
                }
            } else {
                data = new IntRangeData(begin, end);
                stack.put(nextPC, data);
            }
            data.addData(0);
        }

        public void addHook(int pc, int nextPC, int offset, int begin, int end) {
            AbstractHookData<Integer> data = stack.get(nextPC);
            if (data != null) {
                if (data.getPara1() != begin || data.getPara2() != end) {
                    throw new IllegalStateException("hook error");
                }
            } else {
                data = new HookProcData(begin, end, hookBack);
                stack.put(nextPC, data);
            }
            data.addData(offset);
        }

        public void hookProc(int pc, int nextPC, int offset, ThreadQueue current, ThreadQueue next) {
            AbstractHookData<Integer> data = stack.get(pc);
            if (data == null) {
                return;
            }
            if (offset >= characters.length) {
                data.clear();
                stack.remove(pc);
                return;
            }
            boolean continueHook = false;
            boolean succHook = false;

            ListIterator<Integer> it = data.values.listIterator();
            ListValue<Integer> value = new ListValue<Integer>(it);
            while (it.hasNext()) {
                value.reset(it.next());
                int code = callHook(data, value, offset);
                switch (code) {
                    case 0:
                        continueHook = true;
                        break;
                    case 1:
                        it.remove();
                        succHook = true;
                        break;
                    case 2:
                        continueHook = true;
                        succHook = true;
                        break;
                    default:
                        it.remove();
                }
            }

            if (data.values.isEmpty()) {
                stack.remove(pc);
            }
            if (continueHook) {
                current.queueNext(pc, pc, next);
            }
            if (succHook) {
                current.queueNext(pc, nextPC, next);
                next.copyThreadState(nextPC);
            }
        }

        private int callHook(AbstractHookData<Integer> data, ListValue<Integer> value, int offset) {
            return data.execute(value, characters, offset);
        }
    }
}