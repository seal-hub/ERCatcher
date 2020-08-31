package com.ercatcher.ConcurrencyAnalysis.C2G;

import com.ercatcher.Util;
import com.ercatcher.ConcurrencyAnalysis.CBox;
import com.ercatcher.LOG;
import com.ercatcher.ConcurrencyAnalysis.AbstractNode;
import com.ercatcher.ConcurrencyAnalysis.LibraryCaSFGenerator;
import com.ercatcher.ConcurrencyAnalysis.CSF.MethodBox;
import com.ercatcher.ConcurrencyAnalysis.CSF.UInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.CSF.UNode;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.scalar.Pair;

import java.util.*;

public class InterMethodBox extends CBox<CNode, MethodBox> {
    boolean isSSC = false;
    boolean canBeAsync = false;
    List<InterMethodBox> incomingIMBs = new ArrayList<>();
    List<InterMethodBox> outgoingIMBs = new ArrayList<>();
    private InterMethodBox sscParent = null;

    InterMethodBox(MethodBox methodBox) {
        super(methodBox);
    }

    public boolean isSSC() {
        return isSSC;
    }

    public List<InterMethodBox> getIncomingIMBs() {
        return incomingIMBs;
    }

    public List<InterMethodBox> getOutgoingIMBs() {
        return outgoingIMBs;
    }

    public void setCanBeAsync() {
        this.canBeAsync = true;
    }

    Set<InterMethodBox> dfsAllOutgoingEdges(SootMethod method, Set<SootMethod> visited, Map<SootMethod, InterMethodBox> methodToInterMethodBox) {
        Set<InterMethodBox> interMethodBoxes = new HashSet<>();
        if (visited.contains(method))
            return interMethodBoxes;
        visited.add(method);
        for (Iterator<Edge> it = Scene.v().getCallGraph().edgesOutOf(method); it.hasNext(); ) {
            Edge edge = it.next();
            InterMethodBox target = methodToInterMethodBox.getOrDefault(edge.tgt(), null);
            if (target == null)
                interMethodBoxes.addAll(dfsAllOutgoingEdges(edge.tgt(), visited, methodToInterMethodBox));
            else
                interMethodBoxes.add(target);
        }
        return interMethodBoxes;
    }

    void summarizeSSC(List<InterMethodBox> sscIMBs) {
        this.isSSC = true;
        start = CNode.newStart(null);
        end = CNode.newEnd(null);
        for (InterMethodBox imb : sscIMBs) {
            CInvokeNode cInvokeNode = new CInvokeNode(null, imb, imb.canBeAsync);
            start.addChild(cInvokeNode);
            imb.sscParent = this;
//            cInvokeNode.addChild(end);
        }
        setAllNodes();
    }

    void updateTargets(Map<InterMethodBox, InterMethodBox> newDAGIMBMap) {
        if (!isSSC) {
            for (CNode cNode : allNodes) {
                if (cNode instanceof CInvokeNode) {
                    CInvokeNode cInvokeNode = (CInvokeNode) cNode;
                    InterMethodBox target = newDAGIMBMap.get((InterMethodBox) cInvokeNode.getTarget());
                    if (target.isSSC)
                        cInvokeNode.setAsyncInvoke(false);
                    if (sscParent != null)
                        if (target == sscParent)
                            target = null;
                    if (target == this)
                        target = null;
                    cInvokeNode.setTarget(target);
                }
            }
        }
        end.removeMySelf();
        cleanNodes();
        setAllNodes();
        outgoingIMBs.clear();
        incomingIMBs.clear();
    }

