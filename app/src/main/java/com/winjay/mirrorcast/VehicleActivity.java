package com.winjay.mirrorcast;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.media.MediaRouter;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Display;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.winjay.mirrorcast.app_mirror.AppSocketServer;
import com.winjay.mirrorcast.app_mirror.AppSocketServerManager;
import com.winjay.mirrorcast.car.client.CarShowActivity;
import com.winjay.mirrorcast.databinding.ActivityVehicleBinding;
import com.winjay.mirrorcast.decode.ScreenDecoderActivity;
import com.winjay.mirrorcast.server.ScreenService;
import com.winjay.mirrorcast.util.DisplayUtil;
import com.winjay.mirrorcast.util.LogUtil;
import com.winjay.mirrorcast.wifidirect.ClientThread;
import com.winjay.mirrorcast.wifidirect.ServerThread;
import com.winjay.mirrorcast.wifidirect.WIFIDirectActivity;

/**
 * @author F2848777
 * @date 2023-03-31
 */
public class VehicleActivity extends BaseActivity implements View.OnClickListener {
    private static final String TAG = VehicleActivity.class.getSimpleName();

    private ActivityVehicleBinding binding;

    private MediaProjectionManager mediaProjectionManager;
    private static final int PROJECTION_REQUEST_CODE = 1;

    private boolean isRecording = false;

    private ServerThread serverThread;


    @Override
    protected View viewBinding() {
        binding = ActivityVehicleBinding.inflate(getLayoutInflater());
        return binding.getRoot();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        super.onCreate(savedInstanceState);
        initView();

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        serverThread = new ServerThread();
        serverThread.start();

        AppSocketServerManager.getInstance().startServer();

        registerUsbReceiver();
    }

    private void initView() {
        binding.btnWifiP2p.setOnClickListener(this);
        binding.btnStartRecord.setOnClickListener(this);
        binding.connectMirrorCastServer.setOnClickListener(this);
        binding.btnStartReceive.setOnClickListener(this);
        binding.btnCarHome.setOnClickListener(this);


        binding.aoaSendBtn.setOnClickListener(this);
    }


    @Override
    public void onClick(View v) {
        // wifi p2p
        if (v == binding.btnWifiP2p) {
            Intent intent = new Intent(this, WIFIDirectActivity.class);
            startActivity(intent);

//            Intent intent = new Intent(this, CarLauncherActivity.class);
//            startActivity(intent);
        }
        if (v == binding.btnStartRecord) {
//            if (!isRecording) {
//                isRecording = true;
//                startProjection();
//                binding.btnStartRecord.setText("停止投屏");
//            } else {
//                isRecording = false;
//                stopProjection();
//                binding.btnStartRecord.setText("开始投屏");
//            }
        }
        // 连接投屏服务
        if (v == binding.connectMirrorCastServer) {
            if (TextUtils.isEmpty(AppApplication.destDeviceIp) && TextUtils.isEmpty(binding.ipEd.getText().toString())) {
                toast("请输入需要投屏的设备IP地址！");
                return;
            }

            if (!TextUtils.isEmpty(AppApplication.destDeviceIp)) {
                connectMirrorCastServer(AppApplication.destDeviceIp);
                return;
            }

            if (!TextUtils.isEmpty(binding.ipEd.getText().toString())) {
                connectMirrorCastServer(binding.ipEd.getText().toString());
                return;
            }
        }
        // 接收投屏
        if (v == binding.btnStartReceive) {
            if (!mMirrorCastServerConnected) {
                toast("请先连接投屏服务！");
                return;
            }

            Intent intent = new Intent(this, ScreenDecoderActivity.class);
            String serverIp = "";
            if (!TextUtils.isEmpty(AppApplication.destDeviceIp)) {
                serverIp = AppApplication.destDeviceIp;
            } else if (!TextUtils.isEmpty(binding.ipEd.getText().toString())) {
                serverIp = binding.ipEd.getText().toString();
            }

            intent.putExtra("serverIp", serverIp);
            startActivity(intent);
        }
        // car launcher
        if (v == binding.btnCarHome) {
            if (!mMirrorCastServerConnected) {
                toast("请先连接投屏服务！");
                return;
            }

            Intent intent = new Intent(this, CarShowActivity.class);
            String serverIp = "";
            if (!TextUtils.isEmpty(AppApplication.destDeviceIp)) {
                serverIp = AppApplication.destDeviceIp;
            } else if (!TextUtils.isEmpty(binding.ipEd.getText().toString())) {
                serverIp = binding.ipEd.getText().toString();
            }

//            new ClientThread(serverIp, true).start();

            intent.putExtra("serverIp", serverIp);
            startActivity(intent);


//            createVirtualDisplay();

//            Intent intent = new Intent();
//            intent.setComponent(new ComponentName("com.winjay.mirrorcast", "com.winjay.mirrorcast.car.server.CarLauncherActivity"));
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//
//            ActivityOptions options = ActivityOptions.makeBasic();
//            options.setLaunchDisplayId(2);
//            Bundle optsBundle = options.toBundle();
//            startActivity(intent, optsBundle);
        }



        if (v == binding.aoaSendBtn) {
            if (TextUtils.isEmpty(binding.aoaEd.getText().toString())) {
                toast("内容不能为空！");
                return;
            }

            sendAOAMsg(binding.aoaEd.getText().toString());
        }
    }

