package com.ibm.wmlconnector;

import org.json.JSONObject;

public interface COSConnector {
    public void lookupBearerToken();
    public JSONObject getOutputDataReferences(String id);
    public String getFile(String fileName);
}
