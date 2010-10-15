/**
 * @file
 * @brief TestAddEmptyDisks
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.itest;

import java.util.*;

import com.cybozu.vmbkp.util.*;
import com.cybozu.vmbkp.soap.*;

/**
 * @brief Test of adding empty disks to a virtual machine.
 */
public class TestAddEmptyDisks
{
    public static void main()
    {
        String vmName = "virtual machine name";
        String datastoreName = "datastore name";
        String url = "url";
        String username = "username";
        String password = "password";

        Connection conn = new Connection(url, username, password);
        GlobalManager gm = new GlobalManager(conn);
        testAddDisks(gm, vmName, datastoreName);
        conn.disconnect();
    }
    
    /**
     * Test of add disks to newly created virutal machine.
     */
    public static void testAddDisks
        (GlobalManager gm, String vmName, String datastoreName)
    {
        /* target virtual machine */
        VirtualMachineManager vmm;
        try {
            vmm = gm.searchVmWithName(vmName);
        } catch (Exception e) { e.printStackTrace(); return; }

        List<VirtualControllerManager> list =
            new LinkedList<VirtualControllerManager>();
        
        VirtualControllerManager vctrlm0 = 
            new VirtualControllerManager(AdapterType.LSILOGIC, 1000, 0);
        VirtualDiskManager vdiskm0 =
            new VirtualDiskManager(0, 0, 64L * 1024L, datastoreName);
        vctrlm0.add(vdiskm0);
        VirtualDiskManager vdiskm1 =
            new VirtualDiskManager(0, 1, 128L * 1024L, datastoreName);
        vctrlm0.add(vdiskm1);
        VirtualDiskManager vdiskm2 =
            new VirtualDiskManager(0, 2, 256L * 1024L, datastoreName);
        vctrlm0.add(vdiskm2);
        list.add(vctrlm0);

        vctrlm0 = 
            new VirtualControllerManager(AdapterType.BUSLOGIC, 1002, 2);
        vdiskm0 =
            new VirtualDiskManager(0, 0, 4L * 1024L * 1024L, datastoreName);
        vctrlm0.add(vdiskm0);
        list.add(vctrlm0);

        vctrlm0 = 
            new VirtualControllerManager(AdapterType.LSILOGICSAS, 1001, 1);
        vdiskm0 =
            new VirtualDiskManager(0, 0, 32L * 1024L, datastoreName);
        vctrlm0.add(vdiskm0);
        list.add(vctrlm0);

        vctrlm0 = 
            new VirtualControllerManager(AdapterType.IDE, 200, 0);
        vdiskm0 =
            new VirtualDiskManager(0, 0, 1024L * 1024L, datastoreName);
        vctrlm0.add(vdiskm0);
        vdiskm0 =
            new VirtualDiskManager(0, 1, 2L * 1024L * 1024L, datastoreName);
        vctrlm0.add(vdiskm0);
        list.add(vctrlm0);
        
        boolean ret = vmm.addEmptyDisks(list);
        System.out.println("testAddDisks()" + ret);
    }
}
