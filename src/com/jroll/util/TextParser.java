package com.jroll.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by jroll on 12/31/15.
 */
public class TextParser {

    public static LocalDateTime parseDateString(String text) {
        //06/Oct/13 11:01
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MMM/yy HH:mm");
        return LocalDateTime.parse(text, formatter);
    }

    public static boolean dateParsable(String text) {
        //06/Oct/13 11:01
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MMM/yy HH:mm");
            LocalDateTime.parse(text, formatter);
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }


    public static String getTicketIdQpid(String concreteLine) {
        List<String> allMatches = new ArrayList<String>();

        Pattern pattern = Pattern.compile(".*qpid-([0-9]+).*");
        Matcher matcher = pattern.matcher(concreteLine);

        while (matcher.find()) {
            allMatches.add(matcher.group(1));
        }

        return allMatches.size() > 0 ? allMatches.get(0) : null;
    }

    public static String getTicketId(String concreteLine) {
        List<String> allMatches = new ArrayList<String>();

        System.out.println("testing");
        System.out.println(concreteLine);
        Pattern pattern = Pattern.compile(".*-([0-9]+).*");
        Matcher matcher = pattern.matcher(concreteLine);

        while (matcher.find()) {
            allMatches.add(matcher.group(1));
        }

        return allMatches.size() > 0 ? allMatches.get(0) : null;
    }
}
