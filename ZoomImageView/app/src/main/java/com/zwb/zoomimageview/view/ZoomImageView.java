package com.zwb.zoomimageview.view;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

/**
 * Created by zwb
 * Description
 * Date 2017/5/23.
 */

public class ZoomImageView extends ImageView implements ViewTreeObserver.OnGlobalLayoutListener, ScaleGestureDetector.OnScaleGestureListener {

    private boolean once = false;
    private Matrix matrix;

    private float mInitScale;//初始化缩放比例
    private float mMaxScale;//最大缩放比例

    /**
     * 检测图片多点触控的缩放比例
     */
    private ScaleGestureDetector mScaleGestureDetector;

    private int mLastPointCount;//最近一次按下的点个数
    private float mLastX, mLastY;//最后按下时各手指的中心点
    private int mTouchSlop;//滑动和点击事件的临界点
    private boolean isCanDrag;//是否可拖动
    private boolean isCheckLeftAndRight;//左右是否能拖动
    private boolean isCheckTopAndBottom;//上下是否能拖动

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
        if (!once) {
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
        //必须返回true
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
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
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                float dx = touchX - mLastX;
                float dy = touchY - mLastY;
                isCanDrag = isCanDrag(dx, dy);
                if (isCanDrag) {
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
        Log.e("info", "==dx==" + dx);
        Log.e("info", "==dy==" + dy);
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
}
