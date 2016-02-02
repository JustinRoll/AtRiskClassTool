package com.jroll.data;

import gr.spinellis.ckjm.ClassMetrics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by jroll on 12/20/15.
 */
public class ClassData implements Serializable {
    private ArrayList<ArrayList<TreeMap>> staticMetrics;
    private Map<String, ClassMetrics> ckjmMetrics;
    private Map<String, Integer> linesOfCode;
    private TreeMap<String, HashMap<String, Double>> sonarMetrics;

    public ArrayList<ArrayList<TreeMap>> getStaticMetrics() {
        return staticMetrics;
    }

    public void setStaticMetrics(ArrayList<ArrayList<TreeMap>> staticMetrics) {
        this.staticMetrics = staticMetrics;
    }

    public Map<String, ClassMetrics> getCkjmMetrics() {
        return ckjmMetrics;
    }

    public void setCkjmMetrics(Map<String, ClassMetrics> ckjmMetrics) {
        this.ckjmMetrics = ckjmMetrics;
    }

    public Map<String, Integer> getLinesOfCode() {
        return linesOfCode;
    }

    public void setLinesOfCode(Map<String, Integer> linesOfCode) {
        this.linesOfCode = linesOfCode;
    }

    public TreeMap<String, HashMap<String, Double>> getSonarMetrics() {
        return sonarMetrics;
    }

    public void setSonarMetrics(TreeMap<String, HashMap<String, Double>> sonarMetrics) {
        this.sonarMetrics = sonarMetrics;
    }
}
