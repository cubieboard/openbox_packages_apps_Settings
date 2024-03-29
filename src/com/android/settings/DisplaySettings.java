/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import android.app.ActivityManagerNative;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.PowerManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.IWindowManager;
import android.view.Surface;
import android.view.WindowManager;
import android.view.DisplayManager;
import java.util.ArrayList;

public class DisplaySettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "DisplaySettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;
    
    private static final int MIN_HDMI_MODE = 0;
    private static final int AUTO_HDMI_MODE = 0xFF;

    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_ACCELEROMETER = "accelerometer";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_CALABRATION = "tscalibration";
    private static final String KEY_ACCELEROMETER_COORDINATE = "accelerometer_coornadite";
    private static final String KEY_SCREEN_ADAPTION = "screen_adaption";
    private static final String KEY_SMART_BRIGHTNESS = "smart_brightness";
    private static final String KEY_SMART_BRIGHTNESS_PREVIEW = "key_smart_brightness_preview";
    private static final String KEY_HDMI_OUTPUT_MODE = "hdmi_output_mode";
    private CheckBoxPreference mSmartBrightness;
    private CheckBoxPreference mAccelerometer;
    private ListPreference mFontSizePref;
    private CheckBoxPreference mNotificationPulse;
    private Preference mCalibration;
    private CheckBoxPreference mSmartBrightnessPreview;
    private ListPreference mAccelerometerCoordinate;
    private ListPreference mHdmiOutputMode;

    private final Configuration mCurConfig = new Configuration();
    
    private ListPreference mScreenTimeoutPreference;
    private Preference mScreenAdaption;
    private IWindowManager mWindowManager;
    private ContentObserver mAccelerometerRotationObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateAccelerometerRotationCheckbox();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ContentResolver resolver = getActivity().getContentResolver();

        addPreferencesFromResource(R.xml.display_settings);

        mAccelerometer = (CheckBoxPreference) findPreference(KEY_ACCELEROMETER);
        mAccelerometer.setPersistent(false);

        mScreenTimeoutPreference = (ListPreference) findPreference(KEY_SCREEN_TIMEOUT);
        final long currentTimeout = Settings.System.getLong(resolver, SCREEN_OFF_TIMEOUT,
                FALLBACK_SCREEN_TIMEOUT_VALUE);
        mScreenTimeoutPreference.setValue(String.valueOf(currentTimeout));
        mScreenTimeoutPreference.setOnPreferenceChangeListener(this);
        disableUnusableTimeouts(mScreenTimeoutPreference);
        updateTimeoutPreferenceDescription(currentTimeout);

        mFontSizePref = (ListPreference) findPreference(KEY_FONT_SIZE);
        mFontSizePref.setOnPreferenceChangeListener(this);
        mNotificationPulse = (CheckBoxPreference) findPreference(KEY_NOTIFICATION_PULSE);
        if (mNotificationPulse != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_intrusiveNotificationLed) == false) {
            getPreferenceScreen().removePreference(mNotificationPulse);
        } else {
            try {
                mNotificationPulse.setChecked(Settings.System.getInt(resolver,
                        Settings.System.NOTIFICATION_LIGHT_PULSE) == 1);
                mNotificationPulse.setOnPreferenceChangeListener(this);
            } catch (SettingNotFoundException snfe) {
                Log.e(TAG, Settings.System.NOTIFICATION_LIGHT_PULSE + " not found");
            }
        }
        mScreenAdaption = (Preference)findPreference(KEY_SCREEN_ADAPTION);
        WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        android.view.Display display = wm.getDefaultDisplay();
        int width     = display.getWidth();
        int height    = display.getHeight();
        Log.d(TAG,"rate1 = " + (width * 3.0f / (height * 5.0f)) + 
                 " rate2 = " + (width * 5.0f / (height * 3.0f)));
        if(((width * 3.0f / (height * 5.0f) == 1.0f) ||
           (width * 5.0f / (height * 3.0f) == 1.0f)) && mScreenAdaption!=null){
            getPreferenceScreen().removePreference(mScreenAdaption) ;   
        }
        
        mCalibration = (Preference)findPreference(KEY_CALABRATION);
        Utils.updatePreferenceToSpecificActivityOrRemove(getActivity(), 
                getPreferenceScreen(), KEY_CALABRATION, 0);
        mAccelerometerCoordinate = (ListPreference) findPreference(KEY_ACCELEROMETER_COORDINATE);
        if(mAccelerometerCoordinate != null){
            mAccelerometerCoordinate.setOnPreferenceChangeListener(this);
            String value = Settings.System.getString(getContentResolver(),
                    Settings.System.ACCELEROMETER_COORDINATE);
            mAccelerometerCoordinate.setValue(value);
            updateAccelerometerCoordinateSummary(value);
        }
        mSmartBrightnessPreview = new CheckBoxPreference(this.getActivity());
        mSmartBrightnessPreview.setTitle(R.string.smart_brightness_preview);
        mSmartBrightnessPreview.setKey(KEY_SMART_BRIGHTNESS_PREVIEW);
        mSmartBrightness = (CheckBoxPreference)findPreference(KEY_SMART_BRIGHTNESS);
        mSmartBrightness.setOnPreferenceChangeListener(this);
        if(!getResources().getBoolean(R.bool.has_smart_brightness)){
            getPreferenceScreen().removePreference(mSmartBrightness) ;  
        }else{
            boolean enable = Settings.System.getInt(getContentResolver(),
                    Settings.System.SMART_BRIGHTNESS_ENABLE,0) != 0 ? true : false;
            mSmartBrightness.setChecked(enable);
            mSmartBrightnessPreview.setOnPreferenceChangeListener(this);
            if(enable){
                getPreferenceScreen().addPreference(mSmartBrightnessPreview);
            }
        }
        int hdmiMode = Settings.System.getInt(getContentResolver(), 
                    Settings.System.HDMI_OUTPUT_MODE,AUTO_HDMI_MODE);
        Log.d(TAG, "get hdmi value=" + hdmiMode);
        mHdmiOutputMode = (ListPreference) findPreference(KEY_HDMI_OUTPUT_MODE);
        mHdmiOutputMode.setValue(String.valueOf(hdmiMode));
        mHdmiOutputMode.setOnPreferenceChangeListener(this);
        if(!getResources().getBoolean(R.bool.has_hdmi_output_mode)){
        	getPreferenceScreen().removePreference(mHdmiOutputMode);
        }
    }

    private void updateTimeoutPreferenceDescription(long currentTimeout) {
        ListPreference preference = mScreenTimeoutPreference;
        String summary;
        if (currentTimeout < 0) {
            // Unsupported value
            summary = preference.getContext().getString(R.string.never_sleep);
        } else {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            int best = 0;
            for (int i = 0; i < values.length; i++) {
                long timeout = Long.parseLong(values[i].toString());
                if (currentTimeout == timeout) {
                    best = i;
                }
            }
            summary = preference.getContext().getString(R.string.screen_timeout_summary,
                    entries[best]);
        }
        preference.setSummary(summary);
    }

    private void disableUnusableTimeouts(ListPreference screenTimeoutPreference) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) getActivity().getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        final long maxTimeout = dpm != null ? dpm.getMaximumTimeToLock(null) : 0;
        if (maxTimeout == 0) {
            return; // policy not enforced
        }
        final CharSequence[] entries = screenTimeoutPreference.getEntries();
        final CharSequence[] values = screenTimeoutPreference.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.parseLong(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            screenTimeoutPreference.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            screenTimeoutPreference.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            final int userPreference = Integer.parseInt(screenTimeoutPreference.getValue());
            if (userPreference <= maxTimeout) {
                screenTimeoutPreference.setValue(String.valueOf(userPreference));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        screenTimeoutPreference.setEnabled(revisedEntries.size() > 0);
    }

    int floatToIndex(float val) {
        String[] indices = getResources().getStringArray(R.array.entryvalues_font_size);
        float lastVal = Float.parseFloat(indices[0]);
        for (int i=1; i<indices.length; i++) {
            float thisVal = Float.parseFloat(indices[i]);
            if (val < (lastVal + (thisVal-lastVal)*.5f)) {
                return i-1;
            }
            lastVal = thisVal;
        }
        return indices.length-1;
    }
    
    public void readFontSizePreference(ListPreference pref) {
        try {
            mCurConfig.updateFrom(ActivityManagerNative.getDefault().getConfiguration());
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to retrieve font size");
        }

        // mark the appropriate item in the preferences list
        int index = floatToIndex(mCurConfig.fontScale);
        pref.setValueIndex(index);

        // report the current size in the summary text
        final Resources res = getResources();
        String[] fontSizeNames = res.getStringArray(R.array.entries_font_size);
        pref.setSummary(String.format(res.getString(R.string.summary_font_size),
                fontSizeNames[index]));
    }
    
    @Override
    public void onResume() {
        super.onResume();

        updateState();
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), true,
                mAccelerometerRotationObserver);
    }

    @Override
    public void onPause() {
        super.onPause();

        getContentResolver().unregisterContentObserver(mAccelerometerRotationObserver);
    }

    private void updateState() {
        updateAccelerometerRotationCheckbox();
        readFontSizePreference(mFontSizePref);
        if(mAccelerometerCoordinate != null){
            updateAccelerometerCoordinateSummary(mAccelerometerCoordinate.getValue());
        }
    }

    private void updateAccelerometerRotationCheckbox() {
        mAccelerometer.setChecked(Settings.System.getInt(
                getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) != 0);
    }
    
    private void updateAccelerometerCoordinateSummary(Object value){       
        CharSequence[] summaries = getResources().getTextArray(R.array.accelerometer_summaries);
        CharSequence[] values = mAccelerometerCoordinate.getEntryValues();
        for (int i=0; i<values.length; i++) {
            if (values[i].equals(value)) {
                mAccelerometerCoordinate.setSummary(summaries[i]);
                break;
            }
        }
    }

    public void writeFontSizePreference(Object objValue) {
        try {
            mCurConfig.fontScale = Float.parseFloat(objValue.toString());
            ActivityManagerNative.getDefault().updatePersistentConfiguration(mCurConfig);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to save font size");
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mAccelerometer) {
            try {
                IWindowManager wm = IWindowManager.Stub.asInterface(
                        ServiceManager.getService(Context.WINDOW_SERVICE));
                if (mAccelerometer.isChecked()) {
                    wm.thawRotation();
                } else {
                    wm.freezeRotation(Surface.ROTATION_0);
                }
            } catch (RemoteException exc) {
                Log.w(TAG, "Unable to save auto-rotate setting");
            }
        } else if (preference == mNotificationPulse) {
            boolean value = mNotificationPulse.isChecked();
            Settings.System.putInt(getContentResolver(), Settings.System.NOTIFICATION_LIGHT_PULSE,
                    value ? 1 : 0);
            return true;
        } else if (preference == mCalibration ){
            return false;
        }        
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_SCREEN_TIMEOUT.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            try {
                Settings.System.putInt(getContentResolver(), SCREEN_OFF_TIMEOUT, value);
                updateTimeoutPreferenceDescription(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen timeout setting", e);
            }
        }
        if (KEY_FONT_SIZE.equals(key)) {
            writeFontSizePreference(objValue);
        }if (KEY_ACCELEROMETER_COORDINATE.equals(key))
        {
            String value = String.valueOf(objValue);
            try {
                Settings.System.putString(getContentResolver(), 
                        Settings.System.ACCELEROMETER_COORDINATE, value);
                updateAccelerometerCoordinateSummary(objValue);
            }catch (NumberFormatException e) {
                Log.e(TAG, "could not persist key accelerometer coordinate setting", e);
            }
        }
        if (KEY_SMART_BRIGHTNESS.equals(key)){
            //
            int value = (Boolean)objValue == true ? 1 : 0;
            Settings.System.putInt(getContentResolver(), 
                    Settings.System.SMART_BRIGHTNESS_ENABLE, value);
            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);;
            pm.setWiseBacklightMode(value);
            if((Boolean)objValue){
                getPreferenceScreen().addPreference(mSmartBrightnessPreview);
            }else{
            	getPreferenceScreen().removePreference(mSmartBrightnessPreview);
            	mSmartBrightnessPreview.setChecked(false);
            }
        }
        if(KEY_SMART_BRIGHTNESS_PREVIEW.equals(key)){
            int value = (Boolean)objValue == true ? 0x11 : 0x10;
            Settings.System.putInt(getContentResolver(), 
                    Settings.System.SMART_BRIGHTNESS_PREVIEW_ENABLE, value);
            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);;
            pm.setWiseBacklightMode(value);

        }
        if(KEY_HDMI_OUTPUT_MODE.equals(key)){
            int value = Integer.parseInt((String)objValue);
            Settings.System.putInt(getContentResolver(), Settings.System.HDMI_OUTPUT_MODE, value);
            DisplayManager dm = (DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
            int maxhdmimode = dm.getMaxHdmiMode();
            Log.d(TAG, "save hdmi value=" + value);
            if (MIN_HDMI_MODE <= value && value < AUTO_HDMI_MODE) {
                dm.setDisplayOutputType(1, DisplayManager.DISPLAY_OUTPUT_TYPE_HDMI, value);
            } else if (value == AUTO_HDMI_MODE) {
                dm.setDisplayOutputType(1, DisplayManager.DISPLAY_OUTPUT_TYPE_HDMI, maxhdmimode);
            } else {
                throw new IllegalArgumentException();
            }
        }
        return true;
    }
}
