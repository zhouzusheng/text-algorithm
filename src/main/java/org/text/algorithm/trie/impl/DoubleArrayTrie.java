package org.text.algorithm.trie.impl;


import org.text.algorithm.HitCallback;
import org.text.algorithm.SubMatcher;
import org.text.algorithm.trie.Trie;
import org.text.algorithm.utils.ByteUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

//代码来自
//https://github.com/komiya-atsushi/darts-java
//是一个日本人将c++实现转换为java 实现
//缺点: 要求输入是排序的!!!!

public final class  DoubleArrayTrie implements Trie, Externalizable {
	private static final long serialVersionUID = 2L;

	private final static int UNIT_SIZE = 8; // size of int + int

	protected int size;
	protected int check[];
	protected int base[];
	
	public DoubleArrayTrie() {
		check = null;
		base = null;
		size = 0;
	}
	
	
	// no deconstructor

	// set_result omitted
	// the model methods returns (the list of) the value(s) instead
	// of (the list of) the pair(s) of value(s) and length(s)

	// set_array omitted
	// array omitted

	void clear() {
		check = null;
		base = null;
		size = 0;
	}

	public int getUnitSize() {
		return UNIT_SIZE;
	}

	public int getSize() {
		return size;
	}

	public int getTotalSize() {
		return size * UNIT_SIZE;
	}

	public int getNonzeroSize() {
		int result = 0;
		for (int i = 0; i < size; i++)
			if (check[i] != 0)
				result++;
		return result;
	}

	public void attach(int check[], int base[], int size) {
		this.check = check;
		this.base = base;
		this.size = size;
	}
	
	public int[] getCheck() {
		return check;
	}
	
	public int[] getBase() {
		return base;
	}

	@Override
	public void open(String fileName) throws IOException {
		openFast(fileName);
	}
	
	public void openFast(String fileName) throws IOException {
		FileInputStream fis = new FileInputStream(fileName);
        // 1.从FileInputStream对象获取文件通道FileChannel
        FileChannel channel = fis.getChannel();
        int fileSize = (int) channel.size();
     
        // 2.从通道读取文件内容
        ByteBuffer byteBuffer = ByteBuffer.allocate(fileSize);

        // channel.read(ByteBuffer) 方法就类似于 inputstream.read(byte)
        // 每次read都将读取 allocate 个字节到ByteBuffer
        channel.read(byteBuffer);
        // 注意先调用flip方法反转Buffer,再从Buffer读取数据
        byteBuffer.flip();
        // 有几种方式可以操作ByteBuffer
        // 可以将当前Buffer包含的字节数组全部读取出来
        byte[] bytes = byteBuffer.array();
        byteBuffer.clear();
        // 关闭通道和文件流
        channel.close();
        fis.close();
        
        int index = 0;
        size = ByteUtil.bytesHighFirstToInt(bytes, index);
        index += 4;
        base = new int[size];
        check = new int[size];
        for (int i = 0; i < size; i++)
        {
            base[i] = ByteUtil.bytesHighFirstToInt(bytes, index);
            index += 4;
            check[i] = ByteUtil.bytesHighFirstToInt(bytes, index);
            index += 4;
        }

	}

	@Override
	public void save(String fileName) throws IOException {
		DataOutputStream out = null;
		try {
			out = new DataOutputStream(new BufferedOutputStream(
					new FileOutputStream(fileName)));
			write(out);
			out.close();
		} finally {
			if (out != null)
				out.close();
		}
	}
	
	public void load(DataInput is) throws IOException {
		size = is.readInt();
		check = new int[size];
		base = new int[size];
		for (int i = 0; i < size; i++) {
			base[i] = is.readInt();
			check[i] = is.readInt();
		}
	}
	
	public void write(DataOutput out) throws IOException {
		out.writeInt(size);
		for (int i = 0; i < size; i++) {
			out.writeInt(base[i]);
			out.writeInt(check[i]);
		}
	}

	@Override
	public int exactMatchSearch(String key) {
		return exactMatchSearch(key.toCharArray(), 0, 0, 0);
	}

	@Override
	public int exactMatchSearch(char[] code, int pos, int end, int nodePos) {
		if (end <= 0)
			end = code.length;
		if (nodePos <= 0)
			nodePos = 0;

		if(base.length == 0) {
			return -1;
		}

		int result = -1;
		int b = base[nodePos];
        int p;

        for (int i = pos; i < end; i++)
        {
            p = b + (int) (code[i]) + 1;
            if (p < size && b == check[p])
                b = base[p];
            else
                return result;
        }

        p = b;
        int n = base[p];
        if (b == check[p] && n < 0)
        {
            result = -n - 1;
        }
        return result;
	}

	@Override
	public int get(char[] key, int start, int end) {
		return exactMatchSearch(key, start, end, 0);
	}

	@Override
	public int get(String key) {
		return exactMatchSearch(key);
	}

	@Override
	public List<TrieResult> commonPrefixSearch(String key) {
		return commonPrefixSearch(key.toCharArray(), 0, key.length(), 0);
	}

