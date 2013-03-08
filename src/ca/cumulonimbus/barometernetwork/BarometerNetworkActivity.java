package ca.cumulonimbus.barometernetwork;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.ActionBar;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

public class BarometerNetworkActivity extends MapActivity implements SensorEventListener {
	
	double mLatitude = 0.0;
	double mLongitude = 0.0;
	double mReading = 0.0;
	double mTimeOfReading = 0.0;
	float mReadingAccuracy = 0.0f;
	float mLocationAccuracy = 0.0f;
	SensorManager sm;
	
	private String mAppDir = "";
    boolean mExternalStorageAvailable = false;
    boolean mExternalStorageWriteable = false;
    
    private String android_id;
    
    private String localHistoryFile = "recent.txt";
    
    private boolean debugMode = true;
    
    private String mTendency = "";
    
    public String statusText = "";
    private final Handler statusHandler = new Handler();
    private final Handler mapHandler = new Handler();
	
    private String serverURL = PressureNETConfiguration.SERVER_URL;
    
    private int mapFontSize = 18;
    
	public static final String PREFS_NAME = "ca.cumulonimbus.barometernetwork_preferences";
	
	private String mUpdateServerFrequency;
	private boolean mUpdateServerAutomatically;
	Intent serviceIntent;
	
	Unit mUnit = null;
	
	private DBAdapter dbAdapter;

