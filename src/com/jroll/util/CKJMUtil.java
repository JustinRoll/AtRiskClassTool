package com.jroll.util;

import gr.spinellis.ckjm.ClassMetrics;
import gr.spinellis.ckjm.ClassMetricsContainer;
import gr.spinellis.ckjm.ClassVisitor;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Created by jroll on 12/20/15.
 */
public class CKJMUtil {
    static void processClass(ClassMetricsContainer cm, String clspec) {
        int spc;
        JavaClass jc = null;

        if ((spc = clspec.indexOf(' ')) != -1) {
            String jar = clspec.substring(0, spc);
            clspec = clspec.substring(spc + 1);
            try {
                jc = new ClassParser(jar, clspec).parse();
            } catch (IOException e) {
                System.err.println("Error loading " + clspec + " from " + jar + ": " + e);
            }
        } else {
            try {
                jc = new ClassParser(clspec).parse();
            } catch (IOException e) {
                System.err.println("Error loading " + clspec + ": " + e);
            }
        }
        if (jc != null) {
            ClassVisitor visitor = new ClassVisitor(jc, cm);
            visitor.start();
            visitor.end();
        }
    }

    public static Map<String, ClassMetrics> parseData(Set<String> subdirectories) throws Exception {
        //INPUT
        //example java -jar /usr/local/lib/ckjm-1.5.jar build/classes/gr/spinellis/ckjm/*.class
        String command = "/usr/bin/java -jar %s%s %s"; // %s/*.class";
        //String command = "ls > /Users/jroll/outputtest.txt";
        TreeMap<String, HashMap<String, Object>> resultMap = new TreeMap<String, HashMap<String, Object>>();
        ClassMetricsContainer cm = new ClassMetricsContainer();

        for (String subdir : subdirectories) {

            //String finalCommand = String.format(command, jarLocation, jarName, subdir);

            try {
                processClass(cm, subdir);
            }
            catch (Exception e) {
                System.out.println("could not process " + subdir);
            }

            //Set<String> output = runCommand(finalCommand.trim(), "/Users/jroll/dev/thesis");
            /*for (String line : output) {

                HashMap<String, Object> result = parseCkjm(line);
                resultMap.put((String) result.get("Class"), result);
            }*/
        }
        //System.out.println(cm.m);
        return cm.m;

    }
}
