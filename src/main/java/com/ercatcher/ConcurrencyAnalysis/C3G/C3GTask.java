package com.ercatcher.ConcurrencyAnalysis.C3G;

import com.ercatcher.ConcurrencyAnalysis.*;
import com.ercatcher.ConcurrencyAnalysis.C2G.CInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.CNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.InterMethodBox;
import soot.SootMethod;

import java.util.*;

public class C3GTask extends CBox<SNode, InterMethodBox> {
    static int CREATED = 0;
    private ThreadLattice threadLattice = null;
    private Set<InterMethodBox> mayCallIMBWithRaces = new HashSet<>(); // TODO: check if it is properly updated

    public List<CInvokeNode> getCallerSites() {
        return callerSites;
    }

    private List<CInvokeNode> callerSites = new ArrayList<>();
    private SInvokeNode callerSNode;
    private C3GTask callerC3GTask;

    C3GTask(InterMethodBox interMethodBox){
        super(interMethodBox);
        CREATED++;
    }

    public SInvokeNode getCallerSNode() {
        return callerSNode;
    }

    public C3GTask getCallerC3GTask() {
        return callerC3GTask;
    }

    public Set<InterMethodBox> getMayCallIMBWithRaces() {
        return mayCallIMBWithRaces;
    }

    private void updateMayCallIMBWithRaces(Set<SootMethod> methodWithRaces){
        mayCallIMBWithRaces = new HashSet<>();
        for(SNode sNode: allNodes) {
            if(sNode instanceof SInvokeNode) {
                InterMethodBox imb = ((SInvokeNode)sNode).getTarget().getSource();
                if(methodWithRaces.contains(imb.getSource().getSource()))
                    mayCallIMBWithRaces.add(imb);
                mayCallIMBWithRaces.addAll(((SInvokeNode)sNode).getTarget().getMayCallIMBWithRaces());
            }
            for(Set<InterMethodBox> imbSet : sNode.mayBeCalledBeforeMeMap.values()){
                for(InterMethodBox imb : imbSet)
                    if(methodWithRaces.contains(imb.getSource().getSource())){
                        mayCallIMBWithRaces.add(imb);
                    }
            }
        }
    }

    public void destroy(){
        super.destroy();
        callerSites = null;
        callerSNode = null;
        start = null;
        end = null;
        List<CBox> toDestroy = new ArrayList<>();
        for(SNode sNode : allNodes){
            if(sNode instanceof SInvokeNode){
                toDestroy.add(((SInvokeNode) sNode).getTarget());
            }
            sNode.setMyC3GTask(null);
            sNode.destroy();
        }
        for(CBox cBox : toDestroy){
            C3GTask c3GTask = (C3GTask) cBox;
            c3GTask.destroy();
        }
        allNodes = null;
    }

    List<CInvokeNode> getSynchCallers(){
        List<CInvokeNode> ret = new ArrayList<>();
        if(callerC3GTask == null)
            ret.addAll(callerSites);
        else{
            for(int i = callerC3GTask.callerSites.size(); i< callerSites.size(); i++)
                if(!callerSites.get(i).isAsync())
                    ret.add(callerSites.get(i));
        }
        return ret;
    }

