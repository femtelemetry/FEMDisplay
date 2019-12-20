package com.fem.fem17display;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.nifcloud.mbaas.core.NCMB;
import com.nifcloud.mbaas.core.NCMBException;
import com.nifcloud.mbaas.core.NCMBObject;
import com.nifcloud.mbaas.core.DoneCallback;

public class MainActivity extends AppCompatActivity  implements View.OnClickListener {
    /* tag */
    private static final String TAG = "BluetoothSample";

    /* bluetooth Adapter */
    private BluetoothAdapter mAdapter;

    /* Bluetoothデバイス */
    private BluetoothDevice mDevice;

    /* bluetooth UUID */
    private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /* デバイス名 */
    private final String DEVICE_NAME = "RNBT-ADCE";

    /* Soket */
    private BluetoothSocket mSocket;

    /* Thread */
    private Thread mThread;

    /* Threadの状態を表す */
    private boolean isRunning;

    /**
     * 接続ボタン.
     */
    private Button connectButton;

    /**
     * ステータス.
     */
    private TextView mStatusTextView;

    /**
     * Bluetooth接続ステータス.
     */
    private TextView mBluetooth;

    /**
     * Bluetoothから受信した値.
     */
    private TextView mInputTextView;

    /**
     * Action(ステータス表示).
     */
    private static final int VIEW_STATUS = 0;

    /**
     * Action(LV).
     */
    private static final int VIEW_LV = 1;

    /**
     * Action(HV).
     */
    private static final int VIEW_HV = 2;

    /**
     * Action(MOTOR).
     */
    private static final int VIEW_MT = 3;

    /**
     * Action(INV).
     */
    private static final int VIEW_INV = 4;

    /**
     * Action(RTD).
     */
    private static final int VIEW_RTD = 5;

    /**
     * Action(ERROR).
     */
    private static final int VIEW_ERR = 6;

    /**
     * Action(CURRENT)
     */
    private static final int VIEW_CURR = 7;

    /**
     * Action(CURRENT)
     */
    private static final int VIEW_TIME = 8;

    /**
     * Action(bluetooth).
     */
    private static final int VIEW_BLUETOOTH = 100;

    /**
     * Action(デバック用取得文字列).
     */
    private static final int VIEW_INPUT = 101;

    /**
     * Connect確認用フラグ
     */
    private boolean connectFlg = false;

    /**
     * BluetoothのOutputStream.
     */
    OutputStream mmOutputStream = null;

    /**
     * LV電圧値
     */
    private TextView mLV;

    /**
     * HV電圧値
     */
    private TextView mHV;

    /**
     * MOTOR温度値
     */
    private TextView mMT;

    /**
     * INV温度値
     */
    private TextView mINV;

    /**
     * Ready to Drive表示
     */
    private TextView mRTD;

    /**
     * エラー表示
     */
    private TextView mERR;

    /**
     * 電流表示
     */
    private TextView mCURR;

    /**
     * RtD音用
     */
    private SoundPool soundPool;
    private int RtDsound;

    /**
     * RtDONOFF
     */
    private boolean RtDFlag = false;

    /**
     * NCMB(クラウド)
     */
    NCMBObject obj;

    /**
     * bluetooth Image
     */
    ImageView Bluetooth_Image;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //画面常にON
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        NCMB.initialize(this.getApplicationContext(), "7fbdc0ab79557c97b30cd17cf7fdb4220e9a20b2ea5c8bf1e47af25d3e859b66", "5049e231f805ffc63855055fc8861b783f511576e6096fffaebc395f8e0e1ed3");

        mInputTextView = (TextView) findViewById(R.id.inputValue);
        mStatusTextView = (TextView) findViewById(R.id.statusValue);
        mBluetooth = (TextView) findViewById(R.id.bluetoothValue);
        mLV = (TextView) findViewById(R.id.LVValue);
        mHV = (TextView) findViewById(R.id.HVValue);
        mMT = (TextView) findViewById(R.id.MTValue);
        mINV = (TextView) findViewById(R.id.INVValue);
        mRTD = (TextView) findViewById(R.id.RTDValue);
        mERR = (TextView) findViewById(R.id.ERRORValue);

        connectButton = (Button) findViewById(R.id.connectButton);
        //writeButton = (Button)findViewById(R.id.writeButton);

