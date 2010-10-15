/**
 * @file
 * @brief Test VmdkBkp.lock() and unlock() command.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.itest;

import com.cybozu.vmbkp.control.VmdkBkp;
import com.cybozu.vmbkp.profile.ConfigGlobal;

public class TestLock
{
    public static void main(String[] args) {

        try {
            ConfigGlobal cfgGlobal = new ConfigGlobal(args[0]);
            VmdkBkp.lock(cfgGlobal);
            System.out.println("locked");
            Thread.sleep(2000);
            VmdkBkp.unlock();
            System.out.println("unlocked");
        } catch (Exception e) {}
    }
}
