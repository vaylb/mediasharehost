package com.zhao.host;

import java.util.HashMap;

public class UdpOrder {
    public static final String STANDBY_FALSE = "a"; // 定义命令字
    public static final String STANDBY_TRUE = "b";
    public static final String HOST_CALL_COME = "c";
    public static final String HOST_CALL_GO = "d";
    public static final String HOST_EXIT = "e";
    public static final String SLEEP = "f";
    public static final String WAKE_UP = "g";
    public static final String START_PLAY = "h";
    public static final String SLVAE_CALL_COME = "i";
    public static final String SLAVE_CALL_GO = "j";
    public static final String SCREEN_ON = "k";
    public static final String SCREEN_OFF = "l";
    public static final String SLAVE_EXIT="m";
    public static final String START_RETURN="n";
    public static final String GET_WRITED="o";
    public static final String INIT="p";
    public static final String SLAVE_GET_WRITE="q";
    public static final String GET_FRAME_COUNT="r";
    public static final String SPLIT_PLAY_FALSE="s";
    public static final String SPLIT_PLAY_TRUE="t";
    public static final String VIDEO_PREPARE="u";
	
	public static final String MODE_SYNC="s_t"; //同步播放模式
    public static final String MODE_REVERB="t_t"; //轻度混响
    public static final String MODE_KARA="u_t"; //卡拉OK
    public static final String AUDIO_STOP="v";
    public static final String VIDEO_STOP="w";
    public static final String VIDEO_START="x";
    public static HashMap<String, String> map=new HashMap<String, String>();
    static{
        map.put("a", "STANDBY_FALSE");
        map.put("b", "STANDBY_TRUE");
        map.put("c", "HOST_CALL_COME");
        map.put("d", "HOST_CALL_GO");
        map.put("e", "HOST_EXIT");
        map.put("f", "SLEEP");
        map.put("g", "WAKE_UP");
        map.put("h", "START_PLAY");
        map.put("i", "SLVAE_CALL_COME");
        map.put("j", "SLAVE_CALL_GO");
        map.put("k", "SCREEN_ON");
        map.put("l", "SCREEN_OFF");
        map.put("m", "SLAVE_EXIT");
        map.put("n", "START_RETURN");
        map.put("o", "GET_WRITED");
        map.put("p", "INIT");
        map.put("q", "SLAVE_GET_WRITE");
        map.put("r", "GET_FRAME_COUNT");
        map.put("s", "SPLIT_PLAY_FALSE");
        map.put("t", "SPLIT_PLAY_TRUE");
		map.put("s_t", "MODE_SYNC");
        map.put("t_t", "MODE_REVERB");
        map.put("u_t", "MODE_KARA");
        map.put("u", "VIDEO_PREPARE");
        map.put("v", "AUDIO_STOP");
        map.put("w", "VIDEO_STOP");
        map.put("x", "VIDEO_START");
    }
}
