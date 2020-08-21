package com.trip2.bglocation.data.cloud;

import com.trip2.bglocation.entities.SendLocation;
import com.trip2.bglocation.external.ResponseMessage;

public interface ICloudDataSource {

    void sendLocationTracker(String url, String token, SendLocation sendLocation);

    interface sendLocationTrackerCallback {
        void onSuccess(ResponseMessage message);
        void onFailed(String error);
    }
}
