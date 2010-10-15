/**
 * @file
 * @brief ProfileAllVm
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.profile;

import java.util.Calendar;
import java.util.List;
import java.util.LinkedList;
import java.io.File;

import com.cybozu.vmbkp.config.Group;
import com.cybozu.vmbkp.config.Entry;
import com.cybozu.vmbkp.config.FormatBool;
import com.cybozu.vmbkp.util.VmInfo;
import com.cybozu.vmbkp.profile.ConfigWrapper;

/**
 * @brief Wrapper class to access vmbkp_all_vm.profile.
 */
public class ProfileAllVm
    extends ConfigWrapper
{
    /**
     * Default file name of the profile.
     */
    public static final String FILE_NAME = "vmbkp_all_vm.profile";
    
    /**
     * Group [vm_set].
     */
    private Group vmSet_;

    /**
     * Group [index "moref_name"].
     */
    private Group vmIndex_;
    
    /**
     * Constructor
     *
     * @param fileName Path to a vmbkp_all_vm.profile file.
     */
    public ProfileAllVm(String fileName)
        throws Exception
    {
        super(fileName);
        initializeGroups();
    }

    /**
     * Default constructor.
     * The filename is not set.
     * Call write(String filePath) later.
     */
    public ProfileAllVm()
    {
        super();
        initializeGroups();
    }

    /**
     * Initialize group(s).
     */
    private void initializeGroups()
    {
        vmSet_ = new Group("vm_set");
        vmIndex_ = new Group("index", "moref_name");
    }

    /**
     * Get [vm_set] *.
     */
    protected List<Entry> getAllVmEntries()
    {
        return cfg_.getAllEntries(vmSet_);
    }

    /**
     * Set [`moref`] availability = false.
     */
    public void setAllAvailabilityToFalse()
    {
        List<Entry> vmEntryList = getAllVmEntries();
        for (Entry vmEntry : vmEntryList) {
            cfg_.put(new Group(vmEntry.getKey()), "availability", "false");
        }
    }
    
    /**
     * Put [vm_set] `vmInfo.getMoref()`.
     * Put [index "moref_name"] `vmInfo.getName()`.
     * Put [`vmInfo.getMoref()`] *.
     *
     * @param vmInfo virtual machine info.
     * @param calendar Calendar.
     * @param isTemplate True if the entry is template.
     */
    public void addVmEntry(VmInfo vmInfo, Calendar calendar, boolean isTemplate)
    {
        String timestamp = calendar.getTime().toString();
        long timestamp_ms = calendar.getTimeInMillis();
    
        cfg_.put(vmSet_, vmInfo.getMoref(), vmInfo.getName());
        cfg_.put(vmIndex_, vmInfo.getName(), vmInfo.getMoref());
        
        Group groupVmm = new Group(vmInfo.getMoref());
        cfg_.put(groupVmm, "name", vmInfo.getName());
        cfg_.put(groupVmm, "availability", "true");
        cfg_.put(groupVmm, "timestamp", timestamp);
        cfg_.put(groupVmm, "timestamp_ms", Long.toString(timestamp_ms));
        cfg_.put(groupVmm, "is_template", (isTemplate ? "true" : "false"));
    }

    /**
     * Get moref of virtual machine with the specified name
     * using [index "moref_name"] entries.
     * @param vmName
     * @return moref string in success, null in failure.
     */
    public String getVmMorefWithName(String vmName)
    {
        return cfg_.getVal(vmIndex_, vmName);
    }

    /**
     * Get name of virtual machine with the specified moref
     * using [vm_set] entries.
     * @param vmMoref
     * @return vm name string in success, null in failure.
     */
    public String getVmNameWithMoref(String vmMoref)
    {
        return cfg_.getVal(vmSet_, vmMoref);
    }

    /**
     * Get all moref of virtual machines.
     *
     * @return never return null.
     */
    public List<String> getAllVmMorefs()
    {
        List<String> ret = new LinkedList<String>();

        List<Entry> vmEntryList = getAllVmEntries();
        for (Entry vmEntry : vmEntryList) {
            String moref = vmEntry.getKey();
            if (moref != null) {
                ret.add(moref);
            }
        }
        return ret;
    }

    /**
     * Check virtual machine with the name exists.
     */
    public boolean isExistWithName(String vmName)
    {
        assert vmName != null;
        return getVmMorefWithName(vmName) != null;
    }

    /**
     * Check virtual machine with the moref exists.
     */
    public boolean isExistWithMoref(String vmMoref)
    {
        assert vmMoref != null;
        return getVmNameWithMoref(vmMoref) != null;
    }
    
    /**
     * Check availability of the vm.
     */
    public boolean isAvailableWithName(String vmName)
    {
        assert vmName != null;
        String moref = getVmMorefWithName(vmName);
        if (moref == null) { return false; }

        return isAvailableWithMoref(moref);
    }

    /**
     * Check availability of the vm.
     */
    public boolean isAvailableWithMoref(String vmMoref)
    {
        assert vmMoref != null;
        Group vmGrp = new Group(vmMoref);
        String retStr = cfg_.getVal(vmGrp, "availability");
        if (retStr != null && FormatBool.isBool(retStr)) {
            return FormatBool.toBool(retStr);
        } else {
            return false;
        }
    }

    /**
     * Check the vm specified with the name is template or not.
     */
    public boolean isTemplateWithName(String vmName)
    {
        String moref = getVmMorefWithName(vmName);
        if (moref == null) { return false; }

        return isTemplateWithMoref(moref);
    }

    /**
     * Check the moref is template or not.
     *
     * @return False if is_template entry is empty or non-bool value.
     */
    public boolean isTemplateWithMoref(String vmMoref)
    {
        Group vmGrp = new Group(vmMoref);
        String retStr = cfg_.getVal(vmGrp, "is_template");
        if (retStr != null && FormatBool.isBool(retStr)) {
            return FormatBool.toBool(retStr);
        } else {
            return false;
        }
    }

    /**
     * Make VmInfo data from moref.
     */
    public VmInfo makeVmInfoWithMoref(String vmMoref)
    {
        if (vmMoref == null) { return null; }
        String vmName = this.getVmNameWithMoref(vmMoref);
        if (vmName == null) { return null; }
        return new VmInfo(vmName, vmMoref);
    }

    /**
     * Filter moref list passing only available ones.
     */
    public List<String> filterAvailable(List<String> morefList)
    {
        LinkedList<String> ret = new LinkedList<String>();
        for (String moref: morefList) {

            if (moref != null && this.isAvailableWithMoref(moref)) {
                ret.add(moref);
            }
        }
        return ret;
    }

    /**
     * Filter moref list passing only non-template ones.
     */
    public List<String> filterNonTemplate(List<String> morefList)
    {
        LinkedList<String> ret = new LinkedList<String>();
        for (String moref: morefList) {

            if (moref != null && ! this.isTemplateWithMoref(moref)) {
                ret.add(moref);
            }
        }
        return ret;
    }
}
