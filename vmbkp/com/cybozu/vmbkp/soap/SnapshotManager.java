/**
 * @file
 * @brief SnapshotManager
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.soap;

import java.util.LinkedList;
import java.util.logging.Logger;

import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.VirtualMachineSnapshot;
import com.vmware.vim25.VirtualMachineSnapshotTree;
import com.vmware.vim25.DiskChangeInfo;

import com.cybozu.vmbkp.util.VmInfo;
import com.cybozu.vmbkp.util.SnapInfo;
import com.cybozu.vmbkp.util.VmdkInfo;
import com.cybozu.vmbkp.util.VmdkBitmap;
import com.cybozu.vmbkp.util.Utility;

/**
 * @brief Manage a snapshot of a virtual machine.
 */
public class SnapshotManager
{
    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(SnapshotManager.class.getName());
    
    private Connection conn_;
    private VirtualMachineSnapshot snapshot_;
    private VirtualMachineSnapshotTree snapshotTree_;
    private VirtualMachineManager vmm_;
    private VirtualMachineConfigManager configMgr_;

    /**
     * Constructor.
     *
     * You must not create this class by yourself.
     * Instead, 
     * VirtualMachineManager.getCurrentSnapshot() or
     * VirtualMachineManager.searchSnapshotWithName().
     */
    public SnapshotManager
        (Connection conn, VirtualMachine vm, VirtualMachineSnapshot snapshot)
    {
        assert snapshot != null;
        conn_ = conn;
        vmm_ = new VirtualMachineManager(conn_, vm);
        snapshot_ = snapshot;
        snapshotTree_ = vmm_.searchSnapshotTreeWithMoref(this.getMoref());
        configMgr_ = new VirtualMachineConfigManager(conn_, snapshot_.getConfig());
    }

    /**
     * Get name of the snapshot.
     */
    public String getName()
    {
        /* This is virtual machine name. */
        /* return snapshot_.getConfig().getName(); */
        
        return snapshotTree_.getName();
    }

    /**
     * Get moref of the snapshot.
     */
    public String getMoref()
    {
        return snapshot_.getMOR().getVal();
    }

    /**
     * Generate SnapInfo object.
     */
    public SnapInfo getSnapInfo()
    {
        return new SnapInfo(getName(), getMoref());
    }

    /**
     * Generate VmInfo object.
     */
    public VmInfo getVmInfo()
    {
        return vmm_.getVmInfo();
    }

    /**
     * Get config manager of the snapshot.
     */
    public VirtualMachineConfigManager getConfig()
    {
        return configMgr_;
    }

    /**
     * Get virtual machine manager of the snapshot.
     */
    public VirtualMachineManager getVirtualMachine()
    {
        return vmm_;
    }

    /**
     * Get changed blocks information of specified vmdk file.
     * You should call isChangedDisk() before.
     *
     * @param vmdkInfo vmdk information obtained from getAllVmdkInfo() or so.
     * @param changeId changeId of the previous backup time. "*" is used if null.
     * @return changed block information as a bitmap data.
     */
    public VmdkBitmap getChangedBlocksOfDisk
        (VmdkInfo vmdkInfo, String baseChangeId)
    {
        final VirtualMachine vm = vmm_.getVirtualMachine();
        final VirtualMachineSnapshot vmSnap = snapshot_;

        if (baseChangeId == null) {
            baseChangeId = "*";
        }

        logger_.info(vmdkInfo.toString());
        logger_.info(baseChangeId); /* debug */

        final long capacityInBytes = vmdkInfo.capacityInKB_ * 1024L;
        
        /* block size is fixed to 1MB currently */
        VmdkBitmap bmp = new VmdkBitmap(capacityInBytes, 1024 * 1024);
        
        boolean isNotChangedAtAll = false;
        try {
            long offset = 0;
            DiskChangeInfo dci = null;
            do {
                dci = vm.queryChangedDiskAreas
                    (vmSnap, vmdkInfo.key_, offset, baseChangeId);

                if (offset == 0 && (dci.changedArea == null)) {
                    /*
                      ChangeId is not null and DiskChangeInfo.changedArea is null,
                       then the disk is not changed at all.
                     */
                    isNotChangedAtAll = true;
                    logger_.info("This vmdk is not changed at all " +
                                 "from previous backup.");
                    break;
                }
                
                for (int j = 0; j < dci.changedArea.length; j ++) {
                    long start = dci.getChangedArea()[j].getStart();
                    long len = dci.getChangedArea()[j].getLength();
                    logger_.info
                        (String.format
                         ("(%d,%d) %s \n", start, len,
                          (len % 1048576 == 0 ? "" : "not x MB")));

                    bmp.setRangeInBytes(start, len);
                }
                logger_.info
                    (String.format
                     ("offset %d, length %d\n",
                      dci.getStartOffset(), dci.getLength()));
                offset = dci.getStartOffset() + dci.getLength();
            } while (offset < capacityInBytes);
                    
        } catch (Exception e) {
            logger_.warning(Utility.toString(e));
            return null;
        }

        /* If isNotChangedAtAll is true, then all-zero bmp will be returned */
        return bmp;
    }

    /**
     * Check the specified vmdk is changed or not.
     * Call this before calling getChangedBlocksOfDisk().
     *
     * @param vmdkInfo vmdk information obtained from getAllVmdkInfo() or so.
     * @param changeId changeId of the previous backup time.
     * @return true if it is changed, or false.
     *         If baseChangeId is not valid, this method does not mean.
     */
    public boolean isChangedDisk(VmdkInfo vmdkInfo, String baseChangeId)
    {
        logger_.info("isChangedDisk start.");
        
        final VirtualMachine vm = vmm_.getVirtualMachine();
        final VirtualMachineSnapshot vmSnap = snapshot_;

        if (baseChangeId == null) {
            return false;
        }

        /* debug */
        vmdkInfo.print();
        logger_.info(baseChangeId);

        long offset = 0;
        DiskChangeInfo dci;
        try {
            dci = vm.queryChangedDiskAreas
                (vmSnap, vmdkInfo.key_, offset, baseChangeId);
        } catch (Exception e) {
            /*
              Candidates:
              com.vmware.vim25.FileFault,
              com.vmware.vim25.NotFound,
              com.vmware.vim25.RuntimeFault,
              java.rmi.RemoteException
             */
            logger_.warning(Utility.toString(e));
            return false;
        }

        logger_.info("isChangedDisk end");
        
        if (dci != null) {
            return true;
        } else {
            return false;
        }
    }

}
