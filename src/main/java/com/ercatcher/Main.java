package com.ercatcher;

import com.ercatcher.ConcurrencyAnalysis.C2G.C2GManager;
import com.ercatcher.ConcurrencyAnalysis.C2G.InterMethodBox;
import com.ercatcher.ConcurrencyAnalysis.C3G.C3GManager;
import com.ercatcher.ConcurrencyAnalysis.C3G.C3GTask;
import com.ercatcher.ConcurrencyAnalysis.CSF.CSFManager;
import com.ercatcher.ConcurrencyAnalysis.ThreadLattice;
import com.ercatcher.ConcurrencyAnalysis.ThreadLatticeManager;
import com.ercatcher.RaceDetector.*;
import com.ercatcher.memory.MemoryAnalysis;
import com.ercatcher.svc.AbstractOnDemandSHB;
import com.ercatcher.svc.FastOnDemandSHB;
import com.ercatcher.svc.MemSaveOnDemandSHB;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.toolkits.pointer.DumbPointerAnalysis;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static String androidJar;
    private static String apkPath;
    private static String reportPath;
    private static RaceDetector<MethodRace> initalMethodRaceDetector = null;
    private static ReachableMethodRaceFilter myReachableMethodRaceFilter = null;
    private static boolean componentIsolation = true;
    private static boolean UAF = true;


    public static void main(String[] args) {

        if (args.length < 2) {
            System.err.println("Provide (1) Android Path (2) APK Path  (3) Report Directory (4) Verbosity=3 (5) OnlyUAF=true (6) ComponentIsolation=true");
            return;
        }
        setOptions(args);
        LOG.startTimer("WholeProcess", LOG.VERBOSE);
        doAnalysis(UAF);
        LOG.endTimer("WholeProcess", LOG.ESSENTIAL);
        LOG.finish();
    }


    private static void doAnalysis(boolean UAF) {
        try {
            // Setup FlowDroid and SPARK which generates call graph
            Set<SootMethod> allAPKMethods = eventRaceSetupSoot();
            // Analyze and annotate the code for points-to analysis
            MemoryAnalysis memoryAnalysis = new MemoryAnalysis(allAPKMethods, UAF);
            // Generate the CSF of the code and add CSF Library
            CSFManager myCaSFManager = new CSFManager(allAPKMethods, memoryAnalysis);
            if (UAF)
                initalMethodRaceDetector = new InitalMethodRaceDetector(myCaSFManager);
            else
                initalMethodRaceDetector = new GeneralInitalMethodRaceDetector(myCaSFManager);
            // Construct C2G
            C2GManager myC2GManager = new C2GManager(myCaSFManager);
            // Filter reachable event races
            myReachableMethodRaceFilter = new ReachableMethodRaceFilter(initalMethodRaceDetector, myC2GManager);
            LOG.logln(String.format("Reachable MRs: %d out of %d", myReachableMethodRaceFilter.getRaces().size(), initalMethodRaceDetector.getRaces().size()), LOG.ESSENTIAL);
            // Construct C3G
            C3GManager myC3GManager = new C3GManager(myC2GManager, myReachableMethodRaceFilter);
            LOG.flush();
            // EventRaceAggregator stores the filtered event races
            EventRaceAggregator eventRaceAggregator = new EventRaceAggregator();
            EventRaceAggregator unSoundEventRaceAggregator = new EventRaceAggregator();
            Set<MethodRace> accessibleMethodRaces = new HashSet<>();
            if (componentIsolation) {
                int c = 0;
                // Iterate through components
                for (InterMethodBox componentIMBEntryPoint : myC2GManager.getRoot().getOutgoingIMBs()) {
                    c++;
                    LOG.logln("----------------------------------", LOG.VERBOSE);
                    LOG.logln(String.format("%d/%d Component: %s", c, myC2GManager.getRoot().getOutgoingIMBs().size(), componentIMBEntryPoint), LOG.VERBOSE);
                    // Retrieve the C2G corresponding to a component
                    C2GManager componentCaCGMAnager = myC2GManager.getSubCaCG(componentIMBEntryPoint.getSource().getSource());
                    ReachableMethodRaceFilter reachableMethodRaceFilter = new ReachableMethodRaceFilter(initalMethodRaceDetector, componentCaCGMAnager);
                    accessibleMethodRaces.addAll(reachableMethodRaceFilter.getRaces());
                    if (reachableMethodRaceFilter.getRaces().size() == 0)
                        continue;
                    // Retrieve the C3G corresponding to a component
                    C3GManager c3GManager = myC3GManager.getSubCventManager(componentCaCGMAnager);
                    if (c3GManager.getRootC3GTask() == null) {
                        LOG.logln(String.format("Why this component %s doesn't have a Cvent ", componentIMBEntryPoint), LOG.ERROR);
                        continue;
                    }
                    // Apply UF filters for early stopping
                    IfGuardNullAtEndFilter ifGuardNullAtEndFilter = new IfGuardNullAtEndFilter(reachableMethodRaceFilter, c3GManager, componentCaCGMAnager);
                    if (ifGuardNullAtEndFilter.getRaces().size() == 0)
                        continue;
                    LOG.startTimer("On-Demand HB Analysis", LOG.VERBOSE);
                    // According to the size of C3G, we use different implementations of SVC
                    AbstractOnDemandSHB shb = null;
                    if (c3GManager.getAllC3GTasks().size() < 10_000) // TODO: configurable
                        shb = new FastOnDemandSHB(c3GManager); // Uses a tabular cache for HB
                    else
                        shb = new MemSaveOnDemandSHB(c3GManager); // Uses a limited space cache
                    shb.computeSVC();
                    LOG.endTimer("On-Demand HB Analysis", LOG.VERBOSE);
                    // Filter event races using Happens-before relations
                    EventRaceDetector eventRaceDetector = new EventRaceDetector(ifGuardNullAtEndFilter, c3GManager, componentCaCGMAnager, shb);
                    eventRaceAggregator.aggregate(eventRaceDetector, ifGuardNullAtEndFilter);
                    LOG.flush();
                    // Prioritization
                    {
                        if (eventRaceDetector.getAllPossibleERs() < 1000_000) { // TODO: configurable
                            Set<MethodRace> methodRaces = new HashSet<>();
                            for (MethodRace race : reachableMethodRaceFilter.getRaces()) {
                                if (race.isUAF() && race.isInitiatedByInvoke()) {
                                    continue;
                                }
                                race.setPriority(MethodRace.PRIORITY_NOT_INITIATED_BY_INVOKE);
                                methodRaces.add(race);
                            }
                            RaceDetector<MethodRace> tmpRaceDetector = () -> methodRaces;

                            Set<C3GTask> undeterminedThreadC3GTasks = new HashSet<>();
                            for (C3GTask c3GTask : c3GManager.getAllC3GTasks()) {
                                if (c3GTask.getThreadLattice().equals(ThreadLatticeManager.getUNDETERMINED())) {
                                    undeterminedThreadC3GTasks.add(c3GTask);
                                    c3GTask.setThreadLattice(ThreadLatticeManager.getUIThreadLattice());
                                }
                            }
                            IfGuardNullAtEndFilter undeterminedIfGuardNullAtEndFilter = new IfGuardNullAtEndFilter(tmpRaceDetector, c3GManager, componentCaCGMAnager, true);
                            if (undeterminedIfGuardNullAtEndFilter.getRaces().size() > 0) {
                                shb.computeSVC();
                                EventRaceDetector undeterminedEventRaceDetector = new EventRaceDetector(undeterminedIfGuardNullAtEndFilter, c3GManager, componentCaCGMAnager, shb, true);
                                for (MethodRace race : undeterminedEventRaceDetector.getRaces())
                                    race.setPriority(MethodRace.PRIORITY_UNDETERMINED);

                                Set<C3GTask> unknownThreadC3GTasks = new HashSet<>();
                                for (C3GTask c3GTask : c3GManager.getAllC3GTasks()) {
                                    if (c3GTask.getThreadLattice().equals(ThreadLatticeManager.getUNKNOWNThreadLattice())) {
                                        unknownThreadC3GTasks.add(c3GTask);
                                        c3GTask.setThreadLattice(ThreadLatticeManager.getUIThreadLattice());
                                    }
                                }
                                IfGuardNullAtEndFilter unknownIfGuardNullAtEndFilter = new IfGuardNullAtEndFilter(undeterminedEventRaceDetector, c3GManager, componentCaCGMAnager, true);
                                if (undeterminedIfGuardNullAtEndFilter.getRaces().size() > 0) {
                                    shb.computeSVC();
                                    EventRaceDetector unknownEventRaceDetector = new EventRaceDetector(unknownIfGuardNullAtEndFilter, c3GManager, componentCaCGMAnager, shb, true);
                                    for (MethodRace race : unknownEventRaceDetector.getRaces())
                                        race.setPriority(MethodRace.PRIORITY_UNKNOWN);
                                    unSoundEventRaceAggregator.aggregate(unknownEventRaceDetector, unknownIfGuardNullAtEndFilter);
                                }
                                for (C3GTask c3GTask : unknownThreadC3GTasks)
                                    c3GTask.setThreadLattice(ThreadLatticeManager.getUNKNOWNThreadLattice());

                            }
                            for (C3GTask c3GTask : undeterminedThreadC3GTasks)
                                c3GTask.setThreadLattice(ThreadLatticeManager.getUNDETERMINED());
                        }
                    }
                }
                int reachableERs = 0;
                for (MethodRace race : eventRaceAggregator.getRaces())
                    if ((race.getPriority() & MethodRace.PRIORITY_REACHABLE) == MethodRace.PRIORITY_REACHABLE)
                        reachableERs++;
                LOG.logln(String.format("Filtered ERs: %d, With paths: %d", eventRaceAggregator.getRaces().size(), eventRaceAggregator.getAllEventRaces().size()), LOG.ESSENTIAL);
                LOG.logln(String.format("Prioritized ERs: %d", reachableERs), LOG.ESSENTIAL);

            } else {
                IfGuardNullAtEndFilter ifGuardNullAtEndFilter = new IfGuardNullAtEndFilter(myReachableMethodRaceFilter, myC3GManager, myC2GManager);
                if (ifGuardNullAtEndFilter.getRaces().size() > 0) {
                    LOG.startTimer("On-Demand HB Analysis", LOG.VERBOSE);
                    AbstractOnDemandSHB shb = null;
                    if (myC3GManager.getAllC3GTasks().size() < 10_000) // TODO: configurable
                        shb = new FastOnDemandSHB(myC3GManager);
                    else
                        shb = new MemSaveOnDemandSHB(myC3GManager);
                    shb.computeSVC();
                    LOG.endTimer("On-Demand HB Analysis", LOG.VERBOSE);
                    EventRaceDetector eventRaceDetector = new EventRaceDetector(ifGuardNullAtEndFilter, myC3GManager, myC2GManager, shb);
                    eventRaceAggregator.aggregate(eventRaceDetector, ifGuardNullAtEndFilter);
                    LOG.logln(String.format("All ERs: %d, With paths: %d", eventRaceAggregator.getRaces().size(), eventRaceAggregator.getAllEventRaces().size()), LOG.ESSENTIAL);
                    LOG.flush();
                }
            }

            reportRaces(eventRaceAggregator);

        } catch (Exception e) {
            LOG.logln(e.toString(), LOG.ERROR);
            for (StackTraceElement st : e.getStackTrace()) {
                LOG.logln(st.toString(), LOG.ERROR);
            }
        }
    }

    private static void reportRaces(EventRaceAggregator eventRaceAggregator) {
        if (reportPath != null) {
            Map<SootMethod, Map<SootMethod, Map<SootField, Set<MethodRace>>>> myMap = new HashMap<>();
            try (FileWriter out = new FileWriter(reportPath + "/" + "races.csv")) {
                try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader("Id", "Priority", "Race Description", "IfGuard", "NullAtEnd", "InitiatedByInvoke", "Write Threads", "Read Threads", "WriteMethod", "ReadMethod", "Field"))) {
                    AtomicInteger index = new AtomicInteger();
                    eventRaceAggregator.getRaces().stream().sorted((methodRace, t1) -> t1.getPriority() - methodRace.getPriority()).forEach((methodRace) -> {
                        try {
                            index.getAndIncrement();
                            String ERMessage = "";//String.format("#ER: %d\n", c);
                            ERMessage += "Write Event: " + methodRace.getWriteEvent() + "\n";
                            Stmt writeStmt = methodRace.getWriteStmt();
                            String writeStmtLine = "";
                            if (writeStmt != null && writeStmt.getJavaSourceStartLineNumber() > 0) {
                                writeStmtLine = Integer.toString(writeStmt.getJavaSourceStartLineNumber());
                            }
                            ERMessage += "Write Stmt: " + writeStmtLine + ": " + methodRace.getWriteStmt().toString() + "\n";
                            ERMessage += "Read Event: " + methodRace.getReadEvent() + "\n";
                            Stmt readStmt = methodRace.getReadStmt();
                            String readStmtLine = "";
                            if (readStmt != null && readStmt.getJavaSourceStartLineNumber() > 0) {
                                readStmtLine = Integer.toString(readStmt.getJavaSourceStartLineNumber());
                            }
                            ERMessage += "Read Stmt: " + readStmtLine + ": " + methodRace.getReadStmt().toString() + "\n";
                            ERMessage += "Memory: " + methodRace.getMemoryId();
                            String writeThreads = "";
                            for (ThreadLattice threadLattice : eventRaceAggregator.getThreads(methodRace.getWriteEvent()))
                                writeThreads += threadLattice.toString() + " ";
                            String readThreads = "";
                            for (ThreadLattice threadLattice : eventRaceAggregator.getThreads(methodRace.getReadEvent()))
                                readThreads += threadLattice.toString() + " ";

                            printer.printRecord(index, methodRace.getPriority(), ERMessage, methodRace.isIfGuard(), methodRace.isNullAtEnd(), methodRace.isInitiatedByInvoke(), writeThreads, readThreads, methodRace.getWriteEvent(), methodRace.getReadEvent(), methodRace.getMemoryId());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private static Set<SootMethod> eventRaceSetupSoot() {
        LOG.startTimer("FlowDroid Setup", LOG.VERBOSE);
        Util.v().processManifest(apkPath);

        final InfoflowAndroidConfiguration config = new InfoflowAndroidConfiguration();
        config.getAnalysisFileConfig().setTargetAPKFile(apkPath);
        config.getAnalysisFileConfig().setAndroidPlatformDir(androidJar);
        SetupApplication app = new SetupApplication(config);
        LOG.logln(String.format("ICC config is set: %b", app.getConfig().getIccConfig().isIccEnabled()), LOG.VERBOSE);
        app.constructCallgraph();
        if (Scene.v().getPointsToAnalysis() instanceof DumbPointerAnalysis) {
            try {
                Method method = SetupApplication.class.getDeclaredMethod("constructCallgraphInternal");
                method.setAccessible(true);
                method.invoke(app);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        LOG.logln(Scene.v().getPointsToAnalysis().toString(), LOG.VERBOSE);
        int c = 0;
        int m = 0;
        int j = 0;
        for (SootClass sc : Scene.v().getClasses()) {
            for (SootMethod sm : sc.getMethods()) {
                if (Scene.v().getReachableMethods().contains(sm)) {
                    c++;
                    if (sm.getDeclaringClass().getName().startsWith("java") || sm.getDeclaringClass().getName().startsWith("android") || sm.getDeclaringClass().getName().startsWith("androidx"))
                        j++;
                }
                if (Util.v().isAPKMethod(sm))
                    m++;
            }
        }
        LOG.logln(String.format("Reachable Methods: %d, APK Methods: %d, PossibleLib : %d", c, m, j), LOG.ESSENTIAL);
        Util.v().setCalledMethods(app);

        Set<SootMethod> allAPKMethods = new HashSet<>();
        // We retrieve the body of methods that were not retrieved by FlowDroid
        for (SootClass sootClass : Scene.v().getClasses()) {
            for (SootMethod sootMethod : sootClass.getMethods()) {

                if (!sootMethod.hasActiveBody()) {
                    if (!(sootMethod.getDeclaringClass().getName().startsWith("java") || sootMethod.getDeclaringClass().getName().startsWith("android"))) {
                        try {
                            sootMethod.retrieveActiveBody();
                        } catch (Exception ignored) {

                        }
                    }
                }
                allAPKMethods.add(sootMethod);
            }
        }
        LOG.endTimer("FlowDroid Setup", LOG.ESSENTIAL);
        return allAPKMethods;
    }

    private static void setOptions(String[] args) {
        androidJar = args[0];
        apkPath = args[1];

        if (args.length > 2) {
            reportPath = args[2];
            LOG.init(reportPath);
        }
        if (args.length > 3) {
            try {
                int v = Integer.parseInt(args[3]);
                LOG.setLogLevel(v);
            } catch (Exception ignored) {
                System.out.println("LOG level was not correct " + args[3]);
            }
        }

        if (args.length > 4) {
            UAF = Boolean.parseBoolean(args[4]);
        }
        if (args.length > 5) {
            componentIsolation = Boolean.parseBoolean(args[5]);
        }
    }
}
