/**
 * @file
 * @brief CheckInfo
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.control;

import com.cybozu.vmbkp.config.FormatInt;

/**
 * @brief Information for check command.
 */
public class CheckInfo
{
    public int generationId;
    public boolean isDryRun;

    /**
     * Constructor.
     */
    public CheckInfo(VmbkpCommandLine cmdLine)
        throws Exception
    {
        generationId = -1;

        String generationIdStr = null;
        if (cmdLine.isOption("--generation")) {
            generationIdStr = cmdLine.getOptionArgs("--generation").get(0);
        }
        if (generationIdStr != null &&
            FormatInt.canBeInt(generationIdStr)) {
            generationId = FormatInt.toInt(generationIdStr);
        }
        
        if (cmdLine.isOption("--dryrun")) {
            isDryRun = true;
        }
    }

    /**
     * toString()
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        sb.append("CheckInfo: ");

        sb.append
            (String.format("[generation %s]", Integer.toString(generationId)));
        sb.append(String.format("[isDryRun %s]", Boolean.toString(isDryRun)));
        
        return sb.toString();
    }
    
}
