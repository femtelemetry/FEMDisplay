package com.fem.FEMdisplaynoE;

import static android.content.ContentValues.TAG;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
//import android.location.Location;
//import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import com.nifcloud.mbaas.core.DoneCallback;
import com.nifcloud.mbaas.core.NCMB;
import com.nifcloud.mbaas.core.NCMBException;
import com.nifcloud.mbaas.core.NCMBObject;

//import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
//import java.nio.Buffer;
import java.util.Set;
import java.util.UUID;

public class Bluetooth extends Service {
    BluetoothAdapter btAdapter; //Bluetooth Adapter
    BluetoothDevice btDevice; //Bluetooth Device
    //BluetoothDevice btDevice2;
    static BluetoothSocket mSocket; //Bluetooth Socket
    Thread mThread; //Bluetooth通信用のThread
    //Thread mThread2;
    boolean isRunning; //Threadの接続状態を表す
    InputStream mmInputStream = null; //BluetoothのInputStream.
    OutputStream mmOutputStream = null; //BluetoothのOutputStream.

    //InputStream mmInputStream2 = null; //BluetoothのInputStream.
    //OutputStream mmOutputStream2 = null; //BluetoothのOutputStream.

    final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Bluetooth UUID
    //final UUID MY_UUID2 = UUID.randomUUID();

    final String DEVICE_NAME = "ESP32"; //デバイス名（ESP32のBluetooth通信モジュールのデバイス名）//the device has to be paired first
    //final String DEVICE_NAME2 = "AMS_Slave";

    //サウンド
    SoundPool soundPool;     //RtD音用
    int RtDsound;
    NCMBObject objV; //NCMB(クラウド)

    //スリープカウント
    int SleepCount = 0;

    //スリープ関連
    PowerManager pm;
    PowerManager.WakeLock wakelock;
    boolean isWakelock = false;

    @Override
    public void onCreate() {
        MainActivity.btServiceOn = true;
        sendBroadcast(MainActivity.VIEW_STATUS, "Bluetooth.java running..."); //デバック用

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if(btAdapter == null) {
            sendBroadcast(MainActivity.VIEW_STATUS, "Bluetooth not supported");
        }

        //CHECKS BLUETOOTH FOR THE USER, TURNS IT ON
        if(!btAdapter.isEnabled()) {
            sendBroadcast(MainActivity.VIEW_STATUS, "Bluetooth is not yet enabled");
            btAdapter.enable(); //automatically enables bluetooth for the user
            sendBroadcast(MainActivity.VIEW_STATUS, "Bluetooth is now enabled");
        }
        else{
            sendBroadcast(MainActivity.VIEW_STATUS, "Bluetooth is already enabled");
        }

        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            sendBroadcast(MainActivity.VIEW_STATUS, "Getting paired devices");
            for (BluetoothDevice device : pairedDevices) {
                //String deviceName = device.getName();
                //String deviceHardwareAddress = device.getAddress(); // MAC address
                if (device.getName().equals(DEVICE_NAME)) {
                    sendBroadcast(MainActivity.VIEW_STATUS, "Connecting to " + device.getName() + "...");
                    btDevice = device;
                }
                else{
                    sendBroadcast(MainActivity.VIEW_STATUS, "Not yet paired with " + device.getName());
                }
                /*
                if (device.getName().equals(DEVICE_NAME2)){
                    sendBroadcast(MainActivity.VIEW_STATUS, "Connecting to " + device.getName() + "...");
                    btDevice2 = device;
                }
                else{
                    sendBroadcast(MainActivity.VIEW_STATUS, "Not yet paired with " + device.getName());
                }*/
            }
        }

        // クラウド連携先の設定 ApplicationKey ClientKey
       NCMB.initialize(this.getApplicationContext(), "dcce5f03061b495802c3262b617e1b2b791fc33cf035a3f1d31f3afe51cc0235", "1b81571033e7b1837517aa6c75049d9c42d0069fc8bca01e21c031169f3116c6");

        //Ready to Drive用の効果音準備
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

        //Bluetooth接続スレッド起動
        //mThread = new Thread(bluetooth_run); //the problem is with this currently
        //isRunning = true;
        //mThread.start();
        mThread = new ConnectThread(btDevice);

