package com.winjay.mirrorcast.aoa;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;

import com.winjay.mirrorcast.util.LogUtil;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author F2848777
 * @date 2023-04-19
 */
public class AOAAccessoryManager {
    private static final String TAG = AOAAccessoryManager.class.getSimpleName();
    private static volatile AOAAccessoryManager INSTANCE;
    private Context context;

    private static final String ACTION_USB_PERMISSION = "com.winjay.mirrorcast.action.USB_PERMISSION";
    private PendingIntent mPermissionIntent;

    private UsbManager mUsbManager;
    private UsbAccessory mAccessory;
    private ParcelFileDescriptor mFileDescriptor;
    private FileInputStream mInputStream;
    private FileOutputStream mOutputStream;

    private AOAAccessoryListener mAOAAccessoryListener;

    private AOAAccessoryManager() {
    }

    public static AOAAccessoryManager getInstance() {
        if (INSTANCE == null) {
            synchronized (AOAAccessoryManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AOAAccessoryManager();
                }
            }
        }
        return INSTANCE;
    }

    public void start(Context context) {
        this.context = context;
        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);

        registerReceiver();
        checkUSBDevice();
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        context.registerReceiver(mUsbReceiver, filter);
    }

    private void unregisterReceiver() {
        context.unregisterReceiver(mUsbReceiver);
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
                    }

//                    finish();
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

            if (mAOAAccessoryListener != null) {
                LogUtil.d(TAG, "111");
                mAOAAccessoryListener.connectSucceed();
            }

            receiveAOAMessage();
        }
    }

    private void receiveAOAMessage() {
        LogUtil.d(TAG);
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
                        if (mAOAAccessoryListener != null) {
                            mAOAAccessoryListener.onReceivedData(buffer, length);
                        }
                    }
                }
            }
        }).start();
    }

    public void sendAOAMessage(String msg) {
        if (mOutputStream != null) {
            LogUtil.d(TAG, "msg=" + msg);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        mOutputStream.write(msg.getBytes());
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

    public void stop() {
        unregisterReceiver();
        closeAccessory();
    }

    public interface AOAAccessoryListener {
        void connectSucceed();

        void onReceivedData(byte[] data, int length);
    }

    public void setAOAAccessoryListener(AOAAccessoryListener aOAAccessoryListener) {
        mAOAAccessoryListener = aOAAccessoryListener;
    }
}
