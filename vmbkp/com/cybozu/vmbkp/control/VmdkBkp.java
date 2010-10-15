/**
 * @file
 * @brief VmdkBkp, PrintStreamThread, ThreadLock.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.control;

import java.util.List;
import java.util.LinkedList;
import java.util.logging.Logger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;

import com.cybozu.vmbkp.config.FormatString;

import com.cybozu.vmbkp.util.Utility;
import com.cybozu.vmbkp.util.BackupMode;

import com.cybozu.vmbkp.profile.ConfigGlobal;
import com.cybozu.vmbkp.profile.ProfileGeneration;

import com.cybozu.vmbkp.control.VmArchiveManager;

/**
 * @brief Output to stream in parallel.
 */
class PrintStreamThread extends Thread
{
    /**
     * Logger
     */
    private static final Logger logger_ =
        Logger.getLogger(PrintStreamThread.class.getName());
        
    /**
     * Input reader.
     */
    private BufferedReader in_;

    /**
     * Output writer.
     */
    private BufferedWriter out_;

    /**
     * Constructor.
     */
    public PrintStreamThread(InputStream in, String outFileName)
    {    
        in_ = new BufferedReader(new InputStreamReader(in));
        BufferedWriter backupOut = null;
        try {
            backupOut = new BufferedWriter(new OutputStreamWriter(System.out));
            /* open append mode */
            out_ = new BufferedWriter(new FileWriter(outFileName, true));
        } catch (IOException e) {
            logger_.warning(Utility.toString(e));
            logger_.info("Fall back to stdout.");
            out_ = backupOut; /* fall back to stdout. */
        }
    }

    /**
     * Called by start() method automatically.
     */
    public void run()
    {
        logger_.info("PrintStreamThread start.");
        
        assert out_ != null;
        try {
            String str = null;
            while ((str = in_.readLine()) != null) {
                out_.write(str, 0, str.length());
                out_.newLine(); out_.flush();
            }
            out_.close();
            in_.close();
            
        } catch (IOException e) {
            logger_.warning(Utility.toString(e));
        }

        logger_.info("PrintStreamThread end.");
    }
}

/**
 * @brief Lock thread.
 */
class LockThread extends Thread
{
    /**
     * Logger
     */
    private static final Logger logger_ =
        Logger.getLogger(LockThread.class.getName());
        
    private Process proc_;
    private BufferedWriter out_;
    private BufferedReader in_;
    
    private ConfigGlobal cfgGlobal_;

    private final Lock mutex_ = new ReentrantLock();
    private final Condition isLockCond_ = mutex_.newCondition();
    private volatile boolean isLock_ = false;
    private final Condition isUnlockCond_ = mutex_.newCondition();
    private volatile boolean isUnlock_ = false;
    

    public LockThread(ConfigGlobal cfgGlobal) {

        assert (cfgGlobal != null);
        cfgGlobal_ = cfgGlobal;
    }

    public void lock() {

        start();
        waitForLockDone();
    }
    
    public void unlock() {

        mutex_.lock();
        try {
            isUnlock_ = true;
            isUnlockCond_.signal();

        } finally {
            mutex_.unlock();
        }
    }

    /**
     * For master.
     */
    private void waitForLockDone() {

        mutex_.lock();
        try {
            while (! isLock_) {
                isLockCond_.await();
            }
        } catch (InterruptedException ignored) {
        } finally {
            mutex_.unlock();
        }
    }

    /**
     * For worker.
     */
    private void notifyLockDone() {

        mutex_.lock();
        try {
            isLock_ = true;
            isLockCond_.signal();
            
        } finally {
            mutex_.unlock();
        }
    }
    
    /**
     * For worker.
     */
    private void recvLocked() throws IOException {

        String locked = in_.readLine();
        if (! locked.equals("LOCKED")) {
            String msg = "Locker did not say LOCKED.";
            logger_.warning(msg);
            throw new IOException(msg);
        }
    }

