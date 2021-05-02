package stcp;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class SimpleServerSocket implements AutoCloseable{
	private DatagramSocket socket;
	private static final int timeout  = 1000;
	private SimpleSocketAddress address = new SimpleSocketAddress();
	private int nextAcceptedPort;
	DatagramPacket packet = null;

	
	public SimpleServerSocket(int port) throws SocketException, UnknownHostException {
		address.setSourcePort(port);
		nextAcceptedPort = port  + 1;
		socket = new DatagramSocket(port);
	}
	
	/*
	 * Single-threaded, potential bottleneck
	 */
	public SimpleSocket accept() throws SocketException, IOException{
		Timer timer = new Timer();
		
		int destSeq = recvSYN();
		sendSYNACK(destSeq, timer);
		int recvACK = recvACK(timer);
		log("connect to " + address.getDestPort());
		SimpleSocket res = new SimpleSocket(nextAcceptedPort, recvACK);
		res.softConnect(address.getAddress(), address.getDestPort());

		nextAcceptedPort++;
		return res;
	}
	private int recvSYN() throws IOException {
		DatagramPacket recvpacket = new DatagramPacket(new byte[1024], 1024);
		socket.receive(recvpacket);
		address.setAddress(recvpacket.getAddress());
		address.setDestPort(recvpacket.getPort());
		return recvpacket.getData()[1]; //TODO move to WRAPPER
	}
	
	private void sendSYNACK(int destSeq, Timer timer) throws SocketException, IOException {
		packet = Wrapper.wrap(
				ByteBuffer.allocate(4).putInt(nextAcceptedPort).array(),
				destSeq + 1,
				0,
				Flags.SYNACK,
				address);
		socket.send(packet);

		
		timer.schedule(
			new TimerTask() {
				@Override
				public void run() {
					try {
						System.out.println("SERVER: timer elapsed");
						socket.send(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			},
			timeout);
	}
	
	private int recvACK(Timer timer) throws IOException {
		DatagramPacket recvpacket = new DatagramPacket(new byte[1024], 1024);
		int recvDest, recvACK = 0;
		do {
			socket.receive(recvpacket);
			recvDest = recvpacket.getPort();
			recvACK = recvpacket.getData()[0];
			log("SYN: got 3rd ack");
		} while(recvDest != address.getDestPort() || recvACK != 1);
		timer.cancel();
		return recvACK;
	}

	public void close() {
		socket.close();
	}
	
	private void log(String s) {
		System.out.println("Server[" + address.getSourcePort() + "]: " + s);
	}
}
