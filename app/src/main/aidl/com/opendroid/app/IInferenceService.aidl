package com.opendroid.app;

import com.opendroid.app.IInferenceCallback;

interface IInferenceService {
    void inferAsync(String requestJson, IInferenceCallback callback);
    String inferSync(String requestJson);
    void cancelInference();
    String getServiceStatus();
}
