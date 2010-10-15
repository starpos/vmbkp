/**
 * @file
 * @brief VmArchiveManager, LazyTask, LazyTaskId
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.control;

import java.util.Calendar;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.io.File;
import java.io.IOException;

import com.cybozu.vmbkp.config.NotNormalFileException;
import com.cybozu.vmbkp.config.ParseException;
import com.cybozu.vmbkp.config.FormatString;
import com.cybozu.vmbkp.config.FormatInt;

import com.cybozu.vmbkp.profile.ConfigGlobal;
import com.cybozu.vmbkp.profile.ProfileVm;
import com.cybozu.vmbkp.profile.ProfileGeneration;

import com.cybozu.vmbkp.util.VmInfo;
import com.cybozu.vmbkp.util.SnapInfo;
import com.cybozu.vmbkp.util.VmdkInfo;
import com.cybozu.vmbkp.util.AdapterType;
import com.cybozu.vmbkp.util.Utility;

import com.cybozu.vmbkp.soap.SnapshotManager;
import com.cybozu.vmbkp.soap.VirtualControllerManager;
import com.cybozu.vmbkp.soap.VirtualDiskManager;

/**
 * @brief Lazy task identifier.
 */
enum LazyTaskId
{
    MOVE_PREV_DUMP_AND_DIGEST, DEL_PREV_DUMP, UNKNOWN;
}

/**
 * @brief Lazy task.
 */
class LazyTask
{
    private LazyTaskId taskId_;
    private int diskId_;

    public LazyTask(LazyTaskId taskId, int diskId)
    {
        assert taskId != null;
        taskId_ = taskId;

        assert diskId >= 0;
        diskId_ = diskId;
    }

    public LazyTaskId getTaskId() { return taskId_; }
    public int getDiskId() { return diskId_; }
}

/**
 * @brief Manage the ProfileVm, realted ProfileGeneration objects,
 * and deletion/movement of archives of a virtual machine.
 */
public class VmArchiveManager
{
    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(VmArchiveManager.class.getName());
    
    /**
     * Global configuration.
     */
    private ConfigGlobal cfgGlobal_;

    /**
     * Vm profile.
     */
    private ProfileVm profVm_;

    /**
     * Current generation.
     */
    private ProfileGeneration currGen_;

    /**
     * List of lazy tasks.
     */
    private LinkedList<LazyTask> lazyTaskList_;
    
    /**
     * Constructor.
     */
    public VmArchiveManager(ConfigGlobal cfgGlobal, VmInfo vmInfo)
        throws Exception
    {
        assert cfgGlobal != null;
        cfgGlobal_ = cfgGlobal;

        /* Initialize generations */
        currGen_ = null;

        /* Initialize profile. */
        profVm_ = initializeProfileVm(vmInfo);
        assert profVm_ != null;

        lazyTaskList_ = new LinkedList<LazyTask>();
    }

    /**
     * Check target archive exists.
     *
     * @return True if archive directory exists.
     */
    public static boolean isExistArchive
        (ConfigGlobal cfgGlobal, VmInfo vmInfo)
    {
        return (isExistArchiveDetail(cfgGlobal, vmInfo) > 0);
    }

    /**
     * Check target archive exists but
     * succeeded generation does not exist.
     *
     * @return True if archive exists but
     *         succeeded generation does not exist.
     */
    public static boolean isEmptyArchive
        (ConfigGlobal cfgGlobal, VmInfo vmInfo)
    {
        return (isExistArchiveDetail(cfgGlobal, vmInfo) == 1);
    }
    
    /**
     * Check succeeded generation exists.
     *
     * @return True if succeeded generation exists, or false.
     */
    public static boolean isExistSucceededGeneration
        (ConfigGlobal cfgGlobal, VmInfo vmInfo)
    {
        return (isExistArchiveDetail(cfgGlobal, vmInfo) == 2);
    }

    /**
     * Check existance of archive and succeeded generation.
     *
     * @return 0: archive does not exist.
     *         1: archive exists but succeeded generation does not exist.
     *         2: succeeded generation exists.
     */
    private static int isExistArchiveDetail
        (ConfigGlobal cfgGlobal, VmInfo vmInfo)
    {
        assert cfgGlobal != null; assert vmInfo != null;

        String backupDirPath =
            cfgGlobal.getDefaultVmDirectory(vmInfo.getMoref());
        String profVmPath =
            cfgGlobal.getDefaultProfileVmPath(vmInfo.getMoref());

        int ret = 0;
        String errMsg = null;
        if ((new File(backupDirPath)).isDirectory() &&
            (new File(profVmPath)).isFile()) {

            ret = 1;
            try {
                ProfileVm profVm = new ProfileVm(profVmPath);
                if (profVm.getLatestSucceededGenerationId() >= 0) {
                    /* Succeeded generation exists. */
                    ret = 2;
                }
            } catch (IOException e) {
                errMsg = String.format("load profile %s failed.\n", profVmPath);
            } catch (NotNormalFileException e) {
                errMsg = String.format("%s is not normal file.\n", profVmPath);
            } catch (ParseException e) {
                errMsg = String.format("parse profile %s failed.\n", profVmPath);
            }
        }
        if (errMsg != null) {
            logger_.warning(errMsg);
        }
        return ret;
    }
    
