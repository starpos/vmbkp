/**
 * @file
 * @brief VirtualMachineManager
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.soap;

import java.util.List;
import java.util.LinkedList;
import java.util.logging.Logger;

import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.VirtualMachineSnapshot;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.VirtualMachineSnapshotTree;
import com.vmware.vim25.VirtualMachineSnapshotInfo;
import com.vmware.vim25.ManagedObjectReference;

import com.cybozu.vmbkp.util.VmInfo;
import com.cybozu.vmbkp.util.VmdkInfo;
import com.cybozu.vmbkp.util.AdapterType;
import com.cybozu.vmbkp.util.Utility;

import com.cybozu.vmbkp.soap.SnapshotManager;
import com.cybozu.vmbkp.soap.Connection;
import com.cybozu.vmbkp.soap.VirtualMachineConfigManager;
import com.cybozu.vmbkp.soap.VirtualControllerManager;

/* addDisksToVm */
import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualMachineConfigSpec;

/* exportOvf */
import com.vmware.vim25.OvfFile;
import com.vmware.vim25.OvfCreateDescriptorParams;
import com.vmware.vim25.OvfCreateDescriptorResult;

/* createScsiSpec, createDiskSpec, createNicSpec */
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualDeviceConfigSpecFileOperation;
import com.vmware.vim25.VirtualSCSISharing;
import com.vmware.vim25.VirtualIDEController;
import com.vmware.vim25.VirtualSCSIController;
import com.vmware.vim25.VirtualBusLogicController;
import com.vmware.vim25.VirtualLsiLogicController;
import com.vmware.vim25.VirtualLsiLogicSASController;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualE1000;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.Description;


/**
 * @brief Manage a virtual machine.
 */
public class VirtualMachineManager
{
    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(VirtualMachineManager.class.getName());

    Connection conn_;
    VirtualMachine vm_;
    VirtualMachineConfigManager configMgr_;

    /**
     * Constructor.
     *
     * You must not create objects directly with new operator by yourself.
     */
    public VirtualMachineManager(Connection conn, VirtualMachine vm)
    {
        conn_ = conn;
        vm_ = vm;
        configMgr_ = new VirtualMachineConfigManager(conn_, vm_.getConfig());
    }

    /**
     * Get name of the virtual machine.
     */
    public String getName()
    {
        if (vm_ == null) { return null; }
        return vm_.getName();
    }

    /**
     * Get moref of the virtual machine.
     */
    public String getMoref()
    {
        if (vm_ == null) { return null; }
        return vm_.getMOR().getVal();
    }

    /**
     * Get VmInfo.
     */
    public VmInfo getVmInfo()
    {
        if (vm_ == null) { return null; }
        return new VmInfo(getName(), getMoref());
    }

    /**
     * Get config manager.
     */
    public VirtualMachineConfigManager getConfig()
    {
        return configMgr_;
    }

    /********************************************************************************
     * Raw interface for object who talks soap also.
     ********************************************************************************/

    /**
     * Get VirtualMachine object.
     */
    protected VirtualMachine getVirtualMachine()
    {
        return vm_;
    }
    
    /********************************************************************************
     * Snapshot methods.
     ********************************************************************************/

    /**
     * Get manager of current snapshot if exists.
     */
    public SnapshotManager getCurrentSnapshot()
    {
        try {
            VirtualMachineSnapshot vmSnap = vm_.getCurrentSnapShot();
            if (vmSnap != null) {
                return new SnapshotManager(conn_, vm_, vmSnap);
            } else {
                return null;
            }
        } catch (Exception e) { logger_.warning(Utility.toString(e)); return null; }
    }
    
    /**
     * Search virtual machine snapshot with a specified name.
     */
    public SnapshotManager searchSnapshotWithName(String snapName)
    {
        VirtualMachineSnapshot vmSnap = getSnapshotInTree(snapName);
        if (vmSnap != null) {
            return new SnapshotManager(conn_, vm_, vmSnap);
        } else {
            return null;
        }
    }

    /**
     * Search snapshot with a moref. !!!Not implemented yet.!!!
     */
    public SnapshotManager searchSnapshotWithMoref(String snapMorefStr)
    {
        VirtualMachineSnapshot vmSnap =
            conn_.generateSnapshotWithMoref(snapMorefStr);
        if (vmSnap != null) {
            return new SnapshotManager(conn_, vm_, vmSnap);
        } else {
            return null;
        }
    }

    /**
     * Search snapshot tree with a moref.
     */
    protected VirtualMachineSnapshotTree searchSnapshotTreeWithMoref(String snapMorefStr)
    {
        return searchSnapshotTreeWithMoref
            (vm_.getSnapshot().getRootSnapshotList(), snapMorefStr);
    }

