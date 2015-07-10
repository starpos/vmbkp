/**
 * @file
 * @brief VmbkpMain
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.control;

import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.util.LinkedList;
import java.util.logging.Logger;
import java.io.File;
import java.io.FileWriter;
import java.io.FilterOutputStream;
import java.io.FileOutputStream;

import com.cybozu.vmbkp.config.NotNormalFileException;

import com.cybozu.vmbkp.util.BackupMode;
import com.cybozu.vmbkp.util.Utility;
import com.cybozu.vmbkp.util.VmdkBitmap;
import com.cybozu.vmbkp.util.XmlIndent;
import com.cybozu.vmbkp.util.VmbkpOvf;
import com.cybozu.vmbkp.util.AdapterType;
import com.cybozu.vmbkp.util.VmdkInfo;
import com.cybozu.vmbkp.util.VmInfo;
import com.cybozu.vmbkp.util.LockTimeoutException;

import com.cybozu.vmbkp.soap.Connection;
import com.cybozu.vmbkp.soap.GlobalManager;
import com.cybozu.vmbkp.soap.VirtualMachineManager;
import com.cybozu.vmbkp.soap.VirtualMachineConfigManager;
import com.cybozu.vmbkp.soap.SnapshotManager;
import com.cybozu.vmbkp.soap.VirtualControllerManager;
import com.cybozu.vmbkp.soap.VirtualDiskManager;

import com.cybozu.vmbkp.profile.ConfigGlobal;
import com.cybozu.vmbkp.profile.ProfileAllVm;
import com.cybozu.vmbkp.profile.ConfigGroup;
import com.cybozu.vmbkp.profile.ProfileVm;
import com.cybozu.vmbkp.profile.ProfileGeneration;

import com.cybozu.vmbkp.control.VmdkBkp;
import com.cybozu.vmbkp.control.VmbkpLog;
import com.cybozu.vmbkp.control.VmbkpCommandLine;
import com.cybozu.vmbkp.control.RestoreInfo;
import com.cybozu.vmbkp.control.VmArchiveManager;

/**
 * @brief Top-level controller of the software.
 */
public class VmbkpMain
{
    private static VmbkpCommandLine cmdLine_;
    private static ConfigGlobal cfgGlobal_;
    private static ConfigGroup cfgGroup_;
    private static ProfileAllVm profAllVm_;
    private static GlobalManager gm_;
    private static List<String> targetVmMorefList_;
    
    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(VmbkpMain.class.getName());
    
    /**
     * Main method.
     */
    public static void main(String[] args)
    {
        /*
         * 1. Initialize.
         */
        if (initialize(args) == false) { return; }

        /*
         * 2. Get command and help.
         */
        VmbkpCommand cmd = cmdLine_.getCommand();
        if (cmd == VmbkpCommand.HELP) {
            cmdLine_.showHelpMessages();
            return;
        }
        
        /*
         * 3. Read config/profile files
         *    and setup global manager.
         */
        try {
            readConfigAndProfile();
            setupGlobalManager();

        } catch (NotNormalFileException e) {
            logException(e);
            System.err.println("File is not found.");
            return;
        } catch (Exception e) {
            logException(e);
            System.err.println("Some error occurs.");
            return;
        }
        
        /*
         * 4. Execute command.
         */
        try {
            setTargets(cmd);
            dispatch(cmd);
            
        } catch (Exception e) {
            logException(e);
            System.err.printf("Command execution failed: %s.\n", e.getMessage());
            return;
        } finally {
            finalizeApp();
        }
        
        /* Global and group config files is read only.
           So write() call for them is not required. */
    }

    /**
     * Initialize the application.
     */
    public static boolean initialize(String[] args)
    {
        /* Load log setting. */
        try {
            VmbkpLog.loadLogSetting();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Log setting initialiation failed.");
            return false;
        }

        /* Parse command line. */
        try {
            cmdLine_ = new VmbkpCommandLine(args);
        } catch (Exception e) {
            logger_.warning(Utility.toString(e));
            System.err.println("Try with --help option.");
            return false;
        }
        logger_.info(cmdLine_.toString());

        /* The first log. */
        logger_.info("\n" +
                     "----------------------------------------\n" +
                     "Vmbkp start.");
        
        /* Option check */
        if (cmdLine_.isValid() == false) {
            logger_.warning("Option check reports some problem.");
            cmdLine_.showHelpMessages(); return false;
        }

        return true;
    }

    /**
     * Finalize the application.
     */
    public static void finalizeApp()
    {
        assert gm_ != null;
        gm_.disconnect();
    }
    
    /**
     * Read global config and all vm profile files.
     */
    public static void readConfigAndProfile()
        throws NotNormalFileException, Exception
    {
        /* Read global config file */
        cfgGlobal_ = new ConfigGlobal(cmdLine_.getGlobalConfigPath());

        /* Initialize all vm profile. */
        profAllVm_ = initializeProfileAllVm();

        /* Read group config if available.  */
        String cfgGroupPath = cmdLine_.getGroupConfigPath();
        cfgGroup_ = null;
        if ((new File(cfgGroupPath)).isFile()) {
            cfgGroup_ = new ConfigGroup(cfgGroupPath, profAllVm_);
        }
    }

    /**
     * Setup global manager for vSphere management.
     * gm_ will be set.
     */
    public static void setupGlobalManager()
    {
        /* Information to connect to vsphere soap server. */
        String url = cfgGlobal_.getUrl();
        String username = cfgGlobal_.getUsername();
        String password = cfgGlobal_.getPassword();

        /* Prepare connection vSphere soap server.
           It will automatically connect the server
           when the connection is really required. */
        Connection conn = new Connection(url, username, password);

        /* Prepare global manager. */
        gm_ = new GlobalManager(conn);
    }

