/**
 * @file
 * @brief VmdkInfo
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

/**
 * @brief Virtual disk information with controller information.
 */
public class VmdkInfo
{
    /**
     * Disk path.
     */
    public final String name_;

    /**
     * UUID of the virtual disk.
     */
    public final String uuid_;

    /**
     * Change id if available.
     * This is managed by VMware vSphere/ESX(i) for incremental backup.
     * We just get the change id from the current snapshot and stored
     * for next-time incremental backup.
     */
    public final String changeId_;

    /**
     * Device key as a VirtualDevice.
     */
    public final int key_;

    /**
     * Controller key to which the disk belongs.
     * This will be used to reconstruct the controller-disk mapping.
     * Disks who shared the same ckey_ should share a disk controller.
     * Of course the type_ of the disks must be same.
     */
    public final int ckey_;

    /**
     * Disk capacity in kilo bytes.
     */
    public final long capacityInKB_;

    /**
     * Adapter type of the disk controller.
     */
    public final AdapterType type_;

    /**
     * Bus number. Ex. scsiA:B has busnumber A and unitnumber B.
     */
    public final int busNumber_;

    /**
     * Unit number. Ex. scsiA:B has busnumber A and unitnumber B.
     */
    public final int unitNumber_;

    /**
     * Disk mode. Ex. 'persistent', 'independent_persistent'.
     */
    public final String diskMode_;
    
    /**
     * Constructor.
     *
     * @param name Disk path in the vSphere.
     * @param uuid unique identifier of the disk managed in the vSphere.
     * @param changeId changeId string
     * if ctkEnabled of the disk is true, or null.
     * @param key Device key of the disk.
     * @param ckey Device key of the controller to which the disk belongs.
     * @param capacityInKB Disk capacity in kilo bytes.
     * @param type Adapter type.
     * @param busNumber Controller number in the virtual machine.
     * @param unitNumber Disk number in the controller.
     * @param diskMode Disk mode string.
     */
    public VmdkInfo(String name, String uuid, String changeId,
                    int key, int ckey, long capacityInKB, AdapterType type,
                    int busNumber, int unitNumber, String diskMode)
    {
        name_ = name;
        uuid_ = uuid;
        changeId_ = changeId;
        key_ = key;
        ckey_ = ckey;
        capacityInKB_ = capacityInKB;
        type_ = type;
        busNumber_ = busNumber;
        unitNumber_ = unitNumber;
        diskMode_ = diskMode;
    }

    /**
     * Print the contents.
     */
    public void print()
    {
        print("\n");
    }
        
    /**
     * Print the contents with a suffix.
     */
    public void print(String suffix)
    {
        System.out.print(toString(suffix));
    }

    /**
     * Convert to string.
     */
    public String toString(String suffix)
    {
        return String.format("VmdkInfo: name:%s, uuid:%s, changeId:%s, " +
                             "key:%d, ckey:%d, capacityInKB:%d, type:%s, " +
                             "busNumber: %d, unitNumber: %d, diskMode: %s" +
                             "%s",
                             name_, uuid_, changeId_,
                             key_, ckey_, capacityInKB_, type_.toString(),
                             busNumber_, unitNumber_, diskMode_,
                             suffix);
    }

    /**
     * Convert to string.
     */
    public String toString()
    {
        return toString("");
    }
}