    /**
     * Initialize vm profile with the specified virtual machine manager.
     * If the profile file is not found, it tries to create the file.
     *
     * @param vmInfo must not be null.
     * @return vm profile.
     */
    private ProfileVm initializeProfileVm(VmInfo vmInfo)
        throws Exception
    {
        /* Assertions. */
        assert vmInfo != null;
        assert cfgGlobal_ != null;
        
        /* Get backup directory path from global config. */
        String backupDirectory =
            cfgGlobal_.getDefaultVmDirectory(vmInfo.getMoref());

        /* Config file of the virtual machine. */
        String profVmPath = cfgGlobal_.getDefaultProfileVmPath(vmInfo.getMoref());
        if (profVmPath == null) {
            logger_.warning("profVmPath is null."); throw new Exception();
        }
        
        /* Create backup directory if it does not exist. */
        File backupDirectoryFile = new File(backupDirectory);
        if (! backupDirectoryFile.isDirectory()) {
            if (! backupDirectoryFile.mkdir()) {
                logger_.warning
                    (String.format
                     ("mkdir %s failed.", backupDirectory));
                throw new Exception();
            }
        }

        /* Prepare vm profile. */
        ProfileVm profVm;
        if ((new File(profVmPath)).isFile()) {
            /* The file exists so we load and update it. */
            profVm = new ProfileVm(profVmPath);

            /* Check moref of both the profile and vmInfo is same. */
            String moref = profVm.getMoref();
            if (! moref.equals(vmInfo.getMoref())) {
                logger_.warning
                    (String.format
                     ("moref %s != %s.\n", moref, vmInfo.getMoref()));
                throw new Exception();
            }

            /* is_clean must be true to continue backup process. */
            if (profVm.isClean() == false) {
                throw new Exception("profVm is not clean.");
            }
            
            /* Overwrite the name of the virtula machine. */
            profVm.setName(vmInfo.getName());
            
        } else {
            /* Config file does not exist then we create it. */

            profVm = new ProfileVm();
            profVm.initializeMetadata(vmInfo);
            profVm.write(profVmPath);
        }
        
        /* Create backup */
        profVm.makeBackup();

        return profVm;
    }

    /**
     * Get global config.
     */
    public ConfigGlobal getConfigGlobal()
    {
        return cfgGlobal_;
    }
    
    /**
     * Save all profile files.
     */
    public void save()
        throws Exception
    {
        assert profVm_ != null;
        profVm_.write();
        
        if (currGen_ != null) {
            currGen_.write();
        }
    }

    /**
     * Execute lazy tasks and
     * write required data after backup almost finished.
     *
     * @param isSucceeded True if dump of all vmdk files succeeded.
     * @return True if the finalization finished successfully.
     */
    public boolean finalizeBackup(boolean isSucceeded)
        throws Exception
    {
        assert profVm_ != null;
        assert currGen_ != null;

        /* Execute lasy tasks. */
        if (isSucceeded) {
            isSucceeded = execLazyTasks();
        }
        
        currGen_.setIsSucceeded(isSucceeded);
        profVm_.setGenerationInfo(currGen_);

        /* Delete old generations. */
        if (isSucceeded) {
            this.deleteOldGenerations();
        }

        return isSucceeded;
    }
    
    /**
     * Get name of the virtual machine.
     */
    public String getName()
    {
        if (profVm_ == null) { return null; }
        return profVm_.getName();
    }

    /**
     * Get moref of the virtual machine.
     */
    public String getMoref()
    {
        if (profVm_ == null) { return null; }
        return profVm_.getMoref();
    }

    /**
     * Load ProfileVm file.
     *
     * Currently this method is not used.
     *
     * @param vmMoref The moref of target virtual machine.
     * @return Loaded profVm in success, or null in failure.
     */
    public ProfileVm loadProfileVmWithMoref(String vmMoref)
    {
        String path = cfgGlobal_.getDefaultProfileVmPath(vmMoref);
        if (path == null) { return null; }
        if (! (new File(path)).isFile()) { return null; }

        ProfileVm profVm = null;
        try {
            profVm = new ProfileVm(path);
        } catch (Exception e) {
            logger_.warning(Utility.toString(e));
            return null;
        }
        return profVm;
    }

    /**
     * Set target generation.
     */
    public void setTargetGeneration(ProfileGeneration profGen)
        throws Exception
    {
        if (profGen == null) { throw new Exception("profGen is null."); }
        assert profGen.getGenerationId() >= 0;
        currGen_ = profGen;
    }

    /**
     * Get target generation.
     */
    public ProfileGeneration getTargetGeneration()
    {
        return currGen_;
    }

