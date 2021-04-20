package stcp;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
	
	public SimpleSocket accept() throws SocketException, IOException{
		Timer timer = new Timer();
		int destSeq = recvSYN();
		sendSYNACK(destSeq, timer);
		int recvACK = recvACK(timer);
		//TODO no handling for exceptions here;
		//single-threaded also
		System.out.println("SERVER: connected to " + address.getDestPort());
		SimpleSocket res = null;
		res = new SimpleSocket(nextAcceptedPort, recvACK);
		res.softConnect(InetAddress.getByName("localhost"), 5000);

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
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			},
			timeout);
	}
	
	private int recvACK(Timer timer) {
		DatagramPacket recvpacket = new DatagramPacket(new byte[1024], 1024);
		int recvDest, recvACK = 0;
		do {
			try {
				socket.receive(recvpacket);
			} catch (IOException e) {
				e.printStackTrace();
			}
			recvDest = recvpacket.getPort();
			recvACK = recvpacket.getData()[0];
			System.out.println("SERVER: got 3rd ack");
		} while(recvDest != address.getDestPort() || recvACK != 1);
		timer.cancel();
		return recvACK;
	}

	public void close() {
		//TODO
	}
}
