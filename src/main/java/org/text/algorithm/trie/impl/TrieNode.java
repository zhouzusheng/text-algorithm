package org.text.algorithm.trie.impl;

public class TrieNode {
	TrieNode parent;
	int code;
	int depth;
	int left;
	int right;

	static TrieNodefactory factory = new TrieNodefactory() {

		@Override
		public TrieNode makeNode() {
			return new TrieNode();
		}
		
	};
}
