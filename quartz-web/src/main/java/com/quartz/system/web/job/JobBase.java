package com.quartz.system.web.job;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author zj
 * @date 2018/11/30
 */
public abstract class JobBase {

    public static final String projectName = "quartz02";

    public void run(){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String name = Thread.currentThread().getName();
        long id = Thread.currentThread().getId();
        String threadName = name + "-" + id;
        long start = System.currentTimeMillis();
        System.out.println(threadName + " - " + dateFormat.format(new Date()) + " -- " + this.getClass().getSimpleName() + " - " + projectName + " -"  + " start");
        System.out.println(threadName + " - " + dateFormat.format(new Date())  + " -- " + this.getClass().getSimpleName()  + " - " + projectName + " -"  +  "  >> 定时任务正在执行");
        long end = System.currentTimeMillis();
        System.out.println(threadName + " - " + dateFormat.format(new Date()) + " -- " + this.getClass().getSimpleName()  + " - " + projectName + " -"  +  " end cost " + (end - start) + " ms");
    }
}
