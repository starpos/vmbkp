/**
 * @file
 * @brief VirtualMachineConfigManager
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.soap;

import java.util.List;
import java.util.LinkedList;
import java.util.logging.Logger;

import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualDeviceFileBackingInfo;
import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo;
import com.vmware.vim25.VirtualDiskSparseVer2BackingInfo;

import com.vmware.vim25.VirtualController;
import com.vmware.vim25.VirtualIDEController;
import com.vmware.vim25.VirtualBusLogicController;
import com.vmware.vim25.VirtualLsiLogicController;
import com.vmware.vim25.VirtualLsiLogicSASController;


import com.cybozu.vmbkp.util.AdapterType;
import com.cybozu.vmbkp.util.VmdkInfo;

import com.cybozu.vmbkp.soap.Connection;
    
/**
 * @brief Manage configuration of a virtual machine or a snapshot of it..
 */
public class VirtualMachineConfigManager
{
    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(VirtualMachineConfigManager.class.getName());
    
    /**
     * Connection.
     */
    private Connection conn_;

    /* This is got from VirtualMachine.getConfig()
       or VirtuamlMachineSnapshot.getConfig(). */
    private VirtualMachineConfigInfo config_;

    /**
     * Constructor.
     * Users must not create objects with new operator by themselves.
     */
    public VirtualMachineConfigManager(Connection conn, VirtualMachineConfigInfo config)
    {
        conn_ = conn;
        config_ = config;
    }

    /**
     * Get vmdk information of virtual disk with changeId.
     *
     * @return list of VmdkInfo.
     */
    public List<VmdkInfo> getAllVmdkInfo()
    {
        LinkedList<VmdkInfo> ret = new LinkedList<VmdkInfo>();
        
        VirtualDevice[] devices = config_.getHardware().getDevice();
        for (int i = 0; devices != null && i < devices.length; i ++) {
            //String deviceLabel = devices[i].getDeviceInfo().getLabel();
            //logger_.info("deviceLabel: " + deviceLabel);

            VirtualDeviceBackingInfo vdbi = devices[i].getBacking();
            if (devices[i] instanceof VirtualDisk &&
                vdbi instanceof VirtualDeviceFileBackingInfo) {

                VirtualDisk diskDev = (VirtualDisk) devices[i];

                /* set required values in the VirtualDevice data. */
                String name = ((VirtualDeviceFileBackingInfo) vdbi).getFileName();
                String changeId = null;
                String uuid = null;
                long capacityInKB = diskDev.getCapacityInKB();
                int key = diskDev.getKey();
                int unitNumber = diskDev.getUnitNumber();
                String diskMode = null;
                
                /* Get required values in the BackingInfo data. */
                if (vdbi instanceof VirtualDiskFlatVer2BackingInfo) {
                    VirtualDiskFlatVer2BackingInfo bi
                        = (VirtualDiskFlatVer2BackingInfo) vdbi;
                    changeId = bi.getChangeId();
                    uuid = bi.getUuid();
                    diskMode = bi.getDiskMode();
                } else if (vdbi instanceof VirtualDiskSparseVer2BackingInfo) {
                    VirtualDiskSparseVer2BackingInfo bi =
                        (VirtualDiskSparseVer2BackingInfo) vdbi;
                    changeId = bi.getChangeId();
                    uuid = bi.getUuid();
                    diskMode = bi.getDiskMode();
                }

                /* get the controller information  */
                Integer ckeyI = diskDev.getControllerKey(); assert ckeyI != null;
                int ckey = ckeyI.intValue();
                AdapterType type = getAdapterType(ckey);
                int busNumber = getBusNumber(ckey);
                
                /* create VmdkInfo object. */
                VmdkInfo a = new VmdkInfo(name, uuid, changeId,
                                          key, ckey, capacityInKB, type,
                                          busNumber, unitNumber, diskMode);
                //a.print("\n"); //debug
                ret.add(a);
            } /* if */
        } /* for */
        return ret;
    }

    /**
     * Get diskpath of all disks belonging to
     * a specified virtual machine or its snapshot.
     *
     * This is for debug.
     * Call getAllVmdkInfo() and you can get the same result
     * from VmdkInfo.name_ of the returned List<VmdkInfo>.
     *
     * @return List of diskpath.
     *         A list of disk path of all disks.
     *         Never returned 'null', but empty list.
     */
    public List<String> getAllDiskNameList()
    {
        LinkedList<String> ret = new LinkedList<String>();
        
        VirtualDevice[] devices = config_.getHardware().getDevice();
        for (int i = 0; devices != null && i < devices.length; i ++) {
            //String deviceLabel = devices[i].getDeviceInfo().getLabel();
            //logger_.info("deviceLabel: " + deviceLabel);

            VirtualDeviceBackingInfo vdbi = devices[i].getBacking();
            if (devices[i] instanceof VirtualDisk &&
                vdbi instanceof VirtualDeviceFileBackingInfo) {
                String fn = ((VirtualDeviceFileBackingInfo) vdbi).getFileName();
                ret.add(fn);
            }
        }
        return ret;
    }

    /**
     * Get adapter type with the specified controller key.
     *
     * @param ckey key of the controller device.
     * @return Adapter type. This does not return null.
     */
    private AdapterType getAdapterType(int ckey)
    {
        VirtualDevice vd = searchVirtualDeviceWithKey(ckey);
        if (vd == null) {
            return AdapterType.UNKNOWN;
        }
        assert vd.getKey() == ckey;

        AdapterType ret = AdapterType.UNKNOWN;
        if (vd instanceof VirtualIDEController) {
            ret = AdapterType.IDE;
        } else if (vd instanceof VirtualBusLogicController) {
            ret = AdapterType.BUSLOGIC;
        } else if (vd instanceof VirtualLsiLogicController) {
            ret = AdapterType.LSILOGIC;
        } else if (vd instanceof VirtualLsiLogicSASController) {
            ret = AdapterType.LSILOGICSAS;
        }
        
        return ret;
    }

    /**
     * Get bus number of the specified device key of a disk controller.
     *
     * @param ckey key of the controller device.
     * @return busNumber in success, or -1 in failure.
     */
    private int getBusNumber(int ckey)
    {
        VirtualDevice vd = searchVirtualDeviceWithKey(ckey);
        if (vd == null) { return -1; }

        if (vd instanceof VirtualController) {
            return ((VirtualController) vd).getBusNumber();
        } else {
            /* error */
            return -1;
        }
    }

    /**
     * Get unit number of the specified device key of a disk.
     *
     * @param ckey key of the disk device.
     * @return unitNumber in success, or -1 in failure.
     */
    private int getUnitNumber(int key)
    {
        VirtualDevice vd = searchVirtualDeviceWithKey(key);
        if (vd == null) { return -1; }

        if (vd instanceof VirtualDisk) {
            return ((VirtualDisk) vd).getUnitNumber();
        } else {
            /* error */
            return -1;
        }
    }

    /**
     * Search virtual device that has the specified device key.
     *
     * @param deviceKey Key of a virtual device in the virtual machine.
     * @return virtual device object in success, or null in failure.
     */
    private VirtualDevice searchVirtualDeviceWithKey(int deviceKey)
    {
        VirtualDevice[] devices = config_.getHardware().getDevice();
        
        for (int i = 0; devices != null && i < devices.length; i ++) {

            int key = devices[i].getKey();
            if (key == deviceKey) {
                return devices[i];
            }
        }
        return null;
    }

    /**
     * @return True if the config says the vm is template.
     */
    public boolean isTemplate()
    {
        return config_.isTemplate();
    }
}
