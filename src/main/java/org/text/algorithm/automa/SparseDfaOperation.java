package org.text.algorithm.automa;

import java.util.*;

public class SparseDfaOperation {

    private DfaCompose compose;

    private int[] base;        // indexed by state
    private int[] check;       // indexed by offset
    private int[] next;        // indexed by offset
    private int[] accepts;     // indexed by accepts

    private int number_of_states; // length of the table |base|
    private int table_size;       // length of the tables |check| and |next|
    private int accepts_size;     // length of accepts
    private int max_check;
    private int max_state;

    private int min_symbol;    // minimum symbol value
    private int max_symbol;    // maximum symbol value

    private int offset_base;

    private Map<Integer, Integer> accepts_maps;


    public SparseDfaOperation(DfaCompose compose) {
        this.compose = compose;
        this.offset_base = 0;

    }

    public SparseDfa sparse() {
        offset_base = 0;

        int[][] dfaTables = compose.getDfaTables();
        evalMinMaxSymbol(dfaTables);
        number_of_states = dfaTables.length;

        int number_of_symbols = max_symbol - min_symbol + 1;
        createTables(4 * number_of_symbols, number_of_states);
        evalAccepts(compose.getDfaAccepts());
        evalStates(dfaTables);
        SparseDfa dfa = new SparseDfa();
        dfa.setIds(compose.getIds());
        dfa.setAccepts(accepts);
        dfa.setMin_symbol(min_symbol);
        dfa.setMax_symbol(max_symbol);
        dfa.setMax_state(max_state);
        dfa.setAccepts_size(accepts_size);
        dfa.setTable_size(table_size);
        dfa.setNumber_of_states(number_of_states);
        dfa.setBase(base);
        dfa.setCheck(check);
        dfa.setNext(next);
        dfa.setMax_check(max_check);
        return dfa;
    }

    private void evalMinMaxSymbol(int[][] dfaTables) {
        min_symbol = Integer.MAX_VALUE;    // minimum symbol value
        max_symbol = Integer.MIN_VALUE;    // maximum symbol value

        for (int[] tables : dfaTables) {
            if (tables != null) {
                table_size += tables.length / 2;
                for (int i = 0; i < tables.length; i+=2) {
                    int symbol = tables[i];
                    if (symbol > max_symbol) {
                        max_symbol = symbol;
                    }
                    if (symbol < min_symbol) {
                        min_symbol = symbol;
                    }
                }
            }
        }
    }

    private void createTables(int size, int states) {
        table_size = size;
        number_of_states = states;
        max_check = 0;
        max_state = 0;
        base = new int[number_of_states];
        check = new int[table_size];
        next = new int[table_size];
    }

    private void evalAccepts(int[][] dfaAccepts) {
        accepts_size = 0;
        accepts_maps = new HashMap<>();

        ArrayList<Integer> accepts_ids = new ArrayList<>(1000);
        Map<TreeSet<Integer>, Integer> accepts_set = new HashMap<>();

        for (int i = 0; i < dfaAccepts.length; i++) {
            int[] accepts = dfaAccepts[i];
            if (accepts == null || accepts.length == 0) {
                continue;
            }
            TreeSet<Integer> set = new TreeSet<>();
            for (int id : accepts) {
                set.add(id);
            }
            Integer pos = accepts_set.get(set);
            if (pos != null) {
                accepts_maps.put(i, pos);
            } else {
                pos = accepts_ids.size();
                accepts_ids.add(set.size());
                for (int id : set) {
                    accepts_ids.add(id);
                }
                accepts_set.put(set, pos);
                accepts_maps.put(i, pos);
            }
        }
        accepts_size = accepts_ids.size();
        accepts = new int[accepts_size];
        for (int i = 0; i < accepts_size; i++) {
            accepts[i] = accepts_ids.get(i);
        }
    }

    private void evalStates(int[][] dfaTables) {
        for (int i = 0; i < dfaTables.length; i++) {
            add_state(i, dfaTables[i]);
        }
    }

    private void grow_tables(int increment)
    {
        int[] new_check = new int [ table_size + increment ];
        int[] new_next  = new int [ table_size + increment ];
        System.arraycopy(check, 0, new_check, 0, check.length);
        System.arraycopy(next, 0, new_next, 0, next.length);

        check = new_check;
        next  = new_next;
        table_size += increment;
    }

    private void add_state(int s, int[] trans) {
        if (s > max_state) {
            max_state = s;
        }
        Integer id_pos = accepts_maps.get(s);
        if ((trans == null || trans.length == 0) && (id_pos == null)) {
            return;
        }

        //
        // Now fit in the rest using a simple first fit strategy.  Worst case
        // complexity is O(n^2).  However, since the transitions are sparse
        // we expect the average complexity to be better.
        //
        {
            int i, limit;
            for (i = offset_base, limit = table_size; i < limit; i++) {
                if (check[i] == SparseDfa.error_state)
                    break;
            }
            offset_base = i;
        }


        int offset = Math.max(-min_symbol + offset_base, 0);
        for (; ; ) {
            int limit = table_size - max_symbol - 1;
            if (limit <= offset)
                grow_tables(table_size * 3 / 2 + offset - limit);

            //
            // Use linear search on the tables
            //

            for (; offset < limit; offset++) {
                boolean try_again =  id_pos == null ? false : (check[offset] != SparseDfa.error_state);
                if(trans != null && !try_again) {
                    for (int i = trans.length - 2; i >= 0; i -= 2) {
                        if (check[offset + trans[i]] != SparseDfa.error_state) {
                            try_again = true;
                            break;
                        }

                    }
                }
                if (!try_again) {
                    base[s] = offset;
                    if(id_pos != null) {
                        check[offset] = s + 1;
                        next[offset] = - id_pos -1;
                        if (offset > max_check) {
                            max_check = offset;
                        }
                    }
                    if(trans != null) {
                        for (int i = trans.length - 2; i >= 0; i -= 2) {
                            int symbol = trans[i];
                            check[offset + symbol] = s + 1;
                            next[offset + symbol] = trans[i + 1];
                            if (offset + max_symbol > max_check) {
                                max_check = offset + max_symbol;
                            }
                        }
                    }
                    return;
                }
            }
        }
    }
}
