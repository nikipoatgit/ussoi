package com.example.ussoi;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Switch;

public class save_input_field {

    private static save_input_field instance;
    private final SharedPreferences prefs;

    static final String PREFS_NAME   = "UsbAppPrefs";
    static final String KEY_BAUD_RATE = "baud_rate";
    static final String KEY_IP        = "ip";
    static final String KEY_HTTP      = "http";
    static final String KEY_WEBSOCKET = "websocket";
    static final String KEY_BT_SWITCH = "bt_enable";

    // Private constructor for singleton
    private save_input_field(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Get singleton instance
    public static save_input_field getInstance(Context context) {
        if (instance == null) {
            instance = new save_input_field(context);
        }
        return instance;
    }

    public void restorePreferences(EditText baudrateInfoText, EditText url_ip,
                                   CheckBox http_checkbox, CheckBox websocket_checkbox,
                                   Switch btswitch) {
        baudrateInfoText.setText(String.valueOf(prefs.getInt(KEY_BAUD_RATE, 115200)));
        url_ip.setText(prefs.getString(KEY_IP, ""));
        http_checkbox.setChecked(prefs.getBoolean(KEY_HTTP, false));
        websocket_checkbox.setChecked(prefs.getBoolean(KEY_WEBSOCKET, false));
        btswitch.setChecked(prefs.getBoolean(KEY_BT_SWITCH, false));
    }

    public void savePreferences(EditText baudrateInfoText, EditText url_ip,
                                CheckBox http_checkbox, CheckBox websocket_checkbox,
                                Switch btswitch) {
        SharedPreferences.Editor editor = prefs.edit();

        int baud = 115200;
        try {
            baud = Integer.parseInt(baudrateInfoText.getText().toString().trim());
        } catch (NumberFormatException ignored) {}

        editor.putInt(KEY_BAUD_RATE, baud);
        editor.putString(KEY_IP, url_ip.getText().toString().trim());
        editor.putBoolean(KEY_HTTP, http_checkbox.isChecked());
        editor.putBoolean(KEY_WEBSOCKET, websocket_checkbox.isChecked());
        editor.putBoolean(KEY_BT_SWITCH, btswitch.isChecked());
        editor.apply();
    }


    public SharedPreferences get_shared_pref(){
        return prefs;
    }
}
