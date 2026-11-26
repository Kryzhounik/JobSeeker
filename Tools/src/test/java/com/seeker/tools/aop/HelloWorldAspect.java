package com.seeker.tools.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class HelloWorldAspect {
    @Before("execution(@com.seeker.tools.aop.HelloWorld * *(..))")
    public void printHelloWorld() {
        System.out.println("Hello world");
    }
}
