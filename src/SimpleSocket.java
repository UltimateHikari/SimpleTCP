import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleSocket {	
	class Resender extends TimerTask{
		@Override
		public void run() {
			synchronized(lock) {
				System.out.println("timer elapsed");
				if(base != end) {
					System.out.println("now base/end " + base + " " + end);
					try {
						socket.send(sending[base]);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					isTimerSet = true;
					timer.schedule(new Resender(), timeout);
				}else {
					System.out.println("timer stopped");
				}
			}
		}
	}
	
	private static final int BUFFER_SIZE = 256;
	//ack seq flag(nop/ack/fyn/syn) /zero-byte[opt]
	private static final int HEADER_LEN = 3;
	private static final int NOP = 0;
	private static final int ACK = 1;
	private static final int SYN = 2;
	private static final int SYNACK = 3;
	private static final int FIN = 4;
	
	private int base = 0;
	private int end = 0;//nextseqnum
	private int currentACK = 0;
	private final Object lock = new Object();
	private final ReentrantLock connectLock = new ReentrantLock();
	private DatagramPacket[] sending = new DatagramPacket[BUFFER_SIZE];
	private DatagramPacket[] recieving = new DatagramPacket[BUFFER_SIZE];
	private ArrayBlockingQueue<byte[]> recieved = new ArrayBlockingQueue<byte[]>(BUFFER_SIZE);
	
	private Thread rThread;
	private DatagramSocket socket;
	private int destPort = 3000; //placeholder, need to connect anyway
	private InetAddress address = null;
	
	private Timer timer = new Timer();
	private boolean isTimerSet = false;
	private int timeout = 1000;
	
	boolean isRunning = true;
	boolean isConnected = false;
	
	class ReadLoop implements Runnable{
		private DatagramPacket packet = new DatagramPacket(new byte[ 1024 ], 1024);
		private int ackindex;
		private int index;
		private int flag;
		@Override
		public void run() {
			//TODO again, going to break at 256
			while(isRunning || base < end) {
				try {
					socket.receive(packet);
					fillHeaders(packet.getData());
					if(packet.getLength() > HEADER_LEN + 1) {
						synchronized(lock) {
							if(recieving[index] == null) {
								recieving[index] = packet;
							}
						}
						pushRecieved();
						handleACK();
						send(new byte[1], ACK);
					}else {
						//well its (not) clearly ack
						handleACK();
					}
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		private void fillHeaders(byte[] data) {
			ackindex = data[0];
			index = data[1];
			flag = data[2];
			System.out.println("got " + ackindex + " " + index + " " + flag + " from " + destPort);
		}
		
		private void handleACK() {
			//if we have something to resend
			//TODO going to break at 256 tho
			switch(flag) {
			case ACK:
				synchronized(lock) {
					for(int i = base; i < ackindex; i++) {
						sending[i] = null;
					}
				}
				base = ackindex > base ? ackindex : base;
				break;

			case SYN:
				send(new byte[1], SYNACK);
				break;
			case FIN:
				send(new byte[1], ACK);
				send(new byte[1], FIN);
				System.out.println("SOCKET: stopped reading");
				isRunning = false;
				break;
			default:
				//chill lol
			}
		}
	}
	
	public SimpleSocket(int port) throws IOException {
		socket = new DatagramSocket(port);
		connectLock.lock();
	}
	
	private DatagramPacket wrapData(byte[] data, int packetNum, int flag) throws InstantiationException {
		if(address == null) {
			throw new InstantiationException("not connected");
		}
		ByteArrayOutputStream bs = new ByteArrayOutputStream();
		bs.write(currentACK);
		bs.write(packetNum);
		bs.write((byte)flag);
		bs.writeBytes(data);
		byte [] wrapped = bs.toByteArray();
		return new DatagramPacket(wrapped, wrapped.length, address, destPort);
	}
	
	public byte[] recieve() throws InterruptedException {
		byte[] res;
		res = recieved.take();
		return res;
	}
	
	public void send(byte[] data) throws InterruptedException {
		connectLock.lock();
		try {
			send(data, NOP);
		}finally {
			connectLock.unlock();
		}
	}
	
	private void send(byte[] data, int flag) {
		//actually can check for is running here
		DatagramPacket packet;
		System.out.println("sending " + data.length + " bytes to "+ destPort);
		try {
			packet = wrapData(data, end, flag);
			socket.send(packet);
			// fictional, but we have no payload for acks now
			if(flag != ACK && flag != SYNACK) {
				synchronized(lock) {
					sending[end] = packet;
					end = getShifted(end);
					if(!isTimerSet) {
						isTimerSet = true;
						timer.schedule(new Resender(), timeout);
					}
				}
			}
		} catch (InstantiationException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	private int getShifted(int a) {
		return (a + 1) % BUFFER_SIZE;
	}
	
	private void pushRecieved() {
		//acking n-th with n+1 ack
		synchronized(lock) {
			while(recieving[currentACK] != null) {
				if(recieving[currentACK].getLength() > HEADER_LEN + 1) {
				recieved.add(Arrays.copyOfRange(
						recieving[currentACK].getData(),
						HEADER_LEN,
						recieving[currentACK].getLength()
						));
				recieving[currentACK] = null;
				currentACK = getShifted(currentACK);
			
				}
			}
		}
	}
	
	public void connect(InetAddress address_, int port) throws IOException {
		address = address_;
		destPort = port;
		try {
			send(new byte[1], SYN);
			DatagramPacket packet = new DatagramPacket(new byte[5], 5);
			socket.receive(packet);
			base = getShifted(base);
			System.out.println("connected to " + destPort);
		} finally {
			connectLock.unlock();
		}
		//mb want to check for real connection but nah, take your 3-way handshake
		rThread = new Thread(new ReadLoop());
		rThread.start();
	}
	
	public void softConnect(InetAddress address_, int port) {
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
