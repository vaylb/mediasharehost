
package com.zhao.audiohost;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.SurfaceHolder.Callback;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.vaylb.switchbutton.SwitchButton;
import com.zhao.host.HostPlay;
import com.zhao.host.UdpOrder;
import com.zhao.host.WifiLayoutId;

import java.lang.ref.WeakReference;

/**
 * /**
 * 
 * @author 赵鹏
 * @version 创建时间：2014年9月13日 下午8:47:41 说明 界面
 */
public class MainActivity extends Activity {
    private static final String TAG="MainActivity";
    private ImageButton btnWifi;
    private ImageButton btnInit;
    private ImageButton btnStart;
    private ImageButton btnStop;
    public static SwitchButton mSwitchButonAudio;
    public static SwitchButton mSwitchButonVideo;
    public static SwitchButton mSwitchButon_online;
    public static SwitchButton mSwitchButon_screen;
    
    private TextView delay_tv;
    private SeekBar delay_seekBar;
    private double width, fDensity;
    private String delay_number;
    private DisplayMetrics displaysMetrics;
    
    public HostPlay mhp;
    public Handler mHandler;
    public boolean firstInit;
    private long exitTime = 0;

    public int wifi_config_dialog, wifiname, wifiPassword, wifiCheckBox;
    public WifiLayoutId mLayoutId;
    int r;

    private Spinner mSpinner; // 下拉选择框
    private ArrayAdapter<CharSequence> mSpinner_adapter; // 适配器
    private String[] play_mode_array;
    private boolean slave_ip_set_flag = false;
    private boolean video_online_surface_flag = true;
    
  //vaylb
    private SurfaceView surfaceview;
    private SurfaceHolder surfaceHolder;
    
    static {
        System.loadLibrary("media_host");
    }
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.host_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        mHandler = new MyHandler(this);
        wifi_config_dialog = getResources().getIdentifier(
                "wifi_config_dialog", "layout", getPackageName());
        wifiname = getResources().getIdentifier("wifiName", "id",
                getPackageName());

        wifiPassword = getResources().getIdentifier("wifiDialogPsw",
                "id", getPackageName());
        wifiCheckBox = getResources().getIdentifier("checkBox1", "id",
                getPackageName());

        mLayoutId = new WifiLayoutId(wifi_config_dialog, wifiname,
                wifiPassword, wifiCheckBox);
        
        init_surfaceview();
        

        mhp = new HostPlay(MainActivity.this, mLayoutId, mHandler,surfaceHolder.getSurface());
        mhp.registerReceiver();

