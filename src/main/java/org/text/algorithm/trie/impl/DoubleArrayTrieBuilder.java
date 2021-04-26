package org.text.algorithm.trie.impl;

import org.text.algorithm.trie.TrieBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

//代码来自
//https://github.com/komiya-atsushi/darts-java
//是一个日本人将c++实现转换为java 实现
//缺点: 要求输入是排序的!!!!
public class DoubleArrayTrieBuilder implements TrieBuilder {

	protected int check[];
	protected int base[];

	protected boolean used[];
	protected int size;
	protected int allocSize;
	protected List<String> key;
	protected int keySize;
	protected int length[];
	protected int value[];
	protected int progress;
	protected int nextCheckPos;
	
	protected TrieNodefactory nodeFactory;
	
	// boolean no_delete_;
	protected int error_;

	public DoubleArrayTrieBuilder() {
		check = null;
		base = null;
		used = null;
		size = 0;
		allocSize = 0;
		// no_delete_ = false;
		error_ = 0;
		this.nodeFactory = TrieNode.factory;
	}
	public DoubleArrayTrieBuilder(TrieNodefactory nodeFactory) {
		check = null;
		base = null;
		used = null;
		size = 0;
		allocSize = 0;
		// no_delete_ = false;
		error_ = 0;
		this.nodeFactory = nodeFactory;
	}
	
	/**
	 * 构造 trie 树
	 * @param inputs
	 * @return
	 */
	@Override
	public int build(SortedMap<String, Integer> inputs) {
		List<String> keys = new ArrayList<String>(inputs.size());
		int[] values = new int[inputs.size()];

		for (SortedMap.Entry<String, Integer> key : inputs.entrySet()) {
			values[keys.size()] = key.getValue();
			keys.add(key.getKey());
		}
		int r = build(keys, null, values, values.length);
		keys.clear();
		return r;
	}

	@Override
	public void clear() {
		// if (! no_delete_)
		check = null;
		base = null;
		used = null;
		allocSize = 0;
		keySize = 0;
		size = 0;
		value = null;
		key = null;
		length = null;
	}
	
	public void toTrie(DoubleArrayTrie trie) {
		trie.attach(check, base, size);
	}

	@Override
	public DoubleArrayTrie toTrie() {
		DoubleArrayTrie trie = new DoubleArrayTrie();
		toTrie(trie);
		return trie;
	}
	
	/**
	 * 构造 trie 数, key 必须排序
	 * 
	 * @param key
	 * @return
	 */
	public int build(List<String> key) {
		return build(key, null, null, key.size());
	}

	/**
	 *  构造 trie 数, key 必须排序
	 * @param _key
	 * @param _length
	 * @param _value
	 * @param _keySize
	 * @return
	 */
	public int build(List<String> _key, int _length[], int _value[],
			int _keySize) {
		if (_keySize > _key.size() || _key == null)
			return 0;

		if(_key.isEmpty()) {
			size = 0;
			check = new int[0];
			base = new int[0];
			return 0;
		}

		// progress_func_ = progress_func;
		key = _key;
		length = _length;
		keySize = _keySize;
		value = _value;
		progress = 0;

		resize(65536 * 16);

		base[0] = 1;
		nextCheckPos = 0;

		TrieNode root_node = nodeFactory.makeNode();
		root_node.left = 0;
		root_node.right = keySize;
		root_node.depth = 0;
		fixNode(root_node);

		List<TrieNode> siblings = new ArrayList<TrieNode>();
		fetch(root_node, siblings);
		insert(siblings);

		// size += (1 << 8 * 2) + 1; // ???
		// if (size >= allocSize) resize (size);

		used = null;
		key = null;

		return error_;
	}

	/***
	 * 构建完节点后附加处理过程
	 * @param node
	 */
	protected void fixNode(TrieNode node) {
		
	}
	
	/**
	 * 构建完base后附加处理过程
	 * @param begin
	 * @param node
	 */
	protected void fixBase(int begin, TrieNode node) {
		
	}
	
	// inline _resize expanded
	protected int resize(int newSize) {
		int[] base2 = new int[newSize];
		int[] check2 = new int[newSize];
		boolean used2[] = new boolean[newSize];
		if (allocSize > 0) {
			System.arraycopy(base, 0, base2, 0, allocSize);
			System.arraycopy(check, 0, check2, 0, allocSize);
			System.arraycopy(used, 0, used2, 0, allocSize);
		}

		base = base2;
		check = check2;
		used = used2;

		return allocSize = newSize;
	}

