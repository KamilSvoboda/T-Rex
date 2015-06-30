package biz.svoboda.trex;

import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainScreen extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    /*
    Klíč pro uložení stavu activity
     */
    private static final String LOCALIZATION_RUNNING_KEY = "LOCALIZATION_RUNNING";

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
        /*
        Pokud existuje nějaký předchozí stav (např. po otočení obrazovky)
         */
        if (savedInstanceState != null) {
            if (savedInstanceState.keySet().contains(LOCALIZATION_RUNNING_KEY)) {
                if (savedInstanceState.getBoolean(LOCALIZATION_RUNNING_KEY)) //pokud predtim bezela lokalizace
                {
                    mGoogleApiClient.connect();
                }
            }
        }
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
     * Uložení stavu aplikace, při otočení obrazovky, apod.
     * @see "https://developer.android.com/training/location/receive-location-updates.html#connect"
     * @param savedInstanceState
     */
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(LOCALIZATION_RUNNING_KEY, mGoogleApiClient.isConnected());
        super.onSaveInstanceState(savedInstanceState);
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

            TextView respText = (TextView) findViewById(R.id.text_http_response);
            respText.setText(null);

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

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        Integer freq = Integer.valueOf(sharedPref.getString("pref_frequency","30"));

        /*
        Inicializace pravidelného získávání polohy
         */
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(freq * 1000);
        mLocationRequest.setFastestInterval(1000);

        String listPrefs = sharedPref.getString("pref_strategy", "PRIORITY_BALANCED_POWER_ACCURACY");
        switch (listPrefs)
        {
            case "PRIORITY_HIGH_ACCURACY":
                mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                break;
            case "PRIORITY_LOW_POWER":
                mLocationRequest.setPriority(LocationRequest.PRIORITY_LOW_POWER);
                break;
            case "PRIORITY_NO_POWER":
                mLocationRequest.setPriority(LocationRequest.PRIORITY_NO_POWER);
                break;
            default:
                mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
                break;
        }

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
            //2014-06-28T15:07:59
            String mLastUpdateTime =  new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(mCurrentLocation.getTime());
            String lat = String.valueOf(mCurrentLocation.getLatitude());
            String lon = String.valueOf(mCurrentLocation.getLongitude());
            String alt = String.valueOf(mCurrentLocation.getAltitude());
            String speed = String.valueOf(mCurrentLocation.getSpeed());
            String bearing = String.valueOf(mCurrentLocation.getBearing());

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

            /*
            Odeslani na server
             */
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String targetURL = sharedPref.getString("pref_targetUrl", "");

            new NetworkTask().execute(targetURL, mLastUpdateTime, lat, lon, alt, speed, bearing);
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

    /**
     * Privátní třída, která asynchronně posílá POST data na URL
     */
    private class NetworkTask extends AsyncTask<String, Void, HttpResponse> {
        @Override
        protected HttpResponse doInBackground(String... params) {
            String targetURL = params[0];
            HttpPost httppost = new HttpPost(targetURL);
            AndroidHttpClient client = AndroidHttpClient.newInstance("Android");

            try {
                // Add data
                List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(6);
                nameValuePairs.add(new BasicNameValuePair("time", params[1]));
                nameValuePairs.add(new BasicNameValuePair("lat", params[2]));
                nameValuePairs.add(new BasicNameValuePair("lon", params[3]));
                nameValuePairs.add(new BasicNameValuePair("alt", params[4]));
                nameValuePairs.add(new BasicNameValuePair("speed", params[5]));
                nameValuePairs.add(new BasicNameValuePair("bearing", params[6]));
                httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

                return client.execute(httppost);

            } catch (IOException e) {
                e.printStackTrace();
                return null;
            } finally {
                client.close();
            }
        }

        /**
         * Zpracování odpovědi
         * @param result
         */
        @Override
        protected void onPostExecute(HttpResponse result) {
            // Convert the response into a String
            HttpEntity resEntity = result.getEntity();
            // Write the response to a textview
            if (resEntity != null) {
                String data = null;
                try {
                    data = EntityUtils.toString(resEntity);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                TextView httpRespText = (TextView) findViewById(R.id.text_http_response);
                httpRespText.setText(data);
            }
        }
    }
}