    /**
     * Called by searchSnapshotTreeWithMoref() for recursive search.
     */
    private VirtualMachineSnapshotTree searchSnapshotTreeWithMoref
        (VirtualMachineSnapshotTree[] snapTrees, String snapMorefStr)
    {
        for (int i = 0; i < snapTrees.length; i ++) {
            if (snapTrees[i].getSnapshot().getVal().equals(snapMorefStr)) {
                return snapTrees[i];
            }
            VirtualMachineSnapshotTree[] childTrees =
                snapTrees[i].getChildSnapshotList();
            VirtualMachineSnapshotTree ret =
                searchSnapshotTreeWithMoref(childTrees, snapMorefStr);
            if (ret != null) {
                return ret;
            }
        }
        return null;
    }
    
    /**
     * Create snapshot of a specified virtual machine.
     *
     * @param snapName the name of new snapshot.
     * @return True in success, false in failure.
     */
    public boolean createSnapshot(String snapName)
    {
        try {
            Task task = vm_.createSnapshot_Task(snapName, null, false, false);
            String ret = task.waitForTask();
            if (ret.equals("success")) {
                logger_.info
                    (String.format
                     ("%s: snapshot was created successfully.\n", ret));
                return true;
            } else {
                logger_.info
                    (String.format
                     ("%s: createSnapshot task failed.\n", ret));
                return false;
            }
        } catch (Exception e) { logger_.warning(Utility.toString(e)); return false; }
    }

    /**
     * Find ManagedEntity from ManagedObject of snapshot.
     *
     * @param vm A virtual machine.
     * @param vmSnap Managed object of a snapshot.
     * @return The corresponding ManagedEntity as VirtualMachineSnapshotTree.
     */
    private VirtualMachineSnapshotTree getSnapshotInTree(VirtualMachineSnapshot vmSnap)
    {
        if (vmSnap == null) { return null; }
        
        VirtualMachineSnapshotTree[] snapTree =
            vm_.getSnapshot().getRootSnapshotList();

        if (snapTree == null) { return null; }
        
        VirtualMachineSnapshotTree st =
            findSnapshotInTree(snapTree, vmSnap);
        return st;
    }

    /**
     * Called by getSnapshotInTree().
     *
     * @param snapTree snapshot tree.
     * @param vmSnap snapshot
     * @return Found tree with the specified snapshot.
     */
    private VirtualMachineSnapshotTree findSnapshotInTree
        (VirtualMachineSnapshotTree[] snapTree, VirtualMachineSnapshot vmSnap)
    {
        for (int i = 0; i < snapTree.length; i ++) {
            VirtualMachineSnapshotTree node = snapTree[i];
            if (node.getSnapshot().equals(vmSnap.getMOR())) {
                return node;
            }
            VirtualMachineSnapshotTree[] childTree =
                node.getChildSnapshotList();
            if (childTree != null) {
                VirtualMachineSnapshotTree ret =
                    findSnapshotInTree(childTree, vmSnap);
                if (ret != null) {
                    return ret;
                }
            }
        }
        return null;
    }
    
    /**
     * Find a snapshot with the specified name as a moref
     * in the specified snapshot tree.
     *
     * Original version is written by Steve Jin in sample code in VMSnapshot.java.
     * @author Steve Jin
     */
    private ManagedObjectReference findSnapshotInTree
        (VirtualMachineSnapshotTree[] snapTree, String snapName)
    {
        for (int i = 0; i < snapTree.length; i ++) {
            VirtualMachineSnapshotTree node = snapTree[i];
            if (snapName.equals(node.getName())) {
                return node.getSnapshot();
            } else {
                VirtualMachineSnapshotTree[] childTree =
                    node.getChildSnapshotList();
                if (childTree != null) {
                    ManagedObjectReference mor =
                        findSnapshotInTree(childTree, snapName);
                    if (mor != null) { return mor; }
                }
            }
        }
        return null;
    }
    
    /**
     * Search snapshot with the specified name in the snapshot tree
     * of the virtual machine.
     *
     * Original version is written by Steve Jin in sample code
     * in VMSnapshot.java.
     * @author Steve Jin
     */
    private VirtualMachineSnapshot getSnapshotInTree(String snapName)
    {
        if (snapName == null) { return null; }
        
        VirtualMachineSnapshotTree[] snapTree =
            vm_.getSnapshot().getRootSnapshotList();
        
        if (snapTree != null) {
            ManagedObjectReference mor = findSnapshotInTree(snapTree, snapName);
            if (mor != null) {
                return new
                    VirtualMachineSnapshot(vm_.getServerConnection(), mor);
            }
        }
        return null;
    }
    