    /**
     * For worker.
     */
    private void sendUnlock() throws IOException {

        String unlock = "UNLOCK";
        out_.write(unlock, 0, unlock.length());
        out_.newLine(); out_.flush();
    }

    /**
     * For worker.
     */
    private void recvUnlocked() throws IOException {

        String unlocked = in_.readLine();
        if (! unlocked.equals("UNLOCKED")) {
            String msg = "Locker did not say UNLOCKED.";
            logger_.warning(msg);
            throw new IOException(msg);
        }
    }

    /**
     * For worker.
     */
    private void waitUnlockCalled() {
        
        mutex_.lock();
        try {
            /* Wait for unlock() called */
            while (! isUnlock_) {
                isUnlockCond_.await();
            }
        } catch (InterruptedException ignored) {
        } finally {
            mutex_.unlock();
        }
    }
    
    @Override
    public void run() {

        /* Master calls lock() to start this thread. */
        String vmdkBkp = cfgGlobal_.getVmdkBkpPath();
        if (vmdkBkp == null) {
            logger_.severe("vmdkbkp path is not available.");
            return;
        }
        String[] cmd = new String[] { vmdkBkp, "lock" };
        
        /* Execute lock command. */
        try {
            proc_ = Runtime.getRuntime().exec(cmd);
        } catch (IOException e) {
            logger_.severe("execution of vmdkbkp lock command failed.");
            return;
        }

        out_ = new BufferedWriter
            (new OutputStreamWriter(proc_.getOutputStream()));
        in_  = new BufferedReader
            (new InputStreamReader(proc_.getInputStream()));

        try {
            recvLocked();
            notifyLockDone();
            waitUnlockCalled();
            sendUnlock();
            recvUnlocked();

        } catch (IOException e) {

            logger_.warning("IO exception" + e.toString() + ".");
            proc_.destroy();
            return;
        }
        try {
            proc_.waitFor();
        } catch (InterruptedException ignored) {}
    }
}

/**
 * @brief Wrapper to call vmdkbkp command-line tool
 * to backup/restore local/remote vmdk file.
 */
public class VmdkBkp
{
    /**
     * Logger
     */
    private static final Logger logger_ =
        Logger.getLogger(VmdkBkp.class.getName());
    
    /**
     * A wrapper of Runtime.exec().
     *
     * @param command Command list.
     * @param workDirStr Working directory.
     * @return True when the command returns with status 0, or false.
     */
    private static boolean execCommand(List<String> command, String workDirStr,
                                       String outFilePath, String errFilePath)
    {
        try {
            /* return value */
            int ret = -1;

            /* command line */
            String[] commandStrs = command.toArray(new String[0]);

            /* working directory */
            File workDir = new File(workDirStr);

            InputStream stdout = null;
            InputStream stderr = null;
            Process proc = null;
        
            if (workDir.exists() && workDir.isDirectory()) {
                /* good */
                proc = Runtime.getRuntime().exec(commandStrs, null, workDir);
                stdout = proc.getInputStream();
                stderr = proc.getErrorStream();

                (new PrintStreamThread(stdout, outFilePath)).start();
                (new PrintStreamThread(stderr, errFilePath)).start();
        
                ret = proc.waitFor();
                /* proc.exitValue(); */
                logger_.info(String.format("exitvalue: %d\n", ret));

                return ret == 0;
            
            } else {
                /* error */
                throw new VmdkBkpFailedException();
            }

        } catch (VmdkBkpFailedException e) {
            logger_.warning(Utility.toString(e));
            return false;
        } catch (IOException e) {
            logger_.warning(Utility.toString(e));
            return false;
        } catch (InterruptedException e) {
            logger_.warning(Utility.toString(e));
            return false;
        }
    }

