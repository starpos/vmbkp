/**
 * @file
 * @brief Connection
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.soap;

import java.util.List;
import java.util.LinkedList;
import java.util.logging.Logger;
import java.net.URL;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ManagedObject;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.VirtualMachineSnapshot;
import com.vmware.vim25.mo.util.MorUtil;

import com.cybozu.vmbkp.util.Utility;

/**
 * @brief Manage vSphere Connection
 */
public class Connection
{
    /**
     * Logger.
     */
    private static final Logger logger_
        = Logger.getLogger(Connection.class.getName());
    
    /*
     * These variables are shared by all methods.
     * They are initialized at the connect() method.
     * The disconnect() method cleans up them.
     */
    private ServiceInstance si_;
    private Folder rootFolder_;

    private String url_;
    private String username_;
    private String password_;
    
    /**
     * Constructor.
     *
     * @param url URL of vcenter server.
     * @param username username.
     * @param password password.
     */
    public Connection(String url, String username, String password)
    {
        url_ = url;
        username_ = username;
        password_ = password;
        si_ = null;
        rootFolder_ = null;
    }

    /**
     * Connect to the vcenter server.
     */
    public void connect()
        throws Exception
    {
        logger_.info(String.format("connecting to %s...", url_));
		si_ = new ServiceInstance
            (new URL(url_), username_, password_, true);
		rootFolder_ = si_.getRootFolder();
        logger_.info("connected");
    }

    /**
     * Disconnect from the vcenter server.
     */
    public void disconnect() {
        if (isConnected()) {
            logger_.info("disconnecting...");
            si_.getServerConnection().logout();
            si_ = null;
            rootFolder_ = null;
            logger_.info("disconnected");
        } else {
            logger_.info("already connected.");
        }
    }

    /********************************************************************************
     * Methods for object who talks soap directly.
     ********************************************************************************/

    /**
     * If connected, return true.
     */
    protected boolean isConnected()
    {
        return si_ != null;
    }

    /**
     * Get ServiceInstance object.
     */
    protected ServiceInstance getServiceInstance()
    {
        return si_;
    }
    
    /**
     * A wrapper of InventoryNavigator(rootFolder_).searchManagedEntity().
     *
     * @param type Search type like "VirtualMachine", "Datacenter", etc.
     * @param name Name of the entity.
     * @return Found managed entity in success or null in failure.
     */
    protected ManagedEntity searchManagedEntity(String type, String name)
    {
        ManagedEntity ret = null;
        try {
            ret = new InventoryNavigator
                (rootFolder_).searchManagedEntity(type, name);
        } catch (Exception e) { /* InvalidProperty, RuntimeFault, RemoteException */
            logger_.warning(Utility.toString(e));
            return null;
        }
        return ret;
    }

    /**
     * A wrapper of InventoryNavigator(rootFolder_).searchManagedEntities().
     *
     * @param type search type like "VirtualMachine", "Datacenter", etc.
     * @return List of found managed entities.
     *         Never return null, but a list with zero-size.
     */
    protected List<ManagedEntity> searchManagedEntities(String type)
    {
        List<ManagedEntity> ret = new LinkedList<ManagedEntity>();
        
        ManagedEntity[] mes;
        try {
            mes = new InventoryNavigator(rootFolder_).searchManagedEntities(type);
        } catch (Exception e) { /* InvalidProperty, RuntimeFault, RemoteException */
            return ret;
        }
        assert(mes != null);

        for (int i = 0; i < mes.length; i ++) {
            ret.add(mes[i]);
        }
        return ret;
    }

    /**
     * A wrapper of InventoryNavigator(rootFolder_).searchManagedEntities().
     *
     * @param type Search type like "VirtualMachine", "Datacenter", etc.
     * @param type Search name like "name", etc.
     * @return List of found managed entities.
     *         Never return null, but a list with zero-size.
     */
    protected List<ManagedEntity> searchManagedEntities(String type, String name)
    {
        List<ManagedEntity> ret = new LinkedList<ManagedEntity>();
        
        ManagedEntity[] mes;
        try {
            mes = new InventoryNavigator(rootFolder_).searchManagedEntities
                (new String[][] { {type, name}, }, true);
            
        } catch (Exception e) { /* InvalidProperty, RuntimeFault, RemoteException */
            return ret;
        }
        assert(mes != null);

        for (int i = 0; i < mes.length; i ++) {
            ret.add(mes[i]);
        }
        return ret;
    }

    /**
     * Generate ManagedObjectReference with type and moref string.
     *
     * @param entityType Entity type like VirtualMachine, Datacenter, etc.
     * @param morefStr Managed object reference like vm-100, etc.
     * @return The target ManagedObjectReference object.
     */
    protected ManagedObjectReference generateMoref(String entityType, String morefStr)
    {
        if (entityType == null || morefStr == null) {
            return null;
        }

        /* create managed object reference */
        ManagedObjectReference mor = new ManagedObjectReference();
        mor.setType(entityType);
        mor.setVal(morefStr);

        return mor;
    }

    /**
     * Generate VirtualMachine object from moref string.
     *
     * @param morefStr Moref of target virtual machine.
     * @return The target VirtualMachine object.
     */
    protected VirtualMachine generateVirtualMachineWithMoref(String morefStr)
    {
        if (si_ == null || morefStr == null) { return null; }

        ManagedObjectReference mor =
            generateMoref("VirtualMachine", morefStr);
        if (mor == null) { return null; }

        ManagedEntity vm =
            MorUtil.createExactManagedEntity(si_.getServerConnection(), mor);

        if (vm instanceof VirtualMachine) {
            return (VirtualMachine) vm; /* may be null */
        } else {
            return null;
        }
    }

    /**
     * Generate VirtualMachineSnapshot object from moref string.
     *
     * @param morefStr Moref of target snapshot.
     * @return The target VirtualMachineSnapshot object.
     */
    protected VirtualMachineSnapshot generateSnapshotWithMoref(String morefStr)
    {
        if (si_ == null || morefStr == null) { return null; }

        ManagedObjectReference mor =
            generateMoref("VirtualMachineSnapshot", morefStr);
        if (mor == null) { return null; }

        ManagedObject snap =
            MorUtil.createExactManagedObject(si_.getServerConnection(), mor);

        if (snap instanceof VirtualMachineSnapshot) {
            return (VirtualMachineSnapshot) snap; /* may be null */
        } else {
            return null;
        }
    }
}
