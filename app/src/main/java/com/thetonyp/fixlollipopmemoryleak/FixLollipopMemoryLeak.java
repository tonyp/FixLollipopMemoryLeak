package com.thetonyp.fixlollipopmemoryleak;

import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.IBinder;
import android.view.Surface;
import android.opengl.GLES20;

import java.nio.FloatBuffer;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class FixLollipopMemoryLeak implements IXposedHookLoadPackage {
    private static final String TAG = "FixLollipopLeak";
    private static final boolean DEBUG = false;

    private static void log(String entry) {
        XposedBridge.log(TAG + ": " + entry);
    }

    private static final String CLASS_DISPLAY_COLOR_FADE = "com.android.server.display.ColorFade";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("android") || !lpparam.processName.equals("android"))
            return;

        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.LOLLIPOP) {
            log("module disabled. This will only work with Android 5.0.x!");
            return;
        }

        final Class<?> classColorFade = XposedHelpers.findClass(CLASS_DISPLAY_COLOR_FADE, lpparam.classLoader);

        XposedHelpers.findAndHookMethod(classColorFade, "captureScreenshotTextureAndSetViewport",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {

                        boolean attachEglContext = (Boolean) XposedHelpers.callMethod(param.thisObject, "attachEglContext");
                        if (!attachEglContext) {
                            if (DEBUG) log("EGL Context not attached");
                            return false;
                        }

                        int[] mTexNames = (int[]) XposedHelpers.getObjectField(param.thisObject, "mTexNames");
                        boolean mTexNamesGenerated = XposedHelpers.getBooleanField(param.thisObject, "mTexNamesGenerated");

                        try {
                            if (!mTexNamesGenerated) {
                                GLES20.glGenTextures(1, mTexNames, 0);
                                boolean checkGlErrors = (Boolean) XposedHelpers.callMethod(param.thisObject, "checkGlErrors", "glGenTextures");
                                if (checkGlErrors) {
                                    if (DEBUG) log("OpenGL error occured");
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
                                if (DEBUG) log("Surface and SurfaceTexture released");
                            }

                            // Set up texture coordinates for a quad.
                            // We might need to change this if the texture ends up being
                            // a different size from the display for some reason.
                            ((FloatBuffer) XposedHelpers.getObjectField(param.thisObject, "mTexCoordBuffer")).put(0, 0f);
                            ((FloatBuffer) XposedHelpers.getObjectField(param.thisObject, "mTexCoordBuffer")).put(1, 0f);
                            ((FloatBuffer) XposedHelpers.getObjectField(param.thisObject, "mTexCoordBuffer")).put(2, 0f);
                            ((FloatBuffer) XposedHelpers.getObjectField(param.thisObject, "mTexCoordBuffer")).put(3, 1f);
                            ((FloatBuffer) XposedHelpers.getObjectField(param.thisObject, "mTexCoordBuffer")).put(4, 1f);
                            ((FloatBuffer) XposedHelpers.getObjectField(param.thisObject, "mTexCoordBuffer")).put(5, 1f);
                            ((FloatBuffer) XposedHelpers.getObjectField(param.thisObject, "mTexCoordBuffer")).put(6, 1f);
                            ((FloatBuffer) XposedHelpers.getObjectField(param.thisObject, "mTexCoordBuffer")).put(7, 0f);

                            // Set up our viewport.
                            int mDisplayWidth = XposedHelpers.getIntField(param.thisObject, "mDisplayWidth");
                            int mDisplayHeight = XposedHelpers.getIntField(param.thisObject, "mDisplayHeight");
                            GLES20.glViewport(0, 0, mDisplayWidth, mDisplayHeight);
                            XposedHelpers.callMethod(param.thisObject, "ortho", 0, mDisplayWidth, 0, mDisplayHeight, -1, 1);
                        } finally {
                            XposedHelpers.callMethod(param.thisObject, "detachEglContext");
                            if (DEBUG) log("EGL Context detached");
                        }
                        if (DEBUG) log("exiting method");
                        return true;
                    }
                });
    }

}