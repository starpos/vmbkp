/**
 * @file
 * @brief LockFileManagerM
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import java.io.File;

/**
 * @brief Manage file locking using File.mkdir().
 */
public class LockFileManagerM
    implements LockFileManager
{
    String lockPath_;

    /**
     * Constructor.
     *
     * @param lockPath Lock file path.
     */
    public LockFileManagerM(String lockPath)
    {
        assert lockPath != null;
        lockPath_ = lockPath;
    }

    /**
     * Lock.
     *
     * @param timeoutSec Lock timeout.
     */
    public void lock(int timeoutSec)
        throws LockTimeoutException, Exception
    {
        assert lockPath_ != null;
        File lockDir = new File(lockPath_);

        boolean isLocked = false;
        final int sleepMs = 100;
        int elapsedMs = 0;

        while (elapsedMs < timeoutSec * 1000) {

            if (lockDir.mkdir()) {
                isLocked = true;
                break;
            }
            
            Thread.sleep(sleepMs);
            elapsedMs += sleepMs;
        }
        
        if (isLocked == false) {
            throw new LockTimeoutException();
        }
    }

    /**
     * Unlock.
     */
    public void unlock()
    {
        assert lockPath_ != null;
        File lockDir = new File(lockPath_);

        if (lockDir.exists() && lockDir.isDirectory() == false) {
            System.err.printf("unlock2: %s is not directory.\n", lockPath_);
            return;
        }

        if (lockDir.delete() == false) {
            System.err.printf("unlock2: delete %s failed\n", lockPath_);
        }
    }
}
