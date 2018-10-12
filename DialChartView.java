package com.zlianjie.coolwifi.speedtest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.MaskFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.View;

import com.zlianjie.android.util.APIUtils;
import com.zlianjie.coolwifi.R;
import com.zlianjie.coolwifi.util.Constant;
import com.zlianjie.coolwifi.util.UIUtils;

/**
 * 速度测试表盘View
 *
 * @author kzw
 * @since 2015年7月30日
 */
 class DialChartView extends View {

    /** 刻度盘上KB的单位 */
    private static final String KB = "K";
    /** 刻度盘上MB的单位 */
    private static final String MB = "M";

    /** 刻度值 */
    private static final float[] SCALE_VALUES = new float[] {0, power2(1), power2(3), power2(5),
            power2(7), power2(9), power2(11), power2(13)};
    
    /** 需要绘制的刻度文字 */
    private static final String[] SCALE_LABELS = new String[] {
            (int) SCALE_VALUES[0] + KB, (int) SCALE_VALUES[1] + KB, (int) SCALE_VALUES[2] + KB,
            (int) SCALE_VALUES[3] + KB, (int) SCALE_VALUES[4] + KB, (int) SCALE_VALUES[5] + KB,
            (int) SCALE_VALUES[6] / Constant.KB_IN_BYTES + MB,
            (int) SCALE_VALUES[7] / Constant.KB_IN_BYTES + MB };

    /** 刻度字符串坐标 */
    private static final int[][] SCALE_COORDINATES = new int[][] {
            {-144, 34}, {-143, -33}, {-110, -93}, {-48, -132}, {25, -132}, {97, -93},
            {130, -33}, {130, 34} };

    /** 光束渲染颜色数组 */
    private static final int[] SWEEP_GRADIENT_COLORS = new int[] {
            Color.parseColor("#00FFFFFF"), Color.parseColor("#4CFFFFFF") };

    private static final float BG_PIC_WIDTH = 302f;
    private static final int OUTER_CIRCLE_MULTIPLE = 129;
    private static final int INNER_CIRCLE_MULTIPLE = 109;

    /** View的宽度 */
    private int mViewWidth = 0;
    /** View的高度 */
    private int mViewHeight = 0;
    /** View的中心点x */
    private int mViewCenterX = 0;
    /** View的中心点Y */
    private int mViewCenterY = 0;
    /** 比例 */
    private float mProportion;

    /** 指针图片宽度 */
    private float mBitmapWidth;
    /** 指针图片高度 */
    private float mBitmapHeight;
    /** 指针bound的ltrb */
    private int mPointerBoundLeft, mPointerBoundTop, mPointerBoundRight, mPointerBoundBottom;

    /** 外圆矩形 */
    private RectF mArcOval = new RectF();
    /** 内圆矩形 */
    private RectF mInnerOval = new RectF();

    /** 指针图片 */
    private Bitmap mPointerBitmap;
    private BitmapDrawable mPointerBitmapDrawable;

    /** 扇形画笔 */
    private Paint mCirclePaint;
    /** 刻度画笔 */
    private Paint mScalePaint;
    /** 文字画笔 */
    private Paint mTextPaint;
    /** 阴影画笔 */
    private Paint mShadowPaint;

    /** 画笔颜色 */
    private static final int PAINT_COLOR = Color.WHITE;
    /** 画笔透明度 */
    private static final int PAINT_NO_ALPHA = 255;
    /** 画笔透明度－半透明 */
    private static final int PAINT_HALF_ALPHA = (int) (PAINT_NO_ALPHA * .5);
    /** 画笔大小 */
    private static final int PAINT_TEXT_SIZE = UIUtils.getDimenPixelOffset(R.dimen.text_size_tiny);

    /** 开始角度 */
    private static final float START_ANGLE = 165.0f;
    /** 扫描角度 */
    private static final float SWEEP_ANGLE = 210.0f;
    /** 刻度起始角度 */
    private static final float SCALE_START_ANGLE = -104.5f;
    /** 刻度旋转角度 */
    private static final float SCALE_PRO_ANGLE = 4.98f;
    /** 阴影开始角度 */
    private static final float SHADOW_START_ANGLE = START_ANGLE - 360.0f - 5;

    /** 表盘单元格角度值 */
    private static final float CELL_ANGLE = 30.0f;
    /** 最小角度 */
    public static final float MIN_ANGLE = -15.0f;
    /** 最大角度 */
    public static final float MAX_ANGLE = 195.0f;
    /** 当前指针角度 */
    private float mCurrAngle = MIN_ANGLE;

    /** 阴影扫描matrix */
    private Matrix mMatrix = new Matrix();
    /** 阴影扫描光束渲染 */
    private SweepGradient mSweepGradient;
    /** 阴影扫描中心点x */
    private float mShadowCenterX;
    /** 阴影扫描中心点y */
    private float mShadowCenterY;
    /** 阴影扫描位置 */
    private float[] mSweepPosition = new float[2];

    /**
     * 构造方法
     * @param context {@link Context}
     */
    public DialChartView(Context context) {
        super(context);
        init();
    }
    
    /**
     * 构造方法
     * @param context {@link Context}
     * @param attrs {@link AttributeSet}
     */
    public DialChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    /**
     * @param context {@link Context}
     * @param attrs {@link AttributeSet}
     * @param defStyleAttr theme
     */
    public DialChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    /**
     * 初始化
     */
    private void init() {

        if (APIUtils.hasHoneycomb()) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        // MaskFilter类可以为Paint分配边缘效果
        MaskFilter arcBlur = new BlurMaskFilter(1, BlurMaskFilter.Blur.INNER);
        mCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCirclePaint.setColor(PAINT_COLOR);
        mCirclePaint.setStyle(Paint.Style.STROKE);
        mCirclePaint.setStrokeWidth(2);
        mCirclePaint.setMaskFilter(arcBlur);

        mScalePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mScalePaint.setStyle(Paint.Style.STROKE);
        mScalePaint.setColor(PAINT_COLOR);
        mScalePaint.setStrokeWidth(2);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mTextPaint.setColor(PAINT_COLOR);
        mTextPaint.setAlpha(PAINT_HALF_ALPHA);
        mTextPaint.setTextSize(PAINT_TEXT_SIZE);

        mShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mPointerBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_speed_pointer);
        setTag(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mViewWidth == 0 || mViewHeight == 0) {
            computeViewSize();
            initSectorRect();
        }
        // 移动中心点
        canvas.translate(mViewCenterX, mViewCenterY);
        // 绘制外圆
        mCirclePaint.setAlpha(PAINT_NO_ALPHA);
        canvas.drawArc(mArcOval, START_ANGLE, SWEEP_ANGLE, false, mCirclePaint);
        // 绘制内圆
        mCirclePaint.setAlpha(PAINT_HALF_ALPHA);
        canvas.drawArc(mInnerOval, START_ANGLE, SWEEP_ANGLE, false, mCirclePaint);
        // 画扫描阴影
        drawShadow(canvas);
        // 画指针
        drawPointer(canvas);
        // 画表盘刻度
        drawScale(canvas);
        // 画文字
        drawFixedText(canvas);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (w == oldw && h == oldh) {
            return;
        }

        computeViewSize();
        initSectorRect();
        setShadowCenter(mInnerOval);
        doScalePointerBitmap(mInnerOval);
        setPointerBound();
    }

    /**
     * 画指针
     * @param canvas
     */
    private void drawPointer(Canvas canvas) {
        // 缩放指针位图
        scalePointerBitmap(mInnerOval);

        if (mPointerBitmapDrawable == null) {
            mPointerBitmapDrawable = new BitmapDrawable(getResources(), mPointerBitmap);
        }

        canvas.save();
        canvas.rotate(mCurrAngle, mShadowCenterX, mShadowCenterY);

        // 四个都为0时
        if (mPointerBoundLeft == 0 & mPointerBoundTop == 0 & mPointerBoundRight == 0 & mPointerBoundBottom == 0) {
            setPointerBound();
        }

        mPointerBitmapDrawable.setBounds(mPointerBoundLeft, mPointerBoundTop, mPointerBoundRight, mPointerBoundBottom);
        mPointerBitmapDrawable.draw(canvas);
        canvas.restore();
    }

    /**
     * 画阴影，即高光
     * @param canvas 画布
     */
    private void drawShadow(Canvas canvas) {
        mSweepPosition[0] = 0f;
        mSweepPosition[1] = (mCurrAngle - MIN_ANGLE) / 360; // 计算渐变结束位置
        mSweepGradient = new SweepGradient(mShadowCenterX, mShadowCenterY, SWEEP_GRADIENT_COLORS, mSweepPosition);
        // 设置起始偏转角度
        mMatrix.setRotate(SHADOW_START_ANGLE, mShadowCenterX, mShadowCenterY);
        mSweepGradient.setLocalMatrix(mMatrix);
        mShadowPaint.setShader(mSweepGradient);

        canvas.drawArc(mInnerOval, START_ANGLE, mCurrAngle - MIN_ANGLE, true, mShadowPaint);
    }

    /***
     * 画表盘刻度
     * @param canvas
     */
    private void drawScale(Canvas canvas) {
        // 保存canvas状态
        canvas.save();
        canvas.rotate(SCALE_START_ANGLE);
        for (int i = 0; i < 43; i++) {
            if (i % 6 == 0) {
                canvas.drawLine(0, -129.5f * mProportion, 0, -120 * mProportion, mScalePaint);
            }
            canvas.rotate(SCALE_PRO_ANGLE);
        }
        canvas.restore();
    }

    /**
     * 画刻度文字
     * @param canvas
     */
    private void drawFixedText(Canvas canvas) {
        for (int i = 0; i < SCALE_LABELS.length; i++) {
            canvas.drawText(SCALE_LABELS[i],
                    SCALE_COORDINATES[i][0] * mProportion,
                    SCALE_COORDINATES[i][1] * mProportion,
                    mTextPaint);
        }
    }

    /**
     * 初始化矩阵
     */
    private void initSectorRect() {
        constructSectorRect(mArcOval, OUTER_CIRCLE_MULTIPLE);
        constructSectorRect(mInnerOval, INNER_CIRCLE_MULTIPLE);
    }

    /**
     * 构造扇形矩阵
     *
     * @param rect
     * @param circleMultiple
     * @return
     */
    private void constructSectorRect(RectF rect, int circleMultiple) {
        rect.left = -circleMultiple * mProportion;
        rect.top = rect.left;
        rect.right = -rect.left;
        rect.bottom = -rect.left;
        if (mShadowCenterX == 0 || mShadowCenterY == 0) {
            setShadowCenter(rect);
        }
    }

    /**
     * 计算view的宽高度
     */
    private void computeViewSize() {
        mViewWidth = getWidth();
        mViewHeight = getHeight();
        mViewWidth = mViewHeight < mViewWidth ? mViewHeight : mViewWidth;
        mViewCenterX = mViewWidth / 2;
        mViewCenterY = mViewHeight / 2;
        mProportion = mViewWidth / BG_PIC_WIDTH;
    }

    /**
     * 设置shadow的中心
     * @param rect
     */
    private void setShadowCenter(RectF rect) {
        mShadowCenterX= rect.centerX();
        mShadowCenterY = rect.centerY();
    }

    /**
     * 设置指针bound
     */
    private void setPointerBound() {
        mPointerBoundLeft = (int) (mShadowCenterX - mBitmapWidth + mBitmapHeight / 2);
        mPointerBoundTop = (int) (mShadowCenterY - mBitmapHeight / 2);
        mPointerBoundRight = (int) (mShadowCenterX + mBitmapHeight / 2);
        mPointerBoundBottom = (int) (mShadowCenterY + mBitmapHeight / 2);
        // FIXME
    }

    /**
     * 缩放指针位图
     * @param rect
     */
    private void scalePointerBitmap(RectF rect) {
        Object obj = getTag();
        if (obj != null && obj instanceof Boolean) {
            if ((Boolean) obj) {
                return;
            }
        }
        // 只会执行一次，分配一次内存
        doScalePointerBitmap(rect);
        setTag(true);
    }

    /**
     * 执行指针缩放
     * @param rect
     */
    private void doScalePointerBitmap(RectF rect) {
        if (rect.width() == 0) {
            return;
        }
        float originalWidth = mPointerBitmap.getWidth();
        float originalHeight = mPointerBitmap.getHeight();
        mBitmapWidth = rect.width() / 2 + originalHeight / 2;
        mBitmapHeight = mBitmapWidth / originalWidth * originalHeight;
        mPointerBitmap = Bitmap.createScaledBitmap(mPointerBitmap, (int) mBitmapWidth, (int) mBitmapHeight, true);
        mPointerBitmapDrawable = new BitmapDrawable(getResources(), mPointerBitmap);
    }

    /**
     * 设置当前指针角度
     * @param angle
     */
    void setCurrAngle(float angle) {
        mCurrAngle = angle;
    }

    /**
     * 获取当前指针角度
     * @return
     */
    float getCurrAngle() {
        return mCurrAngle;
    }

    /**
     * 计算2的指数
     * @param n
     * @return
     */
    private static float power2(int n) {
        return (float) Math.pow(2, n);
    }

    /**
     * 根据网速计算角度
     *
     * @param speed
     * @return
     */
    float calculateAngle(int speed) {
        float speedValue;
        // speedValue的单位是kb
        speedValue = (float) speed / Constant.KB_IN_BYTES;
        if (speedValue <= SCALE_VALUES[0]) {
            return MIN_ANGLE;
        } else if (speedValue > SCALE_VALUES[SCALE_VALUES.length - 1]) {
            return MAX_ANGLE;
        }
        int lower = SCALE_VALUES.length;
        int upper = lower;
        for (int i = 0; i < SCALE_VALUES.length; i++) {
            if (speedValue < SCALE_VALUES[i]) {
                lower = i - 1;
                upper = i;
                break;
            }
        }
        return linearFitting(speedValue, lower, upper);
    }
    
    /**
     * 根据网速计算角度，为了计算效率，采用线段拟合近似的方式。
     * @param speedValue
     * @param start
     * @param end
     * @return
     */
    private float linearFitting(float speedValue, int start, int end) {
        if (start < 0) {
            start = 0;
        }
        if (end >= SCALE_VALUES.length) {
            end = SCALE_VALUES.length - 1;
        }
        return CELL_ANGLE * (end - 1) + (speedValue - SCALE_VALUES[start]) / (SCALE_VALUES[end]
                - SCALE_VALUES[start]) * CELL_ANGLE - Math.abs(MIN_ANGLE);
    }

    /**
     * 清理资源
     */
    void clearUp() {
        if (mPointerBitmap != null && !mPointerBitmap.isRecycled()) {
            mPointerBitmap.recycle();
            mPointerBitmap = null;
        }
        if (mPointerBitmapDrawable != null) {
            mPointerBitmapDrawable = null;
        }
    }
}