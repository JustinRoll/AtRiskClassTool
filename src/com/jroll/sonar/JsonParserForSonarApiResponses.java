package com.jroll.sonar;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonParserForSonarApiResponses {
    private static final Logger LOG = LoggerFactory.getLogger(JsonParserForSonarApiResponses.class);
    public static String getDateOfLastSonarAnalyse(String version, String json) throws JSONException
    {
        JSONArray versions = new JSONArray(json);

        JSONObject obj = (JSONObject) versions.get(0);
        return obj.getString("dt");
    }

    public static Boolean jsonParsable(String response) {
        try {
            new JSONArray(response);
            return true;
        }
        catch (JSONException e) {
            return false;
        }
    }

    public static int getNloc(String json)
    {
        JSONArray violationsMetric = new JSONArray(json);
        JSONArray cells = ((JSONObject)violationsMetric.get(0)).getJSONArray("msr");
        if(cells.length()==0)
        {
            throw new JSONException("no entry found.");
        }
        JSONObject metric = ((JSONObject)cells.get(0));
        String val = metric.get("val").toString();
        return (int)Math.floor(Double.valueOf(val));
    }

    public static int getNumberOfViolationsOfSpecificRuleForResource(String json)
    {
        JSONObject violationsMetric = new JSONObject(json);
        return ((JSONObject)violationsMetric.get("paging")).getInt("fTotal");
    }

    public static String getProjectId(String json) {
        JSONArray projects = new JSONArray(json);
        JSONObject obj = (JSONObject) projects.get(0);
        return obj.getString("id");
    }

    public static String getResourceId(String json) {
        JSONArray projects = new JSONArray(json);
        JSONObject obj = (JSONObject) projects.get(0);
        return  obj.get("id").toString();
    }

    public static String getIssueIdOfNextViolation(String json)
    {
        JSONObject obj = new JSONObject(json);
        JSONArray issues = obj.getJSONArray("issues");
        //FIXME: SOMEHOW HERE IT WILL CRASH
        JSONObject issue = (JSONObject)issues.get(0);
        return issue.get("key").toString();
    }

    public static String getNumberOfCurrentlyOpenViolations(String json) {
        JSONObject obj = new JSONObject(json);
        JSONObject paging = (JSONObject)obj.get("paging");
        return paging.get("total").toString();
    }

    public static HashMap<String, String> getEffortForFixingRuleMap(String json)
    {
        JSONObject obj = new JSONObject(json);
        JSONArray array = (JSONArray)obj.get("rules");
        HashMap<String, String> effortForFixingRuleMap = new HashMap<String, String>();
        for(int i = 0; i < array.length(); i++)
        {
            JSONObject ruleObj = array.getJSONObject(i);

            if(ruleObj.has("debtRemFnCoeff"))
                effortForFixingRuleMap.put(ruleObj.getString("key"), ruleObj.get("debtRemFnCoeff").toString());
            else if(ruleObj.has("debtRemFnOffset"))
            {
                effortForFixingRuleMap.put(ruleObj.getString("key"), ruleObj.get("debtRemFnOffset").toString());
            }else{
                effortForFixingRuleMap.put(ruleObj.getString("key"), "NA");
                LOG.error(ruleObj.getString("key") + " no debt info");
            }
        }
        return effortForFixingRuleMap;
    }

    public static HashMap<String, String> getProperties(String json) {
        HashMap<String, String> properties = new HashMap<String, String>();
        JSONArray props = new JSONArray(json);
        for(int i = 0 ; i < props.length(); i++)
        {
            JSONObject prop = (JSONObject) props.get(i);
            properties.put(prop.get("key").toString(), prop.get("value").toString());
        }
        return properties;
    }

    public static String getSeverity(String json) {
        JSONObject obj = new JSONObject(json);
        return obj.getJSONObject("rule").getString("severity");

    }

    public static String getQualityProfile(String qualityProfileJson) {
        JSONArray profiles = new JSONArray(qualityProfileJson);
        return profiles.getJSONObject(0).getString("name");
    }

    public static String getQualityProfileKey(String qprofileName, String qualityProfileKeyJson) {
        JSONObject qprofiles = new JSONObject(qualityProfileKeyJson);
        JSONArray qprofilesArray = qprofiles.getJSONArray("qualityprofiles");
        for(int i = 0; i < qprofilesArray.length(); i++)
        {
            JSONObject qprofile = qprofilesArray.getJSONObject(i);
            if(qprofile.getString("name").equals(qprofileName))
                return qprofile.getString("key");
        }
        return null;
    }

    /* Get ALL class metrics from SonarQube */
    public static TreeMap<String, HashMap<String, Double>> parseClassMetrics(String response) {
        JSONArray resources = new JSONArray(response);
        TreeMap<String, HashMap<String, Double>> classMetrics = new TreeMap<String, HashMap<String, Double>>();
        System.out.println(response);
        int good = 0;
        int bad = 0;

        for (int i = 0; i < resources.length(); i++) {
            HashMap<String, Double> metricMap = new HashMap<String, Double>();
            JSONObject currentResource = resources.getJSONObject(i);
            String className = currentResource.getString("key");
            classMetrics.put(className, metricMap);

            try {
                JSONArray metrics = currentResource.getJSONArray("msr");

                for (int j = 0; j < metrics.length(); j++) {
                    JSONObject currentMetric = metrics.getJSONObject(j);
                    String metricName = currentMetric.getString("key");
                    Double metricValue = currentMetric.getDouble("val");
                    metricMap.put(metricName, metricValue);
                }
                good++;
            }
            catch (JSONException e){
                bad++;
                System.out.println("COULD NOT PARSE JSON FOR " + currentResource);
            }

        }
        System.out.println("--------");
        System.out.printf("%d SUCCESSFUL, %d UNSUCCESSFUL\n", good, bad);
        return classMetrics;
    }

    /* parse all JSONObjects. Sort them by date */
    public static String parseResourceId(String response, String projectId) {
        JSONArray resources = new JSONArray(response);
        TreeMap<String, String> dateResourceMap = new TreeMap<String, String>();
        for (int i = 0; i < resources.length(); i++) {
            JSONObject currentResource = resources.getJSONObject(i);
            String resourceName = currentResource.getString("key");
            if (resourceName.toLowerCase().contains(projectId.toLowerCase())) {
                dateResourceMap.put(currentResource.getString("date"), currentResource.getString("key"));
            }
        }
        return dateResourceMap.lastKey();
    }
}
