package com.ibm.wmlconnector;

import org.json.JSONArray;

public interface WMLConnector {
    public void lookupAsynchDearerToken();
    public String getBearerToken();
    public String createNewModel(String modelName, String type, String modelAssetFilePath );
    public String getModelHref(String modelId, boolean displayModel);
    public String deployModel(String deployName, String modelHref, String size, int nodes);
    public WMLJob createJob(String deployment_id, JSONArray input_data);
    public void deleteDeployment(String deployment_id);
}
