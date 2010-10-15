/**
 * @file
 * @brief ConfigWrapper
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.profile;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.cybozu.vmbkp.config.ConfigLikeGit;
import com.cybozu.vmbkp.config.Group;
import com.cybozu.vmbkp.config.FileNotSetException;
import com.cybozu.vmbkp.config.ParseException;
import com.cybozu.vmbkp.config.NotNormalFileException;

import com.cybozu.vmbkp.util.LockFileManager;
import com.cybozu.vmbkp.util.LockFileManagerN;


/**
 * @brief Wrapper class to access vmbkp_*.conf file.
 */
abstract public class ConfigWrapper
{
    /**
     * Config data
     */
    protected ConfigLikeGit cfg_;

    /**
     * Config file name. Can be null.
     */
    protected String configFilePath_;

    /**
     * Lock file manager.
     */
    private LockFileManager lockM_;

    /**
     * Constructor.
     *
     * @param filePath Path to a vmbkp_*.{conf|profile} file.
     */
    public ConfigWrapper(String filePath)
        throws IOException, ParseException, NotNormalFileException
    {
        assert filePath != null;
        lockM_ = null;
        
        configFilePath_ = filePath;
        cfg_ = new ConfigLikeGit();
        if (configFilePath_ != null) {
            read(configFilePath_);
        }
    }

    /**
     * Constructor.
     * This does not set config file name.
     *
     * @param cfg config object.
     */
    public ConfigWrapper(ConfigLikeGit cfg)
    {
        assert cfg != null;
        lockM_ = null;
        
        configFilePath_ = null;
        if (cfg != null) {
            cfg_ = cfg;
        } else {
            cfg_ = new ConfigLikeGit();
        }
    }

    /**
     * Default constructor.
     * This creates empty config data without config file name.
     */
    public ConfigWrapper()
    {
        configFilePath_ = null;
        cfg_ = new ConfigLikeGit();
        lockM_ = null;
    }

    /**
     * Get directory that config file exists.
     *
     * @return directory path string if config file is set, or null.
     */
    public String getDirectory()
    {
        if (configFilePath_ != null) {
            File configFile = new File(configFilePath_);
            return configFile.getParent();
        } else {
            return null;
        }
    }

    /**
     * Get directory that config file exists.
     *
     * @return directory file object if config file is set, or null.
     */
    public File getDirectoryFile()
    {
        String dir = getDirectory();
        if (dir != null) {
            return new File(dir);
        } else {
            return null;
        }
    }

    /**
     * Read from a config file.
     * The object will be initialized with the new config file.
     *
     * @param filePath File name to read contents from.
     */
    public void read(String filePath)
        throws IOException, ParseException, NotNormalFileException
    {
        cfg_.clear();
        configFilePath_ = filePath;
        cfg_.read(configFilePath_);
    }

    /**
     * Reload the config file.
     */
    public void reload()
        throws IOException, ParseException, NotNormalFileException
    {
        if (configFilePath_ == null) { return; }
        read(configFilePath_);
    }

    /**
     * Write to the config file.
     * Do not call this before calling constructor or read
     * with a file path, otherwise, call write(filePath).
     */
    public void write()
        throws IOException, FileNotSetException
    {
        if (configFilePath_ == null) {
            throw new FileNotSetException();
        }
        cfg_.write(configFilePath_);
    }

    /**
     * Write to the specified config file.
     *
     * @param filePath File name to write contents to.
     */
    public void write(String filePath)
        throws IOException, FileNotSetException
    {
        configFilePath_ = filePath;
        write();
    }

    /**
     * Lock threads/processes to access the config file.
     * If another thread/process already lock this,
     * current thread/process will wait for its unlock.
     *
     * @param timeoutSec Lock timeout.
     */
    public synchronized void lock(int timeoutSec)
        throws Exception
    {
        if (configFilePath_ == null) { 
            throw new Exception("configFilePath_ is null.");
        }

        if (lockM_ == null) {
            lockM_ = new LockFileManagerN(configFilePath_ + ".lock");
        }

        lockM_.lock(timeoutSec);
    }

    /**
     * Unlock threads/processes to access the config file.
     *
     */
    public synchronized void unlock()
    {
        assert lockM_ != null;
        lockM_.unlock();
    }

    /**
     * Backup config file.
     *
     * @return True in succeess, false in failure.
     */
    public boolean makeBackup()
    {
        if (configFilePath_ == null) { return false; }
        
        File file = new File(configFilePath_);
        if (file.isFile() && file.canRead()) {
            try {
                String backupFilePath = configFilePath_ + ".bak";
                FileChannel src, dst;
                src = (new FileInputStream(configFilePath_)).getChannel();
                dst = (new FileOutputStream(backupFilePath)).getChannel();

                ByteBuffer buf = ByteBuffer.allocateDirect(4096);

                while (src.read(buf) > 0) {
                    buf.flip(); dst.write(buf); buf.clear();
                }
                src.close(); dst.close();
            } catch (Exception e) { return false; }
        } else { return false; }

        return true;
    }
}
