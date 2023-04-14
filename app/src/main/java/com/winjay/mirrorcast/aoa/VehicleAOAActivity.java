package com.winjay.mirrorcast.aoa;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;

import com.winjay.mirrorcast.BaseActivity;
import com.winjay.mirrorcast.databinding.ActivityVehicleAoaBinding;
import com.winjay.mirrorcast.util.LogUtil;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author F2848777
 * @date 2023-04-10
 */
public class VehicleAOAActivity extends BaseActivity implements View.OnClickListener {
    private static final String TAG = VehicleAOAActivity.class.getSimpleName();

    private ActivityVehicleAoaBinding binding;

    private static final int MESSAGE_LOG = 1;

    private static final int STORAGE_INTERFACE_COUNT = 1;
    private static final int STORAGE_INTERFACE_ID = 0;
    private static final int STORAGE_INTERFACE_CLASS = 8;
    private static final int STORAGE_INTERFACE_SUBCLASS = 6;
    private static final int STORAGE_INTERFACE_PROTOCOL = 80;
    private static final int VID_IPHONE = 0x05AC;

    private final LinkedBlockingQueue<UsbDevice> pendingPermissionDevices = new LinkedBlockingQueue<>();

    private UsbManager mUsbManager;
    private static final int AOA_GET_PROTOCOL = 51;
    private static final int AOA_SEND_IDENT = 52;
    private static final int AOA_START_ACCESSORY = 53;
    private static final String AOA_MANUFACTURER = "MobileDriveTech";
    private static final String AOA_MODEL_NAME = "SuperLink";
    private static final String AOA_DESCRIPTION = "MobileDriveTech SuperLink";
    private static final String AOA_VERSION = "1.0";
    private static final String AOA_URI = "http://www.MobileDriveTech.com.cn/";
    private static final String AOA_SERIAL_NUMBER = "0123456789";
    private PendingIntent mPermissionIntent;
    private static final String ACTION_USB_PERMISSION = "com.winjay.mirrorcast.USB_PERMISSION";
    private static final int VID_ACCESSORY = 0x18D1;
    private static final int PID_ACCESSORY_ONLY = 0x2D00;
    private static final int PID_ACCESSORY_AUDIO_ADB_BULK = 0x2D05;
    private UsbInterface usbInterface;
    private UsbDeviceConnection usbDeviceConnection;
    private UsbEndpoint inEndpoint;
    private UsbEndpoint outEndpoint;

    @Override
    protected View viewBinding() {
        binding = ActivityVehicleAoaBinding.inflate(getLayoutInflater());
        return binding.getRoot();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        LogUtil.d(TAG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        super.onCreate(savedInstanceState);
        initView();

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        // PendingIntent.FLAG_INMUTABLE 会导致权限请求失败！！！
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);

        registerUsbReceiver();
        checkDevice();
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

    private void checkDevice() {
        LogUtil.d(TAG);
        pendingPermissionDevices.clear();
        for (UsbDevice device : mUsbManager.getDeviceList().values()) {
            if (isUsbStorageDevice(device)) {
                continue;
            }

            boolean hasPermission = mUsbManager.hasPermission(device);
            LogUtil.d(TAG, "UsbAccessoryScanner deal with " + device + " has permission " + hasPermission);
            if (hasPermission) {
                if (openAccessory(device)) {
                    // 成功打开usb accessory，清空权限列表，返回设备
                    pendingPermissionDevices.clear();
                    break;
                }
            } else if (device.getVendorId() != VID_IPHONE) {
                // 过滤掉iPhone
                pendingPermissionDevices.add(device);
            }
        }
        requestPendingPermission();
    }

    private void requestPendingPermission() {
        UsbDevice usbDevice = pendingPermissionDevices.poll();
        if (usbDevice != null) {
            LogUtil.d(TAG);
            mUsbManager.requestPermission(usbDevice, mPermissionIntent);
        }
    }

    private boolean isUsbStorageDevice(UsbDevice device) {
        if (device == null) {
            LogUtil.d(TAG, "this device is null");
            return false;
        }

        if (STORAGE_INTERFACE_COUNT == device.getInterfaceCount()) {
            UsbInterface usbInter = device.getInterface(STORAGE_INTERFACE_ID);
            if (STORAGE_INTERFACE_CLASS == usbInter.getInterfaceClass()
                    && STORAGE_INTERFACE_SUBCLASS == usbInter.getInterfaceSubclass()
                    && STORAGE_INTERFACE_PROTOCOL == usbInter.getInterfaceProtocol()) {
                LogUtil.d(TAG, "this device is mass storage 2");
                return true;
            }
        }
        return false;
    }

    private void registerUsbReceiver() {
        IntentFilter intentFilter = new IntentFilter(ACTION_USB_PERMISSION);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, intentFilter);
    }

