package com.fem.fem17display;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity  implements View.OnClickListener {
    /* tag */
    static final String TAG = "BluetoothSample";

     //Action(ステータス表示).
    static final int VIEW_STATUS = 0;

    //Action(LV).
    static final int VIEW_LV = 1;

    //Action(HV)
    static final int VIEW_HV = 2;

    //Action(MOTOR)
    static final int VIEW_MT = 3;

    //Action(INV)
    static final int VIEW_INV = 4;

    //Action(RTD)
    static final int VIEW_RTD = 5;

    //信号受信時エラーは六番目
    static final int VIEW_ERR = 6;

    //Action(ERROR)
    static final int VIEW_ERRFR = 61;
    static final int VIEW_ERRFL = 62;
    static final int VIEW_ERRRR = 63;
    static final int VIEW_ERRRL = 64;

    //Action(CURRENT)
    static final int VIEW_CURR = 7;

    //Action(DELTA)
    static final int VIEW_DELTA = 8;

    //Action(BTT)
    static final int VIEW_BTT = 9;

    //Action(NOWBTT)
    static final int VIEW_NOWBTT = 10;

    //Action(VCMINFO)
    static final int VIEW_VCMINFO = 11;

    //Action(LayoutChange:RTD)
    static final int LAYOUT_RTD = 51;

    //Action(LayoutChange:HVON)
    static final int LAYOUT_HVON = 52;

    //Action(LayoutChange:LVON)
    static final int LAYOUT_LVON = 53;

    //Action(LayoutChange:ERROR)
    static final int LAYOUT_ERR = 54;

    //Action(LayoutChange:BORON)
    static final int LAYOUT_BOR = 55;

    //Action(LayoutVisible:HITEMP)
    static final int LAYOUT_HITEMP = 56;

    //Action(bluetooth)
    static final int VIEW_BLUETOOTH = 100;

    //Action(デバック用取得文字列)
    static final int VIEW_INPUT = 101;

    //Showmessageする文字列の受け渡し用
    static String msg;

    //HTTPSサービス確認用フラグ
    static boolean isSerHTTPS = false;

    //BLUETOOTHサービス確認用フラグ
    static boolean isSerBT = false;

    //接続ボタン
    Button connectButton;

    //電流積算リセットボタン
    Button zeroButton;

    //BLUETOOTHONボタン
    Button btButton;

    //ステータス
    TextView mStatusTextView;

    //Bluetooth接続ステータス
    TextView mBluetooth;

    //Bluetoothから受信した生のデータ
    TextView mInputTextView;

    //LV電圧値
    TextView mLV;

    //HV電圧値
    TextView mHV;

    //MOTOR温度値
    TextView mMT;

    //INV温度値
    TextView mINV;

    //Ready to Drive表示
    TextView mRTD;

    //エラー表示
    TextView mERRFR;
    TextView mERRFL;
    TextView mERRRR;
    TextView mERRRL;

    //電流表示
    TextView mCURR;

    //バッテリ残量表示
    TextView mBTT;
    ProgressBar bttBar;
    TextView mNOWBTT;

    //VCMINFO表示
    TextView mVCMINFO;

    //高温時レイアウト
    ConstraintLayout mHitemp;

    //RtD ONOFF
    static boolean RtDFlag = false;

    //HV ONOFF
    static boolean HVFlag = false;

    //ERROR ONOFF
    static boolean ERRFlag = false;

    //BOR ONOFF
    static boolean BORFlag = false;

    //HI TMPERATURE
    static boolean isHITEMP = false;

    //bluetooth Image
    ImageView Bluetooth_Image;

    //現在のレイアウト情報
    static final int LVON = 0;
    static final int HVON = 1;
    static final int RTD = 2;
    static final int ERR = 3;
    static final int BOR = 4;
    static int NowLayout;

    //スリープ関連
    static final int ADMIN_INTENT = 1;
    static DevicePolicyManager mDevicePolicyManager;
    static ComponentName mComponentName;
    static boolean isSleep = false;

    //以下使わない
    static boolean isSleeping = false;
    static boolean isBT = false;
    static boolean isHTTPS = false;

    //クラス間のmessageやり取り用
    private BroadcastReceiver mReceiver = null;
    private BroadcastReceiver mReceiver2 = null;
    private IntentFilter mIntentFilter = null;
    private IntentFilter mIntentFilter2 = null;

    //電流積算値を保存するテキストファイル名の設定
    static final String CurrFilename = "sum.txt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //画面生成（LVON）
        setContentView(R.layout.lvon);
        NowLayout = LVON;

        //画面常にON 自動でスリープしないように設定
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //テキスト, 画像表示場所設定
        mInputTextView = (TextView) findViewById(R.id.inputValue);
        mStatusTextView = (TextView) findViewById(R.id.statusValue);
        mBluetooth = (TextView) findViewById(R.id.bluetoothValue);
        mLV = (TextView) findViewById(R.id.LVValue);
        mHV = (TextView) findViewById(R.id.HVValue);
        mMT = (TextView) findViewById(R.id.MTValue);
        mINV = (TextView) findViewById(R.id.INVValue);
        mRTD = (TextView) findViewById(R.id.RTDValue);
        mERRFR = (TextView) findViewById(R.id.ERRORValueFR);
        mERRFL = (TextView) findViewById(R.id.ERRORValueFL);
        mERRRR = (TextView) findViewById(R.id.ERRORValueRR);
        mERRRL = (TextView) findViewById(R.id.ERRORValueRL);
        mBTT = (TextView) findViewById(R.id.bttValue);
        mNOWBTT = (TextView) findViewById(R.id.nowbtt);
        mVCMINFO = (TextView) findViewById(R.id.vcminfo);
        bttBar = findViewById(R.id.bttBar);
        Bluetooth_Image = findViewById(R.id.bluetooth);
        mHitemp = findViewById(R.id.HitempLayout);

        //ボタン表示場所設定&クリックリスナー設定
        connectButton = (Button) findViewById(R.id.connectButton);
        connectButton.setOnClickListener(this);
        //zeroButton = (Button) findViewById(R.id.zeroButton);
        //zeroButton.setOnClickListener(this);
        btButton = (Button) findViewById(R.id.btButton);
        btButton.setOnClickListener(this);

        //スリープ関連の設定
        mDevicePolicyManager = (DevicePolicyManager)getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        mComponentName = new ComponentName(this, Admin.class);
        if (!(mDevicePolicyManager.isAdminActive(mComponentName))) {
            Log.d("LockScreen", "admin not active");
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mComponentName);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Administrator description");
            startActivityForResult(intent, ADMIN_INTENT);
        }

        //Bluetooth.javaからの表示文字列受信用
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // このonReceiveでMainServiceからのIntentを受信する。
                Bundle bundle = intent.getExtras();
                int VIEW = bundle.getInt("VIEW");
                String message = bundle.getString("message");
                ShowMessage(VIEW, message); //受信した文字列を表示
            }
        };
        // "BLUETOOTH" Intentフィルターをセット
        mIntentFilter = new IntentFilter();
        //mIntentFilter.addAction("BLUETOOTH");
        mIntentFilter.addAction("BLUETOOTH");
        registerReceiver(mReceiver, mIntentFilter);

        /*
        //HTTPSRequest.javaからの表示文字列受信用
        mReceiver2 = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context2, Intent intent2) {
                // このonReceiveでMainServiceからのIntentを受信する。
                Bundle bundle2 = intent2.getExtras();
                int VIEW2 = bundle2.getInt("VIEW2");
                String message2 = bundle2.getString("message2");
                ShowMessage(VIEW2, message2); //受信した文字列を表示
            }
        };
        // "HTTPS" Intentフィルターをセット
        mIntentFilter2 = new IntentFilter();
        mIntentFilter2.addAction("HTTPS");
        registerReceiver(mReceiver2, mIntentFilter2);
        */
    }

    public void onRestart() {
        super.onRestart();
        isSleep = false;
    }

    public void onDestroy() {
        super.onDestroy();
        /*if (isSerHTTPS) {
            // HTTPSRequest.javaを停止
            stopService(new Intent(MainActivity.this, HTTPSRequest.class));
        }*/
        if(isSerBT){
            // Bluetooth.javaを停止
            stopService(new Intent(MainActivity.this, Bluetooth.class));
        }
    }

    @Override
    public void onClick(View v) {
        /*if (v.equals(connectButton)) { // CONNECTボタンが押されていたら
            if (!isSerHTTPS) { // サービス実行されていないとき
                // HTTPSRequest.java実行
                startService( new Intent( MainActivity.this, HTTPSRequest.class ) );
                //電流積算値取得&表示
                try {
                    //現在の電流積算値を取得
                    FileInputStream fileInputStream = openFileInput(CurrFilename); //電流積算値が保存されているテキストファイルを参照
                    BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream));
                    String lineBuffer;
                    while ((lineBuffer = reader.readLine()) != null) {
                        SUM = lineBuffer;
                    }
                    String MAX = String.valueOf(MaxCURR);
                    String NOWBTT = SUM + "/" + MAX;
                    ShowMessage(VIEW_NOWBTT, NOWBTT);
                } catch(FileNotFoundException e){
                    e.printStackTrace();
                } catch(IOException e){
                    e.printStackTrace();
                }

            }
        }*/
        if (v.equals(btButton)) { // btCONNECTボタンが押されていたら
            if (!isBT) { // 接続されていないとき
                // bluetooth.java実行
                startService(new Intent(MainActivity.this, Bluetooth.class));
            }
        }/*
        else if (v.equals(zeroButton)) { //電流積算値リセットボタンが押されていたら
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("!!!警告!!!")
                    .setMessage("電流積算値をリセットしてもよろしいですか？")
                    .setPositiveButton("はい", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                FileOutputStream fos = openFileOutput(CurrFilename, Context.MODE_PRIVATE);
                                String first = "0.0"; //電流積算値を0に設定
                                fos.write(first.getBytes());
                                String MAX = String.valueOf(MaxCURR);
                                String NOWBTT = "0.0/" + MAX;
                                ShowMessage(VIEW_NOWBTT, NOWBTT);
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    })
                    .setNegativeButton("いいえ", null)
                    .show();
        }*/
    }

    /**
     * Handlerへのmessage送信関数
     */
    public void ShowMessage(int VIEW, String MSG) {
        Message valueMsg = new Message();
        //何の情報かを設定
        valueMsg.what = VIEW;
        //表示内容を設定
        valueMsg.obj = MSG;
        //文字列表示ハンドラに送信
        mHandler.sendMessage(valueMsg);
    }

    /**
     *レイアウト遷移時、テキスト等のIdを再取得
     */
    private void RefindId(){
        mInputTextView = (TextView) findViewById(R.id.inputValue);
        mStatusTextView = (TextView) findViewById(R.id.statusValue);
        mBluetooth = (TextView) findViewById(R.id.bluetoothValue);
        mLV = (TextView) findViewById(R.id.LVValue);
        mHV = (TextView) findViewById(R.id.HVValue);
        mMT = (TextView) findViewById(R.id.MTValue);
        mINV = (TextView) findViewById(R.id.INVValue);
        mRTD = (TextView) findViewById(R.id.RTDValue);
        mERRFR = (TextView) findViewById(R.id.ERRORValueFR);
        mERRFL = (TextView) findViewById(R.id.ERRORValueFL);
        mERRRR = (TextView) findViewById(R.id.ERRORValueRR);
        mERRRL = (TextView) findViewById(R.id.ERRORValueRL);
        mBTT = (TextView) findViewById(R.id.bttValue);
        mNOWBTT = (TextView) findViewById(R.id.nowbtt);
        mVCMINFO = (TextView) findViewById(R.id.vcminfo);
        bttBar = findViewById(R.id.bttBar);
        Bluetooth_Image = findViewById(R.id.bluetooth);
        mHitemp = findViewById(R.id.HitempLayout);
        connectButton = (Button) findViewById(R.id.connectButton);
        connectButton.setOnClickListener(this);
        btButton = (Button) findViewById(R.id.btButton);
        btButton.setOnClickListener(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ADMIN_INTENT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(getApplicationContext(), "Registered As Admin", Toast.LENGTH_SHORT).show();
            }else{
                Toast.makeText(getApplicationContext(), "Failed to register as Admin", Toast.LENGTH_SHORT).show();
            }
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
                if(msgStr.contains("bok")){
                    Bluetooth_Image.setImageResource(R.drawable.bluetooth);
                }
                else if(msgStr.contains("bno")){
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
            else if(action == VIEW_ERRFR){
                mERRFR.setText(msgStr);
            }
            else if(action == VIEW_ERRFL){
                mERRFL.setText(msgStr);
            }
            else if(action == VIEW_ERRRR){
                mERRRR.setText(msgStr);
            }
            else if(action == VIEW_ERRRL){
                mERRRL.setText(msgStr);
            }
            else if(action == VIEW_BTT){
                mBTT.setText(msgStr);
                Log.d("btt","現在のBttは" + msgStr);
                if(NowLayout == RTD) {
                    bttBar.setProgress(Integer.parseInt(msgStr)); //バッテリ残量バー更新
                }
            }
            else if(action == VIEW_NOWBTT){
                mNOWBTT.setText(msgStr);
            }
            else if(action == VIEW_VCMINFO){
                mVCMINFO.setText(msgStr);
            }
            else if(action == LAYOUT_RTD){
                setContentView(R.layout.rtd2);
                NowLayout = RTD;
                RefindId();
            }
            else if(action == LAYOUT_HVON){
                setContentView(R.layout.hvon);
                NowLayout = HVON;
                RefindId();
            }
            else if(action == LAYOUT_LVON){
                setContentView(R.layout.lvon);
                NowLayout = LVON;
                RefindId();
            }
            else if(action == LAYOUT_ERR){
                setContentView(R.layout.error);
                NowLayout = ERR;
                RefindId();
            }
            else if(action == LAYOUT_BOR){
                setContentView(R.layout.bor);
                NowLayout = BOR;
                RefindId();
            }
            else if(action == LAYOUT_HITEMP){
                if(RtDFlag){
                    if(msgStr.contains("YES")){
                        mHitemp.setVisibility(View.VISIBLE);
                    }
                    else if(msgStr.contains("NO")){
                        mHitemp.setVisibility(View.INVISIBLE);
                    }
                }
            }
        }
    };

}

