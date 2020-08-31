package com.ercatcher.ConcurrencyAnalysis.CSF;

import com.ercatcher.LOG;
import com.ercatcher.ConcurrencyAnalysis.CBox;
import com.ercatcher.ConcurrencyAnalysis.AbstractNode;
import com.ercatcher.memory.IntraAllocationAnalysis;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.toolkits.graph.*;

import java.util.*;

public class MethodBox extends CBox<UNode, SootMethod> {

    private LibraryMethodSummarizer myLibrarySummarizer = null;
    private Set<UInvokeNode> pendingNodes = new HashSet<>();
    private IntraAllocationAnalysis intraAllocationAnalysis;

    public Set<Type> getAccessibleTypes() {
        return accessibleTypes;
    }

    private Set<Type> accessibleTypes = new HashSet<>();

    MethodBox(SootMethod sootMethod, IntraAllocationAnalysis intraAllocationAnalysis){
        super(sootMethod);
        this.intraAllocationAnalysis = intraAllocationAnalysis;
    }

    public MethodBox(SootMethod sootMethod){
        this(sootMethod, IntraAllocationAnalysis.EMPTY);
    }

    public MethodBox(SootMethod sootMethod, LibraryMethodSummarizer libraryMethodSummarizer){
        this(sootMethod);
        this.myLibrarySummarizer = libraryMethodSummarizer;
    }

    public boolean isLibraryMethodBox() {
        return myLibrarySummarizer != null;
    }

    public Set<UInvokeNode> getPendingNodes() {
        return pendingNodes;
    }

    public IntraAllocationAnalysis getIntraAllocationAnalysis() {
        return intraAllocationAnalysis;
    }

    void summarize(){

        Map<Unit, UNode> unitToUNode = new HashMap<>();
        start = UNode.newStart();
        end = UNode.newEnd();
        if(isLibraryMethodBox()){
            Set<UNode> children = myLibrarySummarizer.summarize();
            if(children.size() == 0){
                start.addChild(end);
            }
            else {
                for (UNode child : children)
                    start.addChild(child);
                setAllNodes();
            }
            return;
        }
        if(!getSource().hasActiveBody()){
            start.addChild(end);
            return;
        }
        for(Unit unit : getSource().getActiveBody().getUnits()){
            if(unit instanceof AssignStmt && ((AssignStmt)unit).getRightOp() instanceof NewExpr){
                accessibleTypes.add(((AssignStmt)unit).getRightOp().getType());
            }
        }
        for(Local local : getSource().getActiveBody().getLocals())
            accessibleTypes.add(local.getType());

        List<UNode> uQueue = new ArrayList<>();
        Set<UNode> visited = new HashSet<>();
        // TODO: the threshold can be congfigurable
//        TODO: configurable
        if(getSource().getActiveBody().getUnits().size() < 500) {
            UnitGraph unitGraph = new CompleteUnitGraph(getSource().getActiveBody());
            DominatorTree<Unit> dTree = new DominatorTree<>(new MHGDominatorsFinder(unitGraph));
            DominatorNode<Unit> head = dTree.getHead();

            UNode headUNode = new UInvokeNode(head.getGode());
            unitToUNode.put(head.getGode(), headUNode);
            start.addChild(headUNode);
            // Transfer to UNodes

            uQueue.add(headUNode);
            visited = new HashSet<>();
            for (int i = 0; i < uQueue.size(); i++) {
                UNode current = uQueue.get(i);
                if (visited.contains(current))
                    continue;
                visited.add(current);
                for (DominatorNode<Unit> childDNode : dTree.getChildrenOf(dTree.getDode(current.getSource()))) {
                    if (!unitToUNode.containsKey(childDNode.getGode())) {
                        UNode child = new UInvokeNode(childDNode.getGode());
                        unitToUNode.put(child.getSource(), child);
                        uQueue.add(child);
                    }
                    current.addChild(unitToUNode.get(childDNode.getGode()));
                }
            }
        }
        else{
            for(Unit unit : getSource().getActiveBody().getUnits()){
                UInvokeNode uNode = new UInvokeNode(unit);
                unitToUNode.put(unit, uNode);
                start.addChild(uNode);
            }
        }
        // Clear non-invoke expressions
        cleanNodes();
        if(getSource().getDeclaringClass().getName().equals("dummyMainClass")){
            // TODO: FlowDroid 2.7
            if(getSource().getName().equals("dummyMainMethod")) {
                if (getSource().getDeclaringClass().getMethods().size() == 1) {
                    throw new RuntimeException("It seems FlowDroid version is 2.5");
                }
            }
            else {
                fixEntryPoints();
            }
        }
        setAllNodes();
    }

