package com.ercatcher.RaceDetector;

import com.ercatcher.LOG;
import com.ercatcher.ConcurrencyAnalysis.CSF.MethodBox;
import com.ercatcher.memory.*;
import com.ercatcher.ConcurrencyAnalysis.CSF.CSFManager;
import soot.*;
import soot.jimple.*;
import soot.jimple.spark.pag.AllocNode;
import soot.jimple.spark.pag.Node;
import soot.jimple.spark.pag.PAG;
import soot.jimple.spark.sets.P2SetVisitor;
import soot.jimple.spark.sets.PointsToSetInternal;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.Pair;

import java.util.*;

public class GeneralInitalMethodRaceDetector implements RaceDetector<MethodRace> {
    private Map<FieldId, Set<MethodStmtPair>> fieldIdInitiated = new HashMap<>();
    private final int INITIATED_BY_INVOKE = 1;
    private final int INITIATED_BY_OTHER = 2;
    private final int NOT_INITIATED = 3;
    private Map<FieldId, List<MethodStmtPair>> writeFieldIdToMethods = new HashMap<>();
    private Map<FieldId, List<MethodStmtPair>> readFieldIdToMethods = new HashMap<>();
    private Map<FieldId, Set<FieldId>> nonAllocToAllAllocFieldIds = new HashMap<>();
    private Set<MethodRace> methodRaceSet = new HashSet<>();
    public GeneralInitalMethodRaceDetector(CSFManager myCaSFManager) {
        LOG.startTimer("GeneralInitalMethodRaceDetector", LOG.VERBOSE);
        for (MethodBox methodBox : myCaSFManager.getAllMethodBoxes()) {
            if (methodBox.getIntraAllocationAnalysis() == null || methodBox.getSource().getDeclaringClass().getName().equals("dummyMainClass"))
                continue;
            findReadAccesses(methodBox);
            if(methodBox.getSource().getName().equals("<clinit>") || methodBox.getSource().getName().equals("<init>")) // TODO: configurable
                continue;
            findWriteAccesses(methodBox);
        }
        // Write-Write
        for (FieldId fieldId : writeFieldIdToMethods.keySet()) {
            for (MethodStmtPair writePair : writeFieldIdToMethods.get(fieldId)) {
                for (MethodStmtPair readPair : writeFieldIdToMethods.get(fieldId)) {
                    boolean initiatedByInvoke = fieldIdInitiated.containsKey(fieldId) && (fieldIdInitiated.get(fieldId).contains(readPair) || fieldIdInitiated.get(fieldId).contains(writePair));
                    MethodRace methodRace = new MethodRace(writePair.getMethod(), writePair.getStmt(), readPair.getMethod(), readPair.getStmt(), fieldId)
                            .setUAF(false)
                            .setIfGuard(false)
                            .setNullAtEnd(false)
                            .setInitiatedByInvoke(initiatedByInvoke)
                            .setWW(true);
                    methodRaceSet.add(methodRace);
                }
            }
            FieldId overApproxFieldId = fieldId.getOverApproximate();
            if(!fieldId.equals(overApproxFieldId))
                continue;
            for(FieldId possibleWriteFieldId : nonAllocToAllAllocFieldIds.getOrDefault(overApproxFieldId, new HashSet<>())) {
                if (fieldId.equals(possibleWriteFieldId))
                    continue;
                for (MethodStmtPair writePair : writeFieldIdToMethods.getOrDefault(possibleWriteFieldId, new ArrayList<>())) {
                    for (MethodStmtPair readPair : writeFieldIdToMethods.get(fieldId)) {
                        boolean initiatedByInvoke = fieldIdInitiated.containsKey(fieldId) && (fieldIdInitiated.get(fieldId).contains(readPair) || fieldIdInitiated.get(fieldId).contains(writePair));
                        MethodRace methodRace = new MethodRace(writePair.getMethod(), writePair.getStmt(), readPair.getMethod(), readPair.getStmt(), fieldId)
                                .setUAF(false)
                                .setIfGuard(false)
                                .setNullAtEnd(false)
                                .setInitiatedByInvoke(initiatedByInvoke)
                                .setWW(true);
                        methodRaceSet.add(methodRace);
                    }
                }
            }
        }
        // Write-Read
        for (FieldId fieldId : readFieldIdToMethods.keySet()) {
            for (MethodStmtPair writePair : writeFieldIdToMethods.getOrDefault(fieldId, new ArrayList<>())) {
                for (MethodStmtPair readPair : readFieldIdToMethods.get(fieldId)) {
                    boolean initiatedByInvoke = fieldIdInitiated.containsKey(fieldId) && (fieldIdInitiated.get(fieldId).contains(readPair) || fieldIdInitiated.get(fieldId).contains(writePair));
                    MethodRace methodRace = new MethodRace(writePair.getMethod(), writePair.getStmt(), readPair.getMethod(), readPair.getStmt(), fieldId)
                            .setUAF(false)
                            .setIfGuard(false)
                            .setNullAtEnd(false)
                            .setInitiatedByInvoke(initiatedByInvoke);
                    methodRaceSet.add(methodRace);
                }
            }
            FieldId overApproxFieldId = fieldId.getOverApproximate();
            if(!fieldId.equals(overApproxFieldId))
                continue;
            for(FieldId possibleWriteFieldId : nonAllocToAllAllocFieldIds.getOrDefault(overApproxFieldId, new HashSet<>())) {
                if (fieldId.equals(possibleWriteFieldId))
                    continue;
                for (MethodStmtPair writePair : writeFieldIdToMethods.getOrDefault(possibleWriteFieldId, new ArrayList<>())) {
                    for (MethodStmtPair readPair : readFieldIdToMethods.get(fieldId)) {
                        boolean initiatedByInvoke = fieldIdInitiated.containsKey(fieldId) && (fieldIdInitiated.get(fieldId).contains(readPair) || fieldIdInitiated.get(fieldId).contains(writePair));
                        MethodRace methodRace = new MethodRace(writePair.getMethod(), writePair.getStmt(), readPair.getMethod(), readPair.getStmt(), fieldId)
                                .setUAF(false)
                                .setIfGuard(false)
                                .setNullAtEnd(false)
                                .setInitiatedByInvoke(initiatedByInvoke);
                        methodRaceSet.add(methodRace);
                    }
                }
            }
        }
        for(FieldId fieldId : writeFieldIdToMethods.keySet()){
            FieldId overApproxFieldId = fieldId.getOverApproximate();
            if(!fieldId.equals(overApproxFieldId))
                continue;
            for(FieldId possibleReadFieldId : nonAllocToAllAllocFieldIds.getOrDefault(overApproxFieldId, new HashSet<>())) {
                if (fieldId.equals(possibleReadFieldId))
                    continue;
                for (MethodStmtPair readPair : readFieldIdToMethods.getOrDefault(possibleReadFieldId, new ArrayList<>())) {
                    for (MethodStmtPair writePair : writeFieldIdToMethods.get(fieldId)) {
                        boolean initiatedByInvoke = fieldIdInitiated.containsKey(fieldId) && (fieldIdInitiated.get(fieldId).contains(readPair) || fieldIdInitiated.get(fieldId).contains(writePair));
                        MethodRace methodRace = new MethodRace(writePair.getMethod(), writePair.getStmt(), readPair.getMethod(), readPair.getStmt(), fieldId)
                                .setUAF(false)
                                .setIfGuard(false)
                                .setNullAtEnd(false)
                                .setInitiatedByInvoke(initiatedByInvoke);
                        methodRaceSet.add(methodRace);
                    }
                }
            }
        }
        for(MethodRace race : methodRaceSet){
            LOG.logln(race.toString(), LOG.SUPER_VERBOSE);
        }
        int totalUAF = 0;
        int totalIfGuard = 0;
        int totalNullAtEnd = 0;
        int nAdroid = 0;
        for(MethodRace methodRace : methodRaceSet){
            boolean flag = methodRace.isNullAtEnd();
            if(methodRace.isIfGuard()) {
                totalIfGuard++;
                flag = false;
            }
            if(methodRace.isNullAtEnd()) {
                totalNullAtEnd++;
            }
            if(methodRace.isUAF()) {
                totalUAF++;
                if(flag)
                    nAdroid++;
            }
        }
        LOG.logln(String.format("ER: %d, UAF: %d, IfGuard: %d, NullAtEnd: %d, nAdroid: %d, Write Fields %d", methodRaceSet.size(), totalUAF, totalIfGuard, totalNullAtEnd, nAdroid, writeFieldIdToMethods.size()), LOG.ESSENTIAL);
        LOG.endTimer("GeneralInitalMethodRaceDetector", LOG.VERBOSE);

    }

