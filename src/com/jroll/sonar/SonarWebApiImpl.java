package com.jroll.sonar;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

import com.jroll.util.FinalConfig;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpResponseException;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.commons.codec.binary.Base64;

import static com.jroll.sonar.JsonParserForSonarApiResponses.jsonParsable;

public class SonarWebApiImpl implements SonarWebApi {
    public static String sonarHost; //FIXME
    private static String project;
    private static String[] projectList;
    private static String projectResourceKey;
    private String qualityProfileKey;
    String[] metrics;


    /* given a project, pull metrics for ALL classes
     * eg view-source:http://localhost:9000/api/resources?resource=ignite:ignite&depth=-1&scope=FIL&metrics=lines&format=json */
    public TreeMap<String, HashMap<String, Double>> getSonarMetrics() throws IOException, InterruptedException {
        int longRetries = 0;
        int MAX_LONG_RETRIES = 7;
        String response = "bad response";
        System.out.println(projectList.length);


        while (!jsonParsable(response) && longRetries++ < MAX_LONG_RETRIES) {
            for (String currProject : projectList) {
                String baseUrl = "%s/api/resources?resource=%s&depth=-1&scope=FIL&metrics=%s&format=json";
                String finalUrl = String.format(baseUrl, sonarHost, currProject, String.join(",", metrics));
                for (int i = 0; i < 4 && response.equals("bad response"); i++) {
                    try {
                        response = sendGet(finalUrl);
                        Thread.sleep(1000);
                        System.out.println("Breaking out");
                    } catch (FileNotFoundException e) {
                        System.out.println(e);

                    }
                }

            }
            if (!jsonParsable(response))
                Thread.sleep(100000);
        }

        return JsonParserForSonarApiResponses.parseClassMetrics(response);
    }

    private String getProjectResourceKey() throws IOException {
        String baseUrl = String.format("%s/api/resources?format=json", sonarHost);
        String response = sendGet(baseUrl);

        return JsonParserForSonarApiResponses.parseResourceId(response, project);
    }


    public SonarWebApiImpl(FinalConfig config) throws ConfigurationException, IOException
    {
        metrics = config.sonarMetrics;
        SonarWebApiImpl.sonarHost = config.sonarHost;
        SonarWebApiImpl.project = config.sonarProject[0];
        SonarWebApiImpl.projectList = config.sonarProject;
        //this.qualityProfileKey = getProjectResourceKey();


    }

    private String getQualityProfileKey() throws IOException {
        String qualityProfileJson = sendGet(sonarHost + "/api/profiles/list?project=" + project);
        String qualityProfile = JsonParserForSonarApiResponses.getQualityProfile(qualityProfileJson);
        String qualityProfileKeyJson = sendGet(sonarHost + "/api/rules/app");
        return JsonParserForSonarApiResponses.getQualityProfileKey(qualityProfile, qualityProfileKeyJson);


    }

    public List<String> getListOfAllRules() throws IOException
    {
        List<String> rulesList = new ArrayList<String>();
        String json2 = sendGet(sonarHost + "/api/rules/search?qprofile=" + qualityProfileKey+ "&ps=10000&is_template=false&activation=true"); //FIXME: CHECK IF IT IS BETTER TO QUERY THROUGH QUALITY PROFILES
        JSONObject rulesObj = new JSONObject(json2);

        JSONArray rules = (JSONArray) rulesObj.get("rules");
        for (int i = 0; i < rules.length(); i++) {
            rulesList.add(((JSONObject) (rules.get(i))).get("key").toString());
        }
        return rulesList;
    }

    public List<String> getListOfAllResources() throws IOException {
        String resourcesJSON = sendGet(sonarHost + "/api/resources?resource=" + project + ";depth=-1;scopes=FIL");
        JSONArray resourcesArray = new JSONArray(resourcesJSON.substring(0, resourcesJSON.length()));
        List<String> resourcesList = new ArrayList<String>();
        for(int i = 0; i < resourcesArray.length(); i++)
        {
            resourcesList.add(((JSONObject) (resourcesArray.get(i))).get("key").toString());
        }
        return resourcesList;
    }

    public int getNumberOfViolationsOfSpecificRuleForResource(String resourceKey, String rule) throws IOException {
        String numberOfViolationsJSON = sendGet(sonarHost+"/api/issues/search?componentRoots=" + resourceKey + "&rules="+rule);
        return JsonParserForSonarApiResponses.getNumberOfViolationsOfSpecificRuleForResource(numberOfViolationsJSON);
    }

