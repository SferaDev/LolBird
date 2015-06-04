package com.sferadev.lolbird;

import android.os.Build;

public class Utils {

    // Check Whether Build Version is higher than x
    public static boolean isBuildHigherThanVersion(int version) {
        return Build.VERSION.SDK_INT >= version;
    }
}
