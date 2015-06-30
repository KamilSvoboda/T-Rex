package biz.svoboda.trex;

import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationListener;

import java.text.DateFormat;
import java.util.Date;

public class MainScreen extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private GoogleApiClient mGoogleApiClient;
    private Location mCurrentLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_screen);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
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
        mGoogleApiClient.connect();
    }

    /**
     * Kliknutí na tlačítko vypnutí odesílání
     * @param view
     */
    public void stopSending(View view) {
        if (mGoogleApiClient.isConnected())
        {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);

            TextView dateText = (TextView) findViewById(R.id.text_position_date);
            dateText.setText(getResources().getString(R.string.textview_date));

            TextView latText = (TextView) findViewById(R.id.text_position_lat);
            latText.setText(getResources().getString(R.string.textview_lat));

            TextView lonText = (TextView) findViewById(R.id.text_position_lon);
            lonText.setText(getResources().getString(R.string.textview_lon));

            mGoogleApiClient.disconnect();
        }
    }

    /**
     * Callback metoda po připojení ke službám polohy
     *
     * @param bundle
     */
    @Override
    public void onConnected(Bundle bundle) {
        processLocation(LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient));

        /*
        Inicializace pravidelného získávání polohy
         */
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    /**
     * Zpracuje novou pozici
     * @param location
     */
    private void processLocation(Location location)
    {
        if (location != null) {
            mCurrentLocation = location;
            String mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
            String lat = String.valueOf(mCurrentLocation.getLatitude());
            String lon = String.valueOf(mCurrentLocation.getLongitude());

            TextView dateText = (TextView) findViewById(R.id.text_position_date);
            dateText.setText(getResources().getString(R.string.textview_date) + mLastUpdateTime);

            TextView latText = (TextView) findViewById(R.id.text_position_lat);
            latText.setText(getResources().getString(R.string.textview_lat) + lat);

            TextView lonText = (TextView) findViewById(R.id.text_position_lon);
            lonText.setText(getResources().getString(R.string.textview_lon) + lon);

            /*
            Odeslani na server
             */
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String targetURL = sharedPref.getString("pref_targetUrl", "");

        }
    }

    /**
     * Called when the location has changed.
     * <p/>
     * <p> There are no restrictions on the use of the supplied Location object.
     *
     * @param location The new location, as a Location object.
     */
    @Override
    public void onLocationChanged(Location location) {
        processLocation(location);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

}
