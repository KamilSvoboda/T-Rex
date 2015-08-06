package biz.svoboda.trex;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;

public class MainScreen extends ActionBarActivity {

    /*TODO: https://gist.github.com/blackcj/20efe2ac885c7297a676 */
    private static final String TAG = "MainScreen";

    private Boolean mKeepScreenOn = false;
    private Boolean mKeepCpuOn = true;

    private PowerManager.WakeLock mWakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_screen);

        //Registrace broadcastreceiveru komunikaci se sluzbou (musi byt tady, aby fungoval i po nove inicializaci aplikace z notifikace
        // The filter's action is BROADCAST_ACTION
        IntentFilter mIntentFilter = new IntentFilter(Constants.LOCATION_BROADCAST);
        // Instantiates a new mPositionReceiver
        NewPositionReceiver mPositionReceiver = new NewPositionReceiver();
        // Registers the mPositionReceiver and its intent filters
        LocalBroadcastManager.getInstance(this).registerReceiver(mPositionReceiver, mIntentFilter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main_screen, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, Settings.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Kliknutí na tlačítko spuštění odesílání
     *
     * @param view
     */
    public void startSending(View view) {
        if (!isServiceRunning(BackgroundLocationService.class))
        {
            //Check screen on/off settings
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            mKeepScreenOn = sharedPref.getBoolean("pref_screen_on", false);
            mKeepCpuOn = sharedPref.getBoolean("pref_cpu_on", true);
            if (mKeepScreenOn)
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (mKeepCpuOn) {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "TRexWakelockTag");
                mWakeLock.acquire();
            }

            //Nastartovani sluzby
            ComponentName comp = new ComponentName(getApplicationContext().getPackageName(), BackgroundLocationService.class.getName());
            ComponentName service = getApplicationContext().startService(new Intent().setComponent(comp));

            if (null == service) {
                // something really wrong here
                Toast.makeText(this, R.string.localiz_could_not_start, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Could not start localization service " + comp.toString());
            }
        }
        else
        {
            Toast.makeText(this, R.string.localiz_run, Toast.LENGTH_SHORT).show();
        }

    }

    /**
     * Kliknutí na tlačítko vypnutí odesílání
     *
     * @param view
     */
    public void stopSending(View view) {
        if (isServiceRunning(BackgroundLocationService.class)) {
            ComponentName comp = new ComponentName(getApplicationContext().getPackageName(), BackgroundLocationService.class.getName());
            getApplicationContext().stopService(new Intent().setComponent(comp));

            TextView dateText = (TextView) findViewById(R.id.text_position_date);
            dateText.setText(getResources().getString(R.string.textview_date));

            TextView latText = (TextView) findViewById(R.id.text_position_lat);
            latText.setText(getResources().getString(R.string.textview_lat));

            TextView lonText = (TextView) findViewById(R.id.text_position_lon);
            lonText.setText(getResources().getString(R.string.textview_lon));

            TextView altText = (TextView) findViewById(R.id.text_position_alt);
            altText.setText(getResources().getString(R.string.textview_alt));

            TextView speedText = (TextView) findViewById(R.id.text_position_speed);
            speedText.setText(getResources().getString(R.string.textview_speed));

            TextView speedBearing = (TextView) findViewById(R.id.text_position_bearing);
            speedBearing.setText(getResources().getString(R.string.textview_bearing));

            TextView respText = (TextView) findViewById(R.id.text_http_response);
            respText.setText(null);

            //remove flag, if any
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (mWakeLock != null) {
                mWakeLock.release();
                mWakeLock = null;
            }
        }
        else
            Toast.makeText(this, R.string.localiz_not_run, Toast.LENGTH_SHORT).show();
    }

    /**
     * Check service is running
     * http://stackoverflow.com/questions/600207/how-to-check-if-a-service-is-running-on-android
     * @param serviceClass
     * @return
     */
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void UpdateGUI(Location location, String serverResponse)
    {
        if (location != null) {
            //2014-06-28T15:07:59
            String mLastUpdateTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(location.getTime());
            String lat = Double.toString(location.getLatitude());
            String lon = Double.toString(location.getLongitude());
            String alt = String.valueOf(location.getAltitude());
            String speed = String.valueOf(location.getSpeed());
            String bearing = String.valueOf(location.getBearing());
            TextView dateText = (TextView) findViewById(R.id.text_position_date);
            dateText.setText(getResources().getString(R.string.textview_date) + mLastUpdateTime);

            TextView latText = (TextView) findViewById(R.id.text_position_lat);
            latText.setText(getResources().getString(R.string.textview_lat) + lat);

            TextView lonText = (TextView) findViewById(R.id.text_position_lon);
            lonText.setText(getResources().getString(R.string.textview_lon) + lon);

            TextView altText = (TextView) findViewById(R.id.text_position_alt);
            altText.setText(getResources().getString(R.string.textview_alt) + alt);

            TextView speedText = (TextView) findViewById(R.id.text_position_speed);
            speedText.setText(getResources().getString(R.string.textview_speed) + speed);

            TextView speedBearing = (TextView) findViewById(R.id.text_position_bearing);
            speedBearing.setText(getResources().getString(R.string.textview_bearing) + bearing);
        }

        if (serverResponse != null)
        {
            TextView httpRespText = (TextView) findViewById(R.id.text_http_response);
            httpRespText.setText(serverResponse);
        }
    }

    /**
     * This class uses the BroadcastReceiver framework to detect and handle new postition messages from
     * the service
     */
    private class NewPositionReceiver extends BroadcastReceiver
    {
        private NewPositionReceiver()
        {
            // prevents instantiation by other packages.
        }

        /**
         * This method is called by the system when a broadcast Intent is matched by this class'
         * intent filters
         * @param context
         * @param intent
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            Location location = (Location)intent.getExtras().get(Constants.POSITION_DATA);
            String serverResponse = intent.getStringExtra(Constants.SERVER_RESPONSE);
            if (location != null || serverResponse != null) {
                UpdateGUI(location,serverResponse);
            }
        }
    }

}

