
package com.zhao.host;

import android.R.bool;
import android.R.integer;
import android.R.string;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;

/**
 * @author 赵鹏
 * @version 创建时间：2014年9月13日 下午9:27:48 说明 主机类
 */
public class HostPlay {
    private static final String TAG = "HostPlay";
    public Context mContext;
    public Handler mHandler;
    public ByteBuffer buffer;
    public volatile boolean native_checkexitFlag;
    public volatile boolean wifiFlag;
    public volatile boolean tcpFlag;
    public volatile boolean udpFlag;
    public volatile boolean standbyFlag;
    public volatile boolean hostPlay;
    public volatile boolean startPlay;
    public volatile boolean startFlag;
    public volatile boolean nativeStartPlay;
    public volatile boolean getSlaveWriting;
    public boolean hasInit;
    public volatile boolean hasGetSlaveip;
    public static final String PREFS_NAME = "prefsname"; // 偏好设置名称
    public static final String REMEMBER_USERID_KEY = "remember"; // 记住用户名
    public static final String USERID_KEY = "userid"; // 用户名标记
    public static final String DEFAULT_USERNAME = "audio"; // 默认用户名
    public static final String USERID_PSW = "usepsw";
    public static final String DEFAULT_USERPSW = "12345678"; // 默认密码
    public SharedPreferences mSettings = null;
    private String wifiName;
    private String wifiPsw;
    private WifiManager mWifiManager;
    public String slaveIp = null;
    public InetAddress slaveip = null;
    public String hostIpString;
    public AudioTrack mAudioTrack;
    public AudioManager mAudioManager;
    public HostPhoneStateListener mHostPhoneStateListener;
    private BroadcastReceiver mWifiApReceiver;
    public TelephonyManager mTelManager;
    public ConnectivityManager connmanager;
    public BroadcastReceiver mConnectivityReceiver;
    public BroadcastReceiver mScreenReceiver;
    public IntentFilter filter_screen;
    public IntentFilter filter_wifi;
    private IntentFilter mWifiApFilter;
    public WifiLayoutId mWifiLayoutId;
    public static int mFrameCount;
    public static final int DEFAULTFRAME = 240;
    public static final int DEFAULTCOUNT = 32;
    public static volatile int slave_host = 17;
    public static volatile int check_begin = 20;
    public static volatile int check_end = 15;
    public ExecutorService hostExecutor = Executors.newCachedThreadPool();
    private HostTCPThread tcpThread;
    public volatile InetAddress slaveAddress;
    public ReceiveUdp mReceiveUdp;
    public GetWriteUdp getWriteUdp = null;
    public ConcurrentHashMap<String,InetAddress> slaveAddressMap = new ConcurrentHashMap<String, InetAddress>();
    public volatile boolean slave_init_stat = false;  //标示所有slave是否执行初始化步骤，全部完成初始化后置true，在
    
    //vaylb videoonline
    public volatile boolean mediaOnline;
    private MediaOnlinePlayback mediaOnlineThread;
    public ByteBuffer online_pkt_in,online_pkt_out,online_csd;
    private int online_video_width = 0,online_video_height = 0;
    private MediaFormat online_video_format;
    private MediaCodec online_video_decoder;
    private static final int default_online_pkt_in_size = 512*1024;//512k
    private Condition pkt_in_condition;
    private Surface video_online_surface;
    
    //vaylb flags
    private boolean teamshare_audio_init = false;
    private boolean teamshare_audio_native_buffer_setupflag = false;
    

    public HostPlay(Context context, WifiLayoutId mId, Handler mHandler, Surface surface) {
        this.mContext = context;
        this.mHandler = mHandler;
        this.mWifiLayoutId = mId;
        this.mediaOnline = true;
        this.video_online_surface = surface;
    }
    

    // native函数

    public native int native_setup(int receivebuffer, int sendbuffer);

    public native void native_setstartflag(int flag);

    public native boolean native_checkstandbyflag();

    public static native boolean native_checkreadpos();

    public static native boolean native_checkexitflagI();

    public static native boolean native_checkexitflagII();

    public static native void native_setreadpos(int pos);

    public static native void native_setplayflag(long time_java);