    private void fixEntryPoints() {
        if(getSource().getName().contains("redirector"))
            return;
        for (AbstractNode aComponentInit : start.getChildren()) {
            UNode componentInitNode = (UNode) aComponentInit;
            Local initBase = null;
            if (componentInitNode.getSource() instanceof InvokeStmt)
                initBase = (Local) ((InstanceInvokeExpr) ((Stmt) componentInitNode.getSource()).getInvokeExpr()).getBase();
            else if (componentInitNode.getSource() instanceof JAssignStmt) {
                initBase = (Local) ((JAssignStmt) componentInitNode.getSource()).getLeftOp();
            } else
                throw new RuntimeException("Why init unit is not new or init?");


            String[] lifeCycleMethodNames = new String[]{"onCreate", "onCreateView", "onViewCreated", "onActivityCreated", "onViewStateRestored", "onStart", "onRestoreInstanceState", "onPostCreate", "onResume", "onPostResume", "onPause", "onStop", "onSaveInstanceState", "onRestart", "onDestroy"};
            int onPauseIndex = Arrays.asList(lifeCycleMethodNames).indexOf("onPause");
            UNode[] lifeCycleUNodes = new UNode[lifeCycleMethodNames.length];
            List<UNode> myQueue = new ArrayList<>();
            Set<UNode> myVisited = new HashSet<>();
            Set<UNode> beforeOnCreateNodes = new HashSet<>();
            boolean seenLC = false;
            myQueue.add(componentInitNode);
            for (int i = 0; i < myQueue.size(); i++) {
                UNode uNode = myQueue.get(i);
                if (myVisited.contains(uNode))
                    continue;
                myVisited.add(uNode);
                for (int j = 0; j < lifeCycleMethodNames.length; j++) {
                    Stmt stmt = uNode.getSource();
                    if (stmt.containsInvokeExpr() && stmt.getInvokeExpr().getMethod().getName().equals(lifeCycleMethodNames[j])) {
                        if (((InstanceInvokeExpr) stmt.getInvokeExpr()).getBase().equals(initBase)) {
                            lifeCycleUNodes[j] = uNode;
                            seenLC = true;
                            break;
                        }
                    }
                }
                if (!seenLC)
                    beforeOnCreateNodes.add(uNode);
                for (AbstractNode child : uNode.getChildren())
                    myQueue.add((UNode) child);
            }
//            for (int i = 0; i < lifeCycleUNodes.length; i++) {
//                if (lifeCycleUNodes[i] == null)
//                    continue;
//                for (int j = i + 1; j < lifeCycleUNodes.length; j++) {
//                    if (lifeCycleUNodes[j] == null)
//                        continue;
//                    lifeCycleUNodes[i].addChild(lifeCycleUNodes[j]);
//                }
//            }
            Map<UNode, UNode> needNopNode = new HashMap<>();
            for (int i = 0; i < lifeCycleUNodes.length; i++) {
                if (lifeCycleUNodes[i] == null)
                    continue;
                if(lifeCycleMethodNames[i].equals("onCreate") || lifeCycleMethodNames[i].equals("onStart") || lifeCycleMethodNames[i].equals("onResume")){
                    UNode nopNode = lifeCycleUNodes[i].addNopNode();
                    needNopNode.put(nopNode, lifeCycleUNodes[i]);
                    lifeCycleUNodes[i] = nopNode;
                }
            }
            for (int i = 1; i < lifeCycleUNodes.length-1; i++) {
                if (lifeCycleUNodes[i] == null)
                    continue;
//                for (int j = i + 1; j < lifeCycleUNodes.length; j++) {
//                    if (lifeCycleUNodes[j] == null)
//                        continue;
                if(lifeCycleUNodes[0] != null)
                    lifeCycleUNodes[0].addChild(lifeCycleUNodes[i]);
                if(lifeCycleUNodes[lifeCycleUNodes.length-1] != null)
                    lifeCycleUNodes[i].addChild(lifeCycleUNodes[lifeCycleUNodes.length-1]);
//                }
            }
            for (UNode uNode : myVisited) {
                if (beforeOnCreateNodes.contains(uNode))
                    continue;
                boolean flag = false;
                if(needNopNode.containsValue(uNode))
                    continue;
                for (UNode lcNode : lifeCycleUNodes)
                    if (uNode.equals(lcNode)) {
                        flag = true;
                        break;
                    }
                if (flag) {
                    continue;
                }
                Local baseOrLeftOp = null;
                Stmt stmt = (Stmt) uNode.getSource();
                if(stmt.containsInvokeExpr()){
                    if(stmt.getInvokeExpr() instanceof InstanceInvokeExpr)
                        baseOrLeftOp = (Local) ((InstanceInvokeExpr) stmt.getInvokeExpr()).getBase();
                    if(stmt.getInvokeExpr().getMethod().getName().equals("onAttach"))
                        continue;
                }
                else if(stmt instanceof AssignStmt)
                    baseOrLeftOp = (Local) ((AssignStmt) stmt).getLeftOp();
                if(baseOrLeftOp != null) {
                    SootClass sc = Scene.v().getSootClass(baseOrLeftOp.getType().toString());
                    if (sc.implementsInterface("android.app.Application$ActivityLifecycleCallbacks"))
                        continue;
                }
                else{
                    LOG.logln(String.format("What is this call? %s", stmt), LOG.ERROR);
                    continue;
                }
                for (int i = 0; i < onPauseIndex; i++) {
                    if (lifeCycleUNodes[i] != null) {
                        lifeCycleUNodes[i].addChild(uNode);
                    }
                }
                for (int i = onPauseIndex; i < lifeCycleUNodes.length; i++) {
                    if (lifeCycleUNodes[i] != null) {
                        uNode.addChild(lifeCycleUNodes[i]);
                    }
                }
            }
            int onResumeIndex = Arrays.asList(lifeCycleMethodNames).indexOf("onResume");
            if(lifeCycleUNodes[onPauseIndex] != null && lifeCycleUNodes[onResumeIndex] != null){
                UInvokeNode resumeInvokeNode = new UInvokeNode(needNopNode.get(lifeCycleUNodes[onResumeIndex]).getSource(), true);
                List<UNode> copyChildren = new ArrayList<>(lifeCycleUNodes[onPauseIndex].getChildren());
                for(UNode child : copyChildren){
                    resumeInvokeNode.addChild(child);
                }
                lifeCycleUNodes[onPauseIndex].addChild(resumeInvokeNode);
            }
            int onRestartIndex = Arrays.asList(lifeCycleMethodNames).indexOf("onRestart");
            int onStartIndex = Arrays.asList(lifeCycleMethodNames).indexOf("onStart");
            if(lifeCycleUNodes[onRestartIndex] != null && lifeCycleUNodes[onStartIndex] != null){
                UInvokeNode startInvokeNode = new UInvokeNode(needNopNode.get(lifeCycleUNodes[onStartIndex]).getSource(), true);
                List<UNode> copyChildren = new ArrayList<>(lifeCycleUNodes[onRestartIndex].getChildren());
                for(UNode child : copyChildren){
                    startInvokeNode.addChild(child);
                }
                lifeCycleUNodes[onRestartIndex].addChild(startInvokeNode);
            }
        }
    }