    /**
     * Setup generation config file.
     *
     * @param vmInfo Vm information.
     * @param snapInfo Snapshot information.
     * @param vmdkInfoList The list of vmdk information.
     * @param calendar timestamp.
     * @param isGzip True when you use gziped archives.
     * @return Initialized ProfileGeneration object in success.
     */
    public ProfileGeneration prepareNewGeneration
        (VmInfo vmInfo, SnapInfo snapInfo,
         List<VmdkInfo> vmdkInfoList, Calendar calendar,
         boolean isGzip)
        throws Exception
    {
        ProfileVm profVm = profVm_;
        
        /*
         * Create new generation id.
         */
        int currGenId = profVm.getLatestSucceededGenerationId();
        String timestampMs = Long.toString(calendar.getTimeInMillis());
        int newGenId = profVm.createNewGenerationId(timestampMs);
        profVm.write();
        
        /*
         * Make generation directory if it does not exist.
         */
        String genDir = profVm.getDefaultGenerationDirectory(newGenId);
        if ((new File(genDir)).mkdir() == false) {
            logger_.warning(String.format("mkdir %s failed.", genDir));
            throw new Exception();
        }

        /*
         * Make new ProfileGeneration and initialize it.
         */
        String profGenFn = genDir + "/" + ProfileGeneration.FILE_NAME;
        ProfileGeneration profGen = new ProfileGeneration();
        profGen.initializeGeneration
            (newGenId, currGenId,
             vmInfo, snapInfo,
             vmdkInfoList, calendar, isGzip);
        profGen.write(profGenFn);

        return profGen;
    }

    /**
     * Load profile of the specified generation.
     *
     * @param genId Specify -1 to select the latest generation.
     * @return ProfileGeneration object in success, or null.
     */
    public ProfileGeneration loadProfileGeneration(int genId)
        throws IOException, ParseException, NotNormalFileException,
               BackupFailedException
    {
        assert profVm_ != null;
        ProfileVm profVm = profVm_;
        
        if (genId < 0) {
            genId = profVm.getLatestSucceededGenerationId();
            if (genId < 0) { return null; }
        }

        if (profVm.isGenerationSucceeded(genId) == false) {
            logger_.warning
                (String.format
                 ("status of the generation  %d is not succeeded.", genId));
            throw new BackupFailedException();
        }
        String path = profVm.getDefaultProfileGenerationPath(genId);
        if (path == null) { return null; }

        return new ProfileGeneration(path);
    }

    /**
     * Delete backup directoriy of old generations
     * if the number of generations is more than "keep_generations" value.
     */
    public void deleteOldGenerations()
        throws Exception
    {
        /* Used member variables. */
        ConfigGlobal cfgGlobal = cfgGlobal_;
        ProfileVm profVm = profVm_;
        
        /* get keep_generations value */
        int keep = cfgGlobal.getKeepGenerations();
        
        /* get set of id of old generations */
        Set<Integer> oldGenIdSet = profVm.getOldGenerationSet(keep);

        /* debug */
        if (oldGenIdSet.isEmpty()) {
            logger_.info("No generation was deleted.");
            return;
        }
        
        /* debug */
        Set<String> oldGenStrSet = new TreeSet<String>();
        for (Integer gnIdI : oldGenIdSet) {
            oldGenStrSet.add(gnIdI.toString());
        }
        logger_.info(Utility.concat
                     (oldGenStrSet, "\n",
                      "Old generations to be deleted: ", "\n"));
                                    

        for (Integer genIdI : oldGenIdSet) {
            assert genIdI != null;
            int genId = genIdI.intValue();

            deleteGeneration(genId);
        }
    }

    /**
     * Delete backup directory of failed generations.
     */
    public void deleteFailedGenerations()
        throws Exception
    {
        ProfileVm profVm = profVm_;
        List<Integer> list = profVm.getFailedGenerationList();

        for (Integer genIdI : list) {
            assert genIdI != null;
            deleteGeneration(genIdI.intValue());
        }
    }

    /**
     * Get list of failed generations.
     */
    public List<Integer> getFailedGenerationList()
    {
        return profVm_.getFailedGenerationList();
    }

    /**
     * Delete the specified generation directory and
     * the corresponding entry in the vm profile.
     *
     * @param genId
     * @return True if succeeded.
     */
    private boolean deleteGeneration(int genId)
    {
        ProfileVm profVm = profVm_;
        
        String tgtPath =
            profVm.getDefaultGenerationDirectory(genId);
            
        /* delete generation directory */
        boolean ret = Utility.deleteDirectoryRecursive(new File(tgtPath));
        logger_.info
            (String.format
             ("delete generation %d directory %s.\n",
              genId, (ret ? "succeeded" : "failed")));

        /* delete information of the generation from profile vm */
        profVm.delGenerationInfo(genId);

        return ret;
    }

    /**
     * Get previous succeeded generation.
     *
     * @return null if not found.
     */
    private ProfileGeneration getPrevGeneration()
    {
        int currGenId = currGen_.getGenerationId();
        return getPrevGeneration(currGenId);
    }