	private boolean barometerDetected = true;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        migratePreferences(); 
        startLog();
        setUpDatabase();
        setUpBarometer();
        getStoredPreferences();
        getLocationInformation();
        setId();
        setUpFiles();
        setUpMap();
        showWelcomeActivity();
        startSendingData();
        setUpActionBar();
        findTendency();
    }
    
    public void findTendency() {
    	try {
			dbAdapter = new DBAdapter(getApplicationContext());
			dbAdapter.open();
			ArrayList<BarometerReading> recents = new ArrayList<BarometerReading>();
			recents = dbAdapter.fetchRecentReadings(1); // the last little while (in hours)
			// String tendency = ScienceHandler.findTendency(recents);
			ScienceHandler science = new ScienceHandler(mAppDir);
			String tendency = science.findApproximateTendency(recents);
			mTendency = tendency;
			if (mTendency.equals("Unknown")) {
				mTendency = "";
			}
			dbAdapter.close();
		} catch(Exception e) {
			System.out.println("tendency error " + e.getMessage());
		}
    }
    
    public void startLog() {
    	// Log
    	String version = "";
		PackageInfo pInfo;
		try {
			pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			version = pInfo.versionName;
		} catch (NameNotFoundException e) { }
		
    	
    	log("oncreate main activity v: " + version);
    }
    
    // Get the phone ID and hash it
	public String getID() {
    	try {
    		MessageDigest md = MessageDigest.getInstance("MD5");
    		
    		String actual_id = Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID);
    		byte[] bytes = actual_id.getBytes();
    		byte[] digest = md.digest(bytes);
    		StringBuffer hexString = new StringBuffer();
    		for(int i = 0; i< digest.length; i++) {
    			hexString.append(Integer.toHexString(0xFF & digest[i]));
    		}
    		return hexString.toString();
    	} catch(Exception e) {
    		return "--";
    	}
	}
	
	/**
	 * Tell the server the user wants to delete his/her data
	 *
	 */
    private class SendDeleteRequest extends AsyncTask<String, String, String> {
    	String responseText= "";
    	
    	@Override
		protected String doInBackground(String... arg0) {
    		SecureHttpClient client = new SecureHttpClient(getApplicationContext());
        	HttpPost httppost = new HttpPost(serverURL);
        	String id = getID();
        	try {
        		List<NameValuePair> nvp = new ArrayList<NameValuePair>();
        		nvp.add(new BasicNameValuePair("download","full_delete_request"));
        		nvp.add(new BasicNameValuePair("userid",id));
        
        		httppost.setEntity(new UrlEncodedFormEntity(nvp));
        		HttpResponse response = client.execute(httppost);
        		HttpEntity responseEntity = response.getEntity();
        		
        		BufferedReader r = new BufferedReader(new InputStreamReader(responseEntity.getContent()));
        		
        		StringBuilder total = new StringBuilder();
        		
        		String line;
        		if(r!=null) {
    	    		while((line = r.readLine()) != null) {
    	    			total.append(line);
    	    		}
    	    		responseText = total.toString();
        		}
        		return responseText;
        		
        	}catch(Exception e) {
        		
        	}
	    	return responseText;
		}
		protected void onPostExecute(String result) {
			
			Toast.makeText(getApplicationContext(), "Data deleted.", Toast.LENGTH_SHORT).show();
		}
    }
	
	public void deleteUserData() {
		// show a dialog, listen for its response.
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(getResources().getString(R.string.deleteWarning)).setPositiveButton("Continue", dialogDeleteClickListener)
		    .setNegativeButton("Cancel", dialogDeleteClickListener).show();
	}
    
	DialogInterface.OnClickListener dialogDeleteClickListener = new DialogInterface.OnClickListener() {
	    @Override
	    public void onClick(DialogInterface dialog, int which) {
	        switch (which){
	        case DialogInterface.BUTTON_POSITIVE:
	        	new SendDeleteRequest().execute("");
	        	break;

	        case DialogInterface.BUTTON_NEGATIVE:
	            break;
	        }
	    }
	};
    
    /**
     * Migrate from the old 'pressureNETprefs' system to the new one. 3.0 -> 3.0.1
     */
    private void migratePreferences() {
    	// Log
    	String version = "";
		PackageInfo pInfo;
		try {
			pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			version = pInfo.versionName;
		} catch (NameNotFoundException e) { }
		
    	log("migrate prefs to " + version);
		
		// Migrate old preferences
		SharedPreferences oldSettings = getSharedPreferences("pressureNETPrefs", 0);
	    if(oldSettings.contains("autoupdate")) {
			// Load
			boolean autoUpdate = oldSettings.getBoolean("autoupdate", true);
			String unit = oldSettings.getString("units", "Millibars (mbar)" );
		    String autoFrequency = oldSettings.getString("autofrequency", "10 minutes");
		    String sharing = oldSettings.getString("sharing_preference", "Us, Researchers and Forecasters");
		    int firstRun = oldSettings.getInt("first_run", 1);
		    
		    firstRun++;
		    
		    // Store
		    SharedPreferences newSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		    SharedPreferences.Editor editor = newSharedPreferences.edit();
		    editor.putBoolean("autoupdate", autoUpdate);
		    editor.putString("units", unit);
		    editor.putString("autoFrequency", autoFrequency);
		    editor.putString("sharing_preference", sharing);
		    editor.putInt("first_run", firstRun);
		    editor.commit();
	    }
    }

    /**
     * Welcome the user to pressureNET and explain the 
     * privacy options
     */
    public void showWelcomeActivity() {
    	// has this been shown yet?
    	if(barometerDetected) {
	    	SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
	    	int firstRun = settings.getInt("first_run", 0);
	    	if(firstRun==0) {
	    		Intent intent = new Intent(this, ca.cumulonimbus.barometernetwork.WelcomeActivity.class);
	    		startActivityForResult(intent, 0);
	    		firstRun++;
	    		SharedPreferences.Editor editor = settings.edit();
	    		editor.putInt("first_run", firstRun);
	  	      	editor.commit();
	    	}
    	}
    }

    /**
     * Some devices have barometers, other's don't. Fix up the UI 
     * a bit so that most useful elements show for the right users
     */
    public void cleanUI(Menu menu) {
    	if(barometerDetected) {
    		// keep the UI as-is. default assumes barometer exists :) 
    		// ensure the right items are always visible, though, in case of detection error
    	} else {
    		// hide some menu items that are barometer-specific
    		menu.removeItem(R.id.menu_my_info);
    		menu.removeItem(R.id.menu_submit_reading);
    		menu.removeItem(R.id.menu_log_viewer);
    		menu.removeItem(R.id.menu_delete_data);
    	}
    	
    	if (!debugMode) {
    		// hide menu item
    		menu.removeItem(R.id.send_debug_log);
    	}
    }
    
    public void setUpDatabase() {
    	try {
    		dbAdapter = new DBAdapter(this);
    		dbAdapter.open();
    	} catch(Exception e) {
    		Toast.makeText(getApplicationContext(), "Unable to open local database. No problem (no local history.)", Toast.LENGTH_LONG).show();
    	}
    }
    

	// Add a new barometer reading to the local database
	// Having this allows user to view trends but keeps 
    // the data offline (no server visibility to the data.)
	public void addToLocalDatabase(BarometerReading br) {
		try {
			dbAdapter.addReading(br.getReading(), br.getLatitude(), br.getLongitude(), br.getTime(), br.getSharingPrivacy(), br.getLocationAccuracy(), br.getReadingAccuracy());
		} catch(RuntimeException re) {
			// :(
		}
	}
	
	public double convertfromMillibarTo(double read) {
		String abbrev = mUnit.getAbbreviation();
		if(abbrev.contains("mbar")) {
			// No change. reading comes to us in mbar.
			return read;
		} else if(abbrev.contains("hPa")) {
			// mbar = hpa.
			return read;
		} else if(abbrev.contains("kPa")) {
			return read * 0.1;
		} else if(abbrev.contains("atm")) {
			return read * 0.000986923;
		} else if(abbrev.contains("mmHg")) {
			return read * 0.75006;
		} else if(abbrev.contains("inHg")) {
			return read * 0.02961;
		}
		return 0.0;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.settings, menu);
	    return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		cleanUI(menu);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
    	if(item.getItemId()==R.id.menu_settings) {
    		Intent i = new Intent(this, PreferencesActivity.class);
    		i.putExtra("hasBarometer", barometerDetected);
    		startActivityForResult(i, 1);
    	} else if(item.getItemId() == R.id.menu_check_trend) { 
    		log("menu check trend " + mLatitude + " " + mLongitude);
    		ScienceHandler science = new ScienceHandler(mAppDir, getApplicationContext());
    		science.checkForTrends(getApplicationContext(), dbAdapter, mLatitude, mLongitude, true);
    		
    	} else if(item.getItemId()==R.id.menu_my_info) {
    		Intent intent = new Intent(getApplication(), SingleUserChartActivity.class);
    		intent.putExtra("userid", android_id);
    		intent.putExtra("selfstats", "yes");
    		intent.putExtra("appdir", mAppDir);
    		startActivityForResult(intent, 0);
    	} else if(item.getItemId()==R.id.menu_submit_reading) {
			getLocationInformation();
			submitDataToServer();
    	} else if(item.getItemId()==R.id.menu_log_viewer) {
    		showRecentHistory();
    	} else if(item.getItemId()==R.id.send_debug_log) {
    		// send logs to Cumulonimbus
    		emailLogs();
    	}  else if(item.getItemId()==R.id.menu_current_conditions) {
    		// Current conditions
    		Intent intent = new Intent(getApplicationContext(), CurrentConditionsActivity.class);
    		try {
        		if(mLatitude == 0.0) {
        			LocationManager lm = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
            		Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            		double latitude = loc.getLatitude();
            		double longitude = loc.getLongitude();
            		mLatitude = latitude;
            		mLongitude = longitude;
            		mLocationAccuracy = loc.getAccuracy();
        		}
    			
        		intent.putExtra("appdir", mAppDir);
        		intent.putExtra("latitude", mLatitude);
        		intent.putExtra("longitude", mLongitude);
        		log("starting condition " + mLatitude + " , " + mLongitude);
        		startActivity(intent);
    		} catch(NullPointerException e) {
    			Toast.makeText(getApplicationContext(), "No location found. Please try again soon.", Toast.LENGTH_SHORT).show();
    		}
    	} else if(item.getItemId()==R.id.menu_load_data_vis) {
    		// Load up pressurenet.cumulonimbus.ca with the user's location
    		// and current timeframe
        	try {
        		LocationManager lm = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        		Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        		double latitude = loc.getLatitude();
        		double longitude = loc.getLongitude();
        		Intent intent = new Intent(getApplicationContext(),PNDVActivity.class);
        		intent.putExtra("latitude", latitude);
        		intent.putExtra("longitude", longitude);
        		startActivity(intent);
        	} catch (NullPointerException npe) {
        		// Android 4.2 NPEs here. Try again but still be careful
        		try {
	        		Intent intent = new Intent(getApplicationContext(),PNDVActivity.class);
	        		intent.putExtra("latitude", mLatitude);
	        		intent.putExtra("longitude", mLongitude);
	        		startActivity(intent);        		
        		} catch(Exception e) {
        			Intent intent = new Intent(getApplicationContext(),PNDVActivity.class);
	        		startActivity(intent);	
        		}
        	}
    	} else if (item.getItemId() == R.id.menu_delete_data) {
    		deleteUserData();
    	}
    	
    		/* else if(item.getItemId()==R.id.menu_about) {
    	}
    		Toast.makeText(getApplicationContext(), "About pressureNET and Cumulonimbus", Toast.LENGTH_SHORT).show();
    		
    	}*/ /*else if(item.getItemId()==R.id.menu_reload) {
    		loadAndShowData();
    	}/* 
    		// Show a graph of local data points. Local is defined by
    		// visible map region. Intended for viewing tendencies 
    		else if(item.getItemId()==R.id.menu_showLocalGraph) {
    		Intent intent = new Intent(getApplication(), LocalChartActivity.class);
    		intent.putExtra("appdir", mAppDir);
    		intent.putExtra("regioninfo","");
    		startActivityForResult(intent, 0);
    	}*/
    	
		return super.onOptionsItemSelected(item);
	}
	
    // Assemble a list of CurrentConditions. This is the opposite of function currentConditionToWeb in the servlet.
	public ArrayList<CurrentCondition> csvToCurrentConditions(String[] conditions) {
    	ArrayList<CurrentCondition> conditionsList = new ArrayList<CurrentCondition>();
    	for(int i = 0; i<conditions.length; i++) {
    		try {
	    		String[] values = conditions[i].split("\\|");
	    		CurrentCondition cc = new CurrentCondition();
	    		cc.setLatitude(Double.parseDouble(values[0]));
	    		cc.setLongitude(Double.parseDouble(values[1]));
	    		cc.setGeneral_condition(values[2]);
	    		cc.setTime(Double.parseDouble(values[3]));
	    		cc.setTzoffset((Integer.parseInt(values[4])));
	    		cc.setWindy(values[5]);
	    		cc.setPrecipitation_type(values[6]);
	    		cc.setPrecipitation_amount(Double.parseDouble(values[7]));
	    		cc.setThunderstorm_intensity(values[8]);
	    		cc.setCloud_type(values[9]);
	    		cc.setFog_thickness(values[10]);
	    		cc.setUser_id(values[11]);
	    		conditionsList.add(cc);
    		} catch(NumberFormatException nfe) {
    			// Likely, tomcat returned an error.
    			log("Server error? " + nfe.getMessage());
    		}
    	}
    	return conditionsList;
    }
	

	/**
	 * 
	 * Email debug logs to Cumulonimbus.
	 * 
	 */
	public void emailLogs() {
		try {
			String strFile = mAppDir + "/log.txt";

			File file = new File(strFile);
			if (!file.exists())
				file.mkdirs();
			
			final Intent emailIntent = new Intent(
					android.content.Intent.ACTION_SEND);

			PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			String version = pInfo.versionName;
			
			String address = "software@cumulonimbus.ca";
			String subject = "pressureNET " + version  + " Debug Log"; 
			String emailtext = "Debug log sent " + (new Date()).toLocaleString();

			emailIntent.setType("plain/text");

			emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL,
					new String[] { address });

			emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
					subject);

			emailIntent.putExtra(Intent.EXTRA_STREAM,
					Uri.parse("file://" + strFile));

			emailIntent.putExtra(android.content.Intent.EXTRA_TEXT,
					emailtext);

			startActivityForResult(Intent.createChooser(emailIntent,
					"Send mail..."),105);


		} catch (Throwable t) {
			Toast.makeText(this, "Request failed: " + t.toString(),
					Toast.LENGTH_LONG).show();
		}
	}
	
	private String fullUnitToRealAbbrev(String unit) {
		if(unit.contains("mbar")) {
			return "mbar";
		} else if(unit.contains("mmHg")) {
			return "mmHg";
		} else if(unit.contains("inHg")) {
			return "inHg";
		} else if(unit.contains("hPa")) {
			return "hPa";
		} else if(unit.contains("kPa")) {
			return "kPa";
		} else if(unit.contains("atm")) {
			return "atm";
		} else {
			return "mbar";
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == 105) {
			// Clear the log
			String strFile = mAppDir + "/log.txt";
			File file = new File(strFile);
			if (file.exists())
				file.delete();
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	// Give a quick overview of recent 
	// submissions
	public void showRecentHistory() {
		String log = "";
		ArrayList<BarometerReading> recents = new ArrayList<BarometerReading>();
		try {
			dbAdapter.open();
			recents = dbAdapter.fetchRecentReadings(24); // the last few hours
			dbAdapter.close();
			for (BarometerReading r : recents) {
				String d = new Date((long)r.getTime()).toLocaleString();
				DecimalFormat df = new DecimalFormat("####.00");
				String unit = fullUnitToRealAbbrev(mUnit.getAbbreviation());
				double reading = convertfromMillibarTo(r.getReading());
				log += d + ": " + df.format(reading) + " " + unit + "\n";
			}
			Intent intent = new Intent(this, LogViewerActivity.class);
			intent.putExtra("log", log);
			startActivity(intent);
		} catch(Exception e) {
			
		}
	}

    
    public void setUpActionBar() {
    	// TODO: Compatible Action Bar
    	ActionBar bar = getActionBar();
    	bar.setDisplayUseLogoEnabled(true);
    	bar.setTitle("");

    }
    
    @Override
	protected void onRestart() {
		super.onRestart();
		setUpBarometer();
		getStoredPreferences();
		getLocationInformation();
	}
    
	// Periodically send barometer readings if allowed by Preferences
    // pref check done inside the service; start the service even if no 
    // data is sent to allow the widget to use the service
    // probably should rename away from startSendingData
    private void startSendingData() {
    	log("start sending data");
		try {
			// only start the service if it's not already running
			if(!isPressureNETServiceRunning()) {
				serviceIntent = new Intent(this, SubmitReadingService.class);
				//serviceIntent.putExtra("appdir", mAppDir);
				startService(serviceIntent);
			} else {
				log("not starting service, already running");
			}
			
		} catch(Exception e) {
			log(e.getMessage() + "");
		}

    }
    
    // Get the user preferences
    private void getStoredPreferences() {
    	try {
    		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
    		mUpdateServerAutomatically = settings.getBoolean("autoupdate", true);
    		mUpdateServerFrequency = settings.getString("autofrequency", "10 minutes");
    		//log("shared prefs: " + mUpdateServerFrequency + " " + mUpdateServerAutomatically);
    		
    		// Units
    		String abbrev = settings.getString("units", "mbar"); 
    		mUnit = new Unit(abbrev);
    		//log("abbrev: "  + abbrev);
    		
    	} catch(Exception e) {
    		log(e.getMessage() + "");
    		mUpdateServerAutomatically = false;
    	}
    }
    
    // Set a unique identifier so that updates from the same user are 
    // seen as updates and not new data. MD5 to minimize privacy problems. (?)
    public void setId() {
    	try {
    		MessageDigest md = MessageDigest.getInstance("MD5");
    		
    		String actual_id = Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID);
    		byte[] bytes = actual_id.getBytes();
    		byte[] digest = md.digest(bytes);
    		StringBuffer hexString = new StringBuffer();
    		for(int i = 0; i< digest.length; i++) {
    			hexString.append(Integer.toHexString(0xFF & digest[i]));
    		}
    		android_id = hexString.toString();
    	} catch(Exception e) {
    		log(e.getMessage() + "");
    	}
    }
    
    // Used to write a log to SD card. Not used unless logging enabled.
    public void setUpFiles() {
    	try {
	    	File homeDirectory = getExternalFilesDir(null);
	    	if(homeDirectory!=null) {
	    		mAppDir = homeDirectory.getAbsolutePath();
	    		
	    	}
    	} catch (Exception e) {
    		log(e.getMessage());
    	}
    }
    
    // Start getting barometer readings.
    public void setUpBarometer() {
    	log("set up barometer");
    	try {
	    	sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
	    	Sensor bar = sm.getDefaultSensor(Sensor.TYPE_PRESSURE);
	    	
	    	if(bar!=null) {
	        	boolean running = sm.registerListener(this, bar, SensorManager.SENSOR_DELAY_NORMAL);
	        	//log(running + "");
	        	barometerDetected = true;
	    	} else {
	    		barometerDetected = false;
	    		//Toast.makeText(getApplicationContext(), "No barometer detected.", Toast.LENGTH_SHORT).show();
	    	}
	    	invalidateOptionsMenu(); // ensure right right menus are showing, given barometer detection status
    	} catch(Exception e) {
    		log(e.getMessage() + "");
    	}
    }
    
    // Zoom into the user's location, add pinch zoom controls
    public void setUpMap() {
    	log("setting up map");
        // Add zoom
    	BarometerMapView mapView = (BarometerMapView) findViewById(R.id.mapview);
        mapView.setBuiltInZoomControls(true);
        
        // Set default coordinates (centered around the user's location)

        try {
        	MapController mc = mapView.getController();
        	LocationManager lm = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        	Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        	mc.setZoom(8);
        	if(loc.getLatitude()!=0) {
        		//log("setting center " + loc.getLatitude() + " " + loc.getLongitude());
        		mc.animateTo(new GeoPoint((int)(loc.getLatitude()*1E6), (int)(loc.getLongitude() * 1E6)));
        	} else {
        		log("no known last location");
        	}

	        
	        mapView.invalidate();
	        mapView.refreshDrawableState();
        } catch(Exception e) {
        	log(e.getMessage() + "");
        }
       
    }
    
    LocationManager mLocationManager;
    LocationListener locationListener;
    
    // Get the user's location from the location service, preferably GPS.
    public void getLocationInformation() {
    	log("getting location information");
    	// get the location
    	// Acquire a reference to the system Location Manager
    	mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

    	// Define a listener that responds to location updates
    	locationListener = new LocationListener() {
    	    public void onLocationChanged(Location location) {
    	        // Called when a new location is found by the network location provider.
    	    	try {
	    	    	boolean first = false;
	    	    	if(mLatitude==0.0) {
	    	    		log("latitude is 0");
	    	    		first = true;
	    	    	}
	    	    	double latitude = location.getLatitude();
	    	    	double longitude = location.getLongitude();
	    	    	mLatitude = latitude;
	    	    	mLongitude = longitude;
	    	    	mLocationAccuracy = location.getAccuracy();
	    	    	if(first) {
	    	    		//log("first location start");
	    	    		setUpMap();
	    	    		loadAndShowData();
	    	    		//log("end");
	    	    	}
    	    	} catch(Exception e) {
    	    		log("On Location change failed.");
    	    	}
    	    }
    	    
    	    

    	    public void onStatusChanged(String provider, int status, Bundle extras) {}

    	    public void onProviderEnabled(String provider) {}

    	    public void onProviderDisabled(String provider) {}
    	  };

    	// Register the listener with the Location Manager to receive location updates
    	try {
    		mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
    		
    	} catch(Exception e) {
    		log(e.getMessage() + "");
    	}

    }

    // Custom map overlays for barometer readings
    public class MapOverlay extends ItemizedOverlay<OverlayItem> {

    	private ArrayList<OverlayItem> mOverlays = new ArrayList<OverlayItem>();
    	Context mContext;
    	private int mTextSize;

    	@Override
    	protected boolean onTap(int index) {
    		// Open the chart view
    		OverlayItem item = mOverlays.get(index);
    		String snippet = item.getSnippet();
    		if(!snippet.equals("singleton_condition")) {
        		Intent intent = new Intent(getApplication(), SingleUserChartActivity.class);
        		intent.putExtra("userid", snippet);
        		intent.putExtra("appdir", mAppDir);
        		startActivityForResult(intent, 0);    			
    		}

    		return true;
    	}

    	public MapOverlay(Drawable defaultMarker, Context context, int textSize) {
    		super(boundCenterBottom(defaultMarker));
    		mContext = context;
    		mTextSize = textSize;
    	}
    	
    	public MapOverlay(Drawable defaultMarker, Context context) {
    		super(defaultMarker);
    		mContext = context;
    	}
    	
		public MapOverlay(Drawable defaultMarker) {
			super(boundCenterBottom(defaultMarker));
		}

		public void addOverlay(OverlayItem overlay) {
			mOverlays.add(overlay);
			populate();
		}
		
	    public void removeOverlay(OverlayItem overlay)
	    {
	        mOverlays.remove(overlay);
	        populate();
	    }
		
		@Override
		protected OverlayItem createItem(int i) {
			return mOverlays.get(i);
		}
		@Override
		public int size() {
			return mOverlays.size();
		}
		
		// Draw all the overlay data points onto the map. Include an icon as well as 
		@Override
		public void draw(android.graphics.Canvas canvas, MapView mapView, boolean shadow)
	    {
			shadow = false;
	        super.draw(canvas, mapView, shadow);

	        if (shadow == false)
	        {
	            //cycle through all overlays
	            for (int index = 0; index < mOverlays.size(); index++)
	            {
	            	try {
		                OverlayItem item = mOverlays.get(index);
	
		                // Converts lat/lng-Point to coordinates on the screen
		                GeoPoint point = item.getPoint();
		                Point ptScreenCoord = new Point() ;
		                mapView.getProjection().toPixels(point, ptScreenCoord);
	
		                //Paint
		                Paint paint = new Paint();
		                paint.setTextAlign(Paint.Align.CENTER);
		                paint.setTextSize(mTextSize);
		                paint.setShadowLayer(15, 5, 5, 0);
		                paint.setARGB(255, 0, 0, 0); // alpha, r, g, b (Black, semi see-through)
		                paint.setAntiAlias(true);
		                
		                //String toPrint = item.getTitle().substring(0, item.getTitle().length() - 5);
		                String toPrint = item.getTitle().split(" ")[0];
		                Double value = Double.parseDouble(toPrint);
		                DecimalFormat df = new DecimalFormat("####.00");
		                toPrint = df.format(value);
		                
		                //show text to the right of the icon
		                float textWidth = paint.measureText(toPrint);
		                Paint bgPaint = new Paint();
		                bgPaint.setColor(Color.WHITE);
		                
		                Rect rect = new Rect((int)(ptScreenCoord.x - (textWidth / 2) - 2), ptScreenCoord.y, (int)(ptScreenCoord.x + (textWidth / 2) + 2), ptScreenCoord.y + mTextSize + 5);
		                canvas.drawRoundRect(new RectF(rect), 6, 6, bgPaint);
		                canvas.drawText(toPrint, ptScreenCoord.x, ptScreenCoord.y+mTextSize, paint);
	            	} catch(Exception e) {
	            		log(e.getMessage() + "");
	            	}
	            }
	        }
	    }
    }
    
    // Assume that matching latitude and longitude can only be you. 
    public boolean brIsMe(BarometerReading br) {
    	return ((br.getAndroidId().equals(android_id)));
    }
    
    public MapOverlay getCurrentConditionOverlay(CurrentCondition condition, Drawable drawable) {
    	MapOverlay overlay = new MapOverlay(drawable, this, mapFontSize);
		
		Drawable weatherBackgroundDrawable = resizeDrawable(this.getResources().getDrawable(R.drawable.bg_wea_square));
		
		if(condition.getGeneral_condition().equals(getString(R.string.sunny))) {
			Drawable sunDrawable = this.getResources().getDrawable(R.drawable.ic_col_sun);
			Drawable[] layers = {weatherBackgroundDrawable, resizeDrawable(sunDrawable)};
			LayerDrawable layerDrawable = new LayerDrawable(layers);
			overlay = new MapOverlay(layerDrawable, this, mapFontSize);
		} else if(condition.getGeneral_condition().equals(getString(R.string.precipitation))) {
			if(condition.getPrecipitation_type().equals(getString(R.string.rain))) {
				if(condition.getPrecipitation_amount() == 0.0) {
					Drawable rainDrawable = this.getResources().getDrawable(R.drawable.ic_col_rain1);
					Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable(rainDrawable)};
					LayerDrawable layerDrawable = new LayerDrawable(layers);
					overlay = new MapOverlay(layerDrawable, this, mapFontSize);
				} else if(condition.getPrecipitation_amount() == 1.0) {
					Drawable rainDrawable = this.getResources().getDrawable(R.drawable.ic_col_rain2);
					Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable(rainDrawable)};
					LayerDrawable layerDrawable = new LayerDrawable(layers);
					overlay = new MapOverlay(layerDrawable, this, mapFontSize);
				} else if(condition.getPrecipitation_amount() == 2.0) {
					Drawable rainDrawable = this.getResources().getDrawable(R.drawable.ic_col_rain3);
					Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable(rainDrawable)};
					LayerDrawable layerDrawable = new LayerDrawable(layers);
					overlay = new MapOverlay(layerDrawable, this, mapFontSize);
				}
			} else if(condition.getPrecipitation_type().equals(getString(R.string.snow))) {
				if(condition.getPrecipitation_amount() == 0.0) {
					Drawable snowDrawable = this.getResources().getDrawable(R.drawable.ic_col_snow1);
					Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable(snowDrawable)};
					LayerDrawable layerDrawable = new LayerDrawable(layers);
					overlay = new MapOverlay(layerDrawable, this, mapFontSize);
				} else if(condition.getPrecipitation_amount() == 1.0) {
					Drawable snowDrawable = this.getResources().getDrawable(R.drawable.ic_col_snow2);
					Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable(snowDrawable)};
					LayerDrawable layerDrawable = new LayerDrawable(layers);
					overlay = new MapOverlay(layerDrawable, this, mapFontSize);
				} else if(condition.getPrecipitation_amount() == 2.0) {
					Drawable snowDrawable = this.getResources().getDrawable(R.drawable.ic_col_snow3);
					Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable(snowDrawable)};
					LayerDrawable layerDrawable = new LayerDrawable(layers);
					overlay = new MapOverlay(layerDrawable, this, mapFontSize);
				}
			} else if(condition.getPrecipitation_type().equals(getString(R.string.hail))) {
				if(condition.getPrecipitation_amount() == 0.0) { 
					Drawable hailDrawable = this.getResources().getDrawable(R.drawable.ic_col_hail1);
					Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable(hailDrawable)};
					LayerDrawable layerDrawable = new LayerDrawable(layers);
					overlay = new MapOverlay(layerDrawable, this, mapFontSize);
				} else if(condition.getPrecipitation_amount() == 1.0) { 
					Drawable hailDrawable = this.getResources().getDrawable(R.drawable.ic_col_hail2);
					Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable(hailDrawable)};
					LayerDrawable layerDrawable = new LayerDrawable(layers);
					overlay = new MapOverlay(layerDrawable, this, mapFontSize);
				} else if(condition.getPrecipitation_amount() == 2.0) { 
					Drawable hailDrawable = this.getResources().getDrawable(R.drawable.ic_col_hail3);
					Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable( hailDrawable)};
					LayerDrawable layerDrawable = new LayerDrawable(layers);
					overlay = new MapOverlay(layerDrawable, this, mapFontSize);
				} 
			}
		} else if(condition.getGeneral_condition().equals(getString(R.string.cloudy))) {
			if(condition.getCloud_type().equals(getString(R.string.partly_cloudy))) {
				Drawable cloudDrawable = this.getResources().getDrawable(R.drawable.ic_col_cloudy1);
				Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable( cloudDrawable)};
				LayerDrawable layerDrawable = new LayerDrawable(layers);
				overlay = new MapOverlay(layerDrawable, this, mapFontSize);
			} else if(condition.getCloud_type().equals(getString(R.string.mostly_cloudy))) {
				Drawable cloudDrawable = this.getResources().getDrawable(R.drawable.ic_col_cloudy2);
				Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable( cloudDrawable)};
				LayerDrawable layerDrawable = new LayerDrawable(layers);
				overlay = new MapOverlay(layerDrawable, this, mapFontSize);
			} else if(condition.getCloud_type().equals(getString(R.string.very_cloudy))) {
				Drawable cloudDrawable = this.getResources().getDrawable(R.drawable.ic_col_cloudy);
				Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable( cloudDrawable)};
				LayerDrawable layerDrawable = new LayerDrawable(layers);
				overlay = new MapOverlay(layerDrawable, this, mapFontSize);
			} else {
				Drawable cloudDrawable = this.getResources().getDrawable(R.drawable.ic_col_cloudy);
				Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable( cloudDrawable)};
				LayerDrawable layerDrawable = new LayerDrawable(layers);
				overlay = new MapOverlay(layerDrawable, this, mapFontSize);
			}
		}  else if(condition.getGeneral_condition().equals(getString(R.string.foggy))) {
			if(condition.getFog_thickness().equals(getString(R.string.light_fog))) {
				Drawable fogDrawable = this.getResources().getDrawable(R.drawable.ic_col_fog1);
				Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable( fogDrawable)};
				LayerDrawable layerDrawable = new LayerDrawable(layers);
				overlay = new MapOverlay(layerDrawable, this, mapFontSize);
			} else if(condition.getFog_thickness().equals(getString(R.string.moderate_fog))) {
				Drawable fogDrawable = this.getResources().getDrawable(R.drawable.ic_col_fog2);
				Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable( fogDrawable)};
				LayerDrawable layerDrawable = new LayerDrawable(layers);
				overlay = new MapOverlay(layerDrawable, this, mapFontSize);
			} else if(condition.getFog_thickness().equals(getString(R.string.heavy_fog))) {
				Drawable fogDrawable = this.getResources().getDrawable(R.drawable.ic_col_fog3);
				Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable( fogDrawable)};
				LayerDrawable layerDrawable = new LayerDrawable(layers);
				overlay = new MapOverlay(layerDrawable, this, mapFontSize);
			} else {
				Drawable fogDrawable = this.getResources().getDrawable(R.drawable.ic_col_fog2);
				Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable( fogDrawable)};
				LayerDrawable layerDrawable = new LayerDrawable(layers);
				overlay = new MapOverlay(layerDrawable, this, mapFontSize);
			}
		} else if(condition.getGeneral_condition().equals(getString(R.string.thunderstorm))) {
			if(Double.parseDouble(condition.getThunderstorm_intensity()) == 0.0) { 
				Drawable thunderstormDrawable = this.getResources().getDrawable(R.drawable.ic_col_r_l1);
				Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable( thunderstormDrawable)};
				LayerDrawable layerDrawable = new LayerDrawable(layers);
				overlay = new MapOverlay(layerDrawable, this, mapFontSize);
			} else if(Double.parseDouble(condition.getThunderstorm_intensity()) == 1.0) { 
				Drawable thunderstormDrawable = this.getResources().getDrawable(R.drawable.ic_col_r_l2);
				Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable( thunderstormDrawable)};
				LayerDrawable layerDrawable = new LayerDrawable(layers);
				overlay = new MapOverlay(layerDrawable, this, mapFontSize);
			} else if(Double.parseDouble(condition.getThunderstorm_intensity()) == 2.0) {
				Drawable thunderstormDrawable = this.getResources().getDrawable(R.drawable.ic_col_r_l3);
				Drawable[] layers = {weatherBackgroundDrawable,resizeDrawable( thunderstormDrawable)};
				LayerDrawable layerDrawable = new LayerDrawable(layers);
				overlay = new MapOverlay(layerDrawable, this, mapFontSize);
			} 
		} else {
			// there is no current condition. show just the barometer icon
			// perhaps with a tendency arrow
			//log("No condition found, default: " + getString(R.string.precipitation) + " " + condition.getGeneral_condition());
			
		}

		
    	return overlay;
    }
    
    // The gesture threshold expressed in dp
    // http://developer.android.com/guide/practices/screens_support.html#density-independence
    private static final float GESTURE_THRESHOLD_DP = 16.0f;
    
    // resize drawables on demand. 
    // High-res bitmaps on Android? Be careful of memory issues
    private Drawable resizeDrawable(Drawable image) {
        Bitmap d = ((BitmapDrawable)image).getBitmap();
        final float scale = getResources().getDisplayMetrics().density;
        int p = (int) (GESTURE_THRESHOLD_DP * scale + 0.5f);
        Bitmap bitmapOrig = Bitmap.createScaledBitmap(d, p*4, p*4, false);
        return new BitmapDrawable(bitmapOrig);
    }
    
    // Put a bunch of barometer readings and current conditions on the map.
    public void addDataToMap(ArrayList<BarometerReading> readingsList, ArrayList<CurrentCondition> conditionsList, boolean showTendencies, HashMap<String, String> tendencies) {
    	log("add data to map " + readingsList.size());
    	BarometerMapView mv = (BarometerMapView) findViewById(R.id.mapview);
    	List<Overlay> mapOverlays = mv.getOverlays();
    	
    	Drawable drawable = this.getResources().getDrawable(R.drawable.ic_marker);
    	mapOverlays.clear();
    	
    	try {
    		// Add Barometer Readings and associated current Conditions
    		for(BarometerReading br : readingsList) {
	    		MapOverlay overlay;
	    		
	    		String snippet = br.getAndroidId();
	    		
	    		// Pick an overlay icon depending on the reading and 
	    		// the current conditions. reading alone? reading with tendency?
	    		// current condition alone? current condition with tendency?
	    		
	    		// is there a current condition from the same user as this reading?
	    		Drawable pressureBackgroundDrawable = resizeDrawable(this.getResources().getDrawable(R.drawable.bg_pre_rect));
	        	boolean onlyPressure = true;
	    		overlay = new MapOverlay(drawable, this, mapFontSize);
	    		for(CurrentCondition condition: conditionsList) {
	    			if(condition.getUser_id().equals(br.getAndroidId())) {
	    				// pick and hold the overlay
	    				overlay = getCurrentConditionOverlay(condition, drawable);
	    				// remove this condition from the list and break out of the loop
	    				// this leaves all non-barometer device conditions in the list
	    				// for processing just after.
	    				conditionsList.remove(condition);
	    				onlyPressure = false;
	    				break;
	    			} 
	    		}
	    		
	    		if(onlyPressure ) {
	       			//overlay = new MapOverlay(pressureBackgroundDrawable, this, mapFontSize);
	    		}
	    		
	        	GeoPoint point = new GeoPoint((int)((br.getLatitude()) * 1E6), (int)((br.getLongitude()) * 1E6));
	        	
	        	String textForTitle = convertfromMillibarTo(br.getReading()) + " " + mUnit.getAbbreviation();
	        	OverlayItem overlayitem = new OverlayItem(point, textForTitle, snippet);
	        	overlay.addOverlay(overlayitem);
	        	mapOverlays.add(overlay);
	        	
	        	mv.invalidate();
	    	}
    		
    		// Add singleton Current Conditions
    		for(CurrentCondition condition : conditionsList) {
	    		MapOverlay overlay;
	    		
	    		// Pick an overlay icon depending on the reading and 
	    		// the current conditions. reading alone? reading with tendency?
	    		// current condition alone? current condition with tendency?
	    		
	    		// is there a current condition from the same user as this reading?
	    		overlay = new MapOverlay(drawable, this, mapFontSize);
	    		overlay = getCurrentConditionOverlay(condition, drawable);
	    		
	    		
	        	GeoPoint point = new GeoPoint((int)((condition.getLatitude()) * 1E6), (int)((condition.getLongitude()) * 1E6));
	        	String snippet = "singleton_condition"; //condition.getUser_id();
	        	String textForTitle = "";
	        	OverlayItem overlayitem = new OverlayItem(point, textForTitle, snippet);
	        	overlay.addOverlay(overlayitem);
	        	mapOverlays.add(overlay);
	        	
	        	mv.invalidate();
    		}
	    } catch(Exception e) {
	    	log("add data error: " + e.getMessage());
	    }
    }

    // Runnable to refresh the map. Can be called when another
    // thread wishes to refresh the view.
    private final Runnable refreshMap = new Runnable() {
        public void run() {
        	BarometerMapView mapView = (BarometerMapView) findViewById(R.id.mapview);
        	mapView.invalidate();
        	mapView.refreshDrawableState();
        }
    };
    
    // Assemble a list of BarometerReadings. This is the opposite of function barometerReadingToWeb in the servlet.
    public ArrayList<BarometerReading> csvToBarometerReadings(String[] readings) {
    	ArrayList<BarometerReading> readingsList = new ArrayList<BarometerReading>();
    	for(int i = 0; i<readings.length; i++) {
    		try {
	    		String[] values = readings[i].split("\\|");
	    		BarometerReading br = new BarometerReading();
	    		br.setLatitude(Double.parseDouble(values[0]));
	    		br.setLongitude(Double.parseDouble(values[1]));
	    		br.setReading(Double.parseDouble(values[2]));
	    		br.setTime(Double.parseDouble(values[3]));
	    		br.setTimeZoneOffset(Integer.parseInt(values[4]));
	    		br.setAndroidId(values[5]);
	    		br.setSharingPrivacy(values[6]);
	    		// client_key
	    		br.setLocationAccuracy(Float.parseFloat(values[8]));
	    		br.setReadingAccuracy(Float.parseFloat(values[9]));
	    		readingsList.add(br);
    		} catch(NumberFormatException nfe) {
    			// Likely, tomcat returned an error.
    			log("Server error? " + nfe.getMessage());
    		} catch(ArrayIndexOutOfBoundsException aioobe) {
    			aioobe.printStackTrace();
    			log("array index error");
    		}
    	}
    	
    	return readingsList;
    }
    
    // Assemble a HashMap of userIDs and tendencies. //This is the opposite of function barometerReadingToWeb in the servlet.
    public HashMap<String, String> csvToBarometerTendencies(String[] readings) {
    	//log("csv to barometer tendencies : " + readings[0] + ", " + readings.length);
    	HashMap<String, String> tendencies = new HashMap<String, String>();
    	for(int i = 0; i<readings.length; i++) {
    		try {
	    		String[] values = readings[i].split(",");
	    		
	    		for(String a : values) {
	    			//log(a);
	    		}
	    		//log("tendency check " + values[5]);
	    		tendencies.put(values[5], values[6]); // userid, tendency
    		} catch(Exception e) {
    			// Likely, the server returned an error.
    			log("Server error tendencies? " + e.getMessage());
    		}
    	}
    	
    	return tendencies;
    }
    
    // When we get a download, split up the data into barometer readings
    // or tendency information and display it on the map.
    public void processDownloadResult(String result) {
    	log("process download result");
    	if(!result.equals("")) {
    		if(result.contains("local_data return;")) {
    			// local_data return now contains both pressure readings
    			// and current conditions
    			
    			// get the result
    			result = result.substring("local_data return;".length());
	    		
    			// split into barometer data and weather data
    			String[] bothDataSets = result.split("----------");
    			log("both data sets " + result);
    			
    			try {
	    			String[] csvReading = bothDataSets[0].split(";");
	    			ArrayList<CurrentCondition> conditions = new ArrayList<CurrentCondition>();
	    			try {
		    			if(bothDataSets[1].contains(";")) {
		    				String[] csvConditions = bothDataSets[1].split(";");
		    				conditions = csvToCurrentConditions(csvConditions);
		    			}
	    			} catch(ArrayIndexOutOfBoundsException aiooe) {
	    				// no problem. there aren't any conditions.
	    			}
	    			ArrayList<BarometerReading> readings = csvToBarometerReadings(csvReading);
	    			addDataToMap(readings, conditions, false, null);
	    		} catch(Exception e) {
	    			e.printStackTrace();
	    			log("error in add data : " + e.getMessage());
	    		}
	    		
    		} 
    	} else {
    		log("failed to process download result.");
    		
    	}
    }
    
    // Preparation for sending a barometer reading through the network. 
    // Take the object and NVP it.
    public List<NameValuePair> barometerReadingToNVP(BarometerReading br) {
    	List<NameValuePair> nvp = new ArrayList<NameValuePair>();
    	nvp.add(new BasicNameValuePair("latitude",br.getLatitude() + ""));
    	nvp.add(new BasicNameValuePair("longitude",br.getLongitude() + ""));
    	nvp.add(new BasicNameValuePair("reading",br.getReading() + ""));
    	nvp.add(new BasicNameValuePair("time",br.getTime() + ""));
    	nvp.add(new BasicNameValuePair("tzoffset",br.getTimeZoneOffset() + ""));
    	nvp.add(new BasicNameValuePair("text",br.getAndroidId() + ""));
    	nvp.add(new BasicNameValuePair("share",br.getSharingPrivacy() + ""));
    	nvp.add(new BasicNameValuePair("client_key", getApplicationContext().getPackageName()));
    	nvp.add(new BasicNameValuePair("location_accuracy",br.getLocationAccuracy() + ""));
    	nvp.add(new BasicNameValuePair("reading_accuracy", br.getReadingAccuracy() + ""));
    	return nvp;
    }
    
    public void submitDataToServer() {
    	new ReadingSender().execute("");
    }
    
    public void loadAndShowData() {
    	new DataDownload().execute("");
    }
    
    private BroadcastReceiver receiveForMap = 
    	new BroadcastReceiver() {
        	@Override
        	public void onReceive(Context context, Intent intent) {
        		if (intent.getAction().equals(BarometerMapView.CUSTOM_INTENT)) {
        			loadAndShowData();
        		}
        	}
    	};
    
    // Send data to the server in the background.
    private class ReadingSender extends AsyncTask<String, Integer, Long> {
		@Override
		protected Long doInBackground(String... arg0) {
			
			if(mLatitude == 0.0) {
				LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
				Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        		double latitude = loc.getLatitude();
        		double longitude = loc.getLongitude();
        		mLatitude = latitude;
        		mLongitude = longitude;
        		mLocationAccuracy = loc.getAccuracy();
			}
			
			if((mLatitude == 0.0) || (mLongitude == 0.0) || (mReading == 0.0)) {
				//don't submit
				return null;
			}
			
			// get sharing preference
			SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
			String share = settings.getString("sharing_preference", "Us and Researchers");
		    
			// if sharing is None, don't send anything anywhere.
			if (share.equals("Nobody")) {
				return null;
			}
			
			log("app sending " + mReading);
			
	    	BarometerReading br = new BarometerReading();
	    	br.setLatitude(mLatitude);
	    	br.setLongitude(mLongitude);
	    	br.setTime(Calendar.getInstance().getTimeInMillis());
	    	br.setTimeZoneOffset(Calendar.getInstance().getTimeZone().getOffset((long)br.getTime()));
	    	br.setReading(mReading);
	    	br.setAndroidId(android_id);
	    	br.setSharingPrivacy(share);
	    	br.setLocationAccuracy(mLocationAccuracy);
	    	br.setReadingAccuracy(mReadingAccuracy);
	    	
	    	
	    	
	    	SecureHttpClient client = new SecureHttpClient(getApplicationContext());
	    	HttpPost httppost = new HttpPost(serverURL);
	    	// keep a history of readings on the user's device
	    	addToLocalDatabase(br);
	    	
	    	try {
	    		List<NameValuePair> nvps = barometerReadingToNVP(br);
	    		httppost.setEntity(new UrlEncodedFormEntity(nvps));
	    		HttpResponse response = client.execute(httppost);
	    	} catch(ClientProtocolException cpe) {
	    		// BROKEN HERE
	    		cpe.printStackTrace();
	    		// log(cpe.getMessage());
	    		// TODO: alert of failed submit
	    	} catch(IOException ioe) {
	    		ioe.printStackTrace();
	    		// log(ioe.getMessage());
	    		// TODO: alert of failed submit
	    	}
			return null;
		}
    	
		protected void onPostExecute(Long result) {
			
			Toast.makeText(getApplicationContext(), "Submitted Reading", Toast.LENGTH_SHORT).show();
			loadAndShowData();
		}
    }

    // Download data from the server in the background
    private class DataDownload extends AsyncTask<String, String, String> {
    	@Override
		protected String doInBackground(String... arg0) {
			log("DataDownload doInBackground start");
			
	    	String responseText = "";
	    	
	    	try {
	    		//log("DataDownload doInBackground start try block");
	    		
	    		// Instantiate the custom HttpClient
	    		SecureHttpClient client = new SecureHttpClient(getApplicationContext());
	    	
	    		HttpPost post = new HttpPost(serverURL);
	    		
	    		List<NameValuePair> nvps = new ArrayList<NameValuePair>();

	    		String type = "local_current_data";
	    		
	    		if(type.equals("all_data")) {
	    			nvps.add(new BasicNameValuePair("download","all_data"));
	    		} else if(type.equals("recent_data")) {
	    			nvps.add(new BasicNameValuePair("download","recent_data"));
	    			nvps.add(new BasicNameValuePair("days","1"));
	    		} else if(type.equals("local_data")) {
	    			// PRESSURENET DEFAULT v. 1.0 to 3.0.1
	    			BarometerMapView mv = (BarometerMapView) findViewById(R.id.mapview);
		    		GeoPoint center = mv.getMapCenter();
		    		int latSpan = mv.getLatitudeSpan();
		    		int longSpan = mv.getLongitudeSpan();
		    		
		    		nvps.add(new BasicNameValuePair("download", "local_data"));
		    		nvps.add(new BasicNameValuePair("centerlat", center.getLatitudeE6() + ""));
		    		nvps.add(new BasicNameValuePair("centerlon", center.getLongitudeE6() + ""));
		    		nvps.add(new BasicNameValuePair("latspan", latSpan + ""));
		    		nvps.add(new BasicNameValuePair("longspan", longSpan + ""));
	    		} else if(type.equals("local_current_data")) {
	    			// Same base as local_data but with Current Conditions added
	    			BarometerMapView mv = (BarometerMapView) findViewById(R.id.mapview);
		    		GeoPoint center = mv.getMapCenter();
		    		int latSpan = mv.getLatitudeSpan();
		    		int longSpan = mv.getLongitudeSpan();
		    		
		    		// masquerading as local_data might break the local_data else if above
		    		nvps.add(new BasicNameValuePair("download", "local_data")); 
		    		nvps.add(new BasicNameValuePair("centerlat", center.getLatitudeE6() + ""));
		    		nvps.add(new BasicNameValuePair("centerlon", center.getLongitudeE6() + ""));
		    		nvps.add(new BasicNameValuePair("latspan", latSpan + ""));
		    		nvps.add(new BasicNameValuePair("longspan", longSpan + ""));
		    		
	    		} else if(type.equals("local_tendency_data")) {
	    			BarometerMapView mv = (BarometerMapView) findViewById(R.id.mapview);
		    		GeoPoint center = mv.getMapCenter();
		    		int latSpan = mv.getLatitudeSpan();
		    		int longSpan = mv.getLongitudeSpan();
		    		nvps.add(new BasicNameValuePair("download", "local_tendency_data"));
		    		nvps.add(new BasicNameValuePair("centerlat", center.getLatitudeE6() + ""));
		    		nvps.add(new BasicNameValuePair("centerlon", center.getLongitudeE6() + ""));
		    		nvps.add(new BasicNameValuePair("latspan", latSpan + ""));
		    		nvps.add(new BasicNameValuePair("longspan", longSpan + ""));
	    		}
	    		
	    		post.setEntity(new UrlEncodedFormEntity(nvps));
	    		
	    		
	    		// Execute the GET call and obtain the response
	    		HttpResponse getResponse = client.execute(post);
	    		HttpEntity responseEntity = getResponse.getEntity();
	    		
	    		
	    		BufferedReader r = new BufferedReader(new InputStreamReader(responseEntity.getContent()));
	    		
	    		StringBuilder total = new StringBuilder();
	    		String line;
	    		if(r!=null) {
		    		while((line = r.readLine()) != null) {
		    			total.append(line);
		    		}
		    		responseText = total.toString();
	    		}
	    	} catch(Exception e) {
	    		log(e.getMessage() + "");
	    	}
	    	return responseText;
		}
		protected void onPostExecute(String result) {
			log("datadownload post execute: '" + result + "'");
			processDownloadResult(result);
		
			mapHandler.postDelayed(refreshMap, 100);
		}
    }
    
	/*
	 * TODO: remove in favour of externalized Unit class
	 */
	public class Unit {
		double value;
		String abbrev;
		
		// Conversion factors from http://www.csgnetwork.com/meteorologyconvtbl.html
		public void convertToPreferredUnit(String unit) {
			//log("converting " + mReading + " to " + unit);
			try {
				if(abbrev.contains("mbar")) {
					// No change. reading comes to us in mbar.
					this.value = mReading;
				} else if(abbrev.contains("hPa")) {
					// mbar = hpa.
					this.value = mReading;
				} else if(abbrev.contains("atm")) {
					this.value = mReading * 0.000986923;
				} else if(abbrev.contains("kPa")) {
					this.value = mReading * 0.1;
				} else if(abbrev.contains("mmHg")) {
					this.value = mReading * 0.75006;
				} else if(abbrev.contains("inHg")) {
					this.value = mReading* 0.02961; 
				} else {
					// default to mb
					this.value = mReading;
				}
			} catch(Exception e) {
				// Probably no barometer reading.
				log(e.getMessage() + "");				
			}
		}
		
		public void updateReadingFromOutside() {
			convertToPreferredUnit(this.abbrev);
		}
		
		public String getDisplayText() {
			return value + " " + abbrev;
		}
		
		public Unit(String abbrev) {
			convertToPreferredUnit(abbrev);
			this.abbrev = abbrev;
		}
		public double getValue() {
			return value;
		}
		public void setValue(double value) {
			this.value = value;
		}
		public String getAbbreviation() {
			return abbrev;
		}
		public void setAbbreviation(String abbreviation) {
			this.abbrev = abbreviation;
		}
	}
    
	// Stop listening to the barometer when our app is paused.
	@Override
	protected void onPause() {
        super.onPause();
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mLocationManager.removeUpdates(locationListener);
        sm.unregisterListener(this);
        unregisterReceiver(receiveForMap);
        dbAdapter.close();
	}
	
	// Register a broadcast listener
	@Override 
	protected void onResume() {
		super.onResume();
		registerReceiver(receiveForMap, new IntentFilter(BarometerMapView.CUSTOM_INTENT));
		// Check for auto-send settings change.
		getStoredPreferences();
		getLocationInformation();
		if(mUpdateServerAutomatically) {
			// Start the update service.
			try {
				if(!isPressureNETServiceRunning()) {
					log("on resume restarting the update service");
					serviceIntent = new Intent(this, SubmitReadingService.class);
					serviceIntent.putExtra("appdir", mAppDir);
					stopService(serviceIntent);
					startService(serviceIntent);
				} else {
					log("on resume not starting service, already running");
				}
			} catch(Exception e) {
				log(e.getMessage());
			}
		} else {
			// Stop the update service.
			try {
				stopService(serviceIntent);
			} catch(Exception e) {
				serviceIntent = new Intent(this, SubmitReadingService.class);
				serviceIntent.putExtra("appdir", mAppDir);
				stopService(serviceIntent);
				log(e.getMessage());
			}
		}
		setUpBarometer();
		updateVisibleReading();
        loadAndShowData();
        setUpDatabase();
	}
	
	// Must exist for the MapView.
	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		try {
			dbAdapter.close();
			sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
			sm.unregisterListener(this);
			mLocationManager.removeUpdates(locationListener);
			unregisterReceiver(receiveForMap);
		} catch(Exception e) {
			
		}
		super.onDestroy();
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		switch (sensor.getType()) {
		case Sensor.TYPE_PRESSURE: 
			mReading = accuracy;
		    break;
	    }
	}

	private boolean isPressureNETServiceRunning() {
	    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (SubmitReadingService.class.getName().equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
	
	public void updateVisibleReading() {
		mUnit.updateReadingFromOutside();
		double value = mUnit.getValue();
		TextView textView = (TextView) findViewById(R.id.textReading); 
		if(value!=0.0) {
			textView.setVisibility(View.VISIBLE);
			String abbrev = mUnit.getAbbreviation();
	    	DecimalFormat df = new DecimalFormat("####.00");
	        String toPrint = df.format(value);
	    	textView.setText(toPrint + " " + abbrev + " " + mTendency + " ");
		} else {
			textView.setText("No barometer detected.");
			// textView.setVisibility(View.GONE);
		}
	}
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		switch (event.sensor.getType()) {
		case Sensor.TYPE_PRESSURE: 
			mReading = event.values[0];
	    	updateVisibleReading();
		    break;
	    }
	}


	// Log data to SD card for debug purposes.
	// To enable logging, ensure the Manifest allows writing to SD card.
	public void logToFile(String text) {
		try {
			OutputStream output = new FileOutputStream(mAppDir + "/log.txt", true);
			String logString = (new Date()).toString() + ": " + text + "\n";
			output.write(logString.getBytes());
			output.close();
		} catch(FileNotFoundException e) {
			
		} catch(IOException ioe) {
			
		}
	}
	
    public void log(String text) {
    	//logToFile(text);
    	//System.out.println(text);
    }
}
