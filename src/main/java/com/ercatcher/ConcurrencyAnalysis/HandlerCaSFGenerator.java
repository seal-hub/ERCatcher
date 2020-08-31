package com.ercatcher.ConcurrencyAnalysis;

import com.ercatcher.LOG;
import com.ercatcher.Util;
import com.ercatcher.ConcurrencyAnalysis.C3G.C3GTask;
import com.ercatcher.ConcurrencyAnalysis.C3G.SInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.CInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.InterMethodBox;
import com.ercatcher.ConcurrencyAnalysis.CSF.MethodBox;
import com.ercatcher.memory.FieldMemLoc;
import com.ercatcher.memory.InitAllocMemLoc;
import com.ercatcher.memory.InvokeAllocMemLoc;
import com.ercatcher.memory.MemoryLocation;
import com.ercatcher.ConcurrencyAnalysis.CSF.UInvokeNode;
import soot.*;
import soot.jimple.*;
import soot.jimple.spark.pag.AllocNode;
import soot.jimple.spark.pag.Node;
import soot.jimple.spark.sets.P2SetVisitor;
import soot.jimple.spark.sets.PointsToSetInternal;
import soot.toolkits.scalar.Pair;

import java.util.*;

public class HandlerCaSFGenerator implements LibraryCaSFGenerator {

    private Set<MethodBox> addedMethodBoxes = new HashSet<>();

    public HandlerCaSFGenerator(SootClass sootClass) {
    }

    public Set<MethodBox> getAddedMethodBoxes() {
        return addedMethodBoxes;
    }

    public boolean canDetectTarget(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox) {
        if (!targetBox.getSource().getDeclaringClass().getName().equals("android.os.Handler"))
            return false;
        return targetBox.getSource().getName().equals("sendMessage") || targetBox.getSource().getName().equals("sendMessageDelayed") || targetBox.getSource().getName().equals("sendMessageAtFrontOfQueue");
    }