    public native void native_setbuffertemp(ByteBuffer buffer);

    public native void native_exit();

    public native int native_haswrite();

    public native void native_read_ahead(int readahead);

    public native boolean native_needcheckwrited();

    public native void native_signaleToWrite();
    
    public native void native_setvideohook(int flag);
    public native void native_setslaveip(String ip);
    public native void native_setslavenum(int num);
    
    public native void native_videoonline_init(String local_ip,String gateway_ip);
    public native void native_videoonline_exit();
    public native boolean native_videoonline_setVideoSurface(Surface surface);
    public native boolean native_videoonline_getcsd(ByteBuffer csd, int size);
    public native void native_setscreensplit(int flag);
    public native boolean native_videoonline_getPktInStat();
    public native void native_videoonline_setPktInStat(boolean stat);
    public native void native_videoonline_setVideoBuffer(ByteBuffer buf_in,ByteBuffer buf_out);
    
    // 设置底层Buffer
    public void setBuffer() {
    	if(!teamshare_audio_native_buffer_setupflag){
	        mFrameCount = native_setup(0, 0); // 240 or 960
	        if (mFrameCount <= 0)
	            mFrameCount = DEFAULTFRAME;
	        buffer = ByteBuffer.allocateDirect(mFrameCount * DEFAULTCOUNT);
	        native_setbuffertemp(buffer);
	        teamshare_audio_init = true;
//	        teamshare_audio_native_buffer_setupflag = true;
    	}
    }

    // ------------------------------------------------
    // 打开WIFI
    // ------------------------------------------------

