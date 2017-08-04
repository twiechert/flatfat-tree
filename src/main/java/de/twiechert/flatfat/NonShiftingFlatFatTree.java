package de.twiechert.flatfat;

import com.google.common.collect.Sets;
import de.twiechert.flatfat.node.Node;
import de.twiechert.flatfat.node.NodeIndexPosition;

import java.util.*;

/**
 * @author Tayfun Wiechert <wiechert@campus.tu-berlin.de>
 */
public class NonShiftingFlatFatTree<IN, ACC> extends FlatFatTree.BaseFlatFatTree<IN, ACC> implements FlatFatTree<IN, ACC> {

	private final int ROOT = 0;

	/**
	 * We use a fixed size list for the circular heap. We did not use an array due to the usual generics
	 * issue in Java. Performance should be comparably reasonable using an ArrayList implementation.
	 */
	private List<Node<IN, ACC>> circularHeap;

	/**
	 * Front and back pointer of the circular heap
	 */
	private int back, front;

	/**
	 * Because we employ a circular heap, this map is required,a s the mapping rule from positions to indices is no longer applicable
	 */
	private Map<Integer, Integer> leafIndex;


	protected NonShiftingFlatFatTree(Builder<IN, ACC> builder) throws Exception {
		super(builder);
		this.back = builder.capacity - 2;
		this.front = builder.capacity - 1;
		this.leafIndex = new LinkedHashMap<>(builder.capacity);
		int fullCapacity = 2 * builder.capacity - 1;
		this.circularHeap = new ArrayList<>(Collections.nCopies(fullCapacity, identityNode));
	}


	@Override
	public void add(int position, Node<IN, ACC> node, boolean commit) throws Exception {
		this.add(position, node, commit, true);
	}


	/**
	 * @param position
	 * @param node
	 * @param commit
	 * @param shiftIndex
	 * @throws Exception
	 */
	private void add(int position, Node<IN, ACC> node, boolean commit, boolean shiftIndex) throws Exception {

		if (currentCapacity() == 0) {
			resize(2 * this.numLeafs);
		}

		incrBack();
		this.circularHeap.set(this.back, node);

		if (shiftIndex && position != this.currentLeafCount) {
			this.shiftIndex(position);
		}

		this.leafIndex.put(position, this.back);

		if (commit) {
			if (!shiftIndex)
				update(position);
			else update();
		}

		this.currentLeafCount++;
		/*
		  If element is in order leaf count increases, if not also because leafs shift---
		 */
		this.currentLeafPosition++;
	}


	@Override
	public void addPotentiallyOutOfOrder(Node<IN, ACC> node, boolean commit) throws Exception {
		NodeIndexPosition<IN, ACC> predecessor = this.findPredecessor(node);
		int position = (predecessor != null) ? predecessor.getPosition() + 1 : 0;
		if (position != currentLeafPosition + 1)
			this.add(position, node, commit, true);
		else add(node, commit);
	}

	@Override
	public void add(Node<IN, ACC> node, boolean commit) throws Exception {
		this.add(currentLeafPosition + 1, node, commit, false);
	}


	@Override
	public Node<IN, ACC> getNodeOrNull(int leafPosition) {

		if (leafIndex.get(leafPosition) != null && !this.circularHeap.get(leafIndex.get(leafPosition)).equals(identityNode)) {
			return this.circularHeap.get(this.leafIndex.get(leafPosition));
		}

		return null;
	}


	@Override
	public Node<IN, ACC> getNode(int leafPosition) {

		if (this.leafIndex.get(leafPosition) != null) {
			return this.circularHeap.get(this.leafIndex.get(leafPosition));
		}

		return null;
	}


	/**
	 * It reconstructs the heap with a new leaf space of size newCapacity
	 *
	 * @param newCapacity the new capacity of the buffer
	 */
	private void resize(int newCapacity) throws Exception {

		int fullCapacity = 2 * newCapacity - 1;
		// creates new heap
		List<Node<IN, ACC>> newHeap = new ArrayList<>(Collections.nCopies(fullCapacity, identityNode));

		Integer[] updated = new Integer[this.leafIndex.size()];

		int indx = newCapacity - 2;
		int updateCount = 0;

		Iterator<NodeIndexPosition<IN, ACC>> iterator = getSliceForwardsIterator();
		NodeIndexPosition<IN, ACC> nodeIndexPosition;
		while (iterator.hasNext()) {
			nodeIndexPosition = iterator.next();
			newHeap.set(++indx, nodeIndexPosition.getNode());
			updated[updateCount++] = nodeIndexPosition.getPosition();
			this.leafIndex.put(nodeIndexPosition.getPosition(), indx);
		}

		this.numLeafs = newCapacity;
		this.back = indx;
		this.front = newCapacity - 1;
		this.circularHeap = newHeap;
		update(updated);
	}


