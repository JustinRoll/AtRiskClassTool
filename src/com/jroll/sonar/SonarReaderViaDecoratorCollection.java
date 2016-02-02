package com.jroll.sonar;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import com.google.inject.Inject;

public class SonarReaderViaDecoratorCollection implements SonarReader {
    List<String> allRules;
    private MeasureDAO measureDAO;
    private static final Logger LOG = LoggerFactory.getLogger(SonarReaderViaDecoratorCollection.class);

    @Inject
    public SonarReaderViaDecoratorCollection(SonarWebApi api) throws IOException
    {
        allRules = api.getListOfAllRules();
    }

    @Override
    public HashMap<String, Integer> getNumberOfViolationsPerRule(String resourceKey) throws IOException {
        HashMap<String, Integer> violationMap = measureDAO.getNumberOfViolationsMap(resourceKey);
        for(String rule : getListOfAllRules())
        {
            if(!violationMap.containsKey(rule))
            {
                violationMap.put(rule, 0);
            }
        }
        return violationMap;
    }

    @Override
    public int getSizeOfClassAtTheEndOfVersion(String resourceKey)
            throws IOException {
        return measureDAO.getSizeOfResource(resourceKey);
    }

    @Override
    public List<String> getListOfAllResources() throws IOException {
        return measureDAO.getListOfResources();
    }

    @Override
    public HashMap<String, Integer> getNumberOfViolationsPerRuleEverythingZero()
            throws IOException {
        HashMap<String, Integer> numberOfViolationsPerRule = new HashMap<String, Integer>();


        for(String rule : getListOfAllRules())
        {
            numberOfViolationsPerRule.put(rule, 0);
        }

        return numberOfViolationsPerRule;
    }

    @Override
    public List<String> getListOfAllRules() throws IOException {
        return allRules;
    }

    @Override
    public void setSonarData(MeasureDAO measureDAO) {
        this.measureDAO = measureDAO;

    }

}
