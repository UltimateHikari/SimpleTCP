import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class SimpleSocket {
	class Resender extends TimerTask{
		@Override
		public void run() {
			synchronized(lock) {
				System.out.println("timer elapsed");
				if(getShifted(base) != end) {
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
	private static final int FIN = 3;
	
	private int base = 0;
	private int end = 0;//nextseqnum
	private int currentACK = 0;
	private final Object lock = new Object();
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

	
	class ReadLoop implements Runnable{
		private DatagramPacket packet = new DatagramPacket(new byte[ 1024 ], 1024);
		private int ackindex;
		private int index;
		private int flag;
		boolean isRunning = true;
		@Override
		public void run() {
			while(isRunning) {
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
						sendACK();
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
		
		private void sendACK() {
			send(new byte[1], ACK);
			//if we had sth to send with we would
		}
		
		private void handleACK() {
			//if we have something to resend
			//going to break at 256 tho
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
				sendACK();
				break;
			case FIN:
				sendACK();
				isRunning = false;
				break;
			default:
				//chill lol
			}
		}
	}
	
	public SimpleSocket(int port) throws IOException {
		socket = new DatagramSocket(port);
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
	
	public void send(byte[] data) {
		send(data, NOP);
	}
	
	private void send(byte[] data, int flag) {
		DatagramPacket packet;
		System.out.println("sending " + data.length + " bytes");
		try {
			packet = wrapData(data, end, flag);
			socket.send(packet);
			// fictional, but we have no payload for acks now
			if(flag != NOP) {
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
				recieved.add(Arrays.copyOfRange(
						recieving[currentACK].getData(),
						2,
						recieving[currentACK].getLength()
						));
				recieving[currentACK] = null;
				currentACK = getShifted(currentACK);
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
