package com.ibm.wmlconnector.impl;

import com.ibm.wmlconnector.COSConnector;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.logging.Logger;

public class COSConnectorImpl extends ConnectorImpl implements COSConnector {

    private static final Logger LOGGER = Logger.getLogger(COSConnectorImpl.class.getName());


    String url;
    String bucket;

    String access_key_id;
    String secret_access_key;

    public COSConnectorImpl(String url, String apikey, String bucket, String access_key_id, String secret_access_key) {
        super(apikey);
        this.url = url;
        this.bucket = bucket;
        this.access_key_id = access_key_id;
        this.secret_access_key = secret_access_key;
        lookupBearerToken();
    }


    @Override
    public void putFile(String fileName, String filePath) {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "bearer " + bearerToken);
        headers.put("Content-Type", "text/plain");

        doPut(url + "/" + bucket + "/" + fileName, headers, getFileContent(filePath));
    }

    @Override
    public void putBinaryFile(String fileName, String filePath) {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "bearer " + bearerToken);

        byte[] bytes = getBinaryFileContent(filePath);

        doPut(url + "/" + bucket + "/" + fileName, headers, bytes);

    }

    @Override
    public String getFile(String fileName) {

        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "bearer " + bearerToken);
        headers.put("Content-Type", "text/plain");


        String res = doGet(url + "/" + bucket + "/" + fileName, headers);

        return res;
    }

    @Override
    public JSONObject getDataReferences(String id) {
        String data = "{\n" +
                        "\"id\": \"" + id + "\",\n" +
                        "\"type\": \"s3\",\n" +
                        "\"connection\": {\n" +
                            "\"endpoint_url\": \"" + url + "\",\n" +
                            "\"access_key_id\": \"" + access_key_id + "\",\n" +
                            "\"secret_access_key\": \"" + secret_access_key + "\"\n" +
                        "}, \n" +
                        "\"location\": {\n" +
                            "\"bucket\": \"" + bucket + "\",\n" +
                            "\"path\": \"" + id + "\"\n" +
                        "}\n" +
                        "}\n";
        JSONObject jsonData  = new JSONObject(data);
        return jsonData;
    }


}
