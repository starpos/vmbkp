/**
 * @file
 * @brief NotNormalFileException
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.config;

/**
 * @brief Exception when the file is not normal file.
 */
public class NotNormalFileException
    extends Exception
{
    public NotNormalFileException() {
        super();
    }
}
