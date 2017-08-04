package de.twiechert.flatfat;


import de.twiechert.flatfat.node.Node;
import de.twiechert.flatfat.node.NodeIndexPosition;
import de.twiechert.flatfat.resolver.NodeByTimestampResolver;
import de.twiechert.flatfat.resolver.StartAndStopSliceResolver;
import org.javatuples.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

/**
 * @author Tayfun Wiechert <wiechert@campus.tu-berlin.de>
 */
public interface FlatFatTree<IN, ACC> {

    /**
     * @param position
     * @param node
     * @param commit
     */
    void add(int position, Node<IN, ACC> node, boolean commit) throws Exception;

    /**
     * @param node
     * @param commit
     * @throws Exception
     */
    void addPotentiallyOutOfOrder(Node<IN, ACC> node, boolean commit) throws Exception;

    /**
     * @param node
     * @param commit
     * @throws Exception
     */
    void add(Node<IN, ACC> node, boolean commit) throws Exception;


    /**
     * @param leafPosition
     * @return
     */
    Node<IN, ACC> getNodeOrNull(int leafPosition);

    /**
     * @param leafPosition
     * @return
     */
    Node<IN, ACC> getNode(int leafPosition);

    /**
     * @param positions
     * @throws Exception
     */
    void remove(Integer... positions) throws Exception;


    /**
     * Removes all leafs up to position <code>leafPosition</code>
     *
     * @param leafPosition the leaf position up to which to delete
     * @throws Exception in case deletion is not possible
     */
    void removeUpTo(int leafPosition) throws Exception;

    /**
     * It collects an aggregated result starting from the leafID given until the back index of the circular heap
     *
     * @param startPosition
     * @param endPosition
     * @return
     * @throws Exception
     */
    Node<IN, ACC> aggregateFromTo(final int startPosition, final int endPosition) throws Exception;

    /**
     * @param tc
     * @return
     */
    Integer getNodePositionByTimestamp(long tc);


    /**
     * @param tc
     * @param comparator
     * @return
     */
    Integer getNodePositionByTimestampAndComparator(long tc, NodeByTimestampResolver.Comparator comparator);


    /**
     * @param startTime
     * @param endTime
     * @return
     * @throws Exception
     */
    Node<IN, ACC> aggregateFromTo(long startTime, long endTime) throws Exception;


    /**
     * @return
     */
    int currentCapacity();

    /**
     * @return
     */
    Node<IN, ACC> getCurrentNode();


    /**
     * @param node
     */
    void setCurrentNode(Node<IN, ACC> node);

    /**
     * @return
     */
    int getCurrentLeafCount();

    int getCurrentLeafPosition();

    /**
     * @return
     */
    Iterator<NodeIndexPosition<IN, ACC>> getSliceBackwardsIterator();

    /**
     * @return
     */
    Iterator<NodeIndexPosition<IN, ACC>> getSliceForwardsIterator();


    /**
     * @param positions
     * @throws Exception
     */
    void update(Integer... positions) throws Exception;


    abstract class BaseFlatFatTree<IN, ACC> implements FlatFatTree<IN, ACC> {

        protected final Node<IN, ACC> identityNode;

        /**
         * Points to the current node
         */
        protected Node<IN, ACC> currentNode;


        /**
         * Corresponds to the max number of leafs, thre tree can currently hold
         */
        protected int numLeafs;

        /**
         * Corresponds to the current number of leafs
         */
        protected int currentLeafCount = 0;


        /**
         * Points to the the position of the current leaf
         */
        protected int currentLeafPosition = -1;

        private NodeByTimestampResolver nodeByTimestampResolver;
        private NodeByTimestampResolver.FindSliceIndexByTimestampComparator findSliceIndexByTimestampComparator;
        private StartAndStopSliceResolver startAndStopSliceResolver;

        protected final StateFactory<IN, ACC> partialStateFactory;

        public BaseFlatFatTree(Builder<IN, ACC> builder) throws Exception {
            if (((builder.capacity & -builder.capacity) != builder.capacity))
                throw new IllegalArgumentException("Capacity should be a power of two");

            this.currentNode = builder.initSlice;
            this.partialStateFactory = builder.partialStateFactory;
            this.numLeafs = builder.capacity;

            this.identityNode = createEmpty(true);
            if (builder.nodeByTimestampStrategy == NonShiftingFlatFatTree.Builder.NodeByTimestampStrategy.LINEARSCAN_BACKWARD) {
                nodeByTimestampResolver = new NodeByTimestampResolver.LinearBackwardScanResolver(this);

            } else {
                findSliceIndexByTimestampComparator = new NodeByTimestampResolver.FindSliceIndexByTimestampComparator();
                nodeByTimestampResolver = new NodeByTimestampResolver.BinarySearch(this);
            }

            if (builder.startEndNodeStrategy == Builder.StartEndNodeStrategy.LINEARSCAN_FORWARD) {
                this.startAndStopSliceResolver = new StartAndStopSliceResolver.StartAndStopSliceLinearForwardResolver<>(this);
            } else if (builder.startEndNodeStrategy == Builder.StartEndNodeStrategy.LINEARSCAN_BACKWARD) {
                this.startAndStopSliceResolver = new StartAndStopSliceResolver.StartAndStopSliceLinearBackwardResolver<>(this);
            } else if (builder.startEndNodeStrategy == Builder.StartEndNodeStrategy.BINARY_SEARCH) {
                this.startAndStopSliceResolver = new StartAndStopSliceResolver.BinarySearchResolver<>(this);
            }
        }

