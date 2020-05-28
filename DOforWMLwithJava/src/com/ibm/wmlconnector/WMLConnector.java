package com.ibm.wmlconnector;

import org.json.JSONArray;

public interface WMLConnector {
    public enum Runtime {
        DO_12_9 ("/v4/runtimes/do_12.9"),
        DO_12_10 ("/v4/runtimes/do_12.10");

        private String name = "";

        Runtime(String name){
            this.name = name;
        }

        @Override
        public String toString(){
            return name;
        }
    }

    public enum ModelType {
        CPLEX_12_9 ("do-cplex_12.9"),
        CPO_12_9 ("do-cpo_12.9"),
        OPL_12_9 ("do-opl_12.9"),
        DOCPLEX_12_9 ("do-docplex_12.9"),
        CPLEX_12_10 ("do-cplex_12.10"),
        CPO_12_10 ("do-cpo_12.10"),
        OPL_12_10 ("do-opl_12.10"),
        DOCPLEX_12_10 ("do-docplex_12.10");

        private String name = "";

        ModelType(String name){
            this.name = name;
        }

        @Override
        public String toString(){
            return name;
        }
    }

    public enum TShirtSize {
        S ("S"),
        M ("M"),
        XL ("XL");

        private String name = "";

        TShirtSize(String name){
            this.name = name;
        }

        @Override
        public String toString(){
            return name;
        }
    }

    public void lookupBearerToken();
    public String getBearerToken();
    public String createNewModel(String modelName, ModelType type, String modelAssetFilePath, Runtime runtime);
    public String createNewModel(String modelName, ModelType type, String modelAssetFilePath);
    public String getModelHref(String modelId, boolean displayModel);
    public String deployModel(String deployName, String modelHref, TShirtSize size, int nodes);
    public WMLJob createJob(String deployment_id,
                            JSONArray input_data,
                            JSONArray input_data_references,
                            JSONArray output_data,
                            JSONArray output_data_references);
    public WMLJob createAndRunJob(String deployment_id,
                                  JSONArray input_data,
                                  JSONArray input_data_references,
                                  JSONArray output_data,
                                  JSONArray output_data_references);
    public void deleteDeployment(String deployment_id);
}
