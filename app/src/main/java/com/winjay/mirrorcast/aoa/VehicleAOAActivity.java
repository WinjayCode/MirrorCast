package com.winjay.mirrorcast.aoa;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.winjay.mirrorcast.ADBCommands;
import com.winjay.mirrorcast.Constants;
import com.winjay.mirrorcast.common.BaseActivity;
import com.winjay.mirrorcast.databinding.ActivityVehicleAoaBinding;
import com.winjay.mirrorcast.decode.ScreenDecoder;
import com.winjay.mirrorcast.util.DisplayUtil;
import com.winjay.mirrorcast.util.LogUtil;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author F2848777
 * @date 2023-04-10
 */
public class VehicleAOAActivity extends BaseActivity implements View.OnClickListener, AOAHostManager.AOAHostListener {
    private static final String TAG = VehicleAOAActivity.class.getSimpleName();

    private ActivityVehicleAoaBinding binding;

    private static final int MESSAGE_STR = 1;
    private static final int MESSAGE_START_SCREEN_DECODE = 2;

    private UsbDevice usbDevice;
    private UsbDeviceConnection usbDeviceConnection;
    private UsbInterface usbInterface;

    private int[] screenSize = new int[2];
    private int maxSize;
    private ScreenDecoder mScreenDecoder;
    private float phoneMainScreenWidthRatio;
    private float phoneMainScreenHeightRatio;

    private boolean startMirrorCastSucceed = false;
    private boolean isStartMirrorCast = false;

    @Override
    public boolean isFullScreen() {
        return true;
    }

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