    public String getDateOfLastSonarAnalyse(String version) throws IOException {
        String versionsJSON = sendGet(sonarHost + "/api/events?resource=" + project + "&categories=Version");
        return JsonParserForSonarApiResponses.getDateOfLastSonarAnalyse(version, versionsJSON);
    }

    public int getSizeOfResource(String resourceKey) throws IOException {
        String sizeJson = sendGet(sonarHost + "/api/resources?resource=" + resourceKey + ";metrics=ncloc");
        return JsonParserForSonarApiResponses.getNloc(sizeJson);
    }

    private static String sendGet(String url) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        // optional default is GET
        con.setRequestMethod("GET");

        // add request header
        String USER_AGENT = "Mozilla/32.0";
        con.setRequestProperty("User-Agent", USER_AGENT);

        BufferedReader in = new BufferedReader(new InputStreamReader(
                con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response.toString();
    }

    /*
    curl -u admin:admin -X POST 'http://localhost:9000/api/projects/delete?key=org.apache.tika:tika'
     */
    public void deleteProject() throws IOException, InterruptedException {

        for (String project : projectList) {
            String finalUrl = String.format("%s/api/projects/delete?key=%s", sonarHost, project);
            System.out.println(finalUrl);
            try {
                sendRequest(finalUrl, new ArrayList(), "POST");
            } catch (HttpResponseException e) {
                System.out.println(e);
            }
        }
    }

    private static void sendRequest(String url, List<NameValuePair> params, String type) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        // optional default is GET
        con.setRequestMethod(type);

        // add request header
        String USER_AGENT = "Mozilla/32.0";


        String userCredentials = "admin:admin"; //FIXME
        String basicAuth = "Basic " + new String(new Base64().encode(userCredentials.getBytes()));
        con.setRequestProperty ("Authorization", basicAuth);
        con.setRequestProperty("User-Agent", USER_AGENT);
        con.setDoOutput(true);
        con.setDoInput(true);
        OutputStream os = con.getOutputStream();
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(os, "UTF-8"));
        writer.write(getQuery(params));
        writer.flush();
        writer.close();
        os.close();
        if(con.getResponseCode() != 200)
            throw new HttpResponseException(con.getResponseCode(), con.getResponseMessage()+ " URL: " + con.getURL());

        con.getResponseCode();
        con.connect();
    }

    private static String getQuery(List<NameValuePair> params) throws UnsupportedEncodingException
    {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (NameValuePair pair : params)
        {
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(pair.getName(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
        }

        return result.toString();
    }

    public static String getProjectId() throws IOException {
        String json = sendGet(sonarHost + "/api/projects/index?key=" + project);
        return JsonParserForSonarApiResponses.getProjectId(json);
    }

    public static String getResourceId(String resource) throws IOException {
        String json = sendGet(sonarHost + "/api/resources?resource="+ resource);
        return JsonParserForSonarApiResponses.getResourceId(json);
    }

    public static String getIssueIdOfNextViolation(String resource, String rule) throws IOException
    {
        String json = sendGet(sonarHost + "/api/issues/search?componentRoots=" + resource + "&rules=" + rule+"&statuses=OPEN");
        return JsonParserForSonarApiResponses.getIssueIdOfNextViolation(json);
    }

    public static HashMap<String, String> getProperties() throws IOException
    {
        String json = sendGet(sonarHost + "/api/properties");
        return JsonParserForSonarApiResponses.getProperties(json);

    }

    public static String getDefaultSeveritiy(String rule) throws IOException
    {
        String json = sendGet(sonarHost + "/api/rules/show?key="+ rule);
        return JsonParserForSonarApiResponses.getSeverity(json);
    }

    @Override
    public void updateRuleSeverity(String ruleKey, String rulePriority) throws IOException {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("profile_key", qualityProfileKey));
        params.add(new BasicNameValuePair("rule_key", ruleKey));
        params.add(new BasicNameValuePair("severity", rulePriority));
        sendRequest(sonarHost + "/api/qualityprofiles/activate_rule", params, "POST");
    }

    public String[] getMetrics() {
        return metrics;
    }

    public void setMetrics(String[] metrics) {
        this.metrics = metrics;
    }
}
