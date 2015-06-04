package com.sferadev.lolbird;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class WearEggActivity extends Activity {
    Egg world;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_egg);
        world = (Egg) findViewById(R.id.world);
        world.setScoreField((TextView) findViewById(R.id.score));
        world.setSplash(findViewById(R.id.welcome));
        Log.v("LolBird", "focus: " + world.requestFocus());
    }

    @Override
    public void onPause() {
        world.stop();
        super.onPause();
    }
}
