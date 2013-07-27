package com.zipeg;

import java.util.*;
import java.lang.reflect.*;

public class calls {

    public static final Class[] VOID = new Class[]{};
    public static final Class[] OBJECT = new Class[]{Object.class};
    public static final Class[] STRING = new Class[]{String.class};
    public static final Class[] BOOLEAN = new Class[]{boolean.class};
    public static final Class[] MAP = new Class[]{Map.class};
    public static final Object[] NONE = new Object[]{};

    public static Object callStatic(String method) {
        return callStatic(method, NONE);
    }

    public static Object callStatic(String method, Object[] params) {
        try {
            int ix = method.lastIndexOf('.');
            String cls = method.substring(0, ix);
            String mtd = method.substring(ix + 1);
            Class[] signature;
            if (params.length == 0) {
                signature = new Class[]{};
            } else {
                signature = new Class[params.length];
                for (int i = 0; i < params.length; i++) {
                    signature[i] = params[i].getClass();
                }
            }
            return Class.forName(cls).getMethod(mtd, signature).invoke(null, params);
        } catch (Throwable e) {
            throw new Error(e);
        }
    }

    public static Object callStatic(Method method, Object[] params) {
        try {
            return method.invoke(null, params);
        } catch (Throwable e) {
            throw new Error(e);
        }
    }

    public static Method getDeclaredMethod(String method, Class[] s) {
        try {
            int ix = method.lastIndexOf('.');
            String cls = method.substring(0, ix);
            String mtd = method.substring(ix + 1);
            Method m = Class.forName(cls).getDeclaredMethod(mtd, s);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            Debug.printStackTrace("no such method " + method, e);
            return null;
        } catch (ClassNotFoundException e) {
            Debug.printStackTrace("class not found " + method, e);
            return null;
        }
    }

    public static Method getMethod(String method, Class[] s) {
        try {
            int ix = method.lastIndexOf('.');
            String cls = method.substring(0, ix);
            String mtd = method.substring(ix + 1);
            Method m = Class.forName(cls).getMethod(mtd, s);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            Debug.printStackTrace("no such method " + method, e);
            return null;
        } catch (ClassNotFoundException e) {
            Debug.printStackTrace("class not found " + method, e);
            return null;
        }
    }

    public static Method getAccessibleMethod(String method, Class[] signature) {
        Method m = calls.getMethod(method, signature);
        return m != null ? m : calls.getDeclaredMethod(method, signature);
    }

    public static Object call(Object that, Method m, Object[] params) {
        try {
            return m == null ? null : m.invoke(that, params);
        } catch (IllegalAccessException e) {
            Debug.printStackTrace("failed to call " + m, e);
            return null;
        } catch (InvocationTargetException e) {
            Debug.printStackTrace("failed to call " + m, e);
            throw new Error(e.getCause());
        }
    }

    public static Object call(Object that, String method, Class[] signature, Object[] params) {
        if (method.indexOf('.') < 0) {
            method = that.getClass().getName() + "." + method;
        }
        return calls.call(that, calls.getAccessibleMethod(method, signature), params);
    }

    private calls() {}

}
