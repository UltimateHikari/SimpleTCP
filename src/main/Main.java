package main;

public class Main {
	public static void main(String[] args) throws InterruptedException{
		Thread server = new Thread(new Server());
		server.start();
		Thread client = new Thread(new Client());
		client.start();
		server.join();
		client.join();
	}
}