    private void findReadAccesses(MethodBox methodBox) {
        DominatorTree<Unit> dTree = null;
        SootMethod sootMethod = methodBox.getSource();
        IntraAllocationAnalysis intraAllocationAnalysis = methodBox.getIntraAllocationAnalysis();
        if(sootMethod.getActiveBody().getUnits().size() < 500) { // TODO: configurable
            UnitGraph unitGraph = new CompleteUnitGraph(sootMethod.getActiveBody());
            dTree = new DominatorTree<>(new MHGDominatorsFinder(unitGraph));
        }
        DominatorTree<Unit> finalDTree = dTree;
        for (Unit unit : sootMethod.getActiveBody().getUnits()) {
            for (ValueBox useBox : unit.getUseBoxes()) {
                useBox.getValue().apply(new AbstractJimpleValueSwitch() {
//                    @Override
//                    public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v) {
//                        Local base = (Local) v.getBase();
//                        addReadAccess(base, intraAllocationAnalysis, unit, sootMethod, finalDTree);
//                    }
//
//                    //
//                    @Override
//                    public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {
//                        Local base = (Local) v.getBase();
//                        addReadAccess(base, intraAllocationAnalysis, unit, sootMethod, finalDTree);
//                    }
//
//                    //
//                    @Override
//                    public void caseDynamicInvokeExpr(DynamicInvokeExpr v) {
//                        throw new RuntimeException("Why dynamic?");
//                    }
//
//                    @Override
//                    public void defaultCase(Object v) {
//                        super.defaultCase(v);
//                    }

                    @Override
                    public void caseLocal(Local v) {
                        addReadAccess(v, intraAllocationAnalysis, unit, sootMethod, finalDTree);
//                        super.caseLocal(v);
                    }

//                    @Override
//                    public void caseVirtualInvokeExpr(VirtualInvokeExpr v) {
//                        Local base = (Local) v.getBase();
//                        addReadAccess(base, intraAllocationAnalysis, unit, sootMethod, finalDTree);
//                    }
//
//                    @Override
//                    public void caseArrayRef(ArrayRef v) {
//                        Local base = (Local) v.getBase();
//                        addReadAccess(base, intraAllocationAnalysis, unit, sootMethod, finalDTree);
//                    }
//
//                    @Override
//                    public void caseInstanceFieldRef(InstanceFieldRef v) {
//                        Local base = (Local) v.getBase();
//                        addReadAccess(base, intraAllocationAnalysis, unit, sootMethod, finalDTree);
//                    }
//
//                    @Override
//                    public void caseCastExpr(CastExpr v) {
//                        if (v.getOp() instanceof Local) {
//                            Local base = (Local) v.getOp();
//                            addReadAccess(base, intraAllocationAnalysis, unit, sootMethod, finalDTree);
//                        } else if (v.getOp() instanceof FieldRef || v.getOp() instanceof ArrayRef)
//                            throw new RuntimeException("Field or Array Ref");
//                    }
                });
            }
        }
    }

