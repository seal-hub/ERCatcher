package com.ercatcher;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LOG {
    public static final int SUPER_VERBOSE = 4;
    public static final int VERBOSE = 3;
    public static final int ESSENTIAL = 2;
    public static final int SILENCE = 1;
    public static final int ERROR = 0;
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLACK = "\u001B[30m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    static int LEVEL = VERBOSE;
    static String timerSuffix = "";
    private static Map<String, Long> timerMap = new HashMap<>();

    public static void setLogLevel(int logLevel){
        LEVEL = logLevel;
    }
    private static FileWriter timeWriter = null;
    private static FileWriter resultWriter = null;
    private static FileWriter errorWriter = null;
    private static FileWriter logWriter = null;
    private static String reportDirectory = null;

    public static void init(String reportPath){
        reportDirectory = reportPath;
        try {
            timeWriter = new FileWriter(String.format("%s/time.txt", reportPath));
            resultWriter = new FileWriter(String.format("%s/result.txt", reportPath));
            errorWriter = new FileWriter(String.format("%s/errors.txt", reportPath));
            logWriter = new FileWriter(String.format("%s/log.txt", reportPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void finish(){
        try {
            if(timeWriter != null) {
                timeWriter.flush();
                timeWriter.close();
            }
            if(resultWriter != null) {
                resultWriter.flush();
                resultWriter.close();
            }
            if(errorWriter != null) {
                errorWriter.flush();
                errorWriter.close();
            }
            if(logWriter != null) {
                logWriter.flush();
                logWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void logln(String string, int logLevel){
        log(string+"\n", logLevel);
    }

    public static void flush(){
        try {
            if(timeWriter != null) {
                timeWriter.flush();
            }
            if(resultWriter != null) {
                resultWriter.flush();
            }
            if(errorWriter != null) {
                errorWriter.flush();
            }
            if(logWriter != null) {
                logWriter.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void log(String string, int logLevel){
        if(Thread.interrupted()){
            flush();
            System.exit(1);
        }
        try {
            if(logLevel == ESSENTIAL && resultWriter != null)
                resultWriter.write(string);
            if(logLevel == ERROR && resultWriter != null)
                errorWriter.write(string);
            if(logWriter != null)
                logWriter.write(string);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(logLevel <= LOG.LEVEL) {
            if(logLevel == ERROR) {
                System.out.print(ANSI_RED + timerSuffix + string + ANSI_RESET);
            }
            else
                System.out.print(timerSuffix + string);
        }
    }

    public static void startTimer(String name, int logLevel) {
        if (timerMap.containsKey(name)) {
            LOG.logln(String.format("The time for %s is already initiated.", name), LOG.ESSENTIAL);
            return;
        }
        timerMap.put(name, System.currentTimeMillis());
        LOG.logln(String.format("Starting %s%s%s...",  ANSI_YELLOW, name, ANSI_RESET), logLevel);
        LOG.timerSuffix += "\t";
    }

    public static void endTimer(String name, int logLevel) {
        if (!timerMap.containsKey(name)) {
            LOG.logln(String.format("The time for %s is not existed.", name), LOG.ESSENTIAL);
            return;
        }
        long startTime = timerMap.get(name);
        long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
        timerMap.remove(name);
        LOG.timerSuffix = LOG.timerSuffix.substring(0, LOG.timerSuffix.length()-1);
        try {
            if(timeWriter != null)
                timeWriter.write(String.format("%s took %d seconds\n", name,elapsedTime));
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOG.logln(String.format("%s took %s%d seconds%s", name, ANSI_BLUE,elapsedTime, ANSI_RESET), logLevel);
    }
}
