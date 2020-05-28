package com.ibm;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

import com.ibm.wmlconnector.COSConnector;
import com.ibm.wmlconnector.WMLConnector;
import com.ibm.wmlconnector.WMLJob;
import com.ibm.wmlconnector.impl.COSConnectorImpl;
import com.ibm.wmlconnector.impl.WMLConnectorImpl;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.ibm.wmlconnector.WMLConnector.ModelType.CPLEX_12_9;

public class Sample {
    private static final Logger LOGGER = Logger.getLogger(Sample.class.getName());

    public static String getFileContent(String inputFilename)  {
        String res = "";
        try {
            List<String> lines = Files.readAllLines(Paths.get(inputFilename));
            for (Iterator<String> it = lines.iterator(); it.hasNext();)
                res += it.next() + "\n";
        } catch (IOException e) {
            LOGGER.severe("Error getting file" + e.getStackTrace());
        }

        return res;
    }

    public static byte[] getFileContentAsBytes(String inputFilename)  {
        byte[] bytes = null;
        try {
            bytes = Files.readAllBytes(Paths.get(inputFilename));
        } catch (IOException e) {
            LOGGER.severe("Error getting file" + e.getStackTrace());
        }
        return bytes;
    }


    private static JSONObject createDataFromCSV(String fileName) {

        JSONObject data = new JSONObject();
        data.put("id", fileName);

        JSONArray fields = new JSONArray();
        JSONArray all_values = new JSONArray();
        String file = getFileContent("src/resources/"+fileName);
        String[] lines = file.split("\n");
        int nlines = lines.length;
        String[] fields_array = lines[0].split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        int nfields = fields_array.length;
        for (int i=0; i<nfields; i++) {
            String field = fields_array[i];
            if (field.charAt(0) == '"')
                field = field.substring(1);
            if  (field.charAt(field.length()-1) == '"')
                field = field.substring(0, field.length()-1);
            fields.put(field);
        }
        data.put("fields", fields);

        for (int i = 1; i<nlines; i++) {
            JSONArray values = new JSONArray();
            String[] values_array = lines[i].split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (int j=0; j<nfields; j++) {
                String value = values_array[j];
                if (value.charAt(0) == '"')
                    value = value.substring(1);
                if (value.charAt(value.length() - 1) == '"')
                    value = value.substring(0, value.length() - 1);

                try {
                    int ivalue = Integer.parseInt(value);
                    values.put(ivalue);
                } catch (NumberFormatException e) {
                    try {
                        double dvalue = Double.parseDouble(value);
                        values.put(dvalue);
                    } catch (NumberFormatException e2) {
                        values.put(value);
                    }
                }
            }
            all_values.put(values);
        }
        data.put("values", all_values);
        return data;
    }


    private static JSONObject createDataFromFile(String fileName) {

        byte[] bytes = getFileContentAsBytes("src/resources/"+fileName);
        byte[] encoded = Base64.getEncoder().encode(bytes);

        JSONObject data = new JSONObject();
        data.put("id", fileName);

        JSONArray fields = new JSONArray();
        fields.put("___TEXT___");
        data.put("fields", fields);

        JSONArray values = new JSONArray();
        values.put(new JSONArray().put(new String(encoded)));
        data.put("values", values);

        return data;
    }

    public String createAndDeployEmptyCPLEXModel(WMLConnector wml) {
        return createAndDeployEmptyModel(wml, WMLConnector.ModelType.CPLEX_12_9, WMLConnector.TShirtSize.S, 1);
    }

    public String createAndDeployEmptyCPLEXModel(WMLConnector wml, int nodes) {
        return createAndDeployEmptyModel(wml, WMLConnector.ModelType.CPLEX_12_9, WMLConnector.TShirtSize.S, nodes);
    }

