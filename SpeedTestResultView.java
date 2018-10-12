package com.zlianjie.coolwifi.speedtest;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.zlianjie.android.util.SystemUtils;
import com.zlianjie.android.util.animation.EasyAnimationHelper;
import com.zlianjie.coolwifi.R;
import com.zlianjie.coolwifi.util.Constant;
import com.zlianjie.coolwifi.util.TypefaceUtils;
import com.zlianjie.coolwifi.util.UIUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试结果view
 * @author kzw
 * @since 2015-07-31
 */
public class SpeedTestResultView extends RelativeLayout {

    /** 网速慢 */
    private static final long SPEED_LEVEL_SLOW = 100 * Constant.KB_IN_BYTES;
    /** 网速一般 */
    private static final long SPEED_LEVEL_OK = 200 * Constant.KB_IN_BYTES;
    /** 网速好 */
    private static final long SPEED_LEVEL_GOOD = 300 * Constant.KB_IN_BYTES;

    /** 测试结果动画延迟执行时间 */
    private static final int BASE_DELAY_DURATION = 500;
    /** 测试结果icons动画延迟执行时间 */
    private static final int SHOW_ICONS_DELAY_DURATION = 100;

    /** 动画执行时间 */
    public static final int CUSTOM_DURATION = 500;

    private static final float SCALE_VALUE0 = 1.0f;
    private static final float SCALE_VALUE1 = 0.8f;

    /** 隐藏进度条动画时长 */
    private static final int HIDE_PROGRESS_DURATION = 16;
    /** 进度条最大值 */
    private static final int MAX_PROGRESS = (int)SpeedTestTask.COUNT_DOWN_IN_MILLIS;

    /** 检测中的view */
    private View mRunningView;
    /** 检测后的view */
    private View mEndView;

    /** 网速textview */
    private TextView mSpeedTextView;
    /** 测速状态textview */
    private TextView mSpeedStatusTextView;
    /** 测速进度条 */
    private ProgressBar mProgressBar;

    /** 测试结果图标 */
    private TextView[] mResultIcons;

    /** 动画集合 */
    private List<ObjectAnimator> mAnimators = new ArrayList<>();

    private RunState mState = RunState.PREPARE;

    /** 是否是网络不通 */
    private boolean mConnFail = false;

    /**
     * 运行状态机
     */
    enum RunState {
        // 准备状态
        PREPARE,
        // 运行状态
        RUNNING,
        // 结束状态
        END
    }

    /**
     * 设置运行状态
     * @param state
     */
    void setRunState(RunState state) {
        mState = state;
        switch(state) {
        case PREPARE:
            break;
        case RUNNING:
            mConnFail = false;
            clearUpAnimator();
            updateResultIconsVisibility(INVISIBLE);
            mSpeedStatusTextView.setTextColor(UIUtils.getColor(R.color.speed_test_running));
            mSpeedStatusTextView.setText(UIUtils.getString(R.string.speed_test_running));
            break;
        case END:
            updateViewsVisibility(VISIBLE, mEndView, mRunningView);
            break;
        }
    }

    /**
     * 获取运行状态
     * @return
     */
    RunState getRunState() {
        return mState;
    }

    public SpeedTestResultView(Context context) {
        super(context);
        init(context);
    }

    public SpeedTestResultView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SpeedTestResultView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    /**
     * 初始化
     * @param context
     */
    private void init(Context context) {
        View root = LayoutInflater.from(context).inflate(R.layout.speed_test_result_view, this, true);
        mRunningView = root.findViewById(R.id.view_running);
        mEndView = root.findViewById(R.id.view_end);
        mSpeedTextView = (TextView) root.findViewById(R.id.speed_value_text);
        mSpeedStatusTextView = (TextView) root.findViewById(R.id.speed_test_status);
        mResultIcons = new TextView[] {
                (TextView) root.findViewById(R.id.browser_text),
                (TextView) root.findViewById(R.id.games_text),
                (TextView) root.findViewById(R.id.music_text),
                (TextView) root.findViewById(R.id.video_text)
        };
        mSpeedTextView.setTypeface(TypefaceUtils.getNumberTypeface(getContext()));
        mProgressBar = (ProgressBar) root.findViewById(R.id.speed_test_progress);
        mProgressBar.setMax(MAX_PROGRESS);
    }

    /**
     * 设置速度文本值
     * @param value
     */
    void setSpeedTextValue(String value) {
        if (mSpeedTextView != null) {
            mSpeedTextView.setText(value);
        }
    }

    /**
     * 设置进度
     * @param progress
     */
    void setProgress(int progress) {
        mProgressBar.setProgress(progress);
    }

    /**
     * 淡入显示进度条
     */
    void showProgress() {
        setProgress(0);
        EasyAnimationHelper.fadeIn(mProgressBar, EasyAnimationHelper.DURATION_SHORT);
    }

