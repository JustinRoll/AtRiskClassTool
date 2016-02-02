package com.jroll.sonar;

import java.util.ArrayList;
        import java.util.HashMap;
        import java.util.List;

public class MeasureDAO {
    private static HashMap<String, ResourceMeasureDAO> resourceMeasuresMap = new HashMap<String, ResourceMeasureDAO>();

    public MeasureDAO(HashMap<String, ResourceMeasureDAO> resourceMeasuresMap)
    {
        MeasureDAO.resourceMeasuresMap = resourceMeasuresMap;
    }

    public Integer getSizeOfResource(String resourceKey)
    {
        return resourceMeasuresMap.get(resourceKey).getSize();
    }

    public List<String> getListOfResources()
    {
        //FIXME: Is it a problem to always create new List?
        return new ArrayList<String>(resourceMeasuresMap.keySet());
    }

    public HashMap<String, Integer> getNumberOfViolationsMap(String resourceKey)
    {
        return resourceMeasuresMap.get(resourceKey).getVioMap();
    }
}

