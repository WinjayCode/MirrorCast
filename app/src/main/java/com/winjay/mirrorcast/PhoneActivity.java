package com.winjay.mirrorcast;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.winjay.mirrorcast.databinding.ActivityPhoneBinding;
import com.winjay.mirrorcast.util.LogUtil;
import com.winjay.mirrorcast.wifidirect.ServerThread;
import com.winjay.mirrorcast.wifidirect.WIFIDirectActivity;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author F2848777
 * @date 2023-03-31
 */
public class PhoneActivity extends BaseActivity implements View.OnClickListener {
    private static final String TAG = PhoneActivity.class.getSimpleName();

    private static final String ACTION_USB_PERMISSION = "com.winjay.mirrorcast.action.USB_PERMISSION";

    private ActivityPhoneBinding binding;

    private ServerThread serverThread;


    @Override
    protected View viewBinding() {
        binding = ActivityPhoneBinding.inflate(getLayoutInflater());
        return binding.getRoot();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        super.onCreate(savedInstanceState);
        initView();

        serverThread = new ServerThread();
        serverThread.start();

        registerReceiver();
        checkUSBDevice();
    }

    private void initView() {
        binding.btnWifiP2p.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        // wifi p2p
        if (view == binding.btnWifiP2p) {
            Intent intent = new Intent(this, WIFIDirectActivity.class);
            startActivity(intent);
        }
    }

    /////////////////////////////////// AOA ///////////////////////////////////
    private UsbManager mUsbManager;
    private UsbAccessory mAccessory;
    private ParcelFileDescriptor mFileDescriptor;
    private FileInputStream mInputStream;
    private FileOutputStream mOutputStream;
    private boolean isTerminated = false;

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
//        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        registerReceiver(mUsbReceiver, filter);
    }

    private void unregisterReceiver() {
        unregisterReceiver(mUsbReceiver);
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            LogUtil.d(TAG, "receive accessory_filter connect broadcast:" + action);

            switch (action) {
                case ACTION_USB_PERMISSION:
                    UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    //获取accessory句柄成功
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        LogUtil.d(TAG, "prepare to open accessory_filter stream");
                        mAccessory = accessory;
                        openAccessory();
                    } else {
                        LogUtil.d(TAG, "permission denied for accessory " + accessory);
                        mAccessory = null;
                    }
                    break;
                case UsbManager.ACTION_USB_ACCESSORY_ATTACHED:
                    UsbAccessory accessory2 = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

                    //检测到us连接
                    LogUtil.d(TAG, "USB_ACCESSORY_ATTACHED " + accessory2);

                    if (mUsbManager.hasPermission(accessory2)) {
                        mAccessory = accessory2;
                        openAccessory();
                    } else {
                        LogUtil.d(TAG, "accessories null per");
                        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(PhoneActivity.this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
                        mUsbManager.requestPermission(accessory2, mPermissionIntent);
                    }

//                    openAccessory();
                    break;
                case UsbManager.ACTION_USB_ACCESSORY_DETACHED:
                    UsbAccessory accessory3 = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    LogUtil.d(TAG, "USB_ACCESSORY_DETACHED " + accessory3);
                    if (accessory3 != null) {
                        mAccessory = null;
                        try {
                            if (mFileDescriptor != null) {
                                mFileDescriptor.close();
                            }
                            if (mInputStream != null) {
                                mInputStream.close();
                                mInputStream = null;
                            }
                            if (mOutputStream != null) {
                                mOutputStream.close();
                                mOutputStream = null;
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    finish();
//                    System.exit(0);
                    break;
            }
        }
    };

    private void checkUSBDevice() {
        LogUtil.d(TAG);
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        UsbAccessory[] accessories = mUsbManager.getAccessoryList();

        if (accessories == null) {
            LogUtil.d(TAG, "accessories list is null");
            toast("accessories list is null");
            return;
        }

        LogUtil.d(TAG, "accessories length " + accessories.length);

        UsbAccessory accessory = accessories[0];
        if (accessory != null) {
            LogUtil.d(TAG, "accessories not null");
            if (mUsbManager.hasPermission(accessory)) {

                mAccessory = accessory;
                openAccessory();
            } else {
                LogUtil.d(TAG, "accessories null per");
                PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
                mUsbManager.requestPermission(accessory, mPermissionIntent);
            }
        } else {
            LogUtil.d(TAG, "accessories null");
        }
    }

    private void openAccessory() {
        LogUtil.d(TAG);
        mFileDescriptor = mUsbManager.openAccessory(mAccessory);
        if (mFileDescriptor != null) {
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (!isTerminated) {
                        try {
                            byte[] data = new byte[1024];
                            int length = mInputStream.read(data);
                            String receiveText = new String(data);
                            LogUtil.d(TAG, "receiveText=" + receiveText);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(PhoneActivity.this, receiveText, Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();

            sendMsg();
        }
    }

    private void sendMsg() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                LogUtil.d(TAG, "phone send msg.");
                try {
                    String test = "phone test";
                    mOutputStream.write(test.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
    /////////////////////////////////// AOA ///////////////////////////////////

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isTerminated = true;
        unregisterReceiver();
    }
}
