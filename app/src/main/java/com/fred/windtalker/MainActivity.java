package com.fred.windtalker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import static com.fred.windtalker.MainActivity.getIP;
import static com.fred.windtalker.MainActivity.getPort;
import static com.fred.windtalker.MainActivity.talkToUI;


public class MainActivity extends AppCompatActivity {
    //tag for logging
    private final String TAG = MainActivity.class.getSimpleName() + R.string.debug;
    //flag for logging
    private boolean mLogging = false;
    //tag for logging

    private Menu appBarMenu;
    private static String appBarTitle;
    public void setAppBarTitle (String title){
        appBarTitle = title;
    }
    public String getAppBarTitle(){
        return appBarTitle;
    }
    private static SharedPreferences sharedPref;
    private static int hubPort;
    public static int getPort(){
        return hubPort;
    }
    private static String hubIP;
    public static InetAddress getIP() {
        InetAddress IA = null;
        try {
            IA = InetAddress.getByName(hubIP);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return IA;
    }
    private ActionBar ab;
    private EditText editText;
    private String getTxt (){
        return editText.getText().toString();
    }
    private static SendUDP udpSender;
    private Thread udpThread;
    public static Handler talkToUI;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (mLogging) {
            String logstring = "Main Activity Created";
            Log.d(TAG, logstring);
        }
        ab = getSupportActionBar();
        ab.setLogo(R.mipmap.wt_icon);
        ab.setDisplayUseLogoEnabled(true);
        ab.setDisplayShowHomeEnabled(true);
        setAppBarTitle(getString(R.string.advice));
        getFragmentManager().beginTransaction()
                .replace(R.id.SettingsFrag, new SettingsFragment()).commit();
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        hubPort = Integer.parseInt(sharedPref.getString("Port", getString(R.string.defaultPort)));
        hubIP = sharedPref.getString("hubAddress", getString(R.string.defaultIP));
        editText = findViewById(R.id.editInp);
        udpSender = new SendUDP();
        talkToUI = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Toast.makeText(getApplicationContext(), msg.obj.toString(), Toast.LENGTH_LONG).show();
                setAppBarTitle(getString(R.string.advice));
                appBarMenu.getItem(0).setTitle(getAppBarTitle());
            }
        };
        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                    imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
                }
            }
        });
    }
    @Override
    protected void onStart(){
        super.onStart();
    }

    @Override
    public void onResume(){
        super.onResume();
        if (appBarMenu != null){
            appBarMenu.getItem(0).setTitle(getAppBarTitle());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.a_bar_menu, menu);             // Inflate the menu; this adds items to the action bar
        this.appBarMenu = menu;
        menu.getItem(0).setTitle(getAppBarTitle());
        return true;
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

        }
        @Override
        public void onResume() {
            super.onResume();
            // Set up a listener whenever a key changes
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }
        @Override
        public void onPause() {
            super.onPause();
            // Suspend listner on pause
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            //update all
            hubPort =  Integer.parseInt(sharedPreferences.getString("Port", getString(R.string.defaultPort)));
            hubIP = sharedPreferences.getString("hubAddress", getString(R.string.defaultIP));
            try{
                udpSender.reSetSock();
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    public void sendText(View view) {
        setAppBarTitle("Sending");
        appBarMenu.getItem(0).setTitle(getAppBarTitle());
        editText.clearFocus();
        udpSender.setMsgString(getTxt());
        udpThread = new Thread(udpSender);
        udpThread.start();
    }

    @Override
    public void onPause(){
        super.onPause();
    }
}


class SendUDP implements Runnable {
    //tag for logging
    private final String TAG = SendUDP.class.getSimpleName() + R.string.debug;
    //flag for logging
    private boolean mLogging = true;

    private InetAddress IPout = getIP();
    private static int port = getPort();
    private static DatagramSocket socket;
    public static void setSock(){
        try {
            socket = new DatagramSocket(port);
        }
        catch  (SocketException e) {
            e.printStackTrace();
        }
    }
    public void reSetSock(){
        IPout = getIP();
        port = getPort();
        setSock();
    }
    private int pkt_len;
    private byte[] message;
    private DatagramPacket packet;
    private String msgString;
    public void setMsgString(String mString){
        msgString = mString;
    }

    SendUDP(){
        setSock();
    }

    @Override
    public void run() {
        message = msgString.getBytes();
        pkt_len = message.length;
        packet = new DatagramPacket(message, pkt_len,IPout, port);
        try {
            socket.send(packet);
            if (mLogging) {
                Log.d(TAG, msgString);
            }
        }catch (java.io.IOException e) {
            e.printStackTrace();
        }
        Message msg = talkToUI.obtainMessage();
        msg.obj = "Sent";
        talkToUI.sendMessage(msg);
    }
}

