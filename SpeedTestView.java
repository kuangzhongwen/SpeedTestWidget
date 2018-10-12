/*
 * Copyright (C) 2014 Zlianjie Inc. All rights reserved.
 */
package com.zlianjie.coolwifi.speedtest;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import com.zlianjie.android.widget.ColorButton;
import com.zlianjie.coolwifi.R;
import com.zlianjie.coolwifi.util.SharedPrefUtils;
import com.zlianjie.coolwifi.util.TrafficStatsUtils;
import com.zlianjie.coolwifi.util.UIUtils;
import com.zlianjie.coolwifi.wifi.AccessPoint;
import com.zlianjie.coolwifi.wifi.WifiControlManager;

/**
 * 手动测速界面
 * @author lisen
 * @since 2014年7月23日
 *
 * @update kzw 2015年7月31日
 */
public class SpeedTestView extends RelativeLayout implements View.OnClickListener {

    /** 倒计时消息标识 */
    private static final int MSG_COUNT_DOWN_SUB = 1;
    /** 测速状态变更 */
    private static final int MSG_CHANGE_TEST_STATE = 2;
    /** 进度条更新 */
    private static final int MSG_PROGRESS_UPDATE = 3;
    /** 指针刷新 */
    private static final int MSG_POINTER_UPDATE = 4;

    /** 每次减去200毫秒 */
    private static final int COUNT_DOWN_SUB_IN_MILLIS = 200;
    /** 每次更新进度条的时间 */
    private static final int UPDATE_PROGRESS_SUB_IN_MILLIS = 50;
    /** 进度条每次增加进度 */
    private static final float PROGRESS_INCREASE = UPDATE_PROGRESS_SUB_IN_MILLIS * 1.3f;
    /** 指针刷新每帧的时长 */
    private static final long EACH_FRAME_DURATION = 10;
    /** 指针刷新帧数 */
    private static final int FRAME_COUNT = (int) (SpeedTestView.COUNT_DOWN_SUB_IN_MILLIS / EACH_FRAME_DURATION);
    /** 指针每帧刷新角度加速度 */
    private static final float FRAME_ANGLE_ACCELERATION = 3.5f;
    /** 默认速度 */
    private static final int DEFAULT_SPEED = 0;

    /** 仪表盘 */
    private DialChartView mChartView;
    /** 测试结果view */
    private SpeedTestResultView mSpeedTestResultView;
    /** 测试按钮 */
    private ColorButton mTestButton;
    
    /** 手动测速Task */
    private SpeedTestTask mSpeedTestTask;
    /** 保存速率的preference关键字 */
    private String mSpeedPrefKey = null;
    /** 接收外部传入的手动测速监听器 */
    private OnSpeedTestListener mSpeedTestListener;

