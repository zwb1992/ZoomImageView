package com.zwb.zoomimageview.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import java.text.DecimalFormat;

/**
 * Created by zwb
 * Description
 * Date 2017/5/23.
 */

public class ZoomImageView extends ImageView implements ViewTreeObserver.OnGlobalLayoutListener, ScaleGestureDetector.OnScaleGestureListener {

    private boolean once = false;
    private Matrix matrix;

    private float mInitScale;//初始化缩放比例
    private float mMiddleScale;//中等缩放比例
    private float mMaxScale;//最大缩放比例

    /**
     * 检测图片多点触控的缩放比例
     */
    private ScaleGestureDetector mScaleGestureDetector;
    /**
     * 双击放大缩小图片
     */
    private GestureDetector gestureDetector;

    private int mLastPointCount;//最近一次按下的点个数
    private float mLastX, mLastY;//最后按下时各手指的中心点
    private int mTouchSlop;//滑动和点击事件的临界点
    private boolean isCanDrag;//是否可拖动
    private boolean isCheckLeftAndRight;//左右是否能拖动
    private boolean isCheckTopAndBottom;//上下是否能拖动

    private boolean autoScale;//图片是否正在自动缩放

    private TouchCallBack touchCallBack;

    public void setTouchCallBack(TouchCallBack touchCallBack) {
        this.touchCallBack = touchCallBack;
    }

    public ZoomImageView(Context context) {
        this(context, null);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setScaleType(ScaleType.MATRIX);
        matrix = new Matrix();
        mScaleGestureDetector = new ScaleGestureDetector(getContext(), this);
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                Log.e("info", "==onDoubleTap==" + e.getPointerCount());
                if (autoScale) {
                    return true;
                }
                doubleTouchScaleImage(e.getX(), e.getY());
                return true;
            }

            @Override//同上者，但有附加条件，就是Android会确保单击之后短时间内没有再次单击，才会触发该函数。
            public boolean onSingleTapConfirmed(MotionEvent e) {
                Log.e("info", "==onSingleTapConfirmed==" + e.getPointerCount());
                if (touchCallBack != null) {
                    touchCallBack.onSingleTap();
                }
                return true;
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnGlobalLayoutListener(this);
    }

    @Override //布局发生变化
    public void onGlobalLayout() {
        Log.e("info", "==onGlobalLayout====" );
        if (!once) {
            matrix = new Matrix();
            float scale = 1.0f;
            int width = getWidth();
            int height = getHeight();
            Log.e("info", "==onGlobalLayout==width===" + width + ",==height===" + height);
            Drawable drawable = getDrawable();
            if (drawable == null) {
                return;
            }
            int dw = drawable.getIntrinsicWidth();
            int dh = drawable.getIntrinsicHeight();
            Log.e("info", "==onGlobalLayout==dw===" + dw + ",==dh===" + dh);
            //图片的宽度大于控价的宽度，且图片的高度小于控价的高度，缩放宽度
            if (dw > width && dh <= height) {
                scale = width * scale / dw;
            }
            //图片的宽度小于控价的宽度，且图片的高度大于控价的高度，缩放宽度
            if (dw <= width && dh > height) {
                scale = height * scale / dh;
            }
            //图片的宽高都小于或者都大于控件的宽高,取最小缩放比例
            if ((dw < width && dh < height) || (dw > width && dh > height)) {
                scale = Math.min(width * scale / dw, height * scale / dh);
            }
            mInitScale = scale;
            mMiddleScale = scale * 2;
            mMaxScale = scale * 4;
            //先移动到中心点
            matrix.postTranslate((width - dw) / 2, (height - dh) / 2);
            //以图片的中心点开始缩放
            matrix.postScale(mInitScale, mInitScale, width / 2, height / 2);
            setImageMatrix(matrix);
            once = true;
        }
    }

    /**
     * 获取当前图片的缩放比例，X轴与Y轴的缩放比例一致
     *
     * @return 返回当前缩放比例
     */
    private float getScale() {
        float[] floats = new float[9];
        Matrix matrix = this.matrix;
        matrix.getValues(floats);
        return floats[Matrix.MSCALE_X];
    }

