/**
 * @file
 * @brief StatusInfo
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.control;

/**
 * @brief Information for status command.
 */
public class StatusInfo
{
    public boolean isDetail;

    public StatusInfo(VmbkpCommandLine cmdLine)
    {
        if (cmdLine.isOption("--detail")) {
            isDetail = true;
        } else {
            isDetail = false;
        }
    }
}
