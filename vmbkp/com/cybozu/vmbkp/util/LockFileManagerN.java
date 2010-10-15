/**
 * @file
 * @brief LockFileManagerN
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;
import java.nio.channels.FileLock;
import java.nio.channels.FileChannel;

/**
 * @brief Manage file locking using java.nio.FileLock.
 */
public class LockFileManagerN
    implements LockFileManager
{
    String lockPath_;
    FileLock lock_;
    FileOutputStream lockOut_;

    /**
     * Constructor.
     *
     * @param lockPath Lock file path.
     */
    public LockFileManagerN(String lockPath)
    {
        assert lockPath != null;
        lockPath_ = lockPath;
        lock_ = null;
        lockOut_ = null;
    }

    /**
     * Lock the resource.
     *
     * @param timeoutSec Lock timeout.
     *        If timeoutSec == 0, no wait.
     *        If timeoutSec <  0, block.
     */
    public void lock(int timeoutSec)
        throws LockTimeoutException, Exception
    {
        if (lock_ != null || lockPath_ == null) {
            throw new Exception("lock failed.");
        }
        assert lockOut_ == null;
        
        File lockFile = new File(lockPath_);

        lockOut_ = new FileOutputStream(lockFile);

        FileChannel ch = lockOut_.getChannel();
        assert lock_ == null;

        if (timeoutSec == 0) {
            lock_ = ch.tryLock(); /* no wait */

        } else if (timeoutSec < 0) {
            lock_ = ch.lock(); /* wait */
            
        } else {
            final int sleepMs = 100;
            int elapsedMs = 0;
            while (elapsedMs < timeoutSec * 1000) {
                if ((lock_ = ch.tryLock()) != null) { break; }
                Thread.sleep(sleepMs);
                elapsedMs += sleepMs;
            }
        }

        if (lock_ == null) {
            lockOut_.close();
            lockOut_ = null;
            throw new LockTimeoutException();
        }
    }

    /**
     * Unlock threads/processes to access the config file.
     */
    public void unlock()
    {
        if (lock_ == null || lockPath_ == null) {
            return;
        }
        assert lockOut_ != null;
        
        try {
            lock_.release();
        } catch (IOException e) {
            e.printStackTrace();
            return;
            
        } finally {
            try {
                lockOut_.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            lock_ = null;
            lockOut_ = null;
        }
    }
}
