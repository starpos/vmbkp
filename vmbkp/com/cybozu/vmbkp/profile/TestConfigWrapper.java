/**
 * @file
 * @brief TestConfigWrapper
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.profile;

import java.util.*;
import java.io.*;

import com.cybozu.vmbkp.util.*;
import com.cybozu.vmbkp.config.*;



class TestConfigWrapperThread
    extends Thread
{
    TestConfigWrapper cfgW_;
    Group group_;
    Random rand_;
    

    public TestConfigWrapperThread(TestConfigWrapper cfgW, String name)
    {
        assert cfgW != null;
        cfgW_ = cfgW;
        this.setName(name);

        group_ = new Group("group");
        rand_ = new Random();
    }

    public void run()
    {
        System.out.println("Thread start: " + this.getName());
        
        for (int i = 0; i < 5; i ++) {

            try {
                int counter = -1;
                //Thread.sleep(rand(100));

                cfgW_.lock(10);
                cfgW_.reload();
                
                counter = cfgW_.get(this.getName());
                System.out.printf("%s 1 %d\n",
                                  this.getName(), counter);
                
                //Thread.sleep(rand(100));
                cfgW_.inc(this.getName());

                //Thread.sleep(rand(100));
                counter = cfgW_.get(this.getName());
                System.out.printf("%s 2 %d\n",
                                  this.getName(), counter);
                
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                cfgW_.unlock();
            }
        }

        System.out.println("Thread end: " + this.getName());
    }

    private int rand(int max)
    {
        int ret = rand_.nextInt();
        if (ret < 0) { ret = 0 - ret; }
        return ret % max;
    }
}


/**
 * @brief Test of ConfigWrapper, especialy lock() and unlock().
 */
public class TestConfigWrapper
    extends ConfigWrapper
{
    public static final String FILE_NAME = "testconfigwrapper.conf";
    private static Group group_;

    /**
     * Constructor.
     */
    public TestConfigWrapper(String fileName)
        throws Exception
    {
        super(fileName);
        group_ = new Group("group");
    }

    public TestConfigWrapper()
    {
        super();
        group_ = new Group("group");
    }

    public void initialize()
    {
        cfg_.put(group_, "counter", "0");
    }

    public void inc(String name)
        throws Exception
    {
        String counterStr = cfg_.getVal(group_, "counter");
        if (counterStr == null ||
            FormatInt.canBeInt(counterStr) == false) {

            System.err.printf("%s counterStr error.\n", name);

            BufferedWriter bw =
                new BufferedWriter(new OutputStreamWriter(System.out));
            bw.write("----------\n");
            cfg_.write(bw);
            bw.write("----------\n");
            bw.close();
            return;
        }
        int counter = FormatInt.toInt(counterStr);
        assert counter >= 0;
        counter ++;
        cfg_.put(group_, "counter", Integer.toString(counter));
        cfg_.write();
    }

    public int get(String name)
    {
        int ret = -1;
        ret = cfg_.getValAsInt(group_, "counter");
        return ret;
    }
    
    public static void main2(String[] args)
    {
        try {
            /* Initialize test config file. */
            TestConfigWrapper cfgW =
                new TestConfigWrapper();
            cfgW.initialize();
            cfgW.write(FILE_NAME);

            /* Make n threads */
            int n = 3;
            List<Thread> threads = new LinkedList<Thread>();
            for (int i = 0; i < n; i ++) {
                String name = String.format("T%d", i);
                Thread th = 
                    new TestConfigWrapperThread
                    (new TestConfigWrapper(FILE_NAME), name);
                th.start();
                threads.add(th);
            }

         
            for (Thread th: threads) {
                th.join();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    public static void main(String[] args)
    {
        try {
            if (args.length == 0) {
                /* Initialize test config file. */
                TestConfigWrapper cfgW =
                    new TestConfigWrapper();
                cfgW.initialize();
                cfgW.write(FILE_NAME);
            } else {
                /* Make a thread */
                String name = String.format("T%s", args[0]);
                Thread th = 
                    new TestConfigWrapperThread
                    (new TestConfigWrapper(FILE_NAME), name);
                th.start();
                th.join();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
