package com.jroll.util;


import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Add;
import weka.filters.unsupervised.attribute.PrincipalComponents;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Created by jroll on 12/14/15.
 */
public class ARFFGenerator {

    public static Instances performPCA(Instances data) throws Exception {
        PrincipalComponents pca = new PrincipalComponents();
        Instances filter = new Instances(data);
        System.out.println("name " + filter.attribute(filter.numAttributes() - 1).name());
        filter.deleteAttributeAt(filter.attribute("Changed?").index());

        pca.setInputFormat(filter);

        pca.setMaximumAttributeNames(data.numAttributes());
        pca.setMaximumAttributes(-1);


        Instances newData = Filter.useFilter(filter, pca);
        newData.insertAttributeAt(data.attribute("Changed?"), newData.numAttributes());
        for (int i = 0; i < newData.numInstances(); i++) {
            newData.instance(i).setValue(newData.numAttributes() - 1, data.instance(i).value(data.numAttributes() - 1));
        }

        return newData;
    }

    /**
     * takes 2 arguments:
     * - CSV input file
     * - ARFF output file
     */
    public static void convertFile(String csvName, String outFileName, String project) throws Exception {

        // load CSV
        project = project.contains("demo") ? "digital-democracy" : project;
        String[] options = {"-F", "\t"};
        CSVLoader loader = new CSVLoader();
        loader.setOptions(options);
        loader.setSource(new File(csvName));
        String[] filter = {"Ticket",
                "Last 10 Touched",
                "Fix Version",
                "Issue Type",
                "Last Commit Time",
                "Req Created",
                "Commits",
                "File",
                "Requirement",
                "First Req?",
                "code_sim_Index",
                "code_sim_TicketId",
                "code_sim_File",
                "code_sim_FixedVersion",
                "code_sim_LastCommitTime",
                "req_sim_Index",
                "req_sim_TicketId",
                "req_sim_File",
                "req_sim_FixedVersion",
                "req_sim_LastCommitTime",
                "conditions_to_cover",
                "branch_coverage",
                "line_coverage",
                "lines_to_cover",
                "SMELL_COUNT_NON_EXCEPTION",
                "SMELL_DEBT",
                "Changed?"};

        String[] javaGroupNames = {
                "codeSim",
                "reqSim", "temporalLocality", "sonarqube"
                ,"ckjm"
                };
        String[] phpGroupNames = {
                "reqSim", "temporalLocality", "sonarqube"
        };

        String[] CODE_SIM = {"code_sim_ReqToCode_JSD","code_sim_ReqToCode_VSM", "Changed?"};
        String[] REQ_SIM =  {"req_sim_BleuComparer_LIN_MaxSimilarity", "req_sim_BleuComparer_LIN_AverSimilarity",
                "req_sim_BleuComparer_LIN_AverTopFive",
                "req_sim_CmComparer_JCN_MaxSimilarity",
                "req_sim_CmComparer_JCN_AverSimilarity",
                "req_sim_CmComparer_JCN_AverTopFive",
                "req_sim_GreedyComparer_RES_MaxSimilarity",
                "req_sim_GreedyComparer_RES_AverSimilarity",
                "req_sim_GreedyComparer_RES_AverTopFive",
                "req_sim_OptimumComparer_WUP_MaxSimilarity",
                "req_sim_OptimumComparer_WUP_AverSimilarity",
                "req_sim_OptimumComparer_WUP_AverTopFive",
                "req_sim_JsdSim_MaxSimilarity",
                "req_sim_JsdSim_AverSimilarity",
                "req_sim_JsdSim_AverTopFive",
                "req_sim_VsmSim_MaxSimilarity",
                "req_sim_VsmSim_AverSimilarity",
                "req_sim_VsmSim_AverTopFive",
                "Changed?"};
        String[] SONAR = {"complexity",
                "violations",
                "ncloc",
                "Changed?"};
        String[] TEMPORAL = {"simple", "linear", "logarithmic", "Changed?"};

        String[] CKJM = {"ckjm_noc", "ckjm_wmc", "ckjm_rfc",
                "ckjm_cbo",
                "ckjm_dit",
                "ckjm_lcom",
                "ckjm_ca",
                "ckjm_npm",
                "Changed?"};

        String[][] groups;
        String[] groupNames;
        String[][] javaGroups = {CODE_SIM, REQ_SIM, TEMPORAL, SONAR, CKJM};;
        String[][] phpGroups = {REQ_SIM, TEMPORAL, SONAR};
        if (project.contains("digital")) {
            groups = phpGroups;
            groupNames = phpGroupNames;
        }
        else {
            groups = javaGroups;
            groupNames = javaGroupNames;
        }

        int groupSize = 0;
        for (String[] group : groups)
            groupSize+= group.length;

        String[] combined = new String[groupSize];
        int i = 0;
        for (String[] group: groups)
            for (String item : group)
                if (!item.equals("Changed?")) {
                    combined[i++] = item;
                }

        combined[combined.length - 1] = "Changed?";
        Instances data = loader.getDataSet();

        splitFeatures(data, groups, groupNames, project);
        splitGroups(data, groups, groupNames, project);
        makeRandom(data, 10, 3000, project);
        //renameAttributes(data, outFileName);

        saveAndFilterInstances(data, outFileName, combined);
        pcaMain(data, groups, project);

        // save ARFF

    }

    private static void pcaMain(Instances data, String[][] groups, String project) throws Exception {
        ArrayList<String> features = new ArrayList<String>();
        String fileName = String.format("%s_pca.arff", project);
        for (String[] group : groups)
            for (String feat : group)
                features.add(feat);

        Instances pca = new Instances(data);
        deleteAllBut((String[]) features.toArray(new String[0]), pca);

        saveInstances(performPCA(pca), fileName);

    }

