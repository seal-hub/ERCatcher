package com.ercatcher.ConcurrencyAnalysis.CSF;

import com.ercatcher.Util;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Borrowed from EventManager
public class CallGraphUtil {
    private static Set<SootMethod> cbOtherMethods = new HashSet<>();
    private static Set<SootMethod> otherMethods = new HashSet<>();
    public static Set<Edge> addedEdges = new HashSet<>();
    public static Map<SootMethod, Set<Stmt>> pendingEdges = new HashMap<>();
    public static void improveCallGraph(){
        findOtherCallbackMethods();
        for(Edge edge : addedEdges){
            Scene.v().getCallGraph().addEdge(edge);
        }
        makeCallGraphPrecise();
    }
    private static void findOtherCallbackMethods() {
        for (SootClass sc : Scene.v().getClasses()) {
            for (SootMethod sootMethod : sc.getMethods()) {
                if (!Util.v().isAPKMethod(sootMethod))
                    continue;
                if (!sootMethod.hasActiveBody())
                    continue;
                // runOnUiThread
                if (Util.v().isSubclass(sc.getName(), "android.os.AsyncTask")) {
                    // TODO: onPreExecute
                    if (sootMethod.getName().equals("doInBackground") || sootMethod.getName().equals("onPostExecute") || sootMethod.getName().equals("onCancelled")) {
                        cbOtherMethods.add(sootMethod);
//                        sootMethod.retrieveActiveBody();
                    }
                } else if (Util.v().isSubclass(sc.getName(), "android.os.Handler")) {
                    if (sootMethod.getName().equals("handleMessage")) {
                        cbOtherMethods.add(sootMethod);
//                        sootMethod.retrieveActiveBody();
                    }
                } else if (sootMethod.getName().equals("run") && sc.implementsInterface("java.lang.Runnable")) {
                    cbOtherMethods.add(sootMethod);
//                    sootMethod.retrieveActiveBody();
                }
//                else if (!cbGUIMethods.contains(sootMethod))
//                    otherMethods.add(sootMethod);
            }
        }
    }
    private static void makeCallGraphPrecise() {
        CallGraph callGraph = Scene.v().getCallGraph();
        for (SootMethod sootMethod : cbOtherMethods) {
            if (callGraph.edgesInto(sootMethod).next().kind().equals(Kind.INVALID)) {
                generateOutEdges(callGraph, sootMethod);
                SootClass sootClass = sootMethod.getDeclaringClass();
                if (Util.v().isSubclass(sootClass.toString(), "android.os.AsyncTask")) {
                    if (sootMethod.getSubSignature().equals("void onCancelled(java.lang.Object)") || sootMethod.getSubSignature().equals("void onCancelled()") || sootMethod.getSubSignature().equals("void onPostExecute(java.lang.Object)")) {
                        if (sootClass.declaresMethod("java.lang.Object doInBackground(java.lang.Object[])")) {
                            SootMethod doInBackgroundMethod = sootClass.getMethod("java.lang.Object doInBackground(java.lang.Object[])");
                            Edge newEdge = new Edge(doInBackgroundMethod, doInBackgroundMethod.getActiveBody().getUnits().getLast(), sootMethod, Kind.ASYNCTASK);
                            callGraph.addEdge(newEdge);
                        }
                    } else if (sootMethod.getSubSignature().equals("java.lang.Object doInBackground(java.lang.Object[])")) {
//                        for (Pair<Local, LocalInfo> pair : LocalInfoManager.v().getLocalInfosByType("android.os.AsyncTask")) {
//                            AsyncLocalInfo asyncLocalInfo = (AsyncLocalInfo) pair.getO2();
//                            for (int i = 0; i < asyncLocalInfo.executeUnit.size(); i++) {
//                                Edge newEdge = new Edge(asyncLocalInfo.executeMethods.get(i), asyncLocalInfo.executeUnit.get(i), sootMethod, Kind.ASYNCTASK);
//                                callGraph.addEdge(newEdge);
//                            }
//                        }
                    } else {
                        System.out.print("");
                    }
                }
                else {
                    System.out.print("");
                }
            }
        }
    }
    private static void generateOutEdges(CallGraph callGraph, SootMethod sootMethod) {
        for (Unit unit : sootMethod.getActiveBody().getUnits()) {
            InvokeExpr invokeExpr = null;
            if (unit instanceof InvokeStmt) {
                invokeExpr = ((InvokeStmt) unit).getInvokeExpr();
            } else if (unit instanceof AssignStmt) {
                AssignStmt assignStmt = (AssignStmt) unit;
                if (assignStmt.getRightOp() instanceof InvokeExpr)
                    invokeExpr = (InvokeExpr) assignStmt.getRightOp();
            }
            if (invokeExpr != null) {
                invokeExpr.apply(new AbstractExprSwitch() {
                    @Override
                    public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {
                        Edge outEdge = new Edge(sootMethod, unit, v.getMethod(), Kind.SPECIAL);
                        callGraph.addEdge(outEdge);
                    }

                    @Override
                    public void caseStaticInvokeExpr(StaticInvokeExpr v) {
                        Edge outEdge = new Edge(sootMethod, unit, v.getMethod(), Kind.STATIC);
                        callGraph.addEdge(outEdge);
                    }

                    @Override
                    public void caseVirtualInvokeExpr(VirtualInvokeExpr v) {
                        Edge outEdge = new Edge(sootMethod, unit, v.getMethod(), Kind.VIRTUAL);
                        callGraph.addEdge(outEdge);
                    }

                    @Override
                    public void defaultCase(Object obj) {
                        super.defaultCase(obj);
                    }
                });
            }
        }
    }
}
