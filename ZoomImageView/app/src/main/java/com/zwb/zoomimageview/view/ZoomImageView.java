package com.zwb.zoomimageview.view;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

/**
 * Created by zwb
 * Description
 * Date 2017/5/23.
 */

public class ZoomImageView extends ImageView implements ViewTreeObserver.OnGlobalLayoutListener {

    private boolean once = false;
    private Matrix matrix;

    private float mInitScale;//初始化缩放比例
    private float mMaxScale;//最大缩放比例

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
            //图片的宽高都小于或者都大于控件的宽高
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
}
