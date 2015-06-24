package com.sferadev.lolbird;

import android.animation.TimeAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build.VERSION_CODES;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Random;

import static com.sferadev.lolbird.Utils.isBuildHigherThanVersion;

@SuppressLint("NewApi")
public class Egg extends FrameLayout {
    private static final String TAG = "LolBird";

    private static final boolean DEBUG = false;
    private static final boolean DEBUG_DRAW = false;
    private static final boolean AUTOSTART = true;
    private static final float DEBUG_SPEED_MULTIPLIER = 0.7f; // 0.1f;
    private static final int[][] SKIES = {
            {0xFFc0c0FF, 0xFFa0a0FF}, // DAY
            {0xFF000010, 0xFF000000}, // NIGHT
            {0xFF000040, 0xFF000010}, // TWILIGHT
            {0xFFa08020, 0xFF204080}, // SUNSET
    };
    private static Params PARAMS;
    private final float[] hsv = {0, 0, 0};
    private final Vibrator mVibrator;
    private final AudioManager mAudioManager;
    private final ArrayList<Obstacle> mObstaclesInPlay = new ArrayList<>();
    private TimeAnimator mAnim;
    private TextView mScoreField;
    private View mSplash;
    private Player mDroid;
    private float t, dt;
    private int mScore;
    private float mLastPipeTime; // in sec
    private int mWidth, mHeight;
    private boolean mAnimating, mPlaying;
    private boolean mFrozen; // after death, a short backoff
    private int mTimeOfDay;

    public Egg(Context context) {
        this(context, null);
    }

    public Egg(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Egg(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        setFocusable(true);
        PARAMS = new Params(getResources());
        mTimeOfDay = irand(0, SKIES.length);
        // we assume everything will be laid out left|top
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
    }

    private static void L(String s, Object... objects) {
        if (DEBUG) {
            Log.d(TAG, objects.length == 0 ? s : String.format(s, objects));
        }
    }

    private static float lerp(float x, float a, float b) {
        return (b - a) * x + a;
    }

    private static float rlerp(float v, float a, float b) {
        return (v - a) / (b - a);
    }

    private static float clamp(float f) {
        return f < 0f ? 0f : f > 1f ? 1f : f;
    }

    private static float frand() {
        return (float) Math.random();
    }

    private static float frand(float a, float b) {
        return lerp(frand(), a, b);
    }

    private static int irand(int a, int b) {
        return (int) lerp(frand(), (float) a, (float) b);
    }

    @Override
    public boolean willNotDraw() {
        return true;
    }

    public int getGameWidth() {
        return mWidth;
    }

    public int getGameHeight() {
        return mHeight;
    }

    private float getGameTime() {
        return t;
    }

    public float getLastTimeStep() {
        return dt;
    }

    public void setScoreField(TextView tv) {
        mScoreField = tv;
        if (tv != null) {
            if (isBuildHigherThanVersion(VERSION_CODES.LOLLIPOP))
                tv.setTranslationZ(PARAMS.HUD_Z);
            if (!(mAnimating && mPlaying)) {
                tv.setTranslationY(-500);
            }
        }
    }

    public void setSplash(View v) {
        mSplash = v;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        stop();
        reset();
        if (AUTOSTART) {
            start(false);
        }
    }

    private void thump() {
        if (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
            // No interruptions. Not even game haptics.
            return;
        }
        mVibrator.vibrate(80);
    }

    public void reset() {
        L("reset");
        final Drawable sky = new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                SKIES[mTimeOfDay]
        );
        sky.setDither(true);
        setBackground(sky);

        boolean mFlipped = frand() > 0.5f;
        setScaleX(mFlipped ? -1 : 1);

        setScore(0);

        int i = getChildCount();
        while (i-- > 0) {
            final View v = getChildAt(i);
            if (v instanceof GameView) {
                removeViewAt(i);
            }
        }

        mObstaclesInPlay.clear();

        mWidth = getWidth();
        mHeight = getHeight();

        final int mh = mHeight / 6;
        final int N = 20;
        for (i = 0; i < N; i++) {
            final Scenery s;
            s = new Building(getContext());

            s.z = (float) i / N;
            if (isBuildHigherThanVersion(VERSION_CODES.LOLLIPOP))
                s.setTranslationZ(PARAMS.SCENERY_Z * (1 + s.z));
            s.v = 0.85f * s.z; // buildings move proportional to their distance
            hsv[0] = 175;
            hsv[1] = 0.25f;
            hsv[2] = 1 * s.z;
            //noinspection ResourceType
            s.setBackgroundColor(Color.HSVToColor(hsv));
            s.h = irand(PARAMS.BUILDING_HEIGHT_MIN, mh);

            final LayoutParams lp = new LayoutParams(s.w, s.h);
            lp.gravity = Gravity.BOTTOM;

            addView(s, lp);
            s.setTranslationX(frand(-lp.width, mWidth + lp.width));
        }

        mDroid = new Player(getContext());
        mDroid.setX(mWidth / 2);
        mDroid.setY(mHeight / 2);
        addView(mDroid, new LayoutParams(PARAMS.PLAYER_SIZE, PARAMS.PLAYER_SIZE));
        mAnim = new TimeAnimator();
        mAnim.setTimeListener(new TimeAnimator.TimeListener() {
            @Override
            public void onTimeUpdate(TimeAnimator timeAnimator, long t, long dt) {
                step(t, dt);
            }
        });
    }

