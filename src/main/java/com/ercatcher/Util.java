package com.ercatcher;

import org.xmlpull.v1.XmlPullParserException;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.manifest.ProcessManifest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class Util {
    // Singleton
    private static Util util;
    public static Util v() {
        if (util == null)
            util = new Util();
        return util;
    }

    // App data
    private String packageName;
    private Set<String> componentPackageNames = new HashSet<>();
    private Set<SootMethod> newCallBackMethods = null;

    void processManifest(String apkPath){
        try {
            ProcessManifest manifest = new ProcessManifest(apkPath);
            packageName = manifest.getPackageName();

            if (manifest.getApplicationName() != null) {
                String[] parts = manifest.getApplicationName().split("\\.");
                StringBuilder newPackageName = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (i > 0)
                        newPackageName.append(".");
                    newPackageName.append(parts[i]);
                }
                addComponentPackageName(newPackageName.toString());
            }
        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
        }
    }

    void setCalledMethods(SetupApplication app) {
//        try {
//            if (app.getSourceSinkManager() instanceof AndroidSourceSinkManager) {
//                AndroidSourceSinkManager sourceSinkManager = (AndroidSourceSinkManager) app.getSourceSinkManager();
//                Field field = AndroidSourceSinkManager.class.getDeclaredField("callbackMethods");
//                field.setAccessible(true);
//                Map<SootMethod, CallbackDefinition> multiMap = (Map<SootMethod, CallbackDefinition>) field.get(sourceSinkManager);
//                cbMethods = new HashSet<>(multiMap.keySet());
//            }
//        } catch (NoSuchFieldException | IllegalAccessException e) {
//            e.printStackTrace();
//        }
//        if (cbMethods == null) {
//            System.out.println("The callback methods are not initialized");
//            throw new RuntimeException("The callback methods are not initialized");
//        }
        newCallBackMethods = new HashSet<>();
        File file = new File("public/AndroidCallBacks.txt");
        Scanner sc;
        try {
            sc = new Scanner(file);
            while (sc.hasNextLine()) {
                String clbSignature = sc.nextLine();
                if (!Scene.v().containsClass(clbSignature))
                    continue;
                SootClass clbClass = Scene.v().getSootClass(clbSignature);
                for (SootClass possibleCBClass : Scene.v().getApplicationClasses()) {
                    if (Util.v().isSubclass(possibleCBClass.getName(), clbSignature)) {
                        for (SootMethod sootMethod : clbClass.getMethods()) {
                            if (sootMethod.getName().equals("<init>") || sootMethod.getName().equals("<clinit>"))
                                continue;
                            // Check if the method is overridden
                            if (!Scene.v().containsMethod("<" + clbSignature + ": " + sootMethod.getSubSignature() + ">"))
                                continue;
                            newCallBackMethods.add(sootMethod);
                        }
                        break;
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    public Set<SootMethod> getNewCallBackMethods() {
        return newCallBackMethods;
    }

    public void addComponentPackageName(String packageName) {
        componentPackageNames.add(packageName);
    }

    // Method and Class helpers
    public boolean isHandlerPostOrSendMethodName(String methodName) {
        // TODO Refactor
        return methodName.equals("post") || methodName.equals("postDelayed")
                || methodName.equals("postAtFrontOfQueue") || methodName.equals("sendMessage")
                || methodName.equals("sendMessageDelayed")
                || methodName.equals("sendMessageAtFrontOfQueue");
        // TODO: check sendEmptyMessage and other possible methods
    }

    public boolean isAPKMethod(SootMethod sootMethod) {
        if (sootMethod.isPhantom() || sootMethod.getDeclaringClass().isPhantom() || sootMethod.getDeclaringClass().isLibraryClass())
            return false;
        if (sootMethod.getDeclaringClass().getName().startsWith("java.")
            || sootMethod.getDeclaringClass().getName().startsWith("android.")
            || sootMethod.getDeclaringClass().getName().startsWith("androidx."))
            return false;
        boolean isGoogleApp = false;
        for(String packageName : componentPackageNames){
            if (packageName.startsWith("com.android") || packageName.startsWith("com.google")) {
                isGoogleApp = true;
                break;
            }
        }
        if(!isGoogleApp)
            return !sootMethod.getDeclaringClass().getName().startsWith("com.android.") && !sootMethod.getDeclaringClass().getName().startsWith("com.google.");
        return true;
    }

    public SootMethod findMethod(SootClass sootClass, String methodName){
        while(sootClass != null){
            try{
                SootMethod ret = sootClass.getMethodByName(methodName);
                return ret;
            }catch (Exception e){

            }
            if(sootClass.hasSuperclass())
                sootClass = sootClass.getSuperclass();
            else
                break;
        }
        return null;
    }

    public boolean isSubclass(Type t, String superType) {
        return isSubclass(t.toString(), superType);
    }

    public boolean isSubclass(String classSignature, String superType) {
        SootClass sc = Scene.v().getSootClass(classSignature);
        while (true) {
            if (sc.getName().equals(superType))
                return true;
            if (sc.hasSuperclass())
                sc = sc.getSuperclass();
            else
                break;
        }
        return false;
    }
}
