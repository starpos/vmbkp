/**
 * @file
 * @brief ConfigGroup
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.profile;

import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import com.cybozu.vmbkp.config.Entry;
import com.cybozu.vmbkp.config.Group;

import com.cybozu.vmbkp.profile.ConfigWrapper;
import com.cybozu.vmbkp.profile.ProfileAllVm;

/**
 * @brief Wrapper class to access vmbkp_group.conf
 */
public class ConfigGroup
    extends ConfigWrapper
{
    /**
     * Default file name.
     */
    public static final String FILE_NAME = "vmbkp_group.conf";

    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(ConfigGroup.class.getName());

    /**
     * Profile of all vm to get moref from vm name.
     */
    ProfileAllVm profAllVm_;

    /**
     * Constructor
     *
     * This file should be read only.
     * Don't call write().
     *
     * @param fileName Path to a vmbkp_group.conf file.
     * @param profAllVm ProfileAllVm object.
     */
    public ConfigGroup(String fileName, ProfileAllVm profAllVm)
        throws Exception
    {
        super(fileName);
        assert profAllVm != null;
        profAllVm_ = profAllVm;
    }

    /**
     * Make a group [group "`groupName`"].
     */
    private Group makeGroup(String groupName)
    {
        return new Group("group", groupName);
    }

    /**
     * Get moref of all virtual machines in [group "`groupName`"].
     * If a entry indicates a group, it searches recursively.
     *
     * @param groupName group name.
     * @return never return null.
     */
    public List<String> getAllVmMorefListOfGroup(String groupName)
    {
        /* We use Set class because it eliminates
           duplication automatically. */
        Set<String> sset = new TreeSet<String>();
        
        Group grp = makeGroup(groupName);
        List<Entry> entryList = cfg_.getAllEntries(grp);

        for (Entry entry : entryList) {
            String key = entry.getKey();
            String val = entry.getVal();
            
            if (val.equals("moref")) {
                sset.add(key);
                
            } else if (val.equals("name")) {
                String moref = profAllVm_.getVmMorefWithName(key);
                if (moref != null) {
                    sset.add(moref);
                }
                    
            } else if (val.equals("group")) {
                /* recursive call */
                sset.addAll(getAllVmMorefListOfGroup(key));
                
            } else {
                /* error */
                logger_.warning
                    (String.format
                     ("Val %s is not supported for key %s.\n" +
                      "Please specify moref or name or group.",
                      val, key));
            }
        }

        /* Set -> List */
        List<String> ret = new LinkedList<String>();
        ret.addAll(sset);
        return ret;
    }

    /**
     * Check the name is group name or not.
     *
     * @param name name of a group.
     * @return true when the name is defined as a group, or false.
     */
    public boolean isGroupName(String name)
    {
        Group grp = makeGroup(name);
        List<Entry> entryList = cfg_.getAllEntries(grp);
        /* When the name is defined but the group is empty,
           the group name is invalid for us. */

        return entryList.isEmpty() == false;
    }

    /**
     * Get moref of all virtual machines
     * specified by the name.
     *
     * @param name group name or vm name or vm moref, or 'all'.
     * @return moref list, never return null.
     */
    public List<String> getAllVmMorefList(String name)
    {
        assert name != null;
        List<String> vmList = null;

        if (name.equals("all")) {
            vmList = profAllVm_.getAllVmMorefs();
            
        } else if (isGroupName(name)) {
            vmList = getAllVmMorefListOfGroup(name);
            
        } else {
            vmList = new LinkedList<String>();
            
            /* check the name is vm name */
            String vmMoref = profAllVm_.getVmMorefWithName(name);
            if (vmMoref != null) {
                vmList.add(vmMoref);
            } else {
                /* check the name is vm moref */
                String vmName = profAllVm_.getVmNameWithMoref(name);
                if (vmName != null) {
                    vmList.add(name);
                }
            }
        }
        assert vmList != null;
        return vmList;
    }

    /**
     * Get moref of all available virtual machines
     * specified by the name.
     *
     * @param name group name or vm name or vm moref, or 'all'.
     * @return never return null.
     */
    public List<String> getAllAvailableVmMorefList(String name)
    {
        return profAllVm_.filterAvailable(getAllVmMorefList(name));
    }
}
