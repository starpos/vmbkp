/**
 * @file
 * @brief ProfileGeneration
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.profile;

import java.util.Calendar;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.Collections;
import java.util.logging.Logger;

import java.io.File;
import java.io.IOException;

import com.cybozu.vmbkp.config.Group;
import com.cybozu.vmbkp.config.Entry;
import com.cybozu.vmbkp.config.FormatString;
import com.cybozu.vmbkp.config.FormatInt;
import com.cybozu.vmbkp.config.FormatBool;
import com.cybozu.vmbkp.config.ParseException;
import com.cybozu.vmbkp.config.NotNormalFileException;

import com.cybozu.vmbkp.util.BackupMode;
import com.cybozu.vmbkp.util.VmInfo;
import com.cybozu.vmbkp.util.SnapInfo;
import com.cybozu.vmbkp.util.VmdkInfo;
import com.cybozu.vmbkp.util.AdapterType;
import com.cybozu.vmbkp.util.Utility;
import com.cybozu.vmbkp.profile.ConfigWrapper;

/**
 * @brief Wrapper class to access vmbkp_generation.profile.
 */
public class ProfileGeneration
    extends ConfigWrapper
{
    public static final String FILE_NAME = "vmbkp_generation.profile";
    
    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(ProfileGeneration.class.getName());
    
    /**
     * Group [generation].
     */
    private Group generation_;

    /**
     * Default constructor. This will create an empty profile.
     */
    public ProfileGeneration()
    {
        super();
        initializeGroups();
    }
    
    /**
     * Read the specified config file and construct an object with it.
     *
     * @param filePath Path to a vmbkp_generation.profile file.
     */
    public ProfileGeneration(String filePath)
        throws IOException, ParseException, NotNormalFileException
    {
        super(filePath);
        initializeGroups();
    }

    /**
     * Initialize group(s).
     */
    private void initializeGroups()
    {
        /* initialize group(s) */
        generation_ = new Group("generation");
    }

    /**
     * Generate Group of [disk "`diskId`"]
     */
    private Group generateDiskGroup(int diskId)
    {
        return new Group("disk", Integer.toString(diskId));
    }

    /**
     * Generate Group of [index "disk"] 
     */
    private Group generateDiskIndexGroup()
    {
        return new Group("index", "disk");
    }

    /**
     * Generate Groups of [index "controller"]
     */
    private Group generateControllerIndexGroup()
    {
        return new Group("index", "controller");
    }

    /**
     * Generate Group of [controller "`ckey`"]
     *
     * @param ckey key of the specified controller device.
     */
    private Group generateControllerGroup(int ckey)
    {
        return new Group("controller", Integer.toString(ckey));
    }
    
    /**
     * Initialize generation profile.
     *
     * @param genId genration id.
     * @param prevGenId previous generation id.
     *        that this generation depends on.
     * @param vmInfo virtual machine info.
     * @param snapInfo snapshot info.
     * @param vmdkInfoList list of vmdk info.
     * @param calendar for timestamp.
     * @param use gziped archives or not.
     */
    public void initializeGeneration
        (int genId, int prevGenId,
         VmInfo vmInfo, SnapInfo snapInfo,
         List<VmdkInfo> vmdkInfoList,
         Calendar calendar, boolean isGzip)
    {
        logger_.info("initializeGeneration() begin.");

        /* [generation] group */
        initializeGenerationGroup
            (genId, prevGenId, vmInfo, snapInfo,
             calendar, vmdkInfoList.size());

        int diskId = 0;
        for (VmdkInfo vmdkInfo : vmdkInfoList) {
            /* [disk `diskId`] group */
            initializeDiskGroup(diskId, vmdkInfo, isGzip);
            diskId ++;
        }

        logger_.info("initializeGeneration() end.");
    }

    /**
     * Initialize contents in [generation] group.
     *
     * @param genId genration id.
     * @param prevGenId previous generation id.
     *        that this generation depends on.
     * @param vmInfo virtual machine info.
     * @param snapInfo snapshot info.
     * @param calendar for timestamp.
     * @param numVmdk # of vmdk files with the virtual machine.
     */
    private void initializeGenerationGroup
        (int genId, int prevGenId,
         VmInfo vmInfo, SnapInfo snapInfo,
         Calendar calendar, int numVmdk)
    {
        String timestamp = calendar.getTime().toString();
        long timestampMs = calendar.getTimeInMillis();

        cfg_.put(generation_, "generation_id", Integer.toString(genId));
        cfg_.put(generation_, "moref", vmInfo.getMoref());
        cfg_.put(generation_, "name", vmInfo.getName());
        cfg_.put(generation_, "status", "initialized");
        cfg_.put(generation_, "snapshot_moref", snapInfo.getMoref());
        cfg_.put(generation_, "snapshot_name", snapInfo.getName());
        cfg_.put(generation_, "ovf_filename", snapInfo.getMoref() + ".ovf");
        cfg_.put(generation_, "timestamp", timestamp);
        cfg_.put(generation_, "timestamp_ms", Long.toString(timestampMs));
        cfg_.put(generation_, "prev_generation_id",
                 Integer.toString(prevGenId));
        cfg_.put(generation_, "num_disks", Integer.toString(numVmdk));
        cfg_.put(generation_, "num_vmdkdump_succeeded", "0");
        cfg_.put(generation_, "num_vmdkdump_failed", "0");
    }

    /**
     * Initialize contents in [disk `diskId`] group.
     *
     * @param diskId Disk identifier inside the generation only.
     * @param vmdkInfo vmdk info.
     * @param isGzip use gziped archives or not.
     */
    private void initializeDiskGroup
        (int diskId, VmdkInfo vmdkInfo, boolean isGzip)
    {

        String iStr = Integer.toString(diskId);
        Group diskGroup = generateDiskGroup(diskId);

        cfg_.put(diskGroup, "remote_path", vmdkInfo.name_);
        cfg_.put(diskGroup, "uuid", vmdkInfo.uuid_);
        cfg_.put(diskGroup, "capacity", Long.toString(vmdkInfo.capacityInKB_) + "K");

        if (vmdkInfo.changeId_ != null) {
            cfg_.put(diskGroup, "change_id", vmdkInfo.changeId_);
        } else {
            cfg_.put(diskGroup, "change_id", "*");
        }

        String gzipExt = ""; if (isGzip) { gzipExt = ".gz"; }
        
        cfg_.put(diskGroup, "adapter_type", vmdkInfo.type_.toString());
        cfg_.put(diskGroup, "controller_key", Integer.toString(vmdkInfo.ckey_));
        cfg_.put(diskGroup, "device_key", Integer.toString(vmdkInfo.key_));
        cfg_.put(diskGroup, "bus_number", Integer.toString(vmdkInfo.busNumber_));
        cfg_.put(diskGroup, "unit_number", Integer.toString(vmdkInfo.unitNumber_));
            
        cfg_.put(diskGroup, "backup_mode", "unknown"); /* must be set later. */
        cfg_.put(diskGroup, "disk_mode", vmdkInfo.diskMode_);
        cfg_.put(diskGroup, "rdiff_timestamp_ms", "-1");
        cfg_.put(diskGroup, "filename_dump", iStr + ".dump" + gzipExt);
        cfg_.put(diskGroup, "filename_digest", iStr + ".digest" + gzipExt);
        cfg_.put(diskGroup, "filename_rdiff", iStr + ".rdiff" + gzipExt);
        cfg_.put(diskGroup, "filename_bmp", iStr + ".bmp");
        cfg_.put(diskGroup, "is_deleted_previous_dump", "false");
        cfg_.put(diskGroup, "is_changed", "undefined"); /* must be set later. */

        cfg_.put(diskGroup, "dump_begin_timestamp_ms", "-1");
        cfg_.put(diskGroup, "dump_end_timestamp_ms", "-1");

        cfg_.put(diskGroup, "status", "failed");

        /* index (uuid -> diskId) */
        Group diskIndexGroup = this.generateDiskIndexGroup();
        cfg_.put(diskIndexGroup, vmdkInfo.uuid_, iStr);

        /* make controller group and entries. */
        Group controllerGroup = this.generateControllerGroup(vmdkInfo.ckey_);
        cfg_.put(controllerGroup, "adapter_type", vmdkInfo.type_.toString());
        cfg_.put(controllerGroup, "bus_number", Integer.toString(vmdkInfo.busNumber_));

        /* index (uuid -> controller_key) */
        Group controllerIndexGroup = this.generateControllerIndexGroup();
        cfg_.put(controllerIndexGroup, vmdkInfo.uuid_,
                 Integer.toString(vmdkInfo.ckey_));

    }

    /**
     * Get [generation] generation_id as int.
     */
    public int getGenerationId()
    {
        return cfg_.getValAsInt(generation_, "generation_id");
    }

    /**
     * Get [generation] generation_id.
     */
    public String getGenerationIdStr()
    {
        return cfg_.getVal(generation_, "generation_id");
    }

    /**
     * Get diskId with uuid using [index "disk"].
     *
     * @return diskId in success, or -1 in failure.
     */
    public int getDiskIdWithUuid(String uuid)
    {
        Group diskIndexGroup = this.generateDiskIndexGroup();
        String iStr = cfg_.getVal(diskIndexGroup, uuid);
        if (iStr == null || ! FormatInt.canBeInt(iStr)) {
            return -1;
        }

        return FormatInt.toInt(iStr);
    }

    /**
     * Set [generation] status.
     */
    private void setStatus(String status)
    {
        cfg_.put(generation_, "status", status);
    }

    /**
     * Get [generation] status.
     */
    protected String getStatus()
    {
        return cfg_.getVal(generation_, "status");
    }

    /**
     * Set succeeded flag.
     */
    public void setIsSucceeded(boolean isSucceeded)
    {
        setStatus(isSucceeded ? "succeeded" : "failed");
    }

    /**
     * Check the generation backup succeeded.
     */
    public boolean isSucceeded()
    {
        String ret = this.getStatus();
        return (ret != null && ret.equals("succeeded"));
    }
    
    /**
     * Get [generation] moref.
     * This returns moref of the virtual machine, not the snapshot.
     */
    public String getMoref()
    {
        return cfg_.getVal(generation_, "moref");
    }

    /**
     * Get [generation] snapshot_moref.
     */
    public String getSnapshotMoref()
    {
        return cfg_.getVal(generation_, "snapshot_moref");
    }

    /**
     * Get [generation] timestamp_ms.
     */
    public String getTimestampMs()
    {
        return cfg_.getVal(generation_, "timestamp_ms");
    }

    /**
     * Get [disk "`diskId`"] remote_path.
     */
    public String getRemoteDiskPath(int diskId)
    {
        return this.getDiskGroupValAsQString(diskId, "remote_path");
    }

    /**
     * Get filename of [generation] filename_dump.
     */
    public String getDumpOutFileName(int diskId)
    {
        return this.getDiskGroupValAsAutoString(diskId, "filename_dump");
    }

    /**
     * Set filename of [generation] filename_dump.
     */
    public void setDumpOutFileName(int diskId, String filename)
    {
        cfg_.put(generateDiskGroup(diskId), "filename_dump", filename);
    }

    /**
     * Get path of dump out.
     */
    public String getDumpOutPath(int diskId)
    {
        String filename = getDumpOutFileName(diskId);
        if (filename == null) { return null; }
        return getDirectory() + "/" + filename;
    }

    /**
     * @return True if the dump out file exists.
     */
    public boolean isDumpOutExist(int diskId)
    {
        String filepath = getDumpOutPath(diskId);
        logger_.info("path: " + filepath); /* debug */
        if (filepath == null) { return false; }
        return (new File(filepath)).isFile();
    }

    /**
     * Get filename of [disk "`diskId`"] filename_digest.
     */
    public String getDigestOutFileName(int diskId)
    {
        return this.getDiskGroupValAsAutoString(diskId, "filename_digest");
    }

    /**
     * Set filename of [generateion] filename_digest.
     */
    public void setDigestOutFileName(int diskId, String filename)
    {
        cfg_.put(generateDiskGroup(diskId), "filename_digest", filename);
    }

    /**
     * Get path of digest out.
     */
    public String getDigestOutPath(int diskId)
    {
        String filename =  getDigestOutFileName(diskId);
        if (filename == null) { return null; }
        return getDirectory() + "/" + filename;
    }

    /**
     * @return True if the digest out file exists.
     */
    public boolean isDigestOutExist(int diskId)
    {
        String filepath = getDigestOutPath(diskId);
        logger_.info("path: " + filepath); /* debug */
        if (filepath == null) { return false; }
        return (new File(filepath)).isFile();
    }

    /**
     * Get filename of [disk "`diskId`"] filename_rdiff.
     */
    public String getRdiffOutFileName(int diskId)
    {
        return this.getDiskGroupValAsAutoString(diskId, "filename_rdiff");
    }        

    /**
     * The rdiff of this generation is for the latest generation
     * that is not (backup_mode is "incr" and is_changed is "false").
     */
    public String getRdiffOutPath(int diskId)
    {
        String fname = this.getRdiffOutFileName(diskId);
        if (fname == null) { return null; }
        return this.getDirectory() + "/" + fname;
    }

    /**
     * @return true if the rdiff out file exists.
     */
    public boolean isRdiffOutExist(int diskId)
    {
        String filepath = getRdiffOutPath(diskId);
        if (filepath == null) { return false; }
        return (new File(filepath)).isFile();
    }

    /**
     * Get filename of [disk "`diskId`"] filename_bmp.
     */
    public String getBmpInFileName(int diskId)
    {
        return this.getDiskGroupValAsAutoString(diskId, "filename_bmp");
    }

    /**
     * Get filename of [disk "`diskId`"] filename_bmp and return full path.
     */
    public String getBmpInPath(int diskId)
    {
        String fname = this.getBmpInFileName(diskId);
        if (fname == null) { return null; }
        return this.getDirectory() + "/" + fname;
    }

    /**
     * Get [disk "`diskId`"] uuid.
     */
    public String getUuid(int diskId)
    {
        return this.getDiskGroupValAsAutoString(diskId, "uuid");
    }

    /**
     * Get [disk "`diskId`"] change_id.
     */
    public String getChangeId(int diskId)
    {
        return this.getDiskGroupValAsAutoString(diskId, "change_id");
    }

    /**
     * Get [disk "`diskId`"] key as a quated string.
     */
    private String getDiskGroupValAsQString(int diskId, String key)
    {
        String val = cfg_.getVal(generateDiskGroup(diskId), key);

        if (val != null) {
            return FormatString.toQuatedString(val);
        } else {
            return null;
        }
    }
    
    /**
     * Get [disk "`diskId`"] key as a string.
     */
    private String getDiskGroupValAsAutoString(int diskId, String key)
    {
        String val = cfg_.getVal(generateDiskGroup(diskId), key);

        if (val != null) {
            return FormatString.toStringAuto(val);
        } else {
            return null;
        }
    }

    /**
     * Get [disk "`diskId`"] capacity as long value.
     */
    public long getCapacity(int diskId)
    {
        return cfg_.getValAsLong(generateDiskGroup(diskId), "capacity");
    }

    /**
     * Get [disk "`diskId`"] controller_key as int value.
     */
    public int getControllerDeviceKey(int diskId)
    {
        return cfg_.getValAsInt(generateDiskGroup(diskId), "controller_key");
    }

    /**
     * Get [disk "`diskId`"] device_key as int value.
     */
    public int getDiskDeviceKey(int diskId)
    {
        return cfg_.getValAsInt(generateDiskGroup(diskId), "device_key");
    }

    /**
     * Get [disk "`diskId`"] unit_number as int value.
     */
    public int getUnitNumber(int diskId)
    {
        return cfg_.getValAsInt(generateDiskGroup(diskId), "unit_number");
    }

    /**
     * Get [disk "`diskId`"] disk_mode.
     */
    public String getDiskMode(int diskId)
    {
        return cfg_.getVal(generateDiskGroup(diskId), "disk_mode");
    }

    /**
     * @return True if the specified disk is independent disk.
     */
    public boolean isIndependentDisk(int diskId)
    {
        return "independent_persistent".equals(getDiskMode(diskId));
    }

    /**
     * Get [controller "`ckey`" bus_number as int value.
     */
    public int getBusNumber(int ckey)
    {
        return cfg_.getValAsInt(generateControllerGroup(ckey), "bus_number");
    }

    /**
     * The vmdk with the specified diskId has been backuped successfuly?
     *
     * @param diskId disk id in the config.
     * @return true if status entry is "succeeded", or false.
     */
    public boolean isVmdkdumpSucceeded(int diskId)
    {
        String val = getDiskGroupValAsAutoString(diskId, "status");
        if (val == null) { return false; }
        return val.equals("succeeded");
    }
    
    /**
     * Set [disk "`diskId`"] status
     * and count
     * [generation] num_vmdkdump_succeeded
     * or
     * [generation] num_vmdkdump_failed
     *
     * @param diskId Disk id managed in ProfileGeneration.
     * @param isSucceeded true in success, false in failure.
     */
    public void setVmdkdumpResult(int diskId, boolean isSucceeded)
    {
        String status = (isSucceeded ? "succeeded" : "failed");
        cfg_.put(generateDiskGroup(diskId), "status", status);

        if (isSucceeded) {
            incrementNumOfSucceededVmdkdump();
        } else {
            incrementNumOfFailedVmdkdump();
        }
    }

    /**
     * Set [disk "`diskId`"] backup_mode.
     */
    public void setBackupMode(int diskId, BackupMode mode)
    {
        cfg_.put(generateDiskGroup(diskId), "backup_mode", mode.toString());
    }

    /**
     * Get [disk "`diskId`"] backup_mode.
     */
    public BackupMode getBackupMode(int diskId)
    {
        return BackupMode.parse
            (cfg_.getVal(generateDiskGroup(diskId), "backup_mode"));
    }

    /**
     * Check the specified mode is the same with [disk "`diskId`"] bacukp_mode.
     */
    public boolean isBackupModeSame(int diskId, BackupMode mode)
    {
        BackupMode thisMode = this.getBackupMode(diskId);
        if (thisMode == null || mode == null) { return false; }
        return thisMode == mode;
    }

    /**
     * Get [disk "`diskId`"] adapter_type as AdapterType.
     */
    public AdapterType getAdapterType(int diskId)
    {
        String typeStr =
            cfg_.getVal(generateDiskGroup(diskId), "adapter_type");

        if (typeStr == null) {
            return AdapterType.UNKNOWN;
        } else {
            return AdapterType.parse(typeStr);
        }
    }

    /**
     * Set adapter type [disk "`diskId`"] adapter_type.
     */
    public void setAdapterType(int diskId, AdapterType type)
    {
        cfg_.put(generateDiskGroup(diskId), "adapter_type", type.toString());
    }

    /**
     * Set [disk "`diskId`"] is_deleted_previous_dump flag.
     */
    public void setDeletedPreviousDump(int diskId, boolean isDeleted)
    {
        String val = (isDeleted ? "true" : "false");
        cfg_.put(generateDiskGroup(diskId), "is_deleted_previous_dump", val);
    }


    /**
     * Set [disk "`diskId`"] is_changed flag.
     */
    public void setIsChanged(int diskId, boolean isChanged)
    {
        String val = (isChanged ? "true" : "false");
        cfg_.put(generateDiskGroup(diskId), "is_changed", val);
    }

    /**
     * Get [disk "`diskId`"] is_changed flag.
     *
     * @param diskId
     * @return True if is_changed is true, False if is_changed is false,
     *         null if others (error).
     *
     */
    public Boolean isChanged(int diskId)
    {
        String str =
            cfg_.getVal(generateDiskGroup(diskId), "is_changed");

        if (str != null && FormatBool.isBool(str)) {
            if (FormatBool.toBool(str)) {
                return new Boolean(true);
            } else {
                return new Boolean(false);
            }
        }
        /* error */
        return null;
    }

    /**
     * Get ovf path.
     * This do not check the file exists or not.
     */
    public String getOvfPath()
    {
        String ovfFileName = cfg_.getVal(generation_, "ovf_filename");
        if (ovfFileName == null) { return null; }
        return getDirectory() + "/" + ovfFileName;
    }

    /**
     * Get a list of information of all vmdk files.
     * (sorted by diskId).
     */
    public List<Integer> getDiskIdList()
    {
        List<Integer> ret = new LinkedList<Integer>();
        
        /* Traverse the index (uuid -> diskId) */
        Group diskIndexGroup = this.generateDiskIndexGroup();
        List<Entry> entryList = cfg_.getAllEntries(diskIndexGroup);
        for (Entry entry : entryList) {
            String uuid = entry.getKey();
            String diskIdStr = entry.getVal();

            assert FormatInt.canBeInt(diskIdStr);
            int diskId = FormatInt.toInt(diskIdStr);
            assert diskId >= 0;
            assert getDiskIdWithUuid(uuid) == diskId;

            /* Now we get an available diskId */
            ret.add(new Integer(diskId));
        }

        Collections.sort(ret);
        return ret;
    }

    /**
     * Get number of succceeded vmdk dump.
     * [generation] num_vmdkdump_succeeded.
     *
     * @return Number of succeeded vmdk dump, or -1 in errors.
     */
    public int getNumOfSucceededVmdkdump()
    {
        int ret = cfg_.getValAsInt(generation_, "num_vmdkdump_succeeded");
        if (ret < 0) {
            logger_.warning
                ("num_vmdkdump_succeeded is not set or not an integer.");
        }
        return ret;
    }

    /**
     * Get number of failed vmdk dump.
     * [generation] num_vmdkdump_failed.
     *
     * @return Number of failed vmdk dump, or -1 in errors.
     */
    public int getNumOfFailedVmdkdump()
    {
        int ret = cfg_.getValAsInt(generation_, "num_vmdkdump_failed");
        if (ret < 0) {
            logger_.warning
                ("num_vmdkdump_failed is not set or not an integer.");
        }
        return ret;
    }

    /**
     * Increment [generation] num_vmdkdump_succeeded.
     */
    public void incrementNumOfSucceededVmdkdump()
    {
        int count = getNumOfSucceededVmdkdump();
        count ++;
        cfg_.put(generation_, "num_vmdkdump_succeeded",
                 (new Integer(count)).toString());
    }

    /**
     * Increment [generation] num_vmdkdump_failed.
     */
    public void incrementNumOfFailedVmdkdump()
    {
        int count = getNumOfFailedVmdkdump();
        count ++;
        cfg_.put(generation_, "num_vmdkdump_failed",
                 (new Integer(count)).toString());
    }

    /**
     * Set current time of [disk `diskId`] dump_begin_timestamp_ms.
     */
    public void setDumpBeginTimestamp(int diskId)
    {
        Group diskGroup = this.generateDiskGroup(diskId);
        long timestampMs = Calendar.getInstance().getTimeInMillis();
        cfg_.put(diskGroup, "dump_begin_timestamp_ms",
                 Long.toString(timestampMs));
    }

    /**
     * Set current timestamp of [disk `diskId`] dump_end_timestamp_ms.
     */
    public void setDumpEndTimestamp(int diskId)
    {
        Group diskGroup = this.generateDiskGroup(diskId);
        long timestampMs = Calendar.getInstance().getTimeInMillis();
        cfg_.put(diskGroup, "dump_end_timestamp_ms",
                 Long.toString(timestampMs));
    }

    /**
     * Get elapsed time in milliseconds to dump disk `diskId`.
     */
    public long getDumpElapsedTimeMs(int diskId)
    {
        Group diskGroup = this.generateDiskGroup(diskId);
        
        String beginStr = cfg_.getVal(diskGroup, "dump_begin_timestamp_ms");
        String endStr = cfg_.getVal(diskGroup, "dump_end_timestamp_ms");
        if (beginStr == null || endStr == null) {
            logger_.warning("beginStr or endStr is is null.");
            return -1;
        }
        if (FormatInt.canBeLong(beginStr) == false &&
            FormatInt.canBeLong(endStr)   == false) {

            logger_.warning("beginStr or endStr is not long value.");
            return -1;
        }

        long beginMs = FormatInt.toLong(beginStr);
        long endMs = FormatInt.toLong(endStr);

        return endMs - beginMs;
    }
    
}