    /**
     * Get previous succeeded generation of the specified genId.
     */
    private ProfileGeneration getPrevGeneration(int genId)
    {
        if (genId < 0) { return null; }
        int prevGenId = profVm_.getPrevSucceededGenerationId(genId);
        logger_.fine("prevGenId: " + prevGenId); /* debug */
        if (prevGenId < 0) { return null; }
        assert profVm_.isGenerationSucceeded(prevGenId);

        ProfileGeneration ret = null;
        try {
            ret = loadProfileGeneration(prevGenId);
        } catch (Exception e) {
            logger_.warning(Utility.toString(e));
        }
        return ret;
    }

    /**
     * Get next succeeded generation.
     *
     * @return null if not found.
     */
    private ProfileGeneration getNextGeneration()
    {
        int currGenId = currGen_.getGenerationId();
        return getNextGeneration(currGenId);
    }

    /**
     * Get next succeeded generation of the specified genId.
     */
    private ProfileGeneration getNextGeneration(int genId)
    {
        if (genId < 0) { return null; }
        int nextGenId = profVm_.getNextSucceededGenerationId(genId);
        logger_.fine("nextGenId: " + nextGenId); /* debug */
        if (nextGenId < 0) { return null; }
        assert profVm_.isGenerationSucceeded(nextGenId);

        ProfileGeneration ret = null;
        try {
            ret = loadProfileGeneration(nextGenId);
        } catch (Exception e) {
            logger_.warning(Utility.toString(e));
        }
        return ret;
    }
    
    /**
     * Get disk id of corresponding vmdk of previous generation.
     *
     * @return >=0 in success, or -1 in failure.
     */
    public int getPrevDiskId(int diskId)
    {
        ProfileVm profVm = profVm_;
        ProfileGeneration currGen = currGen_;
        
        String uuid = currGen.getUuid(diskId);

        /* get previous generation and check. */
        ProfileGeneration prevGen = getPrevGeneration();
        if (prevGen == null) { return -1; }

        /* search and check disk id. */
        int prevDiskId = prevGen.getDiskIdWithUuid(uuid);
        if (prevDiskId < 0) { return -1; }

        return prevDiskId;
    }
    
    /**
     * Get change id of corresponding vmdk of previous generation.
     *
     * @return change id in success, or null in failure.
     */
    public String getPrevChangeId(int diskId)
    {
        /* get previous generation and check. */
        ProfileGeneration prevGen = getPrevGeneration();
        if (prevGen == null) { return null; }

        int prevDiskId = getPrevDiskId(diskId);
        if (prevDiskId < 0) { return null; }
        
        /* check that changed block information is available */
        String prevChangeId = prevGen.getChangeId(prevDiskId);

        if (prevChangeId != null && prevChangeId.equals("*") == false) {
            return prevChangeId;
        } else {
            return null;
        }
    }

    /**
     * Get previous dump path
     * using diskId of the current generation.
     */
    public String getPrevDumpPath(int diskId)
    {
        ProfileGeneration prevGen = getPrevGeneration();
        if (prevGen == null) { return null; }
        int prevDiskId = getPrevDiskId(diskId);
        if (prevDiskId < 0) { return null; }

        return prevGen.getDumpOutPath(prevDiskId);
    }

    /**
     * Get previous digest path
     * using diskId of the current generation.
     */
    public String getPrevDigestPath(int diskId)
    {
        ProfileGeneration prevGen = getPrevGeneration();
        if (prevGen == null) { return null; }
        int prevDiskId = getPrevDiskId(diskId);
        if (prevDiskId < 0) { return null; }

        return prevGen.getDigestOutPath(prevDiskId);
    }

    /**
     * Check if the differential backup can be executed.
     */
    public boolean canExecDiffBackup(String uuid)
    {
        /* Get current diskId */
        int diskId = currGen_.getDiskIdWithUuid(uuid);
        logger_.info("diskId: " + diskId); /* debug */
        if (diskId < 0) { return false; };

        /* Check the previous dump succeeded */
        ProfileGeneration prevGen = this.getPrevGeneration();
        if (prevGen == null) {
            logger_.warning("prevGen is null.");
            return false;
        }
        int prevDiskId = prevGen.getDiskIdWithUuid(uuid);
        if (prevDiskId < 0) {
            logger_.warning("prevDiskId < 0");
            return false;
        }
        if (prevGen.isVmdkdumpSucceeded(prevDiskId) == false) {
            logger_.warning("vmdkbkp did not succeed.");
            return false;
        }
        
        /* Capacity check. */
        long capacity = currGen_.getCapacity(diskId);
        long prevCapacity = prevGen.getCapacity(prevDiskId);
        if (capacity < 0 || capacity != prevCapacity) {
            logger_.info("capacity is invalid or different.");
            return false;
        }
        
        /* Check full dump file exists in previous generation. */
        if (prevGen.isDumpOutExist(prevDiskId) == false ||
            prevGen.isDigestOutExist(prevDiskId) == false) {
            logger_.info("dump or digest does not exist.");
            return false;
        }

        return true;
    }

