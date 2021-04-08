import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;

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
			) throws InstantiationException {
		if(address == null) {
			throw new InstantiationException("not connected");
		}
		ByteArrayOutputStream bs = new ByteArrayOutputStream();
		bs.write(currentACK);
		bs.write(packetNum);
		bs.write((byte)flag.value);
		bs.writeBytes(data);
		byte [] wrapped = bs.toByteArray();
		return new DatagramPacket(wrapped, wrapped.length, address, destPort);
	}
}
