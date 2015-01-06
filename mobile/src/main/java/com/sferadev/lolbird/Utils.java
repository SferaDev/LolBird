package com.sferadev.lolbird;

import android.os.Build;

public class Utils {

    // Check Whether Build Version is higher than x
    public static boolean isBuildHigherThanVersion(int version) {
        if (Build.VERSION.SDK_INT >= version) {
            return true;
        } else {
            return false;
        }
    }
}
