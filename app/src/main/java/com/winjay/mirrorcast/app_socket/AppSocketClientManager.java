package com.winjay.mirrorcast.app_socket;

import com.winjay.mirrorcast.Constants;
import com.winjay.mirrorcast.util.LogUtil;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author F2848777
 * @date 2022-11-29
 */
public class AppSocketClientManager {
    private static final String TAG = AppSocketClientManager.class.getSimpleName();
    private static volatile AppSocketClientManager mInstance;

    private AppSocketClient mAppSocketClient;

    private AppSocketClientManager() {
    }

    public static AppSocketClientManager getInstance() {
        if (mInstance == null) {
            synchronized (AppSocketClientManager.class) {
                if (mInstance == null) {
                    mInstance = new AppSocketClientManager();
                }
            }
        }
        return mInstance;
    }

    public void connect(String serverIp) {
        if (mAppSocketClient == null) {
            LogUtil.d(TAG, "serverIp=" + serverIp);
            try {
                URI uri = new URI("ws://" + serverIp + ":" + Constants.APP_SOCKET_PORT);
                mAppSocketClient = new AppSocketClient(uri);
                mAppSocketClient.connect();
            } catch (Exception e) {
                LogUtil.e(TAG, "error=" + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void sendMessage(String message) {
        if (mAppSocketClient != null) {
            LogUtil.d(TAG, "message=" + message);
            try {
                mAppSocketClient.send(message);
            } catch (Exception e) {
                LogUtil.e(TAG, e.getMessage());
            }
        }
    }

    public void setAppSocketClientListener(AppSocketClient.AppSocketClientListener listener) {
        if (mAppSocketClient != null) {
            mAppSocketClient.setAppSocketClientListener(listener);
        }
    }

    public void close() {
        if (mAppSocketClient != null) {
            LogUtil.d(TAG);
            mAppSocketClient.close();
        }
    }
}
