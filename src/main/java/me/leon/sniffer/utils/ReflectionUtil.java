package me.leon.sniffer.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ReflectionUtil {
    private static final String VERSION = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
    private static final Map<String, Class<?>> CLASS_CACHE = new HashMap<>();
    private static final Map<String, Method> METHOD_CACHE = new HashMap<>();
    private static final Map<String, Field> FIELD_CACHE = new HashMap<>();
    private static final Map<String, Constructor<?>> CONSTRUCTOR_CACHE = new HashMap<>();

    public static Class<?> getNMSClass(String name) {
        try {
            String className = "net.minecraft.server." + VERSION + "." + name;
            return getClass(className);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Class<?> getCraftClass(String name) {
        try {
            String className = "org.bukkit.craftbukkit." + VERSION + "." + name;
            return getClass(className);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Class<?> getClass(String className) {
        Class<?> cachedClass = CLASS_CACHE.get(className);
        if (cachedClass != null) {
            return cachedClass;
        }

        try {
            Class<?> clazz = Class.forName(className);
            CLASS_CACHE.put(className, clazz);
            return clazz;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Object getHandle(Object craftObject) {
        try {
            Method handleMethod = craftObject.getClass().getMethod("getHandle");
            return handleMethod.invoke(craftObject);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Constructor<?> getConstructor(Class<?> clazz, Class<?>... parameterTypes) {
        String key = clazz.getName() + Arrays.toString(parameterTypes);
        Constructor<?> cachedConstructor = CONSTRUCTOR_CACHE.get(key);

        if (cachedConstructor != null) {
            return cachedConstructor;
        }

        try {
            Constructor<?> constructor = clazz.getConstructor(parameterTypes);
            constructor.setAccessible(true);
            CONSTRUCTOR_CACHE.put(key, constructor);
            return constructor;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        String key = clazz.getName() + methodName + Arrays.toString(parameterTypes);
        Method cachedMethod = METHOD_CACHE.get(key);

        if (cachedMethod != null) {
            return cachedMethod;
        }

        try {
            Method method = clazz.getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            METHOD_CACHE.put(key, method);
            return method;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Field getField(Class<?> clazz, String fieldName) {
        String key = clazz.getName() + fieldName;
        Field cachedField = FIELD_CACHE.get(key);

        if (cachedField != null) {
            return cachedField;
        }

        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            FIELD_CACHE.put(key, field);
            return field;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Object getFieldValue(Object object, String fieldName) {
        try {
            Field field = getField(object.getClass(), fieldName);
            return field.get(object);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void setFieldValue(Object object, String fieldName, Object value) {
        try {
            Field field = getField(object.getClass(), fieldName);
            field.set(object, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Object invokeMethod(Object object, String methodName, Object... args) {
        try {
            Class<?>[] parameterTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                parameterTypes[i] = args[i].getClass();
            }

            Method method = getMethod(object.getClass(), methodName, parameterTypes);
            return method.invoke(object, args);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static int getPing(Player player) {
        try {
            Object entityPlayer = getHandle(player);
            Field pingField = getField(entityPlayer.getClass(), "ping");
            return pingField.getInt(entityPlayer);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static void clearCaches() {
        CLASS_CACHE.clear();
        METHOD_CACHE.clear();
        FIELD_CACHE.clear();
        CONSTRUCTOR_CACHE.clear();
    }
}