        // setOnTouchListener
        View.OnTouchListener ImageButtonTouchListener = new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // TODO Auto-generated method stub
                switch (v.getId()) {
                    case R.id.wifi:
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            btnWifi.setBackgroundResource(R.drawable.wifi_press);
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {
                            btnWifi.setBackgroundResource(R.drawable.wifi);
                            mhp.openWifi();
                        }
                        break;
                    case R.id.init:
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            btnInit.setBackgroundResource(R.drawable.init_press);
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {
                            btnInit.setBackgroundResource(R.drawable.init);
                            //vaylb: replaced for init video_playback  
                            //mhp.setBuffer(); //16-03-24 move from onCreate to here
                            mhp.init();
                        }
                        break;
                    case R.id.start:
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            btnStart.setBackgroundResource(R.drawable.play_press);
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {
                            btnStart.setBackgroundResource(R.drawable.play);
                            mhp.start();
                        }
                        break;
                    case R.id.stop:
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            btnStop.setBackgroundResource(R.drawable.stop_press);
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {
                            btnStop.setBackgroundResource(R.drawable.stop);
                            mhp.quit();
                        }
                        break;

                }
                return false;
            }
        };

        btnWifi = (ImageButton) findViewById(R.id.wifi);
        btnWifi.setOnTouchListener(ImageButtonTouchListener);
        btnWifi.setEnabled(true);
        btnInit = (ImageButton) findViewById(R.id.init);
        btnInit.setOnTouchListener(ImageButtonTouchListener);
        btnStart = (ImageButton) findViewById(R.id.start);
        btnStart.setOnTouchListener(ImageButtonTouchListener);
        btnStop = (ImageButton) findViewById(R.id.stop);
        btnStop.setOnTouchListener(ImageButtonTouchListener);
        
        
        mSwitchButonAudio = (SwitchButton)findViewById(R.id.audio_switch);
        mSwitchButonAudio.setOnCheckedChangeListener(switchListener);
        
        mSwitchButonVideo = (SwitchButton)findViewById(R.id.video_switch);
        mSwitchButonVideo.setOnCheckedChangeListener(switchListener);
        
        mSwitchButon_online = (SwitchButton)findViewById(R.id.online_switch);
        mSwitchButon_online.setOnCheckedChangeListener(switchListener);
        
        mSwitchButon_screen = (SwitchButton)findViewById(R.id.split_switch);
        mSwitchButon_screen.setOnCheckedChangeListener(switchListener);
        
        //seekBar
        //initSeekBarProgress();
        
        // 下拉选择框
        play_mode_array = getResources().getStringArray(R.array.play_mode);
        mSpinner = (Spinner) findViewById(R.id.spinner);
        mSpinner_adapter = ArrayAdapter.createFromResource(this, R.array.play_mode,
                android.R.layout.simple_spinner_item);
        mSpinner_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner.setAdapter(mSpinner_adapter);
        mSpinner.setSelection(0);
        mSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                // TODO Auto-generated method stub
                switch (position) {
                    case 0:
                        Toast.makeText(getApplicationContext(),
                                "以" + play_mode_array[0] + "模式进行播放", Toast.LENGTH_SHORT).show();
                        if (mhp != null && mhp.hasInit)
                        	mhp.commandCast(UdpOrder.MODE_SYNC);
//                            mhp.defaultMode();
                        break;
                    case 1:
                        Toast.makeText(getApplicationContext(),
                                "以" + play_mode_array[1] + "模式进行播放", Toast.LENGTH_SHORT).show();
                        if (mhp != null && mhp.hasInit)
                        	mhp.commandCast(UdpOrder.MODE_REVERB);
//                            mhp.delay50ms();
                        break;
                    case 2:
                        Toast.makeText(getApplicationContext(),
                                "以" + play_mode_array[2] + "模式进行播放", Toast.LENGTH_SHORT).show();
                        if (mhp != null && mhp.hasInit)
                        	mhp.commandCast(UdpOrder.MODE_KARA);
//                            mhp.delay100ms();
                        break;

                    default:
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // TODO Auto-generated method stub
            }
        });
        
       
    }
    
    ////vaylb
    private void init_surfaceview(){
        surfaceview = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceview.setZOrderOnTop(true);//设置画布  背景透明
        
        surfaceHolder = surfaceview.getHolder();
        surfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
        surfaceHolder.addCallback(new Callback(){  
  
            @Override  
            public void surfaceCreated(SurfaceHolder holder) {  
                Log.d(TAG,"vaylb-->surfaceCreated");    
            }  
  
            @Override  
            public void surfaceChanged(SurfaceHolder holder, int format,  
                    int width, int height) {  
                  
            }  
  
            @Override  
            public void surfaceDestroyed(SurfaceHolder holder) {  
                  
            }
        });
    }
    
    private void initSeekBarProgress(){
        displaysMetrics = getResources().getDisplayMetrics();
        width = displaysMetrics.widthPixels;
        fDensity = (width - Utils.dip2px(this, 51)) / 100;
        delay_seekBar=(SeekBar)findViewById(R.id.seekBar1);
        delay_tv=(TextView)findViewById(R.id.num_tv);
        
        delay_seekBar.setProgress(20);
        delay_seekBar.setOnSeekBarChangeListener(mSeekChange);
        LinearLayout.LayoutParams paramsStrength = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        delay_number = 0 + "";
        paramsStrength.leftMargin = (int) (((20*100)/40) * fDensity);
        delay_tv.setLayoutParams(paramsStrength);
        delay_tv.setText(delay_number + " ms");
    }
    private OnSeekBarChangeListener mSeekChange = new OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                boolean fromUser) {
            if(mhp!=null){
                int delay=17+progress-20;
                mhp.setSlaveHost(delay);
               
            }
            delay_number = (progress-20)*5 + "";
            LinearLayout.LayoutParams paramsStrength = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            paramsStrength.leftMargin = (int) ((progress*100/40) * fDensity);
            delay_tv.setLayoutParams(paramsStrength);
            delay_tv.setText(delay_number + " ms");

        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // TODO Auto-generated method stub

        }
    };
    public void onBackPressed() {
        if ((System.currentTimeMillis() - exitTime) > 3000) {
            Toast.makeText(getApplicationContext(), "再按一次退出",
                    Toast.LENGTH_SHORT).show();
            exitTime = System.currentTimeMillis();
        } else {
            Message msg = new Message();
            msg.what = 88;
            mHandler.sendMessage(msg);
        }
    }

    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();
            if (msg.what == 0) {
                Toast.makeText(activity, "初始化完成",
                        Toast.LENGTH_SHORT).show();
            }
            else if (msg.what == 1) {
                Toast.makeText(activity, "从机来电，设置静音",
                        Toast.LENGTH_SHORT).show();
            }
            else if (msg.what == 2) {

                Toast.makeText(activity, "从机通话完成，音量恢复",
                        Toast.LENGTH_SHORT).show();
            }
            else if (msg.what == 3) {
                Toast.makeText(activity, "从机已退出，主机恢复单独播放..",
                        Toast.LENGTH_SHORT).show();
                activity.mhp.nativeStartPlay = false;
                activity.mhp.getWriteUdp.stop();
                activity.mhp.native_setstartflag(0);
//                activity.mhp.native_setvideohook(0);
                mSwitchButonVideo.setChecked(false);

            } else if (msg.what == 5) {
                Toast.makeText(activity, "抱歉，主机出现错误",
                        Toast.LENGTH_SHORT).show();
            } else if (msg.what == 6) {
                Toast.makeText(activity, "从机出现错误，主机恢复单独播放",
                        Toast.LENGTH_SHORT).show();
                activity.mhp.nativeStartPlay = false;
                activity.mhp.getWriteUdp.stop();
                activity.mhp.native_setstartflag(0);
            } else if (msg.what == 7) {
                Toast.makeText(activity, "从机已经退出，恢复单独播放",
                        Toast.LENGTH_SHORT).show();
                activity.mhp.nativeStartPlay = false;
                activity.mhp.getWriteUdp.stop();
                activity.mhp.native_setstartflag(0);
            }
            else if (msg.what == 8) {
                Toast.makeText(activity, "Wifi热点被关闭，恢复单独播放",
                        Toast.LENGTH_SHORT).show();
                activity.mhp.nativeStartPlay = false;
                activity.mhp.getWriteUdp.stop();
                activity.mhp.native_setstartflag(0);
            }
            else if (msg.what == 4) {
                Toast.makeText(activity, "正在退出..",
                        Toast.LENGTH_SHORT).show();
                new Thread() {

                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        super.run();
                        try {
                            sleep(500);
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        Message msg = new Message();
                        msg.what = 88;
                        sendMessage(msg);
                    }

                }.start();
            }
            else if(msg.what == 9){
            	Toast.makeText(activity, "无法连接至网关服务器", Toast.LENGTH_SHORT).show();
            }
            else if (msg.what == 13) {
            	Toast.makeText(activity, "从机"+msg.obj+"已加入",
                        Toast.LENGTH_SHORT).show();
            }
            if (msg.what == 88) {
//                activity.mhp.exitingState = true;
//                activity.mhp.exit();
            	activity.mhp.commandCast(UdpOrder.HOST_EXIT);
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
            }
            
            if (msg.what == 10) {
            	mSwitchButonAudio.setChecked(false);
            }else if (msg.what == 11) {
                activity.mhp.exit();
            }else if(msg.what == 12){
            	Toast.makeText(activity, "视频传输网络出现问题，恢复主机单独播放", Toast.LENGTH_SHORT).show();
            	activity.mhp.native_setvideohook(0);
            }

        }

    }
    
	OnCheckedChangeListener switchListener = new OnCheckedChangeListener() {
		
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			// TODO Auto-generated method stub
			Log.e("MainActivity", "vaylb_test--> switchbutton state changed");
			switch (buttonView.getId()) {
			
			case R.id.audio_switch:
				if (isChecked) {
					mhp.setBuffer(); //16-03-24 move from initbutton to here
					mhp.start();
				}else if (!isChecked) {
					mhp.quit();
				}
				break;
				
			case R.id.video_switch:
				if (isChecked) {
					if(!slave_ip_set_flag)
					{
						new Thread() {
							@Override
							public void run() {
								super.run();
								try {
									sleep(1500);
									mhp.commandCast(UdpOrder.VIDEO_PREPARE);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
						}.start();
						mhp.native_setslavenum(mhp.slaveAddressMap.size());
						slave_ip_set_flag = true;
					}
					mhp.native_setvideohook(1);
					mhp.commandCast(UdpOrder.VIDEO_START);
				}else if (!isChecked) {
					mhp.native_setvideohook(0);
					mhp.commandCast(UdpOrder.VIDEO_STOP);
				}
				break;
				
			case R.id.online_switch:
				if (isChecked) {
					//native
					if(!video_online_surface_flag){
						mhp.native_videoonline_setVideoSurface(surfaceHolder.getSurface());
						video_online_surface_flag = true;
					}
					String local_ip = getLocalIpAddr();
					mhp.native_videoonline_init(local_ip,getGatewayIp(local_ip));
					
					/** java
					String local_ip = getLocalIpAddr();
					mhp.start_mediaonline(getGatewayIp(local_ip));
					*/
				}else if (!isChecked) {
					//native
					mhp.native_videoonline_exit();
					
					/**java
					mhp.stop_mediaonline();
					*/
				}
				break;
				
			case R.id.split_switch:
				if(mSwitchButonVideo.isChecked()){
					mhp.split_play(isChecked);
					if(isChecked) mhp.native_setscreensplit(1);
					else mhp.native_setscreensplit(0);
				}
				break;

			default:
				break;
			}
		}
	};
	
	 /**
     * vaylb
     * get local IP 
     */
    String getLocalIpAddr(){
    	//获取wifi服务  
        WifiManager wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);  
        //判断wifi是否开启  
        if (!wifiManager.isWifiEnabled()) {  
        wifiManager.setWifiEnabled(true);    
        }  
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();       
        int ipAddress = wifiInfo.getIpAddress();   
        return intToIp(ipAddress); 
    }
    
    String getGatewayIp(String local_ip){
    	return local_ip.substring(0, local_ip.lastIndexOf("."))+".1";
    }
    
    private String intToIp(int i) {       
        
        return (i & 0xFF ) + "." + ((i >> 8 ) & 0xFF) + "." + ((i >> 16 ) & 0xFF) + "." + ( i >> 24 & 0xFF) ;  
    }

}
