/**
 * @file
 * @brief LockTimeoutException
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

/**
 * @brief Exception for lock timeout.
 */
public class LockTimeoutException
    extends Exception
{
    public LockTimeoutException() {
        super();
    }
}
