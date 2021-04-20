package stcp;

import java.net.InetAddress;

public class SimpleSocketAddress {
	private Integer sourcePort;
	private Integer destPort;
	private InetAddress address;

	public SimpleSocketAddress() {
		this(null, null, null);
	}

	public SimpleSocketAddress(Integer sourcePort_, Integer destPort_, InetAddress address_) {
		sourcePort = sourcePort_;
		destPort = destPort_;
		address = address_;
	}
	
	public void setAddress(InetAddress address_) {
		address = address_;
	}
	
	public void setSourcePort(Integer port) {
		sourcePort = port;
	}
	
	public void setDestPort(Integer port) {
		sourcePort = port;
	}
	
	public Integer getSourcePort() {
		return sourcePort;
	}
	
	public Integer getDestPort() {
		return destPort;
	}
	
	public InetAddress getAddress(){
		return address;
	}
}
