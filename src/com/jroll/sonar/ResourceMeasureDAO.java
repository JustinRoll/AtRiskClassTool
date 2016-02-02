package com.jroll.sonar;



        import java.util.HashMap;

public class ResourceMeasureDAO
{
    private HashMap<String, Integer> vioMap;
    Integer size;

    public HashMap<String, Integer> getVioMap() {
        return vioMap;
    }

    public void setVioMap(HashMap<String, Integer> vioMap) {
        this.vioMap = vioMap;
    }

    public Integer getSize()
    {
        return size;
    }

    public void setSize(Integer size)
    {
        this.size = size;
    }
}
