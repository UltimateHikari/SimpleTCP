package stcp;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;

/*
 * need this guy in both server and common sockets
 */

public class PacketWrapper {
	public static DatagramPacket wrap(
			byte[] data,
			int currentACK,
			int packetNum,
			Flags flag,
			InetAddress address,
			int destPort
			) throws SocketException {
		if(address == null) {
			throw new SocketException("Socket is not connected");
		}
		ByteArrayOutputStream bs = new ByteArrayOutputStream();
		bs.write(currentACK);
		bs.write(packetNum);
		bs.write((byte)flag.ordinal());
		bs.writeBytes(data);
		byte [] wrapped = bs.toByteArray();
		return new DatagramPacket(wrapped, wrapped.length, address, destPort);
	}
}
