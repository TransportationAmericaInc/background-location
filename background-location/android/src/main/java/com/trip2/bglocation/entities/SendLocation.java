package com.trip2.bglocation.entities;

import com.google.gson.annotations.SerializedName;

public class SendLocation {

    @SerializedName("driverid")
    String DriverId;
    @SerializedName("latitude")
    Double Latitude;
    @SerializedName("longitude")
    Double Longitude;
    @SerializedName("speed")
    float Speed;

    public SendLocation() {
        super();
    }

    public String getDriverId() {
        return DriverId;
    }

    public void setDriverId(String driverId) {
        DriverId = driverId;
    }

    public Double getLatitude() {
        return Latitude;
    }

    public void setLatitude(Double latitude) {
        Latitude = latitude;
    }

    public Double getLongitude() {
        return Longitude;
    }

    public void setLongitude(Double longitude) {
        Longitude = longitude;
    }

    public float getSpeed() {
        return Speed;
    }

    public void setSpeed(float speed) {
        Speed = speed;
    }
}
