package com.zlianjie.coolwifi.speedtest;

import android.text.format.DateUtils;

import com.zlianjie.android.util.AsyncTaskAssistant;
import com.zlianjie.android.util.log.Log;
import com.zlianjie.coolwifi.CoolWifi;
import com.zlianjie.coolwifi.jobs.WifiSpeedUploadJob;
import com.zlianjie.coolwifi.location.LocationInfo;
import com.zlianjie.coolwifi.util.IdentityManager;
import com.zlianjie.coolwifi.util.TrafficStatsUtils;
import com.zlianjie.coolwifi.util.Utility;
import com.zlianjie.coolwifi.wifi.AccessPoint;
import com.zlianjie.coolwifi.wifi.WifiControlManager;
import com.zlianjie.coolwifi.wifiinfo.AccessPointSpeed;

import org.json.JSONArray;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.HttpStatus;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.config.RequestConfig;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;

/**
 * 手动测试网速Task
 *
 * @author kejunyao
 * @since 2014年7月22日
 */
public class SpeedTestTask {
    /** debug switch */
    private static final boolean DEBUG = CoolWifi.GLOBAL_DEBUG;
    /** log tag */
    private static final String TAG = "SpeedTestTask";
    
    /** 请求超时时间 */
    private static final int TIME_OUT_MILLIS = 5000;
    
    /** 任务执行失败状态 */
    public static final int TASK_STATE_FAIL = -1;
    /** 任务停止执行状态 */
    public static final int TASK_STATE_CANCEL = 0;
    /** 任务执行成功状态 */
    public static final int TASK_STATE_FINISHED = 1;
    /** 任务开始执行状态 */
    public static final int TASK_STATE_START = 2;
    
    /** 缓存大小 */
    private static final int BUFFER_SIZE = 512;
    
    /** 刷新速率信息的时间间隔(秒) */
    private static final float UPDATE_RATE_IN_SECOND = 0.2f;
    /** 刷新速率信息的时间间隔(毫秒) */
    private static final int UPDATE_RATE_IN_MILLIS = (int) (UPDATE_RATE_IN_SECOND * DateUtils.SECOND_IN_MILLIS);
    /** 倒计时(秒) */
    private static final int COUNT_DOWN_IN_SECOND = 10;
    /** 倒计时(毫秒) */
    public static final long COUNT_DOWN_IN_MILLIS = COUNT_DOWN_IN_SECOND * DateUtils.SECOND_IN_MILLIS;
    
    /** 任务是否为正在运行 */
    private volatile boolean isRunning = false;
    /** 任务执行的开始时间 */
    private long mTaskStartTime;
    /** 记录每次任务开始时的流量值 */
    private long mStartTotalBytes;
    /*** 连接等待时间 */
    private long mConnectionWaitTime;
    /** 文件下载url地址 */
    private String[] mSpeedUrlArray;
    /** 上一次的资源数据下标值 */
    private int tempIndex = -1;
    /** 任务的执行状态 */
    private volatile int mTaskState;
    /** 记录最大平均速率值 */
    private volatile long mMaxArvSpeed;
    
    /** {@link HttpClient} */
    private CloseableHttpClient mHttpClient;
    /** {@link HttpGet} */
    private HttpGet mRequest;
    /** 接收外部传入的监听器(在UI线程中运行) */
    private SpeedTestTaskListener mSpeedTestTaskListener;
    
    /**
     * 构造方法
     * @param speedTestTaskListener {@link SpeedTestTaskListener}
     */
    public SpeedTestTask(SpeedTestTaskListener speedTestTaskListener) {
        mSpeedTestTaskListener = speedTestTaskListener;
        try {
            InputStream inStream = CoolWifi.getAppContext().getAssets().open("speed_test");
            Properties properties = new Properties();
            properties.load(inStream);
            final int size = properties.size();
            mSpeedUrlArray = new String[size];
            Enumeration<Object> en = properties.elements();
            int index = 0;
            while (en.hasMoreElements()) {
                String url = (String) en.nextElement();
                mSpeedUrlArray[index] = url;
                if (DEBUG) {
                    Log.i(TAG, "mSpeedUrlArray[" + index + "]=" + url);
                }
                index += 1;
            }
        } catch (IOException e) {
            if (DEBUG) {
                Log.w(TAG, "speed_test file load error", e);
            }
            mSpeedUrlArray = new String[] {"http://kuwifi.cn/download/coolwifi.apk"};
        }
    }
    
