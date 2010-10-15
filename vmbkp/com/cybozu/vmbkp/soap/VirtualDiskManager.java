/**
 * @file
 * @brief VirtualDiskManager
 *
 * !!!Causion!!! This is not 'com.vmware.vim25.mo.VirtualDiskManager'.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.soap;

import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualDeviceConfigSpecFileOperation;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo;

import com.cybozu.vmbkp.util.AdapterType;

/**
 * @brief Manage a virtual disk specification.
 * 
 * This class is used to add disks to a virtual machine.
 */
public class VirtualDiskManager
    implements Comparable<VirtualDiskManager>
{
    /**
     * device key of the disk.
     */
    private int key_;

    /**
     * unit number of the disk. (B of scsiA:B for example.)
     */
    private int unitNumber_;

    /**
     * Disk capacity.
     */
    private long capacityInKb_;

    /**
     * Datastore name.
     */
    private String datastore_;
    
    /**
     * Disk controller data.
     */
    private VirtualControllerManager controller_;

    /**
     * Constructor.
     *
     * @param key device key. you can set 0 and it will be automatically set,
     *        but you cannot detect the newly added disk uniquely.
     * @param unitNumber unit number of the disk.
     * @param capacityInKb disk capacity in kilo bytes.
     * @param datastore datastore name (not disk path).
     */
    public VirtualDiskManager(int key, int unitNumber,
                              long capacityInKb, String datastore)
    {
        key_ = key;
        unitNumber_ = unitNumber;
        capacityInKb_ = capacityInKb;
        datastore_ = datastore;
        controller_ = null;
    }

    /**
     * Comparator
     *
     * Rule 1. controller_ order.
     * Rule 2. unitNumber_ order.
     */
    public int compareTo(VirtualDiskManager rht)
    {
        int cmpCtrl = this.controller_.compareTo(rht.controller_);
        if (cmpCtrl == 0) {
            return this.unitNumber_ - rht.unitNumber_;
        } else {
            return cmpCtrl;
        }
    }

    /**
     * This is called by VirtualControllerManager.add().
     */
    protected void setController(VirtualControllerManager controller)
    {
        controller_ = controller;
    }

    /**
     * Create disk spec for VMware vSphere environment.
     */
    public VirtualDeviceConfigSpec create(boolean isThinProvisioned)
    {
        /* controller_ must not be null */
        if (controller_ == null) { return null; }
        
        VirtualDeviceConfigSpec diskSpec = 
            new VirtualDeviceConfigSpec();
        diskSpec.setOperation(VirtualDeviceConfigSpecOperation.add);
        diskSpec.setFileOperation
            (VirtualDeviceConfigSpecFileOperation.create);
    
        VirtualDisk vd = new VirtualDisk();
        vd.setCapacityInKB(capacityInKb_);
        diskSpec.setDevice(vd);
        vd.setKey(key_); /* no need to set key_ */
        vd.setUnitNumber(unitNumber_);
        vd.setControllerKey(controller_.getCkey());

        VirtualDiskFlatVer2BackingInfo diskfileBacking = 
            new VirtualDiskFlatVer2BackingInfo();
        String fileName = "["+ datastore_ +"]";
        diskfileBacking.setFileName(fileName);
        diskfileBacking.setDiskMode("persistent");
        diskfileBacking.setThinProvisioned(isThinProvisioned);
        vd.setBacking(diskfileBacking);
        return diskSpec;
    }

    /**
     * Print information for debug.
     */
    public void print()
    {
        System.out.println(toString());
    }

    /**
     * Convert to string.
     */
    public String toString()
    {
        return
            String.format
            ("VirtualDiskManager: " +
             "key: %d, unitNumber: %d, capacityInKb: %d, datastore: %s\n",
             key_, unitNumber_, capacityInKb_, datastore_);
    }

}
