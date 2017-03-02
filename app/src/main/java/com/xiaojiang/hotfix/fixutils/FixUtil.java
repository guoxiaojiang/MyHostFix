package com.xiaojiang.hotfix.fixutils;

import android.content.Context;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashSet;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

/**
 * Created by guoxiaojiang on 17/3/2.
 */

public class FixUtil {

    /**
     * 获取类的字段
     * @param obj
     * @param cls
     * @param str
     * @return
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private static Object getField(Object obj, Class<?> cls, String str)
            throws NoSuchFieldException, IllegalAccessException {
        Field declaredField = cls.getDeclaredField(str);
        declaredField.setAccessible(true);
        return declaredField.get(obj);
    }

    /**
     * 设置类的字段
     * @param obj
     * @param cls
     * @param str
     * @param value
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private static void setField(Object obj, Class<?> cls, String str, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        Field declaredField = cls.getDeclaredField(str);
        declaredField.setAccessible(true);
        declaredField.set(obj, value);
    }

    private static Object getPathList(Object obj) throws ClassNotFoundException, NoSuchFieldException,
            IllegalAccessException {
        return getField(obj, Class.forName("dalvik.system.BaseDexClassLoader"), "pathList");
    }

    private static Object getDexElements(Object obj) throws NoSuchFieldException, IllegalAccessException {
        return getField(obj, obj.getClass(), "dexElements");
    }

    /**
     * 数组拼接
     * @param obj
     * @param obj2
     * @return
     */
    private static Object combineArray(Object obj, Object obj2) {
        Class componentType = obj2.getClass().getComponentType();
        int length = Array.getLength(obj2);
        int length2 = Array.getLength(obj) + length;
        Object newInstance = Array.newInstance(componentType, length2);
        for (int i = 0; i < length2; i++) {
            if (i < length) {
                Array.set(newInstance, i, Array.get(obj2, i));
            } else {
                Array.set(newInstance, i, Array.get(obj, i - length));
            }
        }
        return newInstance;
    }

    /**
     * 注入dex
     * @param context
     * @param fileDir
     * @param loadedDexs 要注入的dex（可有多个）
     */
    public static void doDexInject(Context context, File fileDir, HashSet<File> loadedDexs) {
        //inject
        // 思路：先用一个类加载器（BaseDexClassLoader）来加载我们自己的补丁(dex)
        // 然后拿到系统的类加载器，获取系统的pathList中的Element集合
        // 接下来用我们的集合和系统的集合进行拼接
        // 注：我们的集合要放到前面，这样才能使用我们的class，而不用原来有问题的class
        // https://yq.aliyun.com/articles/70320
        /*
         *见源码：DexPathList中：
            public Class findClass(String name, List<Throwable> suppressed) {
                for (Element element : dexElements) {
                    DexFile dex = element.dexFile;

                    if (dex != null) {
                        Class clazz = dex.loadClassBinaryName(name, definingContext, suppressed);
                        if (clazz != null) {
                            return clazz;
                        }
                    }
                }
                if (dexElementsSuppressedExceptions != null) {
                    suppressed.addAll(Arrays.asList(dexElementsSuppressedExceptions));
                }
                return null;
            }
        * */

        //用来解压dex的临时目录
        String optimizedDir = fileDir.getAbsolutePath() + File.separator + "opt_dex";
        File fopt = new File(optimizedDir);
        if (! fopt.exists()) {
            fopt.mkdirs();
        }
        for (File dex: loadedDexs) {
            DexClassLoader classLoader = new DexClassLoader(dex.getAbsolutePath(), optimizedDir, null, context.getClassLoader());
            inject(classLoader, context);
        }
    }

    private static void inject(DexClassLoader classLoader, Context context) {
        PathClassLoader pathLoader = (PathClassLoader) context.getClassLoader();
        try {
            Object dexElements = combineArray(getDexElements(getPathList(classLoader)), getDexElements(getPathList(pathLoader)));
            Object pathList = getPathList(pathLoader);
            setField(pathList, pathList.getClass(), "dexElements", dexElements);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
