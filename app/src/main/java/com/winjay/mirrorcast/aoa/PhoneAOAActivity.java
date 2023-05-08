package com.winjay.mirrorcast.aoa;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;

import com.winjay.mirrorcast.Constants;
import com.winjay.mirrorcast.common.BaseActivity;
import com.winjay.mirrorcast.databinding.ActivityPhoneAoaBinding;
import com.winjay.mirrorcast.util.LogUtil;

import java.io.File;
import java.net.InetSocketAddress;

/**
 * @author F2848777
 * @date 2023-04-10
 */
public class PhoneAOAActivity extends BaseActivity implements View.OnClickListener,
        AOAAccessoryManager.AOAAccessoryListener, PhoneAOASocketServer.OnWebSocketServerListener {
    private static final String TAG = PhoneAOAActivity.class.getSimpleName();

    private ActivityPhoneAoaBinding binding;

    private static final int MESSAGE_STR = 1;

    private PhoneAOASocketServer phoneAOASocketServer;

    @Override
    protected View viewBinding() {
        binding = ActivityPhoneAoaBinding.inflate(getLayoutInflater());
        return binding.getRoot();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        LogUtil.d(TAG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        super.onCreate(savedInstanceState);
        initView();

        phoneAOASocketServer = new PhoneAOASocketServer(new InetSocketAddress(Constants.PHONE_MAIN_SCREEN_MIRROR_CAST_SERVER_PORT));
        phoneAOASocketServer.setOnWebSocketServerListener(this);
        phoneAOASocketServer.start();

        AOAAccessoryManager.getInstance().setAOAAccessoryListener(this);
        AOAAccessoryManager.getInstance().start(this);
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

            AOAAccessoryManager.getInstance().sendAOAMessage(binding.aoaEd.getText().toString());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG);

        try {
            phoneAOASocketServer.stop();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        AOAAccessoryManager.getInstance().stop();
    }

    Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STR:
                    binding.aoaMsgTv.setText(binding.aoaMsgTv.getText() + "\n" + (String) msg.obj);
                    break;
            }
        }
    };

    @Override
    public void connectSucceed() {
        LogUtil.d(TAG);
        // 通知车机端当前手机端设备data/local/tmp/目录下是否存在scrcpy-server.jar文件
        notifyScrcpyServerJarIsExist();
    }

    @Override
    public void onReceivedData(byte[] data, int length) {
        String message = new String(data, 0, length);
        LogUtil.d(TAG, "message=" + message);

        Message m = Message.obtain(mHandler, MESSAGE_STR);
        m.obj = message;
        mHandler.sendMessage(m);

        // 转发AOA Host端的数据给scrcpy-server.jar
        phoneAOASocketServer.sendMessage(message);
    }

    private void notifyScrcpyServerJarIsExist() {
        int isExist = 0;
        File file = new File("data/local/tmp/scrcpy-server.jar");
        if (file.exists()) {
            isExist = 1;
        }
        AOAAccessoryManager.getInstance().sendAOAMessage(Constants.APP_REPLY_CHECK_SCRCPY_SERVER_JAR + Constants.COMMAND_SPLIT + isExist);
    }

    @Override
    public void onOpen() {
        LogUtil.d(TAG);
    }

    @Override
    public void onMessage(String message) {
        LogUtil.d(TAG, "message=" + message);
        // 转发scrcpy-server.jar的数据给AOA Host端
        AOAAccessoryManager.getInstance().sendAOAMessage(message);
    }

    @Override
    public void onClose(String reason) {
        LogUtil.d(TAG, "reason=" + reason);
    }

    @Override
    public void onError(String errorMessage) {
        LogUtil.e(TAG, "errorMessage=" + errorMessage);
    }
}
