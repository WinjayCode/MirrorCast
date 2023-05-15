package com.winjay.mirrorcast.aoa;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.winjay.mirrorcast.Constants;
import com.winjay.mirrorcast.util.LogUtil;

import java.net.InetSocketAddress;

/**
 * @author F2848777
 * @date 2023-05-15
 */
public class PhoneAOAService extends Service implements PhoneAOASocketServer.OnWebSocketServerListener {
    private static final String TAG = PhoneAOAService.class.getSimpleName();

    private PhoneAOASocketServer phoneAOASocketServer;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new MyBinder();
    }

    public class MyBinder extends Binder {

        public PhoneAOAService getService() {
            return PhoneAOAService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtil.d(TAG);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("1", "PhoneAOAService", NotificationManager.IMPORTANCE_DEFAULT));
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "1");
        startForeground(1, builder.build());

        phoneAOASocketServer = new PhoneAOASocketServer(new InetSocketAddress(Constants.PHONE_MAIN_SCREEN_MIRROR_CAST_SERVER_PORT));
        phoneAOASocketServer.setOnWebSocketServerListener(this);
        phoneAOASocketServer.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG);

        try {
            phoneAOASocketServer.stop();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) {
        phoneAOASocketServer.sendMessage(message);
    }

    @Override
    public void onOpen() {
        LogUtil.d(TAG);
    }

    @Override
    public void onMessage(String message) {
        LogUtil.d(TAG, "message=" + message);
        // 转发scrcpy-server.jar的消息数据给AOA Host端
        AOAAccessoryManager.getInstance().sendAOAMessage(message);
    }

    @Override
    public void onReceiveByteData(byte[] data) {
        LogUtil.d(TAG, "encode data.length=" + data.length);
        // 转发scrcpy-server.jar的录屏数据给AOA Host端
        AOAAccessoryManager.getInstance().sendAOAByte(data);
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
