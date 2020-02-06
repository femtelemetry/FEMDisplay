package com.fem.fem17display;

import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.WindowManager;

import com.nifcloud.mbaas.core.DoneCallback;
import com.nifcloud.mbaas.core.NCMB;
import com.nifcloud.mbaas.core.NCMBException;
import com.nifcloud.mbaas.core.NCMBObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import static com.fem.fem17display.MainActivity.*;

public class Bluetooth extends Service {

    /* bluetooth Adapter */
    BluetoothAdapter mAdapter;

    /* Bluetoothデバイス */
    BluetoothDevice mDevice;

    /* bluetooth UUID */
    final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /* デバイス名 */
    final String DEVICE_NAME = "RNBT-ADCE";

    /* Soket */
    BluetoothSocket mSocket;

    /* Thread */
    Thread mThread;

    /* Threadの状態を表す */
    boolean isRunning;

    /**
     * BluetoothのOutputStream.
     */
    OutputStream mmOutputStream = null;

    /**
     * RtD音用
     */
    SoundPool soundPool;
    int RtDsound;

    /**
     * NCMB(クラウド)
     */
    NCMBObject obj;

    /**
     * スリープカウント　ブーリアン
     */
    int SleepCount = 0;

    /**
     * スリープ関連
     */
    PowerManager pm;
    PowerManager.WakeLock wakelock;

