package com.trip2.bglocation;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.ui.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.trip2.bglocation.constants.Constant;
import com.trip2.bglocation.data.preferences.ITrackerPreferences;
import com.trip2.bglocation.data.preferences.TrackerPreferences;
import com.trip2.bglocation.entities.SessionData;
import com.trip2.bglocation.services.TrackerService;

import java.util.Timer;
import java.util.TimerTask;

@NativePlugin(
        permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION
        }
)
public class T2BackgroundLocation extends Plugin implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String CLASS_NAME = T2BackgroundLocation.class.getName();
    private Context context;
    private Activity activity;
    private ITrackerPreferences preferences;

    private Timer timer;

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
    private PendingIntent pendingIntent;

    // Códigos de petición
    private static final int REQUEST_LOCATION = 1;
    private static final int REQUEST_CHECK_SETTINGS = 2;

    private static final long UPDATE_INTERVAL = 5000;
    private static final long UPDATE_FASTEST_INTERVAL = UPDATE_INTERVAL / 2;

//    @PluginMethod
//    public void echo(PluginCall call) {
//        String value = call.getString("value");
//
//        JSObject ret = new JSObject();
//        ret.put("value", value);
//        call.success(ret);
//    }

    @PluginMethod()
    public void stopBackgroundService(PluginCall call) {
        context = this.getContext();
        activity = getActivity();
        preferences = TrackerPreferences.getInstance(context);
        preferences.clearData();
        stopLocationUpdates();
        JSObject ret = new JSObject();
        ret.put("value", "Stop Service");
        call.resolve(ret);
    }

    @PluginMethod()
    public void startBackgroundService(PluginCall call) {
        context = this.getContext();
        activity = getActivity();
        preferences = TrackerPreferences.getInstance(context);
        requestChangeBatteryOptimizations();

        SessionData sessionData = new SessionData();
        sessionData.setDriverId(call.getString("driver_id"));
        sessionData.setDriverName(call.getString("driver_name"));
        sessionData.setVehicleId(call.getString("vehicle_id"));
        sessionData.setVehicleName(call.getString("vehicle_name"));
        sessionData.setRouteId(call.getString("route_id"));
        sessionData.setRouteName(call.getString("route_name"));
        sessionData.setSocketUrl(call.getString("socket_url"));
        sessionData.setEventNewPosition(call.getString("event_new_position"));
        sessionData.setToken(call.getString("token"));

        preferences.save(sessionData);

        if (networkStatus()) {
            if (timer == null) {
                timer = new Timer();
            }
            buildGoogleApiClient();
            createLocationRequest();
            buildLocationSettingsRequest();
            checkLocationSettings();
            startLocationsTimer();
        }

    }

    private void requestChangeBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = context.getPackageName();
            PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            assert pm != null;
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            } else {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                activity.startActivity(intent);
            }
        }
    }

    private boolean networkStatus() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        assert connectivityManager != null;
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            return true;
        } else {
            AlertDialog dialog = new AlertDialog.Builder(activity).create();
            dialog.setMessage("Please activate an internet connection!");
            dialog.setCancelable(false);
            dialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int buttonId) {
                }
            });
            dialog.show();
            return false;
        }
    }

    private synchronized void buildGoogleApiClient() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(context)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .enableAutoManage((FragmentActivity) activity, this)
                    .build();
        }
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest()
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(UPDATE_FASTEST_INTERVAL)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest)
                .setAlwaysShow(true);
        mLocationSettingsRequest = builder.build();
    }

    private void checkLocationSettings() {
        LocationServices.getSettingsClient(context).checkLocationSettings(mLocationSettingsRequest)
                .addOnCompleteListener(new OnCompleteListener<LocationSettingsResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<LocationSettingsResponse> task) {
                        try {
                            LocationSettingsResponse response = task.getResult(ApiException.class);
                            if (isLocationPermissionGranted()) {
                                startLocationUpdates();
                            }
                        } catch (ApiException ex) {
                            switch (ex.getStatusCode()) {
                                // Location settings are not satisfied. But could be fixed by showing the
                                // user a dialog.
                                case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                    try {
                                        ResolvableApiException resolvable = (ResolvableApiException) ex;
                                        resolvable.startResolutionForResult(
                                                activity,
                                                REQUEST_CHECK_SETTINGS
                                        );
                                    } catch (IntentSender.SendIntentException e) {
                                    } catch (ClassCastException e) {
                                    }
                                    break;
                                case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                    // Location settings are not satisfied. However, we have no way to fix the
                                    // settings so we won't show the dialog.
                                    break;
                            }
                        }
                    }
                });
    }

    private boolean isLocationPermissionGranted() {
        int permission = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permission == PackageManager.PERMISSION_GRANTED;
    }

    private void startLocationsTimer() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Log.d(CLASS_NAME, "Init timer locations");
                startLocationUpdates();
                Log.d(CLASS_NAME, "Stop timer locations");
            }
        };
        timer.schedule(task, 10000L);
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        Intent intent = new Intent(context, TrackerService.class);
        intent.setAction(Constant.START_FOREGROUND_ACTION);
        pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        LocationServices.getFusedLocationProviderClient(context).requestLocationUpdates(
                mLocationRequest, pendingIntent
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private void stopLocationUpdates() {
        Intent intent = new Intent(context, TrackerService.class);
//        PendingIntent pendingIntent = PendingIntent.getService(context, 0,
//                intent, PendingIntent.FLAG_UPDATE_CURRENT);

        LocationServices.getFusedLocationProviderClient(context)
                .removeLocationUpdates(pendingIntent);

        intent.setAction(Constant.STOP_FOREGROUND_ACTION);
        context.startService(intent);
        mGoogleApiClient.disconnect();
        mGoogleApiClient.stopAutoManage((FragmentActivity) activity);
        mGoogleApiClient = null;
        mLocationSettingsRequest = null;
        mLocationRequest = null;
        pendingIntent = null;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (isLocationPermissionGranted()) {
            // Obtenemos la última ubicación al ser la primera vez
            //processLastLocation();
            // Iniciamos las actualizaciones de ubicación
            startLocationUpdates();
        } else {
            manageDeniedPermission();
        }
    }

    private void manageDeniedPermission() {
        pluginRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION, REQUEST_LOCATION);
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.show(context, "Code connection error:" + connectionResult.getErrorCode());
    }
}
