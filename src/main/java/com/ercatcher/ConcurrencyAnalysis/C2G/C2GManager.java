package com.ercatcher.ConcurrencyAnalysis.C2G;

import com.ercatcher.LOG;
import com.ercatcher.ConcurrencyAnalysis.CSF.CSFManager;
import com.ercatcher.ConcurrencyAnalysis.LibraryCaSFGenerator;
import com.ercatcher.ConcurrencyAnalysis.CSF.MethodBox;
import soot.*;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.PseudoTopologicalOrderer;
import soot.toolkits.graph.StronglyConnectedComponents;

import java.util.*;

public class C2GManager {
    CSFManager myCaSFManager;
    DirectedGraph<InterMethodBox> dgIMB = null;
    private Set<InterMethodBox> sscInterMethodBoxes = new HashSet<>();
    private Map<SootMethod, InterMethodBox> methodToInterMethodBox = new HashMap<>();
    private Set<InterMethodBox> asynchInvokes;
    private Set<InterMethodBox> reachableInterMethodBoxes = new HashSet<>();
    private Set<SootMethod> reachableSootMethods = new HashSet<>();
    private InterMethodBox root;
    private C2GManager(){ }

    public C2GManager(CSFManager caSFManager){
        LOG.startTimer("CaCG Generation", LOG.VERBOSE);
        this.myCaSFManager = caSFManager;
        for(SootMethod apkMethod : this.myCaSFManager.getAllMethods()){
            MethodBox methodBox = this.myCaSFManager.getMethodBox(apkMethod);
            methodToInterMethodBox.put(apkMethod, new InterMethodBox(methodBox));
        }
        SootClass dummyClass = Scene.v().getSootClass("dummyMainClass");
        SootMethod dummyMethod = dummyClass.getMethodByName("dummyMainMethod");
        this.root = methodToInterMethodBox.get(dummyMethod);
        summarizeInterMethodBoxes();
//        for(CNode cNode : root.getAllNodes()){ // TODO
//            if(cNode instanceof CInvokeNode){
//                SootMethod targetMethod = ((CInvokeNode) cNode).getTarget().getSource().getSource();
//                if(targetMethod.getReturnType() instanceof VoidType)
//                    continue;
//                SootClass targetClass = Scene.v().getSootClass(targetMethod.getReturnType().toString());
//                if(caSFManager.addedServices.contains(targetClass)){
//                    cNode.removeMySelf();
//                }
//            }
//        }
//        this.root.setAllNodes();
        int not_in_FD = 0;
        int not_in_FD_has_active_body = 0;
        for(SootMethod sootMethod : getReachableSootMethods()){
            if(!myCaSFManager.getFlowDroidReachableMethods().contains(sootMethod) && !myCaSFManager.getMethodBox(sootMethod).isLibraryMethodBox()) {
                if(sootMethod.toString().contains("onServiceConnected") || sootMethod.toString().contains("onServiceDisconnected") || sootMethod.toString().contains("onReceive"))
                    continue;
                if(sootMethod.hasActiveBody()) {
                    LOG.logln(String.format("New Method: %s", sootMethod), LOG.ESSENTIAL);
                    not_in_FD_has_active_body++;
                }
                not_in_FD++;
            }
        }
        accesibleIMBs = new HashMap<>(methodToInterMethodBox);
        LOG.logln(String.format("New: %d, HasActiveBody: %d, FD: %d, Us: %d", not_in_FD, not_in_FD_has_active_body, myCaSFManager.getFlowDroidReachableMethods().size(), getReachableSootMethods().size()), LOG.ESSENTIAL);
        LOG.endTimer("CaCG Generation", LOG.ESSENTIAL);
    }
    public Map<SootMethod, InterMethodBox> accesibleIMBs = new HashMap<>();
    public C2GManager getSubCaCG(SootMethod entryPoint){
        C2GManager subC2GManager = new C2GManager();
        InterMethodBox entryIMB = getMethodToInterMethodBox().getOrDefault(entryPoint, null);
        if(entryIMB == null)
            return subC2GManager;
        subC2GManager.myCaSFManager = this.myCaSFManager;
        subC2GManager.root = entryIMB;
        for(InterMethodBox reachableIMB : this.getReachableIMBs(entryIMB)){
            subC2GManager.methodToInterMethodBox.put(reachableIMB.getSource().getSource(), reachableIMB);
        }
        subC2GManager.setReachableInterMethodBoxes();
        setAccessibleMethods(subC2GManager);
        return subC2GManager;
    }

