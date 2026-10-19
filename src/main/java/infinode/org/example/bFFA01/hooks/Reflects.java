package infinode.org.example.bFFA01.hooks;

import java.lang.reflect.Method;

/**
 * Reflection helpers for soft-dependency APIs. Failures return null instead of throwing
 * into gameplay.
 */
public final class Reflects {

    private Reflects() {
    }

    public static Object call(Object target, String name, Object... args) {
        if (target == null || name == null) {
            return null;
        }
        Method method = find(target.getClass(), name, args);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean invoke(Object target, String name, Object... args) {
        if (target == null || name == null) {
            return false;
        }
        Method method = find(target.getClass(), name, args);
        if (method == null) {
            return false;
        }
        try {
            method.invoke(target, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Object callStatic(Class<?> type, String name, Object... args) {
        if (type == null || name == null) {
            return null;
        }
        Method method = find(type, name, args);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Class<?> load(ClassLoader loader, String name) {
        if (loader == null || name == null) {
            return null;
        }
        try {
            return Class.forName(name, true, loader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method find(Class<?> type, String name, Object[] args) {
        int count = args == null ? 0 : args.length;
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != count) {
                continue;
            }
            if (matches(method.getParameterTypes(), args)) {
                return method;
            }
        }
        return null;
    }

    private static boolean matches(Class<?>[] types, Object[] args) {
        if (args == null) {
            return types.length == 0;
        }
        for (int i = 0; i < types.length; i++) {
            if (args[i] == null) {
                if (types[i].isPrimitive()) {
                    return false;
                }
                continue;
            }
            if (!wrap(types[i]).isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        return type;
    }
}
