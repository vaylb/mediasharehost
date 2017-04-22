
package com.zhao.host;

import android.media.AudioManager;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentMap;

public class ReceiveUdp implements Runnable {
    private final static int PORT = 43709;// local port
    private final static String TAG = "ReceiveUdp";
    private DatagramSocket socket = null;
    private int preVolume = 14;
    public volatile boolean runFlag = true;
    private HostPlay mHostPlay;
    public volatile boolean hasInit = false;
    private int slave_ready_count = 0;

    public ReceiveUdp(HostPlay play) {
        this.mHostPlay = play;
    }

    public void stop() {
        runFlag = false;
        if (socket != null)
            socket.close();
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        try {
            socket = new DatagramSocket(PORT);
            socket.setReuseAddress(true);
            byte[] data = new byte[10];
            DatagramPacket receive = new DatagramPacket(data, data.length);
            while (runFlag) {
//                Log.d(TAG, "pzhao-> udp listen");
                socket.receive(receive);
                /*
                if (!hasInit) {
                    mHostPlay.getSlaveIp(receive.getAddress());
                    hasInit = true;
                    Message message = new Message();
                    message.what = 0;
                    mHostPlay.mHandler.sendMessage(message);
                    Log.d(TAG, "vaylbpzhao->get slave ip success " + receive.getAddress());
                }*/
                String result = new String(receive.getData(), 0, receive.getLength());
                mHostPlay.hostExecutor.execute(new AckUdp(mHostPlay,result, receive.getAddress(), receive
                        .getPort()));
                Log.d(TAG, "vaylbpzhao->receive udp " + UdpOrder.map.get(result));
                // 判断信令
                if (result.equals(UdpOrder.INIT)) {
                	
                	Log.d(TAG, "vaylb->INIT ");
                	mHostPlay.addSlaveIp(receive.getAddress());
                	Message message = new Message();
                    message.what = 13;
                    message.obj = receive.getAddress().getHostName();
                    mHostPlay.mHandler.sendMessage(message);
                }else if (result.equals(UdpOrder.START_PLAY)) {
                	//统计接收到的START_PLAY数目，从机全部可以播放时，通知各从机开始播放。
                	slave_ready_count++;
                	if(slave_ready_count==mHostPlay.slaveAddressMap.size()){
                		mHostPlay.commandCast(UdpOrder.START_RETURN);
//                		for(ConcurrentMap.Entry<String,InetAddress> e: mHostPlay.slaveAddressMap.entrySet() ){
//                			mHostPlay.hostExecutor.execute(new SendUdp(UdpOrder.START_RETURN, e.getValue()));
//                			//Thread.sleep(200);
//                		}
                		slave_ready_count = 0;
                		mHostPlay.slave_init_stat = true; //置为true后可通过spinner进行模式调节
                	}
                    //mHostPlay.hostExecutor.execute(new SendUdp(UdpOrder.START_RETURN,mHostPlay.slaveAddress));
                    long time_java = System.currentTimeMillis();
                    Log.d(TAG, "vaylbpzhao->receive start time:" + time_java);
                    HostPlay.native_setplayflag(time_java);
                    //mHostPlay.startgetSlaveWrite();
                    //mHostPlay.getSlaveWrite();
                    mHostPlay.nativeStartPlay = true;
                    Log.e(TAG, "vaylb_time-->Audio playback time:"+System.currentTimeMillis());
                }
                else if (result.equals(UdpOrder.SLVAE_CALL_COME)) {
                    Message message = new Message();
                    message.what = 1;
                    mHostPlay.mHandler.sendMessage(message);
                    Log.d(TAG, "receive slave call coming");
                    preVolume = mHostPlay.mAudioManager
                            .getStreamVolume(AudioManager.STREAM_MUSIC);
                    mHostPlay.mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
                }
                else if (result.equals(UdpOrder.SLAVE_CALL_GO)) {
                    Log.d(TAG, "receive slave call going");
                    if (mHostPlay.nativeStartPlay) {
                        Message message = new Message();
                        message.what = 2;
                        mHostPlay.mHandler.sendMessage(message);
                        mHostPlay.mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
                        mHostPlay.mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                                preVolume,
                                AudioManager.FLAG_SHOW_UI);
                    }

                }
                else if (result.equals(UdpOrder.SLAVE_EXIT)) {
//                    Message message = new Message();
//                    message.what = 3;
//                    mHostPlay.mHandler.sendMessage(message);
                }
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        } finally {
            if (socket != null)
                socket.close();
        }

    }

}
