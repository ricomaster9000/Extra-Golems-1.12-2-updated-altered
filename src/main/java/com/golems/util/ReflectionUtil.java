package com.golems.util;

import com.golems.main.ExtraGolems;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ReflectionUtil {

    private static final Logger LOGGER = LogManager.getFormatterLogger(ExtraGolems.MODID);
    public static final List<Class<?>> BASE_VALUE_TYPES = Arrays.asList(new Class<?>[]{
            String.class,
            Character.class,
            Byte.class,
            Integer.class,
            Short.class,
            Long.class,
            Boolean.class,
            Double.class,
            Float.class,
            BigDecimal.class,
            BigInteger.class,
            Number.class,
            Timestamp.class,
            Calendar.class,
            java.sql.Date.class,
            Date.class,
            Class.class,
            byte[].class, // its sort of primitive just wrapped in a array and the usual base way of working with bytes for every object
            char.class,
            byte.class,
            int.class,
            short.class,
            long.class,
            boolean.class,
            double.class,
            float.class
    });

    private static final ConcurrentHashMap<String, List<ReflectionSimilarClassToClassMethod>> similarClassToClassMethodGroupingByClassToClassNames = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Method> methodsCached = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Field> fieldsCached = new ConcurrentHashMap<>();
    private static final String CALL_METHOD_QUICK_CACHE_KEY_LOOKUP_UNFORMATTED = "cc%s_%s__%s";
    private static final String SET_FIELD_QUICK_CACHE_KEY_LOOKUP_UNFORMATTED = "ff%s_%s__";
    private static final String SET_METHOD_QUICK_CACHE_KEY_LOOKUP_UNFORMATTED = "mm%s_%s__";


    private static final Timer cacheCleanerLowProfileTimer;
    static {
        Timer timer = new Timer();
        timer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        methodsCached.clear();
                        fieldsCached.clear();
                        similarClassToClassMethodGroupingByClassToClassNames.clear();
                    }
                },
                300000L, 300000L
        );
        cacheCleanerLowProfileTimer = timer;
    }

    public static boolean fieldExists(Class<?> clazz, String field) {
        return fieldExists(field,clazz);
    }

    public static boolean fieldExists(String field, Class<?> clazz) {
        return Arrays.stream(getClassFields(clazz, false,null)).
                anyMatch(clazzField -> clazzField.getName().equals(field));
    }

    public static <T> T getFieldValueNoException(String field, Object instance) {
        T result = null;
        try {
            result = getFieldValue(field, instance);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {}
        return result;
    }

    public static Field getFieldFromClass(String fieldName, Class<?> clazz) {
        Field field = null;
        Class<?> currentClass = clazz;
        // Search through the class hierarchy
        while (currentClass != null) {
            try {
                // Try to find the field in the current class
                field = currentClass.getDeclaredField(fieldName);
                break; // Exit loop if field is found
            } catch (Exception e) {
                // Move to the superclass if the field is not found
                currentClass = currentClass.getSuperclass();
            }
        }
        return field;
    }

    public static <T> T getFieldValue(String field, Object instance) throws NoSuchFieldException, IllegalAccessException {
        T result = null;
        Field fieldReflection = null;
        boolean hadToSetMethodToAccessible = false;
        boolean hadToSetFieldToAccessible = false;

        try {
            fieldReflection = getFieldFromClass(field, instance.getClass());
            if(fieldReflection == null) {
                throw new NoSuchFieldException("field");
            }
        } catch(NoSuchFieldException e) {
            try {
                Method fieldGetterMethod = instance.getClass().getMethod("get" + capitalize(field));
                try {
                    if (!fieldGetterMethod.isAccessible()) {
                        hadToSetMethodToAccessible = true;
                        fieldGetterMethod.setAccessible(true);
                        result = (T) fieldGetterMethod.invoke(instance);
                    } else {
                        result = (T) fieldGetterMethod.invoke(instance);
                    }
                    return result;
                } finally {
                    if(hadToSetMethodToAccessible) {
                        fieldGetterMethod.setAccessible(false);
                    }
                }
            } catch(NoSuchMethodException | InvocationTargetException innerException) {
                throw e;
            }
        }
        try {
            if (!fieldReflection.isAccessible()) {
                fieldReflection.setAccessible(true);
                hadToSetFieldToAccessible = true;
                result = (T) fieldReflection.get(instance);
            } else {
                result = (T) fieldReflection.get(instance);
            }
        } finally {
            if(hadToSetFieldToAccessible) {
                fieldReflection.setAccessible(false);
            }
        }
        return result;
    }

    public static void setFieldToNull(Object object, String fieldName) throws IllegalAccessException, NoSuchFieldException {
        setFieldValue(object, fieldName, null);
    }

    public static void setFieldValueNoException(Object object, String fieldName, Object fieldValue) {
        try {
            setFieldValue(object,fieldName,fieldValue);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {}
    }

    public static void setFieldValueViaSetter(Object object, String fieldName, Object fieldValue) throws IllegalAccessException, NoSuchMethodException {
        String methodCacheKey = String.format(SET_METHOD_QUICK_CACHE_KEY_LOOKUP_UNFORMATTED, object.getClass(), fieldName);
        Method method = methodsCached.get(methodCacheKey);
        try {
            if (method == null) {
                Class<?> fieldType = fieldValue != null ? fieldValue.getClass() : getFieldType(object, fieldName);
                method = object.getClass().getMethod("set" + capitalize(fieldName), fieldType);
                methodsCached.put(methodCacheKey, method);
            }
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodException("Setter method not found for field: " + fieldName);
        }

        boolean hadToSetMethodToAccessible = false;
        try {
            if (!method.isAccessible()) {
                hadToSetMethodToAccessible = true;
                method.setAccessible(true);
            }
            method.invoke(object, fieldValue);
        } catch (InvocationTargetException e) {
            throw new IllegalAccessException(e.getCause().getMessage());
        } finally {
            if (hadToSetMethodToAccessible) {
                method.setAccessible(false);
            }
        }
    }

    private static Class<?> getFieldType(Object object, String fieldName) throws NoSuchMethodException {
        try {
            return object.getClass().getMethod("get" + capitalize(fieldName)).getReturnType();
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodException("Getter method not found for field: " + fieldName);
        }
    }

    public static void setFieldViaDirectAccess(Object object, Field field, Object fieldValue) throws IllegalAccessException {
        boolean hadToSetFieldToAccessible = false;
        try {
            if(!field.isAccessible()) {
                field.setAccessible(true);
                hadToSetFieldToAccessible = true;
            }
            field.set(object, fieldValue);
        } finally {
            if(hadToSetFieldToAccessible) {
                field.setAccessible(false);
            }
        }
    }

    public static void setFieldValueAsynchronously(Object object, String fieldName, Object fieldValue) throws IllegalAccessException, NoSuchFieldException {
        setFieldValueAsynchronouslyInternal(object,fieldName,fieldValue,0);
    }

    private static void setFieldValueAsynchronouslyInternal(Object object, String fieldName, Object fieldValue, int totalTimesRetried) throws IllegalAccessException, NoSuchFieldException {
        String fieldCacheKey = String.format(SET_FIELD_QUICK_CACHE_KEY_LOOKUP_UNFORMATTED,object.getClass(),fieldName);
        Field field = fieldsCached.get(fieldCacheKey);
        if(field == null) {
            field = object.getClass().getDeclaredField(fieldName);
            fieldsCached.put(fieldCacheKey, field);
        }
        try {
            setFieldValueViaSetter(object,fieldName,fieldValue);
        } catch (NoSuchMethodException e) {
            if(totalTimesRetried >= 5) {
                setFieldViaDirectAccess(object, field, fieldValue);
            } else {
                setFieldValueAsynchronouslyInternal(object,fieldName,fieldValue,++totalTimesRetried);
            }
        }
    }

    public static void setFieldValue(Object object, String fieldName, Object fieldValue) throws IllegalAccessException, NoSuchFieldException {
        String fieldCacheKey = String.format(SET_FIELD_QUICK_CACHE_KEY_LOOKUP_UNFORMATTED,object.getClass(),fieldName);
        Field field = fieldsCached.get(fieldCacheKey);
        if(field == null) {
            try {
                field = object.getClass().getDeclaredField(fieldName);
                fieldsCached.put(fieldCacheKey, field);
            } catch (NoSuchFieldException e) {
                try {
                    setFieldValueViaSetter(object,fieldName,fieldValue);
                } catch (NoSuchMethodException ex) {
                    throw new NoSuchFieldException(ex.getMessage());
                }
            }
        }
        setFieldViaDirectAccess(object, field, fieldValue);
    }

    public static Field[] getClassFields(Class<?> clazz) {
        return getClassFields(clazz, false, new ArrayList<>());
    }

    public static Field[] getClassFields(Class<?> clazz, boolean excludeDeclaredCustomClassFields) {
        return getClassFields(clazz, excludeDeclaredCustomClassFields, new ArrayList<>());
    }

    public static Field[] getClassFields(Class<?> clazz, boolean excludeDeclaredCustomClassFields, List<Class<? extends Annotation>> bypassWithTheseAnnotations) {
        return getClassFields(clazz, excludeDeclaredCustomClassFields, bypassWithTheseAnnotations, true);
    }

    public static Field[] getClassFields(Class<?> clazz, boolean excludeDeclaredCustomClassFields, List<Class<? extends Annotation>> bypassWithTheseAnnotations, boolean includeLists) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> (
                        ((!excludeDeclaredCustomClassFields && !checkIfClassIsFromMainJavaPackages(field.getType())) ||
                                BASE_VALUE_TYPES.contains(field.getType()) ||
                                field.getType().isPrimitive() ||
                                field.getType().isEnum() ||
                                (includeLists && Collection.class.isAssignableFrom(field.getType()))) &&
                                Arrays.stream(field.getAnnotations()).noneMatch(annotation -> bypassWithTheseAnnotations != null && bypassWithTheseAnnotations.contains(annotation.annotationType()))
                )).toArray(Field[]::new);
    }

    public static Set<String> getGetters(Class<?> clazz) throws IntrospectionException {
        return Arrays.stream(Introspector.getBeanInfo(clazz).getPropertyDescriptors())
                .filter(propertyDescriptor -> propertyDescriptor.getReadMethod() != null)
                .map(propertyDescriptor -> propertyDescriptor.getReadMethod().getName())
                .collect(Collectors.toSet());
    }

    public static Set<Method> getGetterMethods(Class<?> clazz) throws IntrospectionException {
        return Arrays.stream(Introspector.getBeanInfo(clazz).getPropertyDescriptors())
                .filter(propertyDescriptor -> propertyDescriptor.getReadMethod() != null)
                .map(PropertyDescriptor::getReadMethod)
                .collect(Collectors.toSet());
    }

    public static Set<String> getGettersForBaseValueTypes(Class<?> clazz, boolean includeEnums, boolean includeLists) throws IntrospectionException {
        return getGetters(clazz, BASE_VALUE_TYPES, true, includeEnums, includeLists);
    }

    public static Set<String> getGetters(Class<?> clazz, List<Class<?>> onlyForTheseValueTypes, boolean includePrimitives, boolean includeEnums, boolean includeLists) throws IntrospectionException {
        List<Class<?>> finalOnlyForTheseValueTypes = (onlyForTheseValueTypes == null) ? new ArrayList<>() : new ArrayList<>(onlyForTheseValueTypes);
        return Arrays.stream(Introspector.getBeanInfo(clazz).getPropertyDescriptors())
                .filter(
                        propertyDescriptor -> propertyDescriptor.getReadMethod() != null &&
                                (
                                        finalOnlyForTheseValueTypes.contains(propertyDescriptor.getPropertyType()) ||
                                                (includePrimitives && propertyDescriptor.getPropertyType().isPrimitive()) ||
                                                (includeLists && Collection.class.isAssignableFrom(propertyDescriptor.getPropertyType())) ||
                                                (includeEnums && propertyDescriptor.getPropertyType().isEnum())
                                )
                )
                .map(propertyDescriptor -> propertyDescriptor.getReadMethod().getName())
                .collect(Collectors.toSet());
    }

    public static Set<String> getSettersForBaseValueTypes(Class<?> clazz, boolean includeEnums, boolean includeLists) throws IntrospectionException {
        return getSetters(clazz, BASE_VALUE_TYPES, true, includeEnums, includeLists);
    }

    public static Set<String> getSetters(Class<?> clazz) throws IntrospectionException {
        return Arrays.stream(Introspector.getBeanInfo(clazz).getPropertyDescriptors())
                .filter(propertyDescriptor -> propertyDescriptor .getWriteMethod() != null)
                .map(propertyDescriptor -> propertyDescriptor.getWriteMethod().getName())
                .collect(Collectors.toSet());
    }

    public static Set<String> getSetters(Class<?> clazz, List<Class<?>> onlyForTheseValueTypes) throws IntrospectionException {
        List<Class<?>> finalOnlyForTheseValueTypes = (onlyForTheseValueTypes == null) ? new ArrayList<>() : onlyForTheseValueTypes;;
        return Arrays.stream(Introspector.getBeanInfo(clazz).getPropertyDescriptors())
                .filter(propertyDescriptor -> propertyDescriptor.getWriteMethod() != null && finalOnlyForTheseValueTypes.contains(propertyDescriptor.getPropertyType()))
                .map(propertyDescriptor -> propertyDescriptor.getWriteMethod().getName())
                .collect(Collectors.toSet());
    }

    public static Set<String> getSetters(Class<?> clazz, List<Class<?>> onlyForTheseValueTypes, boolean includePrimitives, boolean includeEnums, boolean includeLists) throws IntrospectionException {
        List<Class<?>> finalOnlyForTheseValueTypes = (onlyForTheseValueTypes == null) ? new ArrayList<>() : onlyForTheseValueTypes;
        return Arrays.stream(Introspector.getBeanInfo(clazz).getPropertyDescriptors())
                .filter(
                        propertyDescriptor -> propertyDescriptor.getWriteMethod() != null &&
                                (
                                        finalOnlyForTheseValueTypes.contains(propertyDescriptor.getPropertyType()) ||
                                                (includePrimitives && propertyDescriptor.getPropertyType().isPrimitive()) ||
                                                (includeLists && Collection.class.isAssignableFrom(propertyDescriptor.getPropertyType())) ||
                                                (includeEnums && propertyDescriptor.getPropertyType().isEnum())
                                )
                )
                .map(propertyDescriptor -> propertyDescriptor.getWriteMethod().getName())
                .collect(Collectors.toSet());
    }

    /**
     * Scans all classes accessible from the context class loader which belong to the given package and subpackages.
     *
     * @param packageName The base package
     * @return The classes
     * @throws ClassNotFoundException
     * @throws IOException
     */
    public static List<Class<?>> getClasses(String packageName)
            throws ClassNotFoundException, IOException {

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<File>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }
        List<Class<?>> classes = new ArrayList<>();
        for (File directory : dirs) {
            List<Class<?>> innerClasses = findClasses(directory, packageName);
            for (Class<?> clazz : innerClasses) {
                Class<?> classToAdd = getClassByName(clazz.getName());
                classToAdd = classToAdd == null ? Class.forName(clazz.getName()) : classToAdd;
                classes.add(classToAdd);
            }
        }

        if(classes.size() <= 0) {
            classes = findAllClassesUsingRunningJarFile(packageName);
        }

        return classes;
    }

    public static List<Class<?>> findAllClassesUsingRunningJarFile(String packageName) {
        List<Class<?>> result = new ArrayList<>();

        Set<String> classNames = new HashSet<>();
        JarFile jarFile = getCurrentRunningJarFile(null);
        if(jarFile != null) {
            Enumeration<JarEntry> e = jarFile.entries();
            while (e.hasMoreElements()) {
                JarEntry jarEntry = e.nextElement();
                if (jarEntry.getName().endsWith(".class")) {
                    String className = jarEntry.getName()
                            .replace("/", ".")
                            .replace(".class", "");
                    classNames.add(className);
                }
            }

            for (String className : classNames) {
                try {
                    Class<?> classToAdd = getClassByName(className);
                    classToAdd = classToAdd == null ? Class.forName(className) : classToAdd;
                    result.add(classToAdd);
                } catch (Exception | LinkageError ignored) {
                }
            }
        }
        return result;
    }

    private static JarFile getCurrentRunningJarFile(String jarFileName) {
        JarFile jarFile = null;

        try {
            jarFile = new JarFile(getRunningJarPath(getCallerClassName()).toFile());
        } catch(Exception ignored) {}

        if(jarFile == null && getContextClassLoader().getClass().getProtectionDomain() != null && getContextClassLoader().getClass().getProtectionDomain().getCodeSource() != null)
        {
            try {
                jarFile = new JarFile(getContextClassLoader().getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
            } catch (Exception ignored) {}
        }
        if(jarFile == null) {
            try {
                jarFile = new JarFile("application.jar");
            } catch (Exception ignored) {}
        }
        if(jarFile == null) {
            try {
                jarFile = new JarFile("app.jar");
            } catch (Exception ignored) {}
        }
        if(jarFile == null && jarFileName != null) {
            try {
                jarFile = new JarFile(jarFileName);
            } catch (Exception ignored) {}
        }
        return jarFile;
    }

    private static String getCallerClassName() {
        StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
        String callerClassName = null;
        for (int i=1; i<stElements.length; i++) {
            StackTraceElement ste = stElements[i];
            if (!ste.getClassName().equals(ReflectionUtil.class.getName())&& ste.getClassName().indexOf("java.lang.Thread")!=0) {
                if (callerClassName==null) {
                    callerClassName = ste.getClassName();
                } else if (!callerClassName.equals(ste.getClassName())) {
                    return ste.getClassName();
                }
            }
        }
        return null;
    }

    private static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    private static Path getRunningJarPath(String callerClassName) {
        Path result = null;
        try {
            result = Paths.get(Thread.currentThread()
                    .getContextClassLoader()
                    .loadClass(callerClassName)
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch(ClassNotFoundException | URISyntaxException ignored) {}
        return result;
    }

    private static Class<?> getClass(String className, String packageName) {
        try {
            return Class.forName(packageName + "."
                    + className.substring(0, className.lastIndexOf('.')));
        } catch (ClassNotFoundException e) {
            // handle the exception
        }
        return null;
    }

    /**
     * Recursive method used to find all classes in a given directory and subdirs.
     *
     * @param directory   The base directory
     * @param packageName The package name for classes found inside the base directory
     * @return The classes
     * @throws ClassNotFoundException
     */
    private static List<Class<?>> findClasses(File directory, String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        if (!directory.exists()) {
            return classes;
        }
        File[] files = directory.listFiles();
        if(files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    assert !file.getName().contains(".");
                    classes.addAll(findClasses(file, packageName + "." + file.getName()));
                } else if (file.getName().endsWith(".class")) {
                    classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
                }
            }
        }
        return classes;
    }

    public static List<Object> getAllConstantValuesInClass(Class<?> clazz) throws IOException, ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        List<Object> result = new ArrayList<>();

        Field[] fields = getClassFields(clazz);
        for (Field field : fields) {
            // Check if the field is a constant (static and final)
            if (isConstantField(field)) {
                result.add(clazz.getDeclaredField(field.getName()).get(null));
            }
        }

        return result;
    }

    public static boolean isConstantField(Field field) {
        // Check if the field is a constant (static and final)
        int modifiers = field.getModifiers();
        return (java.lang.reflect.Modifier.isStatic(modifiers) && java.lang.reflect.Modifier.isFinal(modifiers));
    }

    public static String capitalize(String str) {
        return capitalizeString(str);
    }

    public static String capitalizeString(String str) {
        return (str != null && str.length() > 0) ? str.substring(0, 1).toUpperCase() + str.substring(1) : str;
    }

    public static <T> T[] concatenate(T[] a, T[] b) {
        int aLen = a.length;
        int bLen = b.length;
        @SuppressWarnings("unchecked")
        T[] c = (T[]) Array.newInstance(a.getClass().getComponentType(), aLen + bLen);
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }

    public static boolean checkIfClassIsFromMainJavaPackages(Class<?> clazz) {
        return (clazz.getName().startsWith("java.") ||
                clazz.getName().startsWith("javax.") ||
                clazz.getName().startsWith("javafx.") ||
                clazz.getName().startsWith("com.sun.") ||
                clazz.getName().startsWith("com.oracle.") ||
                clazz.getName().startsWith("org.apache.") ||
                clazz.getName().startsWith("jdk.") ||
                clazz.getName().startsWith("org.w3c.") ||
                clazz.getName().startsWith("org.xml.") ||
                clazz.getName().startsWith("org.ietf.")
        );
    }

    public static Object callReflectionMethod(Object object, String methodName) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        return callReflectionMethod(object, methodName, null, null);
    }

    // I WOULD SAY WITH THE LATER VERSIONS OF JAVA, JAVA Reflection logic should run fast enough (if kept simple)
    public static Object callReflectionMethod(Object object, String methodName, Object[] methodParams, Class<?>[] methodParamTypes) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        Object methodResult;
        boolean setParams = methodParams != null && methodParams.length != 0;
        Method method = setParams ? object.getClass().getMethod(methodName, methodParamTypes) : object.getClass().getMethod(methodName);
        boolean hadToSetMethodToAccessible = false;
        if(!method.isAccessible()) {
            method.setAccessible(true);
            hadToSetMethodToAccessible = true;
        }
        try {
            if (setParams) {
                methodResult = method.invoke(object, methodParams);
            } else {
                methodResult = method.invoke(object);
            }
        } finally {
            if(hadToSetMethodToAccessible) {
                method.setAccessible(false);
            }
        }
        return methodResult;
    }

    public static Object callReflectionMethodQuick(Object object, String methodName) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        return callReflectionMethodQuick(object,methodName,null,Object.class);
    }

    public static Object callReflectionMethodQuick(Object object, String methodName, Object methodParam, Class<?> methodParamType) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        boolean setParams = methodParam != null && methodParamType != null;
        String methodCacheKey = String.format(CALL_METHOD_QUICK_CACHE_KEY_LOOKUP_UNFORMATTED,object.getClass(),methodName,methodParamType);
        Method method = methodsCached.get(methodCacheKey);
        if(method == null) {
            method = setParams ? object.getClass().getMethod(methodName, methodParamType) : object.getClass().getMethod(methodName);
            methodsCached.put(methodCacheKey,method);
        }
        return (setParams) ? method.invoke(object, methodParam) : method.invoke(object);
    }

    public static Object callReflectionMethodQuickIgnoreException(Object object, String methodName) {
        return callReflectionMethodQuickIgnoreException(object,methodName, null, Object.class);
    }

    public static Object callReflectionMethodQuickIgnoreException(Object object, String methodName, Object methodParam, Class<?> methodParamType) {
        Object result = null;
        boolean setParams = methodParam != null && methodParamType != null;
        String methodCacheKey;
        Method method;
        try {
            methodCacheKey = String.format(CALL_METHOD_QUICK_CACHE_KEY_LOOKUP_UNFORMATTED,object.getClass(),methodName,methodParamType);
            method = methodsCached.get(methodCacheKey);
            if (method == null) {
                method = setParams ? object.getClass().getMethod(methodName, methodParamType) : object.getClass().getMethod(methodName);
                methodsCached.put(methodCacheKey, method);
            }
            result = (setParams) ? method.invoke(object, methodParam) : method.invoke(object);
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException ignored) {}
        return result;
    }

    public static Object callReflectionMethodQuick(Object object, String methodName, Object[] methodParam, Class<?>[] methodParamType) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        boolean setParams = methodParam != null && methodParam.length > 0;

        String methodCacheKey = setParams ?
                object.getClass()+"_"+methodName+"_"+methodParamType[0]+"_"+methodParamType.length :
                object.getClass()+"_"+methodName;

        Method method = methodsCached.get(methodCacheKey);
        if(method == null) {
            method = setParams ? object.getClass().getMethod(methodName, methodParamType) : object.getClass().getMethod(methodName);
            methodsCached.put(methodCacheKey,method);
        }
        return (setParams) ? method.invoke(object, methodParam) : method.invoke(object);
    }

    public static Object callReflectionMethod(Object object, Method method) throws InvocationTargetException, IllegalAccessException {
        return callReflectionMethod(object,method,null);
    }

    public static Object callReflectionMethod(Object object, Method method, Object... methodParams) throws InvocationTargetException, IllegalAccessException {
        Object methodResult;
        boolean setParams = method.getParameterTypes().length > 0 && methodParams != null;
        boolean hadToSetMethodToAccessible = false;
        if(!method.isAccessible()) {
            method.setAccessible(true);
            hadToSetMethodToAccessible = true;
        }
        try {
            if (setParams) {
                methodResult = method.invoke(object, methodParams);
            } else {
                methodResult = method.invoke(object);
            }
        } finally {
            if(hadToSetMethodToAccessible) {
                method.setAccessible(false);
            }
        }
        return methodResult;
    }

    public static Object callReflectionMethodQuick(Object object, Method method, Object... methodParams) throws InvocationTargetException, IllegalAccessException {
        return (methodParams != null) ? method.invoke(object, methodParams) : method.invoke(object);
    }

    public static <T> T callReflectionMethodGeneric(Object object, String methodName) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        return callReflectionMethodGeneric(object, methodName, null, null);
    }

    public static <T> T callReflectionMethodGeneric(Object object, String methodName, Object[] methodParams, Class<?>[] methodParamTypes) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        Object methodResult;
        boolean setParams = methodParams != null && methodParams.length != 0;
        Method method = setParams ? object.getClass().getMethod(methodName, methodParamTypes) : object.getClass().getMethod(methodName);
        boolean hadToSetMethodToAccessible = false;
        if(!method.isAccessible()) {
            method.setAccessible(true);
            hadToSetMethodToAccessible = true;
        }
        try {
            if (setParams) {
                methodResult = method.invoke(object, methodParams);
            } else {
                methodResult = method.invoke(object);
            }
        } finally {
            if(hadToSetMethodToAccessible) {
                method.setAccessible(false);
            }
        }
        return (T) methodResult;
    }

    public static Class<?> getClassByName(String fullName) {
        Class<?> result = null;
        Method method = null;
        boolean methodHadToBeSetToAccessible = false;
        try {
            try {
                result = Thread.currentThread().getContextClassLoader().loadClass(fullName);
            } catch (Exception ignore) {}
            if(result == null) {
                method = Thread.currentThread().getContextClassLoader().getClass().getDeclaredMethod("findClass", String.class, String.class);
                if (!method.isAccessible()) {
                    method.setAccessible(true);
                    methodHadToBeSetToAccessible = true;
                }
                result = (Class<?>) method.invoke(Thread.currentThread().getContextClassLoader(), fullName);
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            return null;
        } finally {
            if(methodHadToBeSetToAccessible) {
                method.setAccessible(false);
            }
        }
        return result;
    }

    public static <T> T cleanObject(T objectToClean) throws NoSuchFieldException, IllegalAccessException {
        if(objectToClean != null) {
            List<Field> fields = Arrays.asList(getClassFields(objectToClean.getClass()));
            for(Field field : fields) {
                if(field.getType().isPrimitive()) {
                    if (isNumericField(field)) {
                        setFieldValue(objectToClean, field.getName(), 0);
                    } else if (field.getType().equals(boolean.class)) {
                        setFieldValue(objectToClean, field.getName(), false);
                    } else if (field.getType().equals(char.class)) {
                        setFieldValue(objectToClean, field.getName(), '\u0000');
                    }
                } else {
                    setFieldToNull(objectToClean, field.getName());
                }
            }
        }
        return objectToClean;
    }

    public static boolean isNumericField(Field field) {
        return field != null &&
                (field.getType().equals(Short.class) ||
                        field.getType().equals(Integer.class) ||
                        field.getType().equals(Long.class) ||
                        field.getType().equals(Float.class) ||
                        field.getType().equals(Double.class) ||
                        field.getType().equals(BigInteger.class) ||
                        field.getType().equals(BigDecimal.class) ||
                        field.getType().equals(short.class) ||
                        field.getType().equals(int.class) ||
                        field.getType().equals(long.class) ||
                        field.getType().equals(float.class) ||
                        field.getType().equals(double.class)
                );
    }

    public List<Object> getObjectFieldValues(Object object) throws NoSuchFieldException, IllegalAccessException {
        List<Object> result = new ArrayList<>();

        Field[] fields = getClassFields(object.getClass());
        for (Field field : fields) {
            result.add(object.getClass().getDeclaredField(field.getName()).get(object));
        }

        return result;
    }

    public static Class<?> findValueTypeForNonEmptyList(List<?> list) {
        if(list != null && !list.isEmpty() && !list.stream().allMatch(Objects::nonNull)) {
            return list.stream().filter(Objects::nonNull).findFirst().get().getClass();
        } else {
            return null;
        }
    }

    public static <T> Class<?> findValueTypeForNonEmptyArray(T[] list) {
        if(list != null && list.length > 0 && !Arrays.stream(list).allMatch(Objects::nonNull)) {
            return Arrays.stream(list).filter(Objects::nonNull).findFirst().get().getClass();
        } else {
            return null;
        }
    }
}
