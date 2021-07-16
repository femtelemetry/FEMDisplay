package com.fem.fem17display;

import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import static com.fem.fem17display.AsyncHttpRequest.json;
import static com.fem.fem17display.MainActivity.*;

public class HTTPSRequest  extends Service {
    //Loaderを識別する為のID
    private AsyncHttpRequest httpreq;
    private static final String TAG = "Async";
    private static String lastID = "";
    private static int sleepcount = 0;

    //RtD音用
    SoundPool soundPool;
    int RtDsound;

    //スリープ関連
    PowerManager pm;
    PowerManager.WakeLock wakelock;
    boolean isWakelock = false;

    @Override
    public void onCreate() {
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

        sendBroadcast_h(VIEW_STATUS, "routine started"); //デバック用

        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            public void run() {
                // 定期的に実行したい処理
                try {
                    routine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        timer.scheduleAtFixedRate(task,1000,1000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isSerHTTPS = false;
        isHTTPS = false;

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void routine() throws IOException {
        //sendBroadcast_h(VIEW_STATUS, "routine started"); //デバック用
        //スリープ判定
        if(isSleeping){ //現在スリープ中で
            if(isBT || isHTTPS){ //BTもしくはHTTPSがONなら
                //画面点灯
                WakeUp();
                Log.i(TAG, "Wake up");
                isSleeping = false;
            }
            else{
                Log.i(TAG, "I'm sleeping");
            }
        }
        else{ //現在画面点灯中で
            if(!isBT && !isHTTPS){ //BTとHTTPSともにOFFなら
                //画面消灯
                /*
                if(isWakelock) {
                    isWakelock = false;
                    wakelock.release();
                }
                //画面消す
                mDevicePolicyManager.lockNow();
                */
                Log.i(TAG, "Go to sleep");
                sendBroadcast_h(VIEW_STATUS, "now sleeping"); //デバック用
                isSleeping = true;
            }
        }

        if(!(isHTTPS)){
            httpreq = new AsyncHttpRequest(this);
            httpreq.execute("https://api.sakura.io/datastore/v2/messages?token=563734fe-d149-40ef-9505-7ecccdd21242&module=uYK7HCzFLAd7&limit=1");
        }
        else{
            httpreq = new AsyncHttpRequest(this);
            httpreq.execute("https://api.sakura.io/datastore/v2/messages?token=563734fe-d149-40ef-9505-7ecccdd21242&module=uYK7HCzFLAd7&limit=10");
        }

        //取得したjsonファイルを解析
        if(json != null){
            StringBuilder builder = new StringBuilder();
            if (!isHTTPS) { //HTTPS切断時の処理
                try {
                    String newID;
                    JSONArray array = json.getJSONArray("results");
                    JSONObject result = array.getJSONObject(0); //最新のデータ取得
                    builder.append(result.getString("id")); //最新データのid抽出
                    newID = builder.toString();
                    if (newID.contains(lastID)) { //スリープのまま
                        Log.i(TAG, "I'm sleeping");
                    } else { //スリープ解除
                        isHTTPS = true;
                        Log.i(TAG, "Wake Up");
                    }
                    lastID = builder.toString();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else { //HTTPS更新時の処理
                try {
                    // Step1:情報が更新されているかを確認し、5回連続で更新されていなければスリープ
                    String newID;
                    JSONArray array = json.getJSONArray("results");
                    JSONObject result = array.getJSONObject(0); //最新データ取得
                    builder.append(result.getString("id")); //最新データのidを参照
                    newID = builder.toString();
                    if (newID.contains(lastID)) { //今回と前回の最新データが同じならスリープカウント+1
                        sleepcount += 1;
                    } else {
                        sleepcount = 0;
                    }
                    lastID = builder.toString();
                    if (sleepcount >= 5) { //5回連続更新されていなければスリープ
                        isHTTPS = false;
                    } /////////Step1ここまで
                    else {
                        // Step2:取得したJSONデータから情報を抽出
                        for (int i = 0; i < array.length(); i++) {
                            try {
                                StringBuilder builderp = new StringBuilder();
                                String type;
                                boolean isNext = false;

                                JSONObject resultp = array.getJSONObject(i);
                                builderp.append(resultp.getString("type")); //最新データのtypeを参照
                                type = builderp.toString();

                                Integer[] ERRORs = new Integer[4];
                                Integer[] ERRORis = new Integer[12];

                                if (type.contains("channels")) { //もしデータタイプがchannelなら解析（connectionは解析しない）
                                    JSONObject payload = resultp.getJSONObject("payload");
                                    JSONArray channels = payload.getJSONArray("channels");
                                    boolean isMTget = false;
                                    Double MTfr = 0.0;
                                    Double MTfl = 0.0;
                                    Double MTrr = 0.0;
                                    Double MTrl = 0.0;
                                    Double INV = 0.0;
                                    boolean isRTDget = false;
                                    Integer RTDfr = 0;
                                    Integer RTDfl = 0;
                                    Integer RTDrr = 0;
                                    Integer RTDrl = 0;
                                    for (int a = 0; a < channels.length(); a++) {
                                        JSONObject data = channels.getJSONObject(a);
                                        int channelnum;
                                        channelnum = data.getInt("channel");
                                        StringBuilder builderdata = new StringBuilder();
                                        //データ抽出＆送信
                                        switch (channelnum) {
                                            case 0: //LV
                                                builderdata.append(data.getDouble("value"));
                                                Double LV = Double.parseDouble(builderdata.toString());
                                                String sLV = builderdata.toString();
                                                if(LV > 5.0) {
                                                    // 小数第一位以下を表示しないように文字列切り取り
                                                    if (LV < 10.0) {
                                                        if (sLV.length() >= 3) {
                                                            sLV = sLV.substring(0, 3);
                                                        }
                                                    } else {
                                                        if (sLV.length() >= 4) {
                                                            sLV = sLV.substring(0, 4);
                                                        }
                                                    }
                                                    sendBroadcast_h(VIEW_LV,sLV);
                                                }
                                                else{
                                                    sendBroadcast_h(VIEW_LV,"-----");
                                                }
                                                break;
                                            case 1: //HV
                                                builderdata.append(data.getInt("value"));
                                                String sHV = builderdata.toString();
                                                Double HV = Double.parseDouble(sHV);
                                                if(HV >= 400){
                                                    sendBroadcast_h(VIEW_HV,sHV);
                                                    HVFlag = true;
                                                }
                                                else{
                                                    sendBroadcast_h(VIEW_HV,"-----");
                                                    HVFlag = false;
                                                }
                                                break;
                                            case 2: //MOTORFR
                                                isMTget = true;
                                                builderdata.append(data.getDouble("value"));
                                                MTfr = Double.parseDouble(builderdata.toString());
                                                break;
                                            case 3: //MOTORFL
                                                builderdata.append(data.getDouble("value"));
                                                MTfl = Double.parseDouble(builderdata.toString());
                                                break;
                                            case 4: //MOTORRR
                                                builderdata.append(data.getDouble("value"));
                                                MTrr = Double.parseDouble(builderdata.toString());
                                                break;
                                            case 5: //MOTORRL
                                                builderdata.append(data.getDouble("value"));
                                                MTrl = Double.parseDouble(builderdata.toString());
                                                break;
                                            case 6: //INV
                                                builderdata.append(data.getDouble("value"));
                                                String sINV = builderdata.toString();
                                                INV = Double.parseDouble(builderdata.toString());
                                                if(INV >= 0.0) {
                                                    sendBroadcast_h(VIEW_INV, sINV);
                                                }
                                                else{
                                                    sendBroadcast_h(VIEW_INV, "-----");
                                                }
                                                break;
                                            case 7: //RtDFR
                                                isRTDget = true;
                                                builderdata.append(data.getInt("value"));
                                                RTDfr = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 8: //RtDFL
                                                builderdata.append(data.getInt("value"));
                                                RTDfl = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 9: //RtDRR
                                                builderdata.append(data.getInt("value"));
                                                RTDrr = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 10: //RtDRL
                                                builderdata.append(data.getInt("value"));
                                                RTDrl = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 11: //Current 使用しない
                                                builderdata.append(data.getDouble("value"));
                                                String CURR = builderdata.toString();
                                                break;
                                            case 12: //VCMinfo
                                                builderdata.append(data.getInt("value"));
                                                String VCMi = builderdata.toString();
                                                //VCMから両踏みの情報を得る
                                                if(VCMi.contains("1")){
                                                    BORFlag = true;
                                                }
                                                else if(VCMi.contains("0")){
                                                    BORFlag = false;
                                                }
                                                sendBroadcast_h(VIEW_VCMINFO, VCMi);
                                                break;
                                            case 13: //ERRFR
                                                builderdata.append(data.getInt("value"));
                                                ERRORs[0] = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 14: //ERRFRinfo1
                                                builderdata.append(data.getInt("value"));
                                                ERRORis[0] = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 15: //ERRFRinfo2
                                                builderdata.append(data.getInt("value"));
                                                ERRORis[1] = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 16: //ERRFRinfo3
                                                builderdata.append(data.getInt("value"));
                                                ERRORis[2] = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 17: //ERRFL
                                                builderdata.append(data.getInt("value"));
                                                ERRORs[1] = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 18: //ERRFLinfo1
                                                builderdata.append(data.getInt("value"));
                                                ERRORis[3] = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 19: //ERRFLinfo2
                                                builderdata.append(data.getInt("value"));
                                                ERRORis[4] = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 20: //ERRFLinfo3
                                                builderdata.append(data.getInt("value"));
                                                ERRORis[5] = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 21: //ERRRR
                                                builderdata.append(data.getInt("value"));
                                                ERRORs[2] = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 22: //ERRRRinfo1
                                                builderdata.append(data.getInt("value"));
                                                ERRORis[6] = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 23: //ERRRRinfo2
                                                builderdata.append(data.getInt("value"));
                                                ERRORis[7] = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 24: //ERRRRinfo3
                                                builderdata.append(data.getInt("value"));
                                                ERRORis[8] = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 25: //ERRRL
                                                builderdata.append(data.getInt("value"));
                                                ERRORs[3] = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 26: //ERRRLinfo1
                                                builderdata.append(data.getInt("value"));
                                                ERRORis[9] = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 27: //ERRRLinfo2
                                                builderdata.append(data.getInt("value"));
                                                ERRORis[10] = Integer.parseInt(builderdata.toString());
                                                break;
                                            case 28: //ERRRLinfo3
                                                builderdata.append(data.getInt("value"));
                                                ERRORis[11] = Integer.parseInt(builderdata.toString());
                                                break;
                                        }
                                        String ch = String.valueOf(channelnum);
                                        Log.i(TAG, ch);
                                        Log.i(TAG, builderdata.toString());
                                    }

                                    /////////後処理////////////
                                    if(isMTget){ //MOTOR温度最大値を表示
                                        if(MTfr < MTfl){
                                            MTfr = MTfl;
                                        }
                                        if(MTfr < MTrr){
                                            MTfr = MTrr;
                                        }
                                        if(MTfr < MTrl){
                                            MTfr = MTrl;
                                        }
                                        if(MTfr > 0.0) { //温度が0でなければ
                                            String MTstring = Double.toString(MTfr);
                                            MTstring = MTstring.split("\\.", 0)[0]; //整数にする
                                            sendBroadcast_h(VIEW_MT, MTstring);
                                        }
                                        else{
                                            sendBroadcast_h(VIEW_MT, "-----");
                                        }
                                        if(MTfr >= 115 || INV >= 40){ //高温判定
                                            if(!(isHITEMP)) { //高温表示
                                                sendBroadcast_h(LAYOUT_HITEMP, "YES");
                                                isHITEMP = true;
                                            }
                                        }
                                        else{
                                            if(isHITEMP){ //高温非表示
                                                sendBroadcast_h(LAYOUT_HITEMP, "NO");
                                                isHITEMP = false;
                                            }
                                        }
                                    }
                                    if(isRTDget){ //RTDONOFF判定
                                        String result_RTD;
                                        if (RTDfr == 1 || RTDfl == 1 || RTDrr == 1 || RTDrl == 1) { //四輪のうち一つでもONであれば実行
                                            result_RTD = "RTD";
                                            if (!(RtDFlag)) { //RtDがOFF→ONのとき効果音を鳴らす
                                                soundPool.play(RtDsound, 1f, 1f, 0, 0, 1f);
                                                RtDFlag = true;
                                            }
                                        } else { //四輪ともOFFのとき実行
                                            result_RTD = "---";
                                            if (RtDFlag) {
                                                RtDFlag = false;
                                            }
                                        }
                                        sendBroadcast_h(VIEW_RTD, result_RTD);
                                    }
                                    LayoutChange(); //画面遷移処理
                                    ////////後処理ここまで////////

                                    if (!isNext) { //このデータ群の処理で初めての解析なら連続で解析
                                        isNext = true;
                                    } else {
                                        isNext = false;
                                        //2つのデータ群解析すれば処理終了

                                        ////////エラー処理////////
                                        ERRFlag = ERRORs[0] != 0 || ERRORs[1] != 0 || ERRORs[2] != 0 || ERRORs[3] != 0;
                                        String[] sERR = new String[4];
                                        for(int n = 0; n < 4; n++) {
                                            sERR[n] = "Error:" + ERRORs[n] + " Info:[1]" + ERRORis[3*n] + " [2]" + ERRORis[3*n+1] + " [3]" + ERRORis[3*n+2];
                                            if(ERRORs[n] == 2310) { //特定の情報はエラーの数字だけでなくその内容も表示
                                                sERR[n] += "\nEncoder communication";
                                            }
                                            else if(ERRORs[n] == 3587 && ERRORis[3*n] != 0){ //エラー番号が3587かつエラーインフォ1に情報があれば実行
                                                sERR[n] += "\nError during operation";
                                            }
                                            else if(ERRORs[n] == 3587){
                                                sERR[n] += "\nTemperature cabinet too high";
                                            }
                                            else if(ERRORs[n] == 1){
                                                sERR[n] += "\n出力制限① インバータ温度50℃↑";
                                            }
                                            else if(ERRORs[n] == 2){
                                                sERR[n] += "\n出力制限② モータ温度125℃↑";
                                            }
                                            else if(ERRORs[n] == 3){
                                                sERR[n] += "\n出力制限③ IGBT温度115℃↑";
                                            }
                                            else if(ERRORs[n] == 4){
                                                sERR[n] += "\n出力制限④ HV250V↓";
                                            }
                                            else if(ERRORs[n] == 5){
                                                sERR[n] += "\n出力制限⑤ HV720V↑";
                                            }
                                            else if(ERRORs[n] == 6){
                                                sERR[n] += "\n出力制限";
                                            }
                                        }
                                        sendBroadcast_h(VIEW_ERRFR, sERR[0]);
                                        sendBroadcast_h(VIEW_ERRFL, sERR[1]);
                                        sendBroadcast_h(VIEW_ERRRR, sERR[2]);
                                        sendBroadcast_h(VIEW_ERRRL, sERR[3]);
                                        ////////エラー処理ここまで///////////

                                        break;
                                    }
                                    Log.i(TAG, "finished");
                                } else {
                                    if(isNext) {    //二つ目のデータ群がconnectionなら処理終了
                                        isNext = false;
                                        break;
                                    }
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public void sendBroadcast_h(int VIEW, String message) {
        // IntentをブロードキャストすることでMainActivityへデータを送信
        Intent intent = new Intent();
        intent.setAction("HTTPS");
        intent.putExtra("VIEW2", VIEW);
        intent.putExtra("message2", message);
        getBaseContext().sendBroadcast(intent);
    }

    public void LayoutChange(){
        if(ERRFlag){
            if(!(NowLayout == ERR)) {
                //ERRモードに遷移
                sendBroadcast_h(LAYOUT_ERR, null);
            }
        }
        else if(BORFlag){
            if(!(NowLayout == BOR)) {
                //BORモードに遷移
                sendBroadcast_h(LAYOUT_BOR, null);
            }
        }
        else if(RtDFlag && HVFlag){
            if(!(NowLayout == RTD)) {
                //RTDモードに遷移
                sendBroadcast_h(LAYOUT_RTD, null);
            }
        }
        else if(HVFlag){
            if(!(NowLayout == HVON)) {
                //HVONモードに遷移
                sendBroadcast_h(LAYOUT_HVON, null);
            }
        }
        else{
            if(!(NowLayout == LVON)) {
                //LVONモードに遷移
                sendBroadcast_h(LAYOUT_LVON, null);
            }
        }
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

}
