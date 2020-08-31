package com.ercatcher.ConcurrencyAnalysis.CSF;

import com.ercatcher.LOG;
import com.ercatcher.Util;
import com.ercatcher.ConcurrencyAnalysis.*;
import com.ercatcher.memory.MemoryAnalysis;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CSFManager {
    private Map<SootMethod, MethodBox> methodToMethodBox = new HashMap<>();
    private MemoryAnalysis memoryAnalysis;
    private Set<SootMethod> flowDroidReachableMethods = new HashSet<>();
    private Set<LibraryCaSFGenerator> libraryCaSFGenerators = new HashSet<>();

    public CSFManager(Set<SootMethod> allMethods, MemoryAnalysis memoryAnalysis){
        LOG.startTimer("CaSF Generation", LOG.VERBOSE);
        this.memoryAnalysis = memoryAnalysis;
        for(SootMethod apkMethod : allMethods){
            if(Scene.v().getReachableMethods().contains(apkMethod))
                flowDroidReachableMethods.add(apkMethod);
            MethodBox methodBox = new MethodBox(apkMethod, this.memoryAnalysis.getAllocInfo(apkMethod));
            methodToMethodBox.put(apkMethod, methodBox);
        }
        CallGraphUtil.improveCallGraph();

        addLibraryCaSFGenerators(allMethods);

        summarizeMethodBoxes();
        LOG.endTimer("CaSF Generation", LOG.ESSENTIAL);
    }

    public Set<LibraryCaSFGenerator> getLibraryCaSFGenerators() {
        return libraryCaSFGenerators;
    }

    public Set<SootClass> addedServices = new HashSet<>();

    private void addLibraryCaSFGenerators(Set<SootMethod> allMethods) {
        Set<SootClass> allClasses = new HashSet<>();
        for(SootMethod apkMethod : allMethods)
            allClasses.add(apkMethod.getDeclaringClass());
        for(SootClass sootClass : allClasses){
            if(Util.v().isSubclass(sootClass.getType(), "android.app.Service")){
                if(!Util.v().isSubclass(sootClass.getType(), "android.app.IntentService")) {
                    addedServices.add(sootClass);
                    LibraryCaSFGenerator libCaSFGenerator = new ServiceCaSFGenerator(sootClass);
                    for(MethodBox mb : libCaSFGenerator.getAddedMethodBoxes()){
                        methodToMethodBox.put(mb.getSource(), mb);
                    }
                    libraryCaSFGenerators.add(libCaSFGenerator);
                }
            }
            if(Util.v().isSubclass(sootClass.getType(), "android.content.BroadcastReceiver")){
                libraryCaSFGenerators.add(new ReceiverCaSFGenerator(sootClass));
            }
            if(sootClass.getName().equals("android.app.IntentService")){
                libraryCaSFGenerators.add(new IntentServiceCaSFGenerator(sootClass));
            }
            if(sootClass.getName().equals("android.os.Handler")){
                libraryCaSFGenerators.add(new HandlerCaSFGenerator(sootClass));
            }
            if(sootClass.getName().equals("java.lang.Thread")){
                libraryCaSFGenerators.add(new ThreadCaSFGenerator(sootClass));
            }
            if(sootClass.getName().equals("android.os.AsyncTask")){
                libraryCaSFGenerators.add(new AsyncTaskCaSFGenerator(sootClass));
            }
            if(sootClass.getName().equals("java.util.TimerTask")){
                libraryCaSFGenerators.add(new TimerCaSFGenerator(sootClass));
            }

//            if(Util.v().isSubclass(sootClass.getType(), "android.os.AsyncTask")){
//                if(sootClass.toString().equals("android.os.AsyncTask")) {
//                    Set<MethodBox> res = AsyncTaskCaSFHelper.initClass(sootClass);
//                    for(MethodBox mb : res){
//                        methodToMethodBox.put(mb.getSource(), mb);
//                    }
//                }
//            }
        }
        SootClass dummyClass = Scene.v().getSootClass("dummyMainClass");
        LibraryCaSFGenerator libCaSFGenerator = new NopCaSFGenerator(dummyClass);
        for(MethodBox mb : libCaSFGenerator.getAddedMethodBoxes()){
            methodToMethodBox.put(mb.getSource(), mb);
        }
        libraryCaSFGenerators.add(libCaSFGenerator);
//        libraryCaSFGenerators.add();
    }

    public Set<SootMethod> getAllMethods(){
        return new HashSet<>(methodToMethodBox.keySet());
    }

    public Set<SootMethod> getFlowDroidReachableMethods(){
        return new HashSet<>(flowDroidReachableMethods);
    }

    public MethodBox getMethodBox(SootMethod sootMethod){
        return methodToMethodBox.getOrDefault(sootMethod, null);
    }

    public Set<MethodBox> getAllMethodBoxes(){
        return new HashSet<>(methodToMethodBox.values());
    }

    public void addMethodBox(MethodBox methodBox){
        methodToMethodBox.put(methodBox.getSource(), methodBox);
    }


    private void summarizeMethodBoxes() {
        LOG.startTimer("MethodBox Generation", LOG.VERBOSE);
        int c = 0;
        for(SootMethod apkMethod : methodToMethodBox.keySet()){
            if(apkMethod.getDeclaringClass().toString().contains("dummy"))
                methodToMethodBox.get(apkMethod).summarize();
        }
        for(SootMethod apkMethod : methodToMethodBox.keySet()){
            c++;
            if(c % 1000 == 0)
                LOG.log(String.format("%d-",c), LOG.SUPER_VERBOSE);
            if(apkMethod.getDeclaringClass().toString().contains("dummy"))
                continue;
            methodToMethodBox.get(apkMethod).summarize();
        }
        LOG.endTimer("MethodBox Generation", LOG.VERBOSE);
    }
}