        //mThread2 = new ConnectThread(btDevice2);
        isRunning = true;
        mThread.start();
        //mThread2.start();
    }

    public class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        //private final BluetoothSocket mmSocket2;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            //BluetoothSocket tmp2 = null;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                if(device == btDevice){
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                }
                /*
                else if(device == btDevice2){
                    tmp2 = device.createRfcommSocketToServiceRecord(MY_UUID2);
                }
                */
                //Thread.sleep(1000); //delay
            } catch (IOException e){// | InterruptedException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
            //mmSocket2 = tmp2;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            btAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
                //mmSocket2.connect();
            }
            catch (IOException connectException) {
                sendBroadcast(MainActivity.VIEW_STATUS, "Unable to connect. Closing socket... Please restart the application");
                this.cancel();
                //Runtime.getRuntime().exit(0); //closes the app
                //System.exit(0);
                return;
            }

            sendBroadcast(MainActivity.VIEW_STATUS, "Now Connected");
            //MainActivity.mToast("Now Connected", "long");
            showToast();
            //MainActivity.mToast.makeText(getApplication().getBaseContext(),"Now Connected", Toast.LENGTH_LONG).show();
            ///Toast.makeText(getApplicationContext(),"Now Connected",Toast.LENGTH_LONG).show();

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            try {
                manageMyConnectedSocket(mmSocket);
                //manageMyConnectedSocket2(mmSocket2);
            } catch (IOException e) {
                e.printStackTrace();
                sendBroadcast(MainActivity.VIEW_BLUETOOTH, "bno");
                //LayoutChange();
                //sendBroadcast(MainActivity.LAYOUT_LVON, null);
                sendBroadcast(MainActivity.VIEW_STATUS, "Lost Connection");
                //stopService(btIntent);
                isRunning = false;
               // MainActivity.isSerBT = false;
                this.cancel();
            }

        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
                //mmSocket2.close();
                MainActivity.btConnected = false;
                stopSelf();
            } catch (IOException e) {
                //Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    private Context appContext = getBaseContext();

    void showToast() {
        if(null != appContext){
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run()
                {
                    Toast.makeText(appContext, "Printing Toast Message", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    public static void Write_file(String[] arr, String filename, int n) {
        String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        String filePath = baseDir + File.separator + filename;
        File file = new File(filePath);
        try {
            // Check if the parent directory exists; if not, create it
            File parentDirectory = file.getParentFile();
            if (!parentDirectory.exists() && !parentDirectory.mkdirs()) {
                // Handle the case where directory creation fails
                throw new IOException("Failed to create parent directory: " + parentDirectory);
            }
            // Create or overwrite the file
            FileWriter writer = new FileWriter(file,true);
            /*/
            //for adding time stamp to each data input
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String timestampedData = LocalDateTime.now().format(formatter) + ":";
            writer.write(timestampedData);
            */
            // Write data to the file
            for (int i = 0; i < n-1; i++){
                writer.write(arr[i] + "/");
            }
            writer.write(arr[0] + "\n");

            // Close the FileWriter
            writer.close();
            System.out.println("File created or updated successfully.");
        } catch (IOException e) {
            e.printStackTrace();
            // Handle the exception
        }
    }
    private void manageMyConnectedSocket(BluetoothSocket mmSocket) throws IOException {
        MainActivity.btConnected = true;
        MainActivity.LVFlag = true;

        mSocket = mmSocket;
        mmInputStream = mSocket.getInputStream(); //受信ソケットオープン
        mmOutputStream = mSocket.getOutputStream(); //送信ソケットオープン

        // InputStreamのバッファを格納
        byte[] buffer = new byte[1024];

        // 取得したバッファのサイズを格納
        int bytes;

        if(MainActivity.isSleep) { //スリープ中であれば画面ON関数を実行
            WakeUp();
            MainActivity.isSleep = false;
        }

        SleepCount = 0;

        //情報受信フェーズ
        while (isRunning) {

            sendBroadcast(MainActivity.VIEW_BLUETOOTH, "bok"); //Bluetooth接続中画像ON
            //sendBroadcast(MainActivity.VIEW_STATUS, "Connected"); //デバック用

            // InputStreamの読み込み（受信情報の読み込み）
            bytes = mmInputStream.read(buffer);
            Log.i(TAG, "Receiving " + bytes + " bytes from " + DEVICE_NAME);
            //String型に変換
            String readMsg = new String(buffer, 0, bytes);

            sendBroadcast(MainActivity.VIEW_INPUT, readMsg.trim()); //デバック用
            //Set example

            //情報解析フェーズ
            // 情報が欠損なしで届いていれば解析　届く情報は A/(LV)/(HV)/(MT1)xMT2xMT3x(MT4)/(INV)/A B/(ERROR1),~,(ERROR4)/B C/(RTD1),~(RTD4)/(vcminfo)/C D/" + TSV + "/" + MAXCELLV + "/" + MINCELLV + "/" + ZYUUDEN + "/" + MAXCELLT + "/" + ERRORAMS + "/" + ERRORCOUNT + "/" + STATUSAMS + "/D"
            if (readMsg.trim().startsWith("A") && readMsg.trim().endsWith("A")) {
                try{
                    appendLog(readMsg.trim());
                    sendBroadcast(MainActivity.VIEW_INPUT, readMsg.trim()); //デバック用

                    objV = new NCMBObject("CLOUD"); //クラウド送信準備

                    String[] values;
                    values = readMsg.trim().split("/", 0);
                    int values_count = values.length;
                    //LV解析
                    try{
                        if (!values[MainActivity.VIEW_LV].contains("-")) {
                            //values[MainActivity.VIEW_LV] = "ON";

                            // 小数第一位以下を表示しないように文字列切り取り
                            if (Float.parseFloat(values[MainActivity.VIEW_LV]) < 30.0) {
                                if (values[MainActivity.VIEW_LV].length() >= 3) {
                                    values[MainActivity.VIEW_LV] = values[MainActivity.VIEW_LV].substring(0, 3);
                                }
                            }

                            else {
                                if (values[MainActivity.VIEW_LV].length() >= 4) {
                                    values[MainActivity.VIEW_LV] = values[MainActivity.VIEW_LV].substring(0, 4);
                                }
                            }
                            sendBroadcast(MainActivity.VIEW_LV, values[MainActivity.VIEW_LV]);
                            AddCloud("LV", values[MainActivity.VIEW_LV]); //クラウド送信情報として登録


                        }
                        else {
                            values[MainActivity.VIEW_LV] = "-----";
                        }
                        sendBroadcast(MainActivity.VIEW_LV, values[MainActivity.VIEW_LV]); //MainActivityに送信し文字列表示
                    }
                    catch(Exception e){
                        Log.w(TAG, "LV Error");
                    }

                    //HV解析
                    try{
                        if (!values[MainActivity.VIEW_HV].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_HV, values[MainActivity.VIEW_HV]);
                            AddCloud("HV", values[MainActivity.VIEW_HV]);
                            MainActivity.HVFlag = true; //HVONフラグをON
                        }
                        else {
                            MainActivity.HVFlag = false; //HVONフラグをOFF
                            values[MainActivity.VIEW_HV] = "-----";
                        }
                        sendBroadcast(MainActivity.VIEW_HV, values[MainActivity.VIEW_HV]);
                        MainActivity.HVFlag = !values[MainActivity.VIEW_HV].contains("-");
                    }
                    catch(Exception e){
                        Log.w(TAG, "HV Error");
                    }

                    int maxMT = 0;

                    //MOTOR温度解析
                    try{
                        String[] MTs;
                        float[] MTInt = new float[4];
                        final int indexNum=4;
                        MTs = values[MainActivity.VIEW_MT].trim().split("x", 0); //FR,FL,RR,RLの情報に分解

                        for (int n = 0; n < indexNum; n++) {
                            MTInt[n] = 0;
                            //Log.i(TAG, "mt index=" + n);
                            Log.i(TAG, "mt" + n + "=" + MTs[n]);

                            if(MTs[n].contains("-")){
                                MTInt[n] = 0;
                            }
                            else{
                                MTInt[n] = Float.valueOf(MTs[n]);
                            }

                            // 画面には四輪の内の最大値のみ表示する
                            if (MTInt[n] >= maxMT) {
                                maxMT = Math.round(MTInt[n]);
                            }

                            // クラウドには四輪とも送信
                            switch (n) {
                                case 0:
                                    AddCloud("MOTOR1", MTs[0]);
                                    break;
                                case 1:
                                    AddCloud("MOTOR2", MTs[1]);
                                    break;
                                case 2:
                                    AddCloud("MOTOR3", MTs[2]);
                                    break;
                                case 3:
                                    AddCloud("MOTOR4", MTs[3]);
                                    break;
                            }
                        }

                        if (maxMT == 0) {
                            sendBroadcast(MainActivity.VIEW_MT, "-----");
                        }
                        else {
                            sendBroadcast(MainActivity.VIEW_MT, Integer.toString(maxMT));
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG, "A - MOTOR Error");
                        e.printStackTrace();
                    }

                    //INV温度解析
                    try{
                        if (!values[MainActivity.VIEW_INV].contains("-")) {
                            values[MainActivity.VIEW_INV] = values[MainActivity.VIEW_INV].split("\\.", 0)[0]; //整数にする
                            AddCloud("INV", values[MainActivity.VIEW_INV]);
                        }
                        else {
                            values[MainActivity.VIEW_INV] = "-----";
                        }
                        sendBroadcast(MainActivity.VIEW_INV, values[MainActivity.VIEW_INV]);
                    }
                    catch(Exception e){
                        Log.w(TAG,"INV Error");
                    }



                    // クラウドのデータストアへの登録
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

                    if(maxMT >= 115){ //高温判定
                        if(!(MainActivity.isHITEMP)) { //高温表示
                            sendBroadcast(MainActivity.LAYOUT_HITEMP, "YES");
                            MainActivity.isHITEMP = true;
                        }
                    }
                    else if(!values[MainActivity.VIEW_INV].contains("-")){
                        if(Integer.parseInt(values[MainActivity.VIEW_INV]) >= 40){
                            if(!(MainActivity.isHITEMP)) { //高温表示
                                sendBroadcast(MainActivity.LAYOUT_HITEMP, "YES");
                                MainActivity.isHITEMP = true;
                            }
                        }
                        else{
                            if(MainActivity.isHITEMP){ //高温非表示
                                sendBroadcast(MainActivity.LAYOUT_HITEMP, "NO");
                                MainActivity.isHITEMP = false;
                            }
                        }
                    }
                    else{
                        if(MainActivity.isHITEMP){ //高温非表示
                            sendBroadcast(MainActivity.LAYOUT_HITEMP, "NO");
                            MainActivity.isHITEMP = false;
                        }
                    }
                    Write_file(values, "data_log.csv", values_count);
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }

            else if (readMsg.trim().startsWith("B") && readMsg.trim().endsWith("B")) { //届く情報は B/(ERROR0),(INFO0),~,(INFO2)x~x(ERROR3)/B
                /*
                try{
                    appendLog(readMsg.trim());
                    sendBroadcast(MainActivity.VIEW_INPUT, readMsg.trim());

                    objV = new NCMBObject("CLOUD");
                    //sendBroadcast(VIEW_STATUS, " ");

                    String[] values;

                    values = readMsg.trim().split("/", 0);

                    //ERROR解析
                    String[] ERRORs;
                    ERRORs = values[MainActivity.VIEW_ERR - 5].split("x", 0);
                    MainActivity.ERRFlag =  false;//((!ERRORs[0].startsWith("-")&&!ERRORs[0].startsWith("d0") )|| (!ERRORs[1].startsWith("-")&&!ERRORs[1].startsWith("d0") )|| (!ERRORs[2].startsWith("-")&&!ERRORs[2].startsWith("d0")) || (!ERRORs[3].startsWith("-")&&!ERRORs[3].startsWith("d0")));//d0を一時的に回避(長)
                    for (int n = 0; n < 4; n++) {
                        String[] s_err;
                        s_err = ERRORs[n].split(",", 0);
                        //ERRORs[n] = "Error:" + s_err[0] + " Info:[1]" + s_err[1] + " [2]" + s_err[2] + " [3]" + s_err[3];
                        if (s_err[0].contains("2310")) { //特定の情報(2310,d1等)はエラーの数字だけでなくその内容も表示
                            ERRORs[n] += "\nEncoder communication";
                        } else if (s_err[0].contains("3587") && !(s_err[1].contains("-"))) { //エラー番号が3587かつエラーインフォ1に情報があれば実行
                            ERRORs[n] += "\nError during operation";
                        } else if (s_err[0].contains("3587")) {
                            ERRORs[n] += "\nTemperature cabinet too high";
                        } else if (s_err[0].contains("d1")) {
                            ERRORs[n] += "\n出力制限① インバータ温度50℃↑";
                        } else if (s_err[0].contains("d2")) {
                            ERRORs[n] += "\n出力制限② モータ温度125℃↑";
                        } else if (s_err[0].contains("d3")) {
                            ERRORs[n] += "\n出力制限③ IGBT温度115℃↑";
                        } else if (s_err[0].contains("d4")) {
                            ERRORs[n] += "\n出力制限④ HV250V↓";
                        } else if (s_err[0].contains("d5")) {
                            ERRORs[n] += "\n出力制限⑤ HV720V↑";
                        } else if (s_err[0].contains("d0")) {
                            ERRORs[n] += "\n出力制限";
                        }
                    }
                    sendBroadcast(MainActivity.VIEW_ERRFR, ERRORs[0]);
                    sendBroadcast(MainActivity.VIEW_ERRFL, ERRORs[1]);
                    sendBroadcast(MainActivity.VIEW_ERRRR, ERRORs[2]);
                    sendBroadcast(MainActivity.VIEW_ERRRL, ERRORs[3]);
                    if (MainActivity.ERRFlag) { //ERRORモードの時のみクラウドに送信
                        AddCloud("ERROR1", ERRORs[0]);
                        AddCloud("ERROR2", ERRORs[1]);
                        AddCloud("ERROR3", ERRORs[2]);
                        AddCloud("ERROR4", ERRORs[3]);
                    } else {
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
                catch(Exception e){
                    e.printStackTrace();
                }
            */
            }


            else if (readMsg.trim().startsWith("C") && readMsg.trim().endsWith("C")) {
                try{
                    appendLog(readMsg.trim());
                    sendBroadcast(MainActivity.VIEW_INPUT, readMsg.trim());

                    objV = new NCMBObject("CLOUD");

                    String[] values;

                    values = readMsg.trim().split("/", 0);
                    int values_count = values.length;
                    //RTD解析
                    String[] RTDs;
                    RTDs = values[MainActivity.VIEW_RTD - 4].trim().split("x", 0);
                    String result_RTD;
                    String RTDCode;
                    RTDCode = RTDs[0]+RTDs[1]+RTDs[2]+RTDs[3];
                    Log.d(TAG, RTDCode);
                    if (Integer.parseInt(RTDCode) == 1111) { //四輪のうち一つでもONであれば実行
                        result_RTD = "RTD";
                        Log.d(TAG, "THIS HAS BEEN RUN" + RTDCode);
                        if (!(MainActivity.RtDFlag) && !MainActivity.RtDSounded) { //RtDがOFF→ONのとき効果音を鳴らす
                            soundPool.play(RtDsound, 1f, 1f, 0, 0, 1f);
                            MainActivity.RtDFlag = true;
                            MainActivity.RtDOn = true;
                            MainActivity.RtDSounded = true;
                        }
                    }
                    else if (RTDs[0].contains("1") || RTDs[1].contains("1") || RTDs[2].contains("1") || RTDs[3].contains("1")) { //四輪のうち一つでもONであれば実行
                        result_RTD = "RTD";
                        MainActivity.RtDOn = true;
                        MainActivity.RtDFlag = false; //added on 8172023
                    }
                    else { //四輪ともOFFのとき実行
                        result_RTD = "---";
                        if (MainActivity.RtDFlag) {
                            MainActivity.RtDFlag = false;
                        }
                        if(MainActivity.RtDOn){
                            MainActivity.RtDOn = false;
                        }
                    }
                    sendBroadcast(MainActivity.CHECK_RTOD, RTDCode);
                    sendBroadcast(MainActivity.VIEW_RTD, result_RTD);
                    AddCloud("RtD1", RTDs[0]);
                    AddCloud("RtD2", RTDs[1]);
                    AddCloud("RtD3", RTDs[2]);
                    AddCloud("RtD4", RTDs[3]);


                    if (!values[MainActivity.VIEW_VCMINFO-9].contains("-")) {
                        //VCMから両踏みの情報を得る
                        sendBroadcast(MainActivity.VIEW_VCMINFO, values[2]);
                        //BOR解析
                        if (values[MainActivity.VIEW_VCMINFO-9].contains("1")) {
                            AddCloud("BrOvRi", "1");
                            MainActivity.BORFlag = false;
                        } else if (values[MainActivity.VIEW_VCMINFO-9].contains("0")) {
                            AddCloud("BrOvRi", "0");
                            MainActivity.BORFlag = false;
                        }
                    }
                    if (!values[MainActivity.VIEW_VELO-4].contains("-")) {
                        /*
                        if (values[MainActivity.VIEW_VELO].length() >= 3) {
                            values[MainActivity.VIEW_VELO] = values[MainActivity.VIEW_VELO].substring(0, 3);
                        }
*/
                        sendBroadcast(MainActivity.VIEW_VELO, values[3]);
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
                    Write_file(values, "data_log.csv", values_count);
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }
            else if (readMsg.trim().startsWith("D") && readMsg.trim().endsWith("D")) { //届く情報は D/" + TSV + "/" + MAXCELLV + "/" + MINCELLV + "/" + ZYUUDEN + "/" + MAXCELLT + "/" + ERRORAMS + "/" + ERRORCOUNT + "/" + STATUSAMS + "/D";
                try{
                    appendLog(readMsg.trim());
                    sendBroadcast(MainActivity.VIEW_INPUT, readMsg.trim());

                    objV = new NCMBObject("CLOUD");

                    String[] values;

                    values = readMsg.trim().split("/", 0);
                    int values_count = values.length;
                    try{
                        if (!values[MainActivity.VIEW_TSV-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_TSV, values[1]);
                            AddCloud("TSV", values[1]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_TSV Error");
                    }

                    try{
                        if (!values[MainActivity.VIEW_MAXCELLV-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_MAXCELLV, values[2]);
                            AddCloud("MAXCELLV", values[2]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_MAXCELLV Error");
                    }

                    try{
                        if (!values[MainActivity.VIEW_MINCELLV-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_MINCELLV, values[3]);
                            AddCloud("MINCELLV", values[3]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_MINCELLV Error");
                    }

                    //JUUDEN
                    /*
                    try{
                        if (!values[MainActivity.VIEW_ZYUUDEN-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_ZYUUDEN, values[4]);
                            if (Integer.valueOf(values[4]) >= 50) {
                                sendBroadcast(MainActivity.VIEW_GREEN, null);
                            }
                            else if (Integer.valueOf(values[4]) >= 30){
                                sendBroadcast(MainActivity.VIEW_LIGHTGREEN, null);
                            }
                            else if (Integer.valueOf(values[4]) >= 10){
                                sendBroadcast(MainActivity.VIEW_YELLOW, null);
                            }
                            else if (Integer.valueOf(values[4]) < 10){
                                sendBroadcast(MainActivity.VIEW_RED, null);
                            }
                            AddCloud("ZYUUDEN", values[4]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_ZYUUDEN Error");
                    }
                    */

                    try{
                        if (!values[MainActivity.VIEW_MAXCELLT-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_MAXCELLT, values[5]);
                            AddCloud("MAXCELLT", values[5]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_MAXCELLT Error");
                    }

                    try{
                        if (!values[MainActivity.VIEW_ERRORAMS-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_ERRORAMS, values[6]);
                            AddCloud("ERRORAMS", values[6]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_ERRORAMS Error");
                    }

                    try{
                        if (!values[MainActivity.VIEW_ERRORCOUNT-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_ERRORCOUNT, values[7]);
                            AddCloud("ERRORCOUNT", values[7]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_ERRORCOUNT Error");
                    }

                    try{
                        if (!values[MainActivity.VIEW_STATUSAMS-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_STATUSAMS, values[8]);
                            AddCloud("STATUSAMS", values[8]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_STATUSAMS Error");
                    }

                    try {
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
                    catch(Exception e){
                        e.printStackTrace();
                    }
                    Write_file(values, "data_log.csv", values_count);
                }
                catch(Exception e){
                    e.printStackTrace();
                }

            }

            //レイアウト変更フェーズ
            LayoutChange();
        }

    }/*
    private void manageMyConnectedSocket2(BluetoothSocket mmSocket) throws IOException {
        MainActivity.btConnected = true;
        mSocket = mmSocket;
        //mmInputStream2 = mSocket.getInputStream(); //受信ソケットオープン
        //mmOutputStream2 = mSocket.getOutputStream(); //送信ソケットオープン

        // InputStreamのバッファを格納
        byte[] buffer = new byte[1024];

        // 取得したバッファのサイズを格納
        int bytes;

        if(MainActivity.isSleep) { //スリープ中であれば画面ON関数を実行
            WakeUp();
            MainActivity.isSleep = false;
        }

        SleepCount = 0;

        //情報受信フェーズ
        while (isRunning) {

            sendBroadcast(MainActivity.VIEW_BLUETOOTH, "bok"); //Bluetooth接続中画像ON
            //sendBroadcast(MainActivity.VIEW_STATUS, "Connected"); //デバック用

            // InputStreamの読み込み（受信情報の読み込み）
            //bytes = mmInputStream2.read(buffer);
            //Log.i(TAG, "Receiving " + bytes + " bytes from " + DEVICE_NAME);
            //String型に変換
            String readMsg = new String(buffer, 0, bytes);

            sendBroadcast(MainActivity.VIEW_INPUT, readMsg.trim()); //デバック用

            //情報解析フェーズ
            // 情報が欠損なしで届いていれば解析　届く情報は A/(LV)/(HV)/(MT1)xMT2xMT3x(MT4)/(INV)/A B/(ERROR1),~,(ERROR4)/B C/(RTD1),~(RTD4)/(vcminfo)/C D/" + TSV + "/" + MAXCELLV + "/" + MINCELLV + "/" + ZYUUDEN + "/" + MAXCELLT + "/" + ERRORAMS + "/" + ERRORCOUNT + "/" + STATUSAMS + "/D"
            if (readMsg.trim().startsWith("A") && readMsg.trim().endsWith("A")) {
                try{
                    appendLog(readMsg.trim());
                    sendBroadcast(MainActivity.VIEW_INPUT, readMsg.trim()); //デバック用

                    objV = new NCMBObject("CLOUD"); //クラウド送信準備

                    String[] values;
                    values = readMsg.trim().split("/", 0);

                    //LV解析
                    try{
                        if (!values[MainActivity.VIEW_LV].contains("-")) {
                            // 小数第一位以下を表示しないように文字列切り取り
                            if (Float.parseFloat(values[MainActivity.VIEW_LV]) < 30.0) {
                                if (values[MainActivity.VIEW_LV].length() >= 3) {
                                    values[MainActivity.VIEW_LV] = values[MainActivity.VIEW_LV].substring(0, 3);
                                }
                            }

                            else {
                                if (values[MainActivity.VIEW_LV].length() >= 4) {
                                    values[MainActivity.VIEW_LV] = values[MainActivity.VIEW_LV].substring(0, 4);
                                }
                            }
                            sendBroadcast(MainActivity.VIEW_LV, values[MainActivity.VIEW_LV]);
                            AddCloud("LV", values[MainActivity.VIEW_LV]); //クラウド送信情報として登録
                        }
                        else {
                            values[MainActivity.VIEW_LV] = "-----";
                        }
                        sendBroadcast(MainActivity.VIEW_LV, values[MainActivity.VIEW_LV]); //MainActivityに送信し文字列表示
                    }
                    catch(Exception e){
                        Log.w(TAG, "LV Error");
                    }

                    //HV解析
                    try{
                        if (!values[MainActivity.VIEW_HV].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_HV, values[MainActivity.VIEW_HV]);
                            AddCloud("HV", values[MainActivity.VIEW_HV]);
                            MainActivity.HVFlag = true; //HVONフラグをON
                        }
                        else {
                            MainActivity.HVFlag = false; //HVONフラグをOFF
                            values[MainActivity.VIEW_HV] = "-----";
                        }
                        sendBroadcast(MainActivity.VIEW_HV, values[MainActivity.VIEW_HV]);
                        MainActivity.HVFlag = !values[MainActivity.VIEW_HV].contains("-");
                    }
                    catch(Exception e){
                        Log.w(TAG, "HV Error");
                    }

                    int maxMT = 0;

                    //MOTOR温度解析
                    try{
                        String[] MTs;
                        float[] MTInt = new float[4];
                        final int indexNum=4;
                        MTs = values[MainActivity.VIEW_MT].trim().split("x", 0); //FR,FL,RR,RLの情報に分解

                        for (int n = 0; n < indexNum; n++) {
                            MTInt[n] = 0;
                            //Log.i(TAG, "mt index=" + n);
                            Log.i(TAG, "mt" + n + "=" + MTs[n]);

                            if(MTs[n].contains("-")){
                                MTInt[n] = 0;
                            }
                            else{
                                MTInt[n] = Float.valueOf(MTs[n]);
                            }

                            // 画面には四輪の内の最大値のみ表示する
                            if (MTInt[n] >= maxMT) {
                                maxMT = Math.round(MTInt[n]);
                            }

                            // クラウドには四輪とも送信
                            switch (n) {
                                case 0:
                                    AddCloud("MOTOR1", MTs[0]);
                                    break;
                                case 1:
                                    AddCloud("MOTOR2", MTs[1]);
                                    break;
                                case 2:
                                    AddCloud("MOTOR3", MTs[2]);
                                    break;
                                case 3:
                                    AddCloud("MOTOR4", MTs[3]);
                                    break;
                            }
                        }

                        if (maxMT == 0) {
                            sendBroadcast(MainActivity.VIEW_MT, "-----");
                        }
                        else {
                            sendBroadcast(MainActivity.VIEW_MT, Integer.toString(maxMT));
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG, "A - MOTOR Error");
                        e.printStackTrace();
                    }

                    //INV温度解析
                    try{
                        if (!values[MainActivity.VIEW_INV].contains("-")) {
                            values[MainActivity.VIEW_INV] = values[MainActivity.VIEW_INV].split("\\.", 0)[0]; //整数にする
                            AddCloud("INV", values[MainActivity.VIEW_INV]);
                        }
                        else {
                            values[MainActivity.VIEW_INV] = "-----";
                        }
                        sendBroadcast(MainActivity.VIEW_INV, values[MainActivity.VIEW_INV]);
                    }
                    catch(Exception e){
                        Log.w(TAG,"INV Error");
                    }



                    // クラウドのデータストアへの登録
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

                    if(maxMT >= 115){ //高温判定
                        if(!(MainActivity.isHITEMP)) { //高温表示
                            sendBroadcast(MainActivity.LAYOUT_HITEMP, "YES");
                            MainActivity.isHITEMP = true;
                        }
                    }
                    else if(!values[MainActivity.VIEW_INV].contains("-")){
                        if(Integer.parseInt(values[MainActivity.VIEW_INV]) >= 40){
                            if(!(MainActivity.isHITEMP)) { //高温表示
                                sendBroadcast(MainActivity.LAYOUT_HITEMP, "YES");
                                MainActivity.isHITEMP = true;
                            }
                        }
                        else{
                            if(MainActivity.isHITEMP){ //高温非表示
                                sendBroadcast(MainActivity.LAYOUT_HITEMP, "NO");
                                MainActivity.isHITEMP = false;
                            }
                        }
                    }
                    else{
                        if(MainActivity.isHITEMP){ //高温非表示
                            sendBroadcast(MainActivity.LAYOUT_HITEMP, "NO");
                            MainActivity.isHITEMP = false;
                        }
                    }
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }

            else if (readMsg.trim().startsWith("B") && readMsg.trim().endsWith("B")) { //届く情報は B/(ERROR0),(INFO0),~,(INFO2)x~x(ERROR3)/B
                try{
                    sendBroadcast(MainActivity.VIEW_INPUT, readMsg.trim());

                    objV = new NCMBObject("CLOUD");

                    String[] values;

                    values = readMsg.trim().split("/",0);

                    //first value is "B"
                    String[] Errors;

                    Errors = values[MainActivity.VIEW_ERR - 5].split("x", 0);

                }
                catch(Exception e){
                    e.printStackTrace();
                }

                /*
                try{
                    appendLog(readMsg.trim());
                    sendBroadcast(MainActivity.VIEW_INPUT, readMsg.trim());

                    objV = new NCMBObject("CLOUD");
                    //sendBroadcast(VIEW_STATUS, " ");

                    String[] values;

                    values = readMsg.trim().split("/", 0);

                    //ERROR解析
                    String[] ERRORs;
                    ERRORs = values[MainActivity.VIEW_ERR - 5].split("x", 0);
                    MainActivity.ERRFlag =  false;//((!ERRORs[0].startsWith("-")&&!ERRORs[0].startsWith("d0") )|| (!ERRORs[1].startsWith("-")&&!ERRORs[1].startsWith("d0") )|| (!ERRORs[2].startsWith("-")&&!ERRORs[2].startsWith("d0")) || (!ERRORs[3].startsWith("-")&&!ERRORs[3].startsWith("d0")));//d0を一時的に回避(長)
                    for (int n = 0; n < 4; n++) {
                        String[] s_err;
                        s_err = ERRORs[n].split(",", 0);
                        //ERRORs[n] = "Error:" + s_err[0] + " Info:[1]" + s_err[1] + " [2]" + s_err[2] + " [3]" + s_err[3];
                        if (s_err[0].contains("2310")) { //特定の情報(2310,d1等)はエラーの数字だけでなくその内容も表示
                            ERRORs[n] += "\nEncoder communication";
                        } else if (s_err[0].contains("3587") && !(s_err[1].contains("-"))) { //エラー番号が3587かつエラーインフォ1に情報があれば実行
                            ERRORs[n] += "\nError during operation";
                        } else if (s_err[0].contains("3587")) {
                            ERRORs[n] += "\nTemperature cabinet too high";
                        } else if (s_err[0].contains("d1")) {
                            ERRORs[n] += "\n出力制限① インバータ温度50℃↑";
                        } else if (s_err[0].contains("d2")) {
                            ERRORs[n] += "\n出力制限② モータ温度125℃↑";
                        } else if (s_err[0].contains("d3")) {
                            ERRORs[n] += "\n出力制限③ IGBT温度115℃↑";
                        } else if (s_err[0].contains("d4")) {
                            ERRORs[n] += "\n出力制限④ HV250V↓";
                        } else if (s_err[0].contains("d5")) {
                            ERRORs[n] += "\n出力制限⑤ HV720V↑";
                        } else if (s_err[0].contains("d0")) {
                            ERRORs[n] += "\n出力制限";
                        }
                    }
                    sendBroadcast(MainActivity.VIEW_ERRFR, ERRORs[0]);
                    sendBroadcast(MainActivity.VIEW_ERRFL, ERRORs[1]);
                    sendBroadcast(MainActivity.VIEW_ERRRR, ERRORs[2]);
                    sendBroadcast(MainActivity.VIEW_ERRRL, ERRORs[3]);
                    if (MainActivity.ERRFlag) { //ERRORモードの時のみクラウドに送信
                        AddCloud("ERROR1", ERRORs[0]);
                        AddCloud("ERROR2", ERRORs[1]);
                        AddCloud("ERROR3", ERRORs[2]);
                        AddCloud("ERROR4", ERRORs[3]);
                    } else {
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
                catch(Exception e){
                    e.printStackTrace();
                }

            }


            else if (readMsg.trim().startsWith("C") && readMsg.trim().endsWith("C")) {
                try{
                    appendLog(readMsg.trim());
                    sendBroadcast(MainActivity.VIEW_INPUT, readMsg.trim());

                    objV = new NCMBObject("CLOUD");

                    String[] values;

                    values = readMsg.trim().split("/", 0);

                    //RTD解析
                    String[] RTDs;
                    RTDs = values[MainActivity.VIEW_RTD - 4].trim().split("x", 0);
                    String result_RTD;
                    String RTDCode;
                    RTDCode = RTDs[0]+RTDs[1]+RTDs[2]+RTDs[3];
                    Log.d(TAG, RTDCode);
                    if (Integer.parseInt(RTDCode) == 1111) { //四輪のうち一つでもONであれば実行
                        result_RTD = "RTD";
                        Log.d(TAG, "THIS HAS BEEN RUN" + RTDCode);
                        if (!(MainActivity.RtDFlag)) { //RtDがOFF→ONのとき効果音を鳴らす
                            soundPool.play(RtDsound, 1f, 1f, 0, 0, 1f);
                            MainActivity.RtDFlag = true;
                            MainActivity.RtDOn = true;

                        }
                    }
                    else if (RTDs[0].contains("1") || RTDs[1].contains("1") || RTDs[2].contains("1") || RTDs[3].contains("1")) { //四輪のうち一つでもONであれば実行
                        result_RTD = "RTD";
                        MainActivity.RtDOn = true;
                        MainActivity.RtDFlag = false; //added on 8172023
                    }
                    else { //四輪ともOFFのとき実行
                        result_RTD = "---";
                        if (MainActivity.RtDFlag) {
                            MainActivity.RtDFlag = false;
                        }
                        if(MainActivity.RtDOn){
                            MainActivity.RtDOn = false;
                        }
                    }
                    sendBroadcast(MainActivity.CHECK_RTOD, RTDCode);
                    sendBroadcast(MainActivity.VIEW_RTD, result_RTD);
                    AddCloud("RtD1", RTDs[0]);
                    AddCloud("RtD2", RTDs[1]);
                    AddCloud("RtD3", RTDs[2]);
                    AddCloud("RtD4", RTDs[3]);


                    if (!values[MainActivity.VIEW_VCMINFO-9].contains("-")) {
                        //VCMから両踏みの情報を得る
                        sendBroadcast(MainActivity.VIEW_VCMINFO, values[2]);
                        //BOR解析
                        if (values[MainActivity.VIEW_VCMINFO-9].contains("1")) {
                            AddCloud("BrOvRi", "1");
                            MainActivity.BORFlag = false;
                        } else if (values[MainActivity.VIEW_VCMINFO-9].contains("0")) {
                            AddCloud("BrOvRi", "0");
                            MainActivity.BORFlag = false;
                        }
                    }
                    if (!values[MainActivity.VIEW_VELO-4].contains("-")) {
                        if (values[MainActivity.VIEW_VELO].length() >= 3) {
                            values[MainActivity.VIEW_VELO] = values[MainActivity.VIEW_VELO].substring(0, 3);
                        }
                        sendBroadcast(MainActivity.VIEW_VELO, values[3]);
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
                catch(Exception e){
                    e.printStackTrace();
                }
            }

            /*
            else if (readMsg.trim().startsWith("Batt") && readMsg.trim().endsWith("Batt")) {
                /*Log.w(TAG, "AMSBT is on");
                try{
                    sendBroadcast(MainActivity.VIEW_INPUT, readMsg.trim());

                    objV = new NCMBObject("CLOUD");

                    String[] values;

                    values = readMsg.trim().split("/", 0);

                    String batteryLevel = values[1];
                    String minCellVoltage = values[2];
                    String CellTemp1 = values[3];
                    String CellTemp2 = values[4];



                    sendBroadcast(MainActivity.VIEW_ZYUUDEN, values[1]);
                    sendBroadcast(MainActivity.VIEW_VELO, values[2]);
                    sendBroadcast(MainActivity.VIEW_INV, values[3]);
                    sendBroadcast(MainActivity.VIEW_MT, values[4]);
                    /*
                    //RTD解析
                    String[] RTDs;
                    RTDs = values[MainActivity.VIEW_RTD - 4].trim().split("x", 0);
                    String result_RTD;
                    String RTDCode;
                    RTDCode = RTDs[0]+RTDs[1]+RTDs[2]+RTDs[3];
                    Log.d(TAG, RTDCode);
                    if (Integer.parseInt(RTDCode) == 1111) { //四輪のうち一つでもONであれば実行
                        result_RTD = "RTD";
                        Log.d(TAG, "THIS HAS BEEN RUN" + RTDCode);
                        if (!(MainActivity.RtDFlag)) { //RtDがOFF→ONのとき効果音を鳴らす
                            soundPool.play(RtDsound, 1f, 1f, 0, 0, 1f);
                            MainActivity.RtDFlag = true;
                            MainActivity.RtDOn = true;

                        }
                    }
                    else if (RTDs[0].contains("1") || RTDs[1].contains("1") || RTDs[2].contains("1") || RTDs[3].contains("1")) { //四輪のうち一つでもONであれば実行
                        result_RTD = "RTD";
                        MainActivity.RtDOn = true;
                        MainActivity.RtDFlag = false; //added on 8172023
                    }
                    else { //四輪ともOFFのとき実行
                        result_RTD = "---";
                        if (MainActivity.RtDFlag) {
                            MainActivity.RtDFlag = false;
                        }
                        if(MainActivity.RtDOn){
                            MainActivity.RtDOn = false;
                        }
                    }
                    sendBroadcast(MainActivity.CHECK_RTOD, RTDCode);
                    sendBroadcast(MainActivity.VIEW_RTD, result_RTD);
                    AddCloud("RtD1", RTDs[0]);
                    AddCloud("RtD2", RTDs[1]);
                    AddCloud("RtD3", RTDs[2]);
                    AddCloud("RtD4", RTDs[3]);


                    if (!values[MainActivity.VIEW_VCMINFO-9].contains("-")) {
                        //VCMから両踏みの情報を得る
                        sendBroadcast(MainActivity.VIEW_VCMINFO, values[2]);
                        //BOR解析
                        if (values[MainActivity.VIEW_VCMINFO-9].contains("1")) {
                            AddCloud("BrOvRi", "1");
                            MainActivity.BORFlag = false;
                        } else if (values[MainActivity.VIEW_VCMINFO-9].contains("0")) {
                            AddCloud("BrOvRi", "0");
                            MainActivity.BORFlag = false;
                        }
                    }
                   /* if (!values[MainActivity.VIEW_VELO-4].contains("-")) {
                        /*
                        if (values[MainActivity.VIEW_VELO].length() >= 3) {
                            values[MainActivity.VIEW_VELO] = values[MainActivity.VIEW_VELO].substring(0, 3);
                        }

                        sendBroadcast(MainActivity.VIEW_VELO, values[3]);
                    }
                    /*
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

                catch(Exception e){
                    e.printStackTrace();
                }
            }

            else if (readMsg.trim().startsWith("D") && readMsg.trim().endsWith("D")) { //届く情報は D/" + TSV + "/" + MAXCELLV + "/" + MINCELLV + "/" + ZYUUDEN + "/" + MAXCELLT + "/" + ERRORAMS + "/" + ERRORCOUNT + "/" + STATUSAMS + "/D";
                try{
                    appendLog(readMsg.trim());
                    sendBroadcast(MainActivity.VIEW_INPUT, readMsg.trim());

                    objV = new NCMBObject("CLOUD");

                    String[] values;

                    values = readMsg.trim().split("/", 0);

                    try{
                        if (!values[MainActivity.VIEW_TSV-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_TSV, values[1]);
                            AddCloud("TSV", values[1]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_TSV Error");
                    }

                    try{
                        if (!values[MainActivity.VIEW_MAXCELLV-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_MAXCELLV, values[2]);
                            AddCloud("MAXCELLV", values[2]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_MAXCELLV Error");
                    }

                    try{
                        if (!values[MainActivity.VIEW_MINCELLV-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_MINCELLV, values[3]);
                            AddCloud("MINCELLV", values[3]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_MINCELLV Error");
                    }

                    //JUUDEN
                    /*
                    try{
                        if (!values[MainActivity.VIEW_ZYUUDEN-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_ZYUUDEN, values[4]);
                            if (Integer.valueOf(values[4]) >= 50) {
                                sendBroadcast(MainActivity.VIEW_GREEN, null);
                            }
                            else if (Integer.valueOf(values[4]) >= 30){
                                sendBroadcast(MainActivity.VIEW_LIGHTGREEN, null);
                            }
                            else if (Integer.valueOf(values[4]) >= 10){
                                sendBroadcast(MainActivity.VIEW_YELLOW, null);
                            }
                            else if (Integer.valueOf(values[4]) < 10){
                                sendBroadcast(MainActivity.VIEW_RED, null);
                            }
                            AddCloud("ZYUUDEN", values[4]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_ZYUUDEN Error");
                    }
                    *//*

                    try{
                        if (!values[MainActivity.VIEW_MAXCELLT-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_MAXCELLT, values[5]);
                            AddCloud("MAXCELLT", values[5]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_MAXCELLT Error");
                    }

                    try{
                        if (!values[MainActivity.VIEW_ERRORAMS-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_ERRORAMS, values[6]);
                            AddCloud("ERRORAMS", values[6]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_ERRORAMS Error");
                    }

                    try{
                        if (!values[MainActivity.VIEW_ERRORCOUNT-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_ERRORCOUNT, values[7]);
                            AddCloud("ERRORCOUNT", values[7]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_ERRORCOUNT Error");
                    }

                    try{
                        if (!values[MainActivity.VIEW_STATUSAMS-11].contains("-")) {
                            sendBroadcast(MainActivity.VIEW_STATUSAMS, values[8]);
                            AddCloud("STATUSAMS", values[8]);
                        }
                    }
                    catch(Exception e){
                        Log.w(TAG,"VIEW_STATUSAMS Error");
                    }

                    try {
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
                    catch(Exception e){
                        e.printStackTrace();
                    }
                }
                catch(Exception e){
                    e.printStackTrace();
                }

            }

            //レイアウト変更フェーズ
            LayoutChange();
        }

    }
    */

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        MainActivity.btServiceOn = false;
        MainActivity.btConnected = false;
        MainActivity.RtDOn = false;
        MainActivity.RtDFlag = false;
        sendBroadcast(MainActivity.LAYOUT_DEFAULT,null);
        try{
            Thread.sleep(250);
        }
        catch(Exception e){
            e.printStackTrace();
        }
        stopSelf();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    //クラウド送信メッセージ登録
    private void AddCloud(String INFO, String MSG) {
        // オブジェクトの値を設定
        try {
            objV.put(INFO, MSG);
        } catch (NCMBException e) {
            e.printStackTrace();
        }
    }

    //レイアウト変更メソッド
    public void LayoutChange(){
        if(MainActivity.LVFlag && !MainActivity.HVFlag && !MainActivity.RtDOn && !MainActivity.RtDFlag){
            if(!(MainActivity.NowLayout == MainActivity.LVON)) {
                sendBroadcast(MainActivity.LAYOUT_LVON, null);
                Log.d(TAG, "LayoutChange() called to lvon");
            }
        }
        if(MainActivity.ERRFlag){
            if(!(MainActivity.NowLayout == MainActivity.ERR)) {
                //ERRモードに遷移
                sendBroadcast(MainActivity.LAYOUT_ERR, null);
            }
        }
        else if(MainActivity.BORFlag){
            if(!(MainActivity.NowLayout == MainActivity.BOR)) {
                //BORモードに遷移
                sendBroadcast(MainActivity.LAYOUT_BOR, null);
            }
        }
        else if((MainActivity.HVFlag && MainActivity.RtDOn) && !(MainActivity.RtDFlag)){
            if(!(MainActivity.NowLayout == MainActivity.RTD)) {
                //RTDモードに遷移
                sendBroadcast(MainActivity.LAYOUT_RTD, null);
                Log.d(TAG, "LayoutChange() called to rtod");
            }
            Log.d(TAG, String.valueOf(MainActivity.RtDFlag));
        }
        else if(MainActivity.RtDFlag && MainActivity.RtDOn){
            if(!(MainActivity.NowLayout == MainActivity.DRIVE)) {
                //RTDモードに遷移
                sendBroadcast(MainActivity.LAYOUT_DRIVE, null);
                Log.d(TAG, "LayoutChange() called to soukou");
            }
        }
        /*else if(MainActivity.HVFlag){
            if(!(MainActivity.NowLayout == MainActivity.HVON)) {
                //HVONモードに遷移
                sendBroadcast(MainActivity.LAYOUT_HVON, null);
            }
        }*/
        else{
            if(!(MainActivity.NowLayout == MainActivity.DEFAULT) && !MainActivity.btConnected) {
                //LVONモードに遷移
             sendBroadcast(MainActivity.LAYOUT_DEFAULT, null);
            }
            Log.d(TAG, "LayoutChange() called default");
        }
    }

    //works well
    private void sendBroadcast(int VIEW, String message) {
        // IntentをブロードキャストすることでMainActivityへデータを送信
        Intent intent = new Intent();
        intent.setAction("BLUETOOTH");
        intent.putExtra("VIEW", VIEW);
        intent.putExtra("message", message);
        getBaseContext().sendBroadcast(intent);
    }

    private void WakeUp() throws IOException {
        //画面点灯処理
        pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakelock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.ON_AFTER_RELEASE, "myapp:Your App Tag");
        wakelock.acquire();
        isWakelock = true;
    }
    
    public void appendLog(String text){
        File logFile = new File(getApplicationContext().getFilesDir(), "mydir");
        if (!logFile.exists()){
            logFile.mkdir();
        }
        try{
            File gpxFile = new File(logFile, "telemetrylog.txt");
            FileWriter writer = new FileWriter(gpxFile);
            writer.append(text);
            writer.flush();
            writer.close();
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }
}