    private void setScore(int score) {
        mScore = score;
        if (mScoreField != null) {
            mScoreField.setText(String.valueOf(score));
        }
    }

    private void addScore(int incr) {
        setScore(mScore + incr);
    }

    public void start(boolean startPlaying) {
        L("start(startPlaying=%s)", startPlaying ? "true" : "false");
        if (startPlaying) {
            mPlaying = true;

            t = 0;
            mLastPipeTime = getGameTime() - PARAMS.OBSTACLE_PERIOD;

            if (mSplash != null && mSplash.getAlpha() > 0f) {
                if (isBuildHigherThanVersion(VERSION_CODES.LOLLIPOP))
                    mSplash.setTranslationZ(PARAMS.HUD_Z);
                if (isBuildHigherThanVersion(VERSION_CODES.LOLLIPOP))
                    mSplash.animate().alpha(0).translationZ(0).setDuration(400);

                mScoreField.animate().translationY(0)
                        .setInterpolator(new DecelerateInterpolator())
                        .setDuration(1500);
            }

            mScoreField.setTextColor(0xFFAAAAAA);
            mScoreField.setBackgroundResource(R.drawable.scorecard);
            mDroid.setVisibility(View.VISIBLE);
            mDroid.setX(mWidth / 2);
            mDroid.setY(mHeight / 2);
        } else {
            mDroid.setVisibility(View.GONE);
        }
        if (!mAnimating) {
            mAnim.start();
            mAnimating = true;
        }
    }