    private void addReadAccess(Local base, IntraAllocationAnalysis intraAllocationAnalysis, Unit unit, SootMethod sootMethod, DominatorTree<Unit> dTree) {
        for (MemoryLocation memoryLocation : intraAllocationAnalysis.getMemLocsBefore(unit, base)) {
            if (memoryLocation instanceof FieldMemLoc) {
                MemoryLocation fieldMemLoc = memoryLocation;
                int isInitiated = hasBeenInitiated((FieldMemLoc) memoryLocation, intraAllocationAnalysis, unit);
                if (isInitiated == INITIATED_BY_OTHER)
                    continue;
//                if(isInitiated == INITIATED_BY_INVOKE) // TODO: configurable
//                    continue;
                List<FieldRef> fieldSequence = new ArrayList<>();
                while (fieldMemLoc instanceof FieldMemLoc) {
                    FieldRef fieldRef = ((FieldMemLoc) fieldMemLoc).getFieldRef();
                    fieldSequence.add(fieldRef);
                    fieldMemLoc = ((FieldMemLoc) fieldMemLoc).getBase();
                }
                if (fieldMemLoc instanceof ThisAllocMemLoc
                        || fieldMemLoc instanceof ParamAllocMemLoc) {
                    if (fieldSequence.get(0).getField().getName().startsWith("this$"))
                        continue;
                }
                else if (fieldMemLoc instanceof InvokeAllocMemLoc){
                    Scene.v();
//                    continue; // Optimistic
                }
                else if (fieldMemLoc instanceof InitAllocMemLoc){
                    Scene.v();
//                    continue; // Optimistic
                }
                else if(fieldMemLoc instanceof NewAllocMemLoc){
                    Scene.v();
//                    continue; // Optimistic
                }
                else if(fieldMemLoc instanceof ClassAllocMemLoc){
                    Scene.v();
                }
                else if(fieldMemLoc instanceof ConstantMemLoc){
                    Scene.v();
                }
                else {
                    // TODO: add allocation checking
                    Scene.v();
//                    continue;
                }
                Set<FieldId> fieldIds = getFieldIds(fieldSequence);
                for(FieldId fieldId : fieldIds){
                    FieldId overApproximatedFieldId = fieldId.getOverApproximate();
                    if(!nonAllocToAllAllocFieldIds.containsKey(overApproximatedFieldId))
                        nonAllocToAllAllocFieldIds.put(overApproximatedFieldId, new HashSet<>());
                    nonAllocToAllAllocFieldIds.get(overApproximatedFieldId).add(fieldId);
                    if (!readFieldIdToMethods.containsKey(fieldId))
                        readFieldIdToMethods.put(fieldId, new ArrayList<>());
                    MethodStmtPair readMSPair = new MethodStmtPair(sootMethod, (Stmt) unit);
                    readFieldIdToMethods.get(fieldId).add(readMSPair);
                    if(isInitiated == INITIATED_BY_INVOKE){
                        if(!fieldIdInitiated.containsKey(fieldId))
                            fieldIdInitiated.put(fieldId, new HashSet<>());
                        fieldIdInitiated.get(fieldId).add(readMSPair);
                    }
                }
            }
        }
    }