    /**
     * Check if incremental backup can be executed.
     */
    public boolean canExecIncrBackup(String uuid)
    {
        /* Check diff backup is capable. */
        if (this.canExecDiffBackup(uuid) == false) {
            return false;
        }

        /* Get the latest dump and digest available generation */
        int diskId = currGen_.getDiskIdWithUuid(uuid);
        if (diskId < 0) {
            logger_.warning("diskId < 0");
            return false;
        };
        ProfileGeneration prevGen = this.getPrevGeneration();
        if (prevGen == null) {
            logger_.warning("prevGen is null.");
            return false;
        }
        int prevDiskId = prevGen.getDiskIdWithUuid(uuid);
        if (prevDiskId < 0) {
            logger_.warning("prevDiskId < 0");
            return false;
        }
        
        /* Check that changed block information of both
           previous and current generation is available */
        String currChangeId = currGen_.getChangeId(diskId);
        if (currChangeId == null || currChangeId.equals("*")) {
            logger_.info("currChangeId is null or \"*\"");
            return false;
        }
        String prevChangeId = prevGen.getChangeId(prevDiskId);
        logger_.info("prevChangeId: " + prevChangeId);
        if (prevChangeId == null || prevChangeId.equals("*")) {
            logger_.info("prevChangeId is null or \"*\"");
            return false;
        }

        /* ChangeId of both generations exists. */
        return true;
    }

    /**
     * Get a list of full dump file and rdiff files to restore
     * vmdk specified with diskId.
     *
     * @param diskId Disk id of the !!!current!!! generation.
     * @return The list of file path of the dump and rdiff.
     *         the first element must not be rdiff but dump.
     *         Return null in failure.
     */
    public List<String> getDumpPathListForRestore(int diskId)
    {
        assert currGen_ != null;
        ProfileGeneration currGen = currGen_;
        
        logger_.info
            (String.format
             ("------------------------------------------------------\n" +
              "getDumpPathListForRestore start for diskID %d.\n" +
              "------------------------------------------------------\n",
              diskId));
        
        /* Initialize */
        LinkedList<String> ret = new LinkedList<String>();
        String uuid = currGen.getUuid(diskId);

        /* Check the generation backup succeeded. */
        if (currGen.isVmdkdumpSucceeded(diskId) == false) {
            logger_.warning
                (String.format
                 ("Skipping: " +
                  "generation %d disk %d (%s) is marked FAILED.\n",
                  currGen.getGenerationId(), diskId, currGen.getUuid(diskId)));
            return null;
        }

        /* Does this generation have the full dump */
        String dumpPath;
        dumpPath = currGen.getDumpOutPath(diskId);
        logger_.info(String.format("dumpPath: %s\n", dumpPath));
        if (currGen.isDumpOutExist(diskId)) {
            ret.add(dumpPath);
            return ret; 
        }

        /* There is no dump of the current generation,
           so search corresponding dump in newer generations. */
        assert ret.isEmpty();
        String rdiffPath;

        boolean isFirstElementDump = false;
        ProfileGeneration next = this.getNextGeneration();
        while (next != null) {

            /* Get nextDiskId */
            int nextDiskId = next.getDiskIdWithUuid(uuid);
            if (nextDiskId < 0) {
                logger_.warning("diskId is invalid."); /* debug */
                return null;
            }

            /* Check the vmdk backup succeeded. */
            if (next.isVmdkdumpSucceeded(nextDiskId) == false) {
                logger_.warning
                    (String.format
                     ("backup of vmdk with diskId %d is failed.", 
                      nextDiskId));
                return null;
            }
            
            dumpPath = next.getDumpOutPath(nextDiskId);
            rdiffPath = next.getRdiffOutPath(nextDiskId);

            logger_.info
                (String.format
                 ("dumpPath: %s\n" + "rdiffPath: %s\n",
                  dumpPath, rdiffPath));

            boolean notFoundFile = true;
            if (next.isRdiffOutExist(nextDiskId)) {
                /* Rdiff file is found. */
                ret.addFirst(FormatString.toQuatedString(rdiffPath));
                notFoundFile = false;
            }
            if (next.isDumpOutExist(nextDiskId)) {
                /* Dump file is found. */
                ret.addFirst(FormatString.toQuatedString(dumpPath));
                isFirstElementDump = true;
                break;
            }
            if (next.isChanged(nextDiskId) == false) {
                /* The vmdk is not changed at all in This generation.
                   This generation may have none of dump, digest, and rdiff.
                   Just skip. */
                notFoundFile = false;
            }
            if (notFoundFile) {
                /* Error. */
                logger_.warning
                    (String.format
                     ("Error: neither dump nor rdiff is available in " +
                      " generation %d\n", next.getGenerationId()));
                return null;
            }
                
            next = this.getNextGeneration(next.getGenerationId());
        }
            
        if (isFirstElementDump == false) {
            logger_.warning
                (String.format("isFirstElementDump is false."));
            return null;
        }

        /* debug */
        StringBuffer sb = new StringBuffer();
        sb.append("---getDumpPathListForRestore() result---\n");
        for (String path : ret) {
            sb.append(path);
            sb.append("\n");
        }
        sb.append("----------------------------------------\n");
        logger_.info(sb.toString());

        logger_.info("getDumpPathListForRestore end.");
        return (List<String>) ret;
    }