    /**
     * Set targetVmMorefList_.
     */
    public static void setTargets(VmbkpCommand cmd)
    {
        boolean isAvailableOnly = true;
        switch (cmd) {
        case STATUS:
        case RESTORE:
        case CLEAN:
            isAvailableOnly = false;
        }
        
        List<String> vmNameList = new LinkedList<String>();
        List<String> morefList = getAllTargetVmMorefList
            (isAvailableOnly, true /* isNonTemplateOnly */);
        for (String moref: morefList) {
            vmNameList.add(profAllVm_.getVmNameWithMoref(moref));
        }
        logger_.info("Targets(vmName): "+ Utility.toString(vmNameList));
        targetVmMorefList_ = morefList;
    }
     
    /**
     * Dispatch the specified command.
     */
    public static void dispatch(VmbkpCommand cmd)
        throws Exception
    {
        /* Execute command. */
        switch (cmd) {
        case UPDATE:  doUpdate();  break;
        case BACKUP:  doBackup();  break;
        case RESTORE: doRestore(); break;
        case STATUS:  doStatus();  break;
        case CHECK:   doCheck();   break;
        case DESTROY: doDestroy(); break;
        case CLEAN:   doClean();   break;
        case LIST:    doList();    break;
        case HELP: cmdLine_.showHelpMessages(); break;
        default: assert (false); break;
        }
    }

    /**
     * Read all vm profile or create if the file does not exist.
     * 
     * @return ProfileAllVm object.
     */
    public static ProfileAllVm initializeProfileAllVm()
        throws Exception
    {
        ProfileAllVm profAllVm;
        String profAllVmPath = cfgGlobal_.getProfileAllVmPath();
            
        boolean isExist = false;
        try {
            profAllVm = new ProfileAllVm(profAllVmPath);
            isExist = true;
            
        } catch (NotNormalFileException e) {
            logger_.info
                (String.format
                 ("%s is not found. create it.", profAllVmPath));
            profAllVm = new ProfileAllVm();
            isExist = false;
        }            

        if (isExist == false) {
            profAllVm.write(profAllVmPath);
        }

        /* Reload the profile to counteract the case where
           another processes wrote the profile
           during the above initialization. */
        try {
            profAllVm.lock(60);
            profAllVm.reload();

        } catch (Exception e) {
            throw e;
            
        } finally {
            profAllVm.unlock();
        }

        return profAllVm;
    }
    
    /**
     * Execute update command.
     */
    public static void doUpdate()
        throws Exception
    {
        if (targetVmMorefList_.isEmpty() == false) {
            logger_.warning("Target list is not required for doUpdate().");
            throw new Exception();
        }

        try {
            profAllVm_.lock(60);
            profAllVm_.reload();
            
            getAllVmInfoAndUpdate(profAllVm_);

        } catch (Exception e) {
            throw e;
        } finally {
            profAllVm_.unlock();
        }
    }

    /**
     * Execute backup command.
     */
    public static void doBackup()
        throws Exception
    {
        if (targetVmMorefList_.isEmpty()) {
            String msg = "Target list is required for backup command.";
            logger_.warning(msg);
            throw new Exception(msg);
        }

        BackupInfo backupInfo = new BackupInfo(cmdLine_);
        
        for (String vmMoref: targetVmMorefList_) {
            
            /* Target virtual machine */
            VirtualMachineManager vmm = gm_.searchVmWithMoref(vmMoref);
            VmInfo vmInfo = vmm.getVmInfo();
            VmArchiveManager vmArcMgr =
                new VmArchiveManager(cfgGlobal_, vmInfo);

            boolean ret = false;
            try {
                int timeoutSec = 60;
                if (backupInfo.isDryRun) {timeoutSec = 0;}
                
                vmArcMgr.lock(timeoutSec);
                vmArcMgr.reload();
                ret = backupVm(vmm, vmArcMgr, backupInfo);
                
            } catch (Exception e) {
                logException
                    (e, String.format
                     ("backupVm of %s failed.", vmMoref));
            } finally {
                vmArcMgr.unlock();
            }

            System.out.printf("BACKUP %s %s\n",
                              (ret ? "OK" : "NG"), vmInfo.toString());
        }
    }

    /**
     * Execute restore command.
     */
    public static void doRestore()
        throws Exception
    {
        if (targetVmMorefList_.size() != 1) {
            String msg = "Just one target is required for restore.";
            System.err.println(msg);
            throw new Exception(msg);
        }
        String targetVmMoref = targetVmMorefList_.get(0);

        RestoreInfo restoreInfo =
            new RestoreInfo(cmdLine_);

        VmInfo vmInfo = profAllVm_.makeVmInfoWithMoref(targetVmMoref);
        assert vmInfo != null;

        VmArchiveManager vmArcMgr =
            new VmArchiveManager(cfgGlobal_, vmInfo);
        
        try {
            int timeoutSec = 60;
            if (restoreInfo.isDryRun) {timeoutSec = 0;}
            
            vmArcMgr.lock(timeoutSec);
            vmArcMgr.reload();
            restoreVm(vmArcMgr, restoreInfo);

        } catch (BackupFailedException e) {
            System.out.printf("The generation is marked FAILED.");
            
        } catch (Exception e) {
            logException
                (e, String.format
                 ("restoreVm of %s failed.", targetVmMoref));
        } finally {
            vmArcMgr.unlock();
        }
    }

    /**
     * Execute check command.
     */
    public static void doCheck()
        throws Exception
    {
        CheckInfo checkInfo =
            new CheckInfo(cmdLine_);

        if (checkInfo.generationId >= 0 &&
            targetVmMorefList_.size() != 1) {
            
            String msg =
                "Just one target is required for check with " +
                "--generation option.";
            System.err.println(msg);
            throw new Exception(msg);
        }

        for (String targetVmMoref: targetVmMorefList_) {

            VmInfo vmInfo = profAllVm_.makeVmInfoWithMoref(targetVmMoref);
            assert vmInfo != null;

            VmArchiveManager vmArcMgr =
                new VmArchiveManager(cfgGlobal_, vmInfo);

            boolean isClean = false;
            try {
                int timeoutSec = 60;
                if (checkInfo.isDryRun) {timeoutSec = 0;}
            
                vmArcMgr.lock(timeoutSec);
                vmArcMgr.reload();
                isClean = checkGeneration(vmArcMgr, checkInfo);
            
            } catch (Exception e) {
                logException
                    (e, String.format
                     ("checkGeneration of %s failed.", targetVmMoref));
            } finally {
                vmArcMgr.unlock();
            }

            System.out.printf("CHECK %s %s: %d\n",
                              (isClean ? "OK" : "NG"),
                              vmInfo.toString(),
                              checkInfo.generationId);
        }
    }

