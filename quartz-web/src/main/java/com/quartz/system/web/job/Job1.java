package com.quartz.system.web.job;

import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author zj
 * @date 2018/11/30
 */
@Component("job1")
public class Job1 extends JobBase {

    @Override
    public void run() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS");
        String name = Thread.currentThread().getName();
        long id = Thread.currentThread().getId();
        String threadName = name + "-" + id;
        long start = System.currentTimeMillis();
        System.out.println(threadName + " - " + dateFormat.format(new Date()) + " -- " + this.getClass().getSimpleName() + " - " + projectName + " -"  + " start");
//        try {
//            Thread.sleep(1000 * 3);
//        } catch (Exception  e) {
//
//        }
        System.out.println(threadName + " - " + dateFormat.format(new Date())  + " -- " + this.getClass().getSimpleName()  + " - " + projectName + " -"  +  "  >> 定时任务正在执行");
        long end = System.currentTimeMillis();
        System.out.println(threadName + " - " + dateFormat.format(new Date()) + " -- " + this.getClass().getSimpleName()  + " - " + projectName + " -"  +  " end cost " + (end - start) + " ms");
    }
}
