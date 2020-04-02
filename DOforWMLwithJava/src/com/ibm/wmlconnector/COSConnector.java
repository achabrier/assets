package com.ibm.wmlconnector;

import org.json.JSONObject;

public interface COSConnector {
    public void lookupBearerToken();
    public JSONObject getDataReferences(String id);
    public void putFile(String fileName, String fileContent);
    public void putBinaryFile(String fileName, String fileContent);
    public String getFile(String fileName);
}
