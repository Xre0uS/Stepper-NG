package com.xreous.stepperng.step;

import burp.api.montoya.http.message.HttpRequestResponse;

public class StepExecutionInfo {
    private Step step;
    private long responseTime;
    private HttpRequestResponse requestResponse;

    public StepExecutionInfo(Step step, HttpRequestResponse requestResponse, long responseTime){
        this.step = step;
        this.requestResponse = requestResponse;
        this.responseTime = responseTime;
    }

    public Step getStep() {
        return step;
    }

    public long getResponseTime() {
        return responseTime;
    }

    public HttpRequestResponse getRequestResponse() {
        return requestResponse;
    }
}
