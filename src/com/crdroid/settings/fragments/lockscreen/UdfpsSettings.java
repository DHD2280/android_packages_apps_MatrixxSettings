/*
 * Copyright (C) 2022-2023 crDroid Android Project
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

package com.crdroid.settings.fragments.lockscreen;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.fingerprint.FingerprintManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.ListPreference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.Utils;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.crdroid.settings.fragments.lockscreen.UdfpsIconPicker;
import com.crdroid.settings.preferences.SystemSettingSwitchPreference;

import java.io.FileDescriptor;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UdfpsSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String UDFPS_ANIM_PREVIEW = "udfps_recognizing_animation_preview";
    private static final String SCREEN_OFF_UDFPS_ENABLED = "screen_off_udfps_enabled";
    private static final String CUSTOM_FOD_ICON_KEY = "custom_fp_icon_enabled";
    private static final String CUSTOM_FP_FILE_SELECT = "custom_fp_file_select";
    private static final int REQUEST_PICK_IMAGE = 0;

    private Preference mCustomFPImage;
    private SystemSettingSwitchPreference mCustomFodIcon;
    private Preference mUdfpsIconPicker;
    private Preference mUdfpsAnimPreview;
    private Preference mScreenOffUdfps;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.udfps_settings);

        final PreferenceScreen prefSet = getPreferenceScreen();
        Resources resources = getResources();

        /*
        final boolean udfpsResPkgInstalled = Utils.isPackageInstalled(getContext(),
                "com.crdroid.udfps.animations");
        mUdfpsAnimPreview = findPreference(UDFPS_ANIM_PREVIEW);
        if (!udfpsResPkgInstalled) {
            prefSet.removePreference(mUdfpsAnimPreview);
        }*/

        mScreenOffUdfps = (Preference) prefSet.findPreference(SCREEN_OFF_UDFPS_ENABLED);
        boolean screenOffUdfpsAvailable = resources.getBoolean(
                com.android.internal.R.bool.config_supportScreenOffUdfps) ||
                !TextUtils.isEmpty(resources.getString(
                    com.android.internal.R.string.config_dozeUdfpsLongPressSensorType));
        if (!screenOffUdfpsAvailable)
            prefSet.removePreference(mScreenOffUdfps);

        mUdfpsIconPicker = (Preference) prefSet.findPreference("udfps_icon_picker");

        mCustomFPImage = findPreference(CUSTOM_FP_FILE_SELECT);
        final String customIconURI = Settings.System.getString(getContext().getContentResolver(),
               Settings.System.OMNI_CUSTOM_FP_ICON);
        if (!TextUtils.isEmpty(customIconURI)) {
            setPickerIcon(customIconURI);
        }

        mCustomFodIcon = (SystemSettingSwitchPreference) findPreference(CUSTOM_FOD_ICON_KEY);
        boolean val = Settings.System.getIntForUser(getActivity().getContentResolver(),
                Settings.System.OMNI_CUSTOM_FP_ICON_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
        mCustomFodIcon.setOnPreferenceChangeListener(this);
        if (val) {
            mUdfpsIconPicker.setEnabled(false);
        } else {
            mUdfpsIconPicker.setEnabled(true);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mCustomFPImage) {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mCustomFodIcon) {
            boolean val = (Boolean) newValue;
            Settings.System.putIntForUser(getActivity().getContentResolver(),
                    Settings.System.OMNI_CUSTOM_FP_ICON_ENABLED, val ? 1 : 0,
                    UserHandle.USER_CURRENT);
            if (val) {
                mUdfpsIconPicker.setEnabled(false);
            } else {
                mUdfpsIconPicker.setEnabled(true);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent result) {
       if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
           Uri uri = null;
           if (result != null) {
               uri = result.getData();
               setPickerIcon(uri.toString());
               Settings.System.putString(getContentResolver(), Settings.System.OMNI_CUSTOM_FP_ICON,
                   uri.toString());
            }
        } else if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_CANCELED) {
            mCustomFPImage.setIcon(new ColorDrawable(Color.TRANSPARENT));
            Settings.System.putString(getContentResolver(), Settings.System.OMNI_CUSTOM_FP_ICON, "");
        }
    }

    private void setPickerIcon(String uri) {
        try {
                ParcelFileDescriptor parcelFileDescriptor =
                    getContext().getContentResolver().openFileDescriptor(Uri.parse(uri), "r");
                FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                parcelFileDescriptor.close();
                Drawable d = new BitmapDrawable(getResources(), image);
                mCustomFPImage.setIcon(d);
            }
            catch (Exception e) {}
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.UDFPS_ANIM, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.UDFPS_ANIM_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.UDFPS_ICON, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.SCREEN_OFF_UDFPS_ENABLED, 0, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
