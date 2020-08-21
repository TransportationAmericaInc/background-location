package com.trip2.bglocation.external;

import com.google.gson.annotations.SerializedName;

public class ResponseMessage {
    @SerializedName("message")
    private String message;

    public ResponseMessage(String iMessage) {
        this.message = iMessage;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String iMessage) {
        this.message = iMessage;
    }
}