    /** 当前平均速率 */
    private int mAvgSpeed;
    /** 记录最大速率 */
    private int mMaxSpeed;
    /** 当前进度 */
    private int mProgress;
    /** 还剩的倒计时间 */
    private long mCountDownMillis;
    /** 指针目标角度 */
    private float mPointerTargetAngle = DialChartView.MIN_ANGLE;
    /** 速度每次更新时间 */
    private long mSpeedUpdateTime;
    /** 指针刷新每帧角度 */
    private float mPointerFrameAngle;
    /** 速度值 ＋ 单位 */
    private String mSpeedValue = DEFAULT_SPEED + TrafficStatsUtils.KB_S;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
            case MSG_COUNT_DOWN_SUB:
                handleCountDown();
                break;
            case MSG_CHANGE_TEST_STATE:
                handleTaskStateChange(msg);
                break;
            case MSG_PROGRESS_UPDATE:
                handleProgressUpdate();
                break;
            case MSG_POINTER_UPDATE:
                handlePointerUpdate();
                break;
            default:
                break;
            }
        }
    };
    
    /** 手动测速信息变化监听器 */
    private SpeedTestTask.SpeedTestTaskListener mSpeedTestTaskListener = new SpeedTestTask.SpeedTestTaskListener () {
        @Override
        public void onSpeedChange(final SpeedTestTask.SpeedTest speedTest) {
            mAvgSpeed = speedTest.getAvgSpeed();
            //保存最大速率
            mMaxSpeed = Math.max(mMaxSpeed, mAvgSpeed);
        }

        @Override
        public void onStateChange(final int taskState) {
            mHandler.obtainMessage(MSG_CHANGE_TEST_STATE, taskState, 0).sendToTarget();
        }
    };
    
    /**
     * 构造方法
     * @param context {@link Context}
     */
    public SpeedTestView(Context context) {
        super(context);
        init();
    }
    
    /**
     * 构造方法
     * @param context {@link Context}
     * @param attrs {@link AttributeSet}
     */
    public SpeedTestView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    /**
     * @param context {@link Context}
     * @param attrs {@link AttributeSet}
     * @param defStyleAttr theme
     */
    public SpeedTestView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    /**
     * 初始化
     */
    private void init() {
        mSpeedTestTask = new SpeedTestTask(mSpeedTestTaskListener);
    }
    
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mChartView = (DialChartView) findViewById(R.id.dial_chart_view);
        mSpeedTestResultView = (SpeedTestResultView) findViewById(R.id.speed_test_result_view);
        mTestButton = (ColorButton) findViewById(R.id.test_speed_button);
        mTestButton.setOnClickListener(this);
        // 设置默认速度
        setSpeedInfo(DEFAULT_SPEED);
        getSpeedPrefKey();
        setTestButtonEnable(false);
    }
    
    @Override
    public void onClick(View v) {
        switch(v.getId()) {
        case R.id.test_speed_button:
            executeSpeedTest();
            break;
        }
    }
    
    /**
     * 执行测速
     */
    public void executeSpeedTest() {
        if (!WifiControlManager.getInstance().isWifiConnected()) {
            UIUtils.showToast(getContext(), R.string.speed_test_wifi_error);
            return;
        }
        if (mTestButton.getText().toString().equals(UIUtils.getString(R.string.speed_test_retry))) {
            mSpeedTestResultView.showTestResultView(0, false);
        }
        mSpeedTestResultView.setRunState(SpeedTestResultView.RunState.RUNNING);
        mSpeedTestResultView.showProgress();
        setTestButtonEnable(false);
        if (mSpeedTestListener != null && mSpeedTestListener.onSpeedTaskStart()) {
            return;
        }
        startTask();
    }
    
    /**
     * 开始任务
     */
    public void startTask() {
        mSpeedTestTask.startTask();
        mAvgSpeed = 0;
        //更新速率文字
        setSpeedInfo(mAvgSpeed);
        //开始倒计时
        mCountDownMillis = SpeedTestTask.COUNT_DOWN_IN_MILLIS;
        mHandler.sendEmptyMessageDelayed(MSG_PROGRESS_UPDATE, UPDATE_PROGRESS_SUB_IN_MILLIS);
        mHandler.sendEmptyMessageDelayed(MSG_COUNT_DOWN_SUB, COUNT_DOWN_SUB_IN_MILLIS);
    }
    
    /**
     * 停止任务
     * @param isUIExit 是否上传最大平均速率
     */
    public void stopTask(boolean isUIExit) {
        mSpeedTestResultView.setRunState(SpeedTestResultView.RunState.END);
        setTestButtonEnable(true);
        mCountDownMillis = 0;
        mSpeedTestTask.stopTask(isUIExit);
        mChartView.clearUp();
        if (isUIExit) {
            mHandler.removeCallbacksAndMessages(null);
            //保存本次速率最大速率
            saveSpeed();
        }
    }
    
    /**
     * 取消Task
     */
    public void cancelTask() {
        mCountDownMillis = 0;
        mSpeedTestTask.killTask();
    }

    /**
     * 设置速率信息
     * @param speed 速率
     */
    private void setSpeedInfo(int speed) {
        mSpeedValue = TrafficStatsUtils.getRateString(speed);
        mSpeedTestResultView.setSpeedTextValue(mSpeedValue);
    }

    /**
     * 获取保存速率的preference key
     */
    private void getSpeedPrefKey() {
        AccessPoint accessPoint = WifiControlManager.getInstance().getActiveAp();
        if (accessPoint != null) {
            mSpeedPrefKey = accessPoint.getEssId();
        } else {
            mSpeedPrefKey = null;
        }
    }

    /**
     * 保存最大测速速率
     */
    private void saveSpeed() {
        if (!TextUtils.isEmpty(mSpeedPrefKey)) {
            if (mMaxSpeed > 0) {
                SharedPrefUtils.setInt(mSpeedPrefKey, mMaxSpeed);
            }
        }
    }

    /**
     * 切换到结束view
     */
    private void switchToEndView() {
        mProgress = 0;
        mHandler.removeMessages(MSG_PROGRESS_UPDATE);
        setSpeedInfo(mAvgSpeed);
        mHandler.removeMessages(MSG_POINTER_UPDATE);
        updatePointer(DialChartView.MIN_ANGLE);
        // 加快指针收回速度
        mPointerFrameAngle *= FRAME_ANGLE_ACCELERATION;
        mSpeedTestResultView.setRunState(SpeedTestResultView.RunState.END);
        mSpeedTestResultView.hideProgress(mAvgSpeed);
        setTestButtonEnable(true);
    }
    
    /**
     * 设置测试按钮是否可用
     * @param enable
     */
    private void setTestButtonEnable(boolean enable) {
        mTestButton.setEnabled(enable);
        mTestButton.setText(enable ? R.string.speed_test_retry : R.string.speed_testing);
    }
    
    /**
     * 更新表盘
     * @param speed
     */
    private void updateChartView(int speed) {
        mPointerTargetAngle = mChartView.calculateAngle(speed);
        updatePointer(mPointerTargetAngle);
    }

    /**
     * 更新指针
     * @param angle
     */
    private void updatePointer(float angle) {
        if (angle != 0) {
            mSpeedUpdateTime = SpeedTestView.COUNT_DOWN_SUB_IN_MILLIS;
            mPointerFrameAngle = (angle - mChartView.getCurrAngle()) / FRAME_COUNT;
            mHandler.sendEmptyMessageDelayed(MSG_POINTER_UPDATE, EACH_FRAME_DURATION);
        }
    }

    /**
     * 处理倒计时
     */
    private void handleCountDown() {
        mCountDownMillis -= COUNT_DOWN_SUB_IN_MILLIS;
        // 倒计时完成
        if (mCountDownMillis <= 0 || mSpeedTestResultView.getRunState() == SpeedTestResultView.RunState.END) {
            return;
        }
        // 更新速率文字
        setSpeedInfo(mAvgSpeed);
        // 更新指针
        updateChartView(mAvgSpeed);
        if (mSpeedTestListener != null) {
            mSpeedTestListener.onCountDownTimeChange(mCountDownMillis, SpeedTestTask.COUNT_DOWN_IN_MILLIS);
        }
        mHandler.sendEmptyMessageDelayed(MSG_COUNT_DOWN_SUB, COUNT_DOWN_SUB_IN_MILLIS);
    }

    /**
     * 处理测试任务状态改变
     * @param msg
     */
    private void handleTaskStateChange(Message msg) {
        switch (msg.arg1) {
        case SpeedTestTask.TASK_STATE_START:
            break;

        case SpeedTestTask.TASK_STATE_CANCEL:
            if (mSpeedTestListener != null) {
                mSpeedTestListener.onSpeedTaskCancel();
            }
            switchToEndView();
            break;

        case SpeedTestTask.TASK_STATE_FINISHED:
            if (mSpeedTestListener != null) {
                mSpeedTestListener.onSpeedTaskFinished();
            }
            break;

        case SpeedTestTask.TASK_STATE_FAIL:
            if (mSpeedTestListener != null) {
                mSpeedTestListener.onSpeedTaskFail();
            }
            break;

        default:
            break;
        }
    }

    /**
     * 处理进度条进度更新
     */
    private void handleProgressUpdate() {
        if (mProgress >= SpeedTestTask.COUNT_DOWN_IN_MILLIS) {
            switchToEndView();
            return;
        }
        if (mProgress < SpeedTestTask.COUNT_DOWN_IN_MILLIS) {
            mSpeedTestResultView.setProgress(mProgress += PROGRESS_INCREASE);
            mHandler.sendEmptyMessageDelayed(MSG_PROGRESS_UPDATE, UPDATE_PROGRESS_SUB_IN_MILLIS);
        }
    }

    /**
     * 处理指针更新
     */
    private void handlePointerUpdate() {
        if (mSpeedUpdateTime <= 0) {
            mChartView.setCurrAngle(mPointerTargetAngle);
            return;
        }
        mSpeedUpdateTime -= EACH_FRAME_DURATION;
        mChartView.setCurrAngle(mChartView.getCurrAngle() + mPointerFrameAngle);
        if (mChartView.getCurrAngle() < DialChartView.MIN_ANGLE) {
            mChartView.setCurrAngle(DialChartView.MIN_ANGLE);
        }
        if (mChartView.getCurrAngle() > DialChartView.MAX_ANGLE) {
            mChartView.setCurrAngle(DialChartView.MAX_ANGLE);
        }
        mChartView.invalidate();
        mHandler.sendEmptyMessageDelayed(MSG_POINTER_UPDATE, EACH_FRAME_DURATION);
    }

    /**
     * 设置监听器
     * @param speedTestListener {@link OnSpeedTestListener}
     */
    public void setSpeedTestListener(OnSpeedTestListener speedTestListener) {
        mSpeedTestListener = speedTestListener;
    }

    /**
     * 手动测速监听
     */
    public interface OnSpeedTestListener {
        /**
         * 倒计时
         * @param countDownTime 还剩余的时间
         * @param totalTime 总时间
         */
        void onCountDownTimeChange(long countDownTime, long totalTime);

        /**
         * 测速Task开始
         * @return 开始操作是否成功
         */
        boolean onSpeedTaskStart();

        /**
         * 测速Task取消
         */
        void onSpeedTaskCancel();

        /**
         * 测速Task完成
         */
        void onSpeedTaskFinished();

        /**
         * 测速Task失败
         */
        void onSpeedTaskFail();
    }
}
