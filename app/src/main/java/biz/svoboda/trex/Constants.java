package biz.svoboda.trex;

/**
 * Created by kasvo on 4.8.2015.
 */
public final class Constants {

    //http://stackoverflow.com/questions/2018263/how-do-i-enable-disable-log-levels-in-android
    private static int LOGLEVEL = 3;
    public static boolean INFO = LOGLEVEL > 1;
    public static boolean DEBUG = LOGLEVEL > 2;

    // Defines a custom Intent action
    public static final String LOCATION_BROADCAST =
            "biz.svoboda.trex.LOCATION_BROADCAST";

    // Defines the key for the status "extra" in an Intent
    public static final String POSITION_DATA =
            "biz.svoboda.trex.POSITION_DATA";

    public static final String SERVER_RESPONSE = "biz.svoboda.trex.SERVER_RESPONSE";
}