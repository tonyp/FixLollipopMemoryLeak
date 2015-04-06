package com.thetonyp.fixlollipopmemoryleak;

import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.IBinder;
import android.view.Surface;
import android.opengl.GLES20;

import java.nio.FloatBuffer;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class FixLollipopMemoryLeak implements IXposedHookLoadPackage {
    private static final String TAG = "FixLollipopLeak";
    private static final boolean DEBUG = false;
    private static boolean userDebugging = false;

    private XSharedPreferences prefs;

    private static void debugLog(String entry) {
        if (DEBUG || userDebugging) XposedBridge.log(TAG + ": " + entry);
    }

    private static final String CLASS_DISPLAY_COLOR_FADE = "com.android.server.display.ColorFade";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!"android".equals(lpparam.packageName) || !"android".equals(lpparam.processName))
            return;

        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.LOLLIPOP) {
            XposedBridge.log(TAG + ": DISABLED. This module only works with Android 5.0.x, not with " + Build.VERSION.RELEASE);
            return;
        }

        final XSharedPreferences prefs = new XSharedPreferences(BuildConfig.APPLICATION_ID);
        userDebugging = prefs.getBoolean("pref_debug", false);
        debugLog("v" + BuildConfig.VERSION_NAME + ", Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");

        final Class<?> classColorFade = XposedHelpers.findClass(CLASS_DISPLAY_COLOR_FADE, lpparam.classLoader);

        XposedHelpers.findAndHookMethod(classColorFade, "captureScreenshotTextureAndSetViewport",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {

                        boolean attachEglContext = (boolean) XposedHelpers.callMethod(param.thisObject, "attachEglContext");
                        if (!attachEglContext) {
                            debugLog("EGL Context not attached");
                            return false;
                        }

                        int[] mTexNames = (int[]) XposedHelpers.getObjectField(param.thisObject, "mTexNames");
                        boolean mTexNamesGenerated = XposedHelpers.getBooleanField(param.thisObject, "mTexNamesGenerated");

                        try {
                            if (!mTexNamesGenerated) {
                                GLES20.glGenTextures(1, mTexNames, 0);
                                boolean checkGlErrors = (boolean) XposedHelpers.callMethod(param.thisObject, "checkGlErrors", "glGenTextures");
                                if (checkGlErrors) {
                                    debugLog("OpenGL error occured");
                                    return false;
                                }
                                XposedHelpers.setBooleanField(param.thisObject, "mTexNamesGenerated", true);
                            }

                            final SurfaceTexture st = new SurfaceTexture(mTexNames[0]);
                            final Surface s = new Surface(st);
                            try {

                                //Using reflection to access SurfaceControl's hidden API
                                IBinder bid = (IBinder) XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.view.SurfaceControl", null), "getBuiltInDisplay", 0);
                                XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.view.SurfaceControl", null), "screenshot", bid, s);

                                st.updateTexImage();
                                float mTexMatrix[] = (float[]) XposedHelpers.getObjectField(param.thisObject, "mTexMatrix");
                                st.getTransformMatrix(mTexMatrix);
                            } finally {
                                s.release();
                                st.release();
                                debugLog("Surface and SurfaceTexture released");
                            }

                            // Set up texture coordinates for a quad.
                            // We might need to change this if the texture ends up being
                            // a different size from the display for some reason.
                            FloatBuffer texCoordBuffer = (FloatBuffer) XposedHelpers.getObjectField(param.thisObject, "mTexCoordBuffer");
                            texCoordBuffer.put(0, 0f);
                            texCoordBuffer.put(1, 0f);
                            texCoordBuffer.put(2, 0f);
                            texCoordBuffer.put(3, 1f);
                            texCoordBuffer.put(4, 1f);
                            texCoordBuffer.put(5, 1f);
                            texCoordBuffer.put(6, 1f);
                            texCoordBuffer.put(7, 0f);

                            // Set up our viewport.
                            int mDisplayWidth = XposedHelpers.getIntField(param.thisObject, "mDisplayWidth");
                            int mDisplayHeight = XposedHelpers.getIntField(param.thisObject, "mDisplayHeight");
                            GLES20.glViewport(0, 0, mDisplayWidth, mDisplayHeight);
                            XposedHelpers.callMethod(param.thisObject, "ortho", 0, mDisplayWidth, 0, mDisplayHeight, -1, 1);
                        } finally {
                            XposedHelpers.callMethod(param.thisObject, "detachEglContext");
                            debugLog("EGL Context detached");
                        }
                        debugLog("exiting hook");
                        return true;
                    }
                });
    }
}