package com.joekokosa.oscixer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.menu.ActionMenuItemView;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.net.DatagramSocket;
import java.util.Locale;

import static com.joekokosa.oscixer.FeedbackTracker.CS_FADER;
import static com.joekokosa.oscixer.FeedbackTracker.CS_ID;
import static com.joekokosa.oscixer.FeedbackTracker.CS_NAME;
import static com.joekokosa.oscixer.MainActivity.EXTRA_MESSAGE;

public class ControlActivity extends AppCompatActivity {
    static private DawController controller;
    static private DatagramSocket sock;
    private final Messenger mMessenger = new Messenger(new ResponseHandler(this));
    float fader;
    float last_fader;
    AppCompatImageView touch_area;
    private TextView textView;
    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private boolean mBound = false;
    private int strip;
    private int selected_strip;
    private float rec_enable;
    private VelocityTracker mVelocityTracker = null;


    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {

            Messenger mService = new Messenger(service);

            try {
                Message msg = Message.obtain(null, CixListener.MSG_REGISTER);
                msg.replyTo = mMessenger;
                msg.obj = sock;

                mService.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            controller.connectSurface();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            //
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //setContentView(R.layout.control_surface);
        setContentView(R.layout.control_surface2);
        // android:scaleType="fitXY"
        // android:layout_marginEnd="16dp"
        // app:layout_constraintVertical_bias="1.0"
        // https://developer.android.com/reference/android/support/constraint/ConstraintLayout.html


        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        //myToolbar.inflateMenu(R.menu.cs_menu);
        getSupportActionBar().openOptionsMenu();

        Intent intent = getIntent();
        String msg = intent.getStringExtra(EXTRA_MESSAGE);

        textView = (TextView) findViewById(R.id.feedback_text);
        textView.setText(msg);

        controller = new DawController();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String target_host = sharedPref.getString("target_host", "192.168.0.16");
        String target_port = sharedPref.getString("target_port", "3819");

        int port = 3819;

        try {
            port = Integer.parseInt(target_port);
        } catch (Exception e) {
            // TODO: update port in the shared prefs to be default value
            port = 3819;
        } finally {
            sock = controller.attachPorts(target_host, port);
        }

        touch_area = (AppCompatImageView) findViewById(R.id.touch_area);

        touch_area.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                // ... Respond to touch events
                int index = event.getActionIndex();
                int action = event.getActionMasked();
                int pointerId = event.getPointerId(index);

                int mWidth = v.getLeft() - v.getRight();
                int mHeight = v.getTop() - v.getBottom();
                boolean first_point = true;
                float deltaX;
                float lastX = 0.0f;
                float lastY = 0.0f;
                float posX;
                float posY;
                float avgY;
                float faderChange = 0.0f;
                float velocity_scale = 1.0f;
                float y_scale = 1.0f;
                float maxY = v.getTop();
                float minY = v.getBottom();


                //Log.d("Touch", String.format("Width=%d Height=%d",mWidth,mHeight));

                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        if (mVelocityTracker == null) {
                            // Retrieve a new VelocityTracker object to watch the velocity of a motion.
                            mVelocityTracker = VelocityTracker.obtain();
                        } else {
                            // Reset the velocity tracker back to its initial state.
                            mVelocityTracker.clear();
                        }
                        // Add a user's movement to the tracker.
                        mVelocityTracker.addMovement(event);
                        first_point = true;
                        last_fader = fader;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        mVelocityTracker.addMovement(event);
                        // When you want to determine the velocity, call
                        // computeCurrentVelocity(). Then call getXVelocity()
                        // and getYVelocity() to retrieve the velocity for each pointer ID.
                        mVelocityTracker.computeCurrentVelocity(1000);
                        // Log velocity of pixels per second
                        // Best practice to use VelocityTrackerCompat where possible.
                        // TODO: handle multi-touch
                        int nhist = event.getHistorySize();

                        Log.d("Fader change", String.format("num hist = %d", nhist));
                        for (int idx = 0; idx < nhist; idx++) {
                            posX = event.getHistoricalAxisValue(0, idx);
                            posY = event.getHistoricalAxisValue(1, idx);

                            //Log.d("Track", String.format("Pos(X,Y) = (%f,%f)" , posX, posY));
                            if (first_point) {
                                first_point = false;
                                lastX = posX;
                                lastY = posY;
                            } else {
                                deltaX = posX - lastX;
                                avgY = (lastY + posY) / 2.0f;
                                y_scale = (avgY - minY) / (maxY - minY) * (20.0f - 0.5f) + 0.5f;
                                faderChange += velocity_scale * y_scale * (deltaX / mWidth);
                                if ((faderChange > 0.05f) || (faderChange < -0.05f)) {
                                    Log.d("Fader change", String.format("%f deltaX =%f avgY = %f y_scale = %f lastfader = %f", faderChange, deltaX, avgY, y_scale, last_fader));
                                    last_fader += faderChange;
                                    controller.moveFader(selected_strip, last_fader);
                                    faderChange = 0.0f;
                                }
                                lastX = posX;
                                lastY = posY;
                            }
                        }

                        posX = event.getAxisValue(0);
                        posY = event.getAxisValue(1);

                        //Log.d("Track", String.format("Pos(X,Y) = (%f,%f)" , posX, posY));
                        if (first_point) {
                            first_point = false;
                            lastX = posX;
                            lastY = posY;
                        } else {
                            deltaX = posX - lastX;
                            avgY = (lastY + posY) / 2.0f;
                            y_scale = (avgY - minY) / (maxY - minY) * (20.0f - 0.5f) + 0.5f;
                            faderChange += velocity_scale * y_scale * (deltaX / mWidth);
                            if ((faderChange > 0.05f) || (faderChange < -0.05f)) {
                                Log.d("Fader change", String.format("%f deltaX =%f avgY = %f y_scale = %f lastfader = %f", faderChange, deltaX, avgY, y_scale, last_fader));
                                last_fader += faderChange;
                                controller.moveFader(selected_strip, last_fader);
                                faderChange = 0.0f;
                            }
                            lastX = posX;
                            lastY = posY;
                        }
                        //Log.d("Track", String.format("Pos(X,Y) = (%f,%f)" , event.getAxisValue(0), event.getAxisValue(1)));
                        //Log.d("Track", String.format("Index = %d ", index));
                        //Log.d("Track", "X velocity: " +
                        //        VelocityTrackerCompat.getXVelocity(mVelocityTracker,
                        //                pointerId));
                        //Log.d("Track", "Y velocity: " +
                        //        VelocityTrackerCompat.getYVelocity(mVelocityTracker,
                        //                pointerId));
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // Return a VelocityTracker object back to be re-used by others.
                        mVelocityTracker.recycle();
                        mVelocityTracker = null;

                        first_point = false;
                        break;
                }

                return true;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(com.joekokosa.oscixer.R.menu.cs_menu, menu);
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mBound) {
            Intent intent = new Intent(this, CixListener.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            mBound = true;
        }

        int width;
        int height;

        DisplayMetrics metrics = this.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;

        Log.v("R.attr", String.format(Locale.US, "GridLayout dimensions = %d, %d", width, height));

        Display display = this.getWindowManager().getDefaultDisplay();
        display.getMetrics(metrics);

        width = metrics.widthPixels;
        height = metrics.heightPixels;

        Log.v("R.attr", String.format(Locale.US, "GridLayout dimensions = %d, %d", width, height));

        ViewGroup.LayoutParams params = findViewById(R.id.touch_area).getLayoutParams();
        params.height = (height / 4) * 3;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService
        Intent intent = new Intent(this, CixListener.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mBound = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    public void onClicks(View view) {
        switch (view.getId()) {
            case R.id.transport_play:
                controller.startTransport();
                break;
            case R.id.transport_stop:
                controller.stopTransport();
                break;
            case R.id.transport_home:
                controller.goHome();
                break;
            case R.id.transport_end:
                controller.goEnd();
                break;
            case R.id.transport_prev_mark:
                controller.prevMark();
                break;
            case R.id.transport_next_mark:
                controller.nextMark();
                break;
            case R.id.transport_loop:
                controller.toggle_loop();
                break;
            case R.id.next_strip:
                controller.selectTrack(selected_strip + 1);
                break;
            case R.id.prev_strip:
                controller.selectTrack(selected_strip - 1);
                break;
        }
    }

    public void menuClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.globalRecEnable:
                controller.globalRecordEnable();
                break;
            case R.id.trackRecEnable:
                if (rec_enable == 0.0f) {
                    controller.stripRecordEnable(strip);
                } else {
                    controller.stripRecordDisable(strip);
                }
                break;
            default:
                Log.v("Menu", "Unknown resource id");
        }
    }


