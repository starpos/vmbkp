/**
 * @file
 * @brief CleanInfo
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.control;

import com.cybozu.vmbkp.config.FormatInt;

/**
 * @brief Information for clean command.
 */
public class CleanInfo
{
    /**
     * True when all generations must be deleted, not only failed ones.
     */
    public boolean isAll;
    /**
     * True when the archives must be deleted
     * even if the vm corresponding of the archive is still alive in the vSphere.
     * This is meaningful with isAll is True.
     */
    public boolean isForce;
    /**
     * True when you do not want to delete archives really.
     */
    public boolean isDryRun;

    /**
     * Constructor.
     */
    public CleanInfo(VmbkpCommandLine cmdLine)
        throws Exception
    {
        isAll = false;
        isForce = false;
        isDryRun = false;

        if (cmdLine.isOption("--all")) {
            isAll = true;
        }
        if (cmdLine.isOption("--force")) {
            isForce = true;
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
        sb.append("CleanInfo: ");
        sb.append(String.format("[isAll %s]", Boolean.toString(isAll)));
        sb.append(String.format("[isForce %s]", Boolean.toString(isForce)));
        sb.append(String.format("[isDryRun %s]", Boolean.toString(isDryRun)));
        
        return sb.toString();
    }
}