    private void createVirtualDisplay() {
        try {
            LogUtil.d(TAG);
            DisplayManager displayManager = (DisplayManager) AppApplication.context.getSystemService(Context.DISPLAY_SERVICE);
            int[] screenSize = DisplayUtil.getScreenSize(AppApplication.context);

            int flags = 139;

//            int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION |
//                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC |
//                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;

            VirtualDisplay virtualDisplay = displayManager.createVirtualDisplay("app_mirror",
                    screenSize[0], screenSize[1], screenSize[2], new SurfaceView(AppApplication.context).getHolder().getSurface(),
                    flags);
            int displayId = virtualDisplay.getDisplay().getDisplayId();
            LogUtil.d(TAG, "virtual display ID=" + displayId);

            for (Display display : displayManager.getDisplays()) {
                LogUtil.d(TAG, "dispaly: " + display.getName() + ", id " + display.getDisplayId() + " :" + display.toString());
//                if (display.getDisplayId() != 0) {
//                    SecondeDid = display.getDisplayId();
//                }
            }

            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.winjay.mirrorcast", "com.winjay.mirrorcast.car.server.CarLauncherActivity"));

            ActivityOptions activityOptions = ActivityOptions.makeBasic();
            MediaRouter mediaRouter = (MediaRouter) getSystemService(Context.MEDIA_ROUTER_SERVICE);
            MediaRouter.RouteInfo route = mediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_VIDEO);
            if (route != null) {
                Display presentationDisplay = route.getPresentationDisplay();
                LogUtil.d(TAG, "displayId=" + presentationDisplay.getDisplayId());
                Bundle bundle = activityOptions.setLaunchDisplayId(presentationDisplay.getDisplayId()).toBundle();
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent, bundle);
            }


//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
//
//            ActivityOptions options = ActivityOptions.makeBasic();
//            options.setLaunchDisplayId(9);
//            Bundle optsBundle = options.toBundle();

//            AppApplication.context.startActivity(intent, optsBundle);
        } catch (Exception e) {
            LogUtil.e(TAG, "createVirtualDisplay error " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean mMirrorCastServerConnected = false;

    private void connectMirrorCastServer(String serverIp) {
        if (mMirrorCastServerConnected) {
            toast("投屏服务已连接！");
            return;
        }

        AppSocketServerManager.getInstance().setAppSocketServerListener(new AppSocketServer.OnAppSocketServerListener() {
            @Override
            public void onMessage(String message) {
                LogUtil.d(TAG, "message=" + message);
                if (message.startsWith(Constants.APP_REPLY_CHECK_SCRCPY_SERVER_JAR)) {
                    String[] split = message.split(Constants.COMMAND_SPLIT);
                    if (split[1].equals("0")) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                LogUtil.d(TAG, "serverIp=" + serverIp);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        showLoadingDialog("正在连接投屏服务");
                                    }
                                });

                                if (SendCommands.getInstance(VehicleActivity.this).sendServerJar(serverIp) == 0) {
                                    mMirrorCastServerConnected = true;

                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissLoadingDialog();
                                            toast("投屏服务连接成功！");
                                        }
                                    });
                                } else {
                                    mMirrorCastServerConnected = false;

                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissLoadingDialog();
                                            toast("投屏服务连接失败！");
                                        }
                                    });
                                }
                            }
                        }).start();
                    } else if (split[1].equals("1")) {
                        mMirrorCastServerConnected = true;

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                dismissLoadingDialog();
                                toast("投屏服务连接成功！");
                            }
                        });
                    }
                }
            }
        });
        new ClientThread(serverIp, true).start();
    }

    // 请求开始录屏
    private void startProjection() {
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, PROJECTION_REQUEST_CODE);
    }

    private void stopProjection() {
        Intent service = new Intent(this, ScreenService.class);
        stopService(service);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        if (requestCode == PROJECTION_REQUEST_CODE) {
            Intent service = new Intent(this, ScreenService.class);
            service.putExtra("code", resultCode);
            service.putExtra("data", data);
            startForegroundService(service);
        }
    }


    /////////////////////////////////// AOA ///////////////////////////////////
    private UsbManager mUsbManager;
    private UsbDeviceConnection mUsbDeviceConnection;
    private static final int AOA_GET_PROTOCOL = 51;
    private static final int AOA_SEND_IDENT = 52;
    private static final int AOA_START_ACCESSORY = 53;
    private static final String AOA_MANUFACTURER = "MobileDriveTech";
    private static final String AOA_MODEL_NAME = "SuperLink";
    private static final String AOA_DESCRIPTION = "MobileDriveTech SuperLink";
    private static final String AOA_VERSION = "1.0.0";
    private static final String AOA_URI = "http://www.abc.com.cn/";
    private static final String AOA_SERIAL_NUMBER = "123456.";
    private PendingIntent mPermissionIntent;
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private static final int VID_ACCESSORY = 0x18D1;
    private static final int PID_ACCESSORY_ONLY = 0x2D00;
    private static final int PID_ACCESSORY_AUDIO_ADB_BULK = 0x2D05;
    private UsbInterface usbInterface;
    private UsbDeviceConnection usbDeviceConnection;
    private UsbEndpoint epIn;
    private UsbEndpoint epOut;
    private boolean isTerminated = false;

    private void registerUsbReceiver() {
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        // PendingIntent.FLAG_INMUTABLE 会导致权限请求失败！！！
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
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
                        UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            // 权限已被授予，可以打开连接外部 USB 设备的通道

//                            openAccessory(accessory);
                            LogUtil.d(TAG, "permission granted for accessory " + accessory);
                        } else {
                            // 未授权访问外部 USB 设备
                            LogUtil.d(TAG, "permission denied for accessory " + accessory);
                        }

                        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            LogUtil.d(TAG, "permission granted for device " + device);
                            // 权限已被授予，可以打开连接外部 USB 设备的通道
