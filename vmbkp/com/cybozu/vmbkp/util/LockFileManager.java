/**
 * @file
 * @brief LockFileManager
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

/**
 * @brief Interface of file lock manager.
 *
 * Usage:
 * <pre>
 * try {
 *     lock();
 *
 *     do something.
 * 
 * } finally {
 *     unlock();
 * }
 * </pre>
 */
public interface LockFileManager
{
    /**
     * Lock the resource.
     *
     * @param timeoutSec Lock timeout.
     */
    public void lock(int timeoutSec)
        throws LockTimeoutException, Exception;

    /**
     * Unlock the resource.
     */
    public void unlock();
}
