package com.example.ussoi;

import static com.example.ussoi.save_input_field.KEY_BT_SWITCH;
import static com.example.ussoi.save_input_field.KEY_HTTP;
import static com.example.ussoi.save_input_field.KEY_WEBSOCKET;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class services_handel extends Service {

    save_input_field preferenceHelper;
    private  SharedPreferences in_prefs;

    private boolean bt_status ;

    private usb_handel usb_Handel_obj;
    private  bt_handel bt_handel_obj;
    public static boolean isRunning = false;


    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize any background threads or handlers here
        // Access the same preferences singleton
        isRunning = true;
        preferenceHelper = save_input_field.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        in_prefs = preferenceHelper.get_shared_pref();

        bt_status = in_prefs.getBoolean(KEY_BT_SWITCH,false);

        // 1) Build a notification
        Notification notification = createNotification(); // build with NotificationCompat

        // 2) Move service to foreground
        startForeground(1, notification);
        // This is called whenever service is started


        bt_status =  in_prefs.getBoolean(KEY_BT_SWITCH, false);
        if (bt_status) {
            String btAddr = intent.getStringExtra("bt_address");
            if (btAddr != null) {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                BluetoothDevice device = adapter.getRemoteDevice(btAddr);
                bt_handel_obj = new bt_handel(this, device);   // <-- new constructor
            }
        }



        runServicesBasedOnPrefs();
        // If killed, restart automatically
        return START_STICKY;
    }

    private Notification createNotification() {
        String channelId = "service_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Background Service",
                    NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(channel);
        }

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("USSOI Running")
                .setContentText("Service is active")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // or R.mipmap.ic_launcher
                .build();
    }


    private void runServicesBasedOnPrefs() {

        if ( !bt_status){
            usb_Handel_obj = new usb_handel(this);
            usb_Handel_obj.usb_setup(in_prefs);
        }
        else {
            bt_handel_obj.bt_service_start(in_prefs);

        }
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if ( !bt_status){
            usb_Handel_obj.stopAllServices();
        }
        else {
            bt_handel_obj.stopAllServices();
        }

    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
}
