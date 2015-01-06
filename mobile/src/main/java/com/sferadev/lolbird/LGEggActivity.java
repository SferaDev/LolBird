package com.sferadev.lolbird;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.lge.app.floating.FloatableActivity;

public class LGEggActivity extends FloatableActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_egg);
        Egg world = (Egg) findViewById(R.id.world);
        world.setScoreField((TextView) findViewById(R.id.score));
        world.setSplash(findViewById(R.id.welcome));
        Log.v("LolBird", "focus: " + world.requestFocus());
    }
}