package com.golems.util;

import java.lang.reflect.Method;

class ReflectionSimilarClassToClassMethod {
    Method methodObjectFromGetter;
    Method methodObjectToSetter;

    protected ReflectionSimilarClassToClassMethod(Method methodObjectFromGetter, Method methodObjectToSetter) {
        this.methodObjectFromGetter = methodObjectFromGetter;
        this.methodObjectToSetter = methodObjectToSetter;
    }

    protected Method getMethodObjectFromGetter() {
        return methodObjectFromGetter;
    }

    protected void setMethodObjectFromGetter(Method methodObjectFromGetter) {
        this.methodObjectFromGetter = methodObjectFromGetter;
    }

    protected Method getMethodObjectToSetter() {
        return methodObjectToSetter;
    }

    protected void setMethodObjectToSetter(Method methodObjectToSetter) {
        this.methodObjectToSetter = methodObjectToSetter;
    }
}