    public List<Pair<InterMethodBox, Boolean>> detectTargets(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox, Map<SootMethod, InterMethodBox> methodToInterMethodBox) {
        if (!canDetectTarget(sourceBox, uInvokeNode, targetBox))
            throw new RuntimeException(String.format("I'm not interested in this Source: %s  UNode: %s Target: %s", sourceBox, uInvokeNode, targetBox));
        Stmt stmt = uInvokeNode.getSource();
        if (!stmt.containsInvokeExpr() || !(stmt.getInvokeExpr() instanceof InstanceInvokeExpr)) {
            throw new RuntimeException("How it is send Method?");
        } else {
            InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) stmt.getInvokeExpr();
            Local base = (Local) instanceInvokeExpr.getBase();
            if (!base.getType().toString().equals("android.os.Handler")) {
                SootClass targetClass = Scene.v().getSootClass(base.getType().toString());
                SootMethod sootMethod = targetClass.getMethod("void handleMessage(android.os.Message)");
                return Collections.singletonList(new Pair<>(methodToInterMethodBox.get(sootMethod), true));
            }
            // Intra Analysis
            if (sourceBox.getIntraAllocationAnalysis() != null) { // TODO: why it can be null?
                for (MemoryLocation memoryLocation : sourceBox.getIntraAllocationAnalysis().getMemLocsBefore(stmt, base)) {
                    if (memoryLocation instanceof InitAllocMemLoc) {
                        InitAllocMemLoc initAllocMemLoc = (InitAllocMemLoc) memoryLocation;
                        SpecialInvokeExpr initExpr = initAllocMemLoc.getInitExpr();
                        SootClass targetClass = initExpr.getMethod().getDeclaringClass();
                        List<Pair<InterMethodBox, Boolean>> possibleHandleMethods = findPossibleHandleMethod(methodToInterMethodBox, targetClass);
                        if (possibleHandleMethods != null)
                            return possibleHandleMethods;
                    }
                    if (memoryLocation instanceof FieldMemLoc) {
                        FieldMemLoc fieldMemLoc = (FieldMemLoc) memoryLocation;
                        SootClass targetClass = Scene.v().getSootClassUnsafe(fieldMemLoc.getField().getType().toString());
                        if(targetClass != null && !targetClass.toString().equals("android.os.Handler")) {
                            List<Pair<InterMethodBox, Boolean>> possibleHandleMethods = findPossibleHandleMethod(methodToInterMethodBox, targetClass);
                            if (possibleHandleMethods != null)
                                return possibleHandleMethods;
                        }
                    }
                    if (memoryLocation instanceof InvokeAllocMemLoc) {
                        InvokeAllocMemLoc invokeAllocMemLoc = (InvokeAllocMemLoc) memoryLocation;
                        if (invokeAllocMemLoc.getInvokeExpr() instanceof StaticInvokeExpr) {
                            StaticInvokeExpr staticInvokeExpr = (StaticInvokeExpr) invokeAllocMemLoc.getInvokeExpr();
                            InterMethodBox staticInvokeIMB = methodToInterMethodBox.get(staticInvokeExpr.getMethod());
                            if (staticInvokeIMB != null) {
                                MethodBox staticInvokeBox = staticInvokeIMB.getSource();
                                if (staticInvokeBox != null) {
                                    if (staticInvokeBox.getIntraAllocationAnalysis() != null) {
                                        for (MemoryLocation retValueLocation : staticInvokeBox.getIntraAllocationAnalysis().getReturnValueLocations()) {
                                            if (retValueLocation instanceof InitAllocMemLoc) {
                                                InitAllocMemLoc initAllocMemLoc = (InitAllocMemLoc) retValueLocation;
                                                SpecialInvokeExpr initExpr = initAllocMemLoc.getInitExpr();
                                                SootClass targetClass = initExpr.getMethod().getDeclaringClass();
                                                List<Pair<InterMethodBox, Boolean>> possibleHandleMethods = findPossibleHandleMethod(methodToInterMethodBox, targetClass);
                                                if (possibleHandleMethods != null)
                                                    return possibleHandleMethods;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                }
            }
            // Inter Analysis
            PointsToSetInternal pts = (PointsToSetInternal) Scene.v().getPointsToAnalysis().reachingObjects(base);
            final SootClass[] handlerClass = {null};
            if (!pts.isEmpty()) {
                pts.forall(new P2SetVisitor() {
                    @Override
                    public void visit(Node node) {
                        AllocNode allocNode = (AllocNode) node;
                        NewExpr newHandlerExpr = (NewExpr) allocNode.getNewExpr();
                        handlerClass[0] = newHandlerExpr.getBaseType().getSootClass();
                    }
                });
            }
            List<Pair<InterMethodBox, Boolean>> possibleHandleMethods = findPossibleHandleMethod(methodToInterMethodBox, handlerClass[0]);
            if (possibleHandleMethods != null) return possibleHandleMethods;
        }
        return null;
    }

    private List<Pair<InterMethodBox, Boolean>> findPossibleHandleMethod(Map<SootMethod, InterMethodBox> methodToInterMethodBox, SootClass handlerClass) {
        if (handlerClass != null && !handlerClass.toString().equals("android.os.Handler")) {
            SootMethod sootMethod = handlerClass.getMethodUnsafe("void handleMessage(android.os.Message)");
            if(sootMethod != null)
                return Collections.singletonList(new Pair<>(methodToInterMethodBox.get(sootMethod), true));
        }
        return null;
    }

    @Override
    public ThreadLattice determineThread(SInvokeNode sInvokeNode) {
        ThreadLattice result = ThreadLatticeManager.getUNDETERMINED();
        CInvokeNode cInvokeNode = (CInvokeNode) sInvokeNode.getSource();
        UInvokeNode uInvokeNode = (UInvokeNode) cInvokeNode.getSource();
        C3GTask parent = sInvokeNode.getMyC3GTask();
        C3GTask child = sInvokeNode.getTarget();
        if (uInvokeNode == null) {
            LOG.logln(String.format("Why UInvokeNode of %s is null? Handler CaSF", cInvokeNode), LOG.ERROR);
            return result;
        }
        Stmt stmt = uInvokeNode.getSource();
        if (stmt == null)
            return result;
        if (!stmt.containsInvokeExpr())
            return result;
        InvokeExpr invokeExpr = stmt.getInvokeExpr();
        if (invokeExpr instanceof InstanceInvokeExpr) {
            InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
            Local base = (Local) instanceInvokeExpr.getBase();
            if (Util.v().isSubclass(base.getType(), "android.os.Handler")) {
                if (instanceInvokeExpr.getMethod().getName().equals("sendMessageDelayed") || instanceInvokeExpr.getMethod().getName().equals("postDelayed")) {
                    sInvokeNode.setDelay(1); // TODO: this can be more precise by analyzing the arguments
                }
                if (instanceInvokeExpr.getMethod().getName().equals("sendMessageAtFrontOfQueue") || instanceInvokeExpr.getMethod().getName().equals("postAtFrontOfQueue")) {
                    sInvokeNode.setDelay(-1); // TODO: this can be more precise by analyzing the arguments
                }
                if(cInvokeNode.getMyInterMethodBox().getSource() != null && cInvokeNode.getMyInterMethodBox().getSource().getIntraAllocationAnalysis() != null) {
                    Set<MemoryLocation> handlerSources = cInvokeNode.getMyInterMethodBox().getSource().getIntraAllocationAnalysis().getMemLocsBefore(stmt, base, true);
                    if (handlerSources.size() != 1) {
                        LOG.logln(String.format("More than one sources or no source for the origin handler in CInvokeNode %s and unit %s", cInvokeNode, stmt), LOG.ERROR);
                    } else {
                        MemoryLocation source = (MemoryLocation) handlerSources.toArray()[0];
                        if (source instanceof InitAllocMemLoc) {
                            InitAllocMemLoc initHandlerMemLoc = (InitAllocMemLoc) source;
                            SpecialInvokeExpr initExpr = initHandlerMemLoc.getInitExpr();
                            // TODO: this is imprecise
                            boolean flag = false;
                            for (int i = 0; i < initExpr.getMethod().getParameterCount(); i++) {
                                Type type = initExpr.getMethod().getParameterType(i);
                                if (Util.v().isSubclass(type, "android.os.Looper")) {
                                    flag = true;
                                    Local runnableLocal = (Local) initExpr.getArg(i);
                                    Set<MemoryLocation> looperMemLocs = cInvokeNode.getMyInterMethodBox().getSource().getIntraAllocationAnalysis().getMemLocsBefore(initHandlerMemLoc.getCtxUnit(), runnableLocal, true);
                                    if (looperMemLocs.size() == 1) {
                                        MemoryLocation looperMemLoc = (MemoryLocation) looperMemLocs.toArray()[0];
                                        if (looperMemLoc instanceof InvokeAllocMemLoc) {
                                            InvokeAllocMemLoc invokeLooperMemLoc = (InvokeAllocMemLoc) looperMemLoc;
                                            InvokeExpr invokeLooperExpr = invokeLooperMemLoc.getInvokeExpr();
                                            if (invokeLooperExpr.getMethod().getName().equals("getMainLooper"))
                                                return ThreadLatticeManager.getUIThreadLattice();
                                            else if (invokeLooperExpr.getMethod().getName().equals("getLooper")) {
                                                if (invokeLooperExpr instanceof InstanceInvokeExpr) {
                                                    Local handlerThreadLocal = (Local) ((InstanceInvokeExpr) invokeLooperExpr).getBase();
                                                    PointsToSetInternal pts = (PointsToSetInternal) Scene.v().getPointsToAnalysis().reachingObjects(handlerThreadLocal);
                                                    if (pts.size() == 1) {
                                                        final ThreadLattice[] threadLattice = {null};
                                                        if (!pts.isEmpty()) {
                                                            pts.forall(new P2SetVisitor() {
                                                                @Override
                                                                public void visit(Node node) {
                                                                    AllocNode allocNode = (AllocNode) node;
                                                                    threadLattice[0] = ThreadLatticeManager.getNewAllocThreadLattice(allocNode);
                                                                }
                                                            });
                                                        }
                                                        if (threadLattice[0] != null)
                                                            return threadLattice[0];
                                                    }
                                                    Set<MemoryLocation> threadSources = cInvokeNode.getMyInterMethodBox().getSource().getIntraAllocationAnalysis().getMemLocsBefore(invokeLooperMemLoc.getCtxUnit(), handlerThreadLocal, true);
                                                    if (threadSources.size() != 1) {
                                                        LOG.logln(String.format("More than one sources or no source for the origin handler thread in CInvokeNode %s and unit %s", cInvokeNode, invokeLooperMemLoc.getCtxUnit()), LOG.ERROR);
                                                    } else {
                                                        return ThreadLatticeManager.getNewThreadLattice((MemoryLocation) threadSources.toArray()[0]);
                                                    }
                                                } else {
                                                    throw new RuntimeException("Why getLooper is static?");
                                                }
                                            } else if (invokeLooperExpr.getMethod().getName().equals("myLooper")) {
                                                return parent.getThreadLattice();
                                            } else
                                                throw new RuntimeException("What is this? a new type of ");
                                        }
                                    } else {
                                        throw new RuntimeException("What is this? Multiple source for handler?");
                                    }
                                    break;
                                }
                            }
                            if (!flag)
                                return ThreadLatticeManager.getUIThreadLattice();
                        } else {
                            LOG.logln(String.format("UNKOWN since I don't have any knowledge about this cvent %s to %s through %s on unit %s and handler %s", this, child, cInvokeNode, stmt, source), LOG.SUPER_VERBOSE);
                            return ThreadLatticeManager.getUNKNOWNThreadLattice();
                        }
                    }
                }
            } else {
                // It's not a handler, so we do nothing here.
            }
        } else {
            if (Util.v().isSubclass(invokeExpr.getMethod().getDeclaringClass().getType(), "android.os.AsyncTask"))
                LOG.logln(String.format("Static invocation Unrecognized source of Handler Task: %s in %s", invokeExpr, cInvokeNode), LOG.ERROR);
        }
        return result;
    }
}