    private void findWriteAccesses(MethodBox methodBox) {
        SootMethod sootMethod = methodBox.getSource();
        IntraAllocationAnalysis intraAllocationAnalysis = methodBox.getIntraAllocationAnalysis();
        for (Unit unit : sootMethod.getActiveBody().getUnits()) {
            if(unit instanceof  DefinitionStmt){
                DefinitionStmt defStmt = (DefinitionStmt) unit;
                final boolean[] canBuNull = {false};
                if(defStmt.getRightOp() instanceof NullConstant)
                    canBuNull[0] = true;
                else{
                    defStmt.getRightOp().apply(new AbstractJimpleValueSwitch() {
                        @Override
                        public void caseArrayRef(ArrayRef v) {
                            if(intraAllocationAnalysis.canBeNullBefore(unit, (Local) v.getBase()))
                                canBuNull[0] = true;
                        }

                        @Override
                        public void caseCastExpr(CastExpr v) {
                            if(v instanceof Local) {
                                if (intraAllocationAnalysis.canBeNullBefore(unit, (Local) v))
                                    canBuNull[0] = true;
                            }
                            else if (v.getOp() instanceof FieldRef || v.getOp() instanceof ArrayRef)
                                throw new RuntimeException("Field or Array Ref");
                        }

                        @Override
                        public void caseInstanceFieldRef(InstanceFieldRef v) {
                            if(intraAllocationAnalysis.canBeNullBefore(unit, (Local) v.getBase(), v.getField()))
                                canBuNull[0] = true;
                        }

                        @Override
                        public void caseLocal(Local v) {
                            if(intraAllocationAnalysis.canBeNullBefore(unit, v))
                                canBuNull[0] = true;
                        }

                        @Override
                        public void caseStaticFieldRef(StaticFieldRef v) {
                            if(intraAllocationAnalysis.canBeNullBefore(unit, v.getField()))
                                canBuNull[0] = true;
                        }

                    });
                }
//                if(!canBuNull[0])
//                    continue;
//                intraAllocationAnalysis.
                defStmt.getLeftOp().apply(new AbstractJimpleValueSwitch() {
                    @Override
                    public void defaultCase(Object v) {
                        Unit u = unit;
                        super.defaultCase(v);
                    }

                    @Override
                    public void caseLocal(Local v) {

                    }

                    @Override
                    public void caseInstanceFieldRef(InstanceFieldRef v) {
                        for (MemoryLocation memoryLocation : intraAllocationAnalysis.getMemLocsBefore(unit, (Local) v.getBase())) {
                            List<FieldRef> fieldSeq = new ArrayList<>();
                            MemoryLocation fieldMemLoc = memoryLocation;
                            int isInitiated = NOT_INITIATED;
                            if (memoryLocation instanceof FieldMemLoc )
                                isInitiated = hasBeenInitiated((FieldMemLoc) memoryLocation, intraAllocationAnalysis, unit);
                            if (isInitiated == INITIATED_BY_OTHER)
                                continue;
                            fieldSeq.add(v);
                            while (fieldMemLoc instanceof FieldMemLoc) {
                                FieldRef fieldRef = ((FieldMemLoc) fieldMemLoc).getFieldRef();
                                fieldSeq.add(fieldRef);
                                fieldMemLoc = ((FieldMemLoc) fieldMemLoc).getBase();
                            }
                            Set<FieldId> fieldIds = getFieldIds(fieldSeq); //Collections.singletonList(v));
                            for(FieldId fieldId : fieldIds) {
                                FieldId overApproximatedFieldId = fieldId.getOverApproximate();
                                if(!nonAllocToAllAllocFieldIds.containsKey(overApproximatedFieldId))
                                    nonAllocToAllAllocFieldIds.put(overApproximatedFieldId, new HashSet<>());
                                nonAllocToAllAllocFieldIds.get(overApproximatedFieldId).add(fieldId);
                                if (!writeFieldIdToMethods.containsKey(fieldId))
                                    writeFieldIdToMethods.put(fieldId, new ArrayList<>());
                                MethodStmtPair writeMSPair = new MethodStmtPair(sootMethod, (Stmt) unit);
                                writeFieldIdToMethods.get(fieldId).add(writeMSPair);
                                if(isInitiated == INITIATED_BY_INVOKE){
                                    if(!fieldIdInitiated.containsKey(fieldId))
                                        fieldIdInitiated.put(fieldId, new HashSet<>());
                                    fieldIdInitiated.get(fieldId).add(writeMSPair);
                                }
                            }
                        }
                    }

                    @Override
                    public void caseStaticFieldRef(StaticFieldRef v) {
                        Set<FieldId> fieldIds = getFieldIds(Collections.singletonList(v));
                        for(FieldId fieldId : fieldIds) {
                            FieldId overApproximatedFieldId = fieldId.getOverApproximate();
                            if(!nonAllocToAllAllocFieldIds.containsKey(overApproximatedFieldId))
                                nonAllocToAllAllocFieldIds.put(overApproximatedFieldId, new HashSet<>());
                            nonAllocToAllAllocFieldIds.get(overApproximatedFieldId).add(fieldId);
                            if (!writeFieldIdToMethods.containsKey(fieldId))
                                writeFieldIdToMethods.put(fieldId, new ArrayList<>());
                            writeFieldIdToMethods.get(fieldId).add(new MethodStmtPair(sootMethod, (Stmt) unit));
                        }

                    }
                });
            }
        }
    }

