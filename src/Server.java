import java.io.IOException;
import java.net.InetAddress;

public class Server implements Runnable{

	@Override
	public void run() {
		try {
		//SimpleServerSocket ssocket = new SimpleServerSocket(6000);
		//SimpleSocket socket = ssocket.accept();
		SimpleSocket socket = new SimpleSocket(6000);
		socket.connect(InetAddress.getByName("localhost"), 5000);
		String s = null;
		for(int i = 0; i < 2; i++) {
			byte[] data = socket.recieve();
			s = new String(data);
			System.out.println(s);
		}
		} catch (IOException | InterruptedException e){
			e.printStackTrace();
		}
	}
	
}
