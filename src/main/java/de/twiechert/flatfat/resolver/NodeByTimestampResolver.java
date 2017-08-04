package de.twiechert.flatfat.resolver;

import de.twiechert.flatfat.FlatFatTree;
import de.twiechert.flatfat.node.Node;
import de.twiechert.flatfat.node.NodeIndexPosition;

import java.util.Iterator;

/**
 * @author Tayfun Wiechert <wiechert@campus.tu-berlin.de>
 */
public interface NodeByTimestampResolver {


	interface Comparator {
		int compare(Node currentNode, long timestamp);

	}

	class FindSliceIndexByTimestampComparator implements Comparator {
		@Override
		public int compare(Node currentNode, long timestamp) {
			if(currentNode.getStart() <= timestamp)
				return 0;

			return -1;
		}
	}

	class RemoveSlicesComparator implements Comparator {
		@Override
		public int compare(Node currentNode, long timestamp) {
			if(currentNode.getEnd() <= timestamp)
				return 0;

			return -1;
		}
	}


	Integer getForTimestamp(long timestamp, NodeByTimestampResolver.Comparator comparator);

	class LinearBackwardScanResolver implements NodeByTimestampResolver{


		private final FlatFatTree flatFatTree;


		public LinearBackwardScanResolver(FlatFatTree flatFatTree) {
			this.flatFatTree = flatFatTree;
		}

		@Override
		public Integer getForTimestamp(long timestamp, NodeByTimestampResolver.Comparator comparator) {
			Iterator<NodeIndexPosition> iterator = flatFatTree.getSliceBackwardsIterator();
			NodeIndexPosition nodeIndexPosition;

			//startTime <= currNode.getStart() && endTime > currNode.getTmax()

			while (iterator.hasNext()) {
				nodeIndexPosition = iterator.next();
				if (nodeIndexPosition.getNode().getStart() <= timestamp)
					return nodeIndexPosition.getPosition();
			}
			return null;
		}
	}

	class BinarySearch implements NodeByTimestampResolver{


		private final FlatFatTree flatFatTree;


		public BinarySearch(FlatFatTree flatFatTree) {
			this.flatFatTree = flatFatTree;
		}

		@Override
		public Integer getForTimestamp(long timestamp, NodeByTimestampResolver.Comparator comparator) {
			return this.getPositonBinary(timestamp, 0, this.flatFatTree.getCurrentLeafPosition(), comparator);
		}

		private int getPositonBinary(long timestamp, int leftPosition, int rightPosition,NodeByTimestampResolver.Comparator comparator) {

			if (rightPosition < leftPosition) return -1;
			int currPosition = (leftPosition + rightPosition) / 2;
			Node currNode = flatFatTree.getNodeOrNull(currPosition);
			int comparison = comparator.compare(currNode, timestamp);

			if (comparison == 0 && hasFound(currPosition, timestamp, comparator))
				return currPosition;

			// look left
			if (comparison==-1) {
				return getPositonBinary(timestamp, leftPosition, currPosition - 1, comparator);
			}
			// look right
			return getPositonBinary(timestamp, currPosition + 1, rightPosition, comparator);
	}

		private boolean hasFound(int nodePosition, long timestamp, Comparator comparator) {
			if(nodePosition == this.flatFatTree.getCurrentLeafPosition())
				return true;

			Node successor =  flatFatTree.getNodeOrNull(nodePosition + 1) ;
			return  comparator.compare(successor, timestamp) != 0;
		}

} }
