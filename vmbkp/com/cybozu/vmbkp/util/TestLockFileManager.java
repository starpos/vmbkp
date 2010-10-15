/**
 * @file
 * @brief TestLockFileManager
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import java.util.Random;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;

import com.cybozu.vmbkp.util.LockFileManager;
import com.cybozu.vmbkp.util.LockFileManagerN;
import com.cybozu.vmbkp.util.LockFileManagerM;

/**
 * @brief Test of LockFileManager(s).
 */
public class TestLockFileManager
{
    static Random rand_;

    public static int readIntFromFile(String filePath)
        throws IOException
    {
        InputStreamReader r =
            new InputStreamReader(new FileInputStream(filePath));

        StringBuffer sb = new StringBuffer();
        int c;
        while ((c = r.read()) >= 0) {
            sb.append((char) c);
        }
        r.close();
        String str = sb.toString();
        return Integer.parseInt(str);
    }

    public static void writeIntToFile(String filePath, int i)
        throws IOException
    {
        OutputStreamWriter w =
            new OutputStreamWriter(new FileOutputStream(filePath));

        String str = Integer.toString(i);
        w.write(str, 0, str.length());
        w.close();
    }

    public static void increment
        (String filePath, String name, LockFileManager lockM)
    {
        try {
            lockM.lock(10);

            int counter = readIntFromFile(filePath);
            System.out.printf("T%s %d --> %d\n",
                              name,
                              counter, counter + 1);
            counter ++;
            writeIntToFile(filePath, counter);

        } catch (Exception e) {
            e.printStackTrace();
            
        } finally {
            lockM.unlock();
        }
    }

    public static int rand(int max) {
        int ret = rand_.nextInt();
        if (ret < 0) { ret = 0 - ret; }
        return ret % max;
    }

    public static void runTest
        (String name, String testPath, LockFileManager lockM)
    {
        try {
            for (int i = 0; i < 100; i ++) {
                Thread.sleep(rand(100));
                increment(testPath, name, lockM);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args)
    {
        final String lockPath = "testlock.lock";
        final String testPath = "testlock";
        
        rand_ = new Random();
        
        LockFileManager lockM
            = new LockFileManagerN(lockPath);

        if (args.length == 0) {
            try {
                writeIntToFile(testPath, 0);
            } catch (Exception e) {}

        } else {
            runTest(args[0], testPath, lockM);
        }
    }
}
