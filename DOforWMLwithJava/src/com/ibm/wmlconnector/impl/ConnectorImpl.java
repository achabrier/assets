package com.ibm.wmlconnector.impl;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import org.json.JSONException;
import org.json.JSONObject;


public abstract class ConnectorImpl {

    private static final Logger LOGGER = Logger.getLogger(ConnectorImpl.class.getName());

    private static final String IAM_URL = "https://iam.cloud.ibm.com/identity/token";
    private static final int IAM_TIMEOUT = 3600;

    String apikey;
    String bearerToken;
    long bearerTokenTime;

    ConnectorImpl(String apikey) {
        this.apikey = apikey;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public void lookupBearerToken()  {
        // Cloud
        LOGGER.info("Lookup Bearer Token from IAM (ASYNCH)");
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Basic Yng6Yng=");
        headers.put("Content-Type", "application/x-www-form-urlencoded");

        String res = doPost(IAM_URL, headers, "apikey="+apikey+"&grant_type=urn%3Aibm%3Aparams%3Aoauth%3Agrant-type%3Aapikey");

        try {
            JSONObject json = new JSONObject(res);
            bearerToken = (String) json.get("access_token");
            bearerTokenTime = (new Date()).getTime();

            LOGGER.info("Bearer Token OK : " + this.bearerToken);

        } catch (JSONException e) {
            LOGGER.severe("Error Bearer Token from IAM (ASYNCH)");
        }

    }

    String doCall(String targetUrl, Map<String, String> headers, String body, String method) {

        HttpURLConnection connection = null;

        try {
            URL url = new URL(targetUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);


            if (body != null) {
                connection.setRequestProperty("Content-Length",
                        Integer.toString(body.length()));
            }
            Iterator<String> it = headers.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                connection.setRequestProperty(key, headers.get(key));
            }


            connection.setUseCaches(false);
            connection.setDoOutput(true);

            if (body != null) {
                //Send request
                DataOutputStream wr = new DataOutputStream(
                        connection.getOutputStream());
                wr.writeBytes(body);
                wr.close();
            }

            int statusCode = connection.getResponseCode();

            if (statusCode >= 400) {
                LOGGER.severe("status: "+ statusCode);
                InputStream error = connection.getErrorStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(error));
                StringBuilder response = new StringBuilder(); // or StringBuffer if Java version 5+
                String line;
                while ((line = rd.readLine()) != null) {
                    LOGGER.severe(line);
                    response.append(line);
                    response.append('\r');
                }
                rd.close();
            } else {
                //Get Response
                InputStream is = connection.getInputStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder(); // or StringBuffer if Java version 5+
                String line;
                while ((line = rd.readLine()) != null) {
                    response.append(line);
                    response.append('\r');
                }
                rd.close();
                return response.toString();
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    String doCall(String targetUrl, Map<String, String> headers, byte[] body, String method) {

        HttpURLConnection connection = null;

        try {
            URL url = new URL(targetUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);


            if (body != null) {
                connection.setRequestProperty("Content-Length",
                        Integer.toString(body.length));
            }
            Iterator<String> it = headers.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                connection.setRequestProperty(key, headers.get(key));
            }


            connection.setUseCaches(false);
            connection.setDoOutput(true);

            if (body != null) {
                //Send request
                DataOutputStream wr = new DataOutputStream(
                        connection.getOutputStream());
                wr.write(body);
                wr.close();
            }

            int statusCode = connection.getResponseCode();

            if (statusCode >= 400) {
                LOGGER.severe("status: "+ statusCode);
                InputStream error = connection.getErrorStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(error));
                StringBuilder response = new StringBuilder(); // or StringBuffer if Java version 5+
                String line;
                while ((line = rd.readLine()) != null) {
                    LOGGER.severe(line);
                    response.append(line);
                    response.append('\r');
                }
                rd.close();
            } else {
                //Get Response
                InputStream is = connection.getInputStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder(); // or StringBuffer if Java version 5+
                String line;
                while ((line = rd.readLine()) != null) {
                    response.append(line);
                    response.append('\r');
                }
                rd.close();
                return response.toString();
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }


    String doPost(String targetUrl, Map<String, String> headers, String body) {
        return doCall(targetUrl, headers, body,"POST");
    }

    String doGet(String targetUrl, Map<String, String> headers) {
        return doCall(targetUrl, headers, (String)null,"GET");
    }

    String doPut(String targetUrl, Map<String, String> headers, String body) {
        return doCall(targetUrl, headers, body,"PUT");
    }

    String doPut(String targetUrl, Map<String, String> headers, byte[] body) {
        return doCall(targetUrl, headers, body,"PUT");
    }

    String doDelete(String targetUrl, Map<String, String> headers) {
        return doCall(targetUrl, headers, (String)null, "DELETE");
    }



    public static byte[] getBinaryFileContent(String inputFilename)  {
        byte[] encoded = new byte[0];
        try {
            encoded = Files.readAllBytes(Paths.get(inputFilename));
        } catch (IOException e) {
            LOGGER.severe("Error getting binary file" + e.getStackTrace());
        }
        return encoded;
    }

    public static String getFileContent(String inputFilename)  {
        String res = "";
        try {
            List<String> lines = Files.readAllLines(Paths.get(inputFilename));
            for (Iterator<String> it = lines.iterator(); it.hasNext();)
                res += it.next() + "\n";
        } catch (IOException e) {
            LOGGER.severe("Error getting text file" + e.getStackTrace());
        }

        return res;
    }

}