    /**
     * Execute destroy command.
     */
    public static void doDestroy()
        throws Exception
    {
        if (targetVmMorefList_.size() != 1) {
            
            String msg =
                "Just one target is required for destroy command.";
            System.err.println(msg);
            throw new Exception(msg);
        }

        for (String targetVmMoref: targetVmMorefList_) {

            VirtualMachineManager vmm = gm_.searchVmWithMoref(targetVmMoref);
            VmInfo info = vmm.getVmInfo();
            if (gm_.destroyVm(vmm)) {
                System.out.printf
                    ("Destroy %s(%s) succeeded.\n",
                     info.getName(), info.getMoref());
            } else {
                throw new Exception("destroy virtual machine failed.");
            }
        }
    }

    /**
     * Execute clean command.
     */
    public static void doClean()
        throws Exception
    {
        CleanInfo cleanInfo =
            new CleanInfo(cmdLine_);
        
        if (cleanInfo.isAll && cleanInfo.isForce &&
            targetVmMorefList_.size() != 1) {
            
            String msg =
                "Just one target is required for clean --all --force command.";
            System.err.println(msg);
            throw new Exception(msg);
        }

        for (String targetVmMoref: targetVmMorefList_) {

            VmInfo vmInfo = profAllVm_.makeVmInfoWithMoref(targetVmMoref);
            assert vmInfo != null;

            boolean isExist =
                VmArchiveManager.isExistSucceededGeneration(cfgGlobal_, vmInfo);
            boolean isEmptyArchive =
                VmArchiveManager.isEmptyArchive(cfgGlobal_, vmInfo);
            
            if (! isExist && ! isEmptyArchive) {
                String msg = String.format
                    ("Archive of %s does not exist.", vmInfo.toString());
                logger_.warning(msg);
                System.err.println(msg);
                continue;
            }

            boolean isSucceeded = false;
            if (cleanInfo.isAll || isEmptyArchive) {

                try {
                    deleteVmArchive(vmInfo, cleanInfo);
                    isSucceeded = true;
                } catch (Exception e) {
                    String msg = String.format
                        ("Clean all of %s failed.", targetVmMoref);
                    logException(e, msg);
                    System.err.println(msg);
                }
            } else {

                VmArchiveManager vmArcMgr =
                    new VmArchiveManager(cfgGlobal_, vmInfo);
                try {
                    int timeoutSec = 1;
                    if (cleanInfo.isDryRun) { timeoutSec = 0; }

                    vmArcMgr.lock(timeoutSec);
                    vmArcMgr.reload();
                    deleteFailedGenerations(vmArcMgr, cleanInfo);
                    isSucceeded = true;

                } catch(Exception e) {
                    String msg = String.format
                        ("Clean %s failed.", targetVmMoref);
                    logException(e, msg);
                    System.err.println(msg);
                } finally {
                    vmArcMgr.unlock();
                }
            }
            System.out.printf("CLEAN %s %s.\n",
                              vmInfo.toString(),
                              (isSucceeded ? "succeeded" : "failed"));
        }
    }

    /**
     * Execute list command.
     */
    public static void doList()
        throws Exception
    {
        ListInfo listInfo = new ListInfo(cmdLine_);

        /* 1. get all vm moref list. */
        List<String> morefs = profAllVm_.getAllVmMorefs();
        List<String> tlist;

        /* 2. existance filter. */
        switch (listInfo.exist) {
        case ListInfo.EXIST_YES:
            tlist = new LinkedList<String>();
            for (String moref: morefs) {
                if (profAllVm_.isAvailableWithMoref(moref)) {
                    tlist.add(moref);
                }
            }
            morefs = tlist;
            break;
        case ListInfo.EXIST_NO:
            tlist = new LinkedList<String>();
            for (String moref: morefs) {
                if (! profAllVm_.isAvailableWithMoref(moref)) {
                    tlist.add(moref);
                }
            }
            morefs = tlist;
            break;
        case ListInfo.EXIST_BOTH:
        default:
            /* Do nothing */
        }

        /* 3. mtime/mmin filter. */
        if (listInfo.isMtime || listInfo.isMmin) {
            tlist = new LinkedList<String>();
            
            for (String moref: morefs) {
                VmInfo vmInfo = profAllVm_.makeVmInfoWithMoref(moref);
                if (VmArchiveManager.isExistSucceededGeneration(cfgGlobal_, vmInfo)) {
                    VmArchiveManager vmArcMgr =
                        new VmArchiveManager(cfgGlobal_, vmInfo);

                    long tsGen = vmArcMgr.getTimestampMsOfLatestGeneration();
                    long tsNow = Calendar.getInstance().getTimeInMillis();
                    
                    if (listInfo.isSatisfyTime(tsGen, tsNow)) {
                        tlist.add(moref);
                    }
                }
            }
            morefs = tlist;
        }

        /* print filtered result. */
        for (String moref: morefs) {
            System.out.println(moref);
        }
        System.err.println(listInfo.toString());
    }

