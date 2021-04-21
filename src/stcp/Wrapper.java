package stcp;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/*
 * need this guy in both server and common sockets
 */

//Util class for simplesocket
class Wrapper {
	public static final int HEADER_LEN = 3;

	public static DatagramPacket wrap(byte[] data, int currentACK, int packetNum, Flags flag,
			SimpleSocketAddress address) throws SocketException {
		if (address == null || address.getDestPort() == null) {
			throw new SocketException("Socket is not connected");
		}
		ByteArrayOutputStream bs = new ByteArrayOutputStream();
		bs.write(currentACK);
		bs.write(packetNum);
		bs.write((byte) flag.ordinal());
		bs.writeBytes(data);
		byte[] wrapped = bs.toByteArray();
		return new DatagramPacket(wrapped, wrapped.length, address.getAddress(), address.getDestPort());
	}

	public static Integer getServerAcceptPort(DatagramPacket packet) {
		return ByteBuffer.wrap(Arrays.copyOfRange(packet.getData(), HEADER_LEN, HEADER_LEN + 4)).getInt();
	}

	public static boolean hasPayload(DatagramPacket packet) {
		return (packet.getLength() > HEADER_LEN + 1);
	}
	
	public static byte[] getPayload(DatagramPacket packet) {
		return Arrays.copyOfRange(
				packet.getData(),
				HEADER_LEN,
				packet.getLength()
				);
	}
	
	public static byte getAckindex(DatagramPacket packet) {
		return packet.getData()[0];
	}
	public static byte getSeqindex(DatagramPacket packet) {
		return packet.getData()[1];
	}
	public static byte getFlag(DatagramPacket packet) {
		return packet.getData()[2];
	}
	
	public static String toHeadersString(DatagramPacket packet) {
		return "ACK:" + packet.getData()[0] + 
				";SEQ:" + packet.getData()[1] + 
				";Flag:" + Flags.values()[packet.getData()[2]];
	}

	public static boolean isEligibleForACK(DatagramPacket packet) {
		return (packet.getData()[2] != Flags.ACK.ordinal());
	}
}
