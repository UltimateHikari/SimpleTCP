import java.io.IOException;
import java.net.InetAddress;

public class Client implements Runnable {
	
	@Override
	public void run() {
		try {
			SimpleSocket socket = new SimpleSocket(5000);
			socket.connect(InetAddress.getByName("localhost"), 6000);
			for(int i = 0; i < 1; i++) {
				byte [] data = ("Sample packet number " + i).getBytes();
				socket.send(data);
			}
			socket.close();
		} catch (IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
