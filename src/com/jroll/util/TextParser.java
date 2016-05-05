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

    /*
    31/Mar/07 7:05 AM
     */
    public static LocalDateTime parseDateStringAmPm(String text) {
        //06/Oct/13 11:01
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MMM/yy H:mm");
        return LocalDateTime.parse(text, formatter);
    }
    /* 2016-02-19T00:10:59Z 2014-12-21T21:29:43 */
    public static LocalDateTime parseDateStringAlt(String text) {
        text = text.replace("T", " ").replace("Z", "");
        DateTimeFormatter formatter;
        if (dateParsableAlt(text)) {
             formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        }
        else {
            //2007-09-25 13:37
             formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        }
        return LocalDateTime.parse(text, formatter);
    }

    public static boolean dateParsableAlt(String text) {
        try {
            text = text.replace("T", " ").replace("Z", "");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime.parse(text, formatter);
            return true;
        }
        catch (Exception e) {
            return false;
        }
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

        Pattern pattern = Pattern.compile(".*-([0-9]+).*");
        Matcher matcher = pattern.matcher(concreteLine);

        while (matcher.find()) {
            allMatches.add(matcher.group(1));
        }

        return allMatches.size() > 0 ? allMatches.get(0) : null;
    }

    public static String getTicketId(String concreteLine, String project) {
        List<String> allMatches = new ArrayList<String>();
        String regex = String.format(".*%s-([0-9]+).*", project);

        System.out.println(regex);
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(concreteLine);

        while (matcher.find()) {
            allMatches.add(matcher.group(1));
        }

        return allMatches.size() > 0 ? allMatches.get(0) : null;
    }
}