    public String createAndDeployEmptyModel(WMLConnector wml, WMLConnector.ModelType type, WMLConnector.TShirtSize size, int nodes) {

        LOGGER.info("Create Empty " + type + " Model");

        String model_id = wml.createNewModel("EmptyCPLEXModel",type, null);
        LOGGER.info("model_id = "+ model_id);

        String deployment_id = wml.deployModel("empty-cplex-test-wml-2", wml.getModelHref(model_id, false), size, nodes);
        LOGGER.info("deployment_id = "+ deployment_id);

        return deployment_id;
    }

    public String createAndDeployDietPythonModel(WMLConnector wml) {

        LOGGER.info("Create Python Model");

        String model_id = wml.createNewModel("Diet", WMLConnector.ModelType.DOCPLEX_12_10,"src/resources/diet.zip", WMLConnector.Runtime.DO_12_10);
        //String model_id = wml.createNewModel("Diet","do-docplex_12.9","src/resources/diet.zip", "/v4/runtimes/do_12.9");
        LOGGER.info("model_id = "+ model_id);

        String deployment_id = wml.deployModel("diet-test-wml-2", wml.getModelHref(model_id, false),WMLConnector.TShirtSize.M,1);
        LOGGER.info("deployment_id = "+ deployment_id);

        return deployment_id;
    }

    public void deleteDeployment(WMLConnector wml, String deployment_id) {
        LOGGER.info("Delete deployment");

        wml.deleteDeployment(deployment_id);
    }

