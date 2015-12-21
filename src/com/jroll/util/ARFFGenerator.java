package com.jroll.util;

import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by jroll on 12/14/15.
 */
public class ARFFGenerator {

    /**
     * takes 2 arguments:
     * - CSV input file
     * - ARFF output file
     */
    public static void convertFile(String csvName, String outFileName) throws Exception {

        // load CSV
        String[] options = {"-F", "\t"};
        CSVLoader loader = new CSVLoader();
        loader.setOptions(options);
        loader.setSource(new File(csvName));

        Instances data = loader.getDataSet();
        File outFile = new File(outFileName);

        // save ARFF
        ArffSaver saver = new ArffSaver();
        saver.setInstances(data);
        saver.setFile(outFile);
        saver.setDestination(outFile);
        saver.writeBatch();
    }

    String[] labels = {"h", "s", "n"};
    public static void generate(List<HashMap<String, Object>> featureList, String fileName) throws Exception {
        FastVector atts;
        FastVector      attVals;
        Instances data;
        double[]        vals;
/*

        atts = new FastVector();
        // - nominal
        attVals = new FastVector();
        for (String label : labels)
            attVals.addElement(label);

        atts.addElement(new Attribute("labels", attVals));
        for (String featureName : featureNames)
            atts.addElement(new Attribute(featureName));



        // 2. create Instances object
        data = new Instances(fileName, atts, 0);

        // 3. fill with data
        // first instance
        for (HashMap<String, Object> features : featureList) {
            vals = new double[data.numAttributes()];
            int counter = 1;
            for (String featureName : featureNames) {
                vals[counter++] =  ((Integer) features.get(featureName)) * 1.0;
            }
            //label should be last item in file
            //System.out.println(features.get("label"));
            if (attVals.indexOf( features.get("label")) == -1) {
                System.out.print("Ignoring data with label: " + features.get("label"));
                continue;
            }
            vals[0] = attVals.indexOf( features.get("label"));
            data.add(new Instance(1.0, vals));
        }


        System.out.println(data.toString());
        // 4. output data
        PrintWriter writer = new PrintWriter(fileName, "UTF-8");
        writer.print(data.toString());
        writer.close(); */
    }
}
