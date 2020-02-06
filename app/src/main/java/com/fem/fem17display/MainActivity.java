package com.fem.fem17display;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import com.nifcloud.mbaas.core.NCMB;
import com.nifcloud.mbaas.core.NCMBException;
import com.nifcloud.mbaas.core.NCMBObject;
import com.nifcloud.mbaas.core.DoneCallback;

public class MainActivity extends AppCompatActivity  implements View.OnClickListener {
    /* tag */
    static final String TAG = "BluetoothSample";

    /**
     * Action(ステータス表示).
     */
    static final int VIEW_STATUS = 0;

    /**
     * Action(LV).
     */
    static final int VIEW_LV = 1;

    /**
     * Action(HV).
     */
    static final int VIEW_HV = 2;

    /**
     * Action(MOTOR).
     */
    static final int VIEW_MT = 3;

    /**
     * Action(INV).
     */
    static final int VIEW_INV = 4;

    /**
     * Action(RTD).
     */
    static final int VIEW_RTD = 5;

    /**
     * Action(ERROR).
     */
    static final int VIEW_ERR = 6;

    /**
     * Action(CURRENT)
     */
    static final int VIEW_CURR = 7;

    /**
     * Action(CURRENT)
     */
    static final int VIEW_DELTA = 8;

    /**
     * Action(LayoutChange:RTD)
     */
    static final int LAYOUT_RTD = 51;

    /**
     * Action(LayoutChange:HVON)
     */
    static final int LAYOUT_HVON = 52;

    /**
     * Action(LayoutChange:LVON)
     */
    static final int LAYOUT_LVON = 53;

    /**
     * Action(bluetooth).
     */
    static final int VIEW_BLUETOOTH = 100;

    /**
     * Action(デバック用取得文字列).
     */
    static final int VIEW_INPUT = 101;

    /**
     * Showmessageする文字列の受け渡し用
     */
    static String msg;

    /**
     * Bluetooth接続確認用フラグ
     */
    static boolean connectFlg = false;

    /**
     * 接続ボタン.
     */
    Button connectButton;

    /**
     * ステータス.
     */
    TextView mStatusTextView;

    /**
     * Bluetooth接続ステータス.
     */
    TextView mBluetooth;

    /**
     * Bluetoothから受信した生のデータ.
     */
    TextView mInputTextView;

    /**
     * LV電圧値
     */
    TextView mLV;

    /**
     * HV電圧値
     */
    TextView mHV;

    /**
     * MOTOR温度値
     */
    TextView mMT;

    /**
     * INV温度値
     */
    TextView mINV;

    /**
     * Ready to Drive表示
     */
    TextView mRTD;

    /**
     * エラー表示
     */
    TextView mERR;

    /**
     * 電流表示
     */
    TextView mCURR;

    /**
     * RtDONOFF
     */
    static boolean RtDFlag = false;

    /**
     * HVONOFF
     */
    static boolean HVFlag = false;

    /**
     * bluetooth Image
     */
    ImageView Bluetooth_Image;

    /**
     * 現在のレイアウト情報
     */
    static final int LVON = 0;
    static final int HVON = 1;
    static final int RTD = 2;
    static int NowLayout;

    /**
     * スリープ関連
     */
    static final int ADMIN_INTENT = 1;
    static DevicePolicyManager mDevicePolicyManager;
    static ComponentName mComponentName;
    static boolean isSleep = false;

    /**
     * クラス間のmessageやり取り用
     */
    private BroadcastReceiver mReceiver = null;
    private IntentFilter mIntentFilter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lvon);
        NowLayout = LVON;

        //画面常にON
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mInputTextView = (TextView) findViewById(R.id.inputValue);
        mStatusTextView = (TextView) findViewById(R.id.statusValue);
        mBluetooth = (TextView) findViewById(R.id.bluetoothValue);
        mLV = (TextView) findViewById(R.id.LVValue);
        mHV = (TextView) findViewById(R.id.HVValue);
        mMT = (TextView) findViewById(R.id.MTValue);
        mINV = (TextView) findViewById(R.id.INVValue);
        mRTD = (TextView) findViewById(R.id.RTDValue);
        mERR = (TextView) findViewById(R.id.ERRORValue);
        Bluetooth_Image = findViewById(R.id.bluetooth);
        connectButton = (Button) findViewById(R.id.connectButton);
        connectButton.setOnClickListener(this);

        //スリープ関連
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

        //表示文字列受信用
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // このonReceiveでMainServiceからのIntentを受信する。
                Bundle bundle = intent.getExtras();
                int VIEW = bundle.getInt("VIEW");
                String message = bundle.getString("message");
                ShowMessage(VIEW, message);
            }
        };
        // "BLUETOOTH" Intentフィルターをセット
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction("BLUETOOTH");
        registerReceiver(mReceiver, mIntentFilter);

    }

    @Override
    public void onClick(View v) {
        if (v.equals(connectButton)) {
            // 接続されていない場合のみ
            if (!connectFlg) {
                startService( new Intent( MainActivity.this, Bluetooth.class ) );
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    /**
     * Handlerへのmessage送信関数
     */
    public void ShowMessage(int VIEW, String MSG) {
        Message valueMsg = new Message();
        valueMsg.what = VIEW;
        valueMsg.obj = MSG;
        mHandler.sendMessage(valueMsg);
    }

    /**
     *Blutooth再接続
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
        mERR = (TextView) findViewById(R.id.ERRORValue);
        Bluetooth_Image = findViewById(R.id.bluetooth);
        connectButton = (Button) findViewById(R.id.connectButton);
        connectButton.setOnClickListener(this);
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

    public void onRestart() {
        super.onRestart();
        isSleep = false;
    }

    public void onDestroy() {
        super.onDestroy();
        stopService(new Intent(MainActivity.this, Bluetooth.class));
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
                if(msgStr.contains("Connected")){
                    Bluetooth_Image.setImageResource(R.drawable.bluetooth);
                }
                else if(msgStr.contains("NoConnect")){
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
            else if(action == LAYOUT_RTD){
                setContentView(R.layout.rtd);
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
        }
    };

}