    /**
     * Get digest path of the disk of the current generation.
     * (The digest file may be moved to a future generation.)
     * 
     * @param diskId disk id of current generation.
     * @return Full path of the target digest file.
     */
    public String getDigestPathForCheck(int diskId)
    {
        assert currGen_ != null;
        ProfileGeneration currGen = currGen_;

        String uuid = currGen.getUuid(diskId);

        /* There is no digest file in the current generation.
           Search newer generations. */
        boolean isFound = false;
        ProfileGeneration next = currGen;
        while (next != null) {

            int nextDiskId = next.getDiskIdWithUuid(uuid);
            if (nextDiskId < 0) {
                logger_.warning("diskId is invalid.");
                return null;
            }

            if (next.isVmdkdumpSucceeded(nextDiskId) == false) {
                logger_.warning
                    (String.format
                     ("Geneartion %d disk %d (%s) is marked FAILED.\n",
                      next.getGenerationId(), nextDiskId, uuid));
                return null;
            }

            String digestPath;
            digestPath = next.getDigestOutPath(nextDiskId);
            if (next.isDigestOutExist(nextDiskId)) {
                logger_.info(String.format("digestPath: %s\n", digestPath));
                return digestPath;
            }

            next = this.getNextGeneration(next.getGenerationId());
        }

        /* not found */
        logger_.warning
            (String.format
             ("digestPath not found for genetarion %d disk %d (%s).",
              currGen.getGenerationId(), diskId, uuid));
        return null;
    }

    /**
     * @param filename Given string to test.
     * @return True if the extension is ".gz".
     */
    private boolean isGzipFileName(String filename)
    {
        int len = filename.length();
        return (len > 3 && filename.substring(len - 3).equals(".gz"));
    }

    /**
     * @param filename String to be converted.
     * @param isGzip The name will be gzip file name if true.
     * @return converted string.
     */
    private String convertFileName(String filename, boolean isGzip)
    {
        String tmp = eliminateGzipedExtension(filename);
        if (isGzip) {
            return addGzipedExtension(tmp);
        } else {
            return tmp;
        }
    }

    /**
     * @param filename Given string to be converted.
     * @return non-gzip file name.
     */
    private String eliminateGzipedExtension(String filename)
    {
        int len = filename.length();
        if (isGzipFileName(filename)) {
            assert len > 3;
            return filename.substring(0, len - 3);
        } else {
            return filename;
        }
    }

    /**
     * @param filename Given string to be converted.
     *        must not be gzip file name.
     * @return gzip file name.
     */
    private String addGzipedExtension(String filename) {

        assert ! isGzipFileName(filename);
        return filename + ".gz";
    }
    
    /**
     * Move previous dump and digest files
     * to current generation directory.
     */
    public boolean moveDumpAndDigestFromPrev(int diskId)
    {
        assert profVm_ != null;
        assert currGen_ != null;

        {
            /* Set the same file type (gziped or not). */
            String fromDump = this.getPrevDumpPath(diskId);
            String toDump = currGen_.getDumpOutFileName(diskId);
            boolean isGzipFromDump = isGzipFileName(fromDump);
            boolean isGzipToDump = isGzipFileName(toDump);
            if (isGzipFromDump != isGzipToDump) {
                currGen_.setDumpOutFileName
                    (diskId, convertFileName(toDump, isGzipFromDump));
            }
            logger_.fine
                (String.format
                 ("%s %s %s\n",
                  isGzipFromDump,
                  isGzipToDump,
                  currGen_.getDumpOutFileName(diskId)));

            String fromDigest = this.getPrevDigestPath(diskId);
            String toDigest = currGen_.getDigestOutFileName(diskId);
            boolean isGzipFromDigest = isGzipFileName(fromDigest);
            boolean isGzipToDigest = isGzipFileName(toDigest);
            if (isGzipFromDigest != isGzipToDigest) {
                currGen_.setDigestOutFileName
                    (diskId, convertFileName(toDigest, isGzipFromDigest));
            }
            logger_.fine
                (String.format
                 ("%s %s %s\n",
                  isGzipFromDigest,
                  isGzipToDigest,
                  currGen_.getDigestOutFileName(diskId)));
        }

        
        String fromDumpStr = this.getPrevDumpPath(diskId);
        String toDumpStr = currGen_.getDumpOutPath(diskId);
        assert fromDumpStr != null && toDumpStr != null;

        String fromDigestStr = this.getPrevDigestPath(diskId);
        String toDigestStr = currGen_.getDigestOutPath(diskId);
        assert fromDigestStr != null && toDigestStr != null;

        logger_.info
            (String.format
             ("move dump %s to %s. digest %s to %s.",
              fromDumpStr, toDumpStr,
              fromDigestStr, toDigestStr));
        
        File fromDump = new File(fromDumpStr);
        File toDump = new File(toDumpStr);
        File fromDigest = new File(fromDigestStr);
        File toDigest = new File(toDigestStr);

        
        if (fromDump.isFile() == false) {
            logger_.warning
                (String.format
                 ("File %s not found.", fromDumpStr));
            return false;
        }
        if (fromDigest.isFile() == false) {
            logger_.warning
                (String.format
                 ("File %s not found.", fromDigestStr));
            return false;
        }

        return fromDump.renameTo(toDump) && fromDigest.renameTo(toDigest);
    }

