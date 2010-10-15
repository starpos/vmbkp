/**
 * @file
 * @brief RestoreInfo
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.control;

import com.cybozu.vmbkp.config.FormatInt;

/**
 * @brief Information for restore command.
 */
public class RestoreInfo
{
    public String newVmName;
    public int generationId;
    public String hostName;
    public String datastoreName;
    public boolean isNoVmdk;
    public boolean isDryRun;
    public boolean isSan;
    
    /**
     * Constructor.
     * This create required informatino for restore
     * from command line data.
     */
    public RestoreInfo(VmbkpCommandLine cmdLine)
        throws Exception
    {
        newVmName = null;
        generationId = -1;
        hostName = null;
        datastoreName = null;
        isNoVmdk = false;
        isDryRun = false;
        isSan = false;

        if (cmdLine.isOption("--name")) {
            newVmName = cmdLine.getOptionArgs("--name").get(0);
        } else {
            String errStr = "Please specify --name option for new VM name.";
            System.out.println(errStr);
            throw new Exception(errStr);
        }

        String generationIdStr = null;
        if (cmdLine.isOption("--generation")) {
            generationIdStr = cmdLine.getOptionArgs("--generation").get(0);
        }
        /* Set generationId */
        if (generationIdStr != null &&
            FormatInt.isInteger(generationIdStr) &&
            FormatInt.canBeInt(generationIdStr)) {

            generationId = FormatInt.toInt(generationIdStr);
        }
        
        if (cmdLine.isOption("--host")) {
            hostName = cmdLine.getOptionArgs("--host").get(0);
        }
        
        if (cmdLine.isOption("--datastore")) {
            datastoreName = cmdLine.getOptionArgs("--datastore").get(0);
        }

        if (cmdLine.isOption("--novmdk")) {
            isNoVmdk = true;
        }
        if (cmdLine.isOption("--dryrun")) {
            isDryRun = true;
        }
        if (cmdLine.isOption("--san")) {
            isSan = true;
        }
        if (cmdLine.isOption("--nbd")) {
            isSan = false;
        }
    }

    /**
     * toString()
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        sb.append("RestoreInfo: ");

        sb.append(String.format("[newVmName %s]", newVmName));
        sb.append
            (String.format("[generation %s]", Integer.toString(generationId)));
        sb.append(String.format("[host %s]", hostName));
        sb.append(String.format("[datastore %s]", datastoreName));
        
        sb.append(String.format("[isNoVmdk %s]", Boolean.toString(isNoVmdk)));
        sb.append(String.format("[isDryRun %s]", Boolean.toString(isDryRun)));
        sb.append(String.format("[isSan %s]", Boolean.toString(isSan)));
        
        return sb.toString();
    }
    
}