    /**
     * Execute status command.
     */
    public static void doStatus()
        throws Exception
    {
        if (targetVmMorefList_.isEmpty()) {
            String errMsg = "Targets are required.";
            logger_.warning(errMsg);
            System.err.println(errMsg);
            throw new Exception(errMsg);
        }

        StatusInfo statusInfo = new StatusInfo(cmdLine_);
        
        for (String vmMoref: targetVmMorefList_) {

            VmInfo vmInfo = profAllVm_.makeVmInfoWithMoref(vmMoref);
            if (vmInfo  == null) {
                logger_.warning(vmMoref + " not found.");
                continue;
            }

            /* Check archive existance. */
            if (! VmArchiveManager.isExistSucceededGeneration(cfgGlobal_, vmInfo)) {
                System.out.printf("[%s][%s] ##########_NO_ARCHIVE_##########\n",
                                  vmInfo.getMoref(), vmInfo.getName());
                continue;
            }

            /* Try to load archive metadata. */
            VmArchiveManager vmArcMgr;
            try {
                vmArcMgr = new VmArchiveManager(cfgGlobal_, vmInfo);
            } catch (Exception e) {
                logException(e);
                System.out.printf("[%s][%s] ##########_ERROR_##########\n",
                                  vmInfo.getMoref(), vmInfo.getName());
                continue;
            }

            /* Get status information from the metadata. */
            try {
                vmArcMgr.lock(0);
                vmArcMgr.reload();

                boolean isAvailable = profAllVm_.isAvailableWithMoref(vmMoref);
                System.out.println
                    (vmArcMgr.getStatusString(statusInfo, isAvailable));
                
            } catch (LockTimeoutException e) {
                logException(e, "lock timeout" + vmInfo.toString());
                System.out.printf("[%s][%s] ##########_LOCKED_##########\n",
                                  vmInfo.getMoref(), vmInfo.getName());
            } catch (Exception e) {
                logException
                    (e, String.format
                     ("Reload profile of %s failed.", vmInfo.toString()));
            } finally {
                vmArcMgr.unlock();
            }
        }
    }

    /**
     * Get information of all virtual machines
     * and put them to the vmbkp_all_vm.conf file.
     */
    public static void getAllVmInfoAndUpdate(ProfileAllVm profAllVm)
        throws Exception
    {
        /* set to false of availability of all virtual machines. */
        profAllVm.setAllAvailabilityToFalse();

        /* put all available virtual machine to the config. */
        List<VirtualMachineManager> vmmList = gm_.getAllVmList();
        for (VirtualMachineManager vmm : vmmList) {
            profAllVm.addVmEntry(vmm.getVmInfo(), Calendar.getInstance(),
                                 vmm.getConfig().isTemplate());
        }

        /* write config file. */
        profAllVm.write();
    }

    /**
     * Get target list of moref of virtual machine
     * when the group is specified, it will be replaced to its members.
     *
     * @param isAvailableOnly Specify true when you get only available vm in vsphere.
     * @param isNonTemplateOnly Specify true when you get only non-template vm in vsphere.
     * @return Never return null (but empty list).
     */
    private static List<String> getAllTargetVmMorefList
        (boolean isAvailableOnly, boolean isNonTemplateOnly)
    {
        List<String> morefs = new LinkedList<String>();
        if (cfgGroup_ == null) {
            for (String name: cmdLine_.getTargets()) {

                if (name.equals("all")) {
                    morefs.addAll(profAllVm_.getAllVmMorefs());
                
                } else if (profAllVm_.isExistWithName(name)) {
                    String vmMoref = profAllVm_.getVmMorefWithName(name);
                    assert vmMoref != null;
                    morefs.add(vmMoref);
                    
                } else if (profAllVm_.isExistWithMoref(name)) {
                    morefs.add(name);

                } else {
                    logger_.warning
                        (String.format("Target %s not found.", name));
                }

            }
        } else {
            for (String name: cmdLine_.getTargets()) {
                morefs.addAll(cfgGroup_.getAllVmMorefList(name));
                
                if (morefs.isEmpty()) {
                    logger_.warning
                        (String.format
                         ("%s is not either group name, vm name, or vm moref.",
                          name));
                }
            }
        }

        List<String> ret1 = null;
        List<String> deduped = Utility.dedupKeepingOrder(morefs);
        if (isAvailableOnly) {
            ret1 = profAllVm_.filterAvailable(deduped);
        } else {
            ret1 = deduped;
        }
        List<String> ret = null;
        if (isNonTemplateOnly) {
            ret = profAllVm_.filterNonTemplate(ret1);
        } else {
            ret = ret1;
        }

        assert ret != null;
        logger_.info("Targets(moref): " + Utility.toString(ret));
        return ret;
    }

    /**
     * Write stack trace of the exception to log.
     */
    private static void logException(Exception e)
    {
        logException(e, null);
    }
    
    /**
     * Write stack trace of the exception to log.
     */
    private static void logException(Exception e, String msg)
    {
        String errMsg = Utility.toString(e);
        String msgStr = (msg == null ? "" : msg + "\n");
        logger_.warning(msgStr + errMsg);
    }
    
