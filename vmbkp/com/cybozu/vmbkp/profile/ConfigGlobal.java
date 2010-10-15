/**
 * @file
 * @brief ConfigGlobal
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.profile;

import java.util.logging.Logger;
import java.io.File;

import com.cybozu.vmbkp.config.Group;
import com.cybozu.vmbkp.config.FormatInt;

import com.cybozu.vmbkp.profile.ConfigWrapper;

/**
 * @brief Wrapper class to access vmbkp_global.conf
 */
public class ConfigGlobal
    extends ConfigWrapper
{
    public static final String FILE_NAME = "vmbkp_global.conf";

    public static final String G_GLOBAL = "global";
    public static final String G_VSPHERE = "vsphere";
    
    public static final String ROOT_DIRECTORY = "root_directory";
    public static final String VMDKBKP_PATH = "vmdkbkp_path";
    public static final String
        PROFILE_ALL_VM_FILE_NAME = "profile_all_vm_file_name";
    public static final String SERVER = "server";
    public static final String KEEP_GENERATIONS = "keep_generations";
    public static final String URL = "url";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(ConfigGlobal.class.getName());
    
    /**
     * A group for global configuration.
     */
    private Group global_;

    /**
     * A group for vSphere configuration.
     */
    private Group vsphere_;
    
    /**
     * Constructor
     *
     * @param filePath Path to a vmbkp_global.conf file.
     */
    public ConfigGlobal(String filePath)
        throws Exception
    {
        super(filePath);
        initializeGroups();
    }

    /**
     * Initialize group(s).
     */
    private void initializeGroups()
    {
        /* initialize groups */
        global_ = new Group(G_GLOBAL);
        vsphere_ = new Group(G_VSPHERE);
    }
   
    /**
     * Get value of [global] root_directory
     * and check the path is valid.
     */
    public String getRootDirectory()
    {
        String rootDirectory = cfg_.getVal(global_, ROOT_DIRECTORY);
        if ((new File(rootDirectory)).isDirectory()) {
            return rootDirectory;
        } else {
            logger_.warning
                (String.format
                 ("Root directory %s not found.",
                  rootDirectory));
            return null;
        }
    }

    /**
     * Get value of [global] vmdkbkp_path
     * and check the path is valid.
     */
    public String getVmdkBkpPath()
    {
        String vmdkBkpPath = cfg_.getVal(global_, VMDKBKP_PATH);
        File vmdkBkp = new File(vmdkBkpPath);
        if (vmdkBkp.isFile() && vmdkBkp.canExecute()) {
            return vmdkBkpPath;
        } else {
            logger_.warning
                (String.format
                 ("%s is not found.", vmdkBkpPath));
            return null;
        }
    }

    /**
     * Get all vm profile path
     * using value of [global] profile_all_vm_file_name.
     * This does not check the file existance.
     */
    public String getProfileAllVmPath()
    {
        String filename = cfg_.getVal(global_, PROFILE_ALL_VM_FILE_NAME);
        if (filename == null) { return null; }
        return getRootDirectory() + "/" + filename;
    }

    /**
     * Get value of [global] keep_generations.
     */
    public int getKeepGenerations()
    {
        /* default number of generations to keep */
        final int defaultVal = 5;
        
        String kgs = cfg_.getVal(global_, KEEP_GENERATIONS);
        if (kgs == null) {
            return defaultVal;
        }

        if (FormatInt.canBeInt(kgs)) {
            return FormatInt.toInt(kgs);
        } else {
            return defaultVal;
        }
    }

    /**
     * Get value of [vsphere] server.
     * @return null when the entry is not found.
     */
    public String getServername()
    {
        return cfg_.getVal(vsphere_, SERVER);
    }
    
    /**
     * Get value of [vsphere] url.
     * @return null when the entry is not found.
     */
    public String getUrl()
    {
        return cfg_.getVal(vsphere_, URL);
    }

    /**
     * Get value of [vsphere] username.
     * @return null when the entry is not found.
     */
    public String getUsername()
    {
        return cfg_.getVal(vsphere_, USERNAME);
    }

    /**
     * Get value of [vsphere] password.
     * @return null when the entry is not found.
     */
    public String getPassword()
    {
        return cfg_.getVal(vsphere_, PASSWORD);
    }

    /**
     * Get default backup directory of the vm
     * with the specified moref.
     *
     * @param moref vm moref. must not be null.
     * @return null when root_directory not found.
     */
    public String getDefaultVmDirectory(String moref)
    {
        assert moref != null;
        String rootDirectory = getRootDirectory();
        if (rootDirectory == null) { return null; }
        return rootDirectory + "/" + moref;
    }

    /**
     * Get default ConfigVm file path of
     * the vm with the specified moref.
     *
     * @param moref vm moref, must not be null.
     * @return default profile vm path,
     *         or null when root_directory not found.
     */
    public String getDefaultProfileVmPath(String moref)
    {
        assert moref != null;
        String backupDirectory = getDefaultVmDirectory(moref);
        if (backupDirectory == null) { return null; }
        return backupDirectory + "/" + ProfileVm.FILE_NAME;
    }
}