    /**
     * Delete dump file of previous succeeded generation.
     */
    public boolean deletePrevDump(int diskId)
    {
        ProfileGeneration prevGen = this.getPrevGeneration();
        if (prevGen == null) {
            logger_.warning("prevGen is null.");
            return false;
        }
        String prevDumpPath = this.getPrevDumpPath(diskId);
        if (prevDumpPath == null) {
            logger_.warning("prevDumpPath is null.");
            return false;
        }
        File prevDumpFile = new File(prevDumpPath);
        if (prevDumpFile.isFile() == false) {
            logger_.warning("prevDumpFile is not normal file.");
            return false;
        }

        /* delete previoud dump file! */
        boolean isDeleted = prevDumpFile.delete();
        currGen_.setDeletedPreviousDump(diskId, isDeleted);
        
        logger_.info
            (String.format
             ("Deleting previous dump %s %s.",
              prevDumpPath, (isDeleted ? "succeeded" : "failed")));

        return isDeleted;
    }

    /**
     * Register lazy task of moveDumpAndDigestFromPrev().
     *
     * @param diskId disk id of the current generation.
     */
    public void registerLazyTaskMovePrevDumpAndDigest(int diskId)
    {
        logger_.info
            (String.format
             ("register %s %d.",
              LazyTaskId.MOVE_PREV_DUMP_AND_DIGEST.toString(),
              diskId));

        assert lazyTaskList_ != null;
        LazyTask task =
            new LazyTask(LazyTaskId.MOVE_PREV_DUMP_AND_DIGEST, diskId);
        lazyTaskList_.offer(task);
    }

    /**
     * Register lazy task of deletePrevDump().
     *
     * @param diskId disk id of the current generation.
     */
    public void registerLazyTaskDelPrevDump(int diskId)
    {
        logger_.info
            (String.format
             ("register %s %d.",
              LazyTaskId.DEL_PREV_DUMP.toString(),
              diskId));
        
        assert lazyTaskList_ != null;
        LazyTask task =
            new LazyTask(LazyTaskId.DEL_PREV_DUMP, diskId);
        lazyTaskList_.offer(task);
    }

    /**
     * Execute lazy tasks registered.
     *
     * @return True if all tasks finished successfully, or false.
     */
    private boolean execLazyTasks()
    {
        logger_.info("execLazyTasks() begin.");
        assert lazyTaskList_ != null;
        boolean ret = true;

        while (lazyTaskList_.isEmpty() == false) {
            LazyTask task = lazyTaskList_.poll();

            switch (task.getTaskId()) {
            case MOVE_PREV_DUMP_AND_DIGEST:
                ret &= moveDumpAndDigestFromPrev(task.getDiskId());
                break;
            case DEL_PREV_DUMP:
                ret &= deletePrevDump(task.getDiskId());
                break;
            default:
                ret = false;
                break;
            }
        }
        logger_.info("execLazyTasks() end.");
        return ret;
    }

    /**
     * Generate controller-disk map from config data.
     *
     * @param datastoreName datastore name.
     * @return a list of virtual controllers manager which have
     *         a list of information of its virtual disks.
     */
    public List<VirtualControllerManager>
        generateVirtualControllerManagerList(String datastoreName)
    {
        assert datastoreName != null;
        assert currGen_ != null;
        
        ProfileGeneration profGen = currGen_;
        
        /* Set to make sorted and deduplicated list later. */
        TreeSet<VirtualControllerManager> vcmSet =
            new TreeSet<VirtualControllerManager>();

        List<Integer> diskIdList = profGen.getDiskIdList();
        for (Integer diskIdI : diskIdList) {

            int diskId = diskIdI.intValue();
            assert diskId >= 0;

            /* Skip independent disk. */
            if (profGen.isIndependentDisk(diskId)) { continue; }
            
            /* Get required parameters */
            int key = profGen.getDiskDeviceKey(diskId);
            int unitNumber = profGen.getUnitNumber(diskId);
            long capacity = profGen.getCapacity(diskId);
            assert capacity % 1024L == 0;
            long capacityInKb = capacity / 1024L;

            AdapterType type = profGen.getAdapterType(diskId);
            assert type != AdapterType.UNKNOWN;
            int ckey = profGen.getControllerDeviceKey(diskId);
            int busNumber = profGen.getBusNumber(ckey);

            /* Create virtual disk manager and virtual controller manager. */
            VirtualDiskManager vdm = new VirtualDiskManager
                (key, unitNumber, capacityInKb, datastoreName);
            VirtualControllerManager vcm =
                new VirtualControllerManager(type, ckey, busNumber);

            /* Get the corresponding virtual controller manager
               in the set, or the created one is added to the set. */
            VirtualControllerManager vcmInSet = vcmSet.ceiling(vcm);
            if (vcmInSet == null || vcmInSet.compareTo(vcm) != 0) {
                vcmSet.add(vcm);
                vcmInSet = vcm;
            } else {
                assert vcmInSet.compareTo(vcm) == 0;
            }
            /* Assign relationship of the disk and the controller  */
            vcmInSet.add(vdm);
        } /* for */

        /* Set -> List */
        List<VirtualControllerManager> ret =
            new LinkedList<VirtualControllerManager>();
        ret.addAll(vcmSet);
        return ret;
    }

