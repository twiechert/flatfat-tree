package de.twiechert.flatfat.node;

/**
 * @author Tayfun Wiechert <wiechert@campus.tu-berlin.de>
 */
public  class NodeIndexPosition<IN, ACC> {

	private final Node<IN, ACC> node;

	private final int index;

	private final int position;

	public NodeIndexPosition(Node<IN, ACC> node, int index, int position) {
		this.node = node;
		this.index = index;
		this.position = position;
	}

	public Node<IN, ACC> getNode() {
		return node;
	}

	public int getIndex() {
		return index;
	}

	public int getPosition() {
		return position;
	}
}
