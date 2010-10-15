/**
 * @file
 * @brief TestVmsoap
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.itest;

import java.util.Iterator;
import java.util.Set;
import java.util.List;
import java.util.LinkedList;
import java.net.*;
import javax.net.ssl.*;
import java.io.*;
import java.rmi.*;

import com.cybozu.vmbkp.util.Utility;
import com.cybozu.vmbkp.util.XmlIndent;
import com.cybozu.vmbkp.util.VmdkInfo;
import com.cybozu.vmbkp.util.VmdkBitmap;
import com.cybozu.vmbkp.util.VmbkpOvf;

import com.cybozu.vmbkp.soap.Connection;
import com.cybozu.vmbkp.soap.GlobalManager;
import com.cybozu.vmbkp.soap.VirtualMachineManager;
import com.cybozu.vmbkp.soap.SnapshotManager;
import com.cybozu.vmbkp.soap.VirtualMachineConfigManager;

/**
 * @brief Test of vSphere soap wrapper classes.
 */
public class TestVmsoap
{

    /*************************************************************************
     * You must set these constant strings.
    *************************************************************************/

    /* You specify these parameters for vSphere connection. */
    private static final String url_ = "https://vcenter/sdk";
    private static final String username_ = "testuser";
    private static final String password_ = "testpass";

    /**
     * You must prepare a virtual machine with this name.
     */
    private static final String vmName_ = "vmbkptest-1";

    /**
     * You must prepare directories for ovf test.
     * ./ovf and ./ovf2
     */
    private static final String ovfPath_ = "";

    /**
     * Host ane datastore for restore test.
     */
    private static final String hostName_ = "esxihostname";
    private static final String datastoreName_ = "storagename";

    /*************************************************************************/
    
    
    /**
     * Global manager to talk with vSphere soap server.
     */
    private static GlobalManager gm_;