        /**
         * This method checks if the given array of positions is in order (i.e. it contains no holes)
         *
         * @param positions the array of positions
         * @return whether the array is in order
         */
        protected boolean inOrder(Integer... positions) {
            if (positions.length == 1 && positions[0] == 0)
                return true;
            Arrays.sort(positions, Collections.reverseOrder());
            for (int i = positions.length - 1; i > -1; i--) {
                if (positions[i] != i)
                    return false;
            }
            return true;
        }

        /**
         * @param left
         * @param right
         * @return
         * @throws Exception
         */
        protected Node<IN, ACC> combine(Node<IN, ACC> reusableNode, Node<IN, ACC> left, Node<IN, ACC> right) throws Exception {

            if (reusableNode.equals(left)) {
                reusableNode.getValueState().merge(right.getValueState());
            } else {
                reusableNode.setValueState(this.partialStateFactory.getState());
                reusableNode.getValueState().merge(left.getValueState()).merge(right.getValueState());
            }
            reusableNode.setTmax(Math.max(left.getTmax(), right.getTmax()));
            reusableNode.setStart((left.getStart() != -1L) ? left.getStart() : right.getStart());
            reusableNode.setEnd((right.getEnd() != 0L) ? right.getEnd() : left.getEnd());
            return reusableNode;
        }

        protected Node<IN, ACC> combine(Node<IN, ACC> left, Node<IN, ACC> right) throws Exception {
            Node.InnerNode<IN, ACC> innerNode = this.createEmpty();
            return combine(innerNode, left, right);
        }


        protected Node.InnerNode<IN, ACC> copyFromPosition(int nodePosition) {
            return new Node.InnerNode<IN, ACC>(this.getNode(nodePosition), this.partialStateFactory);
        }

        protected Node.InnerNode<IN, ACC> createEmpty(boolean identity) throws Exception {
            return new Node.InnerNode<>(partialStateFactory.getState(), identity);
        }

        protected Node.InnerNode<IN, ACC> createEmpty() throws Exception {
            return this.createEmpty(false);
        }


        /**
         * @param slice the slice to find the correct position for
         * @return the position to insert the node
         */
        protected NodeIndexPosition<IN, ACC> findPredecessor(Node<IN, ACC> slice) {
            Iterator<NodeIndexPosition<IN, ACC>> iterator = getSliceBackwardsIterator();
            NodeIndexPosition<IN, ACC> nodeIndexPosition;
            while (iterator.hasNext()) {
                nodeIndexPosition = iterator.next();
            /*
			 * Check if we have found the correct position.
			 */
                if (slice.getStart() >= nodeIndexPosition.getNode().getEnd()) {
                    return nodeIndexPosition;
                }
            }

            return null;
        }


        @Override
        public Node<IN, ACC> aggregateFromTo(long startTime, long endTime) throws Exception {

            Pair<Integer, Integer> startAndStop = this.startAndStopSliceResolver.getStartAndStopForAgg(startTime, endTime);

            if (startAndStop.getValue0() > -1 && startAndStop.getValue1() > -1)
                return this.aggregateFromTo(startAndStop.getValue0(), startAndStop.getValue1());
            else return new Node.InnerNode<>(partialStateFactory.getState(), startTime, endTime);

        }