    private void setAccessibleMethods(C2GManager subC2GManager) {
        Set<Type> accessibleTypes = new HashSet<>();

        for(InterMethodBox imb : subC2GManager.getReachableInterMethodBoxes()){
            if(!imb.isSSC())
                accessibleTypes.addAll(imb.getSource().getAccessibleTypes());
        }
        List<Type> queue = new ArrayList<>(accessibleTypes);
        for(int i=0; i< queue.size(); i++){
            Type type = queue.get(i);
            SootClass sootClass = Scene.v().getSootClass(type.toString());
            for(SootMethod sootMethod : sootClass.getMethods()){
                if(getMethodToInterMethodBox().containsKey(sootMethod)){
                    InterMethodBox interMethodBox = getMethodToInterMethodBox().get(sootMethod);
                    if(getReachableInterMethodBoxes().contains(interMethodBox))
                        continue;
                    for(Type newType : interMethodBox.getSource().getAccessibleTypes()){
                        if(!accessibleTypes.contains(newType)){
                            accessibleTypes.add(newType);
                            queue.add(newType);
                        }
                    }
                }
            }
        }
        subC2GManager.accesibleIMBs = new HashMap<>();
        for(Type type : accessibleTypes){
            SootClass sootClass = Scene.v().getSootClass(type.toString());
            for(SootMethod sootMethod : sootClass.getMethods()) {
                if (getMethodToInterMethodBox().containsKey(sootMethod)) {
                    InterMethodBox interMethodBox = getMethodToInterMethodBox().get(sootMethod);
                    if(getReachableInterMethodBoxes().contains(interMethodBox) && !subC2GManager.getReachableInterMethodBoxes().contains(interMethodBox))
                        continue;
                    subC2GManager.accesibleIMBs.put(interMethodBox.getSource().getSource(), interMethodBox);
                }
            }
        }
    }

    public Set<LibraryCaSFGenerator> getLibCaSFGenerators(){
        return myCaSFManager.getLibraryCaSFGenerators();
    }

    public Set<SootMethod> getReachableSootMethods(){
        return reachableSootMethods;
    }

    public List<InterMethodBox> topologicalOrder(){
        DirectedGraph<InterMethodBox> dgIMB = getIMBDirectedGraph();
        PseudoTopologicalOrderer<InterMethodBox> topologicalOrderer = new PseudoTopologicalOrderer<>();
        return topologicalOrderer.newList(dgIMB, true);
    }

    public InterMethodBox getRoot(){
        return root;
    }

    private void summarizeInterMethodBoxes() {
        LOG.startTimer("CaSF", LOG.VERBOSE);
        for(SootMethod method : myCaSFManager.getAllMethods()){
            if(myCaSFManager.getMethodBox(method).isLibraryMethodBox())
                continue;
            methodToInterMethodBox.get(method).summarize(methodToInterMethodBox, myCaSFManager.getLibraryCaSFGenerators());
        }
        for(SootMethod method :  myCaSFManager.getAllMethods()){
            if(myCaSFManager.getMethodBox(method).isLibraryMethodBox())
                methodToInterMethodBox.get(method).summarize(methodToInterMethodBox, myCaSFManager.getLibraryCaSFGenerators());
        }
        updateIncomingOutgoingIMBs();
        LOG.endTimer("CaSF", LOG.VERBOSE);
        LOG.startTimer("Cycle Elimination", LOG.VERBOSE);
        DirectedGraph<InterMethodBox> dgIMB = getIMBDirectedGraph();
        StronglyConnectedComponents scc_slow = new StronglyConnectedComponents(dgIMB);
        logIMBs(scc_slow);
        removeCycles(scc_slow);
        checkAcyclic();
        scc_slow = new StronglyConnectedComponents(getIMBDirectedGraph());
        logIMBs(scc_slow);
//        reportDepthAndPath();
        LOG.endTimer("Cycle Elimination", LOG.VERBOSE);
    }

    private void setReachableInterMethodBoxes() {
        reachableInterMethodBoxes = new HashSet<>();

        List<InterMethodBox> queue = new ArrayList<>();
        queue.add(getRoot());
        for(int i=0; i< queue.size(); i++){
            InterMethodBox current = queue.get(i);
            if(reachableInterMethodBoxes.contains(current))
                continue;
            reachableInterMethodBoxes.add(current);
            if(!current.isSSC) // TODO: I assumed SCC IMBs won't be queried for their SootMethods
                reachableSootMethods.add(current.getSource().getSource());
            queue.addAll(current.outgoingIMBs);
        }
        for(SootMethod sootMethod : methodToInterMethodBox.keySet()){
            if(Scene.v().getReachableMethods().contains(sootMethod))
                reachableSootMethods.add(sootMethod);
        }
        LOG.logln(String.format("Edges: %d", queue.size()), LOG.SUPER_VERBOSE);
    }