    /**
     * Backup the specified virtual machine.
     *
     * @return True in success.
     */
    public static boolean backupVm
        (VirtualMachineManager vmm, VmArchiveManager vmArcMgr,
         BackupInfo backupInfo)
        throws Exception
    {
        logger_.info("backupVm() start.");
        boolean ret;
        
        /* Print and log start message. */
        String msg;
        msg = String.format
            ("Backup \"%s\" (%s) start.", vmm.getName(), vmm.getMoref());
        System.out.println(msg); logger_.info(msg);
        msg = backupInfo.toString();
        System.out.println(msg); logger_.info(msg);

        if (backupInfo.isDryRun) {
            msg = "Backup ends cause dryrun.";
            System.out.println(msg); logger_.info(msg);
            return true;
        }

        /* Decide to use snapshot or not.
           Currently not used. */
        final boolean isUseSnapshot = ! vmm.getConfig().isTemplate();
        
        /* Check the vm is marked as template. */
        if (vmm.getConfig().isTemplate()) {
            msg = "Template backup is not supported.";
            throw new Exception(msg);
        }
        
        /*
         * Create snapshot
         */
        Calendar cal = Calendar.getInstance();
        String snapName = generateSnapshotName(cal);
        createSnapshot(vmm, snapName);
        msg = String.format
            ("create snapshot name %s succeeded.", snapName);
        System.out.println(msg); logger_.info(msg);
        
        /*
         * Make generation profile.
         */
        SnapshotManager snap = vmm.getCurrentSnapshot();
        logger_.info(String.format("snapshot: %s", snap.getName()));
        List<VmdkInfo> vmdkInfoList = snap.getConfig().getAllVmdkInfo();
        /* debug */
        msg = String.format("There are %d disks.", vmdkInfoList.size());
        System.out.println(msg); logger_.info(msg);
        for (VmdkInfo vmdkInfo : vmdkInfoList) {
            logger_.info(vmdkInfo.toString());
        }
        /* Prepare generation profile. */
        ProfileGeneration profGen =
            vmArcMgr.prepareNewGeneration
            (snap.getVmInfo(), snap.getSnapInfo(), vmdkInfoList, cal,
             backupInfo.isGzip);
        vmArcMgr.setTargetGeneration(profGen);

        /*
         * Export ovf and delete disk information from the ovf.
         */
        String ovfFilePath = profGen.getOvfPath();
        if (ovfFilePath == null) {
            throw new Exception("ovfFilePath is null");
        }
        ret = exportOvfAndSave(vmm, ovfFilePath);
        if (ret == false) {
            throw new Exception
                (String.format("ovf export failed (%s).", ovfFilePath));
        }

        /*
         * Dump each vmdk file.
         */
        boolean isAllVmdkDumpSucceeded = true;
        for (VmdkInfo vmdkInfo : vmdkInfoList) {

            int diskId = profGen.getDiskIdWithUuid(vmdkInfo.uuid_);
            
            if (backupInfo.isNoVmdk) {
                profGen.setVmdkdumpResult(diskId, true);
            } else {
                if (profGen.isIndependentDisk(diskId)) {
                    msg = String.format
                        ("Dump vmdk %s skipped (independent disk)",
                         vmdkInfo.uuid_);
                    System.out.println(msg); logger_.info(msg);
                    ret = true;
                } else {
                    ret = backupVmdk
                        (vmm, vmArcMgr, snap, vmdkInfo, backupInfo);
                    msg = String.format("Dump vmdk %s %s.",
                                        vmdkInfo.uuid_,
                                        (ret ? "succeeded" : "failed"));
                    System.out.println(msg); logger_.info(msg);
                }
                isAllVmdkDumpSucceeded &= ret;

                /* Reconnect and reload if disconnected. */
                try {
                    String vmMoref = vmm.getMoref();
                    String snapMoref = snap.getMoref();
                    gm_.connect();
                    vmm = gm_.searchVmWithMoref(vmMoref);
                    if (vmm == null) { throw new Exception("vmm is null."); }
                    snap = vmm.searchSnapshotWithMoref(snapMoref);
                    if (snap == null) { throw new Exception("snap is null."); }
                } catch (Exception e) {
                    logger_.warning("Connect failed or not found vm or snap.");
                    throw e;
                }
            }

            vmArcMgr.save();
        }

        /* Finalize and check */
        ret = vmArcMgr.finalizeBackup(isAllVmdkDumpSucceeded);
        if (ret == false) {
            logger_.warning("Backup finalization failed.");
        }
        vmArcMgr.save();

        /* Delete the snapshot. */
        assert snapName != null;
        deleteSnapshot(vmm, snapName);
        msg = String.format("Delete snapshot %s succeeded.", snapName);
        System.out.println(msg); logger_.info(msg);

        logger_.info("backupVm() end.");
        return ret;
    }

    /**
     * Determine backup mode.
     *
     * @param reuestMode Request backup mode.
     * @param isDiff True if differential backup is available.
     * @param isInc True if incremental backup is available.
     * @return Determined bacukp mode.
     */
    private static BackupMode determineBackupMode
        (BackupMode requestMode, boolean isDiff, boolean isInc)
    {
        BackupMode mode = BackupMode.UNKNOWN;

        /* --mode was specified in command line */
        if (requestMode != BackupMode.UNKNOWN) { 
            if ((isInc   && requestMode == BackupMode.INCR) ||
                (isDiff  && requestMode == BackupMode.DIFF) ||
                (           requestMode == BackupMode.FULL)) { 
                mode = requestMode;
            }
        }
        /* If --mode is not specified, use suitable mode. */
        if (mode == BackupMode.UNKNOWN) {
            if (isInc)       { mode = BackupMode.INCR; }
            else if (isDiff) { mode = BackupMode.DIFF; }
            else             { mode = BackupMode.FULL; }
        }
        assert mode != BackupMode.UNKNOWN;
        return mode;
    }

    /**
     * Backup each vmdk file.
     */
    public static boolean backupVmdk
        (VirtualMachineManager vmm,
         VmArchiveManager vmArcMgr,
         SnapshotManager snap,
         VmdkInfo vmdkInfo,
         BackupInfo info)       /* Use isSan and mode */
    {
        boolean ret = false;
        boolean hasChangedBlocks = false;

        ProfileGeneration profGen = vmArcMgr.getTargetGeneration();

        int diskId = profGen.getDiskIdWithUuid(vmdkInfo.uuid_);

        /* Check available backup mode. */
        boolean isDiff = vmArcMgr.canExecDiffBackup(vmdkInfo.uuid_);
        boolean isInc =  vmArcMgr.canExecIncrBackup(vmdkInfo.uuid_);

        profGen.setIsChanged(diskId, true);

        /* Get changed block info and save if required. */
        if (isInc) {
            /* profGen.setIsChange(diskId, false) will be called inside it. */
            hasChangedBlocks = getAndSaveChangedBlocksOfDisk
                (snap, vmArcMgr, vmdkInfo, diskId);
        }

        /* If ctkEnabled not available, turn off incr mode */
        if (! hasChangedBlocks) {
            isInc = false;
        }

        /* Dump archives with vmdkbkp tool. */
        BackupMode mode = determineBackupMode(info.mode, isDiff, isInc);
        profGen.setBackupMode(diskId, mode);

        String msg = String.format("Available modes: full%s%s. Used mode: %s.",
                                   (isDiff ? ", diff" : ""),
                                   (isInc ? ", incr" : ""),
                                   mode.toString());
        System.out.println(msg); logger_.info(msg);

        profGen.setDumpBeginTimestamp(diskId);
        if (mode == BackupMode.INCR &&
            profGen.isChanged(diskId) == false) {
            
            logger_.info("The vmdk is not changed at all.");
            vmArcMgr.registerLazyTaskMovePrevDumpAndDigest(diskId);
            ret = true;
        } else {
            /* Soap connection is not required during running vmdkbkp.
               It may take a long time and soap timeout will occur,
               therefore we disconnect and re-connect
               after vmdkbkp has done.
               VirtualMachineManger and SnapshotManager must be reload. */
            gm_.disconnect();
            
            ret = VmdkBkp.doDump
                (mode, vmm.getMoref(), vmArcMgr, diskId, info.isSan);
        }
        profGen.setDumpEndTimestamp(diskId);
            
        profGen.setVmdkdumpResult(diskId, ret);
            
        if (profGen.isVmdkdumpSucceeded(diskId) &&
            (mode == BackupMode.DIFF ||
             (mode == BackupMode.INCR && profGen.isChanged(diskId)))) {
                    
            vmArcMgr.registerLazyTaskDelPrevDump(diskId);
        }

        return ret;
    }

