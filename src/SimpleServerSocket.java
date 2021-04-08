import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class SimpleServerSocket {
	private DatagramSocket socket;
	private int port;
	private int childPort;
	
	public SimpleServerSocket(int port_) throws SocketException {
		port = port_;
		childPort = port  + 1;
		socket = new DatagramSocket(port_);
	}
	
	public SimpleSocket accept() throws IOException {
		DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
		SimpleSocket res = new SimpleSocket(childPort);
		socket.receive(packet);
		int destPort = packet.getPort();
		//send synack
		//wait for ack (without timer??)
		System.out.println("SERVER: connected to " + destPort);
		res.softConnect(InetAddress.getByName("localhost"), 5000);
		childPort++;
		return res;
	}
	
	public void close() {
		
	}
}