    private void onSpeedChange(SpeedTest test) {
        if (mSpeedTestTaskListener != null) {
            mSpeedTestTaskListener.onSpeedChange(test);
        }
    }
    
    private void onTestStateChange(int newState) {
        if (mSpeedTestTaskListener != null) {
            mSpeedTestTaskListener.onStateChange(newState);
        }
    }
    
    /**
     * 往本地写文件
     * @param inStream
     *            输入流
     * @throws IOException {@link IOException}
     */
    private void writeFile(InputStream inStream) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        // 上一次监听器方法执行时间, 赋值为当前时间是解决读第一个buffer时流量差为0
        long preTime = System.currentTimeMillis();

        while (inStream.read(buffer) != -1) {
            // 抛异常退出线程
            if (!isRunning) {
                break;
            }
            
            // 获取当前时间, 用于计算是否间隔有200秒
            long currentTime = System.currentTimeMillis();
            if ((currentTime - preTime) >= UPDATE_RATE_IN_MILLIS) {
                // 计算当前速率
                long currentTotalBytes = TrafficStatsUtils.getWifiTotalBytes();
                // 花了多少时间(秒)
                float second = (float) (currentTime - mTaskStartTime - mConnectionWaitTime)
                        / (float) DateUtils.SECOND_IN_MILLIS;
                // 计算平均速率
                int avgSpeed = (int) ((currentTotalBytes - mStartTotalBytes) / second);
                // 保留最大的平均速率值
                mMaxArvSpeed = mMaxArvSpeed > avgSpeed ? mMaxArvSpeed : avgSpeed;
    
                // 更新当前速率、平均速率信息
                onSpeedChange(new SpeedTest(0, avgSpeed));
                preTime = currentTime;
                if (DEBUG) {
                    Log.i(TAG, "avgSpeed=" + avgSpeed + ", second=" + second + ", currentTime=" + currentTime
                            + ", mTaskStartTime=" + mTaskStartTime + ", mConnectionWaitTime=" + mConnectionWaitTime);
                }
            }
            
            // 时间等于或超过10秒了
            if (isTimeout()) {
                mTaskState = TASK_STATE_FINISHED;
                killTask();
            }
        }
    }
    
    /**
     * 向网络发送异步请求
     */
    public void startTask() {
        mTaskState = TASK_STATE_FAIL;
        mTaskStartTime = System.currentTimeMillis();
        mStartTotalBytes = TrafficStatsUtils.getWifiTotalBytes();
        
        // 防止频繁请求
        if (isRunning) {
            if (DEBUG) {
                Log.i(TAG, "Task is running...");
            }
            return;
        }
        if (mSpeedUrlArray == null || mSpeedUrlArray.length == 0) {
            onTestStateChange(TASK_STATE_FAIL);
            return;
        }
        onTestStateChange(TASK_STATE_START);
        request();
    }
    
    /**
     * 发送网络请求
     */
    private void request() {
        Utility.newThread(new Runnable() {
            @Override
            public void run() {
                boolean first = true;
                while (true) {
                    if (!first && mTaskState != TASK_STATE_FINISHED) {
                        if (isTimeout()) {
                            mTaskState = TASK_STATE_FINISHED;
                        }
                    }
                    //人为执行请求的
                    if (first) {
                        first = false;
                        request(true);
                    } else if (mTaskState != TASK_STATE_CANCEL && mTaskState != TASK_STATE_FINISHED) { // 还没超过10秒钟的情况
                        // 继续下载
                        request(false);
                    } else {
                        killTask();
                        if (DEBUG) {
                            Log.i(TAG, "Task is stop, and task state is " + mTaskState + ".");
                        }
                        break;
                    }
                }
            }
        }, "speed_test").start();
    }

    /**
     * 向网络发送请求
     * @param isMan 是否人为操作的
     */
    private void request(boolean isMan) {
        String urlStr = getUrl4Test();
        if (DEBUG) {
            Log.i(TAG, "url=" + urlStr);
        }
        isRunning = true;
        long connectionStartTime = System.currentTimeMillis();
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(TIME_OUT_MILLIS)
                .setSocketTimeout(2 * TIME_OUT_MILLIS)
                .build();
        mHttpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(config)
                .build();
        InputStream inStream = null;
        try {
            mRequest = new HttpGet(urlStr);
            HttpResponse httpResponse = mHttpClient.execute(mRequest);
            if (httpResponse != null && httpResponse.getStatusLine() != null
                    && httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                final HttpEntity httpEntity = httpResponse.getEntity();
                if (httpEntity != null) {
                    inStream = httpEntity.getContent();
                }
            }
            
            if (inStream == null) {
                mTaskState = TASK_STATE_FAIL;
                return;
            }
            
            //等到服务器响应之后才算时间
            if (isMan) {
                mConnectionWaitTime = 0;
            } else {
                //文件切换时要减去连接等待时间
                mConnectionWaitTime += (System.currentTimeMillis() - connectionStartTime);
            }
            writeFile(inStream);
        } catch (Exception e) {
            if (DEBUG) {
                Log.w(TAG, "SpeedTestTask.request(), ", e);
            }
        } finally {
            closeClient();
            Utility.closeSafely(inStream);
        }

    }
    
    private synchronized String getUrl4Test() {
        // 按数组小标顺序取一个url地址
        tempIndex++;
        if (tempIndex < 0 || tempIndex >= mSpeedUrlArray.length) {
            tempIndex = 0;
        }
        return mSpeedUrlArray[tempIndex];
    }
    
    /**
     * 监测是否超时
     * @return 是否超时
     */
    private boolean isTimeout() {
        return (System.currentTimeMillis() - mTaskStartTime) >= COUNT_DOWN_IN_MILLIS;
    }
    
    /**
     * 关闭链接
     */
    private void closeClient() {
        if (mRequest != null) {
            try {
                mRequest.abort();
            } catch (Throwable t) {
            }
            mRequest = null;
        }
        if (mHttpClient != null) {
            try {
                mHttpClient.close();
            } catch (Throwable t) {
            }
            mHttpClient = null;
        }
    }
    
    /**
     * 停止从网络上下载资源
     * @param isUIExit 测速界面是否退出
     */
    public void stopTask(final boolean isUIExit) {
        killTask();
        if (isUIExit) {
            uploadMaxSpeed();
        }
    }
    
    /**
     * 停止线程
     */
    public void killTask() {
        isRunning = false;
        if (mTaskState != TASK_STATE_FINISHED) {
            mTaskState = TASK_STATE_CANCEL;
        }
        mConnectionWaitTime = 0;
        AsyncTaskAssistant.executeOnThreadPool(new Runnable() {
            @Override
            public void run() {
                closeClient();
            }
        });
        onTestStateChange(mTaskState);
    }
    
    /**
     * Task是否正在运行
     * @return is running
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * 上传本次测速的平均速率
     */
    public void uploadMaxSpeed() {
        if (mMaxArvSpeed <= 0) {
            return;
        }
        if (DEBUG) {
            Log.i(TAG, "arvSpeed= " + mMaxArvSpeed);
        }
        AccessPoint ap = WifiControlManager.getInstance().getActiveAp();
        if (ap != null) {
            final AccessPointSpeed aps = new AccessPointSpeed(ap);
            aps.setSpeed(mMaxArvSpeed);
            AsyncTaskAssistant.executeOnThreadPool(new Runnable() {
                @Override
                public void run() {
                    LocationInfo loc = IdentityManager.getInstance().getLocation();
                    aps.setLocation(loc);
                    JSONArray itemArray = new JSONArray();
                    itemArray.put(aps.toJSON());
                    CoolWifi.addJob(new WifiSpeedUploadJob(itemArray.toString()));
                }
            });
        }
    }
    
    /**
     * 手动测速信息
     */
    static class SpeedTest {
        /** 当前速率 */
        private int mCurrentSpeed;
        /** 平均速率 */
        private int mAvgSpeed;
    
        /**
         * 构造方法
         * @param currentSpeed 当前速率
         * @param avgSpeed 平均速率
         */
        SpeedTest(int currentSpeed, int avgSpeed) {
            this.mCurrentSpeed = currentSpeed;
            this.mAvgSpeed = avgSpeed;
        }

        /**
         * 获取当期速率
         * @return 当前速率
         */
        int getCurrentSpeed() {
            return mCurrentSpeed;
        }

        /**
         * 获取当前平均速率
         * @return 当前平均速率
         */
        int getAvgSpeed() {
            return mAvgSpeed;
        }
    }
    
    /**
     * 网速测试Task的监听器
     */
    interface SpeedTestTaskListener {
        /**
         * 监听手动测速信息的变化
         * @param speedTest 手动过测试
         */
        void onSpeedChange(final SpeedTest speedTest);

        /**
         * 监听任务执行状态的变化
         *
         * @param taskState
         *            任务执行状态
         */
        void onStateChange(final int taskState);
    }
}