    void summarize(Map<SootMethod, InterMethodBox> methodToInterMethodBox, Set<LibraryCaSFGenerator> libraryCaSFGeneratorSet) {
        Map<CKey, CNode> uToCNode = new HashMap<>();
        // Generate summary from uNodes
        start = CNode.newStart(getSource().getStart());
        end = CNode.newEnd(getSource().getEnd());
        uToCNode.put(new CKey(getSource().getStart(), null), start);
        uToCNode.put(new CKey(getSource().getEnd(), null), end);
        List<CNode> cQueue = new ArrayList<>();
        cQueue.add(start);
        Set<CNode> visited = new HashSet<>();
        for (int i = 0; i < cQueue.size(); i++) {
            CNode current = cQueue.get(i);
            if (visited.contains(current))
                continue;
            visited.add(current);
            if (current == end)
                continue;
            if (current instanceof CInvokeNode) {
                InterMethodBox interMethodBox = ((CInvokeNode) current).getTarget();
                if (interMethodBox != null)
                    Scene.v();
            }
            List<UNode> children = new ArrayList<>(current.getSource().getChildren());
            Set<UNode> childVisited = new HashSet<>();
            for (int j = 0; j < children.size(); j++) {
                UNode uChild = children.get(j);
                Unit unit = uChild.getSource();
                if (unit == null) {
                    if (uChild == getSource().getEnd() || childVisited.contains(uChild))
                        continue;
                    throw new RuntimeException("Why?");
                }
                UInvokeNode uInvokeChild = (UInvokeNode) uChild;
                if (childVisited.contains(uChild))
                    continue;
                childVisited.add(uChild);
                boolean hasAsyncCall = false;
                for (Iterator<Edge> it = Scene.v().getCallGraph().edgesOutOf(unit); it.hasNext(); ) {
                    Edge edge = it.next();
                    Pair<SootMethod, Boolean> invokeInfo = getInvokeInfo(edge, uInvokeChild, methodToInterMethodBox, libraryCaSFGeneratorSet);
                    if (invokeInfo.getO2()) {
                        hasAsyncCall = true;
                        break;
                    }
                }
                boolean atLeastOneChild = false;
                for (Iterator<Edge> it = Scene.v().getCallGraph().edgesOutOf(unit); it.hasNext(); ) {
                    atLeastOneChild = true;
                    Edge edge = it.next();
                    Pair<SootMethod, Boolean> invokeInfo = getInvokeInfo(edge, uInvokeChild, methodToInterMethodBox, libraryCaSFGeneratorSet);
                    if (hasAsyncCall && !invokeInfo.getO2())
                        continue;
                    Set<InterMethodBox> targets = new HashSet<>();
                    boolean asyncInvoke = invokeInfo.getO2();
                    if (!goodEdge(edge, invokeInfo)) {
                        targets.add(null);
                        asyncInvoke = false;
                    } else {
                        InterMethodBox target = methodToInterMethodBox.getOrDefault(invokeInfo.getO1(), null);
                        targets.add(target);
                        if (target == null) {
                            targets.addAll(dfsAllOutgoingEdges(invokeInfo.getO1(), new HashSet<>(), methodToInterMethodBox));
                        }
                    }
                    for (InterMethodBox pTarget : targets) {
                        CKey key = new CKey(uInvokeChild, pTarget);
                        if (!uToCNode.containsKey(key)) {
                            CInvokeNode childCInvokeNode = new CInvokeNode(uInvokeChild, pTarget, asyncInvoke);
                            uToCNode.put(key, childCInvokeNode);
                            cQueue.add(childCInvokeNode);
                        }
                        current.addChild(uToCNode.get(key));
                    }
                }
                for (MethodBox targetBoxes : uInvokeChild.getTargets()) {
                    atLeastOneChild = true;
                    InterMethodBox pTarget = methodToInterMethodBox.get(targetBoxes.getSource());
                    CKey key = new CKey(uInvokeChild, pTarget);
                    if (!uToCNode.containsKey(key)) {
                        CInvokeNode childCInvokeNode = new CInvokeNode(uInvokeChild, pTarget, uInvokeChild.isAsync());
                        uToCNode.put(key, childCInvokeNode);
                        cQueue.add(childCInvokeNode);
                    }
                    current.addChild(uToCNode.get(key));
                }
                if (!atLeastOneChild) {
                    children.addAll(uChild.getChildren());
                }
            }
        }
        cleanNodes();
        setAllNodes();
    }

    private boolean goodEdge(Edge edge, Pair<SootMethod, Boolean> invokeInfo) {
        if (true)
            return true;
        if (invokeInfo.getO2())
            return true;
        SootClass sc = edge.tgt().getDeclaringClass();
        if (sc.getName().startsWith("java.")) {
            if (sc.getName().startsWith("java.lang.")) {
                if (sc.getName().startsWith("java.lang.Thread"))
                    return true;
                return false;
            }
            if (sc.getName().startsWith("java.util.")) {
                if (sc.getName().startsWith("java.util.HashSet") || sc.getName().startsWith("java.util.HashMap") || sc.getName().startsWith("java.util.ArrayList") || sc.getName().startsWith("java.util.Arrays") || sc.getName().startsWith("java.util.LinkedHashMap") || sc.getName().startsWith("java.util.LinkedHashSet"))
                    return false;
                return true;
            }
        }
        return true;
    }