	private int fetch(TrieNode parent, List<TrieNode> siblings) {
		if (error_ < 0)
			return 0;

		int prev = 0;

		for (int i = parent.left; i < parent.right; i++) {
			if ((length != null ? length[i] : key.get(i).length()) < parent.depth)
				continue;

			String tmp = key.get(i);

			int cur = 0;
			if ((length != null ? length[i] : tmp.length()) != parent.depth)
				cur = (int) tmp.charAt(parent.depth) + 1;

			if (prev > cur) {
				error_ = -3;
				return 0;
			}

			if (cur != prev || siblings.size() == 0) {
				TrieNode tmp_node =  nodeFactory.makeNode();
				tmp_node.parent = parent;
				tmp_node.depth = parent.depth + 1;
				tmp_node.code = cur;
				tmp_node.left = i;
				if (siblings.size() != 0)
					siblings.get(siblings.size() - 1).right = i;

				siblings.add(tmp_node);
				fixNode(tmp_node);
			}

			prev = cur;
		}

		if (siblings.size() != 0)
			siblings.get(siblings.size() - 1).right = parent.right;

		return siblings.size();
	}

	private int insert(List<TrieNode> siblings) {
		if (error_ < 0)
			return 0;

		int begin = 0;
		int pos = ((siblings.get(0).code + 1 > nextCheckPos) ? siblings.get(0).code + 1
				: nextCheckPos) - 1;
		int nonzero_num = 0;
		int first = 0;

		if (allocSize <= pos)
			resize(pos + 1);

		outer: while (true) {
			pos++;

			if (allocSize <= pos)
				resize(pos + 1);

			if (check[pos] != 0) {
				nonzero_num++;
				continue;
			} else if (first == 0) {
				nextCheckPos = pos;
				first = 1;
			}

			begin = pos - siblings.get(0).code;
			if (allocSize <= (begin + siblings.get(siblings.size() - 1).code)) {
				// progress can be zero
				double l = (1.05 > 1.0 * keySize / (progress + 1)) ? 1.05 : 1.0
						* keySize / (progress + 1);
				resize((int) (allocSize * l));
			}

			if (used[begin])
				continue;

			for (int i = 1; i < siblings.size(); i++)
				if (check[begin + siblings.get(i).code] != 0)
					continue outer;

			break;
		}

		// -- Simple heuristics --
		// if the percentage of non-empty contents in check between the
		// index
		// 'next_check_pos' and 'check' is greater than some constant value
		// (e.g. 0.9),
		// new 'next_check_pos' index is written by 'check'.
		if (1.0 * nonzero_num / (pos - nextCheckPos + 1) >= 0.95)
			nextCheckPos = pos;

		used[begin] = true;
		size = (size > begin + siblings.get(siblings.size() - 1).code + 1) ? size
				: begin + siblings.get(siblings.size() - 1).code + 1;

		for (int i = 0; i < siblings.size(); i++)
			check[begin + siblings.get(i).code] = begin;

		for (int i = 0; i < siblings.size(); i++) {
			List<TrieNode> new_siblings = new ArrayList<TrieNode>();

			if (fetch(siblings.get(i), new_siblings) == 0) {
				TrieNode node = siblings.get(i);
				base[begin + node.code] = (value != null) ? (-value[node.left] - 1) : (-node.left - 1);
				fixBase(begin, node);
				if (value != null && (-value[node.left] - 1) >= 0) {
					error_ = -2;
					return 0;
				}

				progress++;
				// if (progress_func_) (*progress_func_) (progress,
				// keySize);
			} else {
				int h = insert(new_siblings);
				TrieNode node = siblings.get(i);
				base[begin + node.code] = h;
				fixBase(begin, node);
			}
		}
		return begin;
	}

	public static  void main(String[] args) throws IOException{
		DoubleArrayTrieBuilder builder = new DoubleArrayTrieBuilder();
		SortedMap<String, Integer> inputs = new TreeMap<String, Integer>();
		inputs.put("abcd", 1);
		inputs.put("12345", 2);
		inputs.put("123456789", 3);
		inputs.put("mp3", 4);
		
		builder.build(inputs);
		
		DoubleArrayTrie trie = builder.toTrie();
		builder.clear();
		System.out.println(trie.exactMatchSearch("123456789"));
		System.out.println(trie.matchAll("abcd 123456789 ").get(1).word);
	}
}