    C3GTask clone(CInvokeNode callerSite, Set<InterMethodBox> visitedIMB, Set<SootMethod> methodWithRaces){
        if(visitedIMB.contains(this.getSource()))
            throw new RuntimeException("Circular");
        visitedIMB.add(this.getSource());
        Map<SKey, SNode> cToSNode = new HashMap<>();
        C3GTask myClone = new C3GTask(this.getSource());
        myClone.start = SNode.newStart(getSource().getStart());
        myClone.end = SNode.newEnd(getSource().getEnd());
        myClone.callerSites.add(callerSite);
        myClone.callerSites.addAll(this.callerSites);
//        myClone.mayCalleeMethodBoxes = new HashSet<>(mayCalleeMethodBoxes);
        Map<SNode, SNode> thisToCloneMap = new HashMap<>();
        thisToCloneMap.put(start, myClone.start);
        thisToCloneMap.put(end, myClone.end);
        List<SNode> sQueue = new ArrayList<>();
        Set<SNode> visited = new HashSet<>();
        sQueue.add(this.start);
        for(int i=0; i< sQueue.size(); i++){
            SNode current = sQueue.get(i);
            if(visited.contains(current))
                continue;
            if(current == end)
                continue;
            visited.add(current);
            List<AbstractNode> sChildren = new ArrayList<>(current.getChildren());
            Set<SNode> childVisited = new HashSet<>();
            for(int j=0; j<sChildren.size(); j++){
                SNode sChild = (SNode) sChildren.get(j);
                if(childVisited.contains(sChild))
                    continue;
                childVisited.add(sChild);
                if(sChild.equals(end)) {
                    thisToCloneMap.get(current).addChild(thisToCloneMap.get(end));
                    continue;
                }
                if(!(sChild instanceof SInvokeNode)){
                    throw new RuntimeException("Why?");
                }
                SInvokeNode childSInvokeNode = (SInvokeNode) sChild;
                if(thisToCloneMap.containsKey(childSInvokeNode)) {
                    thisToCloneMap.get(current).addChild(thisToCloneMap.get(childSInvokeNode));
                    continue;
                }
                C3GTask childTarget = (C3GTask) childSInvokeNode.getTarget();
                C3GTask cloneChildTarget = childTarget.clone(callerSite, new HashSet<>(visitedIMB), methodWithRaces);
                SInvokeNode newCloneSInvokeNode = new SInvokeNode((CInvokeNode) childSInvokeNode.getSource(), cloneChildTarget, childSInvokeNode.isAsync());
                cloneChildTarget.callerSNode = newCloneSInvokeNode;
                cloneChildTarget.callerC3GTask = myClone;
                thisToCloneMap.put(childSInvokeNode, newCloneSInvokeNode);
                thisToCloneMap.get(current).addChild(newCloneSInvokeNode);
                sQueue.add(childSInvokeNode);
            }
        }
        myClone.setAllNodes();
        for(SNode sNode : this.allNodes){
//            thisToCloneMap.get(sNode).mayBeCalledBeforeMe = new HashSet<>(sNode.mayBeCalledBeforeMe);
            for(SNode parent : sNode.mayBeCalledBeforeMeMap.keySet()){
                if(thisToCloneMap.get(parent) == null)
                    throw  new RuntimeException("Why it's null?");
                if(!thisToCloneMap.get(sNode).mayBeCalledBeforeMeMap.containsKey(thisToCloneMap.get(parent)))
                    thisToCloneMap.get(sNode).mayBeCalledBeforeMeMap.put(thisToCloneMap.get(parent), new HashSet<>());
                thisToCloneMap.get(sNode).mayBeCalledBeforeMeMap.get(thisToCloneMap.get(parent)).addAll(sNode.mayBeCalledBeforeMeMap.get(parent));
            }
        }
        myClone.updateMayCallIMBWithRaces(methodWithRaces);
        return myClone;
    }

    @Override
    protected void finalize() throws Throwable {
        CREATED--;
        super.finalize();
    }