    public void fullDietPythonFlow(boolean useOutputDataReferences, int nJobs) {

        LOGGER.info("Full flow with Diet");

        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);
        String deployment_id = createAndDeployDietPythonModel(wml);
        JSONArray input_data = new JSONArray();
        input_data.put(createDataFromCSV("diet_food.csv"));
        input_data.put(createDataFromCSV("diet_food_nutrients.csv"));
        input_data.put(createDataFromCSV("diet_nutrients.csv"));
        JSONArray output_data_references = null;
        COSConnector cos = null;
        if (useOutputDataReferences) {
            cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
            output_data_references = new JSONArray();
            output_data_references.put(cos.getDataReferences("log.txt"));
        }
        long startTime = System.nanoTime();
        for (int i=0; i<nJobs; i++) {
            WMLJob job = wml.createAndRunJob(deployment_id, input_data, null, null, output_data_references);
            if (useOutputDataReferences) {
                getLogFromCOS(cos); // Don't log
            } else {
                getLogFromJob(job); // Don't log
            }
            long endTime   = System.nanoTime();
            long totalTime = endTime - startTime;
            LOGGER.info("Total time: " + (totalTime/1000000000.));
            startTime = System.nanoTime();
        }
        deleteDeployment(wml, deployment_id);
    }

    public void fullLPFLow(String filename) {
        LOGGER.info("Create and authenticate WML Connector");
        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);
        String deployment_id = createAndDeployEmptyCPLEXModel(wml);

        COSConnector cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
        cos.putFile(filename, "src/resources/"+filename);
        JSONArray input_data_references = new JSONArray();
        input_data_references.put(cos.getDataReferences(filename));
        JSONArray output_data_references = new JSONArray();
        output_data_references.put(cos.getDataReferences("solution.json"));
        output_data_references.put(cos.getDataReferences("log.txt"));

        wml.createAndRunJob(deployment_id, null, input_data_references, null, output_data_references);
        LOGGER.info("Log:" + getLogFromCOS(cos));
        LOGGER.info("Solution:" + getSolutionFromCOS(cos));
        deleteDeployment(wml, deployment_id);
    }

    public void deleteLPJob(String filename) {
        LOGGER.info("Create and authenticate WML Connector");
        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);
        String deployment_id = createAndDeployEmptyCPLEXModel(wml);

        COSConnector cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
        cos.putFile(filename, "src/resources/"+filename);
        JSONArray input_data_references = new JSONArray();
        input_data_references.put(cos.getDataReferences(filename));
        JSONArray output_data_references = new JSONArray();
        output_data_references.put(cos.getDataReferences("solution.json"));
        output_data_references.put(cos.getDataReferences("log.txt"));

        WMLJob job = wml.createJob(deployment_id, null, input_data_references, null, output_data_references);
        job.delete();
        deleteDeployment(wml, deployment_id);
    }

    public void parallelFullLPInlineFlow(String filename, int nodes, int nJobs) {

        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);
        String deployment_id = createAndDeployEmptyCPLEXModel(wml, nodes);

        long startTime = System.nanoTime();
        List<WMLJob> jobs = new ArrayList<WMLJob>();
        JSONArray input_data = new JSONArray();
        input_data.put(createDataFromFile(filename));
        for (int i=0; i<nJobs; i++) {
            WMLJob job = wml. createJob(deployment_id, input_data, null, null, null);
            jobs.add(job);
        }
        long endTime   = System.nanoTime();
        long totalTime = endTime - startTime;
        LOGGER.info("Total create job time: " + (totalTime/1000000000.));
        startTime = System.nanoTime();

        while (!jobs.isEmpty()) {
            LOGGER.info("Number of jobs " + jobs.size());
            try {
                Thread.sleep(500);
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            int n = jobs.size();
            for (int j=n-1; j>=0; j--) {
                WMLJob job = jobs.get(j);
                job.updateStatus();
                String state = job.getState();
                LOGGER.info("Job " + job.getId() + ": " + state);
                if (state.equals("completed") || state.equals("failed")) {
                    jobs.remove(job);
                }
            }
        }
        endTime   = System.nanoTime();
        totalTime = endTime - startTime;
        LOGGER.info("Total time: " + (totalTime/1000000000.));
        LOGGER.info("Per instance: " + (totalTime/1000000000.)/nJobs);
        startTime = System.nanoTime();

        //deleteDeployment(wml, deployment_id);
    }

    public void fullLPInlineFLow(String filename, int nJobs) {

        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);
        String deployment_id = createAndDeployEmptyCPLEXModel(wml);

        long startTime = System.nanoTime();
        for (int i=0; i<nJobs; i++) {
            JSONArray input_data = new JSONArray();
            input_data.put(createDataFromFile(filename));
            WMLJob job = wml.createAndRunJob(deployment_id, input_data, null, null, null);
            getLogFromJob(job); // don't log
            getSolutionFromJob(job); // don'tlog
            long endTime   = System.nanoTime();
            long totalTime = endTime - startTime;
            LOGGER.info("Total time: " + (totalTime/1000000000.));
            startTime = System.nanoTime();
        }
        deleteDeployment(wml, deployment_id);
    }


    public String getLogFromJob(WMLJob job) {
        JSONArray output_data = job.extractOutputData();
        for (Iterator<Object> it = output_data.iterator(); it.hasNext(); ) {
            JSONObject o = (JSONObject)it.next();
            if (o.getString("id").equals("log.txt")) {
                byte[] encoded = new byte[0];
                try {
                    encoded = o.getJSONArray("values").getJSONArray(0).getString(0).getBytes("UTF-8");
                    byte[] decoded = Base64.getDecoder().decode(encoded);
                    String log = new String(decoded, "UTF-8");
                    return log;

                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public String getSolutionFromJob(WMLJob job) {
        JSONArray output_data = job.extractOutputData();
        for (Iterator<Object> it = output_data.iterator(); it.hasNext(); ) {
            JSONObject o = (JSONObject)it.next();
            if (o.getString("id").equals("solution.json")) {
                byte[] encoded = new byte[0];
                try {
                    encoded = o.getJSONArray("values").getJSONArray(0).getString(0).getBytes("UTF-8");
                    byte[] decoded = Base64.getDecoder().decode(encoded);
                    String solution = new String(decoded, "UTF-8");
                    return solution;

                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }


    public String getLogFromCOS(COSConnector cos) {
        return getFileFromCOS(cos,"log.txt");
    }

    public String getSolutionFromCOS(COSConnector cos) {
        return getFileFromCOS(cos, "solution.json");
    }

    public String getFileFromCOS(COSConnector cos, String fileName) {
        String content = cos.getFile(fileName);
        content = content.replaceAll("\\r", "\n");
        return content;
    }

    public void fullInfeasibleLPFLow() {
        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);
        String deployment_id = createAndDeployEmptyCPLEXModel(wml);

        COSConnector cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
        cos.putFile("infeasible.lp", "src/resources/infeasible.lp");
        cos.putFile("infeasible.feasibility", "src/resources/infeasible.feasibility");
        JSONArray input_data_references = new JSONArray();
        input_data_references.put(cos.getDataReferences("infeasible.lp"));
        input_data_references.put(cos.getDataReferences("infeasible.feasibility"));
        JSONArray output_data_references = new JSONArray();
        output_data_references.put(cos.getDataReferences("log.txt"));
        output_data_references.put(cos.getDataReferences("conflict.json"));
        wml.createAndRunJob(deployment_id, null, input_data_references, null, output_data_references);
        LOGGER.info("Log:" + getLogFromCOS(cos));
        LOGGER.info("Conflict:" + getFileFromCOS(cos,"conflict.json"));

        deleteDeployment(wml, deployment_id);
    }


    public void runCPO(String modelName) {
        String deployment_id = Credentials.cpo_deployment_id;
        COSConnector cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
        cos.putFile(modelName + ".cpo", "src/resources/" + modelName + ".cpo");
        JSONArray input_data_references = new JSONArray();
        input_data_references.put(cos.getDataReferences(modelName + ".cpo"));
        JSONArray output_data_references = new JSONArray();
        output_data_references.put(cos.getDataReferences("log.txt"));
        output_data_references.put(cos.getDataReferences("solution.json"));
        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);
        wml.createAndRunJob(deployment_id, null, input_data_references, null, output_data_references);
        LOGGER.info("Log:" + getLogFromCOS(cos));
        LOGGER.info("Solution:" + getSolutionFromCOS(cos));
    }

    public String createAndDeployWarehouseOPLModel(WMLConnector wml) {

        LOGGER.info("Create Warehouse OPL Model");

        String model_id = wml.createNewModel("Warehouse", WMLConnector.ModelType.OPL_12_9,"src/resources/warehouse.zip");
        LOGGER.info("model_id = "+ model_id);

        String deployment_id = wml.deployModel("warehouse-opl-test-wml-2", wml.getModelHref(model_id, false), WMLConnector.TShirtSize.S,1);
        LOGGER.info("deployment_id = "+ deployment_id);

        return deployment_id;
    }

    public void fullWarehouseOPLFlow(boolean useOutputDataReferences) {

        LOGGER.info("Full Warehouse with OPL");

        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);
        String deployment_id = createAndDeployWarehouseOPLModel(wml);

        COSConnector cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
        cos.putFile("warehouse.dat", "src/resources/warehouse.dat");
        JSONArray input_data_references = new JSONArray();
        input_data_references.put(cos.getDataReferences("warehouse.dat"));
        JSONArray output_data_references = null;
        if (useOutputDataReferences) {
            output_data_references = new JSONArray();
            output_data_references.put(cos.getDataReferences("log.txt"));
        }

        WMLJob job = wml.createAndRunJob(deployment_id, null, input_data_references, null, output_data_references);
        if (useOutputDataReferences) {
            LOGGER.info("Log:" + getLogFromCOS(cos));
        } else {
            LOGGER.info("Log:" + getLogFromJob(job));
        }

        deleteDeployment(wml, deployment_id);
    }


    public String createAndDeployDietOPLModel(WMLConnector wml) {

        LOGGER.info("Create Diet OPL Model");

        String model_id = wml.createNewModel("Diet OPL", WMLConnector.ModelType.OPL_12_9,"src/resources/dietopl.zip");
        LOGGER.info("model_id = "+ model_id);

        String deployment_id = wml.deployModel("diet-opl-test-wml-2", wml.getModelHref(model_id, false), WMLConnector.TShirtSize.S,1);
        LOGGER.info("deployment_id = "+ deployment_id);

        return deployment_id;
    }

    public String createAndDeployDietMainOPLModel(WMLConnector wml) {

        LOGGER.info("Create Diet Main OPL Model");

        String model_id = wml.createNewModel("Diet Main OPL", WMLConnector.ModelType.OPL_12_9,"src/resources/dietoplmain.zip");
        LOGGER.info("model_id = "+ model_id);

        String deployment_id = wml.deployModel("diet-main-opl-test-wml-2", wml.getModelHref(model_id, false), WMLConnector.TShirtSize.S,1);
        LOGGER.info("deployment_id = "+ deployment_id);

        return deployment_id;
    }


    public void fullDietOPLWithDatFlow(boolean useOutputDataReferences) {

        LOGGER.info("Full Diet with OPL");
        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);

        String deployment_id = createAndDeployDietOPLModel(wml);
        COSConnector cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
        cos.putFile("diet.dat", "src/resources/diet.dat");
        //cos.putFile("dietxls.dat", "src/resources/dietxls.dat");
        //cos.putBinaryFile("diet.xlsx", "src/resources/diet.xlsx");
        JSONArray input_data_references = new JSONArray();
        input_data_references.put(cos.getDataReferences("diet.dat"));
        //input_data_references.put(cos.getDataReferences("dietxls.dat"));
        //input_data_references.put(cos.getDataReferences("diet.xlsx"));
        JSONArray output_data_references = null;
        if (useOutputDataReferences) {
            output_data_references = new JSONArray();
            output_data_references.put(cos.getDataReferences("log.txt"));
            output_data_references.put(cos.getDataReferences("solution.json"));
        } else {

        }

        WMLJob job = wml.createAndRunJob(deployment_id, null, input_data_references, null, output_data_references);
        if (useOutputDataReferences) {
            LOGGER.info("Log:" + getLogFromCOS(cos));
            LOGGER.info("Solution:" + getSolutionFromCOS(cos));
        } else {
            LOGGER.info("Log:" + getLogFromJob(job));
        }
        deleteDeployment(wml, deployment_id);
    }

    public void fullDietMainOPLWithDatFlow(boolean useOutputDataReferences) {

        LOGGER.info("Full Diet with Main OPL");
        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);

        String deployment_id = createAndDeployDietMainOPLModel(wml);
        COSConnector cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
        cos.putFile("diet.dat", "src/resources/diet.dat");
        JSONArray input_data_references = new JSONArray();
        input_data_references.put(cos.getDataReferences("diet.dat"));
        JSONArray output_data_references = null;
        if (useOutputDataReferences) {
            output_data_references = new JSONArray();
            output_data_references.put(cos.getDataReferences("log.txt"));
            output_data_references.put(cos.getDataReferences("solution.json"));
        } else {

        }

        WMLJob job = wml.createAndRunJob(deployment_id, null, input_data_references, null, output_data_references);
        if (useOutputDataReferences) {
            LOGGER.info("Log:" + getLogFromCOS(cos));
            LOGGER.info("Solution:" + getSolutionFromCOS(cos));
        } else {
            LOGGER.info("Log:" + getLogFromJob(job));
        }

        deleteDeployment(wml, deployment_id);
    }

    public void fullDietOPLWithCSVFlow(boolean useOutputDataReferences) {

        LOGGER.info("Full Diet with OPL");
        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);
        String deployment_id = createAndDeployDietOPLModel(wml);

        COSConnector cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
        JSONArray input_data = new JSONArray();
        input_data.put(createDataFromCSV("diet_food.csv"));
        input_data.put(createDataFromCSV("diet_food_nutrients.csv"));
        input_data.put(createDataFromCSV("diet_nutrients.csv"));
        JSONArray output_data_references = null;
        if (useOutputDataReferences) {
            output_data_references = new JSONArray();
            output_data_references.put(cos.getDataReferences("log.txt"));
            output_data_references.put(cos.getDataReferences("solution.json"));
        } else {

        }

        WMLJob job = wml.createAndRunJob(deployment_id, input_data, null, null, output_data_references);
        if (useOutputDataReferences) {
            LOGGER.info("Log:" + getLogFromCOS(cos));
            LOGGER.info("Solution:" + getSolutionFromCOS(cos));
        } else {
            LOGGER.info("Log:" + getLogFromJob(job));
        }

        deleteDeployment(wml, deployment_id);
    }

    public void fullOPLWithJSONFlow(boolean useOutputDataReferences) {

        LOGGER.info("Full JSON Test with OPL");

        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);

        String model_id = wml.createNewModel("JSON Test OPL", WMLConnector.ModelType.OPL_12_9,"src/resources/jsontest.zip");
        LOGGER.info("model_id = "+ model_id);

        String deployment_id = wml.deployModel("json-test-opl-test-wml-2", wml.getModelHref(model_id, false), WMLConnector.TShirtSize.S,1);
        LOGGER.info("deployment_id = "+ deployment_id);

        COSConnector cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
        cos.putFile("Nurses.json", "src/resources/Nurses.json");
        cos.putFile("spokes.json", "src/resources/spokes.json");
        JSONArray input_data_references = new JSONArray();
        input_data_references.put(cos.getDataReferences("Nurses.json"));
        input_data_references.put(cos.getDataReferences("spokes.json"));
        JSONArray output_data_references = null;
        if (useOutputDataReferences) {
            output_data_references = new JSONArray();
            output_data_references.put(cos.getDataReferences("log.txt"));
            output_data_references.put(cos.getDataReferences("solution.json"));
        } else {

        }
        WMLJob job = wml.createAndRunJob(deployment_id, null, input_data_references, null, output_data_references);
        if (useOutputDataReferences) {
            LOGGER.info("Log:" + getLogFromCOS(cos));
            LOGGER.info("Solution:" + getSolutionFromCOS(cos));
        } else {
            LOGGER.info("Log:" + getLogFromJob(job));
        }
        deleteDeployment(wml, deployment_id);
    }

    public void fullInfeasibleDietOPLFlow() {

        LOGGER.info("Full Infeasible Diet with OPL");
        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);
        String deployment_id = createAndDeployDietOPLModel(wml);

        COSConnector cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
        JSONArray input_data = null;
        JSONArray input_data_references = null;
        cos.putFile("infeasible_diet.dat", "src/resources/infeasible_diet.dat");
        cos.putFile("infeasible_diet.ops", "src/resources/infeasible_diet.ops");
        input_data_references = new JSONArray();
        input_data_references.put(cos.getDataReferences("infeasible_diet.dat"));
        input_data_references.put(cos.getDataReferences("infeasible_diet.ops"));

        JSONArray output_data_references = null;
        output_data_references = new JSONArray();
        output_data_references.put(cos.getDataReferences("log.txt"));
        output_data_references.put(cos.getDataReferences("solution.json"));

        WMLJob job = wml.createAndRunJob(deployment_id, input_data, input_data_references, null, output_data_references);
        LOGGER.info("Status:" + job.getStatus());
        LOGGER.info("Log:" + getLogFromCOS(cos));
        LOGGER.info("Solution:" + getSolutionFromCOS(cos));

        deleteDeployment(wml, deployment_id);
    }


    public static void main(String[] args) {
        Sample main = new Sample();


        // Python
        //main.fullDietPythonFlow(false, 100);

        // OPL
        //main.fullWarehouseOPLFlow(true);
        //main.fullDietOPLWithDatFlow(false);
        //main.fullDietOPLWithCSVFlow(false);

        //main.fullDietMainOPLWithDatFlow(false);
        //main.fullOPLWithJSONFlow(true);

        //KO main.fullInfeasibleDietOPLFlow();

        // CPLEX
        main.fullLPFLow("diet.lp");

        //main.fullLPInlineFLow("diet.lp", 100 );
        //main.parallelFullLPInlineFLow("diet.lp", 5, 100 );
        //main.fullLPInlineFLow("acc-tight4.lp", 20 );
        //main.parallelFullLPInlineFlow("acc-tight4.lp", 5, 100 );

//        main.fullInfeasibleLPFLow();


        // CPO
//        main.fullCPOFLow();
        //main.runCPO("colors");
        //main.runCPO("plant_location");

        // Other
        //main.deleteLPJob("diet.lp");
    }
}
