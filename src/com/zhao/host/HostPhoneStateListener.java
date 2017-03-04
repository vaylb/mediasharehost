
package com.zhao.host;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class HostPhoneStateListener extends PhoneStateListener {
    private static final String TAG = "HostPhoneStateListener";
    public HostPlay mHostPlay;

    public HostPhoneStateListener(Object object) {
        this.mHostPlay = (HostPlay) object;
    }

    public void onCallStateChanged(int state, String incomingNumber)
    {
        switch (state)
        {
            case TelephonyManager.CALL_STATE_IDLE:
                // new Thread(new HostUdpThread(mHostPlay.slaveIp,
                // UdpOrder.HOST_CALL_GO)).start();
                if (mHostPlay.mReceiveUdp.hasInit)
                	mHostPlay.commandCast(UdpOrder.HOST_CALL_GO);
                    //new Thread(new SendUdp(UdpOrder.HOST_CALL_GO, mHostPlay.slaveAddress)).start();
                Log.d(TAG, "host no call");
                break;

            case TelephonyManager.CALL_STATE_RINGING:
                // new Thread( new HostUdpThread(mHostPlay.slaveIp,
                // UdpOrder.HOST_CALL_COME)).start();
                if (mHostPlay.mReceiveUdp.hasInit)
                	mHostPlay.commandCast(UdpOrder.HOST_CALL_COME);
                    //new Thread(new SendUdp(UdpOrder.HOST_CALL_COME, mHostPlay.slaveAddress)).start();
                Log.d(TAG, "host ring");
                break;

            case TelephonyManager.CALL_STATE_OFFHOOK:
                Log.d(TAG, "host off hook");
                break;
        }
    }

}