    @Override
    public void onCreate() {
        NCMB.initialize(this.getApplicationContext(), "7fbdc0ab79557c97b30cd17cf7fdb4220e9a20b2ea5c8bf1e47af25d3e859b66", "5049e231f805ffc63855055fc8861b783f511576e6096fffaebc395f8e0e1ed3");

        // Bluetoothのデバイス名を取得
        // デバイス名は、RNBT-XXXXになるため、
        // DVICE_NAMEでデバイス名を定義
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        //mStatusTextView.setText("SearchDevice");
        Set<BluetoothDevice> devices = mAdapter.getBondedDevices();
        for (BluetoothDevice device : devices) {
            if (device.getName().equals(DEVICE_NAME)) {
                //mStatusTextView.setText("find: " + device.getName());
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

        /**
         * Bluetooth接続スレッド起動
         */
        mThread = new Thread(bluetooth_run);
        // Threadを起動し、Bluetooth接続
        isRunning = true;
        mThread.start();
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

    private Runnable bluetooth_run = new Runnable() {
        @Override
        public void run() {
            InputStream mmInStream = null;

            sendBroadcast(VIEW_STATUS, "Bluetooth Connecting...");

            // 取得したデバイス名を使ってBluetoothでSocket接続
            try {
                /**
                 * Bluetooth接続フェーズ
                 */
                mSocket = mDevice.createRfcommSocketToServiceRecord(MY_UUID);
                mSocket.connect();
                mmInStream = mSocket.getInputStream();
                mmOutputStream = mSocket.getOutputStream();

                sendBroadcast(VIEW_BLUETOOTH, "Bluetooth Connected");
                sendBroadcast(VIEW_STATUS, " ");

                // InputStreamのバッファを格納
                byte[] buffer = new byte[1024];

                // 取得したバッファのサイズを格納
                int bytes;

                connectFlg = true;
                if(isSleep) {
                    WakeUp();
                    isSleep = false;
                }
                SleepCount = 0;

                /**
                 * 情報受信フェーズ
                 */
                while (isRunning) {

                    // InputStreamの読み込み
                    bytes = mmInStream.read(buffer);
                    Log.i(TAG, "bytes=" + bytes);
                    //String型に変換
                    String readMsg = new String(buffer, 0, bytes);

                    /**
                     * 情報解析フェーズ
                     */
                    // 情報が欠損なしで届いていれば解析　届く情報は A/(LV)/(HV)/(MT1),~,(MT4)/(INV)/A その次にB/(RTD1),~(RTD4)/(ERROR1),~,(ERROR4)/(CURR)/(DELTA)/B
                    if (readMsg.trim().startsWith("A") && readMsg.trim().endsWith("A")) {
                        sendBroadcast(VIEW_INPUT, readMsg.trim());

                        obj = new NCMBObject("Voltage");
                        sendBroadcast(VIEW_STATUS, " ");
                        String[] values;
                        values = readMsg.trim().split("/", 0);

                        /**
                         * LV解析
                         */
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
                        sendBroadcast(VIEW_LV, values[VIEW_LV]);
                        AddCloud("LV", values[VIEW_LV]);

                        /**
                         * HV解析
                         */
                        sendBroadcast(VIEW_HV, values[VIEW_HV]);
                        AddCloud("HV", values[VIEW_HV]);
                        HVFlag = !values[VIEW_HV].contains("-");

                        /**
                         * MOTOR温度解析
                         */
                        String[] MTs;
                        int maxMT = 0;
                        MTs = values[VIEW_MT].trim().split("x", 0);
                        for(int n = 0; n < 4; n++){
                            MTs[n] = MTs[n].split("\\.", 0)[0]; //整数にする
                            if(Integer.valueOf(MTs[n]) >= maxMT){
                                maxMT = Integer.valueOf(MTs[n]);
                            }
                        }
                        sendBroadcast(VIEW_MT, Integer.toString(maxMT));
                        AddCloud("MOTOR1", MTs[0]);
                        AddCloud("MOTOR2", MTs[1]);
                        AddCloud("MOTOR3", MTs[2]);
                        AddCloud("MOTOR4", MTs[3]);

                        /**
                         * INV温度解析
                         */
                        values[VIEW_INV] = values[VIEW_INV].split("\\.", 0)[0]; //整数にする
                        sendBroadcast(VIEW_INV, values[VIEW_INV]);
                        AddCloud("INV", values[VIEW_INV]);

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
                    }
                    else if (readMsg.trim().startsWith("B") && readMsg.trim().endsWith("B")) {
                        int numA = 4;

                        sendBroadcast(VIEW_INPUT, readMsg.trim());

                        obj = new NCMBObject("Voltage");
                        sendBroadcast(VIEW_STATUS, " ");

                        String[] values;

                        values = readMsg.trim().split("/", 0);

                        /**
                         * RTD解析
                         */
                        String[] RTDs;
                        RTDs = values[VIEW_RTD - numA].trim().split("x", 0);
                        String result_RTD;
                        if (RTDs[0].contains("1") || RTDs[1].contains("1") || RTDs[2].contains("1") || RTDs[3].contains("1")) {
                            result_RTD = "RTD";
                            if (!(RtDFlag)) {
                                soundPool.play(RtDsound, 1f, 1f, 0, 0, 1f);
                                RtDFlag = true;
                            }
                        } else {
                            result_RTD = "---";
                            if (RtDFlag) {
                                RtDFlag = false;
                            }
                        }
                        sendBroadcast(VIEW_RTD, result_RTD);
                        AddCloud("RtD1", RTDs[0]);
                        AddCloud("RtD2", RTDs[0]);
                        AddCloud("RtD3", RTDs[0]);
                        AddCloud("RtD4", RTDs[0]);

                        /**
                         * ERROR解析
                         */
                            /*
                            String[] ERRORs;
                            ERRORs = values[VIEW_ERR].split("\\.", 0);
                            values[VIEW_ERR] = "FR: " +ERRORs[0]+ " " + "FL: " +ERRORs[1]+ " " + "RR: " +ERRORs[2]+ " " + "RL: " +ERRORs[3];
                            */
                        sendBroadcast(VIEW_ERR, values[VIEW_ERR - numA]);

                        /**
                         * 電流値解析
                         */
                        AddCloud("CURRENT", values[VIEW_CURR - numA]);

                        AddCloud("DELTA", values[VIEW_DELTA - numA]);

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

                    }
                    else{
                        //正式なデータが届いていない時の処理
                        sendBroadcast(VIEW_STATUS, "受信データに問題があります");
                    }
                    /**
                     * レイアウト変更フェーズ
                     */
                    LayoutChange();
                }
            } catch (Exception e) {

                try {
                    mSocket.close();
                } catch (Exception ee) {
                }
                isRunning = false;
                connectFlg = false;

                SleepCount++;
                Log.i(TAG, "SleepCount=" + SleepCount);
                sendBroadcast(VIEW_BLUETOOTH, "Bluetooth NoConnect : " + e);

                if(SleepCount > 1 && !isSleep) {
                    isSleep = true;
                    //画面消す
                    mDevicePolicyManager.lockNow();
                }
                /**
                 * Bluetooth自動再接続
                 */
                mThread = new Thread(bluetooth_run);
                // Threadを起動し、Bluetooth接続
                isRunning = true;
                mThread.start();
            }
        }
    };

    /**
     * レイアウト変更メソッド
     */
    public void LayoutChange(){
        if(RtDFlag && HVFlag){
            if(!(NowLayout == RTD)) {
                //RTDモードに遷移
                sendBroadcast(LAYOUT_RTD, null);
            }
        }
        else if(HVFlag){
            if(!(NowLayout == HVON)) {
                //HVONモードに遷移
                sendBroadcast(LAYOUT_HVON, null);
            }
        }
        else{
            if(!(NowLayout == LVON)) {
                //LVONモードに遷移
                sendBroadcast(LAYOUT_LVON, null);
            }
        }
    }

    private void sendBroadcast(int VIEW, String message) {
        // IntentをブロードキャストすることでMainActivityへデータを送信
        Intent intent = new Intent();
        intent.setAction("BLUETOOTH");
        intent.putExtra("VIEW", VIEW);
        intent.putExtra("message", message);
        getBaseContext().sendBroadcast(intent);
    }

    private void WakeUp() throws IOException {
        pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakelock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.ON_AFTER_RELEASE, "myapp:Your App Tag");
        wakelock.acquire();
        wakelock.release();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("TestService", "onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            mSocket.close();
        } catch (Exception ee) {
        }
        isRunning = false;
        connectFlg = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
