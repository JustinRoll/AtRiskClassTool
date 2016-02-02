package com.jroll.sonar;

import java.io.IOException;
import java.util.HashMap;

import com.jroll.util.FinalConfig;
import org.apache.commons.configuration.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import sonarConfiguration.MindConfiguration;
//import sonarExtensionPoints.MindInitializer;

import com.google.inject.Inject;

//import exceptions.SonarSeverityUpdaterException;

public class SonarSeverityUpdater {
    private SonarWebApi sonarWebApi;
    private FinalConfig config;
    private static final Logger LOG = LoggerFactory.getLogger(SonarSeverityUpdater.class);
/*
    @Inject
    public SonarSeverityUpdater(SonarWebApi sonarWebApi, FinalConfig config)
    {
        this.sonarWebApi = sonarWebApi;
        this.config = config;

    }

    public void updateSeverity(HashMap<String, Double>rulesWithRoh) throws SonarSeverityUpdaterException
    {
        LOG.info("start updating severity");
        try
        {
            setSeverity(rulesWithRoh, sonarWebApi, config);
        }catch(NumberFormatException | ConfigurationException | IOException  e)
        {
            throw new SonarSeverityUpdaterException(e.getMessage(), e.getCause());
        }
        LOG.info("updating severity finished");
    }

    private void setSeverity(HashMap<String, Double> rulesRohMap, SonarWebApi api, MindConfiguration config) throws NumberFormatException, ConfigurationException, IOException
    {
        for(String rule : rulesRohMap.keySet())
        {
            Double roh = rulesRohMap.get(rule);
            if(roh != null)
            {
                String severity = getSeverityForRoh(roh, config);
                api.updateRuleSeverity(rule, severity);
                LOG.info(rule + " severity set to " + severity );
            }

        }
    }

    public static String getSeverityForRoh(Double roh, MindConfiguration config) throws NumberFormatException, ConfigurationException
    {
        if(roh >= Double.parseDouble(config.getBlockerRohSeverity()))
        {
            return "BLOCKER";
        }else if(roh >= Double.parseDouble(config.getCriticalRohSeverity()))
        {
            return "CRITICAL";
        }else if(roh >= Double.parseDouble(config.getMajorRohSeverity()))
        {
            return "MAJOR";
        }else if(roh >= Double.parseDouble(config.getMinorRohSeverity()))
        {
            return "MINOR";
        }
        return "INFO";
    }
    */

}
