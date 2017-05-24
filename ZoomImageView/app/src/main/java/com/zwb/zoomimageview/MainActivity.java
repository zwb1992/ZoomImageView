package com.zwb.zoomimageview;

import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import com.zwb.zoomimageview.view.ZoomImageView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ViewPager viewPager;
    private List<ZoomImageView> list = new ArrayList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        viewPager = (ViewPager)findViewById(R.id.viewPager);
        initData();
        viewPager.setAdapter(new MyAdapter());

    }

    private void initData(){
        for (int i = 0;i<4;i++){
            ZoomImageView zoomImageView = new ZoomImageView(this);
            zoomImageView.setImageResource(R.mipmap.bg1);
            list.add(zoomImageView);
            zoomImageView.setTouchCallBack(new ZoomImageView.TouchCallBack() {
                @Override
                public void onSingleTap() {
                    finish();
                }
            });
        }
    }

    private class MyAdapter extends PagerAdapter{
        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView(list.get(position));
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            container.addView(list.get(position));
            return list.get(position);
        }
    }
}
