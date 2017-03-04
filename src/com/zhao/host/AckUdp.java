package com.zhao.host;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class AckUdp implements Runnable {
    private static final String TAG="AckUdp";
    private int port;
    private InetAddress remoteIp;
    private String ackMsg;
    public AckUdp(HostPlay hostplay,String msg,InetAddress ip,int port){
        if(msg!=null&&msg.equals(UdpOrder.GET_FRAME_COUNT)){
            this.ackMsg=String.valueOf(HostPlay.mFrameCount);
        }else if (msg != null && msg.equals(UdpOrder.GET_WRITED)){
            this.ackMsg = String.valueOf(hostplay.native_haswrite());
        }else {
            this.ackMsg=msg;
        }
        this.remoteIp=ip;
        this.port=port;
        Log.i(TAG, "vaylbpzhao->Ack udp  msg "+UdpOrder.map.get(msg));
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        DatagramSocket socket=null;
        try{
            byte[] data=ackMsg.getBytes();
            socket=new DatagramSocket(0);
            socket.setReuseAddress(true);
            DatagramPacket request=new DatagramPacket(data,data.length, remoteIp,port);
            socket.send(request);
        }catch(IOException exception){
            exception.printStackTrace();
        }
        
    }

}
