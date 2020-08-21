package com.trip2.bglocation.data.preferences;

import com.trip2.bglocation.entities.SessionData;

import org.json.JSONException;
import org.json.JSONObject;

public interface ITrackerPreferences {

    void save(SessionData sessionData);

    void setDriverStatus(JSONObject driverStatus);

    JSONObject getDriverStatus() throws JSONException;

    void clearData();

    SessionData getSessionData();
}
