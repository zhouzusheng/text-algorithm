/* Copyright (c) 2018-2020, Aitek Co.

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */
package org.text.algorithm.regexp;

import org.text.algorithm.regexp.vm.PikeVM;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.text.algorithm.regexp.SimpleSerializable.*;

/**
 * 尽量压缩存储 多模式DFA的数据
 */
public class Automaton implements SimpleSerializable {
    public static final int MAGIX = (19720122 << 2) | 3;

    public static final int VERSION_CURRENT = (1 << 8) | 1;

    /**
     * 版本
     */
    private int version = VERSION_CURRENT;

    /**
     * 每个dfa的id, 注意这个ID 是用户自己随便指定的， 最好保证不冲突
     * 下面的数据结构中提到的dfa id 不是这个id， 是dfa 列表中的顺序编号
     * ids 这个数组只有在输出结果时用到， 算法内部不用
     */
    private int[] ids;

    //记录一下 dfaStateCount， 就是dfaTables 的第一维大小
    private int dfaStateCount;

    /**
     * 所有dfa 编译的一个整体DFA的状态表
     * 第一维是所有的状态，下标就是状态id
     * 第二维
     * 第一个数据 是一个数字，0表示这个节点不是accept的，非0 代表 后面几个数字是accept的 dfa 的id
     * 再后面的数据代表状态的转换边， 是多个三元组
     * 三元组含义： 边的 min, max, 转换到的目标状态
     **/
    private int[][] dfaTables;

    /**
     * 注意：我们的DFA有可能太大，无法完整构造出所有状态，这里保留还没有构造出来整体DFA状态的原来状态id
     * 第一维表示等待构造的DFA的状态ID（表示方法：状态id = dfaStateCount + 下标）
     * 第二维表示这个状态对应的单个DFA 中的状态集合
     * Google 的re2 所有状态都是等待构造的，我们参考网上文章，尽量构造出前面的很多状态
     * 如果尾部状态实在太多，就保留起来，查询时按需构造。（re2 把按需构造的状态缓存起来，我们考虑是否需要这样做）
     * 用这个方法，我们理论上支持的规则数大大增加
     * 可惜java 的数组下标只能是整数， 如果是long， 简直就可以支持无限了
     * 这也意味着长文本匹配效率较低一些
     **/
    private int[][] dfaPendingStates;

    /**
     * 状态数超过限制时的还需要用到的各DFA的部分状态。 第一维是所有的状态， 大小是所有还需用到的单DFA状态的和
     * 第二维 第一个数字代表状态 id， 第二个数字如果不是-1 就代表accept，并且值为所属的DFA的id。
     * 之后的数据是是三元组格式，因此大小是 该状态的边个数*3 + 2
     * 三元组含义： 边的 min, max, 转换到的目标状态
     * 当 dfaPendingStates == null 时 tables 也 为 null
     */
    private int[][] tables;

    /**
     * 每一个DFA 的原始 vm（vm 是比nfa 更简单的匹配方法）
     */
    private PikeVM[] vms;


    public int[][] getDfaTables() {
        return dfaTables;
    }

    public void setDfaTables(int[][] dfaTables) {
        this.dfaTables = dfaTables;
    }

    public int getDfaStateCount() {
        return dfaStateCount;
    }

    public void setDfaStateCount(int dfaStateCount) {
        this.dfaStateCount = dfaStateCount;
    }

    public int[][] getDfaPendingStates() {
        return dfaPendingStates;
    }

    public void setDfaPendingStates(int[][] dfaPendingStates) {
        this.dfaPendingStates = dfaPendingStates;
    }

    public int[][] getTables() {
        return tables;
    }

    public void setTables(int[][] tables) {
        this.tables = tables;
    }

    public PikeVM[] getVms() {
        return vms;
    }

    public void setVms(PikeVM[] vms) {
        this.vms = vms;
    }

    public void setIds(int[] ids) {
        this.ids = ids;
    }

    public int[] getIds() {
        return ids;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @Override
    public void save(DataOutput out) throws IOException {
        out.writeInt(MAGIX);
        out.writeInt(version);
        writeOneDimTables(out, ids);
        out.writeInt(dfaStateCount);
        writeTwoDimTables(out, dfaTables);
        writeTwoDimTables(out, dfaPendingStates);
        writeTwoDimTables(out, tables);
        saveObjects(out, vms);
    }

    @Override
    public void load(DataInput input) throws IOException {
        if (input.readInt() != MAGIX) {
            throw new IOException("invalid magic");
        }
        version = input.readInt();
        if (version != VERSION_CURRENT) {
            //TODO:
        }
        ids = loadOneDimTables(input);
        dfaStateCount = input.readInt();
        dfaTables = loadTwoDimTables(input);
        dfaPendingStates = loadTwoDimTables(input);
        tables = loadTwoDimTables(input);
        vms = loadObjects(PikeVM.class, input, () -> new PikeVM());
    }
}