    private int hasBeenInitiated(FieldMemLoc memoryLocation, IntraAllocationAnalysis intraAllocationAnalysis, Unit unit) {
        boolean notArgument = true;
        boolean hasInitiated = false;
        boolean hasInitiatedByInvoke = false;
        for (MemoryLocation fieldValue : intraAllocationAnalysis.getMemLocsBefore(unit, memoryLocation)) {
            if (fieldValue instanceof ParamAllocMemLoc)
                notArgument = false;
            else if(fieldValue instanceof InitAllocMemLoc || fieldValue instanceof NewAllocMemLoc || fieldValue instanceof ThisAllocMemLoc || fieldValue instanceof ConstantMemLoc)
                hasInitiated = true;
            else if(fieldValue instanceof InvokeAllocMemLoc) // Optimistic TODO: configurable
                hasInitiatedByInvoke = true;
        }
        if (notArgument && hasInitiated)
            return INITIATED_BY_OTHER;
        if(notArgument && hasInitiatedByInvoke)
            return INITIATED_BY_INVOKE;
        return NOT_INITIATED;
    }

    private Set<FieldId> getFieldIds(List<FieldRef> fieldSequence) {
        PointsToAnalysis pointsToAnalysis = Scene.v().getPointsToAnalysis();
        PAG pag = (PAG) pointsToAnalysis;
        Set<AllocNode> allocNodeSet = new HashSet<>();
        SootField baseSootField = null;
        Set<FieldId> fieldIds = new HashSet<>();
        List<FieldRef> fieldRefs = new ArrayList<>();

        for(int i=0; i< fieldSequence.size(); i++){
            FieldRef fieldRef = fieldSequence.get(i);
            if(fieldRef instanceof StaticFieldRef){
                baseSootField = fieldRef.getField();
                fieldRefs = new ArrayList<>(fieldSequence.subList(0, i));
                break;
            }
            else{
                InstanceFieldRef instanceFieldRef = (InstanceFieldRef) fieldRef;
                PointsToSetInternal pointsToSetInternal = (PointsToSetInternal) pag.reachingObjects((Local) instanceFieldRef.getBase());
                if(!pointsToSetInternal.isEmpty()){
                    pointsToSetInternal.forall(new P2SetVisitor() {
                        @Override
                        public void visit(Node node) {
                            AllocNode allocNode = (AllocNode) node;
                            allocNodeSet.add(allocNode);
                        }
                    });
                    fieldRefs = new ArrayList<>(fieldSequence.subList(0, i+1));
                    break;
                }
            }
        }

        if(allocNodeSet.size() > 0){
            for(AllocNode allocNode : allocNodeSet){
                fieldIds.add(new FieldId(allocNode, fieldRefs));
            }
        }
        else {
            if(baseSootField == null) {
                baseSootField = fieldSequence.get(0).getField();
                fieldRefs = new ArrayList<>();
            }
            fieldIds.add(new FieldId(baseSootField, fieldRefs));
        }
        return fieldIds;
    }

    @Override
    public Set<MethodRace> getRaces() {
        return new HashSet<>(methodRaceSet);
    }

    static class MethodStmtPair extends Pair<SootMethod, Stmt>{
        public MethodStmtPair(SootMethod o1, Stmt o2) {
            super(o1, o2);
        }
        public SootMethod getMethod(){return getO1();}
        public Stmt getStmt(){return getO2();}
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getO1().hashCode();
            result = prime * result + getO2().hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            MethodStmtPair other = (MethodStmtPair) obj;
            return this.getO1().equals(other.getO1()) && this.getO2().equals(other.getO2());
        }
    }

    static class FieldIdMethodPair extends Pair<FieldId, SootMethod>{
        public FieldIdMethodPair(FieldId o1, SootMethod o2) {
            super(o1, o2);
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getO1().getLatestField().hashCode();
            result = prime * result + getO2().hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            FieldIdMethodPair other = (FieldIdMethodPair) obj;
            return this.getO1().getLatestField().equals(other.getO1().getLatestField()) && this.getO2().equals(other.getO2());
        }
    }
}
