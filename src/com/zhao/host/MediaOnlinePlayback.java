package com.zhao.host;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.os.Message;
import android.util.Log;

public class MediaOnlinePlayback extends Thread{
	private static final String TAG = "MediaOnlinePlayback";
    private static final int DEFAULT_PORT_TCP = 12307;
    private final Lock lock = new ReentrantLock();
    private final Condition start = lock.newCondition();

    private HostPlay mHostPlay;

    public volatile boolean TcpFlag = true;
    private volatile boolean startFlag = false;
    private volatile boolean stopFlag;
    private volatile boolean hasConnect;
    private volatile boolean signalByNative;
    public boolean runFlag = false;

    private ServerSocket serverSocket = null;
    private Socket socket = null;
    public String gatewayIp;
    public BufferedInputStream inputStream;
    public BufferedOutputStream outputStream;

	public MediaOnlinePlayback(HostPlay hostPlay) {
        this.mHostPlay = hostPlay;
        this.gatewayIp = null;
    }
	
	@Override
	public void run() {
		try{
            while (mHostPlay.mediaOnline) {
            	try {
        			if (!runFlag) {
                        Log.d(TAG, "vaylb--> TCP connect to Gateway, ip: "+gatewayIp);
                        socket = new Socket(gatewayIp, DEFAULT_PORT_TCP);
                        socket.setSoTimeout(5000);
                        inputStream = new BufferedInputStream(socket.getInputStream());
                        outputStream = new BufferedOutputStream(socket.getOutputStream());
                        char command = 'P';
                        outputStream.write(command);
                        int rec_size = inputStream.read();
                        byte[] rec_val = new byte[rec_size];
                        inputStream.read(rec_val);
                        Log.d(TAG, "vaylb-->TCP connected, get ips:"+rec_val.toString());
                        runFlag = true;
                        mHostPlay.mediaOnline = false;
                    }
                    
                    if(!runFlag){
                        sleep(10);
                    }
                }catch (SocketTimeoutException e) {
                    e.printStackTrace();
                    Message message=new Message();
                    message.what=9;
                    mHostPlay.mHandler.sendMessage(message);
                }catch (Exception e) {
                    // TODO: handle exception
                    Log.d(TAG, "vaylb-->tcp Exception");
                    e.printStackTrace();
                }
            }
        }
        finally{
            if(socket!=null)
                try {
                    socket.close(); 
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
        }
		Log.d(TAG, "vaylb-->MediaOnlinePlayback thread end");
	}
	
	public void setGatewayIp(String ip){
		this.gatewayIp = ip;
	}
	
	public void start() {
        lock.lock();
        try {
            startFlag = true;
            stopFlag = false;
            start.signal();
        } finally {
            lock.unlock();
        }
    }
	
    private void cheakStart() throws InterruptedException, IOException {
        lock.lock();
        try {
            while (!startFlag) {
                Log.d(TAG, "pzhao->wait in checkatart");
                start.await();
            }
        } finally {
            lock.unlock();
        }

    }
    
    private void checkStop() {
        if (stopFlag)
        {
            try {
                socket.close();
                socket = null;
                hasConnect = false;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    /*
     * call when exit this app
     */
    public void quit(){
        TcpFlag=false;
        if(socket!=null)
            try {
                socket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
    }
}