    /**
     * 淡出隐藏进度条
     * @param speed
     */
    void hideProgress(final long speed) {
        setProgress(MAX_PROGRESS);
        EasyAnimationHelper.fadeOut(mProgressBar, HIDE_PROGRESS_DURATION, new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mProgressBar.setVisibility(GONE);
                // 隐藏进度条后再执行
                showRank(speed);
                showTestResultView(speed, true);
            }
        });
    }

    /**
     * 展示测试结果view
     * @param speed
     * @param isUp
     */
    void showTestResultView(final long speed, final boolean isUp) {
        // 网络不通
        if (isUp && speed == 0) {
            mConnFail = true;
            mSpeedStatusTextView.setTextColor(UIUtils.getColor(R.color.wifi_connect_error_color));
            mSpeedStatusTextView.setText(UIUtils.getString(R.string.speed_test_result_conn_fail));
        }
        // 网络不通时，重新测试，不执行动画
        if (mConnFail) {
            return;
        }
        pushTestResultView(mRunningView, isUp, new MySpeedAnimationListener(speed, isUp));
        if (isUp) {
            scaleSpeedValueView(mSpeedTextView, SCALE_VALUE0, SCALE_VALUE1, SCALE_VALUE0, SCALE_VALUE1);
        } else {
            scaleSpeedValueView(mSpeedTextView, SCALE_VALUE1, SCALE_VALUE0, SCALE_VALUE1, SCALE_VALUE0);
        }
    }

    private class MySpeedAnimationListener implements SpeedAnimationListener {

        private long speed;
        private boolean isUp;

        public MySpeedAnimationListener(long speed, boolean isUp) {
            this.speed = speed;
            this.isUp = isUp;
        }

        /**
         * 动画开始
         */
        @Override
        public void onAnimationStart() {

        }

        /**
         * 动画结束
         */
        @Override
        public void onAnimationEnd() {
            if (isUp) {
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showTestLevelView(speed);
                    }
                }, SHOW_ICONS_DELAY_DURATION);
            }
        }
    }

    /**
     * 展示测试等级view，执行动画
     * @param speed
     */
    private void showTestLevelView(long speed) {
        if (speed <= SPEED_LEVEL_SLOW) {
            // 浏览网页
            updateViewsVisibility(GONE, mResultIcons[1], mResultIcons[2], mResultIcons[3]);
            showTestLevelAnim(mResultIcons[0]);
        } else if (speed > SPEED_LEVEL_SLOW && speed <= SPEED_LEVEL_OK) {
            // 浏览网页，玩游戏
            updateViewsVisibility(GONE, mResultIcons[2], mResultIcons[3]);
            showTestLevelAnim(mResultIcons[0], mResultIcons[1]);
        } else if (speed > SPEED_LEVEL_OK && speed <= SPEED_LEVEL_GOOD) {
            // 浏览网页，玩游戏，听音乐
            updateViewsVisibility(GONE, mResultIcons[3]);
            showTestLevelAnim(mResultIcons[0], mResultIcons[1], mResultIcons[2]);
        } else if (speed > SPEED_LEVEL_GOOD) {
            // 浏览网页，玩游戏，听音乐，看视频
            showTestLevelAnim(mResultIcons);
        }
    }

    /**
     * 设置views的可见性
     * @param visibility
     * @param views
     */
    private void updateViewsVisibility(int visibility, View... views) {
        if (views == null || views.length == 0) {
            return;
        }
        for (View view : views) {
            view.setVisibility(visibility);
        }
    }

    /**
     * 设置结果icons的可见性
     * @param visibility visible, invisible, gone
     */
    private void updateResultIconsVisibility(int visibility) {
        for (View v : mResultIcons) {
            v.setVisibility(visibility);
        }
        invalidate();
    }

    /**
     * 展示测试等级动画
     * @param views
     */
    private void showTestLevelAnim(final View... views) {
        final int length = views.length;
        for (int i = 0; i < length; i++) {
            if (mState == RunState.RUNNING) {
                return;
            }
            final View view = views[i];
            view.setLayoutParams(new LinearLayout.LayoutParams(
                    SystemUtils.getDisplayWidth(getContext()) / 4,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            ObjectAnimator anim = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
            anim.setDuration(EasyAnimationHelper.DURATION_MEDIUM);
            anim.setInterpolator(new FastOutLinearInInterpolator());
            anim.setStartDelay(BASE_DELAY_DURATION * i);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    view.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animation) {

                }
            });
            mAnimators.add(anim);
            anim.start();
        }
    }

    /**
     * 上下推动测试结果view
     * @param view
     * @param isUp
     * @param listener
     */
    private void pushTestResultView(View view, boolean isUp, final SpeedAnimationListener listener) {
        float startY;
        float endY;
        if (isUp) {
            startY = view.getTop();
            endY = startY - UIUtils.getDimenPixelOffset(R.dimen.result_icon_height) / 2;
        } else {
            endY = view.getTop();
            startY = endY - UIUtils.getDimenPixelOffset(R.dimen.result_icon_height) / 2;
        }
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, "y", startY, endY);
        animator.setDuration(CUSTOM_DURATION);
        animator.addListener(new AnimatorListenerAdapter() {
            /**
             * {@inheritDoc}
             *
             * @param animation
             */
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (listener != null) {
                    listener.onAnimationEnd();
                }
            }
        });
        animator.start();
    }

    /**
     * 速度值缩放动画
     * @param view
     * @param fromX
     * @param toX
     * @param fromY
     * @param toY
     */
    private void scaleSpeedValueView(View view, float fromX, float toX, float fromY, float toY) {
        Animation scaleAnimation = new ScaleAnimation(fromX, toX, fromY, toY,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaleAnimation.setDuration(CUSTOM_DURATION);
        scaleAnimation.setFillAfter(true);
        view.startAnimation(scaleAnimation);
    }

    /**
     * 清除动画资源
     */
    public void clearUpAnimator() {
        if (mAnimators != null) {
            int size = mAnimators.size();
            if (size != 0) {
                for (int i = 0; i < size; i++) {
                    ObjectAnimator animator = mAnimators.get(i);
                    animator.cancel();
                    animator.removeAllListeners();
                }
                mAnimators.clear();
            }
        }
    }

    /**
     * 击败全国百分之几的机友
     * @param speed 网速
     */
    private void showRank(long speed) {
        if (speed == 0) {
            return;
        }
        if (mSpeedStatusTextView != null) {
            double percent = cumulativeNormalDistribution(normalize(speed));
            percent *= 100;
            if (percent < 1) {
                percent = 1;
            } else if (percent > 99) {
                percent = 99;
            }
            mSpeedStatusTextView.setText(UIUtils.getString(R.string.speed_test_end, percent));
        }
    }

    /** 网速区间值 */
    private static final long[] SPEED_SECTION = {
            0l,
            200 * Constant.KB_IN_BYTES,
            Constant.MB_IN_BYTES,
            10 * Constant.MB_IN_BYTES
    };
    /** 正态区间值 */
    private static final double[] NORMALIZED_SECTION = { -3d, -0.7d, 0.7d, 6d };

    /**
     * 根据速度计算正态分布的映射值
     * <pre>
     *     -3到-0.7 映射 0到200K
     *     -0.7到0.7 映射 200K到1M
     *     0.7到6 映射 1M到10M
     * </pre>
     * @param speed 单位字节
     * @return
     */
    private static double normalize(long speed) {
        int lower = SPEED_SECTION.length;
        int upper = lower;
        for (int i = 0; i < SPEED_SECTION.length; i++) {
            if (speed < SPEED_SECTION[i]) {
                lower = i - 1;
                upper = i;
                break;
            }
        }
        return linearMapping(speed, lower, upper);
    }

    /**
     * 计算速度对应的正态值
     * @param speed 速度
     * @param start 速度区间起点值
     * @param end 速度区间终点值
     * @return 速度对应的正态值
     */
    private static double linearMapping(long speed, int start, int end) {
        if (start < 0) {
            start = 0;
        }
        if (end >= SPEED_SECTION.length) {
            end = SPEED_SECTION.length - 1;
        }
        if (start >= end) {
            return NORMALIZED_SECTION[end];
        }
        return NORMALIZED_SECTION[start]
                + (NORMALIZED_SECTION[end] - NORMALIZED_SECTION[start]) / (SPEED_SECTION[end] - SPEED_SECTION[start])
                * (speed - SPEED_SECTION[start]);
    }

    /**
     * Calculates the cumulative normal distribution function (CNDF) for a standard normal: N(0,1)
     * @param x value
     * @return Cumulative probability
     */
    private static double cumulativeNormalDistribution(double x) {
        int neg = (x < 0d) ? 1 : 0;
        if (neg == 1) {
            x *= -1d;
        }

        double k = (1d / (1d + 0.2316419 * x));
        double y = ((((1.330274429 * k - 1.821255978) * k + 1.781477937) *
                k - 0.356563782) * k + 0.319381530) * k;
        y = 1.0 - 0.398942280401 * Math.exp(-0.5 * x * x) * y;

        return (1d - neg) * y + neg * (1d - y);
    }

    private interface SpeedAnimationListener {

        /**
         * 动画开始
         */
        void onAnimationStart();

        /**
         * 动画结束
         */
        void onAnimationEnd();

    }

}
