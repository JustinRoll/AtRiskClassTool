package com.jroll.command;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by jroll on 10/3/15.
 */
public class CommandExecutor {
    public CommandExecutor() {

    }

    public static int runCommand(String command, String inDirectory) throws IOException, InterruptedException {

        Runtime rt = Runtime.getRuntime();
        Process pr = rt.exec(command, null, new File(String.format("%s/", inDirectory)));
        printOutput(pr);
        return pr.waitFor();

    }
    public static void printOutput(Process pr) throws IOException {
        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(pr.getInputStream()));

        // read the output from the command
        System.out.println("Here is the standard output of the command:\n");
        String s = null;
        while ((s = stdInput.readLine()) != null) {
            System.out.println(s);
        }
    }
}
