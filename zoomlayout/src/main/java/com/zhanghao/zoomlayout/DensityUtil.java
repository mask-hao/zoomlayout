package com.zhanghao.zoomlayout;

import android.content.Context;

/**
 * Created by zhanghao on 2018/3/4.
 */

public class DensityUtil {

    public static int dp2px(Context context, float dpVal) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpVal * scale + 0.5f);
    }

    public static float px2dp(Context context, float pxVal) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (pxVal / scale + 0.5f);
    }

}
