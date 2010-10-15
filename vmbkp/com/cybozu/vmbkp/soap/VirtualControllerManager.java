/**
 * @file
 * @brief VirtualControllerManager
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.soap;

import java.util.List;
import java.util.LinkedList;

import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualController;
import com.vmware.vim25.VirtualIDEController;
import com.vmware.vim25.VirtualSCSIController;
import com.vmware.vim25.VirtualBusLogicController;
import com.vmware.vim25.VirtualLsiLogicController;
import com.vmware.vim25.VirtualLsiLogicSASController;
import com.vmware.vim25.VirtualSCSISharing;

import com.cybozu.vmbkp.util.AdapterType;

/**
 * @brief Manage a virtual disk controller.
 *
 * This class is used to add disk controllers to a virtual machine.
 */
public class VirtualControllerManager
    implements Comparable<VirtualControllerManager>
{
    /**
     * Adapter type.
     */
    private AdapterType type_;

    /**
     * Controller key.
     */
    private int ckey_;
    
    /**
     * Bus number.
     */
    private int busNumber_;

    /**
     * List of disks.
     */
    private List<VirtualDiskManager> diskList_; 

    /**
     * Constructor.
     */
    public VirtualControllerManager
        (AdapterType type, int ckey, int busNumber)
    {
        type_ = type;
        ckey_ = ckey;
        busNumber_ = busNumber;
        diskList_ = new LinkedList<VirtualDiskManager>();
    }

    /**
     * Comparator.
     *
     * Rule 1. IDE < SCSI.
     * Rule 2. busNumber_ order.
     */
    public int compareTo(VirtualControllerManager rht)
    {
        if (this.type_ == rht.type_ ||
            (this.type_ != AdapterType.IDE &&
             rht.type_ != AdapterType.IDE)) {

            return this.busNumber_ - rht.busNumber_;
        } else {
            if (this.type_ == AdapterType.IDE) {
                return -1;
            } else {
                assert rht.type_ == AdapterType.IDE;
                return 1;
            }
        }
    }
    
    /**
     * Add virtual disk manager and assign to this controller.
     */
    public void add(VirtualDiskManager diskm)
    {
        diskm.setController(this);
        diskList_.add(diskm);
    }

    /**
     * Get controller key.
     */
    protected Integer getCkey()
    {
        return new Integer(ckey_);
    }

    /**
     * Create scsi controller spec and all of its child disks.
     */
    public List<VirtualDeviceConfigSpec> createAll()
    {
        List<VirtualDeviceConfigSpec> specList =
            new LinkedList<VirtualDeviceConfigSpec>();

        /* Create spec of the controller. */
        specList.add(this.create());

        /* Create spec of all disks managed by the controller. */
        for (VirtualDiskManager diskm : diskList_) {
            VirtualDeviceConfigSpec diskSpec =
                diskm.create(true /* isThinProvisioned */);
            assert diskSpec != null;
            specList.add(diskSpec);
        }

        return specList;
    }

    /**
     * Create scsi controller spec, it does not create spec of disks.
     */
    private VirtualDeviceConfigSpec create()
    {
        VirtualDeviceConfigSpec controllerSpec =
            new VirtualDeviceConfigSpec();
        controllerSpec.setOperation(VirtualDeviceConfigSpecOperation.add);

        VirtualController ctrl = null;
        boolean isScsi = true;
        
        switch(type_) {
        case IDE:
            isScsi = false;
            ctrl = new VirtualIDEController();
            break;
        case BUSLOGIC:
            isScsi = true;
            ctrl = new VirtualBusLogicController();
            break;
        case LSILOGIC:
            isScsi = true;
            ctrl = new VirtualLsiLogicController();
            break;
        case LSILOGICSAS:
            isScsi = true;
            ctrl = new VirtualLsiLogicSASController();
            break;
        default:
            return null;
        }

        ctrl.setKey(ckey_);
        ctrl.setBusNumber(busNumber_);
        if (isScsi) {
            assert ctrl instanceof VirtualSCSIController;
            VirtualSCSIController scsiCtrl = (VirtualSCSIController) ctrl;
            scsiCtrl.setSharedBus(VirtualSCSISharing.noSharing);
        }
        controllerSpec.setDevice(ctrl);

        return controllerSpec;
    }

    /**
     * @return Number of disks the controller has.
     */
    public int getNumOfDisks()
    {
        assert diskList_ != null;
        return diskList_.size();
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
        StringBuffer sb = new StringBuffer();

        sb.append(String.format
                  ("-----VirtualControllerManager-----\n" +
                   "type: %s, ckey: %d, busNumber: %d\n",
                   type_.toString(), ckey_, busNumber_));
        for (VirtualDiskManager vdm : diskList_) {
            sb.append(vdm.toString());
        }
        sb.append("----------------------------------------\n");

        return sb.toString();
    }
    
}