        connectButton.setOnClickListener(this);
        //writeButton.setOnClickListener(this);

        // Bluetoothのデバイス名を取得
        // デバイス名は、RNBT-XXXXになるため、
        // DVICE_NAMEでデバイス名を定義
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mStatusTextView.setText("SearchDevice");
        Set<BluetoothDevice> devices = mAdapter.getBondedDevices();
        for (BluetoothDevice device : devices) {
            if (device.getName().equals(DEVICE_NAME)) {
                mStatusTextView.setText("find: " + device.getName());
                mDevice = device;
            }
        }

        //Sound準備コード
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            soundPool = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
        } else {
            AudioAttributes attr = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setAudioAttributes(attr)
                    .setMaxStreams(5)
                    .build();
        }
        RtDsound = soundPool.load(getApplicationContext(), R.raw.pekowave1, 1);
        Bluetooth_Image = findViewById(R.id.bluetooth);
    }


    @Override
    protected void onPause() {
        super.onPause();

        isRunning = false;
        try {
            mSocket.close();
        } catch (Exception e) {
        }
    }

    private Runnable bluetooth_run = new Runnable() {
        @Override
        public void run() {
                InputStream mmInStream = null;

                ShowMessage(VIEW_STATUS, "connecting...");

                // 取得したデバイス名を使ってBluetoothでSocket接続
                try {

                    mSocket = mDevice.createRfcommSocketToServiceRecord(MY_UUID);
                    mSocket.connect();
                    mmInStream = mSocket.getInputStream();
                    mmOutputStream = mSocket.getOutputStream();

                    ShowMessage(VIEW_BLUETOOTH, "BTOK");

                    // InputStreamのバッファを格納
                    byte[] buffer = new byte[1024];

                    // 取得したバッファのサイズを格納
                    int bytes;

                    ShowMessage(VIEW_STATUS, "connected.");

                    connectFlg = true;

                    while (isRunning) {

                        // InputStreamの読み込み
                        bytes = mmInStream.read(buffer);
                        Log.i(TAG, "bytes=" + bytes);
                        // String型に変換
                        String readMsg = new String(buffer, 0, bytes);

                        ShowMessage(VIEW_INPUT, readMsg.trim());

                        // 情報が欠損なしで届いていれば解析　届く情報は F/(LV)/(HV)/(MT)/(INV)/(RTD)/(ERROR1),~,(ERROR4)/(CURR)/(TIME)/L
                        if (readMsg.trim().startsWith("F") && readMsg.trim().endsWith("L")) {
                            obj = new NCMBObject("Voltage");

                            Log.i(TAG, "value=" + readMsg.trim());
                            String[] values;
                            values = readMsg.trim().split("/", 0);

                            if (!values[VIEW_LV].isEmpty()) {
                                if (Float.parseFloat(values[VIEW_LV]) < 10.0) {
                                    if (values[VIEW_LV].length() >= 3) {
                                        values[VIEW_LV] = values[VIEW_LV].substring(0, 3);
                                    }
                                } else {
                                    if (values[VIEW_LV].length() >= 4) {
                                        values[VIEW_LV] = values[VIEW_LV].substring(0, 4);
                                    }
                                }
                            }
                            ShowMessage(VIEW_LV, values[VIEW_LV]);
                            AddCloud("LV", values[VIEW_LV]);

                            ShowMessage(VIEW_HV, values[VIEW_HV]);
                            AddCloud("HV", values[VIEW_HV]);

                            values[VIEW_MT] = values[VIEW_MT].split("\\.", 0)[0]; //整数にする
                            ShowMessage(VIEW_MT, values[VIEW_MT]);
                            AddCloud("MOTOR", values[VIEW_MT]);

                            values[VIEW_INV] = values[VIEW_INV].split("\\.", 0)[0]; //整数にする
                            ShowMessage(VIEW_INV, values[VIEW_INV]);
                            AddCloud("INV", values[VIEW_INV]);

                            if (values[VIEW_RTD].contains("1")) {
                                values[VIEW_RTD] = "ON";
                                AddCloud("RtD", "100");
                                if (!(RtDFlag)) {
                                    soundPool.play(RtDsound, 1f, 1f, 0, 0, 1f);
                                    RtDFlag = true;
                                }
                            } else {
                                values[VIEW_RTD] = "OFF";
                                AddCloud("RtD", "0");
                                if (RtDFlag) {
                                    RtDFlag = false;
                                }
                            }
                            ShowMessage(VIEW_RTD, values[VIEW_RTD]);

                    /*
                    String[] ERRORs;
                    ERRORs = values[VIEW_ERR].split("\\.", 0);
                    values[VIEW_ERR] = "FR: " +ERRORs[0]+ " " + "FL: " +ERRORs[1]+ " " + "RR: " +ERRORs[2]+ " " + "RL: " +ERRORs[3];
                    */
                            ShowMessage(VIEW_ERR, values[VIEW_ERR]);

                            AddCloud("CURRENT", values[VIEW_CURR]);

                            AddCloud("TIME", values[VIEW_TIME]);

                            // データストアへの登録
                            obj.saveInBackground(new DoneCallback() {
                                @Override
                                public void done(NCMBException e) {
                                    if (e != null) {
                                        //保存に失敗した場合の処理
                                        e.printStackTrace();
                                    } else {
                                        //保存に成功した場合の処理

                                    }
                                }
                            });
                        } else {
                            // Log.i(TAG,"value=nodata");
                        }

                    }
                } catch (Exception e) {

                    ShowMessage(VIEW_STATUS, "Bluetooth未接続\nErrorMessage:" + e);

                    try {
                        mSocket.close();
                    } catch (Exception ee) {
                    }
                    isRunning = false;
                    connectFlg = false;

                    ShowMessage(VIEW_BLUETOOTH, "BTNO");

                    /**
                     * Bluetooth自動再接続
                     */
                    /*
                    ScheduledExecutorService reconnect = Executors.newSingleThreadScheduledExecutor();
                    reconnect.schedule(bluetooth_run, 1000, TimeUnit.MILLISECONDS);
                    */
                    mThread = new Thread(bluetooth_run);
                    // Threadを起動し、Bluetooth接続
                    isRunning = true;
                    mThread.start();
                }
            }
    };


    @Override
    public void onClick(View v) {
        if (v.equals(connectButton)) {
            // 接続されていない場合のみ
            if (!connectFlg) {

                mStatusTextView.setText("try connect");

                mThread = new Thread(bluetooth_run);
                // Threadを起動し、Bluetooth接続
                isRunning = true;
                mThread.start();

            }
        }
    }

    /**
     * Handlerへのmessage送信関数
     */
    private void ShowMessage(int VIEW, String MSG) {
        Message valueMsg = new Message();
        valueMsg.what = VIEW;
        valueMsg.obj = MSG;
        mHandler.sendMessage(valueMsg);
    }

    /**
     * クラウド送信メッセージ登録
     */
    private void AddCloud(String INFO, String MSG) {
        // オブジェクトの値を設定
        try {
            obj.put(INFO, MSG);
        } catch (NCMBException e) {
            e.printStackTrace();
        }
    }

    /**
     * 描画処理はHandlerでおこなう
     */
    @SuppressLint("HandlerLeak")
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int action = msg.what;
            String msgStr = (String)msg.obj;
            if(action == VIEW_INPUT){
                mInputTextView.setText(msgStr);
            }
            else if(action == VIEW_STATUS){
                mStatusTextView.setText(msgStr);
            }
            else if(action == VIEW_BLUETOOTH){
                mBluetooth.setText(msgStr);
                if(msgStr.contains("BTOK")){
                    Bluetooth_Image.setImageResource(R.drawable.bluetooth);
                }
                else if(msgStr.contains("BTNO")){
                    Bluetooth_Image.setImageResource(R.drawable.bluetooth_no);
                }
            }
            else if(action == VIEW_LV){
                mLV.setText(msgStr);
            }
            else if(action == VIEW_HV){
                mHV.setText(msgStr);
            }
            else if(action == VIEW_MT){
                mMT.setText(msgStr);
            }
            else if(action == VIEW_INV){
                mINV.setText(msgStr);
            }
            else if(action == VIEW_RTD){
                mRTD.setText(msgStr);
            }
            else if(action == VIEW_ERR){
                mERR.setText(msgStr);
            }
        }
    };

}