    /*
       Group_data_feature.arff
       Filter each group down to the attributes we need */
    private static void splitGroups(Instances data, String[][] groups, String[] groupNames, String project) throws Exception {
        int i = 0;
        for (String[] group : groups) {
            String newOutfileName = String.format("%s_%s_raw.arff", project, groupNames[i++]);
            String pcaName = String.format("%s_%s_pca.arff", project, groupNames[i - 1]);
            Instances groupInstances = new Instances(data);
            System.out.println("group " + group[0].toString());

            deleteAllBut(group, groupInstances);
            saveInstances(groupInstances, newOutfileName);

            //performPCAthenSave(groupInstances, pcaName);

        }

    }

    private static void performPCAthenSave(Instances groupInstances, String newFile) throws Exception {
        Instances pca = performPCA(groupInstances);
        saveInstances(pca, newFile);
    }

    public static void saveInstances(Instances data, String outFileName) throws IOException {
        File outFile = new File(outFileName);
        ArffSaver saver = new ArffSaver();
        data.setRelationName(outFileName);

        if (!outFileName.contains("?")) {
            saver.setInstances(data);
            saver.setFile(outFile);
            saver.setDestination(outFile);
            saver.writeBatch();
        }
    }

    public static void saveAndFilterInstances(Instances data, String outFileName, String[] groupsToUse) throws IOException {
        deleteAllBut(groupsToUse, data);
        saveInstances(data, outFileName);
    }

    private static void splitFeatures(Instances data, String[][]groups, String[] groupNames, String projectName) throws IOException {
        Enumeration<Attribute> e = data.enumerateAttributes();
        Attribute att;


        while (e.hasMoreElements()) {
            att = e.nextElement();
            int grpIndex = getGroup(att.name(), groups);

            if (grpIndex != -1) {
                String groupName = groupNames[grpIndex];
                String attName = att.name().replace("_", "-").replace(groupName + "-", "");
                //int lastIndex = attName.lastIndexOf("-");
                //if (lastIndex > 0)
                //    attName = attName.substring(0, lastIndex - 1) + attName.substring(attName.lastIndexOf("-") + 1);
                String outFileName = String.format("%s_%s_%s.arff", projectName, groupName, attName);

                splitFeature(att, data, outFileName);
            }
        }

    }

    private static int getGroup(String attName, String[][] groups) {
        int i = 0;
        for (String[] group : groups) {
            for (String item : group) {
                if (item.equals(attName)) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    private static void makeRandom(Instances data, int numAttributes, int rand, String project) throws IOException {
        Instances randomData = new Instances(data);
        String outFileName = String.format("%s_random.arff", project);
        deleteAttributes(data.attribute("Changed?"), randomData, "Changed?");

        for (int i = 0; i < numAttributes; i++) {
            Attribute attribute = new Attribute(String.format("random%d",i));
            randomData.insertAttributeAt(attribute, i);

        }

        Enumeration<Instance> e = randomData.enumerateInstances();
        Instance i;
        while (e.hasMoreElements()) {
            i = e.nextElement();
            Random ran = new Random();

            for (int j = 0; j < numAttributes; j++) {
                double r = 1.0 * ran.nextInt(rand);
                i.setValue(j, r);
            }

        }
        //renameAttributes(randomData, outFileName);
        saveInstances(randomData, outFileName);
    }

    private static void deleteAttributes(Attribute useAtt, Instances newData, String except) {
        Enumeration<Attribute> e = newData.enumerateAttributes();
        Attribute att;


        while (e.hasMoreElements()) {

            att = e.nextElement();
            if (!att.name().equals(useAtt.name()) && !att.name().equals(except)) {
                Attribute a = newData.attribute(att.name());
                newData.deleteAttributeAt(a.index());

            }
        }
    }

    private static void deleteAllBut(String[] attributeNames, Instances newData) {
        Enumeration<Attribute> e = newData.enumerateAttributes();
        Attribute att;
        List<String> lstNames = Arrays.asList(attributeNames);


        while (e.hasMoreElements()) {
            att = e.nextElement();

            System.out.println(lstNames.toString());
            if (!lstNames.contains(att.name().trim())) {
                System.out.println("Deleting " + att.name());
                Attribute a = newData.attribute(att.name());
                newData.deleteAttributeAt(a.index());
            }

        }
    }

    private static void splitFeature(Attribute useAtt, Instances data, String outFileName) throws IOException {
        Instances newData = new Instances(data);

        Enumeration<Attribute> e = data.enumerateAttributes();
        Attribute att;


        while (e.hasMoreElements()) {

            att = e.nextElement();
            if (!att.name().equals(useAtt.name()) && !att.name().equals("Changed?")) {
                Attribute a = newData.attribute(att.name());
                newData.deleteAttributeAt(a.index());

            }


        }
        //renameAttributes(newData, newOutFileName);

        saveInstances(newData, outFileName);

/*
        String newOutFileName = att.name() + "_" + outFileName;
        Attribute changed = data.attribute("Changed?").copy(outFileName + "_Changed?");

        FastVector atts = new FastVector(2);


        atts.addElement((Attribute) att.copy(newOutFileName + "_" + att.name()));
        atts.addElement(changed);
        Instances newInstances = new Instances(att.name(), atts, 0);

        saveInstances(newInstances, newOutFileName);
        */

    }

/*
    public static void renameAttributes(Instances data, String outFileName) {


        Enumeration<Attribute> e = data.enumerateAttributes();
        Attribute att;
        while (e.hasMoreElements()) {
            att = e.nextElement();
            data.renameAttribute(att, outFileName + att.name());
            System.out.println(att.name());
        }
    }
*/
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