    /**
     * Delete snapshot of virtual machine
     *
     * @param snapName snapshot name to delete
     * @return true in success, false in failure.
     */
    public boolean deleteSnapshot(String snapName)
    {
        VirtualMachineSnapshot vmsnap = getSnapshotInTree(snapName);
        if (vmsnap == null) { return false; }

        try {
            Task task = vmsnap.removeSnapshot_Task(true);
            String ret = task.waitForTask();
            if (ret.equals("success")) {
                logger_.info
                    (String.format
                     ("%s: snapshot was deleted successfully.\n", ret));
                return true;
            } else {
                logger_.info
                    (String.format
                     ("%s: deleteSnapshot task failed.\n", ret));
                return false;
            }
        } catch (Exception e) { logger_.warning(Utility.toString(e)); return false; }
    }

    /**
     * Get the name of all snapshots of a specified virtual machine.
     *
     * @return a list of the name of snapshots.
     */
    public List<String> getAllSnapshotNameList()
    {
        VirtualMachineSnapshotInfo snapInfo = vm_.getSnapshot();
        if (snapInfo == null) { return null; }

        VirtualMachineSnapshotTree[] snapTree
            = snapInfo.getRootSnapshotList();

        List<String> ret = getAllSnapshotNameList(snapTree);

        return ret;
    }

    /**
     * For recursive processing.
     */
    private List<String>
        getAllSnapshotNameList(VirtualMachineSnapshotTree[] snapTree)
    {
        List<String> ret = new LinkedList<String>();
        
        for (int i = 0; snapTree != null && i < snapTree.length; i ++) {
            ret.add(snapTree[i].getName());
            VirtualMachineSnapshotTree[] childTree
                = snapTree[i].getChildSnapshotList();
            if (childTree != null) {
                ret.addAll(getAllSnapshotNameList(childTree));
            }
        }
        return ret;
    }

    /**
     * Add empty disks to a specified virtual machine.
     * The virtual machine must not have any SCSI Controller or Disks.
     *
     * @deprecated Use addEmptyDisks() instead.
     * @param datastoreName Datastore name to create disks.
     * @param diskSizeMbList A list of disk size in MB.
     *        If the list size is 2 then 2 disks will be created.
     * @return True in success, false in failure.
     */
    public boolean addDisksToVm
        (String datastoreName, List<Long> diskSizeMbList)
    {
        /* list for created devices */
        List<VirtualDeviceConfigSpec> specList =
            new LinkedList<VirtualDeviceConfigSpec>();

        /* create scsi controller spec */
        int cKey = 1000;
        VirtualDeviceConfigSpec scsiSpec = createScsiSpec(cKey);
        specList.add(scsiSpec);
        
        /* create hard disk spec */
        int unitNumber = 0;
        for (Long diskSizeMb: diskSizeMbList) {
            VirtualDeviceConfigSpec diskSpec = createDiskSpec
                (datastoreName, cKey, diskSizeMb.longValue(),
                 "persistent", unitNumber);
            specList.add(diskSpec);
            unitNumber ++;
        }

        /* set vm config spec */
        VirtualMachineConfigSpec vmConfigSpec =
            new VirtualMachineConfigSpec();
        vmConfigSpec.setChangeTrackingEnabled(true); /* set ctkEnabled option. */
        vmConfigSpec.setDeviceChange
            (specList.toArray(new VirtualDeviceConfigSpec[0]));

        /* reconfigure vm task */
        try {
            Task task = vm_.reconfigVM_Task(vmConfigSpec);
            String ret = task.waitForTask();
            logger_.info(String.format("%s: addDisksToVm()\n", ret));
            if (ret.equals("success")) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) { logger_.warning(Utility.toString(e)); return false; }
    }

    /**
     * Add empty disks and its related disk controllers to the virtual machine.
     * 
     * If the virtual machine has controllers and disks which conflicts
     * newly adding devices with device key or busNumber/unitNumber,
     * reconfigure task will fail.
     *
     * @param ctrlmList a list of virtual controller manager.
     * @return true in success, false in failure.
     */
    public boolean addEmptyDisks(List<VirtualControllerManager> ctrlmList)
    {
        /* list for new devices */
        List<VirtualDeviceConfigSpec> specList =
            new LinkedList<VirtualDeviceConfigSpec>();

        /* add devices to the list */
        for (VirtualControllerManager ctrlm : ctrlmList) {
            /* create new device of the controller and
               all disks managed by it. */
            specList.addAll(ctrlm.createAll());
        }

        /* set vm config spec */
        VirtualMachineConfigSpec vmConfigSpec =
            new VirtualMachineConfigSpec();
        vmConfigSpec.setChangeTrackingEnabled(true); /* set ctkEnabled option. */
        vmConfigSpec.setDeviceChange
            (specList.toArray(new VirtualDeviceConfigSpec[0]));

        /* reconfigure vm task */
        try {
            Task task = vm_.reconfigVM_Task(vmConfigSpec);
            String ret = task.waitForTask();
            logger_.info(String.format("%s: addEmptyDisks()\n", ret));
            if (ret.equals("success")) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) { logger_.warning(Utility.toString(e)); return false; }
    }

