package com.ibm.wmlconnector;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;

public interface WMLJob {
    public void updateStatus();
    public String getId();
    public JSONObject getStatus();
    public String getState();
    public boolean hasSolveState();
    public boolean hasSolveStatus();
    public String getSolveStatus();
    public boolean hasLatestEngineActivity();
    public String getLatestEngineActivity();
    public HashMap<String, Object> getKPIs();
    public JSONArray extractOutputData();
    public void delete();
}