    /**
     * Export ovf, delete disk information in the ovf,
     * and save the ovf as a file.
     *
     * @param vmm VirtualMachineManager to export.
     * @param outOvfPath Ovf path to be saved.
     * @return true in success, false in failure.
     */
    public static boolean exportOvfAndSave
        (VirtualMachineManager vmm, String outOvfPath)
    {
        try {
            String ovfStr1 = vmm.exportOvf();
            VmbkpOvf ovf = new VmbkpOvf(ovfStr1);

            /* Export original ovf. This is just for debug. */
            {
                XmlIndent xmli = new XmlIndent(ovf.toString());
                xmli.fixIndent();
                FileWriter fw = new FileWriter(outOvfPath + ".orig");
                fw.write(xmli.toString());
                fw.close();
            }

            /* Delete scsi controller and disk information. */
            ovf.deleteFilesInReferences();
            ovf.deleteDisksInDiskSection();
            Set<String> ctrlIdSet = ovf.deleteDiskDevicesInHardwareSection();
            ovf.deleteControllerDevicesWithoutChildInHardwareSection(ctrlIdSet);

            /* Delete mounted cd-rom information. */
            ovf.deleteMountedCdromInfoInHardwareSection();

            /* Fix indent of xml data for human's eary read. */
            XmlIndent xmli = new XmlIndent(ovf.toString());
            xmli.fixIndent();
            String ovfStr2 = xmli.toString();
            if (ovfStr2 == null) {
                logger_.warning("ovf is null.");
                return false;
            }

            /* Write the ovf file. */
            FileWriter fw = new FileWriter(outOvfPath);
            fw.write(ovfStr2);
            fw.close();
            
        } catch (Exception e) {
            logger_.warning(Utility.toString(e));
            return false;
        }
        
        return true;
    }

    /**
     * Get and save changed blocks of vmdk file.
     *
     * @param snap Target snapshot.
     * @param vmArcMgr Vm profiles manager.
     * @param vmdkInfo Target vmdk.
     * @param diskId Target disk id in the profGen.
     * @return true in success, or false.
     */
    public static boolean getAndSaveChangedBlocksOfDisk
        (SnapshotManager snap,
         VmArchiveManager vmArcMgr,
         VmdkInfo vmdkInfo,
         int diskId)
    {
        String prevChangeId = vmArcMgr.getPrevChangeId(diskId);
        assert prevChangeId != null && (prevChangeId.equals("*") == false);
        
        VmdkBitmap bmp = snap.getChangedBlocksOfDisk(vmdkInfo, prevChangeId);
        if (bmp == null) { return false; }

        ProfileGeneration profGen = vmArcMgr.getTargetGeneration();

        try {
            /* Save the changed bitmap file */
            FilterOutputStream fos =
                new FilterOutputStream
                (new FileOutputStream
                 (profGen.getBmpInPath(diskId)));
            bmp.writeTo(fos);
            fos.close();

            /* Check whether the bitmap is all zero or not.
               (All zero means the vmdk does not change at all.) */
            profGen.setIsChanged(diskId, bmp.isAllZero() == false);
            
        } catch (Exception e) {
            logger_.warning(Utility.toString(e));
            return false;
        }
        return true;
    }