    private Set<InterMethodBox> getReachableIMBs(InterMethodBox entryPointIMB) {
        Set<InterMethodBox> result = new HashSet<>();
        List<InterMethodBox> queue = new ArrayList<>();
        queue.add(entryPointIMB);
        for(int i=0; i< queue.size(); i++){
            InterMethodBox current = queue.get(i);
            if(result.contains(current))
                continue;
            result.add(current);
            queue.addAll(current.outgoingIMBs);
        }
        return result;
    }

    private void removeCycles(StronglyConnectedComponents scc_slow) {
        Map<InterMethodBox, InterMethodBox> newDAGIMBMap = new HashMap<>();
        for(InterMethodBox imb : methodToInterMethodBox.values()){
            newDAGIMBMap.put(imb, imb);
        }
        int i = 0;
//        for(List<InterMethodBox> component : scc.getTrueComponents()){
        for(List component_obj : scc_slow.getComponents()){
            List<InterMethodBox> component = new ArrayList<>();
            for(Object c : component_obj)
                component.add((InterMethodBox) c);
            i++;
            if(component.size() > 1){
//            System.out.println(i + " " + component.size());
                SootMethod newSSCSootMethod = new SootMethod("SCC"+i, new ArrayList<>(), VoidType.v());
                MethodBox newSSMethodBox = new MethodBox(newSSCSootMethod);
                InterMethodBox newSSCInterMethodBox = new InterMethodBox(newSSMethodBox);
//            System.out.println(newSSCInterMethodBox);
                myCaSFManager.addMethodBox(newSSMethodBox);
                methodToInterMethodBox.put(newSSCSootMethod, newSSCInterMethodBox);
                newSSCInterMethodBox.summarizeSSC(component);
                newDAGIMBMap.put(newSSCInterMethodBox, newSSCInterMethodBox);
                for(InterMethodBox interMethodBox : component){
                    newDAGIMBMap.put(interMethodBox, newSSCInterMethodBox);
                }
                sscInterMethodBoxes.add(newSSCInterMethodBox);
            }
        }
        LOG.logln("Before Update", LOG.SUPER_VERBOSE);
        for(InterMethodBox imb : methodToInterMethodBox.values()){
            imb.updateTargets(newDAGIMBMap);
        }
        LOG.logln("After Update", LOG.SUPER_VERBOSE);
        updateIncomingOutgoingIMBs();
    }

    private void checkAcyclic() {
        DirectedGraph<InterMethodBox> dgIMB = getIMBDirectedGraph();
        PseudoTopologicalOrderer<InterMethodBox> topologicalOrderer = new PseudoTopologicalOrderer<>();
        List<InterMethodBox> ordered = topologicalOrderer.newList(dgIMB, true);
        Set<InterMethodBox> visited = new HashSet<>();
        for(int j=ordered.size()-1; j >= 0; j--){
            InterMethodBox imb = ordered.get(j);
            visited.add(imb);
            for(InterMethodBox child : imb.outgoingIMBs){
                if(visited.contains(child))
                    LOG.logln(String.format("@#@#@_find a cycle IMB: %s, CHILD: %s", imb, child), LOG.ERROR);
            }
        }
    }

