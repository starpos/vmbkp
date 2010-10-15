/**
 * @file
 * @brief VmbkpLog
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.control;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.util.logging.LogManager;

/**
 * @brief Log initialization class.
 */
class VmbkpLog
{
    /**
     * Load log setting from the specified inputstream.
     */
    public static void loadLogSetting(InputStream in)
        throws IOException
    {
        LogManager.getLogManager().readConfiguration(in);
    }

    /**
     * Load log setting from the specified file path.
     */
    public static void loadLogSetting(String filePath)
        throws FileNotFoundException, IOException
    {
        InputStream in = new FileInputStream(filePath);
        loadLogSetting(in);
        in.close();
    }

    /**
     * Load log setting from the default resource.
     */
    public static void loadLogSetting()
        throws IOException
    {
        String path = "/com/cybozu/vmbkp/log.properties";
        InputStream in = VmbkpLog.class.getResourceAsStream(path);
        loadLogSetting(in);
        in.close();
    }
}