	@Override
	public void remove(Integer... positions) throws Exception {

		List<Integer> leafBag = new ArrayList<>(positions.length);
		/*
			Checks whether entries are removed in order (from beginning without gaps
		 */
		boolean removedInOrder = this.inOrder(positions);
		boolean onlyRemovedFromBack = true;
		int leafIdx;
		for (int position : positions) {
			if (this.getNode(position) == null)
				continue;

			this.currentLeafCount--;
			this.currentLeafPosition--;
			leafIdx = leafIndex.get(position);
			if (leafIdx == back) {
				decrBack();
				leafIndex.remove(position);
			} else {
				onlyRemovedFromBack = false;
				if (removedInOrder)
					incrFront();
			}

			this.circularHeap.set(leafIdx, this.createEmpty(true));
			leafBag.add(position);
		}

		if (onlyRemovedFromBack) {
			update(leafBag.toArray(new Integer[leafBag.size()]));
		} else {
			this.reorderIndex();
			update();
		}

		// shrink to half when the utilization is only one quarter
		if (currentCapacity() > 3 * numLeafs / 4 && numLeafs >= 4) {
			resize(numLeafs / 2);
		}
	}


	private void reorderIndex() {
		Iterator<NodeIndexPosition<IN, ACC>> iterator = getSliceForwardsIterator();
		NodeIndexPosition<IN, ACC> nodeIndexPosition;
		int i = 0;
		while (iterator.hasNext()) {
			nodeIndexPosition = iterator.next();
			this.leafIndex.put(i, nodeIndexPosition.getIndex());
			i++;
		}

		boolean cleanFront = true;
		int j = i;
		while (cleanFront) {
			if (this.leafIndex.containsKey(j)) {
				this.leafIndex.remove(j);
				j++;
			} else cleanFront = false;
		}
		this.currentLeafCount = i;
	}

	private void shiftIndex(int pos) {
		int index = leafIndex.get(pos);
		int leafIndexCurrSize = leafIndex.size();

		for (int i = pos; i < leafIndexCurrSize; i++) {
			int newIndex = (i != leafIndexCurrSize - 1) ? leafIndex.get(i + 1) : 0;
			this.leafIndex.put(i + 1, index);
			index = newIndex;
		}
	}


	@Override
	public void removeUpTo(int leafPosition) throws Exception {
		if (leafPosition < 0)
			return;

		List<Integer> toRemove = new ArrayList<>();
		if (this.leafIndex.containsKey(leafPosition)) {

			Iterator<NodeIndexPosition<IN, ACC>> iterator = getSliceForwardsIterator();
			NodeIndexPosition<IN, ACC> nodeIndexPosition;
			while (iterator.hasNext()) {
				nodeIndexPosition = iterator.next();

				if (nodeIndexPosition.getPosition() > leafPosition)
					break;

				toRemove.add(nodeIndexPosition.getPosition());

			}
		}

		remove(toRemove.toArray(new Integer[toRemove.size()]));
	}


	protected void update() throws Exception {
		this.update(this.leafIndex.keySet().toArray(new Integer[this.leafIndex.keySet().size()]));
	}


	@Override
	public void update(Integer... positions) throws Exception {
		if (positions.length == 0) return;

		Set<Integer> next = Sets.newHashSet(positions);
		boolean leafs = true;
		do {
			Set<Integer> tmp = new HashSet<>();
			// in case of leafs we use positions, for inner nodes we use internal indices
			for (Integer positionOrIndex : next) {
				if (leafs || positionOrIndex != ROOT) {
					if (leafs)
						tmp.add(parentForPosition(positionOrIndex));
					else
						tmp.add(parent(positionOrIndex));

				}
			}
			for (Integer parent : tmp) {
				circularHeap.set(parent, combine(circularHeap.get(leftChild(parent)), circularHeap.get(rightChild(parent))));
			}
			next = tmp;
			leafs = false;
		} while (!next.isEmpty());

	}


