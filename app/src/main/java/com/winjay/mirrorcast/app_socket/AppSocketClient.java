package com.winjay.mirrorcast.app_socket;

import com.winjay.mirrorcast.util.LogUtil;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;

public class AppSocketClient extends WebSocketClient {
    private static final String TAG = AppSocketClient.class.getSimpleName();

    private AppSocketClientListener appSocketClientListener;

    private Timer timer;
    private TimerTask timerTask;

    public AppSocketClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        LogUtil.d(TAG);

        // 使用心跳机制，保持websocket长链接（因为websocket在不确定的时间内两端没有任何通信时会断开连接）
        timer = new Timer();
        timerTask = new TimerTask() {

            @Override
            public void run() {
                LogUtil.d(TAG, "send ping.");
                send("ping");
            }
        };
        // 间隔30s
        timer.schedule(timerTask,0, 30000);
    }

    @Override
    public void onMessage(String message) {
        LogUtil.d(TAG, "message=" + message);
        if (message.equals("pong")) {
            LogUtil.d(TAG, "receive pong.");
            return;
        }

        if (appSocketClientListener != null) {
            appSocketClientListener.onMessage(message);
        }
        AppSocketManager.getInstance().handleMessage(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        LogUtil.d(TAG, "reason=" + reason);

        timer.cancel();
    }

    @Override
    public void onError(Exception ex) {
        LogUtil.e(TAG, ex.getMessage());

        timer.cancel();
    }

    public void setAppSocketClientListener(AppSocketClientListener listener) {
        appSocketClientListener = listener;
    }

    public interface AppSocketClientListener {
        void onMessage(String message);
    }
}
