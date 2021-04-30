package org.text.algorithm.automa;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;
import java.util.TreeSet;

@Getter
@Setter
public class SparseDfa {

    public static final int error_state = 0;

    /**
     * 多个dfa的并集， ids 保存每个dfa 的 id
     */
    private Object[] ids;

    private int[] base;        // indexed by state
    private int[] check;       // indexed by offset
    private int[] next;        // indexed by offset
    private int[] accepts;     // indexed by accept ids

    private int number_of_states; // length of the table |base|
    private int table_size;       // length of the tables |check| and |next|
    private int accepts_size;     // length of accepts
    private int max_check;
    private int max_state;

    private int min_symbol;    // minimum symbol value
    private int max_symbol;    // maximum symbol value


    public Set<Integer> matchIds(String text, int start) {
        Set<Integer> r = new TreeSet<>();
        int b = 0;
        int o = 0;
        int p;
        for (int i = start; i < text.length(); i++) {
            o = base[b];
            p = o + text.charAt(i);
            if (p < max_check && (b+1) == check[p]) {
                b = next[p];
            }
            else
                return r;
        }
        o = base[b];
        p = o;
        int n = next[o];
        if ((b+1) == check[p] && n < 0) {
            int pos = -n - 1;
            int count = accepts[pos];
            for(int i = 0; i < count; i++) {
                r.add(accepts[pos+i+1]);
            }
        }
        return r;
    }
}