//                            openAccessory(accessory);
                            changeToAccessoryMode(device);
                            openAccessory(device);
                        } else {
                            // 未授权访问外部 USB 设备
                            LogUtil.d(TAG, "permission denied for device " + device);
//                            mUsbManager.requestPermission(attachedDevice, mPermissionIntent);
                        }
                    }
                    break;
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                    // 外部 USB 设备已连接
                    UsbDevice attachDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
//                    if (attachDevice != null && isAccessory(attachDevice)) {
//                        LogUtil.d(TAG, "request device permission");
//                        // 请求权限访问外部 USB 设备
//                        mUsbManager.requestPermission(attachDevice, mPermissionIntent);
//                    } else {
//                        changeToAccessoryMode(attachDevice);
//                    }

                    if (mUsbManager.hasPermission(attachDevice)) {
                        openAccessory(attachDevice);
                    } else {
                        mUsbManager.requestPermission(attachDevice, mPermissionIntent);
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

    private void openAccessory(UsbDevice device) {
        usbInterface = device.getInterface(0);
        usbDeviceConnection = mUsbManager.openDevice(device);
        usbDeviceConnection.claimInterface(usbInterface, true);
        epIn = usbInterface.getEndpoint(0);
        epOut = usbInterface.getEndpoint(1);

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!isTerminated) {
                    // 接收数据
                    byte[] buffer = new byte[1024];
                    int bytesRead = usbDeviceConnection.bulkTransfer(epIn, buffer, buffer.length, 0);
                    if (bytesRead > 0) {
                        String response = new String(buffer, 0, bytesRead);
                        LogUtil.d(TAG, "host received=" + response);

                        if (response.startsWith("phone:")) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String str = binding.aoaMsgTv.getText().toString() + "\n";
                                    binding.aoaMsgTv.setText(str + "手机：" + response);
                                }
                            });
                        }
                    }
                }
            }
        }).start();


        sendAOAMsg("msg from car.");
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                // 发送数据
//                byte[] data = "this is usb host".getBytes();
//                int result = usbDeviceConnection.bulkTransfer(epOut, data, data.length, 0);
//                LogUtil.d(TAG, "host send result=" + result);
//            }
//        }).start();
    }

    private void toast2(String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(VehicleActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendAOAMsg(String msg) {
        if (usbDeviceConnection != null && usbInterface != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    epOut = usbInterface.getEndpoint(1);
                    // 发送数据
                    byte[] data = ("car:" + msg).getBytes();
                    int result = usbDeviceConnection.bulkTransfer(epOut, data, data.length, 0);
                    LogUtil.d(TAG, "host send result=" + result);

                    toast2("result=" + result);

//                    if (result > 0) {
//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                String str = binding.aoaMsgTv.getText().toString() + "\n";
//                                binding.aoaMsgTv.setText(str + "车机：" + msg);
//                            }
//                        });
//                    } else {
//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                toast("发送失败！");
//                            }
//                        });
//                    }
                }
            }).start();
        }
    }

    private void closeAccessory() {
        if (usbDeviceConnection != null) {
            LogUtil.d(TAG);
            usbDeviceConnection.releaseInterface(usbInterface);
            usbDeviceConnection.close();
        }
    }

    private boolean isAccessory(UsbDevice usbDevice) {
        return usbDevice.getVendorId() == VID_ACCESSORY
                && usbDevice.getProductId() >= PID_ACCESSORY_ONLY
                && usbDevice.getProductId() <= PID_ACCESSORY_AUDIO_ADB_BULK;
    }

    public boolean changeToAccessoryMode(UsbDevice usbDevice) {
        mUsbDeviceConnection = mUsbManager.openDevice(usbDevice);

        LogUtil.d(TAG);
        if (usbDevice == null) {
            return false;
        }
        if (!getProtocolVersion()) {
            LogUtil.w(TAG, "Change Accessory Mode getProtocolVersion Fail");
            return false;
        }
        if (!sendIdentityStrings()) {
            LogUtil.w(TAG, "Change Accessory Mode sendIdentityStrings Fail");
            return false;
        }
        if (!startAccessoryMode()) {
            LogUtil.w(TAG, "Change Accessory Mode startAccessoryMode Fail");
            return false;
        }
        LogUtil.d(TAG, "Change Accessory Mode Success");
        return true;
    }

    private boolean getProtocolVersion() {
        byte[] buffer = new byte[2];
        if (controlTransferIn(AOA_GET_PROTOCOL, 0, 0, buffer) < 0) {
            LogUtil.w(TAG, "get protocol version fail");
            return false;
        }

        int version = buffer[1] << 8 | buffer[0];
        if (version < 1 || version > 2) {
            LogUtil.e(TAG, "usb device not capable of AOA 1.0 or 2.0, version = " + version);
            return false;
        }
        LogUtil.d(TAG, "usb device AOA version is " + version);
        return true;
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
        LogUtil.d(TAG, "send indentity string success");
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
        if (mUsbDeviceConnection == null) {
            return -1;
        }
        return mUsbDeviceConnection.controlTransfer(UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR, request,
                value, index, buffer, buffer == null ? 0 : buffer.length, 0);
    }

    private int controlTransferIn(int request, int value, int index, byte[] buffer) {
        if (mUsbDeviceConnection == null) {
            return -1;
        }
        return mUsbDeviceConnection.controlTransfer(UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_VENDOR, request,
                value, index, buffer, buffer == null ? 0 : buffer.length, 0);
    }
    /////////////////////////////////// AOA ///////////////////////////////////

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG);
        serverThread.close();
        AppSocketServerManager.getInstance().stopServer();
//        System.exit(0);

        isTerminated = true;
        unregisterUsbReceiver();
        closeAccessory();
    }
}
