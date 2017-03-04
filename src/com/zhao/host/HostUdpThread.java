package com.zhao.host;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import android.util.Log;

public class HostUdpThread implements Runnable {
	public static final int DEFAULT_PORT = 43708;
	private byte[] buffer = new byte[10];
	private String ipString;
	private String udpmsg;
	private DatagramSocket udpSocket;

	public HostUdpThread(String ip, String msg) {
		this.udpmsg = msg;
		this.ipString = ip;
	}

	public void run() {
		DatagramPacket udpPacket = null;
		try {
			// udpSocket = new DatagramSocket(DEFAULT_PORT);
			if (udpSocket == null) {

				udpSocket = new DatagramSocket(null);
				udpSocket.setReuseAddress(true);
				udpSocket.bind(new InetSocketAddress(DEFAULT_PORT));
			}

			udpPacket = new DatagramPacket(buffer, 10);
			byte[] data = udpmsg.getBytes();
			udpPacket.setData(data);
			udpPacket.setLength(data.length);
			udpPacket.setPort(DEFAULT_PORT);
			InetAddress broadcastAddr = InetAddress.getByName(ipString);
			udpPacket.setAddress(broadcastAddr);
		} catch (Exception e) {
			Log.e("UdpThread", e.toString());
		}

		try {
			udpSocket.send(udpPacket);
			Thread.sleep(10);
		} catch (Exception e) {
			Log.e("UdpThread", e.toString());
		}
		if (null != udpSocket)
			udpSocket.close();
	}

}
