package com.example.ricardo.solartelemetry;

import android.content.Context;
import android.os.PowerManager;

public class WakeLocker {

    private static PowerManager.WakeLock wakeLock;

    public static void acquire(Context ctx){
        if(wakeLock!=null)wakeLock.release();

        PowerManager pm=(PowerManager)ctx.getSystemService(Context.POWER_SERVICE);
        wakeLock=pm.newWakeLock(PowerManager.FULL_WAKE_LOCK|
                PowerManager.ACQUIRE_CAUSES_WAKEUP,"tag");
        wakeLock.acquire();
    }

    public static void release(){
        if(wakeLock!=null)
            wakeLock.release();
        wakeLock=null;
    }

}