        AOAHostManager.getInstance().setAOAHostListener(this);
        AOAHostManager.getInstance().start(this);
    }

    private void initView() {
        screenSize = DisplayUtil.getScreenSize(this);
        maxSize = screenSize[1];

        binding.aoaSendBtn.setOnClickListener(this);

        binding.phoneMainScreenSv.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                String mockEvent = Constants.SCRCPY_COMMAND_MOTION_EVENT
                        + Constants.COMMAND_SPLIT
                        + event.getAction()
                        + Constants.COMMAND_SPLIT
                        + (int) (event.getX() * phoneMainScreenWidthRatio)
                        + Constants.COMMAND_SPLIT
                        + (int) (event.getY() * phoneMainScreenHeightRatio);
                LogUtil.d(TAG, "mirror screen event=" + mockEvent);
                // 发送给手机AOAAccessory，手机AOAAccessory再发送给手机scrcpy的websocketclient端
                AOAHostManager.getInstance().sendAOAMessage(mockEvent);
                return true;
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v == binding.aoaSendBtn) {
            if (TextUtils.isEmpty(binding.aoaEd.getText().toString())) {
                dialogToast("内容不能为空！");
                return;
            }

            AOAHostManager.getInstance().sendAOAMessage(binding.aoaEd.getText().toString());
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG);

        if (mScreenDecoder != null) {
            mScreenDecoder.stopDecode();
        }

        AOAHostManager.getInstance().stop();
    }

    Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STR:
                    binding.aoaMsgTv.setText(binding.aoaMsgTv.getText() + "\n" + (String) msg.obj);
                    break;
                case MESSAGE_START_SCREEN_DECODE:
                    RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) binding.phoneMainScreenSv.getLayoutParams();
                    layoutParams.width = msg.arg1;
                    layoutParams.height = msg.arg2;
                    binding.phoneMainScreenSv.setLayoutParams(layoutParams);

                    binding.phoneMainScreenSv.getHolder().addCallback(new SurfaceHolder.Callback() {
                        @Override
                        public void surfaceCreated(@NonNull SurfaceHolder holder) {
                        }

                        @Override
                        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                            if (binding.phoneMainScreenSv.getVisibility() == View.VISIBLE) {
                                mScreenDecoder = new ScreenDecoder();
                                mScreenDecoder.startDecode(binding.phoneMainScreenSv.getHolder().getSurface(), msg.arg1, msg.arg2);
                                isStartMirrorCast = true;
                            }
                        }

                        @Override
                        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                        }
                    });
                    binding.phoneMainScreenSv.setVisibility(View.VISIBLE);
                    break;
            }
        }
    };

    @Override
    public void connectSucceed(UsbDevice usbDevice, UsbDeviceConnection usbDeviceConnection) {
        LogUtil.d(TAG);
        this.usbDevice = usbDevice;
        this.usbDeviceConnection = usbDeviceConnection;
    }

    @Override
    public void onReceivedData(byte[] data, int length) {
//        LogUtil.d(TAG, "startMirrorCastSucceed=" + startMirrorCastSucceed + ", isStartMirrorCast=" + isStartMirrorCast);
        if (startMirrorCastSucceed && isStartMirrorCast) {
            if (mScreenDecoder != null) {
//                LogUtil.d(TAG, "decoding!");
                // 解析投屏视频数据
                mScreenDecoder.decodeData(data);
            }
            return;
        }

        String message = new String(data, 0, length);
        LogUtil.d(TAG, "message=" + message);

        // 接收手机端是否存在scrcpy-server.jar文件的结果
        if (message.startsWith(Constants.APP_REPLY_CHECK_SCRCPY_SERVER_JAR)) {
            String[] split = message.split(Constants.COMMAND_SPLIT);
            if (split[1].equals("0")) {
                // 车机推送scrcpy-server.jar包到手机端
                boolean result = sendServerJar();
                if (!result) {
                    return;
                }

                // 使用adb启动手机端scrcpy服务开始投屏
                startMirrorCast();
            } else if (split[1].equals("1")) {
                // 使用adb启动手机端scrcpy服务开始投屏
                startMirrorCast();
            }
            return;
        }

        // 解析投屏视频宽高信息
        if (message.startsWith(Constants.SCRCPY_REPLY_VIDEO_SIZE)) {
            String[] split = message.split(Constants.COMMAND_SPLIT);
            int videoWidth = Integer.parseInt(split[1]);
            int videoHeight = Integer.parseInt(split[2]);
            float widthRatio = Float.parseFloat(split[3]);
            float heightRatio = Float.parseFloat(split[4]);
            LogUtil.d(TAG, "computed videoWidth=" + videoWidth + " videoHeight=" + videoHeight + ", widthRatio=" + widthRatio + ", heightRatio=" + heightRatio);

            phoneMainScreenWidthRatio = widthRatio;
            phoneMainScreenHeightRatio = heightRatio;

            Message m = Message.obtain(mHandler, MESSAGE_START_SCREEN_DECODE);
            m.arg1 = videoWidth;
            m.arg2 = videoHeight;
            mHandler.sendMessage(m);
            return;
        }

        Message m = Message.obtain(mHandler, MESSAGE_STR);
        m.obj = message;
        mHandler.sendMessage(m);
    }

    @Override
    public void onDetached() {
        finish();
    }

    private boolean sendServerJar() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(() -> {
            Message m1 = Message.obtain(mHandler, MESSAGE_STR);
            m1.obj = "开始推送scrcpy-server.jar";
            mHandler.sendMessage(m1);

            usbInterface = findAdbInterface(usbDevice);
            if (usbDeviceConnection.claimInterface(usbInterface, false)) {
                boolean sendResult = ADBCommands.getInstance(VehicleAOAActivity.this).sendServerJar(usbDeviceConnection, usbInterface);
                LogUtil.d(TAG, "sendResult=" + sendResult);

                Message m2 = Message.obtain(mHandler, MESSAGE_STR);
                m2.obj = sendResult ? "scrcpy-server.jar推送完成！" : "scrcpy-server.jar推送失败！";
                mHandler.sendMessage(m2);

                return sendResult;
            }
            return false;
        });
        boolean result = false;
        try {
            result = future.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        executor.shutdown();
        LogUtil.d(TAG, "result=" + result);
        return result;
    }

    // searches for an adb interface on the given USB device
    private UsbInterface findAdbInterface(UsbDevice device) {
        int count = device.getInterfaceCount();
        for (int i = 0; i < count; i++) {
            UsbInterface intf = device.getInterface(i);
            if (intf.getInterfaceClass() == 255 && intf.getInterfaceSubclass() == 66 &&
                    intf.getInterfaceProtocol() == 1) {
                return intf;
            }
        }
        return null;
    }

    private void startMirrorCast() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                UsbManager mManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                UsbDeviceConnection connection = mManager.openDevice(usbDevice);
                usbInterface = findAdbInterface(usbDevice);
                if (connection != null) {
                    if (connection.claimInterface(usbInterface, true)) {
                        if (ADBCommands.getInstance(VehicleAOAActivity.this).startMirrorCast(connection, usbInterface, "localhost",
                                Constants.PHONE_MAIN_SCREEN_MIRROR_CAST_SERVER_PORT, 0, maxSize, "0")) {
                            LogUtil.d(TAG, "scrcpy start success.");
                            startMirrorCastSucceed = true;

                            Message m = Message.obtain(mHandler, MESSAGE_STR);
                            m.obj = "投屏启动成功!";
                            mHandler.sendMessage(m);
                        } else {
                            LogUtil.e(TAG, "scrcpy start failure!");
                            startMirrorCastSucceed = false;

                            Message m = Message.obtain(mHandler, MESSAGE_STR);
                            m.obj = "投屏启动失败!";
                            mHandler.sendMessage(m);
                        }
                    }
                }
            }
        }).start();
    }
}