    /**
     * Execute full/differential/incremental dump.
     *
     * @param mode Backup mode.
     * @param vmMoref Moref of the virtual machine.
     * @param vmArcMgr Archive manager.
     * @param isSan True if you use SAN transfer.
     * @param diskId disk id to backup.
     */
    public static boolean doDump
        (BackupMode mode,
         String vmMoref,
         VmArchiveManager vmArcMgr,
         int diskId,
         boolean isSan)
    {
        if (vmMoref == null ||
            vmArcMgr== null) {
            logger_.warning
                (String.format
                 ("doDump(): some config data is null."));
            return false;
        }

        logger_.info(String.format("mode is %s.", mode.toString()));

        /* Get config/profile. */
        ConfigGlobal cfgGlobal = vmArcMgr.getConfigGlobal();
        ProfileGeneration profGen = vmArcMgr.getTargetGeneration();
        assert cfgGlobal != null;
        assert profGen != null;
        
        
        String vmdkBkp = cfgGlobal.getVmdkBkpPath();
        assert vmdkBkp != null;

        List<String> cmds = new LinkedList<String>();
        //cmds.add("echo");
        cmds.add(vmdkBkp);
        cmds.add("dump");

        cmds.add("--mode");
        if (mode == BackupMode.UNKNOWN) {
            logger_.warning("backup mode is unknown.");
            return false;
        }
        cmds.add(mode.toString());

        cmds.add("--server");
        cmds.add(cfgGlobal.getServername());
        cmds.add("--username");
        cmds.add(cfgGlobal.getUsername());
        cmds.add("--password");
        cmds.add(cfgGlobal.getPassword());

        cmds.add("--vm");
        cmds.add(vmMoref);
        
        cmds.add("--snapshot");
        cmds.add(profGen.getSnapshotMoref());

        cmds.add("--remote");
        cmds.add(profGen.getRemoteDiskPath(diskId));

        if (isSan) { cmds.add("--san"); }

        String prevDumpPath = null;
        if (mode == BackupMode.DIFF || mode == BackupMode.INCR) {
            cmds.add("--dumpin"); /* dumpIn */
            prevDumpPath = vmArcMgr.getPrevDumpPath(diskId);
            cmds.add(prevDumpPath);
        }
        cmds.add("--dumpout"); /* dumpOut */
        cmds.add(profGen.getDumpOutFileName(diskId));

        String prevDigestPath = null;
        if (mode == BackupMode.DIFF || mode == BackupMode.INCR) {
            cmds.add("--digestin"); /* digestIn */
            prevDigestPath = vmArcMgr.getPrevDigestPath(diskId);
            cmds.add(prevDigestPath);
        }
        cmds.add("--digestout"); /* digestOut */
        cmds.add(profGen.getDigestOutFileName(diskId));
        
        if (mode == BackupMode.INCR) {
            cmds.add("--bmpin"); /* bmpIn */
            cmds.add(profGen.getBmpInFileName(diskId));
        }
        if (mode == BackupMode.DIFF || mode == BackupMode.INCR) {
            cmds.add("--rdiffout"); /* rdiffOut */
            cmds.add(profGen.getRdiffOutFileName(diskId));
        }

        String joined = FormatString.join(cmds, " ");
        logger_.info(String.format("exec: %s\n", joined)); /* debug */
        //String squated = FormatString.toSingleQuatedString(joined);
        //logger_.info(String.format("squated: %s\n", squated)); /* debug */
        
        List<String> cmds2 = new LinkedList<String>();
        cmds2.add("/bin/sh");
        cmds2.add("-c");
        cmds2.add(joined);
        
        /* get working directory */
        String workDir = profGen.getDirectory();

        /* prepare output files for stdout/stderr. */
        String outFilePath = workDir + "/" + diskId + ".log";
        String errFilePath = workDir + "/" + diskId + ".err";
        
        /* execute the command */
        boolean ret = execCommand(cmds2, workDir, outFilePath, errFilePath);

        logger_.info("doDump " + (ret ? "succeeded" : "failed"));
        return ret;
    }

