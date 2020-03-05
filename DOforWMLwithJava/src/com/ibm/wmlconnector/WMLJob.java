package com.ibm.wmlconnector;

import org.json.JSONArray;

import java.util.HashMap;

public interface WMLJob {
    public void updateStatus();
    public String getState();
    public boolean hasSolveState();
    public HashMap<String, Object> getKPIs();
    public JSONArray extractOutputData();
}