    /**
     * Main method.
     */
    public static void main(String[] args) throws Exception
	{
        Connection conn = new Connection(url_, username_, password_);
        try {
            gm_ = new GlobalManager(conn);

            /* Host and datastore */
            getHostsAndDatastores();
            getDatastores();

            /* Snapshot test */
            createSnapshot();
            deleteSnapshot();

            /* getOvfOfAllVm(); */
            /* exportOvf(); */
            /* importOvf(); */

            /* Export/import ovf test. */
            getInfoAndOvfOfAllVm();
            int num = importAllOvf();
            deleteAllRestoredVm(num);
            
            
        } catch (Exception e) {
            e.printStackTrace();
            
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Get hosts and their datastores
     */
    public static void getHostsAndDatastores()
        throws Exception
    {
        System.out.println("---get host---");
        List<String> hostnameList = gm_.getAllHostNameList();
        Utility.printList(hostnameList);

        for (String hostname: hostnameList) {
            System.out.printf("---get datastore of host %s---\n", hostname);
            Utility.printList(gm_.getAllDatastoreNameList(hostname));
        }
    }
    
    /**
     * Get datastores
     */
    public static void getDatastores()
        throws Exception
    {
        System.out.printf("---get datastores---\n");
        List<String> datastoreList = gm_.getAllDatastoreNameList();
        Utility.printList(datastoreList);
    }

    /**
     * Create snapshot
     **/
    public static void createSnapshot()
        throws Exception
    {
        String snapName = "snapshot-test";
        
        VirtualMachineManager vmm = gm_.searchVmWithName(vmName_);
        assert vmm != null;
        vmm.createSnapshot(snapName);

        SnapshotManager spm = vmm.searchSnapshotWithName(snapName);
        System.out.printf("-----disk list of %s of %s-----\n",
                          spm.getName(), spm.getVirtualMachine().getName());
        Utility.printList(spm.getConfig().getAllDiskNameList());

        System.out.printf("-----vmdkinfo list-----\n");
        List<VmdkInfo> vmdkInfoList = spm.getConfig().getAllVmdkInfo();
        System.out.printf("vmdkInfoList:size %d\n", vmdkInfoList.size());
        for (VmdkInfo vmdkInfo: vmdkInfoList) {
            vmdkInfo.print("\n");
        }
    }

    /**
     * Delete snapshot
     */
    public static void deleteSnapshot()
        throws Exception
    {
        String snapName = "snapshot-test";
        VirtualMachineManager vmm = gm_.searchVmWithName(vmName_);
        assert vmm != null;
        vmm.deleteSnapshot(snapName);
    }

    /**
     * Get configuration of all virtual machines.
     */
    public static void getOvfOfAllVm()
        throws Exception
    {
        List<VirtualMachineManager> list = gm_.getAllVmList();
        for (VirtualMachineManager vmm: list) {
            String ret = vmm.exportOvf();
            System.out.println(ret);
        }
    }
    
    /**
     * Ovf export of a virtual machine.
     */
    public static void exportOvf(String vmName)
        throws Exception
    {
        VirtualMachineManager vmm = gm_.searchVmWithName(vmName);
        if (vmm != null) {
            String ret = vmm.exportOvf();
            FileWriter fw = new FileWriter("ovf/" + vmm.getName() + ".ovf2");
            fw.write(ret);
            fw.close();
                
            VmbkpOvf ovf = new VmbkpOvf(ret);
            ovf.replaceFileLinkInReferences();
            String ret2 = ovf.toString();
            FileWriter fw2 = new FileWriter("ovf/" + vmm.getName() + ".ovf3");
            fw2.write(ret2);
            fw2.close();
        }
    }

    /**
     * Ovf import test.
     */
    public static void importOvf
        (String ovfPath, String newName, String hostName, String datastoreName)
        throws Exception
    {
        String morefOfNewVm =
            gm_.importOvf(ovfPath, newName, hostName, datastoreName);
        
        if (morefOfNewVm == null) {
            System.out.printf("importOvf failed\n");
        } else {
            System.out.printf("moref of new vm is %s\n", morefOfNewVm);
        }
    }

    /**
     * Get name, moref, and diskpath of all virtual machines.
     * Then save OVF file of all virtual machines.
     */
    public static void getInfoAndOvfOfAllVm()
        throws Exception
    {
        File ovfDir = new File("./ovf");
        assert ovfDir.isDirectory();
        
        List<VirtualMachineManager> vmmList = gm_.getAllVmList();
        for (VirtualMachineManager vmm: vmmList) {

            /* Info of virtual machine. */
            String name = vmm.getName();
            String moref = vmm.getMoref();
            String diskstr = "";
            List<String> diskNameList =
                vmm.getConfig().getAllDiskNameList();
            for (String diskName: diskNameList) {
                diskstr = diskstr.concat(diskName);
                diskstr = diskstr.concat(",");
            }
            System.out.printf("vm:%s:%s\n%s\n", name, moref, diskstr);

            /* Latest snapshot of the virtual machine. */
            SnapshotManager vmSnap = vmm.getCurrentSnapshot();
            if (vmSnap != null) {
                /* vm name */
                String vmName = vmSnap.getName();

                String vmSnapMoref = vmSnap.getMoref();
                String vmSnapDiskStr = "";
                List<String> snapDiskList =
                    vmSnap.getConfig().getAllDiskNameList();
                for (String diskName: snapDiskList) {
                    vmSnapDiskStr = vmSnapDiskStr.concat(diskName);
                    vmSnapDiskStr = vmSnapDiskStr.concat(",");
                }
                System.out.printf
                    ("snapshot:%s:%s\n%s\n",
                     vmName, vmSnapMoref, vmSnapDiskStr);
            }

            /* ovf export */
            String ret = vmm.exportOvf();
            VmbkpOvf ovf = new VmbkpOvf(ret);
            //ovf.replaceFileLinkInReferences();
            ovf.deleteFilesInReferences();
            ovf.deleteDisksInDiskSection();
            Set<String> ctrlIdSet = ovf.deleteDiskDevicesInHardwareSection();
            for (String id: ctrlIdSet) {
                System.out.printf("ctrlId:%s\n", id); /* debug */
            }
            ovf.deleteControllerDevicesWithoutChildInHardwareSection(ctrlIdSet);

            /* fix indent of xml data for human's easy read. */
            XmlIndent xmli = new XmlIndent(ovf.toString());
            xmli.fixIndent();
                
            String ret2 = xmli.toString();
            FileWriter fw2 = new FileWriter(ovfDir.getPath() +
                                            File.pathSeparator +
                                            vmm.getName() + ".ovf");
            fw2.write(ret2);
            fw2.close();

            System.out.println();
        }
    }
    
    /**
     * Ovf import test of all virtual machines
     * as the name of ovftest-`i` such that 0 <= i < returned integer.
     *
     * @return number of imported virtual machines.
     */
    public static int importAllOvf()
        throws Exception
    {
        File ovfDir = new File("./ovf");
        assert ovfDir.isDirectory();
        File[] fileList = ovfDir.listFiles();

        int i = 0;
        for (File file: fileList) {

            System.out.printf("--------------------\n");
            System.out.printf("%s\n", file.getName());
            System.out.printf("--------------------\n");
                
            String morefOfNewVm =
                gm_.importOvf(file.getPath(), "ovftest-" + String.valueOf(i),
                              hostName_, datastoreName_);
            if (morefOfNewVm == null) {
                System.out.printf("failed\n");
            } else {
                System.out.printf("succeeded: moref is %s\n", morefOfNewVm);
            }

            ++ i;
        }
        return i;
    }
    
    /**
     * Delete all restored virtual machines in this test.
     * The name of ovftest-`i` such that 0 <= i < num.
     */
    public static void deleteAllRestoredVm(int num)
        throws Exception
    {
        for (int i = 0; i < num; ++ i) {
            String vmName = "ovftest-" + String.valueOf(i);
            VirtualMachineManager vmm = gm_.searchVmWithName(vmName);
            if (vmm != null) {
                System.out.printf("destroy %s...", vmName);
                boolean ret = gm_.destroyVm(vmm);
                if (ret) {
                    System.out.printf("success\n");
                } else {
                    System.out.printf("failed\n");
                }
            }
        }
    }

}
