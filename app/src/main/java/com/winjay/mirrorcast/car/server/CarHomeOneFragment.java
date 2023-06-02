package com.winjay.mirrorcast.car.server;


import android.os.Handler;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.winjay.mirrorcast.R;
import com.winjay.mirrorcast.common.BaseFragment;
import com.winjay.mirrorcast.databinding.FragmentCarHomeOneBinding;

public class CarHomeOneFragment extends BaseFragment<FragmentCarHomeOneBinding> {
    private static final String TAG = CarHomeOneFragment.class.getSimpleName();

    private static final int DELAY_TIME = 10000;
    private Handler mHandler = new Handler();
    private int mIndex = 1;

    @Override
    protected FragmentCarHomeOneBinding onCreateViewBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent) {
        return FragmentCarHomeOneBinding.inflate(inflater, parent, false);
    }

    final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            switchImage();
            mHandler.postDelayed(this, DELAY_TIME);
        }
    };

    @Override
    protected void lazyLoad() {
        mHandler.postDelayed(runnable, DELAY_TIME);
    }

    private void switchImage() {
        if (mIndex == 0) {
            getBinding().carIv.setImageResource(R.mipmap.car_bg);
            mIndex = 1;
        } else {
            getBinding().carIv.setImageResource(R.mipmap.car2_bg);
            mIndex = 0;
        }
    }

    @Override
    protected void stopLoad() {
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
    }
}
