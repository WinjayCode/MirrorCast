package com.winjay.mirrorcast.car.server;


import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.winjay.mirrorcast.common.BaseFragment;
import com.winjay.mirrorcast.databinding.FragmentCarHomeOneBinding;

public class CarHomeOneFragment extends BaseFragment<FragmentCarHomeOneBinding> {
    private static final String TAG = CarHomeOneFragment.class.getSimpleName();

    @Override
    protected FragmentCarHomeOneBinding onCreateViewBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent) {
        return FragmentCarHomeOneBinding.inflate(inflater, parent, false);
    }

    @Override
    protected void lazyLoad() {
    }
}