    /**
     * 根据当前图片的Matrix获得图片的范围
     *
     * @return 当前图片显示的范围
     */
    private RectF getMatrixRectF() {
        Matrix matrix = this.matrix;
        RectF rectF = new RectF();
        Drawable d = getDrawable();
        if (d != null) {
            rectF.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            //对原始坐标点进行矩阵变换
            matrix.mapRect(rectF);
        }
        return rectF;
    }

    /**
     * 在缩放时，进行图片显示范围的控制
     * 即：如果图片一边相对于屏幕有边框，移动至无边框
     */
    private void checkBorderAndCenterWhenScale() {
        RectF rect = getMatrixRectF();
        float deltaX = 0;
        float deltaY = 0;

        int width = getWidth();
        int height = getHeight();

        // 如果宽或高大于屏幕，则控制范围
        if (rect.width() >= width) {
            if (rect.left > 0) {
                deltaX = -rect.left;
            }
            if (rect.right < width) {
                deltaX = width - rect.right;
            }
        }

        if (rect.height() >= height) {
            if (rect.top > 0) {
                deltaY = -rect.top;
            }
            if (rect.bottom < height) {
                deltaY = height - rect.bottom;
            }
        }
        //如果宽度小于屏幕的宽度，让其居中
        if (rect.width() < width) {
            deltaX = width * 0.5f - rect.right + rect.width() * 0.5f;
        }
        if (rect.height() < height) {
            deltaY = height * 0.5f - rect.bottom + 0.5f * rect.height();
        }
        matrix.postTranslate(deltaX, deltaY);
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        Log.e("info", "===onScale==");
        if (getDrawable() == null) {
            return true;
        }
        //当前的缩放比例
        float scale = getScale();
        //手指触摸时缩放的比例
        float scaleFactor = detector.getScaleFactor();

        //当前缩放范围在可缩放的范围
        if ((scale < mMaxScale && scaleFactor > 1.0f) || (scale > mInitScale && scaleFactor < 1.0f)) {
            //最大最小缩放比例判断，因为当前缩放比例是在scale基础上进行缩放的，所以超过了需要除以scale
            if (scale * scaleFactor > mMaxScale) {
                scaleFactor = mMaxScale / scale;
            }
            if (scale * scaleFactor < mInitScale) {
                scaleFactor = mInitScale / scale;
            }
            //在焦点处进行缩放
            matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            checkBorderAndCenterWhenScale();
            setImageMatrix(matrix);
        }
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        Log.e("info", "===onScaleBegin==");
        //必须返回true
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        Log.e("info", "===onScaleEnd==");
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event)) {
            return true;
        }
        mScaleGestureDetector.onTouchEvent(event);
        int pointCount = event.getPointerCount();
        float touchX = event.getX();
        float touchY = event.getY();
        //获得各触摸点x与y的平均值
        for (int i = 0; i < pointCount; i++) {
            touchX += event.getX(i);
            touchY += event.getY(i);
        }
        touchX /= pointCount;
        touchY /= pointCount;
        if (mLastPointCount != pointCount) {//有新的手指触摸
            isCanDrag = false;
            mLastX = touchX;
            mLastY = touchY;
        }
        mLastPointCount = pointCount;
        RectF rect = getMatrixRectF();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                //如果图片有一边超出屏幕，则屏蔽父控件事件，让其可拖动，0.01是各厂商的误差值
                if (rect.width() >= getWidth() + 0.01 || rect.height() >= getHeight() + 0.01) {
                    if (getParent() instanceof ViewPager) {
                        //父控件不拦截
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                //如果图片有一边超出屏幕，则屏蔽父控件事件，让其可拖动，0.01是各厂商的误差值
                if (rect.width() >= getWidth() + 0.01 || rect.height() >= getHeight() + 0.01) {
                    if (getParent() instanceof ViewPager) {
                        //父控件不拦截
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                float dx = touchX - mLastX;
                float dy = touchY - mLastY;
                isCanDrag = isCanDrag(dx, dy);
                if (isCanDrag) {
                    //当图片拖动到边界时，父控件拦截事件
                    if (rect.left >= 0 && dx > 0) {
                        if (getParent() instanceof ViewPager) {
                            //父控件拦截
                            getParent().requestDisallowInterceptTouchEvent(false);
                        }
                    }

                    if (rect.right <= getWidth() && dx < 0) {
                        if (getParent() instanceof ViewPager) {
                            //父控件拦截
                            getParent().requestDisallowInterceptTouchEvent(false);
                        }
                    }
                    moveActionForDrag(dx, dy);
                }
                mLastX = touchX;
                mLastY = touchY;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mLastPointCount = 0;
                break;
        }
        return true;
    }

    /**
     * 是否可以拖动
     *
     * @param dx
     * @param dy
     * @return
     */
    private boolean isCanDrag(float dx, float dy) {
        return Math.sqrt(dx * dx + dy * dy) > mTouchSlop;
    }

    /**
     * 拖动图片时的操作
     *
     * @param dx
     * @param dy
     */
    private void moveActionForDrag(float dx, float dy) {
        RectF rectF = getMatrixRectF();
        Drawable d = getDrawable();
        if (d != null) {
            isCheckLeftAndRight = isCheckTopAndBottom = true;
            // 如果宽度小于屏幕宽度，则禁止左右移动
            if (rectF.width() < getWidth()) {
                dx = 0;
                isCheckLeftAndRight = false;
            }
            // 如果高度小于屏幕高度，则禁止上下移动
            if (rectF.height() < getHeight()) {
                dy = 0;
                isCheckTopAndBottom = false;
            }
            matrix.postTranslate(dx, dy);
            checkMatrixBounds();
            setImageMatrix(matrix);
        }
    }

    /**
     * 移动过程中的边界检测
     */
    private void checkMatrixBounds() {
        RectF rectF = getMatrixRectF();
        float deltaX = 0;
        float deltaY = 0;

        int width = getWidth();
        int height = getHeight();
        //左边距离屏幕有距离,且能拖动
        if (rectF.left > 0 && isCheckLeftAndRight) {
            deltaX = -rectF.left;
        }
        //右边距离右边框有距离,且左右能拖动
        if (rectF.right < width && isCheckLeftAndRight) {
            deltaX = width - rectF.right;
        }
        if (rectF.top > 0 && isCheckTopAndBottom) {
            deltaY = -rectF.top;
        }
        if (rectF.bottom < height && isCheckTopAndBottom) {
            deltaY = height - rectF.bottom;
        }
        matrix.postTranslate(deltaX, deltaY);
    }

    /**
     * 双击后缩放图片
     *
     * @param centerX 缩放的中心点
     * @param centerY 缩放的中心点
     */
    private void doubleTouchScaleImage(final float centerX, final float centerY) {
        //截取两位小数
        final DecimalFormat df = new DecimalFormat("######0.00");
        float scale = getScale();
        autoScale = true;
//        Log.e("info", "==scale==" + scale);
        final float toScale;
        if (scale > mMiddleScale) {//当前缩放偏大，双击后变小
            toScale = mInitScale;
        } else {
            toScale = mMaxScale;
        }
//        Log.e("info", "==toScale==" + toScale);
        ValueAnimator animator = ValueAnimator.ofFloat(scale, toScale);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float curSCale = (float) animation.getAnimatedValue();
//                Log.e("info", "==curSCale==" + curSCale);
                if (df.format(curSCale).equals(df.format(toScale))) {//缩放结束
                    autoScale = false;
                }
                matrix.setScale(curSCale, curSCale, centerX, centerY);
                checkBorderAndCenterWhenScale();
                setImageMatrix(matrix);
            }
        });
        animator.setDuration(500);
        animator.start();
    }

    /**
     * 单击图片操作，类似微信，点击的时候结束预览
     */
    public interface TouchCallBack {
        void onSingleTap();
    }

    @Override //防止第二次设置图片的时候导致图片缩放异常，其他设置图片的方法类似，都会在onGlobalLayout中回调
    public void setImageResource(int resId) {
        once = false;
        super.setImageResource(resId);
    }
}