    public int openWifi() {
        if (isWifiApEnabled()) {
            return 0;
        }
        Log.d(TAG, "open wifihot");
        wifiFlag = !wifiFlag;
        if (wifiFlag) {
            mSettings = mContext.getSharedPreferences("prefname",
                    Context.MODE_PRIVATE);
            LayoutInflater factory = LayoutInflater.from(mContext);
            final View textEntryView = factory.inflate(
                    mWifiLayoutId.wifiLayout, null);
            final EditText userName = (EditText) textEntryView
                    .findViewById(mWifiLayoutId.wifiName);
            final EditText passWord = (EditText) textEntryView
                    .findViewById(mWifiLayoutId.wifiDialogPsw);
            final CheckBox cb = (CheckBox) textEntryView
                    .findViewById(mWifiLayoutId.checkBox1);
            cb.setChecked(getRemember()); // 勾选记住用户名
            userName.setText(getUserName()); // 设置用户名
            passWord.setText(getUserPsw());

            new AlertDialog.Builder(mContext)
                    .setTitle("请设置WIFI热点名及密码：")
                    .setView(textEntryView)
                    .setPositiveButton("确定",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {
                                    if (cb.isChecked()) {
                                        saveRemember(true);
                                        saveUserName(userName.getText()
                                                .toString());
                                        saveUserKey(passWord.getText()
                                                .toString());

                                    } else {
                                        saveRemember(false);
                                        saveUserName("");
                                        saveUserKey("");
                                    }
                                    wifiName = userName.getText().toString()
                                            .trim();

                                    wifiPsw = passWord.getText().toString()
                                            .trim();

                                    if (wifiName != null && wifiPsw != null) {
                                        setWifiApEnabled(wifiFlag, wifiName,
                                                wifiPsw);
                                    }

                                }

                            })
                    .setNegativeButton("取消",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {

                                }

                            }).show();
            return 0;

        } else {
            setWifiApEnabled(wifiFlag, wifiName, wifiPsw);
            return 0;

        }

    }

    // ---------------------------------------------------------------------
    // 保存用户名密码相关函数
    // -------------------------------------------------------------------

    // 保存用户名
    public void saveUserName(String userid) {
        Editor editor = mSettings.edit();// 获取编辑器
        editor.putString(USERID_KEY, userid);
        editor.commit(); // 保存数据

    }

    // 设置保存密码
    public void saveUserKey(String key) {
        Editor editor = mSettings.edit();// 获取编辑器
        editor.putString(USERID_PSW, key);
        editor.commit(); // 保存数据
    }

    // 设置是否保存的用户名
    public void saveRemember(boolean remember) {
        Editor editor = mSettings.edit();// 获取编辑器
        editor.putBoolean(REMEMBER_USERID_KEY, remember);
        editor.commit();
    }

    // 获取保存的用户名
    public String getUserName() {
        return mSettings.getString(USERID_KEY, DEFAULT_USERNAME);
    }

    // 获取保存的密码
    public String getUserPsw() {
        return mSettings.getString(USERID_PSW, DEFAULT_USERPSW);
    }

    // 获取是否保存的用户名
    public boolean getRemember() {
        return mSettings.getBoolean(REMEMBER_USERID_KEY, true);
    }

    // 开启wifi
    public Boolean setWifiApEnabled(Boolean enabled, String name, String psw) {
        if (enabled) {
            mWifiManager.setWifiEnabled(false);
        }
        try {
            WifiConfiguration netConfig = new WifiConfiguration();
            netConfig.SSID = name;
            netConfig.preSharedKey = psw;
            netConfig.allowedAuthAlgorithms
                    .set(WifiConfiguration.AuthAlgorithm.OPEN);
            netConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            netConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            netConfig.allowedKeyManagement
                    .set(WifiConfiguration.KeyMgmt.WPA_PSK);
            netConfig.allowedPairwiseCiphers
                    .set(WifiConfiguration.PairwiseCipher.CCMP);
            netConfig.allowedPairwiseCiphers
                    .set(WifiConfiguration.PairwiseCipher.TKIP);
            netConfig.allowedGroupCiphers
                    .set(WifiConfiguration.GroupCipher.CCMP);
            netConfig.allowedGroupCiphers
                    .set(WifiConfiguration.GroupCipher.TKIP);
            Method method = mWifiManager.getClass().getMethod(
                    "setWifiApEnabled", WifiConfiguration.class, Boolean.TYPE);
            return (Boolean) method.invoke(mWifiManager, netConfig, enabled);
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
            return false;
        }

    }

    //开启信令监听线程
    public void startListenUp() {
        mReceiveUdp = new ReceiveUdp(this);
        hostExecutor.execute(mReceiveUdp);
    }
    
    private void startAudioTcpThread(){
    	tcpThread = new HostTCPThread(this);
        hostExecutor.execute(tcpThread);
    }

    // ----------------------------------------------------
    // 初始化
    // --------------------------------------------------

    public int init() {
        if (!isWifiApEnabled()) {
            Toast.makeText(mContext,
                    "请打开Wifi热点！", Toast.LENGTH_SHORT).show();
            return -1;
        }

        if (!hasInit) {
        	hasInit = true;
            Log.d(TAG, "vaylb-->start to init");
            startListenUp();
            
            //vaylb:listening for wifi state
            mTelManager = (TelephonyManager) mContext
                    .getSystemService(Context.TELEPHONY_SERVICE);
            mHostPhoneStateListener = new HostPhoneStateListener(this);
            mTelManager.listen(mHostPhoneStateListener,
                    PhoneStateListener.LISTEN_CALL_STATE);
            // wifi ap
            mWifiApFilter = new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED");
            mWifiApReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if ("android.net.wifi.WIFI_AP_STATE_CHANGED".equals(action)) {
                        // get Wi-Fi Hotspot state here
                        int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
                        if (WifiManager.WIFI_STATE_ENABLED == state % 10) {
                            // Wifi is enabled
                            Log.d(TAG, "pzhao->wifi ap enabled");
                        }
                        if (WifiManager.WIFI_STATE_DISABLED == state % 10) {
                            Log.d(TAG, "pzhao->wifi ap disabled");
                            Message msg = new Message();
                            msg.what = 8;
                            mHandler.sendMessage(msg);
                        }

                    }
                }
            };
            mContext.registerReceiver(mWifiApReceiver, mWifiApFilter);
        }
        return 0;
    }

    public void getSlaveIp(InetAddress slaveAddr) {
        hasGetSlaveip = true;
        this.slaveAddress = slaveAddr;
        //vaylb:
        //getWriteUdp = new GetWriteUdp(slaveAddress, this);
        //hostExecutor.execute(getWriteUdp);
    }
    
    public void addSlaveIp(InetAddress slaveAddr) {
        hasGetSlaveip = true;
        this.slaveAddress = slaveAddr;
    	if(!slaveAddressMap.containsKey(slaveAddr.getHostAddress())){
    		this.slaveAddressMap.put(slaveAddr.getHostAddress(), slaveAddr);
    		Log.d(TAG, "vaylb-->addSlaveIp:"+slaveAddr.getHostAddress()+", total:"+this.slaveAddressMap.size());
    	}
    }

    public int start() {
        if (!hasGetSlaveip) {
            Toast.makeText(mContext, "初始化未完成",
                    Toast.LENGTH_SHORT).show();
            Log.d(TAG, "slaveIp null");
            Message msg = new Message();
            msg.what = 10;
            mHandler.sendMessage(msg);
        } else {
            if (tcpThread == null) {
            	startAudioTcpThread();
            }
            commandCast(UdpOrder.STANDBY_FALSE);
            // make sure fromJni can call by native
            fromJni(7);
            native_setstartflag(1);
            Log.d(TAG, "vaylb->start");
        }
        return 0;
    }

    public int stop() {
        native_setstartflag(0);
        return 0;

    }
    
    
    //vaylb added for video split play
    public int split_play(boolean split){
    	if(split) hostExecutor.execute(new SendUdp(UdpOrder.SPLIT_PLAY_TRUE, slaveAddress));
    	else hostExecutor.execute(new SendUdp(UdpOrder.SPLIT_PLAY_FALSE, slaveAddress));
    	return 0;
    }

    /*
     * called by native HostProcessThread
     */
    private void fromJni(int i) {
        switch (i) {
            case 1:
            	commandCast(UdpOrder.STANDBY_FALSE);
                tcpThread.start();
                break;
            case 2:
                nativeStartPlay = false;
                tcpThread.stop();
                commandCast(UdpOrder.STANDBY_TRUE);
                break;
            case 3:
                tcpThread.signalToRead();
                break;
            case 4:
                Message msg = new Message();
                msg.what = 3;
                mHandler.sendMessage(msg);
                break;
            case 5:
                Message msg2 = new Message();
                msg2.what = 3;
                mHandler.sendMessage(msg2);
                break;
            default:
                break;
        }

    }
    
    public void commandCast(String command){
    	Log.e(TAG, "vaylb--> commandCast:"+UdpOrder.map.get(command));
    	for(ConcurrentMap.Entry<String,InetAddress> e: slaveAddressMap.entrySet() ){
			hostExecutor.execute(new SendUdp(command, e.getValue()));
		}
    }
    
    public void startgetSlaveWrite() {
        //getWriteUdp.start();
    }

    public void getSlaveWrite() {
        //getWriteUdp.setCount(10);
    }

    public void quit() {
        Log.d(TAG, "vaylb->host audio quit");
        if (hasGetSlaveip)commandCast(UdpOrder.AUDIO_STOP);
        if(teamshare_audio_init)
        {
        	native_setstartflag(0);
        	native_exit();
        }
        tcpThread.quit();
        tcpThread = null;
        
        
//        new Thread() {
//
//            @Override
//            public void run() {
//                super.run();
//                try {
//                    sleep(500);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                Message msg = new Message();
//                msg.what = 11;
//                mHandler.sendMessage(msg);
//            }
//        }.start();
    }

    public void exit() {
        Log.w(TAG, "vaylb->java_exit");       
        
        if (mConnectivityReceiver != null)
            mContext.unregisterReceiver(mConnectivityReceiver);
        if (mScreenReceiver != null)
            mContext.unregisterReceiver(mScreenReceiver);
        if (mWifiApReceiver != null)
            mContext.unregisterReceiver(mWifiApReceiver);
        if (mTelManager != null)
            mTelManager.listen(mHostPhoneStateListener, PhoneStateListener.LISTEN_NONE);
//        if (hasGetSlaveip)
//            hostExecutor.execute(new SendUdp(UdpOrder.HOST_EXIT, slaveAddress));

        if (hasInit) {
            tcpThread.quit();
            mReceiveUdp.stop();
            hostExecutor.shutdown();
            //getWriteUdp.quit();
            releaseWakeLock();
        }
    }

    public void defaultMode() {
        /*
         * slave_host = 17; check_begin = 20; check_end = 15;
         */
        slave_host = 27;
        check_begin = 30;
        check_end = 25;
        // hostExecutor.execute(getWriteforchangeMode);
        //getWriteUdp.setCount(10);
    }

    public void delay50ms() {
        /*
         * slave_host = 7; check_begin = 10; check_end = 5;
         */
        slave_host += 2;
        check_begin += 2;
        check_end += 2;
        // hostExecutor.execute(getWriteforchangeMode);
        //getWriteUdp.setCount(10);
    }

    public void delay100ms() {
        slave_host = -3 + 50;
        check_begin = 0 + 50;
        check_end = -5 + 50;
        // hostExecutor.execute(getWriteforchangeMode);
        //getWriteUdp.setCount(10);
    }

    public void add_slave_host() {
        slave_host += 2;
        check_begin += 2;
        check_end += 2;
        //getWriteUdp.setCount(10);
        Log.d(TAG, "pzhao->slave_host " + slave_host);
    }

    // add by 10/22
    public void quick_host() {
        slave_host -= 1;
        check_begin -= 1;
        check_end -= 1;

        //getWriteUdp.setCount(10);
        Log.d(TAG, "pzhao->slave_host " + slave_host);
    }

    public void quick_slave() {
        slave_host += 1;
        check_begin += 1;
        check_end += 1;
        //getWriteUdp.setCount(10);
        Log.d(TAG, "pzhao->slave_host " + slave_host);
    }

    public void setSlaveHost(int delay) {
        slave_host = delay;
        check_begin = slave_host + 4;
        check_end = slave_host - 4;
        if (getWriteUdp != null)
            getWriteUdp.setCount(10);
        Log.d(TAG, "pzhao->slave_host " + slave_host);
    }

    // 添加：aquireWakeLock() by hui.
    public WakeLock mwakelock;
    
    public boolean isWifiApEnabled() {
        boolean isEnabled = false;
        try {
            Method method = mWifiManager.getClass().getMethod("isWifiApEnabled");
            isEnabled = (Boolean) method.invoke(mWifiManager);
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return isEnabled;
    }

    public void registerReceiver() {
        filter_wifi = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        mConnectivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // TODO Auto-generated method stub
                connmanager = (ConnectivityManager) mContext
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo wifiInfo = connmanager
                        .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (wifiInfo == null || wifiInfo.isConnected() == false) {
                    Log.d(TAG, "pzhao->wifi lost");
                } else {
                    Log.d(TAG, "pzhao->wifi connect");
                }
            }
        };
        mContext.registerReceiver(mConnectivityReceiver, filter_wifi);

        filter_screen = new IntentFilter();
        filter_screen.addAction(Intent.ACTION_SCREEN_ON);
        filter_screen.addAction(Intent.ACTION_SCREEN_OFF);
        mScreenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                // TODO Auto-generated method stub
                String action = arg1.getAction();
                if (action.equals(Intent.ACTION_SCREEN_ON)) {
                    Log.d(TAG, "pzhao->screen_on");
                    if (hasGetSlaveip)
                        hostExecutor.execute(new SendUdp(UdpOrder.SCREEN_ON, slaveAddress));
                } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                    Log.d(TAG, "pzhao->screen_off");
                    if (hasGetSlaveip)
                        hostExecutor.execute(new SendUdp(UdpOrder.SCREEN_OFF, slaveAddress));
                }
            }

        };
        mContext.registerReceiver(mScreenReceiver, filter_screen);

        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mAudioManager = (AudioManager) mContext
                .getSystemService(Context.AUDIO_SERVICE);

        aquireWakeLock();
    }

    public void aquireWakeLock() {
        PowerManager pm = (PowerManager) mContext.
                getSystemService(Context.POWER_SERVICE);
        if (mwakelock == null) {
            mwakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        }
        mwakelock.acquire();

    }

    // 添加：releaseWakeLock().
    public void releaseWakeLock() {
        if (mwakelock != null) {
            mwakelock.release();
            mwakelock = null;
        }
    }
    
    //-------------------------------------------mediaonline------------------------------------------
    //called from native
    private void setupMediaFormat(int width,int height,int csd_size){
    	Log.e(TAG, "vaylb-->setupMediaFormat call from Native");
    	online_video_width = width;
    	online_video_height = height;
    	if(csd_size > 0){
    		online_csd = ByteBuffer.allocateDirect(csd_size);
    		native_videoonline_getcsd(online_csd, csd_size);
    		logAsHex(online_csd.array());
    	}
    	online_video_format = MediaFormat.createVideoFormat("video/avc", online_video_width, online_video_height);
    	online_video_format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, online_video_width*online_video_height);
    	online_video_format.setByteBuffer("csd-0",online_csd);
    	
    	online_pkt_in = ByteBuffer.allocateDirect(default_online_pkt_in_size);
    	online_pkt_out = ByteBuffer.allocateDirect(online_video_width*online_video_height*4);
    	native_videoonline_setVideoBuffer(online_pkt_in, online_pkt_out);
    	try {
    		online_video_decoder = MediaCodec.createDecoderByType("video/avc");
    		online_video_decoder.configure(online_video_format, video_online_surface, null, 0);
    		online_video_decoder.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    private void onNewVideoData(int size,long pts){
    	DecodeThread decode = new DecodeThread(this,size,pts);
    	hostExecutor.execute(decode);
    }
    
    private class DecodeThread implements Runnable{
    	int mVideoPacketSize;
    	long mVideoPacketPts;
    	HostPlay mhp;
    	public DecodeThread(HostPlay host,int size,long pts) {
    		mhp = host;
    		mVideoPacketSize = size;
    		mVideoPacketPts = pts;
		}

		@Override
		public void run() {
			//Log.v(TAG, "vaylb-->Java onNewVideoData, size = "+mVideoPacketSize);
			int index = online_video_decoder.dequeueInputBuffer(0);
			if(index >= 0){
				if(mhp.native_videoonline_getPktInStat()){ //must be true
					ByteBuffer bytebuffer = online_video_decoder.getInputBuffers()[index];
					bytebuffer.clear();
					bytebuffer.put(online_pkt_in.array(), 0, mVideoPacketSize);
					online_video_decoder.queueInputBuffer(index, 0, mVideoPacketSize, mVideoPacketPts, 0);
					mhp.native_videoonline_setPktInStat(false);
				}
			}
			
			MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
			
			int outputindex = online_video_decoder.dequeueOutputBuffer(info, 0);
    		while(outputindex >= 0){
    			Log.v(TAG, "vaylb-->Java getOutPut data, size = "+info.size);
    			online_video_decoder.releaseOutputBuffer(outputindex, true);
    			outputindex = online_video_decoder.dequeueOutputBuffer(info, 0);
    		}
		}
    }
    
    private class OutputThread extends Thread{
    	@Override
    	public void run() {
    		super.run();
    		MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    		while(true){
    			int index = online_video_decoder.dequeueOutputBuffer(info, 0);
    			if(index >= 0){
    				//Log.v(TAG, "vaylb-->Java getOutPut data, size = "+info.size);
    				ByteBuffer buffer = online_video_decoder.getOutputBuffers()[index];
    				buffer.position(info.offset);
    				buffer.limit(info.offset+info.size);
    				online_video_decoder.releaseOutputBuffer(index, false);
    			}else Log.v(TAG, "vaylb-->Java getOutPut no data, index = "+index);
    		}
    	}
    }
    
    public void start_mediaonline(String gateway_ip){
    	if(mediaOnlineThread == null)mediaOnlineThread = new MediaOnlinePlayback(this);
    	mediaOnlineThread.setGatewayIp(gateway_ip);
    	hostExecutor.execute(mediaOnlineThread);
    }
    
    public void stop_mediaonline(){
    	if(mediaOnlineThread != null) this.mediaOnline = false;
    }
    
    void logAsHex(byte[] data){
    	String output = "";
    	for(int i = 0;i<data.length;i++){
    		output += String.format("%02x", data[i])+" ";
    	}
    	Log.e(TAG, "vaylb-->CSD = "+output);
    }
}
