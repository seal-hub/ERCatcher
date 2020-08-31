package com.ercatcher.ConcurrencyAnalysis.C3G;

import com.ercatcher.LOG;
import com.ercatcher.RaceDetector.MethodRace;
import com.ercatcher.RaceDetector.Race;
import com.ercatcher.RaceDetector.RaceDetector;
import com.ercatcher.ConcurrencyAnalysis.MethodThreadLattice;
import com.ercatcher.ConcurrencyAnalysis.ThreadLattice;
import com.ercatcher.ConcurrencyAnalysis.ThreadLatticeManager;
import com.ercatcher.ConcurrencyAnalysis.C2G.CInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.C2GManager;
import com.ercatcher.ConcurrencyAnalysis.C2G.InterMethodBox;
import soot.SootMethod;

import java.util.*;

public class C3GManager {
    C2GManager myC2GManager;
    private Map<SootMethod, Set<C3GTask>> methodToCvent = new HashMap<>();
    private Map<SootMethod, Set<C3GTask>> methodToContextCvent = new HashMap<>();
    private Map<SootMethod, Set<C3GTask>> methodToMayCallInCvent = new HashMap<>();
    private Map<SootMethod, Set<SNode>> methodToMayCallInSNode = new HashMap<>();
    private Set<SNode> allSNodes = new HashSet<>();
    private Set<C3GTask> allC3GTasks = new HashSet<>();
    private List<ThreadLattice>  allThread = new ArrayList<>();
    private Set<SootMethod> methodsWithRace = new HashSet<>();
    private C3GTask rootC3GTask;

    private C3GManager(){}

    public C3GManager(C2GManager c2GManager, RaceDetector<MethodRace> raceDetector){
        LOG.startTimer("Event Extraction", LOG.VERBOSE);
        this.myC2GManager = c2GManager;
        for(Race race : raceDetector.getRaces()){
            methodsWithRace.add((SootMethod) race.getReadEvent());
            methodsWithRace.add((SootMethod) race.getWriteEvent());
        }
        int reachableRace = 0;
        for(SootMethod sootMethod : methodsWithRace){
            if(c2GManager.getReachableSootMethods().contains(sootMethod))
                reachableRace++;
        }
        LOG.logln(String.format("Reachable Method With Races: %d out of %d (All method with races: %d)", reachableRace, c2GManager.getReachableSootMethods().size(), methodsWithRace.size()), LOG.ESSENTIAL);
        rootC3GTask = expandEvents();
        resolveThreads(rootC3GTask);
        logFinalResult();
        LOG.endTimer("Event Extraction", LOG.ESSENTIAL);
    }

    public C3GManager getSubCventManager(C2GManager c2GManager){
        C3GManager c3GManager = new C3GManager();
        if(getMethodToCvent(c2GManager.getRoot().getSource().getSource()).size() > 1) {
            if(c2GManager.getRoot().getSource().getSource().getName().contains("redirector"))
                return c3GManager;
            throw new RuntimeException("Two Cvent for the entrypoint!");
        }
        if(getMethodToCvent(c2GManager.getRoot().getSource().getSource()).size() < 1){
            LOG.logln(String.format("This entryPoint %s doesn't have any Cvent", c2GManager.getRoot()), LOG.ERROR);
            return c3GManager;
        }

        c3GManager.myC2GManager = c2GManager;
        c3GManager.rootC3GTask = new ArrayList<>(getMethodToCvent(c2GManager.getRoot().getSource().getSource())).get(0);
        c3GManager.rootC3GTask.setThreadLattice(ThreadLatticeManager.getUNKNOWNThreadLattice()); // TODO: definitely a bad design
        c3GManager.allThread = this.allThread;
        for(SootMethod sootMethod : this.methodsWithRace){
            if(c3GManager.myC2GManager.getReachableSootMethods().contains(sootMethod))
                c3GManager.methodsWithRace.add(sootMethod);
        }
        c3GManager.initializeOtherInformation(c3GManager.rootC3GTask);
        c3GManager.logFinalResult();
        return c3GManager;
    }

    public Set<C3GTask> getMethodToCvent(SootMethod sootMethod) {
        return methodToCvent.getOrDefault(sootMethod, new HashSet<>());
    }

    public Set<C3GTask> getMethodToContextCvent(SootMethod sootMethod) {
        return methodToContextCvent.getOrDefault(sootMethod, new HashSet<>());
    }

    public Set<SNode> getMethodToMayCallInCvent(SootMethod sootMethod) {
        return methodToMayCallInSNode.getOrDefault(sootMethod, new HashSet<>());
    }

    public List<ThreadLattice> getAllThread() {
        return allThread;
    }

    public C3GTask getRootC3GTask() {
        return rootC3GTask;
    }

    public Set<C3GTask> getAllC3GTasks() {
        return allC3GTasks;
    }

    private void logFinalResult() {
        LOG.logln(String.format("Cvents: %d Threads: %d", allC3GTasks.size(), allThread.size()), LOG.ESSENTIAL);
        int[] tCount = new int[allThread.size()];
        for(C3GTask c3GTask : allC3GTasks){
            tCount[allThread.indexOf(c3GTask.getThreadLattice())]++;
            if(c3GTask.getThreadLattice().equals(ThreadLatticeManager.getUNKNOWNThreadLattice()))
                LOG.logln(String.format("Event in Unkown Thread: %s", c3GTask), LOG.SUPER_VERBOSE);
            if(c3GTask.getThreadLattice().equals(ThreadLatticeManager.getUNDETERMINED()))
                LOG.logln(String.format("Event in Undetermined Thread: %s", c3GTask), LOG.SUPER_VERBOSE);
        }
        LOG.log(String.format("<%d", tCount[0]), LOG.VERBOSE);
        for(int i=1; i<allThread.size(); i++)
            LOG.log(String.format(",%d", tCount[i]), LOG.VERBOSE);
        LOG.logln(">", LOG.VERBOSE);
    }

