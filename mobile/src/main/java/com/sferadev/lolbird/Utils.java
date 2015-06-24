package com.sferadev.lolbird;

import android.graphics.Rect;
import android.os.Build;
import android.view.View;

public class Utils {

    // Check Whether Build Version is higher than x
    public static boolean isBuildHigherThanVersion(int version) {
        return Build.VERSION.SDK_INT >= version;
    }

    //I hate Fragmentation
    public static void getHitRect(View v, Rect rect) {
        rect.left = (int) com.nineoldandroids.view.ViewHelper.getX(v);
        rect.top = (int) com.nineoldandroids.view.ViewHelper.getY(v);
        rect.right = rect.left + v.getWidth();
        rect.bottom = rect.top + v.getHeight();
    }
}