    void summarize(Map<InterMethodBox, C3GTask> preComputedCvents, Set<SootMethod> methodWithRaces){
        Map<SKey, SNode> cToSNode = new HashMap<>();
        start = SNode.newStart(getSource().getStart());
        end = SNode.newEnd(getSource().getEnd());
        cToSNode.put(new SKey(getSource().getStart(), null), start);
        cToSNode.put(new SKey(getSource().getEnd(), null), end);
        List<SNode> sQueue = new ArrayList<>();
        Set<SNode> visited = new HashSet<>();
        sQueue.add(start);
        for(int i=0; i< sQueue.size(); i++){
            SNode current = sQueue.get(i);
            if(visited.contains(current))
                continue;
            visited.add(current);
            if(current == end)
                continue;
            List<AbstractNode> cChildren = new ArrayList<>(current.getSource().getChildren());
            Set<CNode> cVisited = new HashSet<>();
            for(int j=0; j<cChildren.size(); j++){
                CNode cChild = (CNode) cChildren.get(j);
                if(cVisited.contains(cChild))
                    continue;
                cVisited.add(cChild);
                if(cChild.equals(getSource().getEnd()))
                    continue;
                if(!(cChild instanceof CInvokeNode)){
                    throw new RuntimeException("Why?");
                }
                CInvokeNode childCInvokeNode = (CInvokeNode) cChild;
                InterMethodBox childTargetIMB = (InterMethodBox) childCInvokeNode.getTarget();
                if(!preComputedCvents.containsKey(childTargetIMB))
                    throw new RuntimeException("Why?");
                SKey key = new SKey(childCInvokeNode, childTargetIMB);
                if(cToSNode.containsKey(key)){
                    current.addChild(cToSNode.get(key));
                    continue;
                }
                C3GTask templateTarget = preComputedCvents.get(childTargetIMB);
                C3GTask myChildTarget = templateTarget.clone(childCInvokeNode, new HashSet<>(), methodWithRaces);
                if(!childCInvokeNode.isAsync()) {
                    if(methodWithRaces.contains(myChildTarget.getSource().getSource().getSource())) {
                        if(!myChildTarget.start.mayBeCalledBeforeMeMap.containsKey(current))
                            myChildTarget.start.mayBeCalledBeforeMeMap.put(current, new HashSet<>());
                        myChildTarget.start.mayBeCalledBeforeMeMap.get(current).add(myChildTarget.getSource());
                    }
                    cToSNode.put(key, myChildTarget.start);
                    myChildTarget.end.changeSource(childCInvokeNode); // Bad design :(
                    sQueue.add(myChildTarget.end);
                }
                else{
                    SInvokeNode sInvokeNode = new SInvokeNode(childCInvokeNode, myChildTarget, childCInvokeNode.isAsync());
                    cToSNode.put(key, sInvokeNode);
                    sQueue.add(sInvokeNode);
                }
                current.addChild(cToSNode.get(key));
            }
        }
        setAllNodes();
        cleanNodes(methodWithRaces);
        setAllNodes();
        updateMayCallIMBWithRaces(methodWithRaces);
    }

    protected void cleanNodes(Set<SootMethod> methodWithRaces) {
        List<SNode> sQueue = new ArrayList<>();
        Set<SNode> visited = new HashSet<>();
        for(SNode child : start.getChildren())
            sQueue.add(child);
        for(int i=0; i< sQueue.size(); i++){
            SNode current = sQueue.get(i);
            if(visited.contains(current))
                continue;
            visited.add(current);
            for(SNode child : current.getChildren()) {
                if(!visited.contains(child))
                    sQueue.add(child);
            }
            if(current instanceof SInvokeNode) {
                C3GTask target = ((SInvokeNode) current).getTarget();
                boolean flag = methodWithRaces.contains(target.getSource().getSource().getSource());
                flag |= target.getMayCallIMBWithRaces().size() > 0;
                if(!target.getSource().isSSC() && target.getSource().getSource().getSource().equals(NopCaSFGenerator.nopAsyncMethod)) // TODO: bad design
                    flag = true;
                if(!flag){
                    current.removeMySelf();
                }
                else {
                    target.callerSNode = (SInvokeNode) current;
                    target.callerC3GTask = this;
                }
            }
            else{
                if(current != end)
                    current.removeMySelf();
            }
        }
    }

    @Override
    protected void setAllNodes() {
        super.setAllNodes();
        for(SNode sNode: allNodes)
            sNode.setMyC3GTask(this);
    }

    public ThreadLattice getThreadLattice() {
        return threadLattice;
    }

    // TODO: need to be changed to package level
    public void setThreadLattice(ThreadLattice threadLattice) {
        this.threadLattice = threadLattice;
    }

