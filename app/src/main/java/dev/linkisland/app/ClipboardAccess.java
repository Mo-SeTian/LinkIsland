package dev.linkisland.app;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Known AOSP / MIUI signatures, ordered so the vendor userOperate overload is tried first. */
final class ClipboardAccess {
    private static final Class<?>[][] SIGNATURES = {
        {String.class, String.class, int.class, int.class, boolean.class},
        {String.class, String.class, String.class, String.class, int.class, int.class, boolean.class},
        {String.class, String.class, int.class, int.class, String.class},
        {String.class, String.class, int.class, int.class},
        {String.class, String.class, int.class},
        {String.class, int.class, String.class},
        {String.class, int.class},
        {String.class}
    };
    private final Object service;
    private final int userId;
    private final List<Method> readers = new ArrayList<>();
    private String signature = "未读取";

    ClipboardAccess(Object service, int userId) throws NoSuchMethodException {
        this.service = service;
        this.userId = userId;
        for (Class<?>[] types : SIGNATURES) {
            try {
                Method method = service.getClass().getMethod("getPrimaryClip", types);
                method.setAccessible(true);
                readers.add(method);
            } catch (NoSuchMethodException ignored) { }
        }
        if (readers.isEmpty()) throw new NoSuchMethodException("getPrimaryClip");
    }
    Object read() throws ReflectiveOperationException {
        ReflectiveOperationException failure = null;
        boolean returnedEmpty = false;
        for (Method method : readers) {
            try {
                Object result = method.invoke(service, arguments(method, null));
                signature = describe(method);
                if (result != null) return result;
                returnedEmpty = true;
            } catch (ReflectiveOperationException e) { failure = e; }
        }
        if (!returnedEmpty && failure != null) throw failure;
        return null;
    }
    Method listenerMethod(String name, Class<?> listenerClass) throws NoSuchMethodException {
        for (Class<?>[] tail : SIGNATURES) {
            // Listener registration does not have the MIUI read-only extensions.
            if (tail.length > 4 || (tail.length == 4 && tail[3] != int.class)) continue;
            Class<?>[] types = new Class<?>[tail.length + 1];
            types[0] = listenerClass;
            System.arraycopy(tail, 0, types, 1, tail.length);
            try {
                Method method = service.getClass().getMethod(name, types);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
    Object[] arguments(Method method, Object listener) {
        Class<?>[] types = method.getParameterTypes();
        Object[] result = new Object[types.length];
        int strings = 0, integers = 0;
        for (int i = 0; i < types.length; i++) {
            if (listener != null && i == 0) result[i] = listener;
            else if (types[i] == String.class) result[i] = strings++ == 0 ? "com.android.shell" : null;
            else if (types[i] == int.class) result[i] = integers++ == 0 ? userId : 0;
            else if (types[i] == boolean.class) result[i] = true;
        }
        return result;
    }
    String signature() { return signature; }
    private static String describe(Method method) {
        List<String> names = new ArrayList<>();
        for (Class<?> type : method.getParameterTypes()) names.add(type.getSimpleName());
        return "(" + String.join(",", names) + ")";
    }
}
