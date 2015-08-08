package biz.svoboda.trex;

import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/**
 * BackgroundLocationService used for tracking user location in the background.
 * https://gist.github.com/blackcj/20efe2ac885c7297a676
 */
public class BackgroundLocationService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = "LocationService";

    IBinder mBinder = new LocalBinder();

    private GoogleApiClient mGoogleApiClient;

    private int NOTIFICATION = 1975; //Unique number for this notification

    private String mTargetServerURL;
    private String mDeviceIdentifier;
    private String mServerResponse;
    private String mListPrefs;
    private Integer mFrequency = 30;

    public class LocalBinder extends Binder {
        public BackgroundLocationService getServerInstance() {
            return BackgroundLocationService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        mTargetServerURL = sharedPref.getString("pref_targetUrl", "");
        mDeviceIdentifier = sharedPref.getString("pref_id","");
        mListPrefs = sharedPref.getString("pref_strategy", "PRIORITY_BALANCED_POWER_ACCURACY");
        mFrequency = Integer.valueOf(sharedPref.getString("pref_frequency", "30"));

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();

            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(this)
                            .setSmallIcon(R.drawable.trainers)
                            .setContentTitle(getResources().getString(R.string.notif_title))
                            .setContentText(getResources().getString(R.string.notif_text));

            // Creates an explicit intent for an Activity in your app
            Intent resultIntent = new Intent(this, MainScreen.class);
            resultIntent.putExtra(Constants.EXTRAS_LOCALIZATION_IS_RUNNING, true);
            // The stack builder object will contain an artificial back stack for the started Activity.
            // This ensures that navigating backward from the Activity leads out of
            // your application to the Home screen.
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            // Adds the back stack for the Intent (but not the Intent itself)
            stackBuilder.addParentStack(MainScreen.class);
            // Adds the Intent that starts the Activity to the top of the stack
            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent =
                    stackBuilder.getPendingIntent(0,
                            PendingIntent.FLAG_UPDATE_CURRENT
                    );
            mBuilder.setContentIntent(resultPendingIntent);

            startForeground(NOTIFICATION, mBuilder.build()); //spuštění služby s vyšší prioritou na popředí - http://developer.android.com/reference/android/app/Service.html

            if(Constants.INFO) Log.i(TAG, "Localization Started");
            Toast.makeText(this, R.string.localiz_started, Toast.LENGTH_SHORT).show();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        stopForeground(true); //http://developer.android.com/reference/android/app/Service.html

        if (mGoogleApiClient != null) {

            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);

            mGoogleApiClient.disconnect();
            // Destroy the current location client
            mGoogleApiClient = null;
        }
        // Display the connection status
        Toast.makeText(this, R.string.localiz_stopped, Toast.LENGTH_SHORT).show();
        if(Constants.INFO) Log.i(TAG, "Localization Stopped");
        super.onDestroy();
    }

    /*
     * Called by Location Services when the request to connect the
     * client finishes successfully. At this point, you can
     * request the current location or start periodic updates
     */
    @Override
    public void onConnected(Bundle bundle) {
        processLocation(LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient));

        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(mFrequency * 1000);
        mLocationRequest.setFastestInterval(1000);

        switch (mListPrefs)
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
        Toast.makeText(this, "Location Services suspended: " + i, Toast.LENGTH_LONG).show();
        Log.e(TAG, "Location Services suspended: " + i);
    }

    /*
     * Called by Location Services if the attempt to
     * Location Services fails.
     */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Toast.makeText(this, "Connection to Location Services fails: " + connectionResult.getErrorCode(), Toast.LENGTH_LONG).show();
        Log.e(TAG, "Connection to Location Services fails: " + connectionResult.getErrorCode());
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

    /**
     * Process new location
     *
     * @param location
     */
    private void processLocation(Location location) {
        if (location != null) {
            try {
                String mLastUpdateTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(location.getTime());//2014-06-28T15:07:59
                String lat = Double.toString(location.getLatitude());
                String lon = Double.toString(location.getLongitude());
                String alt = Double.toString(location.getAltitude());
                String speed = Float.toString(location.getSpeed());
                String bearing = Float.toString(location.getBearing());

                if (isNetworkOnline() && mTargetServerURL != null && !mTargetServerURL.isEmpty()) {
                    new NetworkTask().execute(mTargetServerURL, mDeviceIdentifier, mLastUpdateTime, lat, lon, alt, speed, bearing);

                    Intent localIntent =  new Intent(Constants.LOCATION_BROADCAST);
                    localIntent.putExtra(Constants.EXTRAS_POSITION_DATA, location);
                    localIntent.putExtra(Constants.EXTRAS_SERVER_RESPONSE, mServerResponse);

                    // Broadcasts the Intent to receivers in this app.
                    LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);

                    if(Constants.INFO) Log.i(TAG, "Position sent to server " + lat + ", " + lon);
                } else
                {
                    Toast.makeText(this, "Cannot connect to server: '" + mTargetServerURL + "'", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Cannot connect to server: '" + mTargetServerURL + "'");
                }
            } catch (Exception e) {
                Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                Log.e(TAG, "Error during location parsing:", e);
                e.printStackTrace();
            }
        }
    }

    /**
     * Checks network connectivity
     *
     * @return
     */
    public boolean isNetworkOnline() {
        ConnectivityManager cm =
                (ConnectivityManager)getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    /**
     * Private class for sending data
     */
    private class NetworkTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String response = "";
            try {
                URL url = new URL(params[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout((mFrequency - 1) * 1000);
                conn.setConnectTimeout((mFrequency - 1) * 1000);
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setDoOutput(true);

                HashMap<String, String> postDataParams = new HashMap<>();
                postDataParams.put("i", params[1]);
                postDataParams.put("t", params[2]);
                postDataParams.put("a", params[3]);
                postDataParams.put("o", params[4]);
                postDataParams.put("l", params[5]);
                postDataParams.put("s", params[6]);
                postDataParams.put("b", params[7]);

                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                writer.write(getPostDataString(postDataParams));
                writer.flush();
                writer.close();
                os.close();

                int responseCode = conn.getResponseCode();

                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    String line;
                    BufferedReader br=new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    while ((line=br.readLine()) != null) {
                        response+=line;
                    }
                }
                else {
                    throw new HttpException("HTTP response: " + responseCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during network connection:", e);
                e.printStackTrace();
                return null;
            }
            return response;
        }

        private String getPostDataString(HashMap<String, String> params) throws UnsupportedEncodingException{
            StringBuilder result = new StringBuilder();
            boolean first = true;
            for(Map.Entry<String, String> entry : params.entrySet()){
                if (first)
                    first = false;
                else
                    result.append("&");

                result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                result.append("=");
                result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
            return result.toString();
        }
        /**
         * Response processing
         * @param result
         */
        @Override
        protected void onPostExecute(String result) {
            mServerResponse = result;
        }
    }
}