    /**
     * Print the specified controller-disk mapping for debug.
     *
     * @param vcmList
     */
    public void printVirtualControllerManagerList
        (List<VirtualControllerManager> vcmList)
    {
        for (VirtualControllerManager vcm : vcmList) {
            vcm.print();
        }
    }

    /**
     * Convert List<VirtualControllerManager>
     * to String as human-readable format.
     *
     * @param vcmList
     * @return Human readable information of
     *  the list of VirtualControllerManager.
     */
    public String toStringVirtualControllerManagerList
        (List<VirtualControllerManager> vcmList)
    {
        StringBuffer sb = new StringBuffer();
        for (VirtualControllerManager vcm : vcmList) {
            sb.append(vcm.toString());
        }
        return sb.toString();
    }
    
    /**
     * Get target disk id of the corresponding vmdk with the given vmdkInfo.
     */
    public int getTargetDiskId(VmdkInfo vmdkInfo)
    {
        assert currGen_ != null;
        ProfileGeneration profGen = currGen_;
        
        List<Integer> diskIdList = profGen.getDiskIdList();
        for (Integer diskIdI : diskIdList) {
            int diskId = diskIdI.intValue();

            /* Comparing key is enough, however,
               The following parameters should be the same. */
            int ckey = profGen.getControllerDeviceKey(diskId);
            if (vmdkInfo.key_ == profGen.getDiskDeviceKey(diskId) &&
                vmdkInfo.capacityInKB_ * 1024L == profGen.getCapacity(diskId) &&
                vmdkInfo.unitNumber_ == profGen.getUnitNumber(diskId) &&
                vmdkInfo.ckey_ == ckey &&
                vmdkInfo.busNumber_ == profGen.getBusNumber(ckey) &&
                vmdkInfo.type_ == profGen.getAdapterType(diskId)) {

                return diskId;
            }
        }
        
        /* not found */
        return -1;
    }

    /**
     * Lock wrapper.
     */
    public void lock(int timeoutSec)
        throws Exception
    {
        assert profVm_ != null;
        profVm_.lock(timeoutSec);
    }

    /**
     * Unlock wrapper.
     */
    public void unlock()
    {
        assert profVm_ != null;
        profVm_.unlock();
    }

    /**
     * Reload wrapper.
     */
    public void reload()
        throws Exception
    {
        assert profVm_ != null;
        profVm_.reload();
    }

    /**
     * Get status string of generation of the genId.
     */
    private String getGenerationStatusString(int genId)
    {
        StringBuffer sb = new StringBuffer();
        sb.append
            (String.format
             ("[Gen %d \"%s\"]", genId, profVm_.getTimestampStr(genId)));

        if (profVm_.isGenerationSucceeded(genId)) {
            try {
                ProfileGeneration profGen =
                    loadProfileGeneration(genId);

                List<Integer> diskIdList = profGen.getDiskIdList();

                for (Integer diskId: diskIdList) {

                    sb.append
                        (String.format
                         ("[%d %s%s:%s %sB %s %ds]",
                          diskId,
                          profGen.getAdapterType(diskId).toTypeString(),
                          profGen.getBusNumber
                          (profGen.getControllerDeviceKey(diskId)),
                          profGen.getUnitNumber(diskId),
                          FormatInt.toString(profGen.getCapacity(diskId)),
                          profGen.getBackupMode(diskId).toString(),
                          profGen.getDumpElapsedTimeMs(diskId) / 1000L
                          ));
                }

            } catch (Exception e) {
                logger_.warning
                    (String.format("failed with generation %d.", genId));
                return String.format("%d ##########_ERROR_##########");
            }
        } else {
            sb.append(" ----------_FAILED_----------");
        }
        return sb.toString();
    }
        
    /**
     * Get status string.
     */
    public String getStatusString(StatusInfo statusInfo, boolean isAvailable)
    {
        assert profVm_ != null;

        StringBuffer sb = new StringBuffer();
        
        sb.append(profVm_.getStatusString(isAvailable));

        if (statusInfo.isDetail) {
            for (Integer genId: profVm_.getGenerationIdList()) {
                assert genId != null;
                assert genId >= 0;
                
                sb.append("\n\t");
                sb.append(this.getGenerationStatusString(genId));
            }
        }
        return sb.toString();
    }

    /**
     * Get timestampMs of latest generation.
     *
     * @return timestampMs. -1 means no available generation.
     */
    public long getTimestampMsOfLatestGeneration()
    {
        if (profVm_ == null) { return -1L; }
        int genId = profVm_.getLatestGenerationId();

        if (genId < 0) {
            return -1L;
        } else {
            return Long.valueOf(profVm_.getTimestampMs(genId)).longValue();
        }
    }
}
