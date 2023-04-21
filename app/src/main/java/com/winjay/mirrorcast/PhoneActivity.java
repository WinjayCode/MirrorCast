package com.winjay.mirrorcast;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.winjay.mirrorcast.aoa.PhoneAOAActivity;
import com.winjay.mirrorcast.common.BaseActivity;
import com.winjay.mirrorcast.databinding.ActivityPhoneBinding;
import com.winjay.mirrorcast.wifidirect.ServerThread;
import com.winjay.mirrorcast.wifidirect.WIFIDirectActivity;

/**
 * @author F2848777
 * @date 2023-03-31
 */
public class PhoneActivity extends BaseActivity implements View.OnClickListener {
    private static final String TAG = PhoneActivity.class.getSimpleName();

    private ActivityPhoneBinding binding;

    private ServerThread serverThread;


    @Override
    protected View viewBinding() {
        binding = ActivityPhoneBinding.inflate(getLayoutInflater());
        return binding.getRoot();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        super.onCreate(savedInstanceState);
        initView();

        serverThread = new ServerThread();
        serverThread.start();
    }

    private void initView() {
        binding.btnWifiP2p.setOnClickListener(this);
        binding.btnAoa.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        // wifi p2p
        if (view == binding.btnWifiP2p) {
            startActivity(WIFIDirectActivity.class);
        }
        // aoa
        if (view == binding.btnAoa) {
            startActivity(PhoneAOAActivity.class);
        }
    }
}
