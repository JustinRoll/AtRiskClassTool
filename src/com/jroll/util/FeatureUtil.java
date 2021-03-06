package com.jroll.util;

import org.apache.commons.collections4.bidimap.AbstractDualBidiMap;
import weka.core.*;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;
import weka.filters.unsupervised.attribute.StringToWordVector;

import java.io.File;
import java.util.*;

/**
 * Created by jroll on 10/24/15.
 */
public class FeatureUtil {




    public static Double computeRequirementSimilarities(String requirements1, String requirements2) {
        /*StringVectorizer sv = StringToWordVector();
        Attribute one = new Attribute("one");
        Attribute two = new Attribute("two");
        Attribute three = new Attribute("three");
        Attribute four = new Attribute("four");
        Attribute five = new Attribute("five");

        FastVector attributes = new FastVector();
        attributes.addElement(one);
        attributes.addElement(two);
        attributes.addElement(three);
        attributes.addElement(four);
        attributes.addElement(five);

        Instances wVector = new Instances("Vector", attributes, 0);

        Instance firstInstance = new Instance(attributes.size());
        firstInstance.setDataset(wClassVector);
        firstInstance.setValue(one, 1.0);
        firstInstance.setValue(two, 2.0);
        firstInstance.setValue(three, 3.0);
        firstInstance.setValue(four, 4.0);
        firstInstance.setValue(five, 5.0);

        Instance secondInstance = new Instance(attributes.size());
        secondInstance.setDataset(wClassVector);
        secondInstance.setValue(one, 10.0);
        secondInstance.setValue(two, 20.0);
        secondInstance.setValue(three, 30.0);
        secondInstance.setValue(four, 40.0);
        secondInstance.setValue(five, 50.0);

        EuclideanDistance ed = new EuclideanDistance(wClassVector);

        Double wDist = ed.distance(firstInstance, secondInstance);

        ed.setDontNormalize(true);
        Double wDist1 = ed.distance(firstInstance, secondInstance);*/
        return 4.0;
    }

    public static Set<String> getLabels(List<HashMap<String, Object>> featureList) {
        Set<String> labelList = new HashSet<String>();
        for (HashMap<String, Object> featureMap : featureList ) {
            String label = (String) featureMap.get("label");
            if (label != null)
                labelList.add(label);
        }
        return labelList;
    }

    public static ArrayList<String> getFeatureNames(List<HashMap<String, Object>> featureList) {
        return null;
    }
}
