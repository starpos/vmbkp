/**
 * @file
 * @brief BackupInfo
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.control;

import com.cybozu.vmbkp.config.FormatInt;
import com.cybozu.vmbkp.util.BackupMode;

/**
 * @brief Information for backup command.
 */
public class BackupInfo
{
    public boolean isNoVmdk;
    public boolean isDryRun;
    public boolean isGzip;
    public boolean isSan;
    public BackupMode mode;

    /**
     * Constructor.
     * This create required informatino for backup
     * from command line data.
     */
    public BackupInfo(VmbkpCommandLine cmdLine)
        throws Exception
    {
        isNoVmdk = false;
        isDryRun = false;
        isGzip = false;
        isSan = true;
        mode = BackupMode.UNKNOWN;

        if (cmdLine.isOption("--novmdk")) {
            isNoVmdk = true;
        }
        if (cmdLine.isOption("--dryrun")) {
            isDryRun = true;
        }
        if (cmdLine.isOption("--gzip")) {
            isGzip = true;
        }
        if (cmdLine.isOption("--san")) {
            isSan = true;
        }
        if (cmdLine.isOption("--nbd")) {
            isSan = false;
        }
        if (cmdLine.isOption("--mode")) {
           mode = BackupMode.parse(cmdLine.getOptionArgs("--mode").get(0));
        }
    }

    /**
     * toString()
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        sb.append("BackupInfo: ");
        sb.append(String.format("[isNoVmdk %s]", Boolean.toString(isNoVmdk)));
        sb.append(String.format("[isDryRun %s]", Boolean.toString(isDryRun)));
        sb.append(String.format("[isGzip %s]", Boolean.toString(isGzip)));
        sb.append(String.format("[isSan %s]", Boolean.toString(isSan)));
        
        return sb.toString();
    }
}