    /**
     * Restore a virtual machine from an archive.
     *
     * @param vmArcMgr Vm profiles manager.
     *
     * @param restoreInfo structure.
     *        1. genId generationId to restore.
     *          Specify -1 to select the latest generation.
     *        2. newVmName The name of new virtual machine.
     *          Specify null to use the same name as in the archive.
     *        3. hostName VMware ESX(i) host name to restore.
     *          This can be null and default host will be used.
     *        4. datastoreName Datastore name to restore.
     *          This can be null and default datastore will be used.
     *          Currently all vm configuration data and vmdk files
     *          will be created in the same directory in the datastore.
     *          The directory will be automatically decided by vSphere.
     */
    public static void restoreVm
        (VmArchiveManager vmArcMgr, RestoreInfo restoreInfo)
        throws Exception
    {
        /* 1. Initialize variables. */
        int genId = restoreInfo.generationId;
        String newVmName = restoreInfo.newVmName;
        String hostName = restoreInfo.hostName;
        String datastoreName = restoreInfo.datastoreName;
        String folderName = restoreInfo.folderName;
        boolean ret = false;
        
        /* 2. Log the command. */
        logger_.info("restoreVm() start.");
        String msg;
        msg = String.format
            ("Restore target: \"%s\" (%s).",
             vmArcMgr.getName(), vmArcMgr.getMoref());
        System.out.println(msg); logger_.info(msg);

        msg = restoreInfo.toString();
        System.out.println(msg); logger_.info(msg);
        
        /* 2. Decide generation and load ProfileGeneration data. */
        ProfileGeneration profGen = 
            vmArcMgr.loadProfileGeneration(genId);
        if (profGen == null) {
            throw new Exception
                ("ProfileGeneration is null.");
        }
        if (profGen.isSucceeded() == false) {
            throw new Exception
                ("The specified generation is marked FAILED.");
        }
        assert profGen != null;
        vmArcMgr.setTargetGeneration(profGen);
        
        /* 3. Decide the name of the new virtual machine */
        if (newVmName == null) {
            newVmName = vmArcMgr.getName();
            logger_.info
                (String.format
                 ("Use default name for new VM: %s.", newVmName));
        }
        if (newVmName == null) {
            throw new Exception("Could not decide the name of new VM.");
        }

        /* 4. Generate controller-disk mapping data
              from the ProfileGeneration
              as a List<VirtualControllerManager> */
        List<VirtualControllerManager> vcmList =
            vmArcMgr.generateVirtualControllerManagerList(datastoreName);
        logger_.info
            (vmArcMgr.toStringVirtualControllerManagerList(vcmList));

        /* Finish if --dryrun option is specified. */
        if (restoreInfo.isDryRun) {
            msg = "Restore ends cause dryrun.";
            System.out.println(msg); logger_.info(msg);
            return;
        }
        
        /* 5. Import ovf without disks. */
        String ovfPath = profGen.getOvfPath();
        if (ovfPath == null) {
            throw new Exception
                (String.format("ovfPath %s is null.", ovfPath));
        }
        assert (ovfPath != null && newVmName != null);

        if (hostName == null) {
            hostName = gm_.getDefaultHostName();
            if (hostName == null) {
                throw new Exception("hostname is null.");
            }
        }
        if (datastoreName == null) {
            datastoreName = gm_.getDefaultDatastoreName(hostName);
        }

        String morefOfNewVm = 
            gm_.importOvf(ovfPath, newVmName, hostName, datastoreName, folderName);
        assert (morefOfNewVm != null);
        msg = "import succeeded.";
        System.out.println(msg); logger_.info(msg);

        /* 6. Add contorollers and empty disks. */
        VirtualMachineManager vmm = gm_.searchVmWithMoref(morefOfNewVm);
        if (vmm == null) {
            throw new Exception("Could not find newly created vm.");
        }
        vmm.addEmptyDisks(vcmList);
        vmm.reload(); /* reload to get information of added disks
                         for the next step. */
        {
            int numDisks = 0;
            for (VirtualControllerManager vcm : vcmList) {
                numDisks += vcm.getNumOfDisks();
            }
            msg = String.format("add empty %d disks succeeded.", numDisks);
        }
        System.out.println(msg); logger_.info(msg);

        /* 7. Get VmdkInfo list for remote disk path of
              the newly created vmdk files,
              and get the corresponding diskId to restore.
              Then call vmdkbkp command to restore each vmdk file. */
        if (restoreInfo.isNoVmdk) {
            msg = String.format
                ("Disks are not restored because specified --novmdk option.");
            System.out.println(msg); logger_.info(msg);
            
        } else {
            /* Create snapshot and get moref of the snapshot. */
            Calendar cal = Calendar.getInstance();
            String snapName = generateSnapshotName(cal);
            createSnapshot(vmm, snapName);
            msg = String.format
                ("create snapshot name %s succeeded.", snapName);
            System.out.println(msg); logger_.info(msg);
                
            SnapshotManager snap = vmm.getCurrentSnapshot();
            if (snap == null || snapName.equals(snap.getName()) == false) {
                throw new Exception("Could not get snapshot.");
            }
            String snapMoref = snap.getMoref();
            
            List<VmdkInfo> vmdkInfoList = snap.getConfig().getAllVmdkInfo();
            msg = String.format("Restore %d disks.", vmdkInfoList.size());
            System.out.println(msg); logger_.info(msg);

            
            gm_.disconnect(); /* to avoid soap timeout. */
            for (VmdkInfo vmdkInfo : vmdkInfoList) {

                int tgtDiskId = vmArcMgr.getTargetDiskId(vmdkInfo);
                if (tgtDiskId < 0) {
                    /* This vmdk restore failed. */
                    logger_.warning
                        ("diskId invalid.\n" + vmdkInfo.toString());
                    continue;
                }

                /* Restore each remote vmdk file. */
                ret = VmdkBkp.doRestore(vmArcMgr, tgtDiskId,
                                        morefOfNewVm, vmdkInfo.name_,
                                        snapMoref, restoreInfo.isSan);
                if (ret) {
                    /* This vmdk restore succeeded. */
                    msg = String.format
                        ("Restoring vmdk %d succeeded.", tgtDiskId);
                    System.out.println(msg); logger_.info(msg);
                
                } else {
                    /* This vmdk restore failed. */
                    msg = "Restoring vmdk %d failed.\n" + vmdkInfo.toString();
                    System.out.println(msg); logger_.info(msg);
                }
            }

            /* Reconnect and reload if disconnected. */
            try {
                gm_.connect();
                vmm = gm_.searchVmWithMoref(morefOfNewVm);
                if (vmm == null) { throw new Exception("vmm is null."); }
            } catch (Exception e) {
                logger_.warning("Connect failed or not found vm.");
                throw e;
            }

            /* Revert the vm to the snapshot. */
            assert snapName != null;
            revertToSnapshot(vmm, snapName);
            msg = String.format("Revert to snapshot %s succeeded.", snapName);
            System.out.println(msg); logger_.info(msg);

            /* Delete the snapshot. */
            assert snapName != null;
            deleteSnapshot(vmm, snapName);
            msg = String.format("Delete snapshot %s succeeded.", snapName);
            System.out.println(msg); logger_.info(msg);
        }

        /* No need to re-connect the soap server */
        logger_.info("restoreVm() end.");
    }