	@Override
	public List<TrieResult> commonPrefixSearch(char[] code, int pos, int end,
			int nodePos) {
		List<TrieResult> result = new ArrayList<TrieResult>();
		if(base.length == 0) {
			return result;
		}
		
		int b = base[nodePos];
		
		char c0;
		for (int i = pos; i < end; i++) {
			c0 = code[i];
			//
			int p = b + (int) (c0) + 1;
			if (p < size && b == check[p]) {
				b = base[p];
				p = b;
				
				int n = base[p];
				if(n < 0) {
					TrieResult r = new TrieResult();
					r.word = new String(code, pos, i + 1- pos);
					r.offset = pos;
					r.len = i + 1 - pos;
					r.value = -n - 1;
					result.add(r);
				}
				
			} else {
				break;
			}
		}
		
		return result;
	}
	
	
	// debug
	public void dump() {
		for (int i = 0; i < size; i++) {
			System.err.println("i: " + i + " [" + base[i] + ", " + check[i]
					+ "]");
		}
	}
	
	@Override
	public TrieResult matchOne(String text) {
		return matchOne(text.toCharArray(), 0, text.length());
	}
	
	@Override
	public List<TrieResult> matchAll(String text) {
		return matchAll(text.toCharArray(), 0, text.length());
	}
	
	@Override
	public TrieResult matchOne(char[] code, int offset, int end) {
		int i = offset;
		while(i< end) {
			List<TrieResult> r = commonPrefixSearch(code, i,  end, 0);
			if(r != null && r.size() > 0)
				return r.get(r.size() -1);
			i++;
		}
		
		return null;
	}
	
	//add by haojing
	public TrieResult searchBest(char[] code, int offset, int end)
	{	
		return commonPrefixBestSearch(code, offset,  end, 0);
	}
	
	public TrieResult commonPrefixBestSearch(char[] code, int pos, int end,
			int nodePos)
	{
		if(base.length == 0) {
			return null;
		}

		TrieResult r = new TrieResult();
		r.offset = -1;
		
		int b = base[nodePos];
		
		char c0;
		for (int i = pos; i < end; i++) {
			c0 = code[i];
			//
			int p = b + (int) (c0) + 1;
			if (p < size && b == check[p]) {
				b = base[p];
				p = b;
				
				int n = base[p];
				if(n < 0) {
					r.offset = pos;
					r.len = i + 1 - pos;
					r.value = -n - 1;
				}
			} else {
				break;
			}
		}
		
		if(r.offset != -1) {
			r.word = new String(code, r.offset, r.len);
		} else 
			r = null;
		return r;
	}
	
	@Override
	public List<TrieResult> matchAll(char[] code, int offset, int end) {
		List<TrieResult> r = new ArrayList<TrieResult>();
		
		int i = offset;
		while(i< end) {
			List<TrieResult> items = commonPrefixSearch(code, i,  end, 0);
			if(items != null && items.size() > 0){
				r.addAll(items);
			}
			i++;
		}
		
		return r;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		write(out);
	}

	@Override
    public void readExternal(ObjectInput in) throws IOException {
		load(in);
	}

	@Override
	public SubMatcher newMatcher(char[] key, int pos, int end, HitCallback callback) {
		return new CharInnerMatcher(key, pos, end, callback);
	}

	@Override
	public SubMatcher newMatcher(String key, int pos, int end, HitCallback callback) {
		return new StringInnerMatcher(key, pos, end, callback);
	}

	public abstract class InnerMatcher implements SubMatcher {
		int start;
		int end;
		HitCallback callback;

		boolean stop;

		public InnerMatcher(int start, int end, HitCallback callback) {
			this.start = start;
			this.end = end;
			this.callback = callback;
		}

		@Override
		public int matchPrefix(int pos) {
			if (base.length == 0) {
				return 0;
			}
			stop = false;
			int count = 0;
			count += matchTrie(pos, base[0], size);
			return count;
		}

		@Override
		public int matchAll(int pos) {
			stop = false;
			int count = 0;
			int startBase = base[0];
			for (int i = pos; i < end; i++) {
				count += matchTrie(i, startBase, size);
				if (stop) {
					break;
				}
			}
			return count;
		}

		int matchTrie(int pos, int baseStart, int baseLimit) {
			int b = baseStart;
			int count = 0;
			char c0;
			for (int i = pos; i < end; i++) {
				c0 = getInput(i);
				//
				int p = b + (int) (c0) + 1;
				if (p < baseLimit && b == check[p]) {
					b = base[p];
					p = b;
					int n = base[p];
					if (n < 0) {
						count++;
						if (!callback.hit(pos, i + 1 - pos, -n - 1)) {
							stop = true;
							break;
						}
					}
				} else {
					break;
				}
			}
			return count;
		}

		abstract char getInput(int index);
	}

	private class CharInnerMatcher extends InnerMatcher {
		char[] keys;

		public CharInnerMatcher(char[] keys, HitCallback callback) {
			super(0, keys.length, callback);
			this.keys = keys;
		}

		public CharInnerMatcher(char[] keys, int start, int end, HitCallback callback) {
			super(start, end, callback);
			this.keys = keys;
		}


		@Override
		char getInput(int index) {
			return keys[index];
		}
	}

	private class StringInnerMatcher extends InnerMatcher {
		String key;

		public StringInnerMatcher(String key, HitCallback callback) {
			super(0, key.length(), callback);
			this.key = key;
		}

		public StringInnerMatcher(String key, int start, int end, HitCallback callback) {
			super(start, end, callback);
			this.key = key;
		}

		@Override
		char getInput(int index) {
			return key.charAt(index);
		}
	}
}