    /**
     * Execute restore vmdk file.
     *
     * @param vmArcMgr Archive manager.
     * @param diskId disk id to restore.
     * @param vmMoref moref of the virtual machine to restore.
     * @param remoteVmdkPath remote path of vmdk file to restore.
     * @param snapMoref snapshot moref to restore.
     * @param isSan True if you use SAN transfer.
     * @return ture in success, false in failure.
     */
    public static boolean doRestore
        (VmArchiveManager vmArcMgr,
         int diskId,
         String vmMoref,
         String remoteVmdkPath,
         String snapMoref,
         boolean isSan)
    {
        /* Check each parameter. */
        if (vmArcMgr == null ||
            diskId < 0 ||
            vmMoref == null ||
            remoteVmdkPath == null) {

            logger_.warning("doRestore(): some parameters are null.");
            return false;
        }

        /* Get config/profile. */
        ConfigGlobal cfgGlobal = vmArcMgr.getConfigGlobal();
        ProfileGeneration profGen = vmArcMgr.getTargetGeneration();

        /* Get the path list of archive files to restore
           the vmdk in the generation. */
        List<String> pathList =
            vmArcMgr.getDumpPathListForRestore(diskId);
        if (pathList == null) {
            logger_.warning("doRestore(): pathList is null" + 
                            " then the vmdk restore is failed.");
            return false;
        }
        /* debug */
        StringBuffer sb = new StringBuffer();
        sb.append("-----pathlist-----\n");
        for (String path : pathList) {
            sb.append(path); sb.append("\n");
        }
        sb.append("------------------\n");
        logger_.info(sb.toString());

        /* Prepare vmdkbkp command line. */
        String vmdkBkp = cfgGlobal.getVmdkBkpPath();
        assert vmdkBkp != null;

        List<String> cmds = new LinkedList<String>();
        //cmds.add("echo");
        cmds.add(vmdkBkp);
        cmds.add("restore");

        cmds.add("--server");
        cmds.add(cfgGlobal.getServername());
        cmds.add("--username");
        cmds.add(cfgGlobal.getUsername());
        cmds.add("--password");
        cmds.add(cfgGlobal.getPassword());

        cmds.add("--vm");
        cmds.add(vmMoref);
        cmds.add("--snapshot");
        cmds.add(snapMoref);
        cmds.add("--remote");
        cmds.add(FormatString.toQuatedString(remoteVmdkPath));

        if (isSan) { cmds.add("--san"); }

        /* These are required to restoring empty thin vmdk. */
        String digestPath = vmArcMgr.getDigestPathForCheck(diskId);
        cmds.add("--digestin");
        cmds.add(digestPath);
        cmds.add("--omitzeroblock");

        /* All dump/rdiff path in line. */
        cmds.addAll(pathList);
        
        String joined = FormatString.join(cmds, " ");
        logger_.info(String.format("exec: %s\n", joined)); /* debug */

        List<String> cmds2 = new LinkedList<String>();
        cmds2.add("/bin/sh");
        cmds2.add("-c");
        cmds2.add(joined);

        /* workDir is not required indeed
           because all files are specified with full path. */
        String workDir = profGen.getDirectory();
        int genId = profGen.getGenerationId();
        String commonFilename =
            "vmdkbkp.restore." + vmMoref + "." + genId + "." + diskId;
        String outFilePath = workDir + "/" + commonFilename + ".log";
        String errFilePath = workDir + "/" + commonFilename + ".err";
            
        boolean ret = execCommand(cmds2, workDir, outFilePath, errFilePath);
        if (ret) {
            logger_.info("vmdkbkp command succeeded.");
        } else {
            logger_.info("vmdkbkp command failed.");
            return false;
        }

        /* We should check the restored remote vmdk file comparing with
           the corresponding digest file.
           Currently the feature is not implemented. */

        logger_.info("The vmdk is successfuly restored.");
        return true;
    }

