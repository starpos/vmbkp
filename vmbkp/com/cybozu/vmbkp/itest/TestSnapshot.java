/**
 * @file
 * @brief TestSnapshot
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.itest;

import com.cybozu.vmbkp.soap.*;

public class TestSnapshot
{
    /**
     * Specify the following arguments in command-line.
     * 1. server, 2. username, 3. password,
     * 4. vm name, 5. snapshot name.
     */
    public static void main(String[] args)
    {
        String server = args[0];
        String username = args[1];
        String password = args[2];
        String vmName = args[3];
        String snapName = args[4];

        String url = String.format("https://%s/sdk/", server);
        
        Connection conn = new Connection(url, username, password);

        GlobalManager gm = new GlobalManager(conn);
        
        VirtualMachineManager vmm = null;
        try {
            vmm = gm.searchVmWithName(vmName);

        } catch (Exception e) { e.printStackTrace(); return; }

        boolean ret;
        ret = vmm.createSnapshot(snapName);
        if (ret == false) { return; }

        try {
            gm.disconnect();
            Thread.sleep(3000);
            gm.connect();
            vmm = gm.searchVmWithName(vmName); /* required!!! */
            
        } catch (Exception e) { e.printStackTrace(); return; }

        ret = vmm.deleteSnapshot(snapName);
        if (ret == false) { return; }

        gm.disconnect();
    }
}
