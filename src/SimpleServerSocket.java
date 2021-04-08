import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

public class SimpleServerSocket {
	private DatagramSocket socket;
	private static final int timeout  = 1000;
	private int port;
	private InetAddress address;
	private int childPort;
	DatagramPacket packet = null;

	
	public SimpleServerSocket(int port_) throws SocketException, UnknownHostException {
		port = port_;
		childPort = port  + 1;
		address = InetAddress.getByName("localhost");
		socket = new DatagramSocket(port_);
	}
	
	public SimpleSocket accept() throws IOException, InstantiationException {
		DatagramPacket recvpacket = new DatagramPacket(new byte[1024], 1024);
		//TODO split in recvSYN/sendSYNACK/recvACK
		//send with synack corrected desPort
		//cause now client is spamming server like damned
		
		socket.receive(recvpacket);
		int destPort = recvpacket.getPort();
		int destSeq = recvpacket.getData()[1];
		
		packet = PacketWrapper.wrap(new byte[1], destSeq + 1, 0, Flags.SYNACK, address, destPort);
		socket.send(packet);
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {

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
			
		}, timeout);
		
		int recvDest, recvACK;
		do {
			socket.receive(recvpacket);
			recvDest = recvpacket.getPort();
			recvACK = recvpacket.getData()[0];
			System.out.println("SERVER: got 3rd ack");
		} while(recvDest != destPort || recvACK != 1);
		timer.cancel();
		
		System.out.println("SERVER: connected to " + destPort);
		SimpleSocket res = new SimpleSocket(childPort, recvACK);

		res.softConnect(InetAddress.getByName("localhost"), 5000);
		childPort++;
		return res;
	}
	
	public void close() {
		
	}
}
