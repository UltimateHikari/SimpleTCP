import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
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
						send(new byte[1], Flags.ACK);
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
			log("got " + ackindex + " " + index + " "
			+ flag + " from " + destPort);
		}
		
		private void handleACK() {
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
				send(new byte[1], Flags.ACK);
				send(new byte[1], Flags.FIN);
				log("stopped reading");
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
	
	public byte[] recieve() throws InterruptedException {
		byte[] res;
		res = recieved.take();
		return res;
	}
	
	public void send(byte[] data) throws InterruptedException {
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
		log("sending " + data.length + " bytes to "+ destPort);
		try {
			packet = PacketWrapper.wrap(data, currentACK, end, flag, address, destPort);
			socket.send(packet);
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
			sendSYN();
			int serverSeq = recvSYNACK();
			send3rdACK(serverSeq);
			
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
		rThread = new Thread(new ReadLoop());
		rThread.start();
	}
	
	private void log(String s) {
		System.out.println("[" + myPort + "]: " + s);
	}
	
	public void close() throws InterruptedException, IOException {
		//ССЗБ if closed early
		socket.close();
		rThread.interrupt();
		//sThread.interrupt();
	}

}
