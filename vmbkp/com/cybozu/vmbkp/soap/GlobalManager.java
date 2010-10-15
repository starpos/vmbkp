/**
 * @file
 * @brief GlobalManager
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.soap;

import java.util.List;
import java.util.LinkedList;
import java.util.logging.Logger;
import java.io.IOException;

import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.ManagedEntity;

/* required by importOvf */
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.ComputeResource;
import com.vmware.vim25.mo.HttpNfcLease;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.OvfCreateImportSpecParams;
import com.vmware.vim25.OvfNetworkMapping;
import com.vmware.vim25.OvfCreateImportSpecResult;
import com.vmware.vim25.HttpNfcLeaseState;

/* required by zerofill */
import com.vmware.vim25.mo.VirtualDiskManager;
import com.vmware.vim25.mo.Datacenter;

/* required by readOvfContent */
import java.lang.StringBuffer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;

import com.cybozu.vmbkp.util.Utility;

/**
 * @brief Manage a vSphere environment.
 */
public class GlobalManager
{
    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(GlobalManager.class.getName());
    
    /**
     * vSphere connection.
     */
    private Connection conn_;

    /**
     * Contructor.
     */
    public GlobalManager(Connection conn)
    {
        assert conn != null;
        conn_ = conn;
    }

    /**
     * Connect to the soap server if not connected.
     */
    public void connect()
        throws Exception
    {
        assert conn_ != null;
        conn_.connect();
    }
    
    /**
     * Disconnect from the soap server if connected.
     * The application must call this at the end.
     */
    public void disconnect()
    {
        assert conn_ != null;
        conn_.disconnect();
    }

    /**
     * Search virtual machine with a specified name.
     *
     * @param vmName The name of a virtual machine.
     * @return virtual machine manager in success.
     */
    public VirtualMachineManager searchVmWithName(String vmName)
        throws Exception
    {
        if (conn_.isConnected() == false) { conn_.connect(); }
        ManagedEntity vm = conn_.searchManagedEntity("VirtualMachine", vmName);
        if (vm == null) { throw new Exception(); }
        return new VirtualMachineManager(conn_, (VirtualMachine)vm);
    }

    /**
     * Search virtual machine with a specified moref.
     *
     * @param vmMorefStr The moref of a virtual machine.
     * @return virtual machine manager in success.
     */
    public VirtualMachineManager searchVmWithMoref(String vmMorefStr)
        throws Exception
    {
        if (conn_.isConnected() == false) { conn_.connect(); }
        /* generate VirtualMachine object from moref string */
        VirtualMachine vm = conn_.generateVirtualMachineWithMoref(vmMorefStr);
        if (vm == null) { throw new Exception("virtual machine is not found."); }
        
        return new VirtualMachineManager(conn_, vm);
    }
    
    /**
     * Remove virtual machine(removing also disk files).
     *
     * @param vm Virtual machine manager to destroy.
     * @return true in success, false in failure.
     */
    public boolean destroyVm(VirtualMachineManager vmm)
    {
        if (vmm == null ) { return false; }
        VirtualMachine vm = vmm.getVirtualMachine();
        if (vm == null) { return false; }
        
        try {
            Task task = vm.destroy_Task();
            String ret = task.waitForTask();
            if (ret.equals("success")) {
                logger_.info
                    (String.format
                     ("%s: virtual machine was destroyed successfully.\n", ret));
                return true;
            } else {
                logger_.info
                    (String.format
                     ("%s: virtual machine destory failed.\n", ret));
                return false;
            }
        } catch (Exception e) {
            /* InterruptedException, VimFault, RuntimeFault, RemoteException */
            logger_.warning(Utility.toString(e));
            return false;
        }
    }

    /**
     * Deploy ovf template using default host and datastore.
     */
    public String importOvf(String ovfPath, String newVmName)
        throws Exception
    {
        return importOvf(ovfPath, newVmName, null, null);
    }
    