    /**
     * Execute check dump/rdiff with digest file.
     *
     * @param vmArcMgr ArchiveManager.
     * @param diskId disk id to restore.
     * @param isDryRun True if you do not check archive contents.
     * @return ture in success, false in failure.
     */
    public static boolean doCheck
        (VmArchiveManager vmArcMgr, int diskId, boolean isDryRun)
    {
        /* Check each parameter. */
        if (vmArcMgr == null || diskId < 0 ) {
            logger_.warning("doCheck(): some parameters are null.");
            return false;
        }

        ConfigGlobal cfgGlobal_ = vmArcMgr.getConfigGlobal();
        if (cfgGlobal_ == null) { return false; }
        ProfileGeneration profGen = vmArcMgr.getTargetGeneration();
        if (profGen == null) { return false; }

        String vmMoref = vmArcMgr.getMoref();

        /* Get path list to generate the target vmdk file. */
        List<String> pathList =
            vmArcMgr.getDumpPathListForRestore(diskId);
        /* debug */
        StringBuffer sb = new StringBuffer();
        sb.append
            (String.format("----- Path list for disk %d -----\n", diskId));
        for (String path : pathList) {
            sb.append(path); sb.append("\n");
        }
        sb.append("------------------\n");
        System.out.print(sb.toString());
        
        /* Check all archive files is available. */
        boolean ret = true;
        for (String path: pathList) {
            /* Path string is quated. */
            path = FormatString.toUnquatedString(path);
            if ((new File(path)).canRead() == false) {
                System.out.printf("File %s is not available.\n", path);
                ret = false;
            }
        }

        String digestPath = vmArcMgr.getDigestPathForCheck(diskId);
        if ((digestPath != null &&
             (new File(digestPath)).canRead()) == false) {

            System.out.printf("File %s is not available.\n", digestPath);
            ret &= false;
        }
        if (ret == false) { return false; }


        String vmdkBkp = cfgGlobal_.getVmdkBkpPath();
        assert vmdkBkp != null;
        
        /* Really check archive contents. */
        List<String> cmds = new LinkedList<String>();
        //cmds.add("echo");
        cmds.add(vmdkBkp);
        cmds.add("check");

        cmds.add("--digestin");
        cmds.add(digestPath);

        for (String path: pathList) { cmds.add(path); }
        String joined = FormatString.join(cmds, " ");
        logger_.info(String.format("exec: %s\n", joined));
        
        List<String> cmds2 = new LinkedList<String>();
        cmds2.add("/bin/sh");
        cmds2.add("-c");
        cmds2.add(joined);
        String workDir = profGen.getDirectory();
        int genId = profGen.getGenerationId();
        String commonFilename =
            "vmdkbkp.check." + vmMoref + "." + genId + "." + diskId;
        String outFilePath = workDir + "/" + commonFilename + ".log";
        String errFilePath = workDir + "/" + commonFilename + ".err";

        if (isDryRun) {
            System.out.printf("dryrun: %s\n", joined);
            ret = true;
        } else {
            ret = execCommand(cmds2, workDir, outFilePath, errFilePath);
            if (ret) {
                logger_.info("vmdkbkp check OK.");
                System.out.println("Check OK.");
            } else {
                logger_.info("vmdkbkp check NG.");
                System.out.println("Check NG.");
            }
        }
        
        return ret;
    }

    /**
     * Lock thread.
     */
    private static LockThread lockThread_ = null;
    
    /**
     * Lock the lock file shared with vmdkbkp processes.
     */
    public static void lock(ConfigGlobal cfgGlobal)
    {
        if (lockThread_ != null) { return; }

        lockThread_ = new LockThread(cfgGlobal);
        lockThread_.lock();
    }

    /**
     * unlock the lock file shared with vmdkbkp processes.
     */
    public static void unlock()
    {
        if (lockThread_ == null) { return; }
        lockThread_.unlock();
        lockThread_ = null;
    }
}
