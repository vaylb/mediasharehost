
package com.zhao.host;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class SendUdp implements Runnable {
    private final static int PORT = 43709;// remote port
    private final static String TAG = "SendUdp";
    private String sendMsg;
    private InetAddress remoteIp;
    private int tryCount = 2;
    private boolean sendSucess;

    public SendUdp(String msg, InetAddress ip) {
        this.sendMsg = msg;
        this.remoteIp = ip;
        Log.d(TAG, "pzhao->send udp " + UdpOrder.map.get(msg));
    }

    @Override
    public void run() {
        DatagramSocket socket = null;
        try {
            byte[] data = sendMsg.getBytes();
            byte[] rcvdata = new byte[10];
            socket = new DatagramSocket(0);
            socket.setReuseAddress(true);
            socket.setSoTimeout(5000);
            DatagramPacket request = new DatagramPacket(data, data.length, remoteIp, PORT);
            DatagramPacket responce = new DatagramPacket(rcvdata, rcvdata.length);
            while (--tryCount >= 0) {
                try {
                    socket.send(request);
                    socket.receive(responce);
                    String result = new String(responce.getData(), 0, responce.getLength());
                    if (result.equals(sendMsg)) {
                        sendSucess = true;
                        Log.d(TAG, "pzhao->udp " + UdpOrder.map.get(sendMsg) + " send success try "+(2-tryCount));
                        break;
                    }
                } catch (SocketTimeoutException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO: handle exception
                    e.printStackTrace();
                    break;
                }
            }
            if (!sendSucess)
                Log.d(TAG, "pzhao->udp " + UdpOrder.map.get(sendMsg) + " send fail");
        } catch (IOException exception) {
            exception.printStackTrace();
        } finally {
            if (socket != null)
                socket.close();
        }

    }

}