    void setChildrenThreadLattices(Set<LibraryCaSFGenerator> libraryCaSFGeneratorSet){
        for(SNode sNode : getAllNodes()){
            if(sNode instanceof SInvokeNode){
                SInvokeNode current = (SInvokeNode) sNode;
                C3GTask target = current.getTarget();
                ThreadLattice possibleChildThreadLattice = ThreadLatticeManager.getUNDETERMINED();
                for(LibraryCaSFGenerator libCaSFGen : libraryCaSFGeneratorSet)
                    possibleChildThreadLattice = ThreadLatticeManager.eval(possibleChildThreadLattice, libCaSFGen.determineThread(current));
                if(possibleChildThreadLattice.equals(ThreadLatticeManager.getUNDETERMINED())) {
                    possibleChildThreadLattice = ThreadLatticeManager.eval(possibleChildThreadLattice, AndroidFrameworkCaSFGenerator.v().determineThread(current));
                }
                target.setThreadLattice(possibleChildThreadLattice);
                target.setChildrenThreadLattices(libraryCaSFGeneratorSet);
            }
        }
    }

    Set<SNode> getAllChildrenSNodes(){
        Set<SNode> allSNodes = new HashSet<>(allNodes);
        List<SNode> queue = new ArrayList<>();
        queue.add(start);
        Set<SNode> visited = new HashSet<>();
        for(int i=0; i<queue.size(); i++){
            SNode current = queue.get(i);
            if(visited.contains(current))
                continue;
            visited.add(current);
            for(AbstractNode abstractNode : current.getChildren())
                queue.add((SNode) abstractNode);
            if(current instanceof SInvokeNode){
                C3GTask target = ((SInvokeNode)current).getTarget();
                allSNodes.addAll(target.getAllChildrenSNodes());
            }
        }
        return allSNodes;
    }

    Set<C3GTask> getAllCvents(){
        Set<C3GTask> allC3GTasks = new HashSet<>(Collections.singleton(this));
        List<SNode> queue = new ArrayList<>();
        queue.add(start);
        Set<SNode> visited = new HashSet<>();
        for(int i=0; i<queue.size(); i++){
            SNode current = queue.get(i);
            if(visited.contains(current))
                continue;
            visited.add(current);
            for(AbstractNode abstractNode : current.getChildren())
                queue.add((SNode) abstractNode);
            if(current instanceof SInvokeNode){
                C3GTask target = ((SInvokeNode)current).getTarget();
                allC3GTasks.addAll(target.getAllCvents());
            }
        }
        return allC3GTasks;
    }

    @Override
    public String toString() {
        String suffix = "";
        if(threadLattice != null)
            suffix += "@"+threadLattice;
        return "CE-"+getSource().toString()+suffix;
    }

    public String completeToString(){
        StringBuilder ret = new StringBuilder();
        for (int i = 0 ; i< callerSites.size();i++) {
            CInvokeNode cInvokeNode = callerSites.get(i);
            InterMethodBox targetIMB = cInvokeNode.getTarget();
            if(targetIMB.isSSC())
                ret.append(targetIMB.toString());
            else
                ret.append(targetIMB.getSource().getSource().toString());
            if(i != callerSites.size()-1)
                ret.append("->");
        }
        return ret.toString();
    }

    public boolean isEqualCallerSites(C3GTask other){
        if(this.callerSites.size() != other.callerSites.size())
            return false;
        for(int i=0; i< this.callerSites.size(); i++)
            if(!this.callerSites.get(i).equals(other.callerSites.get(i)))
                return false;
        return true;
    }

    static class SKey {
        CNode cNode;
        InterMethodBox target;
        SKey(CNode cNode, InterMethodBox target){
            this.cNode = cNode;
            this.target = target;
        }

        @Override
        public int hashCode() {
            int h = this.cNode.hashCode();
            h = h *31 + ((this.target == null)? 7 : this.target.hashCode());
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if(!(obj instanceof SKey))
                return false;
            SKey other = (SKey) obj;
            if(this.cNode != other.cNode)
                return false;
            if(this.target == null && other.target == null)
                return true;
            return this.target == other.target;
        }
    }


}