    /**
     * Deploy ovf template to vSphere environment as a new virtual machine.
     *
     * @param ovfPath Ovf file path.
     *        The ovf file must not include disk information.
     * @param newVmName The name of the newly created virtual machine.
     * @param hostName The name of ESX(i) host.
     * @param datastoreName The name of datastore.
     * @return moref string in success.
     *
     */
    public String importOvf(String ovfPath, String newVmName,
                             String hostName, String datastoreName)
        throws Exception
    {
        if (conn_.isConnected() == false) { conn_.connect(); }

        /* Get the HostSystem */
        HostSystem host = getAvailableHost(hostName);
        assert host != null;

        /* Check the specified datastore exists and available with the host. */
        Datastore datastore = getAvailableDatastore(datastoreName, host);
        assert datastore != null;
        
        /* create spec */
        Folder vmFolder = null;
        OvfCreateImportSpecParams importSpecParams
            = new OvfCreateImportSpecParams();
        String ovfDescriptor = "";

        vmFolder = (Folder) host.getVms()[0].getParent();

        importSpecParams.setLocale("US");
        importSpecParams.setEntityName(newVmName);
        importSpecParams.setDeploymentOption("");
        OvfNetworkMapping networkMapping = new OvfNetworkMapping();
        networkMapping.setName("Network 1");
        networkMapping.setNetwork(host.getNetworks()[0].getMOR());
        importSpecParams.setNetworkMapping(new OvfNetworkMapping[] { networkMapping });
        importSpecParams.setPropertyMapping(null);

        /* read ovf from the file. */
        ovfDescriptor = readOvfContent(ovfPath);

        /* create ovf descriptor */
        ovfDescriptor = escapeSpecialChars(ovfDescriptor);
        //logger_.info("ovfDesc: " + ovfDescriptor);

        ResourcePool rp = ((ComputeResource) host.getParent()).getResourcePool();

        logger_.fine(String.format("vmname: %s\n" +
                                   "resourcepool: %s\n" +
                                   "host:%s\n" +
                                   "datastore:%s\n",
                                   newVmName,
                                   rp.getName(),
                                   host.getName(),
                                   datastore.getName()));
        
        OvfCreateImportSpecResult ovfImportResult = null;
        HttpNfcLease httpNfcLease = null;        

        /* create import spec */
        ovfImportResult =
            conn_.getServiceInstance().getOvfManager().createImportSpec
            (ovfDescriptor, rp, datastore, importSpecParams);

        /* import execution */
        try {
            httpNfcLease = 
                rp.importVApp(ovfImportResult.getImportSpec(), vmFolder, host);
        } catch (Exception e) {
            logger_.warning("importVapp failed.");
            throw e;
        }

        String morefOfNewVm = null;
        
        /* wait nfc lease */
        HttpNfcLeaseState hls;
		while (true) {
			hls = httpNfcLease.getState();
			if (hls == HttpNfcLeaseState.ready ||
                hls == HttpNfcLeaseState.error) { break; }
		}
        if (hls == HttpNfcLeaseState.ready) {

            morefOfNewVm = httpNfcLease.getInfo().getEntity().getVal();

            logger_.info
                (String.format
                 ("Moref of the created vm: %s\n", morefOfNewVm));
            
            httpNfcLease.httpNfcLeaseComplete();
            /*
              We do not upload disk files, because the specified ovf
              must not contain disk information.
            */
        } else {
            logger_.warning("Could not obtain nfc lease.");
            throw new Exception();
        }
        
        return morefOfNewVm;
    }

    /**
     * Called by importOvf().
     * Original version is written by Steve Jin.
     * @author Steve Jin <sjin@vmware.com>
     */
	private String readOvfContent(String ovfFilePath)
        throws IOException 
	{
		StringBuffer strContent = new StringBuffer();
		BufferedReader in = new BufferedReader
            (new InputStreamReader(new FileInputStream(ovfFilePath)));
		String lineStr;
		while ((lineStr = in.readLine()) != null) {
			strContent.append(lineStr);
		}
		in.close();
		return strContent.toString();
	}

    /**
     * Called by importOvf().
     * Original version is written by Steve Jin.
     * @author Steve Jin <sjin@vmware.com>
     */
    private String escapeSpecialChars(String str)
	{
		str = str.replaceAll("<", "&lt;");
		return str.replaceAll(">", "&gt;");
        /* do not escape "&" -> "&amp;", "\"" -> "&quot;" */
	}

    /**
     * Get the list of all virtual machine managers.
     *
     * @return list of virtual machine objects. never return 'null'.
     */
    public List<VirtualMachineManager> getAllVmList()
        throws Exception
    {
        if (conn_.isConnected() == false) { conn_.connect(); }
        
        List<VirtualMachineManager> ret =
            new LinkedList<VirtualMachineManager>();
        
        List<ManagedEntity> tmpList =
            conn_.searchManagedEntities("VirtualMachine", "name");

        int i = 0;
        for (ManagedEntity me: tmpList) {
            assert (me instanceof VirtualMachine);
            VirtualMachineManager vmm =
                new VirtualMachineManager(conn_, (VirtualMachine) me);

            String logStr = String.format
                ("%d: %s %s", i, vmm.getMoref(), vmm.getName());
            System.out.println(logStr);
            logger_.info(logStr);
            
            ret.add(vmm);
            i ++;
        }
        return ret;
    }

    /**
     * Get the list of name of all ESX(i) hosts.
     */
    public List<String> getAllHostNameList()
        throws Exception
    {
        List<String> ret = new LinkedList<String>();

        List<HostSystem> tmp = getAllHostList();

        for (HostSystem hs: tmp) {
            ret.add(hs.getName());
        }
        return ret;
    }

    /**
     * Get the list of name of all Datastores managed
     * by the vSphere server.
     */
    public List<String> getAllDatastoreNameList()
        throws Exception
    {
        List<String> ret = new LinkedList<String>();

        List<Datastore> tmp = getAllDatastoreList();

        for (Datastore ds: tmp) {
            ret.add(ds.getName());
        }
        return ret;
    }

