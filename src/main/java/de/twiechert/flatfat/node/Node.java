package de.twiechert.flatfat.node;

import de.twiechert.flatfat.Mergeable;
import de.twiechert.flatfat.StateFactory;


/**
 * @author Tayfun Wiechert <wiechert@campus.tu-berlin.de>
 */
public interface Node<IN, OUT> {

	Mergeable<IN, OUT> getValueState();

	long getStart();

	long getEnd();

	void setStart(long start);

	void setEnd(long end);

	void setValueState(Mergeable<IN, OUT> valueState);

	long getTmax();

	void setTmax(long tmax);

	class InnerNode<IN, OUT> implements Node<IN, OUT> {

		private  Mergeable<IN, OUT> Mergeable;

		private long start = -1l;
		private long end = 0l;
		private long tmax = 0l;
		private boolean identity = false;


		public InnerNode(Mergeable<IN, OUT> Mergeable, long tmax) {
			this.Mergeable = Mergeable;
			this.tmax = tmax;
		}

		public InnerNode(Mergeable<IN, OUT> Mergeable, long start, long end) {
			this.Mergeable = Mergeable;
			this.start = start;
			this.end = end;
		}

		public InnerNode(Node<IN, OUT> node,
						 StateFactory<IN, OUT> partialStateFactory) {
			this.start = node.getStart();
			this.end = node.getEnd();
			try {
				Mergeable = partialStateFactory.getState().merge(node.getValueState());

			} catch (Exception e) {
			}
		}


		public InnerNode(Mergeable<IN, OUT> Mergeable) {
			this.Mergeable = Mergeable;
		}

		public InnerNode(Mergeable<IN, OUT> Mergeable, boolean identity) {
			this.Mergeable = Mergeable;
			this.identity = identity;
		}

		@Override
		public void setValueState(Mergeable<IN, OUT> valueState) {
			this.Mergeable = valueState;
		}

		@Override
		public void setTmax(long tmax) {
			this.tmax = tmax;
		}

		@Override
		public Mergeable<IN, OUT> getValueState() {
			return Mergeable;
		}

		@Override
		public long getStart() {
			return start;
		}

		@Override
		public long getEnd() {
			return end;
		}

		@Override
		public void setStart(long start) {
			this.start = start;
		}

		@Override
		public void setEnd(long end) {
			this.end = end;
		}

		@Override
		public long getTmax() {
			return tmax;
		}


		@Override
		public boolean equals(Object o) {

			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			InnerNode<?, ?> innerNode = (InnerNode<?, ?>) o;

			if(this.identity && ((InnerNode<?, ?>) o).identity)
				return true;

			if (start != innerNode.start) return false;
			if (end != innerNode.end) return false;
			if (tmax != innerNode.tmax) return false;
			if (identity != innerNode.identity) return false;
			try {
				return Mergeable.get() != null ? Mergeable.get().equals(innerNode.Mergeable) : innerNode.Mergeable.get() == null;
			} catch(Exception e) {
				return false;
			}
		}

		@Override
		public int hashCode() {
			int result = Mergeable != null ? Mergeable.hashCode() : 0;
			result = 31 * result + (int) (start ^ (start >>> 32));
			result = 31 * result + (int) (end ^ (end >>> 32));
			result = 31 * result + (int) (tmax ^ (tmax >>> 32));
			result = 31 * result + (identity ? 1 : 0);
			return result;
		}
	}
}
