package com.ibm;


import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

import com.ibm.wmlconnector.WMLJob;
import com.ibm.wmlconnector.impl.WMLConnectorImpl;
import org.json.JSONArray;
import org.json.JSONException;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final String URL = "https://us-south.ml.cloud.ibm.com";
    private static final String APIKEY  = "XXXXXXXXXXXXXXXXXXXXXXXXXXXX";
    private static final String INSTANCE_ID = "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";


    private static JSONArray getDietData() {
        String data = "[\n" +
                "				{\n" +
                "					\"id\":\"diet_food.csv\",\n" +
                "					\"fields\" : [\"name\",\"unit_cost\",\"qmin\",\"qmax\"],\n" +
                "					\"values\" : [\n" +
                "						[\"Roasted Chicken\", 0.84, 0, 10],\n" +
                "						[\"Spaghetti W/ Sauce\", 0.78, 0, 10],\n" +
                "						[\"Tomato,Red,Ripe,Raw\", 0.27, 0, 10],\n" +
                "						[\"Apple,Raw,W/Skin\", 0.24, 0, 10],\n" +
                "						[\"Grapes\", 0.32, 0, 10],\n" +
                "						[\"Chocolate Chip Cookies\", 0.03, 0, 10],\n" +
                "						[\"Lowfat Milk\", 0.23, 0, 10],\n" +
                "						[\"Raisin Brn\", 0.34, 0, 10],\n" +
                "						[\"Hotdog\", 0.31, 0, 10]\n" +
                "					]\n" +
                "				},\n" +
                "				{\n" +
                "					\"id\":\"diet_food_nutrients.csv\",\n" +
                "					\"fields\" : [\"Food\",\"Calories\",\"Calcium\",\"Iron\",\"Vit_A\",\"Dietary_Fiber\",\"Carbohydrates\",\"Protein\"],\n" +
                "					\"values\" : [\n" +
                "						[\"Spaghetti W/ Sauce\", 358.2, 80.2, 2.3, 3055.2, 11.6, 58.3, 8.2],\n" +
                "						[\"Roasted Chicken\", 277.4, 21.9, 1.8, 77.4, 0, 0, 42.2],\n" +
                "						[\"Tomato,Red,Ripe,Raw\", 25.8, 6.2, 0.6, 766.3, 1.4, 5.7, 1],\n" +
                "						[\"Apple,Raw,W/Skin\", 81.4, 9.7, 0.2, 73.1, 3.7, 21, 0.3],\n" +
                "						[\"Grapes\", 15.1, 3.4, 0.1, 24, 0.2, 4.1, 0.2],\n" +
                "						[\"Chocolate Chip Cookies\", 78.1, 6.2, 0.4, 101.8, 0, 9.3, 0.9],\n" +
                "						[\"Lowfat Milk\", 121.2, 296.7, 0.1, 500.2, 0, 11.7, 8.1],\n" +
                "						[\"Raisin Brn\", 115.1, 12.9, 16.8, 1250.2, 4, 27.9, 4],\n" +
                "						[\"Hotdog\", 242.1, 23.5, 2.3, 0, 0, 18, 10.4	]\n" +
                "					]\n" +
                "				},\n" +
                "				{\n" +
                "					\"id\":\"diet_nutrients.csv\",\n" +
                "					\"fields\" : [\"name\",\"qmin\",\"qmax\"],\n" +
                "					\"values\" : [\n" +
                "						[\"Calories\", 2000, 2500],\n" +
                "						[\"Calcium\", 800, 1600],\n" +
                "						[\"Iron\", 10, 30],\n" +
                "						[\"Vit_A\", 5000, 50000],\n" +
                "						[\"Dietary_Fiber\", 25, 100],\n" +
                "						[\"Carbohydrates\", 0, 300],\n" +
                "						[\"Protein\", 50, 100]\n" +
                "					]\n" +
                "				}\n" +
                "			],\n";
        JSONArray jsonData  = new JSONArray(data);
        return jsonData;

    }
    public void createAndRunJobOnExistingDeployment(String deployment_id) {

        LOGGER.info("Create and run job");


        JSONArray input_data = getDietData();

        WMLConnectorImpl wml = new WMLConnectorImpl(URL, INSTANCE_ID, APIKEY);
        wml.lookupAsynchDearerToken();
        WMLJob job  = wml.createJob(deployment_id, input_data);
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
                    HashMap<String, Object> kpis = job.getKPIs();

                    Iterator<String> keys = kpis.keySet().iterator();

                    while (keys.hasNext()) {
                        String kpi = keys.next();
                        LOGGER.info("KPI " + kpi + " = " + kpis.get(kpi));
                    }
                }
            } catch (JSONException e) {
                LOGGER.severe("Error extractState: " + e);
            }

            LOGGER.info("State: " + state);
        } while (!state.equals("completed") && !state.equals("failed"));
        if (state.equals("failed"))
            LOGGER.severe("Job failed.");
        else {
            JSONArray output_data = job.extractOutputData();
            LOGGER.info("output_data = " + output_data);
        }

    }


    public String createAndDeployDietPythonModel() {

        LOGGER.info("Create Pyhton Model");



        WMLConnectorImpl wml = new WMLConnectorImpl(URL, INSTANCE_ID, APIKEY);
        wml.lookupAsynchDearerToken();

        String model_id = wml.createNewModel("Diet","do-docplex_12.9","src/resources/diet.zip");
        LOGGER.info("model_id ="+ model_id);

        String deployment_id = wml.deployModel("diet-test-wml-2", wml.getModelHref(model_id, false),"S",1);
        LOGGER.info("deployment_id ="+ deployment_id);

        return deployment_id;
    }

    public void deleteDeployment(String deployment_id) {

        LOGGER.info("Delete deployment");


        WMLConnectorImpl wml = new WMLConnectorImpl(URL, INSTANCE_ID, APIKEY);
        wml.lookupAsynchDearerToken();
        wml.deleteDeployment(deployment_id);

    }

    public void createDeployAndRunDietPythonModel() {

        LOGGER.info("Full flow with Diet");

        String deployment_id = createAndDeployDietPythonModel();
        createAndRunJobOnExistingDeployment(deployment_id);

    }

    public void fullDietFlow() {

        LOGGER.info("Full flow with Diet");

        String deployment_id = createAndDeployDietPythonModel();
        createAndRunJobOnExistingDeployment(deployment_id);
        deleteDeployment(deployment_id);
    }


    public static void main(String[] args) {
        Main main = new Main();

//        main.createAndRunJobOnExistingDeployment("abf1485f-7fd1-4d04-a357-513e814b94e6");
//        main.createAndRunJobOnExistingDeployment("53bd920a-24b8-4941-b612-a1bb6b65c0b6");
//        main.createAndDeployDietPythonModel();
        main.fullDietFlow();

    }
}
