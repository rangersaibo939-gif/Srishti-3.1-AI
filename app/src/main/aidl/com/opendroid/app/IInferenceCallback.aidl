package com.opendroid.app;

interface IInferenceCallback {
    void onToken(String token);
    void onComplete(String responseJson);
    void onError(int errorCode, String errorMessage);
}