    protected void cleanNodes() {
        List<CNode> cQueue = new ArrayList<>();
        Set<CNode> visited = new HashSet<>();
        for (AbstractNode child : start.getChildren())
            cQueue.add((CNode) child);
        for (int i = 0; i < cQueue.size(); i++) {
            CNode current = cQueue.get(i);
            if (visited.contains(current))
                continue;
            visited.add(current);
            if (!(current instanceof CInvokeNode)) {
                throw new RuntimeException("Why?");
            }
            for (AbstractNode child : current.getChildren())
                cQueue.add((CNode) child);
            CInvokeNode cInvokeNode = (CInvokeNode) current;
            if (cInvokeNode.getTarget() == null) {
                current.removeMySelf();
                continue;
            }
        }
    }

    // TODO
    @Override
    public void setAllNodes() {
        super.setAllNodes();
        for (CNode cNode : allNodes)
            cNode.setMyInterMethodBox(this);
    }

    private Pair<SootMethod, Boolean> getInvokeInfo(Edge edge, UInvokeNode uNode, Map<SootMethod, InterMethodBox> methodInterMethodBoxMap, Set<LibraryCaSFGenerator> libraryCaSFGeneratorSet) {
        if (edge.tgt().getName().equals("<init>") || edge.tgt().getName().equals("<clinit>"))
            return new Pair<>(edge.tgt(), false);
        if (!edge.src().getDeclaringClass().getName().equals("dummyMainClass")) {
            if (Util.v().getNewCallBackMethods().contains(edge.tgt())) {
                return new Pair<>(edge.tgt(), true);
            }
        }
        for (LibraryCaSFGenerator libCaSFGen : libraryCaSFGeneratorSet) {
            InterMethodBox sourceIMB = methodInterMethodBoxMap.get(edge.src());
            InterMethodBox targetIMB = methodInterMethodBoxMap.get(edge.tgt());
            if (sourceIMB == null || targetIMB == null) {
                continue; // TODO: isn't it strange?
            }
            if (libCaSFGen.canDetectTarget(sourceIMB.getSource(), uNode, targetIMB.getSource())) {
                List<Pair<InterMethodBox, Boolean>> res = libCaSFGen.detectTargets(sourceIMB.getSource(), uNode, targetIMB.getSource(), methodInterMethodBoxMap);
                if (res == null || res.size() != 1) {
                    LOG.logln(String.format("LibCaSFGen %s couldn't detect the target of %s", libCaSFGen, edge), LOG.ERROR);
                } else {
                    return new Pair<>(res.get(0).getO1().getSource().getSource(), res.get(0).getO2());
                }
            }
        }
        boolean isCallback = edge.kind().isFake();
        if (edge.src().getDeclaringClass().getName().equals("dummyMainClass")) {
//            if(edge.src().getName().equals("dummyMainMethod"))
//                isCallback = false;
//            else
            if (edge.tgt().getName().equals("onCreate") || edge.tgt().getName().equals("onRestart") || edge.tgt().getName().equals("onStart") || edge.tgt().getName().equals("onResume"))
                isCallback = false;
            else
                isCallback = true;
        }
        if (uNode instanceof UInvokeNode && ((UInvokeNode) uNode).isAsync())
            isCallback = true;
        return new Pair<>(edge.tgt(), isCallback);
    }

    public boolean isEmpty() {
        return start.getChildren().contains(end);
    }

    @Override
    public String toString() {
        return "I-" + getSource().toString();
    }

    private static class CKey {
        UNode uNode;
        InterMethodBox target;
        CKey(UNode uNode, InterMethodBox target) {
            this.uNode = uNode;
            this.target = target;
        }

        @Override
        public int hashCode() {
            int h = this.uNode.hashCode();
            h = h * 31 + ((this.target == null) ? 7 : this.target.hashCode());
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CKey))
                return false;
            CKey other = (CKey) obj;
            if (this.uNode != other.uNode)
                return false;
            if (this.target == null && other.target == null)
                return true;
            return this.target == other.target;
        }
    }
}
