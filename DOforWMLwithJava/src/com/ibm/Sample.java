package com.ibm;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import com.ibm.wmlconnector.COSConnector;
import com.ibm.wmlconnector.WMLJob;
import com.ibm.wmlconnector.impl.COSConnectorImpl;
import com.ibm.wmlconnector.impl.WMLConnectorImpl;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Sample {
    private static final Logger LOGGER = Logger.getLogger(Sample.class.getName());

    public static String getFileContent(String inputFilename)  {
        String res = "";
        try {
            List<String> lines = Files.readAllLines(Paths.get(inputFilename));
            for (Iterator<String> it = lines.iterator(); it.hasNext();)
                res += it.next() + "\n";
        } catch (IOException e) {
            LOGGER.severe("Error getting binary file" + e.getStackTrace());
        }

        return res;
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


    public WMLJob createAndRunJobOnExistingDeployment(String deployment_id,
                                                    JSONArray input_data,
                                                    JSONArray input_data_references,
                                                    JSONArray output_data,
                                                    JSONArray output_data_references) {

        LOGGER.info("Create and run job");

        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);
        WMLJob job  = wml.createJob(deployment_id, input_data, input_data_references, output_data, output_data_references);
        String state = null;
        do {
            try {
                Thread.sleep(1000);
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            job.updateStatus();

            try {
                state = job.getState();
                if (job.hasSolveState()) {

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
            } catch (JSONException e) {
                LOGGER.severe("Error extractState: " + e);
            }

            LOGGER.info("Job State: " + state);
        } while (!state.equals("completed") && !state.equals("failed"));

        if (state.equals("failed")) {
            LOGGER.severe("Job failed.");
            LOGGER.severe("Job status:" + job.getStatus());
        } else {
            output_data = job.extractOutputData();
            LOGGER.info("output_data = " + output_data);
        }

        return job;
    }

    public String createAndDeployEmptyCPOModel() {

        LOGGER.info("Create Empty CPO Model");

        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);

        String model_id = wml.createNewModel("EmptyCPOModel","do-cpo_12.9", null);
        LOGGER.info("model_id = "+ model_id);

        String deployment_id = wml.deployModel("empty-cpo-test-wml-2", wml.getModelHref(model_id, false),"S",1);
        LOGGER.info("deployment_id = "+ deployment_id);

        return deployment_id;
    }

    public String createAndDeployEmptyCPLEXModel() {

        LOGGER.info("Create Empty CPLEX Model");

        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);

        String model_id = wml.createNewModel("EmptyCPLEXModel","do-cplex_12.9", null);
        LOGGER.info("model_id = "+ model_id);

        String deployment_id = wml.deployModel("empty-cplex-test-wml-2", wml.getModelHref(model_id, false),"S",1);
        LOGGER.info("deployment_id = "+ deployment_id);

        return deployment_id;
    }


    public String createAndDeployDietPythonModel() {

        LOGGER.info("Create Python Model");

        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);

        String model_id = wml.createNewModel("Diet","do-docplex_12.10","src/resources/diet.zip", "/v4/runtimes/do_12.10");
        //String model_id = wml.createNewModel("Diet","do-docplex_12.9","src/resources/diet.zip", "/v4/runtimes/do_12.9");
        LOGGER.info("model_id = "+ model_id);

        String deployment_id = wml.deployModel("diet-test-wml-2", wml.getModelHref(model_id, false),"S",1);
        LOGGER.info("deployment_id = "+ deployment_id);

        return deployment_id;
    }

    public void deleteDeployment(String deployment_id) {

        LOGGER.info("Delete deployment");


        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);
        wml.deleteDeployment(deployment_id);

    }

    public void fullDietPythonFlow(boolean useOutputDataReferences) {

        LOGGER.info("Full flow with Diet");

        String deployment_id = createAndDeployDietPythonModel();
        JSONArray input_data = new JSONArray();
        input_data.put(createDataFromCSV("diet_food.csv"));
        input_data.put(createDataFromCSV("diet_food_nutrients.csv"));
        input_data.put(createDataFromCSV("diet_nutrients.csv"));
        JSONArray output_data_references = null;
        if (useOutputDataReferences) {
            COSConnector cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
            output_data_references = new JSONArray();
            output_data_references.put(cos.getDataReferences("log.txt"));
        }
        WMLJob job = createAndRunJobOnExistingDeployment(deployment_id, input_data, null, null, output_data_references);
        if (useOutputDataReferences) {
            getLogFromCOS();
        } else {
            getLogFromJob(job);
        }
        deleteDeployment(deployment_id);
    }

    public void fullDietLPFLow() {
        String deployment_id = createAndDeployEmptyCPLEXModel();
        COSConnector cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
        cos.putFile("diet.lp", "src/resources/diet.lp");
        JSONArray input_data_references = new JSONArray();
        input_data_references.put(cos.getDataReferences("diet.lp"));
        JSONArray output_data_references = new JSONArray();
        output_data_references.put(cos.getDataReferences("solution.json"));
        output_data_references.put(cos.getDataReferences("log.txt"));
        createAndRunJobOnExistingDeployment(deployment_id, null, input_data_references, null, output_data_references);
        getLogFromCOS();
        getSolutionFromCOS();
        deleteDeployment(deployment_id);
    }

    public void getLogFromJob(WMLJob job) {
        JSONArray output_data = job.extractOutputData();
        for (Iterator<Object> it = output_data.iterator(); it.hasNext(); ) {
            JSONObject o = (JSONObject)it.next();
            if (o.getString("id").equals("log.txt")) {
                byte[] encoded = new byte[0];
                try {
                    encoded = o.getJSONArray("values").getJSONArray(0).getString(0).getBytes("UTF-8");
                    byte[] decoded = Base64.getDecoder().decode(encoded);
                    String log = new String(decoded, "UTF-8");

                    LOGGER.info("log: " + log);

                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public void getLogFromCOS() {
        getFileFromCOS("log.txt");
    }

    public void getSolutionFromCOS() {
        getFileFromCOS("solution.json");
    }

    public void getFileFromCOS(String fileName) {

        LOGGER.info("Get " + fileName);
        COSConnector cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
        String log = cos.getFile(fileName);

        log = log.replaceAll("\\r", "\n");
        LOGGER.info(fileName+": " + log);
    }

    public void fullInfeasibleLPFLow() {
        String deployment_id = createAndDeployEmptyCPLEXModel();
        COSConnector cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
        cos.putFile("infeasible.lp", "src/resources/infeasible.lp");
        cos.putFile("infeasible.feasibility", "src/resources/infeasible.feasibility");
        JSONArray input_data_references = new JSONArray();
        input_data_references.put(cos.getDataReferences("infeasible.lp"));
        input_data_references.put(cos.getDataReferences("infeasible.feasibility"));
        JSONArray output_data_references = new JSONArray();
        output_data_references.put(cos.getDataReferences("log.txt"));
        output_data_references.put(cos.getDataReferences("conflict.json"));
        createAndRunJobOnExistingDeployment(deployment_id, null, input_data_references, null, output_data_references);
        getLogFromCOS();
        getFileFromCOS("conflict.json");
        deleteDeployment(deployment_id);
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
        createAndRunJobOnExistingDeployment(deployment_id, null, input_data_references, null, output_data_references);
        getLogFromCOS();
        getFileFromCOS("solution.json");
    }

    public String createAndDeployWarehouseOPLModel() {

        LOGGER.info("Create Warehouse OPL Model");

        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);

        String model_id = wml.createNewModel("Warehouse","do-opl_12.9","src/resources/warehouse.zip");
        LOGGER.info("model_id = "+ model_id);

        String deployment_id = wml.deployModel("warehouse-opl-test-wml-2", wml.getModelHref(model_id, false),"S",1);
        LOGGER.info("deployment_id = "+ deployment_id);

        return deployment_id;
    }

    public void fullWarehouseOPLFlow(boolean useOutputDataReferences) {

        LOGGER.info("Full Warehouse with OPL");

        String deployment_id = createAndDeployWarehouseOPLModel();
        COSConnector cos = new COSConnectorImpl(Credentials.COS_ENDPOINT, Credentials.COS_APIKEY, Credentials.COS_BUCKET, Credentials.COS_ACCESS_KEY_ID, Credentials.COS_SECRET_ACCESS_KEY);
        cos.putFile("warehouse.dat", "src/resources/warehouse.dat");
        JSONArray input_data_references = new JSONArray();
        input_data_references.put(cos.getDataReferences("warehouse.dat"));
        JSONArray output_data_references = null;
        if (useOutputDataReferences) {
            output_data_references = new JSONArray();
            output_data_references.put(cos.getDataReferences("log.txt"));
        }
        WMLJob job = createAndRunJobOnExistingDeployment(deployment_id, null, input_data_references, null, output_data_references);
        if (useOutputDataReferences) {
            getLogFromCOS();
        } else {
            getLogFromJob(job);
        }
        deleteDeployment(deployment_id);
    }


    public String createAndDeployDietOPLModel() {

        LOGGER.info("Create Diet OPL Model");

        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);

        String model_id = wml.createNewModel("Diet OPL","do-opl_12.9","src/resources/dietopl.zip");
        LOGGER.info("model_id = "+ model_id);

        String deployment_id = wml.deployModel("diet-opl-test-wml-2", wml.getModelHref(model_id, false),"S",1);
        LOGGER.info("deployment_id = "+ deployment_id);

        return deployment_id;
    }

    public String createAndDeployDietMainOPLModel() {

        LOGGER.info("Create Diet Main OPL Model");

        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);

        String model_id = wml.createNewModel("Diet Main OPL","do-opl_12.9","src/resources/dietoplmain.zip");
        LOGGER.info("model_id = "+ model_id);

        String deployment_id = wml.deployModel("diet-main-opl-test-wml-2", wml.getModelHref(model_id, false),"S",1);
        LOGGER.info("deployment_id = "+ deployment_id);

        return deployment_id;
    }


    public void fullDietOPLWithDatFlow(boolean useOutputDataReferences) {

        LOGGER.info("Full Diet with OPL");

        String deployment_id = createAndDeployDietOPLModel();
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
        WMLJob job = createAndRunJobOnExistingDeployment(deployment_id, null, input_data_references, null, output_data_references);
        if (useOutputDataReferences) {
            getLogFromCOS();
            getSolutionFromCOS();
        } else {
            getLogFromJob(job);
        }
        deleteDeployment(deployment_id);
    }

    public void fullDietMainOPLWithDatFlow(boolean useOutputDataReferences) {

        LOGGER.info("Full Diet with Main OPL");

        String deployment_id = createAndDeployDietMainOPLModel();
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
        WMLJob job = createAndRunJobOnExistingDeployment(deployment_id, null, input_data_references, null, output_data_references);
        if (useOutputDataReferences) {
            getLogFromCOS();
            getSolutionFromCOS();
        } else {
            getLogFromJob(job);
        }
        deleteDeployment(deployment_id);
    }

    public void fullDietOPLWithCSVFlow(boolean useOutputDataReferences) {

        LOGGER.info("Full Diet with OPL");

        String deployment_id = createAndDeployDietOPLModel();
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
        WMLJob job = createAndRunJobOnExistingDeployment(deployment_id, input_data, null, null, output_data_references);
        if (useOutputDataReferences) {
            getLogFromCOS();
            getSolutionFromCOS();
        } else {
            getLogFromJob(job);
        }
        deleteDeployment(deployment_id);
    }

    public void fullOPLWithJSONFlow(boolean useOutputDataReferences) {

        LOGGER.info("Full JSON Test with OPL");

        WMLConnectorImpl wml = new WMLConnectorImpl(Credentials.WML_URL, Credentials.WML_INSTANCE_ID, Credentials.WML_APIKEY);

        String model_id = wml.createNewModel("JSON Test OPL","do-opl_12.9","src/resources/jsontest.zip");
        LOGGER.info("model_id = "+ model_id);

        String deployment_id = wml.deployModel("json-test-opl-test-wml-2", wml.getModelHref(model_id, false),"S",1);
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
        WMLJob job = createAndRunJobOnExistingDeployment(deployment_id, null, input_data_references, null, output_data_references);
        if (useOutputDataReferences) {
            getLogFromCOS();
            getSolutionFromCOS();
        } else {
            getLogFromJob(job);
        }
        deleteDeployment(deployment_id);
    }

    public void fullInfeasibleDietOPLFlow() {

        LOGGER.info("Full Infeasible Diet with OPL");

        String deployment_id = createAndDeployDietOPLModel();
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

        WMLJob job = createAndRunJobOnExistingDeployment(deployment_id, input_data, input_data_references, null, output_data_references);
        LOGGER.info("Status:" + job.getStatus());
        getLogFromCOS();
        getSolutionFromCOS();
        deleteDeployment(deployment_id);
    }


    public static void main(String[] args) {
        Sample main = new Sample();


        // Python
        //main.fullDietPythonFlow(false);

        // OPL
        //main.fullWarehouseOPLFlow(true);
        //main.fullDietOPLWithDatFlow(false);
        //main.fullDietOPLWithCSVFlow(false);

        //main.fullDietMainOPLWithDatFlow(false);
        main.fullOPLWithJSONFlow(true);

        //KO main.fullInfeasibleDietOPLFlow();

        // CPLEX
//        main.fullDietLPFLow();
//        main.fullInfeasibleLPFLow();


        // CPO
//        main.fullCPOFLow();
        //main.runCPO("colors");
        //main.runCPO("plant_location");

    }
}
