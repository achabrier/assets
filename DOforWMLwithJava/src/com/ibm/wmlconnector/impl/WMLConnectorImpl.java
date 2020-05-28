package com.ibm.wmlconnector.impl;

import com.ibm.wmlconnector.WMLConnector;
import com.ibm.wmlconnector.WMLJob;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;
import java.util.*;


public class WMLConnectorImpl extends ConnectorImpl implements WMLConnector {

    private static final Logger LOGGER = Logger.getLogger(WMLConnectorImpl.class.getName());
    private static final boolean LOG = false;


    String url;
    String instance_id;


    public WMLConnectorImpl(String url, String instance_id, String apikey) {
        super(apikey);
        this.url = url;
        this.instance_id = instance_id;
        lookupBearerToken();
    }


    class WMLJobImpl implements WMLJob {
        String deployment_id;
        String job_id;
        JSONObject status = null;
        WMLJobImpl(String deployment_id, String job_id) {
            this.deployment_id = deployment_id;
            this.job_id = job_id;
        }

        @Override
        public String getId() {
            return job_id;
        }

        @Override
        public void updateStatus() {
            try {

                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("Accept", "application/json");
                headers.put("Authorization", "bearer " + bearerToken);
                headers.put("ML-Instance-ID", instance_id);
                headers.put("cache-control", "no-cache");

                String res = doGet(url + "/v4/jobs/" + job_id, headers);

                status = new JSONObject(res);

            } catch (JSONException e) {
                LOGGER.severe("Error updateStatus: " + e);
            }

        }

        @Override
        public JSONObject getStatus() {
            return status;
        }

        @Override
        public String getState() {
            return status.getJSONObject("entity").getJSONObject("decision_optimization").getJSONObject("status").getString("state");
        }

        @Override
        public boolean hasSolveState() {
            return status.getJSONObject("entity").getJSONObject("decision_optimization").has("solve_state");
        }

        @Override
        public boolean hasSolveStatus() {
            return status.getJSONObject("entity").getJSONObject("decision_optimization").getJSONObject("solve_state").has("solve_status");
        }

        @Override
        public String getSolveStatus() {
            return status.getJSONObject("entity").getJSONObject("decision_optimization").getJSONObject("solve_state").getString("solve_status");
        }

        @Override
        public boolean hasLatestEngineActivity() {
            return status.getJSONObject("entity").getJSONObject("decision_optimization").getJSONObject("solve_state").has("latest_engine_activity");
        }

        @Override
        public String getLatestEngineActivity() {
            JSONArray lines = status.getJSONObject("entity").getJSONObject("decision_optimization").getJSONObject("solve_state").getJSONArray("latest_engine_activity");
            String log = "";
            for (Iterator<Object> it = lines.iterator(); it.hasNext(); )
                log += (String)it.next() + "\n";
            return log;
        }

        @Override
        public HashMap<String, Object> getKPIs() {
            JSONObject details = status.getJSONObject("entity").getJSONObject("decision_optimization").getJSONObject("solve_state").getJSONObject("details");
            Iterator<String> keys = details.keys();

            HashMap<String, Object> kpis = new LinkedHashMap<String, Object>();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.startsWith("KPI.")) {
                    String kpi = key.substring(4);
                    kpis.put(kpi, details.get(key));
                }
            }

