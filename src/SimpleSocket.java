import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.BlockingQueue;

enum Status{
	QUEUED,
	SENT;
}

class SimplePacket{
	private DatagramPacket packet;
	private Status status;
	public SimplePacket(DatagramPacket packet_) {
		status = Status.QUEUED;
		packet = packet_;
	}
	public Status getStatus() {
		return status;
	}
	public void markSent() {
		status = Status.SENT;
	}
	public DatagramPacket getPacket() {
		return packet;
	}
}

/*
 * datagram structure:
 * ..
 * ACK byte -> ACK bit + 7 ACK bits
 * SEQ byte -> SEQ bit + 7 SEQ bits
 * ..
 * data
 * ..
 * (SEQ bit because of one-sided server nature)
 */

public class SimpleSocket {
	private static final byte IS_TO_IGNORE = (byte) 0x80;
	private static final byte BUFFER_SIZE = (byte)128;
	private int base = 0;
	private int end = 0;
	private int currentACK = 0;
	//TODO rewrite to ArrayList and syncronizing
	private SimplePacket[] sending = new SimplePacket[BUFFER_SIZE];
	private SimplePacket[] recieving = new SimplePacket[BUFFER_SIZE];
	private BlockingQueue<byte[]> recieved;
	private Thread rThread;
	private DatagramSocket socket;
	private int destPort;
	private InetAddress address = null;
	
	class ReadLoop implements Runnable{
		private DatagramPacket packet;
		@Override
		public void run() {
			while(true) {
				try {
					socket.receive(packet);
					//TODO get number and put into recieving
					//and then send ACK;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	public SimpleSocket(int port) throws IOException {
		socket = new DatagramSocket(port);
		rThread = new Thread(new ReadLoop());
		//sThread = new Thread(new SendLoop());
		rThread.start();
		//sThread.start();
	}
	
	private DatagramPacket wrapData(byte[] data) throws InstantiationException {
		if(address == null) {
			throw new InstantiationException("not connected");
		}
		//TODO add packet number here
		return new DatagramPacket(data, data.length, address, destPort);
	}
	
	public byte[] recieve() throws InterruptedException {
		return recieved.take();
	}
	
	public void send(byte[] data) {
		DatagramPacket packet;
		try {
			packet = wrapData(data);
			socket.send(packet);
			sending[end] = new SimplePacket(packet);
			shift(end);
		} catch (InstantiationException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	private void shift(int a) {
		a = (a + 1) % BUFFER_SIZE;
	}
	
	private void pushRecieved() {
		while(recieving[base] != null) {
			recieved.add(recieving[base].getPacket().getData());
			recieving[base] = null;
			shift(base);
		}
	}
	
	public void connect(InetAddress address_, int port) throws IOException {
		address = address_;
		destPort = port;
	}
	
	public void close() throws InterruptedException, IOException {
		//ССЗБ if closed early
		socket.close();
		rThread.interrupt();
		//sThread.interrupt();
	}
}