    @Override
    protected void setAllNodes() {
        super.setAllNodes();
        for(UNode uNode : allNodes){
            if(uNode instanceof UInvokeNode && ((UInvokeNode)uNode).hasPendingTarget())
                pendingNodes.add((UInvokeNode) uNode);
        }
    }

    private void cleanNodes() {
        List<UNode> uQueue = new ArrayList<>();
        Set<UNode> visited;
        uQueue.clear();
        for(AbstractNode aNode : start.getChildren())
            uQueue.add((UNode) aNode);
//        uQueue.add(headUNode);
        visited = new HashSet<>();
        for(int i=0; i< uQueue.size(); i++){
            UNode current = uQueue.get(i);
            if(visited.contains(current))
                continue;
            visited.add(current);
            if(current.getSource() == null) {
                throw new RuntimeException("Why?");
            }
            for(AbstractNode child : current.getChildren())
                uQueue.add((UNode) child);
            Stmt stmt = (Stmt) current.getSource();
            if(!stmt.containsInvokeExpr() && !Scene.v().getCallGraph().edgesOutOf(stmt).hasNext()){
                current.removeMySelf();
                continue;
            }
        }
    }

    @Override
    public String toString() {
        if(!getSource().isDeclared()){
            return "MB-" + getSource().getName();
        }
        return "MB-"+getSource().toString();
    }

    public interface LibraryMethodSummarizer{
        Set<UNode> summarize();
    }
}
