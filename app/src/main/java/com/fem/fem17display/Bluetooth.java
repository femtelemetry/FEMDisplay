package com.fem.fem17display;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.ProgressBar;

import com.nifcloud.mbaas.core.DoneCallback;
import com.nifcloud.mbaas.core.FindCallback;
import com.nifcloud.mbaas.core.NCMB;
import com.nifcloud.mbaas.core.NCMBException;
import com.nifcloud.mbaas.core.NCMBObject;
import com.nifcloud.mbaas.core.NCMBQuery;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.fem.fem17display.MainActivity.*;
import static java.nio.charset.StandardCharsets.UTF_8;

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

    //BluetoothのOutputStream.
    OutputStream mmOutputStream = null;

    //RtD音用
    SoundPool soundPool;
    int RtDsound;

    //NCMB(クラウド)
    NCMBObject objV;
    NCMBQuery<NCMBObject> queryC;
    NCMBObject objC;
    NCMBQuery<NCMBObject> queryM;

    //スリープカウント
    int SleepCount = 0;

    //スリープ関連
    PowerManager pm;
    PowerManager.WakeLock wakelock;
    boolean isWakelock = false;

    //電流積算
    double SumCURR; //満充電時から使った電流の合計
    static String SUM; //使った電流値の文字列
    static final double MaxCURR = 10000.0; //マシンが使う全合計電流値
    double BTT; //計算したバッテリ残量値
    String Battery;

    MainActivity mc = new MainActivity();

    @Override
    public void onCreate() {
        NCMB.initialize(this.getApplicationContext(), "dcce5f03061b495802c3262b617e1b2b791fc33cf035a3f1d31f3afe51cc0235", "1b81571033e7b1837517aa6c75049d9c42d0069fc8bca01e21c031169f3116c6");

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
            objV.put(INFO, MSG);
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

                    sendBroadcast(VIEW_BLUETOOTH, "bok");

                    // InputStreamの読み込み
                    bytes = mmInStream.read(buffer);
                    Log.i(TAG, "bytes=" + bytes);
                    //String型に変換
                    String readMsg = new String(buffer, 0, bytes);

                    /**
                     * 情報解析フェーズ
                     */
                    // 情報が欠損なしで届いていれば解析　届く情報は A/(LV)/(HV)/(MT1),~,(MT4)/(INV)/A その次にB/(RTD1),~(RTD4)/(ERROR1),~,(ERROR4)/B 最後にC/(CURR)/C
                    if (readMsg.trim().startsWith("A") && readMsg.trim().endsWith("A")) {
                        sendBroadcast(VIEW_INPUT, readMsg.trim());

                        objV = new NCMBObject("CLOUD");
                        sendBroadcast(VIEW_STATUS, " ");
                        String[] values;
                        values = readMsg.trim().split("/", 0);

                        /**
                         * LV解析
                         */
                        if (!values[VIEW_LV].contains("-")) {
                            if (Float.parseFloat(values[VIEW_LV]) < 10.0) {
                                if (values[VIEW_LV].length() >= 3) {
                                    values[VIEW_LV] = values[VIEW_LV].substring(0, 3);
                                }
                            } else {
                                if (values[VIEW_LV].length() >= 4) {
                                    values[VIEW_LV] = values[VIEW_LV].substring(0, 4);
                                }
                            }
                            AddCloud("LV", values[VIEW_LV]);
                        }
                        else{
                            values[VIEW_LV] = "-----";
                        }
                        sendBroadcast(VIEW_LV, values[VIEW_LV]);

                        /**
                         * HV解析
                         */
                        if(!values[VIEW_HV].contains("-")) {
                            sendBroadcast(VIEW_HV, values[VIEW_HV]);
                            AddCloud("HV", values[VIEW_HV]);
                            HVFlag = true;
                        }
                        else{
                            HVFlag = false;
                            values[VIEW_HV] = "-----";
                        }
                        sendBroadcast(VIEW_HV, values[VIEW_HV]);
                        //HVFlag = !values[VIEW_HV].contains("-");

                        /**
                         * MOTOR温度解析
                         */
                        String[] MTs;
                        int maxMT = 0;
                        MTs = values[VIEW_MT].trim().split("x", 0);
                        for(int n = 0; n < 4; n++){
                            if(!MTs[n].contains("-")) {
                                MTs[n] = MTs[n].split("\\.", 0)[0]; //整数にする
                                if (Integer.valueOf(MTs[n]) >= maxMT) {
                                    maxMT = Integer.valueOf(MTs[n]);
                                }
                                switch (n){
                                    case 0:
                                        AddCloud("MOTOR1", MTs[0]);
                                        break;
                                    case 1:
                                        AddCloud("MOTOR2", MTs[0]);
                                        break;
                                    case 2:
                                        AddCloud("MOTOR3", MTs[0]);
                                        break;
                                    case 3:
                                        AddCloud("MOTOR4", MTs[0]);
                                        break;
                                }
                            }
                        }
                        if(maxMT == 0){
                            sendBroadcast(VIEW_MT, "-----");
                        }
                        else {
                            sendBroadcast(VIEW_MT, Integer.toString(maxMT));
                        }

                        /**
                         * INV温度解析
                         */
                        if(!values[VIEW_INV].contains("-")) {
                            values[VIEW_INV] = values[VIEW_INV].split("\\.", 0)[0]; //整数にする
                            AddCloud("INV", values[VIEW_INV]);
                        }
                        else{
                            values[VIEW_INV] = "-----";
                        }
                        sendBroadcast(VIEW_INV, values[VIEW_INV]);

                        // データストアへの登録
                        objV.saveInBackground(new DoneCallback() {
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

                        objV = new NCMBObject("CLOUD");
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
                        AddCloud("RtD2", RTDs[1]);
                        AddCloud("RtD3", RTDs[2]);
                        AddCloud("RtD4", RTDs[3]);

                        /**
                         * ERROR解析
                         */
                        String[] ERRORs;
                        ERRORs = values[VIEW_ERR - numA].split("x", 0);
                        ERRFlag = !ERRORs[0].startsWith("-") || !ERRORs[1].startsWith("-") || !ERRORs[2].startsWith("-") || !ERRORs[3].startsWith("-");
                        for(int n = 0; n < 4; n++) {
                            String[] s_err;
                            s_err = ERRORs[n].split(",", 0);
                            ERRORs[n] = "Error:" + s_err[0] + " Info:[1]" + s_err[1] + " [2]" + s_err[2] + " [3]" + s_err[3];
                            if(s_err[0].contains("2310")) {
                                ERRORs[n] += "\nEncoder communication";
                            }
                            else if(s_err[0].contains("3587") && !(s_err[1].contains("-"))){
                                ERRORs[n] += "\nError during operation";
                            }
                            else if(s_err[0].contains("3587")){
                                ERRORs[n] += "\nTemperature cabinet too high";
                            }
                            else if(s_err[0].contains("d1")){
                                ERRORs[n] += "\n出力制限① インバータ温度50℃↑";
                            }
                            else if(s_err[0].contains("d2")){
                                ERRORs[n] += "\n出力制限② モータ温度125℃↑";
                            }
                            else if(s_err[0].contains("d3")){
                                ERRORs[n] += "\n出力制限③ IGBT温度115℃↑";
                            }
                            else if(s_err[0].contains("d4")){
                                ERRORs[n] += "\n出力制限④ HV250V↓";
                            }
                            else if(s_err[0].contains("d5")){
                                ERRORs[n] += "\n出力制限⑤ HV720V↑";
                            }
                            else if(s_err[0].contains("d0")){
                                ERRORs[n] += "\n出力制限";
                            }
                        }
                        sendBroadcast(VIEW_ERRFR, ERRORs[0]);
                        sendBroadcast(VIEW_ERRFL, ERRORs[1]);
                        sendBroadcast(VIEW_ERRRR, ERRORs[2]);
                        sendBroadcast(VIEW_ERRRL, ERRORs[3]);
                        if(ERRFlag){
                            AddCloud("ERROR1", ERRORs[0]);
                            AddCloud("ERROR2", ERRORs[1]);
                            AddCloud("ERROR3", ERRORs[2]);
                            AddCloud("ERROR4", ERRORs[3]);
                        }
                        else{
                            AddCloud("ERROR1", "-");
                            AddCloud("ERROR2", "-");
                            AddCloud("ERROR3", "-");
                            AddCloud("ERROR4", "-");
                        }

                        // データストアへの登録
                        objV.saveInBackground(new DoneCallback() {
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
                    else if (readMsg.trim().startsWith("C") && readMsg.trim().endsWith("C")){

                        sendBroadcast(VIEW_INPUT, readMsg.trim());

                        objV = new NCMBObject("CLOUD");
                        sendBroadcast(VIEW_STATUS, " ");

                        String[] values;

                        values = readMsg.trim().split("/", 0);
                        /**
                         * 電流値解析
                         */
                        if(!values[1].contains("-")) {

                            AddCloud("CURRENT", values[1]);

                            try {
                                //現在の電流積算値を取得
                                FileInputStream fileInputStream = openFileInput(CurrFilename);
                                BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream));
                                String lineBuffer;
                                while ((lineBuffer = reader.readLine()) != null) {
                                    SUM = lineBuffer;
                                }
                            } catch(FileNotFoundException e){
                                FileOutputStream fos = openFileOutput(CurrFilename, Context.MODE_PRIVATE);
                                String first = "0.0";
                                fos.write(first.getBytes());
                                SUM = "0.0";
                                String MAX = String.valueOf(MaxCURR);
                                String NOWBTT = SUM + "/" + MAX;
                                sendBroadcast(VIEW_NOWBTT, NOWBTT);
                            } catch(IOException e){

                            } finally{
                                //電流積算値を更新
                                SumCURR = Double.parseDouble(SUM) + Double.parseDouble(values[1]);
                                try {
                                    FileOutputStream fos = openFileOutput(CurrFilename, Context.MODE_PRIVATE);
                                    SUM = String.valueOf(SumCURR);
                                    fos.write(SUM.getBytes());
                                    fos.close();

                                    //バッテリ残量値を計算
                                    BTT = (1.0 - SumCURR / MaxCURR) * 100.0;
                                    Battery = String.valueOf(BTT);
                                    //sendBroadcast(VIEW_BTT, Battery);
                                    //sendBroadcast(VIEW_INPUT, Battery);
                                    AddCloud("SUMCURR", SUM);
                                    //小数点以下文字切り取り処理
                                    String[] Batteries;
                                    Batteries = Battery.split("\\.", 0);
                                    sendBroadcast(VIEW_BTT, Batteries[0]);   //バッテリ残量表示
                                    AddCloud("BTT", Batteries[0]);   //バッテリ残量クラウド送信

                                    // データストアへの登録
                                    objV.saveInBackground(new DoneCallback() {
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
                                } catch (IOException e) {

                                }
                            }
                            values[1] = "0";
                        }

                    }
                    else{
                        //正式なデータが届いていない時の処理
                        sendBroadcast(VIEW_STATUS, "受信データに問題があります");
                    }
                    /**
                     * レイアウト変更フェーズ
                     */
                    LayoutChange();
                    //現在の電流積算値とMAX電流値表示 デバッグ用
                    String MAX = String.valueOf(MaxCURR);
                    String NOWBTT = SUM + "/" + MAX;
                    sendBroadcast(VIEW_NOWBTT, NOWBTT);
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
                sendBroadcast(VIEW_INPUT, "error"+ e);
                sendBroadcast(VIEW_BLUETOOTH, "bno");

                //Bluetooth未接続ならスリープ状態へ
                if(SleepCount > 2 && !isSleep) {
                    isSleep = true;
                    if(isWakelock) {
                        isWakelock = false;
                        wakelock.release();
                    }
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
        if(ERRFlag){
            if(!(NowLayout == ERR)) {
                //ERRモードに遷移
                sendBroadcast(LAYOUT_ERR, null);
            }
        }
        else if(RtDFlag && HVFlag){
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
        isWakelock = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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