    private void unregisterUsbReceiver() {
        unregisterReceiver(usbReceiver);
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            LogUtil.d(TAG, "action=" + action);
            if (TextUtils.isEmpty(action)) {
                return;
            }
            switch (action) {
                case ACTION_USB_PERMISSION:
                    synchronized (this) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                            if (device != null) {
                                if (openAccessory(device)) {
                                    // 成功打开usb accessory，清空权限列表，返回设备
                                    pendingPermissionDevices.clear();
                                    return;
                                }
                            }
                        }
//                        requestPendingPermission();
                    }
                    break;
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                    // 外部 USB 设备已连接
                    UsbDevice attachDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (attachDevice != null) {
                        if (isUsbStorageDevice(attachDevice)) {
                            return;
                        }

                        LogUtil.d(TAG, "UsbAccessoryScanner ACTION_USB_DEVICE_ATTACHED with " + attachDevice);

                        if (mUsbManager.hasPermission(attachDevice)) {
                            if (openAccessory(attachDevice)) {
                                return;
                            }
                        } else {
                            pendingPermissionDevices.add(attachDevice);
                            requestPendingPermission();
                        }
                    }
                    break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                    // 外部 USB 设备已断开
                    UsbDevice detachedDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (detachedDevice != null && isAccessory(detachedDevice)) {
                        LogUtil.d(TAG, "device had been detached");
                        closeAccessory();
                    }
                    break;
            }
        }
    };

    private boolean openAccessory(UsbDevice device) {
        LogUtil.d(TAG);
        usbInterface = device.getInterface(0);
        usbDeviceConnection = mUsbManager.openDevice(device);
        boolean result = usbDeviceConnection.claimInterface(usbInterface, true);
        LogUtil.d(TAG, "claimInterface result=" + result);
        findEndpoint();

        if (isAccessory(device)) {
            toast("配件连接成功！");

            receiveAOAMsg();
            return true;
        }

        if (getProtocolVersion()) {
            if (sendIdentityStrings()) {
                if (startAccessoryMode()) {
                    toast("切换配件模式成功！");
                    usbDeviceConnection.releaseInterface(usbInterface);
                    usbDeviceConnection.close();
                    usbDeviceConnection = null;
                }
            }
        }
        return false;
    }

    private void findEndpoint() {
        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = usbInterface.getEndpoint(i);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    inEndpoint = endpoint;
                } else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    outEndpoint = endpoint;
                }
            }
        }
    }

    private void closeAccessory() {
        if (usbDeviceConnection != null) {
            LogUtil.d(TAG);
            usbDeviceConnection.releaseInterface(usbInterface);
            usbDeviceConnection.close();
            usbDeviceConnection = null;
        }
    }

    private boolean isAccessory(UsbDevice usbDevice) {
        return usbDevice.getVendorId() == VID_ACCESSORY
                && usbDevice.getProductId() >= PID_ACCESSORY_ONLY
                && usbDevice.getProductId() <= PID_ACCESSORY_AUDIO_ADB_BULK;
    }

    private boolean getProtocolVersion() {
        byte[] buffer = new byte[2];
        return controlTransferIn(AOA_GET_PROTOCOL, 0, 0, buffer) >= 0;
    }

    private boolean sendIdentityStrings() {
        if (controlTransferOut(AOA_SEND_IDENT, 0, 0, AOA_MANUFACTURER.getBytes()) < 0) {
            LogUtil.w(TAG, "send identity AOA_MANUFACTURER fail");
            return false;
        }
        if (controlTransferOut(AOA_SEND_IDENT, 0, 1, AOA_MODEL_NAME.getBytes()) < 0) {
            LogUtil.w(TAG, "send identity AOA_MODEL_NAME fail");
            return false;
        }
        if (controlTransferOut(AOA_SEND_IDENT, 0, 2, AOA_DESCRIPTION.getBytes()) < 0) {
            LogUtil.w(TAG, "send identity AOA_DESCRIPTION fail");
            return false;
        }
        if (controlTransferOut(AOA_SEND_IDENT, 0, 3, AOA_VERSION.getBytes()) < 0) {
            LogUtil.w(TAG, "send identity AOA_VERSION fail");
            return false;
        }
        if (controlTransferOut(AOA_SEND_IDENT, 0, 4, AOA_URI.getBytes()) < 0) {
            LogUtil.w(TAG, "send identity AOA_URI fail");
            return false;
        }
        if (controlTransferOut(AOA_SEND_IDENT, 0, 5, AOA_SERIAL_NUMBER.getBytes()) < 0) {
            LogUtil.w(TAG, "send identity AOA_SERIAL_NUMBER fail");
            return false;
        }
        LogUtil.d(TAG, "send identity string success");
        return true;
    }

    private boolean startAccessoryMode() {
        if (controlTransferOut(AOA_START_ACCESSORY, 0, 0, null) < 0) {
            LogUtil.w(TAG, "start accessory mode fail");
            return false;
        }
        LogUtil.d(TAG, "start accessory mode success");
        return true;
    }

    private int controlTransferOut(int request, int value, int index, byte[] buffer) {
        if (usbDeviceConnection == null) {
            return -1;
        }
        return usbDeviceConnection.controlTransfer(UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR, request,
                value, index, buffer, buffer == null ? 0 : buffer.length, 0);
    }

    private int controlTransferIn(int request, int value, int index, byte[] buffer) {
        if (usbDeviceConnection == null) {
            return -1;
        }
        return usbDeviceConnection.controlTransfer(UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_VENDOR, request,
                value, index, buffer, buffer == null ? 0 : buffer.length, 0);
    }

    private void receiveAOAMsg() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int length = 0;
                byte[] buffer = new byte[16384];
                while (length >= 0) {
                    length = usbDeviceConnection.bulkTransfer(inEndpoint, buffer, buffer.length, 0);

                    if (length > 0) {
                        String response = new String(buffer, 0, length);

                        if (response.startsWith("phone:")) {
                            LogUtil.d(TAG, "host received=" + response);

                            Message m = Message.obtain(mHandler, MESSAGE_LOG);
                            m.obj = response;
                            mHandler.sendMessage(m);
                        }
                    }
                }
            }
        }).start();
    }

    private void sendAOAMsg(String msg) {
        LogUtil.d(TAG, "msg=" + msg);
        if (usbDeviceConnection != null && usbInterface != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    // 发送数据
                    byte[] data = ("car:" + msg).getBytes();
                    int result = usbDeviceConnection.bulkTransfer(outEndpoint, data, data.length, 0);
                    LogUtil.d(TAG, "host send result=" + result);
                }
            }).start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG);

        unregisterUsbReceiver();
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
