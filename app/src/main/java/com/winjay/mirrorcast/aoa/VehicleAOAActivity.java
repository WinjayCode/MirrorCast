package com.winjay.mirrorcast.aoa;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;

import com.winjay.mirrorcast.common.BaseActivity;
import com.winjay.mirrorcast.databinding.ActivityVehicleAoaBinding;
import com.winjay.mirrorcast.util.LogUtil;

/**
 * @author F2848777
 * @date 2023-04-10
 */
public class VehicleAOAActivity extends BaseActivity implements View.OnClickListener, AOAHostManager.AOAHostMessageListener {
    private static final String TAG = VehicleAOAActivity.class.getSimpleName();

    private ActivityVehicleAoaBinding binding;

    private static final int MESSAGE_LOG = 1;

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

        AOAHostManager.getInstance().start(this);
        AOAHostManager.getInstance().setAOAHostMessageListener(this);
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

            AOAHostManager.getInstance().sendAOAMsg(binding.aoaEd.getText().toString());
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG);

        AOAHostManager.getInstance().stop();
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

    @Override
    public void onReceivedData(byte[] data, int length) {
        String response = new String(data, 0, length);

        if (response.startsWith("phone:")) {
            LogUtil.d(TAG, "host received=" + response);

            Message m = Message.obtain(mHandler, MESSAGE_LOG);
            m.obj = response;
            mHandler.sendMessage(m);
        }
    }
}