    private void logIMBs(StronglyConnectedComponents scc_slow) {
        LOG.logln(String.format("--------------- %d", methodToInterMethodBox.size()), LOG.VERBOSE);
        List<InterMethodBox> unreachables = new ArrayList<>();
        List<InterMethodBox> heads = new ArrayList<>();
        for(InterMethodBox interMethodBox : methodToInterMethodBox.values()){
            if(interMethodBox.incomingIMBs.size() == 0) {
                if(interMethodBox.outgoingIMBs.size() > 0)
                    heads.add(interMethodBox);
                else
                    unreachables.add(interMethodBox);
            }
        }
        int asyncIMBInCycles = 0;
        int cyclesWithAsync = 0;
        int cycles = 0;
        for(List component_obj : scc_slow.getComponents()){
            List<InterMethodBox> component = new ArrayList<>();
            for(Object c : component_obj)
                component.add((InterMethodBox) c);

            if(component.size() > 1){
                cycles++;
                boolean flag = false;
                for(InterMethodBox interMethodBox : component) {
                    if (asynchInvokes.contains(interMethodBox)) {
                        flag = true;
                        asyncIMBInCycles++;
                    }
                }
                if(flag)
                    cyclesWithAsync++;
            }
        }
        int reachableAsyncIMBs = 0;
        for(InterMethodBox imb : asynchInvokes){
            if(reachableInterMethodBoxes.contains(imb))
                reachableAsyncIMBs++;
        }
        LOG.logln(String.format("Reachable Async IMB: %d", reachableAsyncIMBs), LOG.VERBOSE);
        LOG.logln(String.format("SCC Size %d", (scc_slow.getComponents().size()-unreachables.size())), LOG.VERBOSE);
        LOG.logln(String.format("Cycles %d", cycles), LOG.VERBOSE);
        LOG.logln(String.format("Asynch IMB in Cycles: %d", asyncIMBInCycles), LOG.VERBOSE);
        LOG.logln(String.format("Cycles with Asynch IMB: %d", cyclesWithAsync), LOG.VERBOSE);
        LOG.logln(String.format("Real Heads: %d", heads.size()), LOG.VERBOSE);
        if(heads.size() > 1){
            for(InterMethodBox head : heads) {
                if(head.getSource().getSource().getName().equals("<clinit>"))
                    continue;
                LOG.logln(String.format(" Head: %s", head), LOG.SUPER_VERBOSE);
            }
        }
    }

    private void updateIncomingOutgoingIMBs() {
        asynchInvokes = new HashSet<>();
        for (InterMethodBox interMethodBox : methodToInterMethodBox.values()) {
            interMethodBox.outgoingIMBs.clear();
            interMethodBox.incomingIMBs.clear();
        }
        for (InterMethodBox interMethodBox : methodToInterMethodBox.values()) {
            for (CNode cNode : interMethodBox.getAllNodes()) {
                if (cNode instanceof CInvokeNode) {
                    CInvokeNode cInvokeNode = (CInvokeNode) cNode;
                    InterMethodBox target = (InterMethodBox) cInvokeNode.getTarget();
                    if (cInvokeNode.isAsync()) {
                        asynchInvokes.add(target);
                        target.setCanBeAsync();
                    }
                    if(target == null)
                        throw new RuntimeException("The target must not be null.");
                    interMethodBox.outgoingIMBs.add(target);
                    target.incomingIMBs.add(interMethodBox);
                }
            }
        }
        for (InterMethodBox interMethodBox : methodToInterMethodBox.values()) {
            interMethodBox.outgoingIMBs = new ArrayList<>(new HashSet<>(interMethodBox.outgoingIMBs));
            interMethodBox.incomingIMBs = new ArrayList<>(new HashSet<>(interMethodBox.incomingIMBs));;
        }
        setReachableInterMethodBoxes();
    }

    private DirectedGraph<InterMethodBox> getIMBDirectedGraph() {
        if(dgIMB == null)
            dgIMB = new DirectedGraph<InterMethodBox>() {
                @Override
                public List<InterMethodBox> getHeads() {
                    List<InterMethodBox> heads = new ArrayList<>();
                    for(InterMethodBox interMethodBox : methodToInterMethodBox.values()){
                        if(interMethodBox.incomingIMBs.size() == 0) {
                            heads.add(interMethodBox);
                        }
                    }
                    return heads;
                }

                @Override
                public List<InterMethodBox> getTails() {
                    List<InterMethodBox> tails = new ArrayList<>();
                    for(InterMethodBox interMethodBox : methodToInterMethodBox.values()){
                        if(interMethodBox.outgoingIMBs.size() == 0)
                            tails.add(interMethodBox);
                    }
                    return tails;
                }

                @Override
                public List<InterMethodBox> getPredsOf(InterMethodBox interMethodBox) {
                    return new ArrayList<>(new HashSet<>(interMethodBox.incomingIMBs));
                }

                @Override
                public List<InterMethodBox> getSuccsOf(InterMethodBox interMethodBox) {
                    return new ArrayList<>(new HashSet<>(interMethodBox.outgoingIMBs));
                }

                @Override
                public int size() {
                    return methodToInterMethodBox.size();
                }

                @Override
                public Iterator<InterMethodBox> iterator() {
                    return methodToInterMethodBox.values().iterator();
                }
            };
        return dgIMB;
    }

    public Set<InterMethodBox> getReachableInterMethodBoxes() {
        return reachableInterMethodBoxes;
    }
    public Map<SootMethod, InterMethodBox> getMethodToInterMethodBox() {
        return methodToInterMethodBox;
    }
}