	/**
	 * it collects an aggregated result starting from the startleafID given until the endleafID
	 *
	 * @param startPosition
	 * @param endPosition
	 * @return
	 * @throws Exception
	 */
	protected Node<IN, ACC> suffix(int startPosition, int endPosition) throws Exception {
		int nextS = this.leafIndex.get(startPosition);
		int nextE = this.leafIndex.get(endPosition);
		boolean leafs = true;

		Node<IN, ACC> aggS = getNode(startPosition);
		Node<IN, ACC> aggE = getNode(endPosition);


		while (nextS != ROOT && nextS != nextE) {
			int pS = (leafs) ? parentForPosition(startPosition) : parent(nextS);
			int pE = (leafs) ? parentForPosition(endPosition) : parent(nextE);

			if (pS != pE) {
				if (nextS == leftChild(pS)) {
					aggS = circularHeap.get(pS);
				}

				if (nextE == rightChild(pE)) {
					aggE = circularHeap.get(pE);
				}

			}			nextS = pS;
			nextE = pE;
			leafs = false;
		}
		return combine(aggS, aggE);
	}


	/**
	 * It returns the left child of the nodeID given
	 * h(leaf(i)) = n + i - 1 => i=  h(leaf(i))-n+1
	 *
	 * @param parentIndex
	 * @return
	 */
	private int leftChild(int parentIndex) {
		int properIndex = 2 * parentIndex + 1;
		int position = getSupposedPositionFromIndex(properIndex);
		// resolve to index, which might be different due to ordering
		if (this.leafIndex.get(position) != null)
			return this.leafIndex.get(position);
		else
			return properIndex;
	}

	/**
	 * It returns the right child of the nodeID given
	 *
	 * @param parentIndex
	 * @return
	 */
	private int rightChild(int parentIndex) {
		//return 2 * leafIndex + 2;
		int properIndex = 2 * parentIndex + 2;
		int position = getSupposedPositionFromIndex(properIndex);
		// resolve to index, which might be different due to ordering
		if (this.leafIndex.get(position) != null)
			return this.leafIndex.get(position);
		else
			return properIndex;
	}

	protected void incrBack() {
		back = ((back - numLeafs + 2) % numLeafs) + numLeafs - 1;
	}


	protected void decrBack() {
		back = ((back - numLeafs + 2) % numLeafs) + numLeafs - 3;
	}


	protected void incrFront() {
		front = ((front - numLeafs + 2) % numLeafs) + numLeafs - 1;
	}

	@Override
	public int currentCapacity() {
		return numLeafs - leafIndex.size();
	}


	@Override
	public Iterator<NodeIndexPosition<IN, ACC>> getSliceBackwardsIterator() {
		return new LeafNodeBackwardsIterator();
	}

	@Override
	public Iterator<NodeIndexPosition<IN, ACC>> getSliceForwardsIterator() {
		return new LeafNodeForwardsIterator();
	}


	public class LeafNodeBackwardsIterator extends LeafIterator implements Iterator<NodeIndexPosition<IN, ACC>> {

		public LeafNodeBackwardsIterator() {
			super(currentLeafCount - 1);
		}

		@Override
		public NodeIndexPosition<IN, ACC> next() {
			int currentIndex = leafIndex.get(currentPosition);
			return new NodeIndexPosition<>(circularHeap.get(currentIndex), currentIndex, currentPosition--);
		}

	}

	public class LeafNodeForwardsIterator extends LeafIterator implements Iterator<NodeIndexPosition<IN, ACC>> {

		public LeafNodeForwardsIterator() {
			super(0);
		}

		@Override
		public NodeIndexPosition<IN, ACC> next() {
			int currentIndex = leafIndex.get(currentPosition);
			return new NodeIndexPosition<>(circularHeap.get(currentIndex), currentIndex, currentPosition++);
		}

	}

	private abstract class LeafIterator implements Iterator<NodeIndexPosition<IN, ACC>> {

		protected int currentPosition;

		private LeafIterator(int currentPosition) {
			this.currentPosition = currentPosition;
		}

		@Override
		public boolean hasNext() {
			if (leafIndex.containsKey(currentPosition) && !circularHeap.get(leafIndex.get(currentPosition)).equals(identityNode))
				return true;
			else if (leafIndex.containsKey(currentPosition)) {
				next();
				return hasNext();
			}
			return false;
		}
	}

}