    /**
     * Export ovf information of the virtual machine.
     *
     * This may contains disk information but they will be not used and deleted.
     *
     * @return ovf descriptor xml as string data.
     */
    public String exportOvf()
    {
        OvfFile[] ovfFiles = new OvfFile[0];
        OvfCreateDescriptorParams ovfDescParams = new OvfCreateDescriptorParams();
        ovfDescParams.setOvfFiles(ovfFiles);
        try {
            OvfCreateDescriptorResult ovfCreateDescriptorResult =
                conn_.getServiceInstance().getOvfManager().createDescriptor
                (vm_, ovfDescParams);
            return ovfCreateDescriptorResult.getOvfDescriptor();
        } catch (Exception e) { logger_.warning(Utility.toString(e)); return null; }
    }

    /**
     * Reload the information of the virtual machine
     * from the soap server.
     */
    public void reload()
    {
        /* reload config */
        configMgr_ = new VirtualMachineConfigManager(conn_, vm_.getConfig());
    }

    /**
     * This code is copied from
     * CreateVM.java of vi-java sample code.
     * 
     * @deprecated This is called by addDisksToVm() only.
     * @author Steve Jin.
     */
    private VirtualDeviceConfigSpec createScsiSpec(int cKey)
    {
        VirtualDeviceConfigSpec scsiSpec = 
            new VirtualDeviceConfigSpec();
        scsiSpec.setOperation(VirtualDeviceConfigSpecOperation.add);
        VirtualLsiLogicController scsiCtrl = 
            new VirtualLsiLogicController();
        scsiCtrl.setKey(cKey);
        scsiCtrl.setBusNumber(0);
        scsiCtrl.setSharedBus(VirtualSCSISharing.noSharing);
        scsiSpec.setDevice(scsiCtrl);
        return scsiSpec;
    }
  
    /**
     * This code is copied from
     * CreateVM.java of vi-java sample code.
     *
     * @deprecated This is called by addDisksToVm() only.
     * @author Steve Jin.
     */
    private VirtualDeviceConfigSpec createDiskSpec
        (String dsName, int cKey, long diskSizeMB, String diskMode, int unitNumber)
    {
        VirtualDeviceConfigSpec diskSpec = 
            new VirtualDeviceConfigSpec();
        diskSpec.setOperation(VirtualDeviceConfigSpecOperation.add);
        diskSpec.setFileOperation
            (VirtualDeviceConfigSpecFileOperation.create);
    
        VirtualDisk vd = new VirtualDisk();
        vd.setCapacityInKB(diskSizeMB * 1024L);
        diskSpec.setDevice(vd);
        vd.setKey(0);
        vd.setUnitNumber(unitNumber);
        vd.setControllerKey(cKey);

        VirtualDiskFlatVer2BackingInfo diskfileBacking = 
            new VirtualDiskFlatVer2BackingInfo();
        String fileName = "["+ dsName +"]";
        diskfileBacking.setFileName(fileName);
        diskfileBacking.setDiskMode(diskMode);
        diskfileBacking.setThinProvisioned(true);
        vd.setBacking(diskfileBacking);
        return diskSpec;
    }
  
    /**
     * This code is copied from
     * CreateVM.java of vi-java sample code.
     *
     * @deprecated This is called by noone.
     * @author Steve Jin.
     */
    private VirtualDeviceConfigSpec createNicSpec
        (String netName, String nicName)
        throws Exception
    {
        VirtualDeviceConfigSpec nicSpec = 
            new VirtualDeviceConfigSpec();
        nicSpec.setOperation(VirtualDeviceConfigSpecOperation.add);

        VirtualEthernetCard nic =  new VirtualE1000();
        VirtualEthernetCardNetworkBackingInfo nicBacking = 
            new VirtualEthernetCardNetworkBackingInfo();
        nicBacking.setDeviceName(netName);

        Description info = new Description();
        info.setLabel(nicName);
        info.setSummary(netName);
        nic.setDeviceInfo(info);
    
        // type: "generated", "manual", "assigned" by VC
        nic.setAddressType("generated");
        nic.setBacking(nicBacking);
        nic.setKey(0);
   
        nicSpec.setDevice(nic);
        return nicSpec;
    }
}
