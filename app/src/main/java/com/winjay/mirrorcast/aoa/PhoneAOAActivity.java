package com.winjay.mirrorcast.aoa;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;

import com.winjay.mirrorcast.BaseActivity;
import com.winjay.mirrorcast.databinding.ActivityPhoneAoaBinding;
import com.winjay.mirrorcast.util.LogUtil;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author F2848777
 * @date 2023-04-10
 */
public class PhoneAOAActivity extends BaseActivity implements View.OnClickListener {
    private static final String TAG = PhoneAOAActivity.class.getSimpleName();

    private ActivityPhoneAoaBinding binding;

    private static final String ACTION_USB_PERMISSION = "com.winjay.mirrorcast.action.USB_PERMISSION";
    private PendingIntent mPermissionIntent;

    private UsbManager mUsbManager;
    private UsbAccessory mAccessory;
    private ParcelFileDescriptor mFileDescriptor;
    private FileInputStream mInputStream;
    private FileOutputStream mOutputStream;

    private static final int MESSAGE_LOG = 1;

    @Override
    protected View viewBinding() {
        binding = ActivityPhoneAoaBinding.inflate(getLayoutInflater());
        return binding.getRoot();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        super.onCreate(savedInstanceState);
        initView();

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);

        registerReceiver();
        checkUSBDevice();
    }

    private void initView() {
        binding.aoaSendBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v == binding.aoaSendBtn) {
            if (TextUtils.isEmpty(binding.aoaEd.getText().toString())) {
                dialogToast("内容不能为空！");
                return;
            }

            sendAOAMsg(binding.aoaEd.getText().toString());
        }
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
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
                    UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    //获取accessory句柄成功
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (accessory != null) {
                            LogUtil.d(TAG, "prepare to open accessory_filter stream");
                            mAccessory = accessory;
                            openAccessory();
                        }
                    } else {
                        LogUtil.d(TAG, "permission denied for accessory " + accessory);
                        mAccessory = null;
                    }
                    break;
                case UsbManager.ACTION_USB_ACCESSORY_ATTACHED:
                    UsbAccessory attachedAccessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

                    //检测到配件连接
                    LogUtil.d(TAG, "USB_ACCESSORY_ATTACHED " + attachedAccessory);

                    if (mUsbManager.hasPermission(attachedAccessory)) {
                        mAccessory = attachedAccessory;
                        openAccessory();
                    } else {
                        LogUtil.d(TAG, "accessories null per");
                        mUsbManager.requestPermission(attachedAccessory, mPermissionIntent);
                    }
                    break;
                case UsbManager.ACTION_USB_ACCESSORY_DETACHED:
                    UsbAccessory detachedAccessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    LogUtil.d(TAG, "USB_ACCESSORY_DETACHED " + detachedAccessory);
                    if (detachedAccessory != null) {
                        mAccessory = detachedAccessory;
                        closeAccessory();
                    }

                    finish();
                    break;
            }
        }
    };

    private void checkUSBDevice() {
        LogUtil.d(TAG);
        // 设备处于配件模式时，getAccessoryList() 方法会返回一个非空的数组
        UsbAccessory[] accessories = mUsbManager.getAccessoryList();

        if (accessories == null) {
            LogUtil.d(TAG, "accessories list is null");
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

            receiveAOAMsg();
        }
    }

    private void receiveAOAMsg() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int length = 0;
                byte[] buffer = new byte[16384];
                while (length >= 0) {
                    try {
                        length = mInputStream.read(buffer);
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                    if (length > 0) {
                        String receiveText = new String(buffer, 0, length);

                        if (receiveText.startsWith("car:")) {
                            LogUtil.d(TAG, "receiveText=" + receiveText);

                            Message m = Message.obtain(mHandler, MESSAGE_LOG);
                            m.obj = receiveText;
                            mHandler.sendMessage(m);
                        }
                    }
                }
            }
        }).start();
    }

    private void sendAOAMsg(String msg) {
        if (mOutputStream != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    LogUtil.d(TAG, "phone send msg.");
                    try {
                        mOutputStream.write(("phone:" + msg).getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    private void closeAccessory() {
        LogUtil.d(TAG);
        if (mAccessory != null) {
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG);

        unregisterReceiver();
        closeAccessory();
    }

    Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_LOG:
                    binding.aoaMsgTv.setText(binding.aoaMsgTv.getText() + "\n" + (String) msg.obj);
                    break;
            }
        }
    };
}
