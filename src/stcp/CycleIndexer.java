package stcp;

class CycleIndexer {
	private final int length;
	public CycleIndexer(int length_) {
		length = length_;
	}
	
	public int getNext(int a) {
		return (a + 1) % length;
	}
	
	public boolean isBefore(int a, int b) {
		return a != b;
	}
}
