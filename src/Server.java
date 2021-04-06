import java.io.IOException;

public class Server implements Runnable{

	@Override
	public void run() {
		SimpleServerSocket ssocket = new SimpleServerSocket(6000);
		SimpleSocket socket = ssocket.accept();
		String s = null;
		for(int i = 0; i < 10; i++) {
			try {
				s = new String(socket.recieve());
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println(s);
		}
		try {
			socket.close();
		} catch (InterruptedException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
