package ca.cumulonimbus.barometernetwork;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class CurrentConditionsActivity extends Activity {

	private ImageButton buttonSunny;
	private ImageButton buttonFoggy;
	private ImageButton buttonCloudy;
	private ImageButton buttonPrecipitation;
	private ImageButton buttonThunderstorm;
	private Button buttonSendCondition;
	private Button buttonCancelCondition;
	private ImageButton buttonIsWindy1;
	private ImageButton buttonIsWindy2;
	private ImageButton buttonIsCalm;
	private ImageButton buttonRain;
	private ImageButton buttonSnow;
	private ImageButton buttonHail;
	private ImageButton buttonInfrequentLightning;
	private ImageButton buttonFrequentLightning;
	private ImageButton buttonHeavyLightning;
	private ImageButton buttonLowPrecip;
	private ImageButton buttonModeratePrecip;
	private ImageButton buttonHeavyPrecip;
	
	private TextView textGeneralDescription;
	private TextView textWindyDescription;
	private TextView textPrecipitationDescription;
	private TextView textPrecipitationAmountDescription;
	private TextView textLightningDescription;
	
	private ScrollView scrollGeneral;
	private ScrollView scrollWind;
	private ScrollView scrollPrecipitation;
	private ScrollView scrollPrecipitationAmount;
	private ScrollView scrollLightning;
	
	private double mLatitude = 0.0;
	private double mLongitude = 0.0;
	private CurrentCondition condition;
	
    private String serverURL = PressureNETConfiguration.SERVER_URL;

	public final String PREFS_NAME = "ca.cumulonimbus.barometernetwork_preferences";
	
	public String mAppDir = "";
	
    // Send data to the server in the background.
    private class ConditionSender extends AsyncTask<String, Integer, String> {
		@Override
		protected String doInBackground(String... arg0) {
			
			// Display condition information;
			String displayInfo = condition.toString();
			// Toast.makeText(getApplicationContext(), displayInfo, Toast.LENGTH_LONG).show();
			log("sending " + displayInfo);
			
			if((mLatitude == 0.0) || (mLongitude == 0.0)) {
				//don't submit
				return "conditionsender bailing. no location.";
			}
			
			// get sharing preference
			SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
			String share = settings.getString("sharing_preference", "Us and Researchers");
		    
			// if sharing is None, don't send anything anywhere.
			if (share.equals("Nobody")) {
				return "conditionsender bailing. no permission to share.";
			}
			
			log("app sending " + condition.getGeneral_condition());
			
			
	    	DefaultHttpClient client = new SecureHttpClient(getApplicationContext());
	    	HttpPost httppost = new HttpPost(serverURL);
	    	// TODO: keep a history of readings on the user's device
	    	// addToLocalDatabase(cc);
	    	
	    	try {
	    		List<NameValuePair> nvps = currentConditionToNVP(condition);
	    		nvps.add(new BasicNameValuePair("current_condition", "add"));
	    		httppost.setEntity(new UrlEncodedFormEntity(nvps));
	    		HttpResponse response = client.execute(httppost);
	    	} catch(ClientProtocolException cpe) {
	    		log(cpe.getMessage());
	    		return cpe.getMessage();
	    	} catch(IOException ioe) {
	    		log(ioe.getMessage());
	    		return ioe.getMessage();
	    	}
			return "Success";
		}
    	
		protected void onPostExecute(String result) {	
			if(!result.equals("Success")) {
				log("failed to send condition: " + result);
				Toast.makeText(getApplicationContext(), "Failed to send condition: "+ result, Toast.LENGTH_LONG).show();
			}
			Toast.makeText(getApplicationContext(), "Sent!", Toast.LENGTH_SHORT).show();
			finish();
		}
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
    
    // Preparation for sending a condition through the network. 
    // Take the object and NVP it.
    public List<NameValuePair> currentConditionToNVP(CurrentCondition cc) {
    	List<NameValuePair> nvp = new ArrayList<NameValuePair>();
    	nvp.add(new BasicNameValuePair("latitude", cc.getLatitude() + ""));
    	nvp.add(new BasicNameValuePair("longitude", cc.getLongitude() + ""));
    	nvp.add(new BasicNameValuePair("general_condition", cc.getGeneral_condition() + ""));
    	nvp.add(new BasicNameValuePair("user_id", cc.getUser_id() + ""));
    	nvp.add(new BasicNameValuePair("time", cc.getTime() + ""));
    	nvp.add(new BasicNameValuePair("tzoffset", cc.getTzoffset() + ""));
    	nvp.add(new BasicNameValuePair("windy", cc.getWindy() + ""));
    	nvp.add(new BasicNameValuePair("precipitation_type", cc.getPrecipitation_type() + ""));
    	nvp.add(new BasicNameValuePair("precipitation_amount", cc.getPrecipitation_amount() + ""));
    	nvp.add(new BasicNameValuePair("thunderstorm_intensity", cc.getThunderstorm_intensity() + ""));
    	return nvp;
    }
    
    /**
     * Change the buttons on the UI. General Conditions.
     * @param condition
     */
    private void switchActiveGeneral(String condition) {
    	// Turn everything off
    	buttonSunny.setImageResource(R.drawable.ic_sun);
    	buttonFoggy.setImageResource(R.drawable.ic_fog3);
    	buttonCloudy.setImageResource(R.drawable.ic_cloudy);
    	buttonPrecipitation.setImageResource(R.drawable.ic_precip);
    	buttonThunderstorm.setImageResource(R.drawable.ic_lightning3);
    	
    	// Turn the new one on
    	if(condition.equals(getString(R.string.sunny))) {
    		buttonSunny.setImageResource(R.drawable.ic_on_sun);
    		scrollPrecipitation.setVisibility(View.GONE);
    		textPrecipitationDescription.setVisibility(View.GONE);
    		scrollLightning.setVisibility(View.GONE);
    		textLightningDescription.setVisibility(View.GONE);
    		scrollPrecipitationAmount.setVisibility(View.GONE);
    		textPrecipitationAmountDescription.setVisibility(View.GONE);
    	} else if(condition.equals(getString(R.string.foggy))) {
    		buttonFoggy.setImageResource(R.drawable.ic_on_fog3);
    		scrollPrecipitation.setVisibility(View.GONE);
    		textPrecipitationDescription.setVisibility(View.GONE);
    		scrollLightning.setVisibility(View.GONE);
    		textLightningDescription.setVisibility(View.GONE);
    		scrollPrecipitationAmount.setVisibility(View.GONE);
    		textPrecipitationAmountDescription.setVisibility(View.GONE);
    	} else if(condition.equals(getString(R.string.cloudy))) {
    		buttonCloudy.setImageResource(R.drawable.ic_on_cloudy);
    		scrollPrecipitation.setVisibility(View.GONE);
    		textPrecipitationDescription.setVisibility(View.GONE);
    		scrollPrecipitationAmount.setVisibility(View.GONE);
    		textPrecipitationAmountDescription.setVisibility(View.GONE);
    		scrollLightning.setVisibility(View.GONE);
    		textLightningDescription.setVisibility(View.GONE);
    	} else if(condition.equals(getString(R.string.precipitation))) {
    		scrollPrecipitation.setVisibility(View.VISIBLE);
    		textPrecipitationDescription.setVisibility(View.VISIBLE);
    		buttonPrecipitation.setImageResource(R.drawable.ic_on_precip);
    	} else if(condition.equals(getString(R.string.thunderstorm))) {
    		scrollLightning.setVisibility(View.VISIBLE);
    		textLightningDescription.setVisibility(View.VISIBLE);
    		buttonThunderstorm.setImageResource(R.drawable.ic_on_lightning3);
    	}
    }

    /**
     * Change the buttons on the UI. Windy
     * @param condition
     */
    private void switchActiveWindy(String condition) {
    	// Turn everything off
    	buttonIsCalm.setImageResource(R.drawable.ic_wind0);
    	buttonIsWindy1.setImageResource(R.drawable.ic_wind1);
    	buttonIsWindy2.setImageResource(R.drawable.ic_wind2);
    	
    	// Turn the new one on
    	if(condition.equals(getString(R.string.calm))) {
    		buttonIsCalm.setImageResource(R.drawable.ic_on_wind0);
    	} else if(condition.equals(getString(R.string.windyOne))) {
    		buttonIsWindy1.setImageResource(R.drawable.ic_on_wind1);
    	} else if(condition.equals(getString(R.string.windyTwo))) {
    		buttonIsWindy2.setImageResource(R.drawable.ic_on_wind2);
    	} 
    }
    
    /**
     * When the type changes, we show the new type icon for the 
     * heaviness of the precipitation type
     * @param condition
     */
    private void switchVisiblePrecipitations(String condition) {
    	if(condition.equals(getString(R.string.rain))) {
    		buttonLowPrecip.setImageResource(R.drawable.ic_on_rain1);
    		buttonModeratePrecip.setImageResource(R.drawable.ic_rain2);
    		buttonHeavyPrecip.setImageResource(R.drawable.ic_rain3);
    	} else if(condition.equals(getString(R.string.snow))) {
    		buttonLowPrecip.setImageResource(R.drawable.ic_on_snow1);
    		buttonModeratePrecip.setImageResource(R.drawable.ic_snow2);
    		buttonHeavyPrecip.setImageResource(R.drawable.ic_snow3);    	
    	} else if(condition.equals(getString(R.string.hail))) {
    		buttonLowPrecip.setImageResource(R.drawable.ic_on_hail1);
    		buttonModeratePrecip.setImageResource(R.drawable.ic_hail2);
    		buttonHeavyPrecip.setImageResource(R.drawable.ic_hail3);
    	}
    }
    
    /**
     * Change the buttons on the UI. Precipitation
     * @param condition
     */
    private void switchActivePrecipitation(String condition) {
    	// Turn everything off
    	buttonRain.setImageResource(R.drawable.ic_rain3);
    	buttonSnow.setImageResource(R.drawable.ic_snow3);
    	buttonHail.setImageResource(R.drawable.ic_hail3);
    	
    	// Turn the new one on
    	if(condition.equals(getString(R.string.rain))) {
    		switchVisiblePrecipitations(getString(R.string.rain));
    		buttonRain.setImageResource(R.drawable.ic_on_rain3);
    	} else if(condition.equals(getString(R.string.snow))) {
    		switchVisiblePrecipitations(getString(R.string.snow));
    		buttonSnow.setImageResource(R.drawable.ic_on_snow3);
    	} else if(condition.equals(getString(R.string.hail))) {
    		switchVisiblePrecipitations(getString(R.string.hail));
    		buttonHail.setImageResource(R.drawable.ic_on_hail3);
    	} 
    	
    	scrollPrecipitationAmount.setVisibility(View.VISIBLE);
    	textPrecipitationAmountDescription.setVisibility(View.VISIBLE);
    }
        
    /**
     * Change the buttons on the UI. Precipitation Amounts
     * @param condition
     */
    private void switchActivePrecipitationAmount(String amount) {
    	// Off and on, all in one go
    	try {
	    	if (condition.getPrecipitation_type().equals(getString(R.string.rain))) {
	    		if(amount.equals("low")) {
	    			buttonLowPrecip.setImageResource(R.drawable.ic_on_rain1);
	    			buttonModeratePrecip.setImageResource(R.drawable.ic_rain2);
	    			buttonHeavyPrecip.setImageResource(R.drawable.ic_rain3);
	    		} else if(amount.equals("moderate")) {
	    			buttonLowPrecip.setImageResource(R.drawable.ic_rain1);
	    			buttonModeratePrecip.setImageResource(R.drawable.ic_on_rain2);
	    			buttonHeavyPrecip.setImageResource(R.drawable.ic_rain3);
	    		} else if(amount.equals("heavy")) {
	    			buttonLowPrecip.setImageResource(R.drawable.ic_rain1);
	    			buttonModeratePrecip.setImageResource(R.drawable.ic_rain2);
	    			buttonHeavyPrecip.setImageResource(R.drawable.ic_on_rain3);
	    		}
	    	} else if (condition.getPrecipitation_type().equals(getString(R.string.snow))) {
	    		if(amount.equals("low")) {
	    			buttonLowPrecip.setImageResource(R.drawable.ic_on_snow1);
	    			buttonModeratePrecip.setImageResource(R.drawable.ic_snow2);
	    			buttonHeavyPrecip.setImageResource(R.drawable.ic_snow3);
	    		} else if(amount.equals("moderate")) {
	    			buttonLowPrecip.setImageResource(R.drawable.ic_snow1);
	    			buttonModeratePrecip.setImageResource(R.drawable.ic_on_snow2);
	    			buttonHeavyPrecip.setImageResource(R.drawable.ic_snow3);
	    		} else if(amount.equals("heavy")) {
	    			buttonLowPrecip.setImageResource(R.drawable.ic_snow1);
	    			buttonModeratePrecip.setImageResource(R.drawable.ic_snow2);
	    			buttonHeavyPrecip.setImageResource(R.drawable.ic_on_snow3);
	    		}
	    	} else if (condition.getPrecipitation_type().equals(getString(R.string.hail))) {
	    		if(amount.equals("low")) {
	    			buttonLowPrecip.setImageResource(R.drawable.ic_on_hail1);
	    			buttonModeratePrecip.setImageResource(R.drawable.ic_hail2);
	    			buttonHeavyPrecip.setImageResource(R.drawable.ic_hail3);
	    		} else if(amount.equals("moderate")) {
	    			buttonLowPrecip.setImageResource(R.drawable.ic_hail1);
	    			buttonModeratePrecip.setImageResource(R.drawable.ic_on_hail2);
	    			buttonHeavyPrecip.setImageResource(R.drawable.ic_hail3);
	    		} else if(amount.equals("heavy")) {
	    			buttonLowPrecip.setImageResource(R.drawable.ic_hail1);
	    			buttonModeratePrecip.setImageResource(R.drawable.ic_hail2);
	    			buttonHeavyPrecip.setImageResource(R.drawable.ic_on_hail3);
	    		}
	    	}
    	} catch(NullPointerException npe) {
    		// must have a precipitation type set. 
    	}
    }
    
    private void switchActiveLightning(String value) {
    	// Turn everything off
    	buttonInfrequentLightning.setImageResource(R.drawable.ic_r_l1);
    	buttonFrequentLightning.setImageResource(R.drawable.ic_r_l2);
    	buttonHeavyLightning.setImageResource(R.drawable.ic_r_l3);
    	// Turn the new one on
    	if(value.equals(getString(R.string.infrequentLightning))) {
    		buttonInfrequentLightning.setImageResource(R.drawable.ic_on_r_l1);
    	} else if(value.equals(getString(R.string.frequentLightning))) {
    		buttonFrequentLightning.setImageResource(R.drawable.ic_on_r_l2);
    	} else if(value.equals(getString(R.string.heavyLightning))) {;
    		buttonHeavyLightning.setImageResource(R.drawable.ic_on_r_l3);
    	} 
    }
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.current_conditions);
		
		condition = new CurrentCondition();
		
		buttonSunny = (ImageButton) findViewById(R.id.buttonSunny);
		buttonFoggy = (ImageButton) findViewById(R.id.buttonFoggy);
		buttonCloudy = (ImageButton) findViewById(R.id.buttonCloudy);
		buttonPrecipitation = (ImageButton) findViewById(R.id.buttonPrecipitation);
		buttonThunderstorm = (ImageButton) findViewById(R.id.buttonThunderstorm);
		buttonSendCondition = (Button) findViewById(R.id.buttonSendCondition);
		buttonCancelCondition = (Button) findViewById(R.id.buttonCancelCondition);
		buttonIsWindy1 = (ImageButton) findViewById(R.id.buttonIsWindy1);
		buttonIsWindy2 = (ImageButton) findViewById(R.id.buttonIsWindy2);
		buttonIsCalm = (ImageButton) findViewById(R.id.buttonIsCalm);
		buttonRain = (ImageButton) findViewById(R.id.buttonRain);
		buttonSnow = (ImageButton) findViewById(R.id.buttonSnow);
		buttonHail= (ImageButton) findViewById(R.id.buttonHail);
		buttonInfrequentLightning = (ImageButton) findViewById(R.id.buttonInfrequentLightning);
		buttonFrequentLightning = (ImageButton) findViewById(R.id.buttonFrequentLightning);
		buttonHeavyLightning = (ImageButton) findViewById(R.id.buttonHeavyLightning);
		buttonLowPrecip = (ImageButton) findViewById(R.id.buttonLowPrecip);
		buttonModeratePrecip = (ImageButton) findViewById(R.id.buttonModeratePrecip);
		buttonHeavyPrecip = (ImageButton) findViewById(R.id.buttonHeavyPrecip);
		
		textGeneralDescription = (TextView) findViewById(R.id.generalDescription);
		textWindyDescription = (TextView) findViewById(R.id.windyDescription);
		textLightningDescription = (TextView) findViewById(R.id.lightningDescription);
		textPrecipitationDescription = (TextView) findViewById(R.id.precipitationDescription);
		textPrecipitationAmountDescription = (TextView) findViewById(R.id.precipitationAmountDescription);
		
		scrollGeneral = (ScrollView) findViewById(R.id.scrollGeneralCondition);
		scrollWind = (ScrollView) findViewById(R.id.scrollWindy);
		scrollPrecipitation = (ScrollView) findViewById(R.id.scrollPrecip);
		scrollPrecipitationAmount = (ScrollView) findViewById(R.id.scrollPrecipAmount);
		scrollLightning = (ScrollView) findViewById(R.id.scrollLightning);
		
		buttonSendCondition.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new ConditionSender().execute("");
			}
		});
		
		buttonCancelCondition.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});
		
		
		/*
		 * General Conditions
		 */
		
		buttonSunny.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String value = getString(R.string.sunny);
				switchActiveGeneral(value);
				condition.setGeneral_condition(value);
				textGeneralDescription.setText(value);
			}
		});
		
		buttonFoggy.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String value =  getString(R.string.foggy);
				switchActiveGeneral(value);
				condition.setGeneral_condition(value);
				textGeneralDescription.setText(value);
			}
		});
		
		buttonCloudy.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String value =  getString(R.string.cloudy);
				switchActiveGeneral(value);
				condition.setGeneral_condition(value);
				textGeneralDescription.setText(value);
			}
		});
		
		buttonPrecipitation.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String value =  getString(R.string.precipitation);
				switchActiveGeneral(value);
				condition.setGeneral_condition(value);
				textGeneralDescription.setText(value);
			}
		});
		
		buttonThunderstorm.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String value = getString(R.string.thunderstorm);
				switchActiveGeneral(value);
				condition.setGeneral_condition(value);
				textGeneralDescription.setText(value);
			}
		});
		
		/*
		 * Windy conditions
		 */
		
		buttonIsWindy1.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				String value = getString(R.string.windyOne);
				switchActiveWindy(value);
				condition.setWindy(1 + "");
				textWindyDescription.setText(value);
			}
		});
		
		buttonIsWindy2.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				String value = getString(R.string.windyTwo);
				switchActiveWindy(value);
				condition.setWindy(2 + "");
				textWindyDescription.setText(value);
			}
		});
		
		buttonIsCalm.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				String value = getString(R.string.calm);
				switchActiveWindy(value);
				condition.setWindy(value);
				textWindyDescription.setText(value);
			}
		});
		
		/*
		 * Precipitation Conditions
		 */
		
		buttonRain.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				String value = getString(R.string.rain);
				switchActivePrecipitation(value);
				condition.setPrecipitation_type(value);
				textPrecipitationDescription.setText(value);
			}
		});
		
		buttonSnow.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				String value = getString(R.string.snow);
				switchActivePrecipitation(value);
				condition.setPrecipitation_type(value);
				textPrecipitationDescription.setText(value);
			}
		});
		
		buttonHail.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				String value = getString(R.string.hail);
				switchActivePrecipitation(value);
				condition.setPrecipitation_type(value);
				textPrecipitationDescription.setText(value);
			}
		});
		
		/*
		 * Precipitation amount
		 */
		
		buttonLowPrecip.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				double value = 0.0;
				String printValue = "Minimal " + condition.getPrecipitation_type();
				condition.setPrecipitation_amount(value);
				switchActivePrecipitationAmount("low");
				textPrecipitationAmountDescription.setText(printValue);
				
			}
		});
		
		buttonModeratePrecip.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				double value = 1.0;
				String printValue = "Moderate " + condition.getPrecipitation_type();
				switchActivePrecipitationAmount("moderate");
				condition.setPrecipitation_amount(value);
				textPrecipitationAmountDescription.setText(printValue);
				
			}
		});
		
		buttonHeavyPrecip.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				double value = 2.0;
				String printValue = "Heavy " + condition.getPrecipitation_type();
				condition.setPrecipitation_amount(value);
				switchActivePrecipitationAmount("heavy");
				textPrecipitationAmountDescription.setText(printValue);
			}
		});
		
		/*
		 * Lightning
		 * 
		 */
		
		buttonInfrequentLightning.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				String value = getString(R.string.infrequentLightning);
				switchActiveLightning(value);
				condition.setThunderstorm_intensity(value);
				textLightningDescription.setText(value);
			}
		});
		
		buttonFrequentLightning.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				String value = getString(R.string.frequentLightning);
				switchActiveLightning(value);
				condition.setThunderstorm_intensity(value);
				textLightningDescription.setText(value);
			}
		});
		
		buttonHeavyLightning.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				String value = getString(R.string.heavyLightning);
				switchActiveLightning(value);
				condition.setThunderstorm_intensity(value);
				textLightningDescription.setText(value);
			}
		});
		
		// Start adding the data for our current condition
		Bundle bundle = getIntent().getExtras();
		try {
			mAppDir = bundle.getString("appdir");
			mLatitude = bundle.getDouble("latitude");
			mLongitude = bundle.getDouble("longitude");
			condition.setLatitude(mLatitude);
			condition.setLongitude(mLongitude);
			condition.setUser_id(getID());
			condition.setTime(Calendar.getInstance().getTimeInMillis());
	    	condition.setTzoffset(Calendar.getInstance().getTimeZone().getOffset((long)condition.getTime()));
	    	
			//Toast.makeText(this, userSelf + " " + mAppDir, Toast.LENGTH_SHORT).show();
		} catch(Exception e) {
			log("conditions missing data, cannot submit");
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
    	logToFile(text);
    	System.out.println(text);
    }
	
	@Override
	protected void onPause() {

		super.onPause();
	}
}
