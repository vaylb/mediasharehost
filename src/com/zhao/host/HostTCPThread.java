
package com.zhao.host;

import android.os.Message;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class HostTCPThread implements Runnable {
    private static final String TAG = "HostTCP";
    private static final int DEFAULT_PORT_TCP = 43709;
    private final Lock lock = new ReentrantLock();
    private final Condition start = lock.newCondition();
    private final Condition isEmpty = lock.newCondition();

    private HostPlay mHostPlay;

    public volatile boolean TcpFlag = true;
    private volatile boolean startFlag = false;
    private volatile boolean stopFlag;
    private volatile boolean hasConnect;
    private volatile boolean signalByNative;

    private ServerSocket serverSocket = null;
    //private Socket socket = null;
    private ArrayList<Socket> sockets = null;
    private HashMap<Socket,BufferedOutputStream> socketstreams = null;
    //private BufferedOutputStream outputStream = null;
    private ByteBuffer data;
    private int mCount;
    private int bufferSize;
    private int readPos;

    public HostTCPThread(HostPlay hostPlay) {
        this.mHostPlay = hostPlay;
        this.data = hostPlay.buffer;
        this.mCount = HostPlay.mFrameCount;
        this.bufferSize=HostPlay.DEFAULTCOUNT*HostPlay.mFrameCount;
        this.sockets = new ArrayList<Socket>();
        this.socketstreams = new HashMap<Socket, BufferedOutputStream>();
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

    /*
     * blocked here when first time connect tcp with slave
     */
    private void cheakStart() throws InterruptedException, IOException {
        lock.lock();
        try {
            while (!startFlag) {
                Log.d(TAG, "vaylb->wait in checkatart");
                start.await();
            }
        } finally {
            lock.unlock();
        }

    }
    /*
     * check sendbuffer if can read
     */
    private boolean checkCanRead() throws InterruptedException {
//        if(HostPlay.native_checkreadpos())
//            return true;
        boolean ret = true;
        lock.lock();
        try {
            while (!HostPlay.native_checkreadpos()) {
                mHostPlay.native_signaleToWrite();
                if (!isEmpty.await(10, TimeUnit.MILLISECONDS)) { // 10ms timeout then
                    ret = false;
                    Log.d(TAG, "vaylb->Tcp check can read false");
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
        return ret;
    }
    
    /*
     * called by native HostProcessThread, signal tcp thread to read
     */
    public void signalToRead() {
        lock.lock();
        try {
            isEmpty.signal();
            signalByNative = true;
            Log.d(TAG, "vaylb-> signal tcp to send data");
        } finally {
            lock.unlock();
        }
    }

    /*
     * when standby is true, tcp thread should be stop
     */
    private void checkStop() {
        if (stopFlag)
        {
            try {
                //socket.close();
                //socket = null;
            	for(int i = 0; i < sockets.size(); i++){
    				sockets.get(i).close();
                }
            	sockets.clear();
            	socketstreams.clear();
                hasConnect = false;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    
    /*
     * called by native HostProcessThread when standby is true
     */
    public void stop() {
        stopFlag = true;
        startFlag = false;
    }

    /*
     * call when exit this app
     */
    public void quit(){
        TcpFlag=false;
        try {
			for (int i = 0; i < sockets.size(); i++) {
				sockets.get(i).close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	sockets.clear();
    	socketstreams.clear();
    }
    

    @Override
    public void run() {
        try {
        	Log.d(TAG, "vaylb-->TCP Thread id:"+Thread.currentThread().getName());
            serverSocket = new ServerSocket(DEFAULT_PORT_TCP);
            serverSocket.setReuseAddress(true);
            serverSocket.setSoTimeout(5000);
            int offset = 0;
            long time_send = 0;
            while (TcpFlag) {
                    if (!hasConnect) {
                        cheakStart();
                        int slave_num = mHostPlay.slaveAddressMap.size();
                        Log.d(TAG, "vaylb->Tcp listen, total slave size:"+slave_num);
                        while(sockets.size()<slave_num){
                        	long start = System.currentTimeMillis();
                        	Socket socket = serverSocket.accept();
                        	socket.setSoTimeout(5000);
                        	BufferedOutputStream outputStream = new BufferedOutputStream(socket.getOutputStream());
                        	sockets.add(socket);
                        	socketstreams.put(socket, outputStream);
                        	Log.d(TAG, "vaylb-->"+socket.getInetAddress().getHostAddress()+" connected, cost time"+(System.currentTimeMillis()-start));
                        }
                        hasConnect = true;
                        readPos=0;
                        Log.d(TAG, "vaylb->Tcp connected!");
                    } else {
                        if (checkCanRead()) {
                            offset = readPos % bufferSize;
                            int slave_num = sockets.size();
                            for(int i = 0; i < slave_num; i++){
                            	socketstreams.get(sockets.get(i)).write(data.array(), offset, mCount << 1);
                            	socketstreams.get(sockets.get(i)).flush();
                            }
                            
                            readPos += (mCount << 1);
                            HostPlay.native_setreadpos(readPos);
                            if (signalByNative) {
                                signalByNative = false;
                                mHostPlay.native_signaleToWrite();
                            }
                           
                            //when music player pause or change song, let check the host and slave has written 
                            if (mHostPlay.native_needcheckwrited())
                                mHostPlay.getSlaveWrite();
//                            Log.d(TAG, "vaylb-->Tcp thread send "+mCount+" bytes data, time slot: "+(System.currentTimeMillis()-time_send));
//                            time_send = System.currentTimeMillis();
                        }
//                        else{
//                        	Log.d(TAG, "vaylb->Tcp checkCanRead false");
//                        }
                        checkStop();
                    }
            }
            Log.d(TAG, "vaylb->Tcp thread end");
        }catch (SocketTimeoutException e) {
            e.printStackTrace();
            Message message=new Message();
            message.what=7;
            mHostPlay.mHandler.sendMessage(message);
        }
        catch (Exception e) {
            Log.d(TAG, "vaylb->tcp Exception");
            e.printStackTrace();
        } finally {
            Log.d(TAG, "vaylb-->Tcp thread end");
            for(int i = 0; i < sockets.size(); i++){
            	try {
					sockets.get(i).close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
            if (serverSocket != null)
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
        }

    }

}