    /**
     * Check the archives of the specified vm and its generation.
     *
     * @param vmArcMgr Virtual machine archive manager.
     * @param checkInfo option information.
     * @return True when the archive is valid, or false.
     */
    public static boolean checkGeneration
        (VmArchiveManager vmArcMgr, CheckInfo checkInfo)
        throws BackupFailedException, Exception
    {
        /* 1. Initialize variables. */
        int genId = checkInfo.generationId;
        
        /* 2. Log the command. */
        logger_.info("checkGeneration() start.");
        String msg;
        msg = String.format
            ("Check target: \"%s\" (%s).",
             vmArcMgr.getName(), vmArcMgr.getMoref());
        System.out.println(msg); logger_.info(msg);

        msg = checkInfo.toString();
        System.out.println(msg); logger_.info(msg);
        
        /* 3. Decide generation and load ProfileGeneration data. */
        ProfileGeneration profGen = null;
        try {
            profGen = vmArcMgr.loadProfileGeneration(genId);
        } catch (BackupFailedException e) {
            System.out.printf("Generation %d is marked FAILED.\n", genId);
            return false;
        }
        if (profGen == null) {
            throw new Exception
                ("ProfileGeneration is null.");
        }
        if (profGen.isSucceeded() == false) {
            throw new Exception
                ("The specified generation is marked FAILED.");
        }
        assert profGen != null;
        vmArcMgr.setTargetGeneration(profGen);

        /* Check vmdk archives. */
        List<Integer> diskIdList = profGen.getDiskIdList();

        boolean ret = true;
        for (Integer diskIdI: diskIdList) {
            if (diskIdI == null) {
                logger_.warning("diskIdList contains null.");
                continue;
            }
            int diskId = diskIdI.intValue();
            if (profGen.isVmdkdumpSucceeded(diskId) == false) {
                System.out.printf
                    ("Dump of disk %d is marked FAILED.\n", diskId);
                ret &= false;
                continue;
            }
            ret &= VmdkBkp.doCheck(vmArcMgr, diskId, checkInfo.isDryRun);
        }
        return ret;
    }

    /**
     * Delete archive directory of the specified vm.
     *
     * @param vmInfo virtual machine info.
     * @param cleanInfo option info.
     */
    public static void deleteVmArchive(VmInfo vmInfo, CleanInfo cleanInfo)
        throws Exception
    {
        boolean isEmptyArchive = VmArchiveManager.isEmptyArchive(cfgGlobal_, vmInfo);
        assert (cleanInfo.isAll || isEmptyArchive);
        if (cleanInfo.isDryRun) { return; }
        
        if (profAllVm_.isAvailableWithMoref(vmInfo.getMoref())
            && (! cleanInfo.isForce && ! isEmptyArchive)) {

            String msg = String.format("Vm %s still exists in vSphere.", vmInfo.toString());
            System.err.println(msg);
            throw new Exception(msg);
        }

        String backupDirPath =
            cfgGlobal_.getDefaultVmDirectory(vmInfo.getMoref());

        if (! Utility.deleteDirectoryRecursive(new File(backupDirPath))) {
            String msg = String.format("Delete directory %s failed.", backupDirPath);
            throw new Exception(msg);
        }
    }
    
    /**
     * Delete failed generations of the specified vm,
     * removing failed generation's directory.
     *
     * @param vmArcMgr Virtual machine archive manager.
     * @param cleanInfo option information.
     */
    public static void deleteFailedGenerations
        (VmArchiveManager vmArcMgr, CleanInfo cleanInfo)
        throws Exception
    {
        List<Integer> list = vmArcMgr.getFailedGenerationList();
        String genListStr = Utility.toString(Utility.toStringList(list));

        String msg;
        if (genListStr.isEmpty()) {
            msg = "No failed generations.";
        } else {
            msg = String.format("Try to delete generations: %s.", genListStr);
        }
        logger_.info(msg);
        System.out.println(msg);
        
        if (! cleanInfo.isDryRun && ! genListStr.isEmpty()) {
            vmArcMgr.deleteFailedGenerations();
            vmArcMgr.save();
        }
    }

    /**
     * Generate snapshot name with the specified calendar.
     *
     * @return Snapshot name.
     */
    public static String generateSnapshotName(Calendar cal)
    {
        String snapName = null;
        snapName = String.format
            ("VMBKP_%d-%02d-%02d_%02d:%02d:%02d",
             cal.get(Calendar.YEAR),
             cal.get(Calendar.MONTH) + 1,
             cal.get(Calendar.DAY_OF_MONTH),
             cal.get(Calendar.HOUR_OF_DAY),
             cal.get(Calendar.MINUTE),
             cal.get(Calendar.SECOND));
        return snapName;
    }

    /**
     * Task type for snapshot. Internal use only.
     */
    private enum TaskType { CREATE, DELETE, REVERT, NONE }
    
    /**
     * Operation for a snapshot.
     */
    private static void operateSnapshotDetail(TaskType type,
                                              VirtualMachineManager vmm, String snapName)
        throws Exception
    {
        VmdkBkp.lock(cfgGlobal_);
        try {
            switch (type) {
            case CREATE:
                if (! vmm.createSnapshot(snapName)) {
                    String msg = String.format("Create snapshot %s failed.", snapName);
                    throw new Exception(msg);
                }
                break;
            
            case DELETE:
                if (! vmm.deleteSnapshot(snapName)) {
                    String msg = String.format("Delete snapshot %s failed.", snapName);
                    throw new Exception(msg);
                }
                break;
            
            case REVERT:
                if (! vmm.revertToSnapshot(snapName)) {
                    String msg = String.format("Reverting to snapshot %s failed.", snapName);
                    throw new Exception(msg);
                }
                break;
            
            default:
                assert false;
            }
        } finally {
            VmdkBkp.unlock();
        }
    }

    
    /**
     * Create snapshot (snapshot name is created by timestamp).
     *
     * @param vmm Virtual machine manager.
     */
    public static void createSnapshot(VirtualMachineManager vmm, String snapName)
        throws Exception
    {
        operateSnapshotDetail(TaskType.CREATE, vmm, snapName);
    }

    /**
     * Delete snapshot.
     */
    public static void deleteSnapshot(VirtualMachineManager vmm, String snapName)
        throws Exception
    {
        operateSnapshotDetail(TaskType.DELETE, vmm, snapName);
    }

    /**
     * Revert to a snapshot.
     *
     * @param vmm Virtual machine manager.
     * @param snapName snapshot name.
     */
    public static void revertToSnapshot(VirtualMachineManager vmm, String snapName)
        throws Exception
    {
        operateSnapshotDetail(TaskType.REVERT, vmm, snapName);
    }
}