    private void resolveThreads(C3GTask rootC3GTask) {
        rootC3GTask.setThreadLattice(ThreadLatticeManager.getUNKNOWNThreadLattice());
        rootC3GTask.setChildrenThreadLattices(myC2GManager.getLibCaSFGenerators());
        fixPointMethodThreadResolution();
        this.allThread = new ArrayList<>();
        this.allThread.add(ThreadLatticeManager.getUIThreadLattice());
        this.allThread.add(ThreadLatticeManager.getUNKNOWNThreadLattice());
        this.allThread.add(ThreadLatticeManager.getUNDETERMINED());
        for(C3GTask c3GTask : allC3GTasks){
            if(this.allThread.contains(c3GTask.getThreadLattice()))
                continue;
            allThread.add(c3GTask.getThreadLattice());
        }
    }

    private C3GTask expandEvents()  {
        Map<InterMethodBox, C3GTask> imbToCventMap = new HashMap<>();
//        DirectedGraph<InterMethodBox> dgIMB = myCaCG.getIMBDirectedGraph();

        Map<InterMethodBox, Integer> incomingCount = new HashMap<>();
        for(InterMethodBox imb : myC2GManager.getReachableInterMethodBoxes()){
            incomingCount.put(imb, imb.getIncomingIMBs().size());
        }
        InterMethodBox rootIMB = myC2GManager.getRoot();
        incomingCount.put(rootIMB, 1);
        List<InterMethodBox> ordered = myC2GManager.topologicalOrder();
        int c = 0;
        for(InterMethodBox imb : ordered){
            if(Thread.interrupted())
                return null;
            if(!myC2GManager.getReachableInterMethodBoxes().contains(imb))
                continue;
            c++;
//            LOG.logln(String.format("%d-%s", c, imb), LOG.SUPER_VERBOSE);
            if(c % 100 == 0) {
                System.gc();
                LOG.log(String.format("%d-%s  ", c, imb), LOG.SUPER_VERBOSE);
            }
            LOG.logln(String.format("%d-%s %d", c, imb, imb.getOutgoingIMBs().size()), LOG.SUPER_VERBOSE);
            C3GTask c3GTask = new C3GTask(imb);
            c3GTask.summarize(imbToCventMap, methodsWithRace);

            imbToCventMap.put(imb, c3GTask);
            for(InterMethodBox outgoing : imb.getOutgoingIMBs()){
                incomingCount.put(outgoing, incomingCount.get(outgoing)-1);
                if(incomingCount.get(outgoing) <= 0) {
                    imbToCventMap.get(outgoing).destroy();
                    imbToCventMap.put(outgoing, null);
                }
            }
        }
        C3GTask root = imbToCventMap.get(rootIMB);
        initializeOtherInformation(root);
        return root;
    }

    private void initializeOtherInformation(C3GTask root) {
        allC3GTasks = root.getAllCvents();
        allSNodes = root.getAllChildrenSNodes();
        for (C3GTask c3GTask : allC3GTasks) {
            SootMethod sootMethod = c3GTask.getSource().getSource().getSource();
            if (!methodToCvent.containsKey(sootMethod))
                methodToCvent.put(sootMethod, new HashSet<>());
            methodToCvent.get(sootMethod).add(c3GTask);
            for (CInvokeNode cInvokeNode : c3GTask.getSynchCallers()) {
                SootMethod target = cInvokeNode.getTarget().getSource().getSource();
                if (!methodToContextCvent.containsKey(target))
                    methodToContextCvent.put(target, new HashSet<>());
                methodToContextCvent.get(target).add(c3GTask);
            }
        }
        for (SNode sNode : allSNodes) {
            for (Set<InterMethodBox> imbSet : sNode.mayBeCalledBeforeMeMap.values()) {
                for (InterMethodBox imb : imbSet) {
                    SootMethod mayCallee = imb.getSource().getSource();
                    if (!methodToMayCallInSNode.containsKey(mayCallee))
                        methodToMayCallInSNode.put(mayCallee, new HashSet<>());
                    methodToMayCallInSNode.get(mayCallee).add(sNode);
                }
            }
        }
        Set<SootMethod> accessibleMethods = new HashSet<>();
        accessibleMethods.addAll(methodToContextCvent.keySet());
        accessibleMethods.addAll(methodToCvent.keySet());
        accessibleMethods.addAll(methodToMayCallInCvent.keySet());
        accessibleMethods.addAll(methodToMayCallInSNode.keySet());
        LOG.logln(String.format("Accessible Methods: %d, Reachable Methods: %d", accessibleMethods.size(), myC2GManager.getReachableInterMethodBoxes().size()), LOG.VERBOSE);
    }

    private void fixPointMethodThreadResolution() {
        boolean update = true;
        while (update) {
            update = false;
            for (C3GTask c3GTask : allC3GTasks) {
                if (c3GTask.getThreadLattice() instanceof MethodThreadLattice) {
                    SootMethod methodLattice = ((MethodThreadLattice) c3GTask.getThreadLattice()).getSootMethod();
                    if (methodToCvent.getOrDefault(methodLattice, new HashSet<>()).size() == 1) {
                        C3GTask threadC3GTask = (C3GTask) methodToCvent.get(methodLattice).toArray()[0];
                        c3GTask.setThreadLattice(threadC3GTask.getThreadLattice());
                        update = true;
                    }
                }
            }
        }
    }



}
