package de.twiechert.flatfat.resolver;

import de.twiechert.flatfat.FlatFatTree;
import de.twiechert.flatfat.node.Node;
import de.twiechert.flatfat.node.NodeIndexPosition;
import org.javatuples.Pair;

import java.util.Iterator;

/**
 * @author Tayfun Wiechert <wiechert@campus.tu-berlin.de>
 */
public interface StartAndStopSliceResolver {

	Pair<Integer, Integer> getStartAndStopForAgg(long startTime, long endTime);

	class StartAndStopSliceLinearForwardResolver<IN, ACC> implements StartAndStopSliceResolver {

		private final FlatFatTree<IN, ACC> flatFatTree;

		public StartAndStopSliceLinearForwardResolver(FlatFatTree<IN, ACC> flatFatTree) {
			this.flatFatTree = flatFatTree;
		}

		@Override
		public Pair<Integer, Integer> getStartAndStopForAgg(long startTime, long endTime) {
			return this.getStartAndStopForAgg(startTime, endTime, flatFatTree.getSliceForwardsIterator());
		}

		public Pair<Integer, Integer> getStartAndStopForAgg(long startTime, long endTime, Iterator<NodeIndexPosition<IN, ACC>> iterator) {
			int startPosition = -1;
			int endPosition = -1;
			NodeIndexPosition<IN, ACC> nodeIndexPosition;
			boolean condition;
			while (iterator.hasNext()) {
				nodeIndexPosition = iterator.next();
				condition = startTime <= nodeIndexPosition.getNode().getStart() && endTime > nodeIndexPosition.getNode().getTmax();

				if (startPosition > -1 && endPosition > -1 && !condition) {
					break;
				}

				if (startPosition == -1 && condition) {
					startPosition = nodeIndexPosition.getPosition();
				}
				if (startPosition > -1 && condition) {
					endPosition = nodeIndexPosition.getPosition();
				}

			}

			return new Pair<>(startPosition, endPosition);
		}


	}

	class StartAndStopSliceLinearBackwardResolver<IN, ACC> implements StartAndStopSliceResolver {


		private final FlatFatTree<IN, ACC> flatFatTree;

		private final StartAndStopSliceLinearForwardResolver<IN, ACC> forwardResolver;

		public StartAndStopSliceLinearBackwardResolver(FlatFatTree<IN, ACC> flatFatTree) {
			this.flatFatTree = flatFatTree;
			this.forwardResolver = new StartAndStopSliceLinearForwardResolver<>(flatFatTree);
		}

		@Override
		public Pair<Integer, Integer> getStartAndStopForAgg(long startTime, long endTime) {

			Pair<Integer, Integer> tuple2 = forwardResolver.getStartAndStopForAgg(startTime, endTime, flatFatTree.getSliceBackwardsIterator());
			return new Pair<>(tuple2.getValue1(), tuple2.getValue0());

		}


	}

	class BinarySearchResolver<IN, ACC> implements StartAndStopSliceResolver {


		private final FlatFatTree<IN, ACC> flatFatTree;


		public BinarySearchResolver(FlatFatTree<IN, ACC> flatFatTree) {
			this.flatFatTree = flatFatTree;
		}


		@Override
		public Pair<Integer, Integer> getStartAndStopForAgg(long startTime, long endTime) {
			int startBinary = this.getStartBinary(startTime, endTime, 0, flatFatTree.getCurrentLeafPosition());
			int endBinary = (startBinary==-1) ? -1 : this.getEndBinary(startTime, endTime, startBinary, flatFatTree.getCurrentLeafPosition());
			return new Pair<>(startBinary, endBinary);
		}


		private int getEndBinary(long startTime, long endTime, int leftPosition, int rightPosition) {

			if (rightPosition < leftPosition) return -1;
			int currPosition = (leftPosition + rightPosition) / 2;
			Node<IN, ACC> currNode = flatFatTree.getNodeOrNull(currPosition);
			if (isStartOrEnd(currNode, currPosition, startTime, endTime, false))
				return currPosition;

			// look left
			if (currNode.getStart() >= endTime) {
				return getEndBinary(startTime, endTime, leftPosition, currPosition - 1);
			}

			// look right
			return getEndBinary(startTime, endTime, currPosition + 1, rightPosition);

		}

		private int getStartBinary(long startTime, long endtime, int leftPosition, int rightPosition) {
			if (rightPosition < leftPosition) return -1;
			int currPosition = (leftPosition + rightPosition) / 2;
			Node<IN, ACC> currNode = flatFatTree.getNodeOrNull(currPosition);
			//startTime <= nodeIndexPosition.getNode().getStart() && endTime > nodeIndexPosition.getNode().getTmax();
			if (isStartOrEnd(currNode, currPosition, startTime, endtime, true))
				return currPosition;
			// look left
			if (startTime < currNode.getStart()) {
				return getStartBinary(startTime, endtime, leftPosition, currPosition - 1);
			}
			// look right
			return getStartBinary(startTime, endtime,currPosition + 1, rightPosition);
		}


		//s;

		private boolean isStartOrEnd(Node<IN, ACC> currNode, int nodePosition, long startTime, long endTime, boolean predecessor) {
			boolean mighBeStartOrEnd = startTime <= currNode.getStart() && endTime > currNode.getTmax();


			if(nodePosition < 0 || nodePosition > flatFatTree.getCurrentLeafPosition())
				return false;

			if(!mighBeStartOrEnd)
				return false;

			if((nodePosition == 0 && predecessor) || (nodePosition == flatFatTree.getCurrentLeafPosition() && !predecessor) )
				return true;

			Node<IN, ACC> predecessorOrSuccessor = (predecessor)? flatFatTree.getNodeOrNull(nodePosition - 1) : flatFatTree.getNodeOrNull(nodePosition + 1)  ;
			return (predecessor)? predecessorOrSuccessor.getStart() < startTime :  predecessorOrSuccessor.getTmax() >= endTime ;
		}


	}


}