    /**
     * Get the list of name of all Datastores of a specified ESX(i) host.
     */
    public List<String> getAllDatastoreNameList(String hostname)
        throws Exception
    {
        if (conn_.isConnected() == false) { conn_.connect(); }

        List<String> ret = new LinkedList<String>();
        
        ManagedEntity host = conn_.searchManagedEntity("HostSystem", hostname);
        if (host == null) { return ret; }

        List<Datastore> tmp = getAllDatastoreList((HostSystem) host);

        for (Datastore ds: tmp) {
            ret.add(ds.getName());
        }
        return ret;
    }
    
    /**
     * Get all ESX(i) hosts.
     *
     * @return list of host system objects. never return 'null'.
     */
    private List<HostSystem> getAllHostList()
        throws Exception
    {
        if (conn_.isConnected() == false) { conn_.connect(); }

        List<HostSystem> ret = new LinkedList<HostSystem>();

        List<ManagedEntity> tmpList =
            conn_.searchManagedEntities("HostSystem", "name");

        for (ManagedEntity me: tmpList) {
            ret.add((HostSystem) me);
        }
        return ret;
    }

    /**
     * Get all Datastores.
     *
     * @return list of datastore objects. never return 'null'.
     */
    private List<Datastore> getAllDatastoreList()
        throws Exception
    {
        if (conn_.isConnected() == false) { conn_.connect(); }

        List<Datastore> ret = new LinkedList<Datastore>();

        List<ManagedEntity> tmpList =
            conn_.searchManagedEntities("Datastore", "name");

        for (ManagedEntity me: tmpList) {
            ret.add((Datastore) me);
        }
        return ret;
    }

    /**
     * Get all Datastores.
     *
     * @return list of datastore objects. never return 'null'.
     */
    private List<Datastore> getAllDatastoreList(HostSystem host)
    {
        List<Datastore> ret = new LinkedList<Datastore>();
        if (host == null) { return ret; }

        try {
            Datastore[] tmpList = host.getDatastores();

            for (int i = 0; i < tmpList.length; i ++) {
                ret.add(tmpList[i]);
            }
            return ret;
        } catch (Exception e) { logger_.warning(Utility.toString(e)); return ret; }
    }

    /**
     * Get name of default host.
     */
    public String getDefaultHostName()
        throws Exception
    {
        return getDefaultHost().getName();
    }

    /**
     * Get default host.
     */
    private HostSystem getDefaultHost()
        throws Exception
    {
     	return getAllHostList().get(0);
    }
    
    /**
     * Get name of default datastore.
     */
    public String getDefaultDatastoreName(String hostName)
        throws Exception
    {
        ManagedEntity host = conn_.searchManagedEntity("HostSystem", hostName);
        if (host == null) { throw new Exception("host is not found."); }
        return getDefaultDatastore((HostSystem) host).getName();
    }
    
    /**
     * Get default datastore.
     */
    private Datastore getDefaultDatastore(HostSystem host)
    {
        return getAllDatastoreList(host).get(0);
    }

    /**
     * Get available host with the name or default one.
     *
     * @param hostName Host name to search or null to use default host.
     * @return HostSystem object.
     * @exception When hostsystem not found.
     */
    private HostSystem getAvailableHost(String hostName)
        throws Exception
    {

        HostSystem host = null;

        if (hostName != null) {
            List<HostSystem> hosts = getAllHostList();
            for (HostSystem h: hosts) {
                if (hostName.equals(h.getName())) {
                    host = h; break;
                }
            }
        }

        if (hostName != null && host == null) {
            logger_.warning
                (String.format("Host %s not found.", hostName));
            hostName = null;
        }
        
        if (hostName == null) {
            host = getDefaultHost();
            logger_.info(String.format
                         ("Use default host %s.", host.getName()));
        }

        if (host == null) {
            throw new Exception("host is null.");
        }
        return host;
    }

    /**
     * Get available datastore with the name or default one.
     *
     * @param hostName Host name to search or null to use default host.
     * @param host HostSystem object that mounts the datastore.
     * @return Datastore object.
     * @exception When datastore not found.
     */
    private Datastore getAvailableDatastore
        (String datastoreName, HostSystem host)
        throws Exception
    {
        assert host != null;
        Datastore datastore = null;

        if (datastoreName != null) {
            List<Datastore> datastores = getAllDatastoreList(host);
            for (Datastore d: datastores) {
                if (datastoreName.equals(d.getName())) {
                    datastore = d; break;
                }
            }
        }

        if (datastoreName != null && datastore == null) {
            logger_.warning
                (String.format("Datastore %s not found.", datastoreName));
            datastoreName = null;
        }
        
        if (datastoreName == null) {
            datastore = getDefaultDatastore(host);
            logger_.info
                (String.format
                 ("Use default datastore %s.", datastore.getName()));
        }
        
        if (datastore == null) {
            throw new Exception("datastore is null.");
        }
        return datastore;
    }
}