        @Override
        public Node<IN, ACC> aggregateFromTo(final int startPosition, final int endPosition) throws Exception {

            int effStartPosition = startPosition;
            int effEndPosition = endPosition;

            if (startPosition == endPosition)
                return this.copyFromPosition(startPosition);

		/*
		  Attention: the solution delivered by Alejandro failed, if the start node is n left node
		  or the end node is no right node. This is due to the proposed algorithm, which handles these cases wrongly.

		  If start position is no left node, we begin at the position right next to it.
		  If end position is no right node, we begin at the position right left to it.

		  The start and end node will in these cases be manually merged after applying the aggregation.
		 */
            boolean mergeStart = false;
            boolean mergeEnd = false;
            Node<IN, ACC> agg = null;

		/*
		  Check whether start position is a left node, if not start with the right neighbour
		 */
            if (startPosition % 2 != 0) {
                mergeStart = true;
                effStartPosition++;
            }

		/*
		  Check whether end position is a right node, if not end with the left neighbour
		 */
            if (endPosition % 2 != 1) {

			/*
			  Special handling if startPosition+1=endPosition --> use end node
			 */
                if (effStartPosition == effEndPosition) {
                    agg = this.copyFromPosition(effStartPosition);
                } else {
                    mergeEnd = true;
                }

                effEndPosition--;
            }

            agg = (agg == null) ? suffix(effStartPosition, effEndPosition) : agg;

		/*
		  Manually merge ...
		 */
            if (mergeStart)
                agg.getValueState().merge(getNode(startPosition).getValueState());

            if (mergeEnd)
                agg.getValueState().merge(getNode(endPosition).getValueState());

            return agg;

        }

        protected abstract Node<IN, ACC> suffix(int startPosition, int endPosition) throws Exception;


        @Override
        public Integer getNodePositionByTimestamp(long tc) {
            return nodeByTimestampResolver.getForTimestamp(tc, findSliceIndexByTimestampComparator);
        }


        @Override
        public Integer getNodePositionByTimestampAndComparator(long tc, NodeByTimestampResolver.Comparator comparator) {
            return nodeByTimestampResolver.getForTimestamp(tc, comparator);
        }


        @Override
        public Node<IN, ACC> getCurrentNode() {
            return this.currentNode;
        }

        @Override
        public void setCurrentNode(Node<IN, ACC> node) {
            this.currentNode = node;
        }

        @Override
        public int getCurrentLeafCount() {
            return currentLeafCount;
        }

        @Override
        public int getCurrentLeafPosition() {
            return currentLeafPosition;
        }

        /**
         * Returns the supposed leaf position given its technical index
         * ATTENTION: Because this implementation allows for out-of-order elements, the technical index might differ
         *
         * @param index the technical index
         * @return the logical leaf position
         */
        protected int getSupposedPositionFromIndex(int index) {
            // is basically the inverse of h(leaf(i)) = n + i - 1 => i=  h(leaf(i))-n+1
            return index - this.numLeafs + 1;
        }

        protected int getSupposedIndexFromPosition(int position) {
            // is basically the inverse of h(leaf(i)) = n + i - 1 => i=  h(leaf(i))-n+1
            return position + this.numLeafs - 1;
        }

        /**
         * Returns the parent index based on a leafs position
         *
         * @param nodePosition
         * @return
         */
        protected int parentForPosition(int nodePosition) {
            // h(parent) = h(v)/2 with h(v)= n+i-1
            // because h(v) for leaf nodes might deviate from the desired position, we first resolve the supposed index
            return this.parent(getSupposedIndexFromPosition(nodePosition));
        }

        /**
         * @param nodeIndex
         * @return
         */
        protected int parent(int nodeIndex) {
            return (nodeIndex - 1) / 2;
        }

    }

    class Builder<IN, ACC> {

        enum StartEndNodeStrategy {
            BTREE, LINEARSCAN_FORWARD, LINEARSCAN_BACKWARD, BINARY_SEARCH
        }

        enum NodeByTimestampStrategy {
            LINEARSCAN_BACKWARD, BINARY_SEARCH
        }

        protected Node<IN, ACC> initSlice;
        protected int capacity;
        protected StateFactory<IN, ACC> partialStateFactory;
        protected StartEndNodeStrategy startEndNodeStrategy = StartEndNodeStrategy.LINEARSCAN_FORWARD;
        protected NodeByTimestampStrategy nodeByTimestampStrategy = NodeByTimestampStrategy.BINARY_SEARCH;

        private Builder(StateFactory<IN, ACC> partialStateFactory) {
            this.partialStateFactory = partialStateFactory;
        }

        public static <IN, ACC> Builder<IN, ACC> newBuilder(StateFactory<IN, ACC> partialStateFactory) {
            return new Builder<>(partialStateFactory);
        }

        public Builder<IN, ACC> initSlice(Node<IN, ACC> initSlice) {
            this.initSlice = initSlice;
            return this;
        }

        public Builder<IN, ACC> capacity(int capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder<IN, ACC> startEndNodeStrategy(StartEndNodeStrategy startEndNodeStrategy) {
            this.startEndNodeStrategy = startEndNodeStrategy;
            return this;
        }

        public Builder<IN, ACC> nodeByTimestampStrategy(NodeByTimestampStrategy nodeByTimestampStrategy) {
            this.nodeByTimestampStrategy = nodeByTimestampStrategy;
            return this;
        }


        public FlatFatTree<IN, ACC> build() throws Exception {
            return new NonShiftingFlatFatTree<IN, ACC>(this);
        }
    }


}
