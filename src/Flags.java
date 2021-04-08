
public enum Flags {
	NOP(0),
	ACK(1),
	SYN(2),
	SYNACK(3),
	FIN (4);
	public int value;
	private Flags(int value_) {
		value = value_;
	}
}