            return kpis;
        }

        @Override
        public JSONArray extractOutputData() {
            try {
                JSONArray output_data = status.getJSONObject("entity").getJSONObject("decision_optimization").getJSONArray("output_data");
                return output_data;

            } catch (JSONException e) {
                LOGGER.severe("Error extractSolution: " + e);
            }
            return null;
        }

        @Override
        public void delete() {
            try {

                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("Accept", "application/json");
                headers.put("Authorization", "bearer " + bearerToken);
                headers.put("ML-Instance-ID", instance_id);
                headers.put("cache-control", "no-cache");

                doDelete(url + "/v4/jobs/" + job_id + "?hard_delete=true", headers);

            } catch (JSONException e) {
                LOGGER.severe("Error updateStatus: " + e);
            }
        }
    }

    @Override
    public WMLJob createJob(String deployment_id,
                            JSONArray input_data,
                            JSONArray input_data_references,
                            JSONArray output_data,
                            JSONArray output_data_references) {
        if (LOG)
            LOGGER.info("Create job");

        try {
            JSONObject payload = new JSONObject();

            JSONObject deployment = new JSONObject();
            deployment.put("href","/v4/deployments/"+deployment_id);
            payload.put("deployment", deployment);

            JSONObject decision_optimization = new JSONObject();
            JSONObject solve_parameters = new JSONObject();
            solve_parameters.put("oaas.logAttachmentName", "log.txt");
            solve_parameters.put("oaas.logTailEnabled", "true");
            solve_parameters.put("oaas.resultsFormat", "JSON");
            decision_optimization.put("solve_parameters", solve_parameters);

            if (input_data != null)
                decision_optimization.put("input_data", input_data);

            if (input_data_references != null)
                decision_optimization.put("input_data_references", input_data_references);


            if (output_data != null)
                decision_optimization.put("output_data", output_data);

            if ((output_data == null) && (output_data_references == null)) {
                output_data = new JSONArray();
                JSONObject outcsv = new JSONObject();
                outcsv.put("id", ".*\\.csv");
                output_data.put(outcsv);
                JSONObject outtxt = new JSONObject();
                outtxt.put("id", ".*\\.txt");
                output_data.put(outtxt);
                JSONObject outjson = new JSONObject();
                outjson.put("id", ".*\\.json");
                output_data.put(outjson);
                decision_optimization.put("output_data", output_data);
            }

            if (output_data_references != null)
                decision_optimization.put("output_data_references", output_data_references);


            payload.put("decision_optimization", decision_optimization);

            HashMap<String, String> headers = new HashMap<String, String>();
            headers.put("Accept", "application/json");
            headers.put("Authorization", "bearer " + bearerToken);
            headers.put("ML-Instance-ID", instance_id);
            headers.put("cache-control", "no-cache");
            headers.put("Content-Type", "application/json");

            String res = doPost(url + "/v4/jobs", headers, payload.toString());

            JSONObject json = new JSONObject(res);
            String jobId = (String)((JSONObject)json.get("metadata")).get("guid");

            if (LOG)
                LOGGER.info("job_id = "+ jobId);

            return new WMLJobImpl(deployment_id, jobId);


        } catch (JSONException e) {
            LOGGER.severe("Error CreateJob: " + e);
        }

        return null;
    }

    @Override
    public WMLJob createAndRunJob(String deployment_id,
                                  JSONArray input_data,
                                  JSONArray input_data_references,
                                  JSONArray output_data,
                                  JSONArray output_data_references) {

        WMLJob job  = createJob(deployment_id, input_data, input_data_references, output_data, output_data_references);

        String state = null;
        do {
            try {
                Thread.sleep(500);
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            job.updateStatus();

            try {
                state = job.getState();
                if (job.hasSolveState()) {

                    if (LOG) {
                        if (job.hasSolveStatus())
                            LOGGER.info("Solve Status : " + job.getSolveStatus());
                        if (job.hasLatestEngineActivity())
                            LOGGER.info("Latest Engine Activity : " + job.getLatestEngineActivity());

                        HashMap<String, Object> kpis = job.getKPIs();

                        Iterator<String> keys = kpis.keySet().iterator();

                        while (keys.hasNext()) {
                            String kpi = keys.next();
                            LOGGER.info("KPI: " + kpi + " = " + kpis.get(kpi));
                        }
                    }
                }
            } catch (JSONException e) {
                LOGGER.severe("Error extractState: " + e);
            }

            if (LOG)
                LOGGER.info("Job State: " + state);
        } while (!state.equals("completed") && !state.equals("failed"));

        if (state.equals("failed")) {
            LOGGER.severe("Job failed.");
            LOGGER.severe("Job status:" + job.getStatus());
        } else {
            output_data = job.extractOutputData();
            if (LOG)
                LOGGER.info("output_data = " + output_data);
        }

        return job;
    }

    @Override
    public String createNewModel(String modelName, ModelType type, String modelAssetFilePath) {
        return this.createNewModel(modelName, type, modelAssetFilePath, WMLConnector.Runtime.DO_12_10);
    }

    @Override
    public String createNewModel(String modelName, ModelType type, String modelAssetFilePath, Runtime runtime) {

        String iamToken = getBearerToken();
        String modelId = null;
        {
            HashMap<String, String> headers = new HashMap<String, String>();
            headers.put("Authorization", "bearer " + bearerToken);
            headers.put("ML-Instance-ID", instance_id);
            headers.put("cache-control", "no-cache");
            headers.put("Content-Type", "application/json");

            JSONObject payload = new JSONObject("{\"name\":\""+modelName+"\", \"description\":\""+modelName+"\", \"type\":\""+type+"\",\"runtime\": {\"href\":\""+runtime+"\"}}");

            String res = doPost(url + "/v4/models", headers, payload.toString());

            JSONObject json = new JSONObject(res);
            modelId = json.getJSONObject("metadata").getString("guid");
            String modelHref = json.getJSONObject("metadata").getString("href");

        }

        {
            HashMap<String, String> headers = new HashMap<String, String>();
            headers.put("Authorization", "bearer " + bearerToken);
            headers.put("ML-Instance-ID", instance_id);
            headers.put("cache-control", "no-cache");


            if (modelAssetFilePath != null) {
                byte[] bytes = getBinaryFileContent(modelAssetFilePath);

                doPut(url + "/v4/models/" + modelId + "/content", headers, bytes);
            }
        }

        return modelId;
    }

    @Override
    public String getModelHref(String modelId, boolean displayModel)  {

        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "bearer " + bearerToken);
        headers.put("ML-Instance-ID", instance_id);
        headers.put("cache-control", "no-cache");
        headers.put("Content-Type", "application/json");

        String res = doGet(url + "/v4/models/"+ modelId, headers);

        JSONObject json = new JSONObject(res);
        String modelHref = json.getJSONObject("metadata").getString("href");

        return modelHref;
    }

    @Override
    public String deployModel(String deployName, String modelHref, TShirtSize size, int nodes) {

        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "bearer " + bearerToken);
        headers.put("ML-Instance-ID", instance_id);
        headers.put("cache-control", "no-cache");
        headers.put("Content-Type", "application/json");

        JSONObject payload = new JSONObject("{\"name\":\""+deployName+"\", \"asset\": { \"href\": \""+modelHref+"\"  }, \"batch\": {}, \"compute\" : { \"name\" : \""+size+"\", \"nodes\" : "+nodes+" }}");

        String res = doPost(url + "/v4/deployments", headers, payload.toString());

        JSONObject json = new JSONObject(res);
        String deployment_id = json.getJSONObject("metadata").getString("guid");

        return deployment_id;
    }

    public void deleteDeployment(String deployment_id) {

        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "bearer " + bearerToken);
        headers.put("ML-Instance-ID", instance_id);
        headers.put("cache-control", "no-cache");
        headers.put("Content-Type", "application/json");

        String res = doDelete(url + "/v4/deployments/" + deployment_id, headers);

    }

}