    class ResponseHandler extends Handler {
        final ControlActivity activity;
        float saved_trk_rec_enable = 0.0f;
        int saved_global_rec_enable;
        int temp_strip;

        ResponseHandler(ControlActivity activity) {
            this.activity = activity;
        }

        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case CixListener.FB_GAIN:
                    strip = message.getData().getInt(CS_ID, 0);
                    fader = message.getData().getFloat(CS_FADER, -999.0f);
                    String name = message.getData().getString(CS_NAME, "not found");
                    activity.textView.setText(String.format("Strip %d-%s = %f", strip, name, fader));
                    break;
                case CixListener.FB_SELECT:
                    selected_strip = message.getData().getInt(CS_ID, 0);
                    break;
                case CixListener.FB_TRACK_RECENABLE:
                    temp_strip = message.getData().getInt(CS_ID, 0);
                    if (selected_strip == temp_strip) {
                        float temp_rec_enable = message.getData().getFloat(FeedbackTracker.CS_TRACK_RECENABLE, 0.0f);
                        if (temp_rec_enable != rec_enable) {
                            try {
                                ActionMenuItemView trk_enable = (ActionMenuItemView) findViewById(R.id.trackRecEnable);
                                if (temp_rec_enable == 0.0f) {
                                    //trk_enable.setIcon(R.drawable.track_rec_disabled);
                                    trk_enable.getItemData().setIcon(R.drawable.track_rec_disabled);
                                } else {
                                    trk_enable.getItemData().setIcon(R.drawable.track_rec_enabled);
                                }
                            } catch (Exception e) {
                                Log.e("Recenable", e.getMessage());
                            }
                        }
                        rec_enable = temp_rec_enable;
                    }
                    break;
                case CixListener.FB_GLOBAL_RECENABLE:
                    int temp_global_rec_enable = message.arg1;
                    if (temp_global_rec_enable != saved_global_rec_enable) {
                        try {
                            ActionMenuItemView global_enable = (ActionMenuItemView) findViewById(R.id.globalRecEnable);
                            if (temp_global_rec_enable == 0) {
                                //trk_enable.setIcon(R.drawable.track_rec_disabled);
                                global_enable.getItemData().setIcon(R.drawable.global_rec_disabled);
                            } else {
                                global_enable.getItemData().setIcon(R.drawable.global_rec_enabled);
                            }
                        } catch (Exception e) {
                            Log.e("Recenable", e.getMessage());
                        }
                    }
                    saved_global_rec_enable = temp_global_rec_enable;

