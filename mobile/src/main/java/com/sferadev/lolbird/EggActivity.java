package com.sferadev.lolbird;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.multiwindow.SMultiWindow;
import com.samsung.android.sdk.multiwindow.SMultiWindowActivity;

public class EggActivity extends Activity {
    private SMultiWindow mMultiWindow = null;
    private SMultiWindowActivity mMultiWindowActivity = null;

    Egg world;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_egg);
        world = (Egg) findViewById(R.id.world);
        world.setScoreField((TextView) findViewById(R.id.score));
        world.setSplash(findViewById(R.id.welcome));
        Log.v("LolBird", "focus: " + world.requestFocus());

        try {
            mMultiWindow = new SMultiWindow();
            mMultiWindow.initialize(this);
            mMultiWindowActivity = new SMultiWindowActivity(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        world.stop();
        super.onPause();
    }
}