    public void stop() {
        if (mAnimating) {
            mAnim.cancel();
            mAnim = null;
            mAnimating = false;
            mScoreField.setTextColor(0xFFFFFFFF);
            mScoreField.setBackgroundResource(R.drawable.scorecard_gameover);
            mTimeOfDay = irand(0, SKIES.length); // for next reset
            mFrozen = true;
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    mFrozen = false;
                }
            }, 250);
        }
    }

    private void step(long t_ms, long dt_ms) {
        t = t_ms / 1000f; // seconds
        dt = dt_ms / 1000f;

        if (DEBUG) {
            t *= DEBUG_SPEED_MULTIPLIER;
            dt *= DEBUG_SPEED_MULTIPLIER;
        }

        // 1. Move all objects and update bounds
        final int N = getChildCount();
        int i = 0;
        for (; i < N; i++) {
            final View v = getChildAt(i);
            if (v instanceof GameView) {
                ((GameView) v).step(t_ms, dt_ms, t, dt);
            }
        }

        // 2. Check for altitude
        if (mPlaying && mDroid.below(mHeight)) {
            L("player hit the floor");
            thump();
            stop();
        }

        // 3. Check for obstacles
        boolean passedBarrier = false;
        for (int j = mObstaclesInPlay.size(); j-- > 0; ) {
            final Obstacle ob = mObstaclesInPlay.get(j);
            if (mPlaying && ob.intersects(mDroid)) {
                L("player hit an obstacle");
                thump();
                stop();
            } else if (ob.cleared(mDroid)) {
                passedBarrier = true;
                mObstaclesInPlay.remove(j);
            }
        }

        if (mPlaying && passedBarrier) {
            addScore(1);
        }

        // 4. Handle edge of screen
        // Walk backwards to make sure removal is safe
        while (i-- > 0) {
            final View v = getChildAt(i);
            if (v instanceof Obstacle) {
                if (v.getTranslationX() + v.getWidth() < 0) {
                    removeViewAt(i);
                }
            } else if (v instanceof Scenery) {
                final Scenery s = (Scenery) v;
                if (v.getTranslationX() + s.w < 0) {
                    v.setTranslationX(getWidth());
                }
            }
        }

        // 3. Time for more obstacles!
        if (mPlaying && (t - mLastPipeTime) > PARAMS.OBSTACLE_PERIOD) {
            mLastPipeTime = t;
            final int obstacley =
                    (int) (frand() * (mHeight - 2 * PARAMS.OBSTACLE_MIN - PARAMS.OBSTACLE_GAP)) +
                            PARAMS.OBSTACLE_MIN;

            final int inset = (PARAMS.OBSTACLE_WIDTH - PARAMS.OBSTACLE_STEM_WIDTH) / 2;
            final int yinset = PARAMS.OBSTACLE_WIDTH / 2;

            final int d1 = irand(0, 250);
            final Obstacle s1 = new Obstacle(getContext(), obstacley - yinset);
            addView(s1, new LayoutParams(
                    PARAMS.OBSTACLE_STEM_WIDTH,
                    (int) s1.h,
                    Gravity.TOP | Gravity.LEFT));
            s1.setTranslationX(mWidth + inset);
            s1.setTranslationY(-s1.h - yinset);
            if (isBuildHigherThanVersion(VERSION_CODES.LOLLIPOP))
                s1.setTranslationZ(PARAMS.OBSTACLE_Z * 0.75f);
            s1.animate()
                    .translationY(0)
                    .setStartDelay(d1)
                    .setDuration(250);
            mObstaclesInPlay.add(s1);

            final Obstacle p1 = new Obstacle(getContext(), PARAMS.OBSTACLE_WIDTH);
            addView(p1, new LayoutParams(
                    PARAMS.OBSTACLE_WIDTH,
                    PARAMS.OBSTACLE_WIDTH,
                    Gravity.TOP | Gravity.LEFT));
            p1.setTranslationX(mWidth);
            p1.setTranslationY(-mHeight);
            if (isBuildHigherThanVersion(VERSION_CODES.LOLLIPOP))
                p1.setTranslationZ(PARAMS.OBSTACLE_Z);
            p1.setScaleX(0.25f);
            p1.setScaleY(0.25f);
            p1.animate()
                    .translationY(s1.h - inset)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(d1)
                    .setDuration(250);
            mObstaclesInPlay.add(p1);

            final int d2 = irand(0, 250);
            final Obstacle s2 = new Obstacle(getContext(),
                    mHeight - obstacley - PARAMS.OBSTACLE_GAP - yinset);
            addView(s2, new LayoutParams(
                    PARAMS.OBSTACLE_STEM_WIDTH,
                    (int) s2.h,
                    Gravity.TOP | Gravity.LEFT));
            s2.setTranslationX(mWidth + inset);
            s2.setTranslationY(mHeight + yinset);
            if (isBuildHigherThanVersion(VERSION_CODES.LOLLIPOP))
                s2.setTranslationZ(PARAMS.OBSTACLE_Z * 0.75f);
            s2.animate()
                    .translationY(mHeight - s2.h)
                    .setStartDelay(d2)
                    .setDuration(400);
            mObstaclesInPlay.add(s2);

            final Obstacle p2 = new Obstacle(getContext(), PARAMS.OBSTACLE_WIDTH);
            addView(p2, new LayoutParams(
                    PARAMS.OBSTACLE_WIDTH,
                    PARAMS.OBSTACLE_WIDTH,
                    Gravity.TOP | Gravity.LEFT));
            p2.setTranslationX(mWidth);
            p2.setTranslationY(mHeight);
            if (isBuildHigherThanVersion(VERSION_CODES.LOLLIPOP))
                p2.setTranslationZ(PARAMS.OBSTACLE_Z);
            p2.setScaleX(0.25f);
            p2.setScaleY(0.25f);
            p2.animate()
                    .translationY(mHeight - s2.h - yinset)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(d2)
                    .setDuration(400);
            mObstaclesInPlay.add(p2);
        }

        if (DEBUG_DRAW) invalidate();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        L("touch: %s", ev);
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                poke();
                return true;
            case MotionEvent.ACTION_UP:
                unpoke();
                return true;
        }
        return false;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        L("trackball: %s", ev);
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                poke();
                return true;
            case MotionEvent.ACTION_UP:
                unpoke();
                return true;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent ev) {
        L("keyDown: %d", keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                poke();
                return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent ev) {
        L("keyDown: %d", keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                unpoke();
                return true;
        }
        return false;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        L("generic: %s", ev);
        return false;
    }

    private void poke() {
        L("poke");
        mDroid.setVisibility(View.VISIBLE);
        if (mFrozen) return;
        if (!mAnimating) {
            reset();
            start(true);
        } else if (!mPlaying) {
            start(true);
        }
        mDroid.boost();
        if (DEBUG) {
            mDroid.dv *= DEBUG_SPEED_MULTIPLIER;
            mDroid.animate().setDuration((long) (200 / DEBUG_SPEED_MULTIPLIER));
        }
    }

    private void unpoke() {
        L("unboost");
        if (mFrozen) return;
        if (!mAnimating) return;
        mDroid.unboost();
    }

    @Override
    public void onDraw(Canvas c) {
        super.onDraw(c);

        if (!DEBUG_DRAW) return;

        final Paint pt = new Paint();
        pt.setColor(0xFFFFFFFF);
        final int L = mDroid.corners.length;
        final int N = L / 2;
        for (int i = 0; i < N; i++) {
            final int x = (int) mDroid.corners[i * 2];
            final int y = (int) mDroid.corners[i * 2 + 1];
            c.drawCircle(x, y, 4, pt);
            c.drawLine(x, y,
                    mDroid.corners[(i * 2 + 2) % L],
                    mDroid.corners[(i * 2 + 3) % L],
                    pt);
        }

        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(getResources().getDisplayMetrics().density);

        final int M = getChildCount();
        pt.setColor(0x8000FF00);
        for (int i = 0; i < M; i++) {
            final View v = getChildAt(i);
            if (v == mDroid) continue;
            if (!(v instanceof GameView)) continue;
            final Rect r = new Rect();
            if(isBuildHigherThanVersion(VERSION_CODES.KITKAT)) v.getHitRect(r);
            else Utils.getHitRect(v, r);
            c.drawRect(r, pt);
        }

        pt.setColor(Color.BLACK);
        final StringBuilder sb = new StringBuilder("obstacles: ");
        for (Obstacle ob : mObstaclesInPlay) {
            sb.append(ob.hitRect.toShortString());
            sb.append(" ");
        }
        pt.setTextSize(20f);
        c.drawText(sb.toString(), 20, 100, pt);
    }

    private interface GameView {
        void step(long t_ms, long dt_ms, float t, float dt);
    }

    private static class Params {
        public final float TRANSLATION_PER_SEC;
        public final int OBSTACLE_SPACING;
        public final int OBSTACLE_PERIOD;
        public final int BOOST_DV;
        public final int PLAYER_HIT_SIZE;
        public final int PLAYER_SIZE;
        public final int OBSTACLE_WIDTH;
        public final int OBSTACLE_STEM_WIDTH;
        public final int OBSTACLE_GAP;
        public final int BUILDING_WIDTH_MIN;
        public final int BUILDING_WIDTH_MAX;
        public final int BUILDING_HEIGHT_MIN;
        public final int G;
        public final int MAX_V;
        public final float SCENERY_Z;
        public final float OBSTACLE_Z;
        public final float PLAYER_Z;
        public final float PLAYER_Z_BOOST;
        public final float HUD_Z;
        public int OBSTACLE_MIN;

        public Params(Resources res) {
            TRANSLATION_PER_SEC = res.getDimension(R.dimen.translation_per_sec);
            OBSTACLE_SPACING = res.getDimensionPixelSize(R.dimen.obstacle_spacing);
            OBSTACLE_PERIOD = (int) (OBSTACLE_SPACING / TRANSLATION_PER_SEC);
            BOOST_DV = res.getDimensionPixelSize(R.dimen.boost_dv);
            PLAYER_HIT_SIZE = res.getDimensionPixelSize(R.dimen.player_hit_size);
            PLAYER_SIZE = res.getDimensionPixelSize(R.dimen.player_size);
            OBSTACLE_WIDTH = res.getDimensionPixelSize(R.dimen.obstacle_width);
            OBSTACLE_STEM_WIDTH = res.getDimensionPixelSize(R.dimen.obstacle_stem_width);
            OBSTACLE_GAP = res.getDimensionPixelSize(R.dimen.obstacle_gap);
            OBSTACLE_MIN = res.getDimensionPixelSize(R.dimen.obstacle_height_min);
            BUILDING_HEIGHT_MIN = res.getDimensionPixelSize(R.dimen.building_height_min);
            BUILDING_WIDTH_MIN = res.getDimensionPixelSize(R.dimen.building_width_min);
            BUILDING_WIDTH_MAX = res.getDimensionPixelSize(R.dimen.building_width_max);

            G = res.getDimensionPixelSize(R.dimen.G);
            MAX_V = res.getDimensionPixelSize(R.dimen.max_v);

            SCENERY_Z = res.getDimensionPixelSize(R.dimen.scenery_z);
            OBSTACLE_Z = res.getDimensionPixelSize(R.dimen.obstacle_z);
            PLAYER_Z = res.getDimensionPixelSize(R.dimen.player_z);
            PLAYER_Z_BOOST = res.getDimensionPixelSize(R.dimen.player_z_boost);
            HUD_Z = res.getDimensionPixelSize(R.dimen.hud_z);

            // Sanity checking
            if (OBSTACLE_MIN <= OBSTACLE_WIDTH / 2) {
                Log.e(TAG, "error: obstacles might be too short, adjusting");
                OBSTACLE_MIN = OBSTACLE_WIDTH / 2 + 1;
            }
        }
    }

    private class Player extends ImageView implements GameView {
        private final float[] sHull = new float[]{
                0.3f, 0f,    // left antenna
                0.7f, 0f,    // right antenna
                0.92f, 0.33f, // off the right shoulder of Orion
                0.92f, 0.75f, // right hand (our right, not his right)
                0.6f, 1f,    // right foot
                0.4f, 1f,    // left foot BLUE!
                0.08f, 0.75f, // sinistram
                0.08f, 0.33f, // cold shoulder
        };
        public final float[] corners = new float[sHull.length];
        public float dv;
        private boolean mBoosting;

        public Player(Context context) {
            super(context);

            setBackgroundResource(R.drawable.ic_launcher);
            if (isBuildHigherThanVersion(VERSION_CODES.LOLLIPOP)) {
                Random rnd = new Random();
                getBackground().setTintMode(PorterDuff.Mode.SRC_ATOP);
                getBackground().setTint(Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)));
                setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        final int w = view.getWidth();
                        final int h = view.getHeight();
                        final int ix = (int) (w * 0.3f);
                        final int iy = (int) (h * 0.2f);
                        outline.setRect(ix, iy, w - ix, h - iy);
                    }
                });
            }
        }

        public void prepareCheckIntersections() {
            final int inset = (PARAMS.PLAYER_SIZE - PARAMS.PLAYER_HIT_SIZE) / 2;
            final int scale = PARAMS.PLAYER_HIT_SIZE;
            final int N = sHull.length / 2;
            for (int i = 0; i < N; i++) {
                corners[i * 2] = scale * sHull[i * 2] + inset;
                corners[i * 2 + 1] = scale * sHull[i * 2 + 1] + inset;
            }
            final Matrix m = getMatrix();
            m.mapPoints(corners);
        }

        public boolean below(int h) {
            final int N = corners.length / 2;
            for (int i = 0; i < N; i++) {
                final int y = (int) corners[i * 2 + 1];
                if (y >= h) return true;
            }
            return false;
        }

        public void step(long t_ms, long dt_ms, float t, float dt) {
            if (getVisibility() != View.VISIBLE) return; // not playing yet

            if (mBoosting) {
                dv = -PARAMS.BOOST_DV;
            } else {
                dv += PARAMS.G;
            }
            if (dv < -PARAMS.MAX_V) dv = -PARAMS.MAX_V;
            else if (dv > PARAMS.MAX_V) dv = PARAMS.MAX_V;

            final float y = getTranslationY() + dv * dt;
            setTranslationY(y < 0 ? 0 : y);
            setRotation(
                    90 + lerp(clamp(rlerp(dv, PARAMS.MAX_V, -1 * PARAMS.MAX_V)), 90, -90));

            prepareCheckIntersections();
        }

        public void boost() {
            mBoosting = true;
            dv = -PARAMS.BOOST_DV;

            if (isBuildHigherThanVersion(VERSION_CODES.LOLLIPOP)) {
                animate().cancel();
                animate()
                        .scaleX(1.25f)
                        .scaleY(1.25f)
                        .translationZ(PARAMS.PLAYER_Z_BOOST)
                        .setDuration(100);
            }
            setScaleX(1.25f);
            setScaleY(1.25f);
        }

        public void unboost() {
            mBoosting = false;

            if (isBuildHigherThanVersion(VERSION_CODES.LOLLIPOP)) {
                animate().cancel();
                animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationZ(PARAMS.PLAYER_Z)
                        .setDuration(200);
            }
        }
    }

    private class Obstacle extends View implements GameView {
        public final Rect hitRect = new Rect();
        public final float h;

        final Random rnd = new Random();
        final int backgroundColor = Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));

        public Obstacle(Context context, float h) {
            super(context);
            setBackgroundColor(backgroundColor);
            this.h = h;
        }

        public boolean intersects(Player p) {
            final int N = p.corners.length / 2;
            for (int i = 0; i < N; i++) {
                final int x = (int) p.corners[i * 2];
                final int y = (int) p.corners[i * 2 + 1];
                if (hitRect.contains(x, y)) return true;
            }
            return false;
        }

        public boolean cleared(Player p) {
            final int N = p.corners.length / 2;
            for (int i = 0; i < N; i++) {
                final int x = (int) p.corners[i * 2];
                if (hitRect.right >= x) return false;
            }
            return true;
        }

        @Override
        public void step(long t_ms, long dt_ms, float t, float dt) {
            setTranslationX(getTranslationX() - PARAMS.TRANSLATION_PER_SEC * dt);
            if(isBuildHigherThanVersion(VERSION_CODES.KITKAT)) {
                getHitRect(hitRect);
            } else {
                Utils.getHitRect(this, hitRect);
            }
        }
    }

    private class Scenery extends FrameLayout implements GameView {
        public float z;
        public float v;
        public int h, w;

        public Scenery(Context context) {
            super(context);
        }

        @Override
        public void step(long t_ms, long dt_ms, float t, float dt) {
            setTranslationX(getTranslationX() - PARAMS.TRANSLATION_PER_SEC * dt * v);
        }
    }

    private class Building extends Scenery {
        public Building(Context context) {
            super(context);
            w = irand(PARAMS.BUILDING_WIDTH_MIN, PARAMS.BUILDING_WIDTH_MAX);
            h = 0; // will be setup later, along with z
            if (isBuildHigherThanVersion(VERSION_CODES.LOLLIPOP))
                setTranslationZ(PARAMS.SCENERY_Z);
        }
    }

}