/*
 * Copyright (C) 2015 Zlianjie Inc. All rights reserved.
 */
package com.zlianjie.coolwifi.speedtest;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.zlianjie.coolwifi.R;
import com.zlianjie.coolwifi.fragment.BaseWiFiStateFragment;
import com.zlianjie.coolwifi.util.IntentConstants;
import com.zlianjie.coolwifi.util.UIUtils;
import com.zlianjie.coolwifi.wifi.AccessPoint;

/**
 * 测速
 *
 * @author Jierain
 * @since 2015-06-01
 */
public class SpeedTestFragment extends BaseWiFiStateFragment {

    private static final String CONTENT_VIEW_ID_KEY = "CONTENT_VIEW_ID";

    public static SpeedTestFragment newInstance(int contentId, Bundle bundle) {
        SpeedTestFragment fragment = new SpeedTestFragment();
        if (bundle == null) {
            bundle = new Bundle();
        }
        bundle.putInt(CONTENT_VIEW_ID_KEY, contentId);
        fragment.setArguments(bundle);
        return fragment;
    }

    private SpeedTestView mSpeedTestView;

    private Handler mHandler = new Handler();

    public SpeedTestFragment() {
        // Required empty public constructor
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View rootView = inflater.inflate(R.layout.speed_test_view, container, false);

        String ssid = null;
        Bundle args = getArguments();
        if (args != null) {
            ssid = args.getString(IntentConstants.EXTRA_AP_SSID);
        }

        TextView ssidView = (TextView) rootView.findViewById(R.id.ssid);
        if (!TextUtils.isEmpty(ssid)) {
            ssidView.setText(ssid);
        }

        mSpeedTestView = (SpeedTestView) rootView;
        mSpeedTestView.setSpeedTestListener(new SpeedTestView.OnSpeedTestListener() {
            private void setSpeedTaskStopViewState() {

            }

            @Override
            public void onSpeedTaskCancel() {
                setSpeedTaskStopViewState();
            }

            @Override
            public boolean onSpeedTaskStart() {
                mSpeedTestView.startTask();
                return true;
            }

            @Override
            public void onSpeedTaskFinished() {
                setSpeedTaskStopViewState();
            }

            @Override
            public void onCountDownTimeChange(long countDownTime, long totalTime) {

            }

            @Override
            public void onSpeedTaskFail() {
                setSpeedTaskStopViewState();
            }
        });

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (mSpeedTestView != null) {
            mSpeedTestView.executeSpeedTest();
        }
    }

    @Override
    public void onDestroyView() {
        mHandler.removeCallbacksAndMessages(null);
        if (mSpeedTestView != null) {
            mSpeedTestView.stopTask(true);
        }
        super.onDestroyView();
    }

    @Override
    protected void onWiFiDisconnected() {
        if (mSpeedTestView != null) {
            mSpeedTestView.cancelTask();
        }
        FragmentActivity act = getActivity();
        if (act != null && !act.isFinishing()) {
            UIUtils.showToast(act, R.string.speed_test_disconnected);
            act.finish();
        }
    }

    @Override
    protected void onWiFiConnected(AccessPoint activeAp) {
    }
}