                    break;
                case CixListener.FB_STRIP:
                    temp_strip = message.getData().getInt(CS_ID, 0);
                    if (selected_strip == 0) {
                        selected_strip = temp_strip;
                        controller.selectTrack(selected_strip);
                    }

                    if (selected_strip == temp_strip) {
                        strip = temp_strip;
                        name = message.getData().getString(FeedbackTracker.CS_NAME, "not found");
                        fader = message.getData().getFloat(FeedbackTracker.CS_FADER, 0.0f);
                        /*
                        String comment = message.getData().getString(FeedbackTracker.CS_COMMENT, "");
                        float mute = message.getData().getFloat(FeedbackTracker.CS_MUTE, 0.0f);
                        float solo = message.getData().getFloat(FeedbackTracker.CS_SOLO, 0.0f);
                        float trim = message.getData().getFloat(FeedbackTracker.CS_TRIM, 0.0f);
                        float solo_iso = message.getData().getFloat(FeedbackTracker.CS_SOLO_ISO, 0.0f);
                        float solo_safe = message.getData().getFloat(FeedbackTracker.CS_SOLO_SAFE, 0.0f);
                        float polarity = message.getData().getFloat(FeedbackTracker.CS_POLARITY, 0.0f);
                        float monitor_input = message.getData().getFloat(FeedbackTracker.CS_MONITOR_INPUT, 0.0f);
                        float monitor_disk = message.getData().getFloat(FeedbackTracker.CS_MONITOR_DISK, 0.0f);
                        rec_enable = message.getData().getFloat(FeedbackTracker.CS_TRACK_RECENABLE, 0.0f);
                        float rec_safe = message.getData().getFloat(FeedbackTracker.CS_RECSAFE, 0.0f);
                        // TODO: findout what expanded is??
                        float expanded = message.getData().getFloat(FeedbackTracker.CS_EXPANDED, 0.0f);
                        float pan_stereo_position = message.getData().getFloat(FeedbackTracker.CS_PAN_STERO_POSITION, 0.0f);
                        float pan_stereo_width = message.getData().getFloat(FeedbackTracker.CS_PAN_STERO_WIDTH, 0.0f);
                        float num_inputs = message.getData().getFloat(FeedbackTracker.CS_NUM_INPUTS, 0.0f);
                        float num_outputs = message.getData().getFloat(FeedbackTracker.CS_NUM_OUTPUTS, 0.0f);

                         */

                        try {
                            String state = String.format("Id = %d\tFader = %f\t", strip, fader);
                            activity.textView.setText(state);

                            Toolbar toolbar = (Toolbar) this.activity.findViewById(R.id.my_toolbar);
                            toolbar.setTitle("Oscixer  -  " + name);
                        } catch (Exception e) {
                            Log.e("STRIP", e.getMessage());
                        }
                    }
                    break;
            }
        }
    }
}