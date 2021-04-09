import java.io.IOException;

public class Server implements Runnable{

	@Override
	public void run() {
		try {
		SimpleServerSocket ssocket = new SimpleServerSocket(6000);
		SimpleSocket socket = ssocket.accept();
		String s = null;
		for(int i = 0; i < 1; i++) {
			byte[] data = socket.recieve();
			s = new String(data);
			System.out.println("MESSAGE: " +  s);
		}
		socket.close();
		} catch (IOException | InterruptedException e){
			e.printStackTrace();
		}
	}
	
}
