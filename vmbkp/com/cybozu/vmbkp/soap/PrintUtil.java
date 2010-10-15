/**
 * @file
 * @brief PrintUtil
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.soap;

import com.vmware.vim25.mo.ManagedEntity;

/**
 * @brief Print Utility class for debug etc.
 */
public class PrintUtil
{
    /**
     * Print all contents of a specified list as Iterable<ManagedEntity> type.
     *
     * @param list specified any list of ManagedEntity.
     */
    protected static void printListM(Iterable<ManagedEntity> list)
    {
        if (list == null) {
            System.out.println("The specified list is null.");
            return;
        }
        for (ManagedEntity m : list) {
            System.out.println(m.getName());
        }
    }
}
