import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleSocket {	
	class Resender extends TimerTask{
		@Override
		public void run() {
			synchronized(lock) {
				log("timer elapsed");
				if(base != end) {
					log("base/end " + base + " " + end);
					try {
						log("resending " + base);
						socket.send(sending[base]);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					isTimerSet = true;
					timer.schedule(new Resender(), timeout);
				}else {
					log("timer stopped");
				}
			}
		}
	}
	
	private static final int BUFFER_SIZE = 256;
	//ack seq flag(nop/ack/fyn/syn) /zero-byte[opt]
	private static final int HEADER_LEN = 3;
	private static final boolean LOG_LEVEL = true;
	
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
	private int myPort;
	private int destPort = 3000; //placeholder, need to connect anyway
	private int correctedDestPort = 3000;
	private InetAddress address = null;
	
	private Timer timer = new Timer();
	private boolean isTimerSet = false;
	private int timeout = 200;
	
	boolean isRunning = true;
	boolean isConnected = false;
	private Random random = new Random();
	
	class ReadLoop implements Runnable{
		private DatagramPacket packet; 
		private int ackindex;
		private int index;
		private int flag;
		@Override
		public void run() {
			//TODO again, going to break at 256
			while(isRunning || base < end || isConnected) {
				try {
					packet = new DatagramPacket(new byte[ 1024 ], 1024);
					socket.receive(packet);
					fillHeaders(packet.getData());
						synchronized(lock) {
							if(recieving[index] == null && currentACK <= index) {
								recieving[index] = packet;
								//log("taking " + index);
							} else {
								//log("discarding " + index);
							}
							pushRecieved();
							if(eligibleForACK(packet)) {
								send(new byte[1], Flags.ACK);
							}
						}
						handleFlag();

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			log("closed");
			socket.close();
		}
		
		private void fillHeaders(byte[] data) {
			ackindex = data[0];
			index = data[1];
			flag = data[2];
			log("got " + ackindex + " " + index + " "
			+ flag + " from " + destPort);
		}
		
		private void handleFlag() {
			//if we have something to resend
			//TODO going to break at 256 tho
			if(flag == Flags.ACK.value) {
				synchronized(lock) {
					for(int i = base; i < ackindex; i++) {
						sending[i] = null;
					}
				}
				base = ackindex > base ? ackindex : base;
			}
			
			if(flag == Flags.FIN.value) {
				//means other side stopped sending useful packets
				log("got fin");
//				if(isConnected) {
//					send(new byte[1], Flags.FIN);
//				}
				isRunning = false;
			}
		}
	}
	
	public SimpleSocket(int port, int destACK) throws IOException {
		this(port);
		currentACK = destACK;
	}
	
	public SimpleSocket(int port) throws IOException {
		myPort = port;
		socket = new DatagramSocket(port);
		connectLock.lock();
	}
	
	public byte[] recieve() throws InterruptedException, SocketException{
		if(isConnected || base < end || recieved.size() > 0) {
			byte[] res;
			res = recieved.take();
			return res;
		}else {
			throw new SocketException("Socket is closed");
		}
	}
	
	public void send(byte[] data) throws InterruptedException, SocketException {
		connectLock.lock();
		try {
			send(data, Flags.NOP);
		}finally {
			connectLock.unlock();
		}
	}
	
	private void send(byte[] data, Flags flag) {
		//actually can check for is running here
		DatagramPacket packet;
		try {
			packet = PacketWrapper.wrap(data, currentACK, end, flag, address, destPort);
			//if(random.nextInt(10) > 6) {
			//	log("NOT sending " + data.length + " bytes to "+ destPort);
			//}else {
				log("sending "+ flag + " ; " + data.length + " bytes to "+ destPort);
				socket.send(packet);
			//}
			// fictional, but we have no payload for acks now
			if(flag != Flags.ACK) {
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
	
	private boolean hasPayload(DatagramPacket packet) {
		return (packet.getLength() > HEADER_LEN + 1);
	}
	
	private boolean eligibleForACK(DatagramPacket packet) {
		return (packet.getData()[2] != Flags.ACK.value);
	}
	
	private void pushRecieved() {
		//acking n-th with n+1 ack
		while(recieving[currentACK] != null) {
			//log("pushing " + currentACK);
			if(hasPayload(recieving[currentACK])) {
			recieved.add(Arrays.copyOfRange(
					recieving[currentACK].getData(),
					HEADER_LEN,
					recieving[currentACK].getLength()
					));
			}
			recieving[currentACK] = null;
			currentACK = getShifted(currentACK);
		}
	}
	
	public void connect(InetAddress address_, int port) throws IOException {
		address = address_;
		destPort = port;
		try {
			sendSYN();
			int serverSeq = recvSYNACK();
			send3rdACK(serverSeq);
			
			isConnected = true;
			log("connected to " + destPort);
		} finally {
			connectLock.unlock();
		}
		//mb want to check for real connection but nah, take your 3-way handshake
		rThread = new Thread(new ReadLoop());
		rThread.start();
	}

	private void sendSYN() {
		send(new byte[1], Flags.SYN);
	}
	
	private int recvSYNACK() {
		DatagramPacket packet = new DatagramPacket(new byte[10], 10);
		try {
			socket.receive(packet);
			correctedDestPort = ByteBuffer.wrap(
					Arrays.copyOfRange(
							packet.getData(), 
							HEADER_LEN,
							HEADER_LEN + 4)
					).getInt();
			log("heard " + correctedDestPort + " from "  + packet.getPort());
			//destPort = newDest;
		
		} catch (IOException e) {
			e.printStackTrace();
		}
		return packet.getData()[1];
	}
	
	private void send3rdACK(int serverSeq) {
		try {
			socket.send(PacketWrapper.wrap(
					new byte[1],
					serverSeq + 1,
					base,
					Flags.ACK,
					address,
					destPort));
			destPort = correctedDestPort;
		} catch (InstantiationException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		base = getShifted(base);
	}
	
	public void softConnect(InetAddress address_, int port) {
		address = address_;
		destPort = port;
		isConnected = true;
		rThread = new Thread(new ReadLoop());
		rThread.start();
	}
	
	private void log(String s) {
		if(LOG_LEVEL) {
			System.out.println("[" + myPort + "]: " + s);
		}
	}
	
	public void close() throws InterruptedException, IOException {
		// send fin when done writing
		// stop recv when got fin
		log("closing...");
		send(new byte[1], Flags.FIN);
		isConnected = false;
		// then getting ack and closing in readloop
	}

}
