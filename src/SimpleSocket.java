import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;

public class SimpleSocket {
	class Resender extends TimerTask{
		@Override
		public void run() {
			synchronized(lock) {
				isTimerSet = false;
				if(getShifted(base) != end) {
					try {
						socket.send(sending[base]);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					isTimerSet = true;
					timer.schedule(resender, timeout);
				}
			}
		}
	}
	
	private static final int BUFFER_SIZE = 256;
	private int base = 0;
	private int end = 0;
	private int currentACK = 0;
	private final Object lock = new Object();
	private DatagramPacket[] sending = new DatagramPacket[BUFFER_SIZE];
	private DatagramPacket[] recieving = new DatagramPacket[BUFFER_SIZE];
	private BlockingQueue<byte[]> recieved;
	
	private Thread rThread;
	private DatagramSocket socket;
	private int destPort = 3000; //placeholder, need to connect anyway
	private InetAddress address = null;
	
	private Timer timer = new Timer();
	private boolean isTimerSet = false;
	private Resender resender = new Resender();
	private int timeout = 500;
	
	class ReadLoop implements Runnable{
		private DatagramPacket packet = new DatagramPacket(new byte[ 1024 ], 1024);
		private int ackindex;
		private int index;
		@Override
		public void run() {
			while(true) {
				try {
					socket.receive(packet);
					ackindex = packet.getData()[0];
					index = packet.getData()[1];
					if(packet.getData().length > 3) {
						synchronized(lock) {
							if(recieving[index] == null) {
								recieving[index] = packet;
							}
						}
						pushRecieved();
					}else {
						//well its clearly ack
					}
					sendACK();
					handleACK();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		private void sendACK() {
			send(new byte[1]);
			//if we had sth to send with we would
		}
		
		private void handleACK() {
			//if we have something to resend
//			if(base != end) {
				//but nah
//			}
			synchronized(lock) {
				for(int i = base; i <= ackindex; i++) {
					sending[i] = null;
				}
			}
			base = ackindex;
		}
	}
	
	public SimpleSocket(int port) throws IOException {
		socket = new DatagramSocket(port);
	}
	
	private DatagramPacket wrapData(byte[] data, int packetNum) throws InstantiationException {
		if(address == null) {
			throw new InstantiationException("not connected");
		}
		ByteArrayOutputStream bs = new ByteArrayOutputStream();
		bs.write(currentACK);
		bs.write(packetNum);
		bs.writeBytes(data);
		byte [] wrapped = bs.toByteArray();
		return new DatagramPacket(wrapped, wrapped.length, address, destPort);
	}
	
	public byte[] recieve() throws InterruptedException {
		byte[] res;
		synchronized(lock) {
			res = recieved.take();
		}
		return res;
	}
	
	public void send(byte[] data) {
		DatagramPacket packet;
		try {
			packet = wrapData(data, end);
			socket.send(packet);
			synchronized(lock) {
				sending[end] = /*new SimplePacket(*/packet;
				shift(end);
				if(!isTimerSet) {
					timer.schedule(resender, timeout);
				}
			}
			
		} catch (InstantiationException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	private void shift(int a) {
		a = getShifted(a);
	}
	
	private int getShifted(int a) {
		return (a + 1) % BUFFER_SIZE;
	}
	
	private void pushRecieved() {
		synchronized(lock) {
			while(recieving[currentACK] != null) {
				recieved.add(recieving[currentACK].getData());
				recieving[currentACK] = null;
				shift(currentACK);
			}
		}
	}
	
	public void connect(InetAddress address_, int port) throws IOException {
		address = address_;
		destPort = port;
		rThread = new Thread(new ReadLoop());
		rThread.start();
	}
	
	public void close() throws InterruptedException, IOException {
		//ССЗБ if closed early
		socket.close();
		rThread.interrupt();
		//sThread.interrupt();
	}
}
