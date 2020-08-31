package com.ercatcher.memory;

import com.ercatcher.LOG;
import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


public class IntraAllocationAnalysis extends ForwardFlowAnalysis<Unit, MemoryPairSet> {

    public final static IntraAllocationAnalysis EMPTY = null;

    public Set<MemoryLocation> getReturnValueLocations() {
        return returnValueLocations;
    }

    private Set<MemoryLocation> returnValueLocations = new HashSet<>();
    private boolean isUAF = true;
    IntraAllocationAnalysis(DirectedGraph<Unit> graph){
        this(graph, true);
    }
    IntraAllocationAnalysis(DirectedGraph<Unit> graph, boolean isUAF) {
        super(graph);
        this.isUAF = isUAF;
        doAnalysis();
        for (Iterator<Unit> it = graph.iterator(); it.hasNext(); ) {
            Unit unit = it.next();
            if (unit instanceof ReturnStmt) {
                ReturnStmt returnStmt = (ReturnStmt) unit;
                Set<MemoryLocation> memoryLocationSet = null;
                if (returnStmt.getOp() instanceof Local)
                    memoryLocationSet = getMemLocsBefore(returnStmt, (Local) returnStmt.getOp());
                else if (returnStmt.getOp() instanceof InstanceFieldRef) {
                    InstanceFieldRef instanceFieldRef = (InstanceFieldRef) returnStmt.getOp();
                    memoryLocationSet = getMemLocsBefore(returnStmt, (Local) instanceFieldRef.getBase(), instanceFieldRef.getField());
                }
                else if (returnStmt.getOp() instanceof StaticFieldRef) {
                    StaticFieldRef staticFieldRef = (StaticFieldRef) returnStmt.getOp();
                    memoryLocationSet = getMemLocsBefore(returnStmt, staticFieldRef.getField());
                }
                if(memoryLocationSet != null)
                    returnValueLocations.addAll(memoryLocationSet);
            }
        }

    }

//     Can be used for checking the static invocation calls
//                    if (memoryLocation instanceof InvokeAllocMemLoc) {
//        InvokeAllocMemLoc invokeAllocMemLoc = (InvokeAllocMemLoc) memoryLocation;
//        if (invokeAllocMemLoc.getInvokeExpr() instanceof StaticInvokeExpr) {
//            StaticInvokeExpr staticInvokeExpr = (StaticInvokeExpr) invokeAllocMemLoc.getInvokeExpr();
//            InterMethodBox staticInvokeIMB = methodToInterMethodBox.get(staticInvokeExpr.getMethod());
//            if (staticInvokeIMB != null) {
//                MethodBox staticInvokeBox = staticInvokeIMB.getSource();
//                if (staticInvokeBox != null) {
//                    if (staticInvokeBox.getIntraAllocationAnalysis() != null) {
//                        for (Unit unit : staticInvokeBox.getSource().getActiveBody().getUnits()) {
//                            if (unit instanceof ReturnStmt) {
//                                ReturnStmt returnStmt = (ReturnStmt) unit;
//                                Set<MemoryLocation> memoryLocationSet = null;
//                                if (returnStmt.getOp() instanceof Local)
//                                    memoryLocationSet = staticInvokeBox.getIntraAllocationAnalysis().getMemLocsBefore(returnStmt, (Local) returnStmt.getOp());
//                                else if (returnStmt.getOp() instanceof InstanceFieldRef) {
//                                    InstanceFieldRef instanceFieldRef = (InstanceFieldRef) returnStmt.getOp();
//                                    memoryLocationSet = staticInvokeBox.getIntraAllocationAnalysis().getMemLocsBefore(returnStmt, (Local) instanceFieldRef.getBase(), instanceFieldRef.getField());
//                                }
//                                else if (returnStmt.getOp() instanceof StaticFieldRef) {
//                                    StaticFieldRef staticFieldRef = (StaticFieldRef) returnStmt.getOp();
//                                    memoryLocationSet = staticInvokeBox.getIntraAllocationAnalysis().getMemLocsBefore(returnStmt, staticFieldRef.getField());
//                                }
//                                if(memoryLocationSet != null) {
//                                    for (MemoryLocation ml2 : memoryLocationSet) {
//                                        if (ml2 instanceof InitAllocMemLoc) {
//                                            InitAllocMemLoc initAllocMemLoc = (InitAllocMemLoc) ml2;
//                                            SpecialInvokeExpr initExpr = initAllocMemLoc.getInitExpr();
//                                            SootClass targetClass = initExpr.getMethod().getDeclaringClass();
//                                            List<Pair<InterMethodBox, Boolean>> possibleHandleMethods = findPossibleHandleMethod(methodToInterMethodBox, targetClass);
//                                            if (possibleHandleMethods != null)
//                                                return possibleHandleMethods;
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    }
////                                        for(MemoryPa : staticInvokeBox.getIntraAllocationAnalysis().getEndMemPairs() )
//                }
//            }
//        }
//    }

    public Set<MemoryLocation> getMemLocsBefore(Unit unit, FieldMemLoc fieldMemLoc){
        return this.getFlowBefore(unit).aliasLoc(new FieldMemAddr(fieldMemLoc));
    }

    public Set<MemoryLocation> getMemLocsBefore(Unit unit, Local local){
        return this.getMemLocsBefore(unit, local, false);
    }

    public Set<MemoryLocation> getMemLocsBefore(Unit unit, Local local, SootField field){
        return this.getMemLocsBefore(unit, local, field, false);
    }

    public Set<MemoryLocation> getMemLocsBefore(Unit unit, SootField field){
        return this.getMemLocsBefore(unit, field, false);
    }

    public Set<MemoryLocation> getMemLocsBefore(Unit unit, Local local, boolean refine){
        return this.getFlowBefore(unit).aliasLoc(new LocalMemAddr(local), refine);
    }

    public Set<MemoryLocation> getMemLocsBefore(Unit unit, Local local, SootField field, boolean refine){
        Set<MemoryLocation> result = new HashSet<>();
        for(MemoryLocation baseLoc : getMemLocsBefore(unit, local)) {
            result.addAll(this.getFlowBefore(unit).aliasLoc(new FieldMemAddr(new FieldMemLoc(baseLoc, field)), refine));
        }
        return result;
    }

    public Set<MemoryLocation> getMemLocsBefore(Unit unit, SootField field, boolean refine){
        Set<MemoryLocation> result = new HashSet<>();
        ClassAllocMemLoc classAllocMemLoc = new ClassAllocMemLoc(field.getDeclaringClass());
        result.addAll(this.getFlowBefore(unit).aliasLoc(new FieldMemAddr(new FieldMemLoc(classAllocMemLoc, field)), refine));
        return result;
    }

    public boolean canBeNullBefore(Unit unit, Local local){
        return hasNull(this.getMemLocsBefore(unit, local));
    }

    public boolean canBeNullBefore(Unit unit, Local local, SootField sootField){
        return hasNull(this.getMemLocsBefore(unit, local, sootField));
    }

    public boolean canBeNullBefore(Unit unit, SootField sootField){
        return hasNull(this.getMemLocsBefore(unit, sootField));
    }

    private boolean hasNull(Set<MemoryLocation> memoryLocations){
        for (MemoryLocation memoryLocation: memoryLocations) {
            if(memoryLocation.equals(NullAllocMemLoc.v()))
                return true;
        }
        return false;
    }

    public Set<MemoryLocation> getEndMemLocs(Local local){
        Set<MemoryLocation> result = new HashSet<>();
        for(Unit tail : graph.getTails()){
            result.addAll(this.getMemLocsBefore(tail, local));
        }
        return result;
    }

    public Set<MemoryLocation> getEndMemLocs(Local local, SootField field){
        Set<MemoryLocation> result = new HashSet<>();
        for(Unit tail : graph.getTails()){
            result.addAll(this.getMemLocsBefore(tail, local, field));
        }
        return result;
    }

    public Set<MemoryLocation> getEndMemLocs(SootField field){
        Set<MemoryLocation> result = new HashSet<>();
        for(Unit tail : graph.getTails()){
            result.addAll(this.getMemLocsBefore(tail, field));
        }
        return result;
    }

    public Set<MemoryPair> getEndMemPairs(){
        Set<MemoryPair> result = new HashSet<>();
        for(Unit tail : graph.getTails()){
            result.addAll(this.getFlowBefore(tail).toList());
        }
        return result;
    }


    @Override
    protected void flowThrough(MemoryPairSet inSet, Unit unit, MemoryPairSet outSet) {
        inSet.copy(outSet);
        Set<MemoryAddress> leftMemAddrs = getMemoryAddresses(unit, inSet);


        for(MemoryAddress addr : leftMemAddrs) {
            // Kill
            if((addr instanceof LocalMemAddr && ((LocalMemAddr) addr).isArray())){

            }
            else{
                outSet.remove(addr);
            }
            // Generate
            if(leftMemAddrs.isEmpty())
                continue;
            Set<MemoryLocation> rightMemLocs = getMemoryLocations(unit, inSet);
            for (MemoryLocation location : rightMemLocs) {
                MemoryPair memoryPair = new MemoryPair(addr, location, (Stmt) unit);
                outSet.add(memoryPair);
            }
        }
    }

    @Override
    protected MemoryPairSet newInitialFlow() {
        return new MemoryPairSet();
    }

    @Override
    protected void merge(MemoryPairSet inSet1, MemoryPairSet inSet2, MemoryPairSet outSet) {
        inSet1.union(inSet2, outSet);
    }

    @Override
    protected void copy(MemoryPairSet source, MemoryPairSet dest) {
        source.copy(dest);

    }

    private Set<MemoryAddress> getMemoryAddresses(Unit unit, MemoryPairSet memoryPairSet){
        Set<MemoryAddress> result = new HashSet<>();
        if(unit instanceof DefinitionStmt){
            DefinitionStmt stmt = (DefinitionStmt) unit;
            stmt.getLeftOp().apply(new AbstractJimpleValueSwitch() {
                @Override
                public void caseInstanceFieldRef(InstanceFieldRef v) {
                    Local base = (Local) v.getBase();
                    Set<MemoryLocation> baseMemLocs = memoryPairSet.aliasLoc(new LocalMemAddr(base));
                    if(baseMemLocs.size() == 0)
                        throw new RuntimeException("Why?");
                    for(MemoryLocation baseLoc : baseMemLocs){
                        FieldMemLoc fieldMemLoc = new FieldMemLoc(unit, baseLoc, v.getField(), v);
                        result.add(new FieldMemAddr(fieldMemLoc));
                    }
                }

                @Override
                public void caseLocal(Local v) {
                    if(isPrimitive(v.getType()))
                        return;
                    result.add(new LocalMemAddr(v));
                }

                @Override
                public void caseStaticFieldRef(StaticFieldRef v) {
                    ClassAllocMemLoc base = new ClassAllocMemLoc(v.getField().getDeclaringClass());
                    FieldMemLoc fieldMemLoc = new FieldMemLoc(unit, base, v.getField(), v);
                    result.add(new FieldMemAddr(fieldMemLoc));
                }

                @Override
                public void caseArrayRef(ArrayRef v) {
                    if(isPrimitive(v.getType()))
                        return;
                    result.add(new LocalMemAddr((Local)v.getBase(), true));
                }

                @Override
                public void defaultCase(Object v) {
                    super.defaultCase(v);
                }
            });
        }
        else if(((Stmt) unit).containsInvokeExpr() && ((Stmt) unit).getInvokeExpr() instanceof SpecialInvokeExpr){
            SpecialInvokeExpr v = (SpecialInvokeExpr) ((Stmt) unit).getInvokeExpr();
            if(v.getMethod().getName().equals("<init>")) {
                if (v.getBase() instanceof Local) {
                    Local base = (Local) v.getBase();
                    result.add(new LocalMemAddr(base, false));
                } else if (v.getBase() instanceof FieldRef || v.getBase() instanceof ArrayRef)
                    throw new RuntimeException("Field or Array Ref");
            }
        }
        return result;
    }

    private Set<MemoryLocation> getMemoryLocations(Unit unit, MemoryPairSet memoryPairSet){
        Set<MemoryLocation> result = new HashSet<>();
        if(unit instanceof DefinitionStmt) {
            DefinitionStmt stmt = (DefinitionStmt) unit;
            stmt.getRightOp().apply(new AbstractJimpleValueSwitch() {
                @Override
                public void caseArrayRef(ArrayRef v) {
                    Local base = (Local) v.getBase();
                    Set<MemoryLocation> baseMemLocs = memoryPairSet.aliasLoc(new LocalMemAddr(base, true));
                    if(baseMemLocs.size() == 0)
                        throw new RuntimeException("Why?");
                    result.addAll(baseMemLocs);
                }

                @Override
                public void caseStringConstant(StringConstant v) {
                    result.add(new ConstantMemLoc(unit, v));
                }

                @Override
                public void caseNullConstant(NullConstant v) {
                    result.add(NullAllocMemLoc.v());
                }

                @Override
                public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v) {
                    result.add(new InvokeAllocMemLoc(unit, v));
                }

                @Override
                public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {
                    if(v.getMethod().getName().equals("<init>")) {
                        throw  new RuntimeException("How it is a definition Stmt");
                    }
                    else {
                        result.add(new InvokeAllocMemLoc(unit, v));
                    }
//                    throw new RuntimeException("Why special has a return value?");
                }

                @Override
                public void caseStaticInvokeExpr(StaticInvokeExpr v) {
                    result.add(new InvokeAllocMemLoc(unit, v));
                }

                @Override
                public void caseVirtualInvokeExpr(VirtualInvokeExpr v) {
                    result.add(new InvokeAllocMemLoc(unit, v));
                }

                @Override
                public void caseDynamicInvokeExpr(DynamicInvokeExpr v) {
                    result.add(new InvokeAllocMemLoc(unit, v));
                    throw new RuntimeException("Why dynamic has a return value?");
                }

                @Override
                public void caseCastExpr(CastExpr v) {
                    if(v.getOp() instanceof Local) {
                        Local base = (Local) v.getOp();
                        if (isPrimitive(base.getType()))
                            return;
                        Set<MemoryLocation> baseMemLocs = memoryPairSet.aliasLoc(new LocalMemAddr(base));
                        if (baseMemLocs.size() == 0)
                            throw new RuntimeException("Why?");
                        result.addAll(baseMemLocs);
                    }
                    else if (v.getOp() instanceof FieldRef || v.getOp() instanceof ArrayRef)
                        throw new RuntimeException("Field or Array Ref");

                }

                @Override
                public void caseNewArrayExpr(NewArrayExpr v) {
                    result.add(new NewAllocMemLoc(unit, v));
                }

                @Override
                public void caseNewMultiArrayExpr(NewMultiArrayExpr v) {
                    result.add(new NewAllocMemLoc(unit, v));
                }

                @Override
                public void caseNewExpr(NewExpr v) {
                    result.add(new NewAllocMemLoc(unit, v));
                }

                @Override
                public void caseInstanceFieldRef(InstanceFieldRef v) {
                    Local base = (Local) v.getBase();
                    Set<MemoryLocation> baseMemLocs = memoryPairSet.aliasLoc(new LocalMemAddr(base));
                    if(baseMemLocs.size() == 0) {
//                        throw new RuntimeException("Why?");
                        LOG.logln(String.format("Couldn't find the alias of memory location %s", v), LOG.ERROR);
                    }
                    for(MemoryLocation baseLoc : baseMemLocs){
                        FieldMemLoc newFML = new FieldMemLoc(unit, baseLoc, v.getField(), v);
                        result.add(newFML); // TODO: I'm not sure about it
                        Set<MemoryLocation> filedMemLocs = memoryPairSet.aliasLoc(new FieldMemAddr(newFML));
                        if(filedMemLocs.size() == 0)
                            result.add(newFML);
                        else
                            result.addAll(filedMemLocs);

                    }
                }

                @Override
                public void caseLocal(Local v) {

                    if(isPrimitive(v.getType()))
                        return;
                    Set<MemoryLocation> baseMemLocs = memoryPairSet.aliasLoc(new LocalMemAddr(v));
                    if(baseMemLocs.size() == 0) {
//                        throw new RuntimeException("Why?");
                        LOG.logln(String.format("Couldn't find the alias of memory location %s", v), LOG.ERROR);
                    }
                    result.addAll(baseMemLocs);
                }

                @Override
                public void caseParameterRef(ParameterRef v) {
                    result.add(new ParamAllocMemLoc(unit, v));
                }

                @Override
                public void caseCaughtExceptionRef(CaughtExceptionRef v) {
                    result.add(new ExceptionAllocMemLoc(unit, v));
                }

                @Override
                public void caseThisRef(ThisRef v) {
                    result.add(new ThisAllocMemLoc(unit, v));
                }

                @Override
                public void caseStaticFieldRef(StaticFieldRef v) {
                    ClassAllocMemLoc base = new ClassAllocMemLoc(v.getField().getDeclaringClass());
                    FieldMemLoc newFML = new FieldMemLoc(unit, base, v.getField(), v);
                    result.add(newFML); // TODO: I'm not sure about it
                    Set<MemoryLocation> filedMemLocs = memoryPairSet.aliasLoc(new FieldMemAddr(newFML));
                    if(filedMemLocs.size() == 0)
                        result.add(newFML);
                    else
                        result.addAll(filedMemLocs);
                }
            });
        }
        else if(((Stmt) unit).containsInvokeExpr() && ((Stmt) unit).getInvokeExpr() instanceof SpecialInvokeExpr){
            SpecialInvokeExpr v = (SpecialInvokeExpr) ((Stmt) unit).getInvokeExpr();
            if(v.getMethod().getName().equals("<init>")) {
                if (v.getBase() instanceof Local) {
                    result.add(new InitAllocMemLoc(unit, v));
                } else if (v.getBase() instanceof FieldRef || v.getBase() instanceof ArrayRef)
                    throw new RuntimeException("Field or Array Ref");
            }
            else {
                result.add(new InvokeAllocMemLoc(unit, v));
            }
        }
        return result;
    }

    private boolean isPrimitive(Type type){
        if(isUAF)
            return type instanceof PrimType;
        return false;
//        || type.equals(StringConstant.v("S").getType());
    }

}