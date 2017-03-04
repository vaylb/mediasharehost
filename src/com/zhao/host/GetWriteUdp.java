
package com.zhao.host;

import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class GetWriteUdp implements Runnable {
    private final static int PORT = 43709;// remote port
    private final static String TAG = "GetWriteUdp";
    private InetAddress remoteIp;
    private HostPlay mHostPlay;
    private int getWriteCount;
    private boolean runflag;
    private boolean stopFlag;
    private long startGetSlave;
    private long endGetSlave;
    private int startHostWrite;
    private int endHostWrite;
    private int slaveHasWrite;
    private byte[] data;
    private byte[] revdata;
    private DatagramPacket request;
    private DatagramPacket responce;
    private DatagramSocket socket;

    public GetWriteUdp(InetAddress ip, HostPlay play) {
        this.remoteIp = ip;
        this.mHostPlay = play;
        this.getWriteCount = 0;
        this.runflag = true;
        this.stopFlag=true;
        this.data = UdpOrder.GET_WRITED.getBytes();
        this.revdata = new byte[10];
        this.request = new DatagramPacket(data, data.length, remoteIp, PORT);
        this.responce = new DatagramPacket(revdata, revdata.length);
    }

    public synchronized void setCount(int count) {
        boolean notify = (getWriteCount < 0);
        getWriteCount = count;
        if (notify)
            notify();
    }

    private synchronized void getCount() throws InterruptedException {
        if (--getWriteCount < 0) {
       //     Log.d(TAG, "pzhao->wait in get count");
            wait(10000);
        }
    }
    public synchronized void start(){
        stopFlag=false;
        notify();
    }
    public synchronized void stop(){
        stopFlag=true;
    }
    
    private synchronized void checkStop() throws InterruptedException{
        while(stopFlag)
            wait();
    }
    
    public synchronized void quit() {
        runflag = false;
        stopFlag=false;
        if (socket != null)
            socket.close();
        getWriteCount = 2;
        notify();
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        try {
            socket = new DatagramSocket(0);
            socket.setReuseAddress(true);
            socket.setSoTimeout(1000);
            while (runflag) {
                try{ 
                    getCount();
                    checkStop();
                    if (!runflag)
                        return;
                    startGetSlave = System.currentTimeMillis();
                    startHostWrite = mHostPlay.native_haswrite();
                    socket.send(request);
                    socket.receive(responce);
                    String result = new String(responce.getData(), 0, responce.getLength());
                    slaveHasWrite = Integer.valueOf(result);
                    endHostWrite = mHostPlay.native_haswrite();
                    endGetSlave = System.currentTimeMillis();
                    int dif, readahead;
                    dif = slaveHasWrite - ((endHostWrite + startHostWrite) >> 1);
//                    Log.d(TAG, "pzhao->udp cost:" + (endGetSlave - startGetSlave)
//                            / 1000000 + "ms, slave ahead host " + dif);
                    if (endGetSlave - startGetSlave > 15) {
                        Log.i(TAG, "pzhao->Udp cost too much time, pass!");
                        getWriteCount++;
                        continue;
                    }   
                    readahead = (dif - HostPlay.slave_host) >> 1;
                    if (readahead > 5 || readahead < -5) {
                        Log.i(TAG, "pzhao->read ahead " + readahead + " too much");
                        getWriteCount++;
                        readahead=readahead>0?5:-5;
                    }
                    if (dif > HostPlay.check_begin || dif < HostPlay.check_end) {
                        Log.i(TAG, "pzhao->host read ahead " + readahead);
                        mHostPlay.native_read_ahead(readahead);
                    }
                    Thread.sleep(200);
                    
                }catch (SocketTimeoutException e) {
                    //timeout declare slave leave
                    e.printStackTrace();
                    Message message=new Message();
                    message.what=6;
                    mHostPlay.mHandler.sendMessage(message);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }catch (IOException exception) {
                    exception.printStackTrace();
                } 
            }
        } catch (SocketException exception) {
            exception.printStackTrace();
            Message message=new Message();
            message.what=5;
            mHostPlay.mHandler.sendMessage(message);
        } finally {
            if (socket != null)
                socket.close();
        }

    }

}
