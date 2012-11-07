/**
 * @file
 * @brief Implementation of Command class.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#define _FILE_OFFSET_BITS 64
#define __STDC_FORMAT_MACROS

#include <getopt.h>
#include <inttypes.h>
#include <unistd.h>
#include <signal.h>

#include "vddk_wrapper.hpp"
#include "archive_manager.hpp"
#include "ipc_lock_manager.hpp"
#include "command.hpp"

/**
 * For signal.
 */
static bool isSignal_ = false;

void signalHandler(int)
{
    isSignal_ = true;
}

/******************************************************************************
 * Command class methods.
 ******************************************************************************/

void Command::showHelp() const
{
    ::printf(
        "\n"
        "Vmdkbkp version %s\n"
        "Backup tool of virtual disk (vmdk) file for VMware vSphere.\n"
        "Copyright (C) 2009,2010 Cybozu Labs, Inc. All rights reserved.\n"
        "This software comes with ABSOLUTELY NO WARRANTY. This is free software,\n"
        "and you are welcome to modify and redistribute it under the GPL v2 license.\n"
        "\n"
        "Usage: %s command <options>\n"
        "\n"
        "Commands:\n"
        "  dump:    dump vmdk file into full/rdiff dump file(s) and a digest file.\n"
        "  restore: restore vmdk file from a dump file.\n"
        "  print:   print dump or digest.\n"
        "  check:   check dump file is correct compared with digest file.\n"
        "  digest:  make digest file from dump file.\n"
        "  merge:   merge two or more dump/rdiff files.\n"
        "  rdiff:   (not supported yet) make binary diff of two dump files.\n"
        "  lock:    lock with lockfile to execute critical path.\n"
        "  help:    print this message.\n"
        "\n"
        "Input/output options:\n"
        "  --dumpin <filename>:    input vmdk dump or rdiff\n"
        "  --digestin <filename>:  input vmdk digest\n"
        "  --dumpout <filename>:   output vmdk dump\n"
        "  --digestout <filename>: output vmdk digest\n"
        "  --bmpin <filename>:     changed block bitmap\n"
        "  --rdiffout <filename>:  output vmdk rdiff\n\n"
        "Required input/output for each command:\n"
        "  dump --mode full: --dumpout and --digestout\n"
        "  dump --mode diff: all input/output options except --bmpin\n"
        "  dump --mode incr: all six input/output options\n"
        "  restore: --digestin\n"
        "           Just specify input dump/rdiff files in line.\n"
        "           digestin will be required with --omitzeroblock only.\n"
        "  print:   --dumpin or --digestin\n"
        "  check:   --digestin\n"
        "           Just specify input dump/rdiff files in line.\n"
        "  digest:  --dumpin and --digestout\n"
        "  merge:   --dumpout for full dump or --rdiffout for rdiff\n"
        "           Do not use input options, \n"
        "           Just specify input dump/rdiff files in line.\n"
        "\n"
        "Options for dump/restore command:\n"
        "  --local <vmdk file>:\n"
        "      Target local vmdk file.\n"
        "      You can omit --local, just specify vmdk file after the command.\n"
        "  --remote <vmdk file>:\n"
        "      Target remote vmdk file.\n"
        "      Either --local or --remote is required.\n"
        "  --server <server name>:\n"
        "      Name of vSphere server (vCenter or ESX(i)). \n"
        "      Required with --remote option.\n"
        "  --username <name>:\n"
        "      User name to login vSphere server.\n"
        "      Required with --remote option.\n"
        "  --password <pass>:\n"
        "      Password to login vSphere server.\n"
        "      Required with --remote option.\n"
        "  --vm <moref>:\n"
        "      Virtual machine identifier as moref having the target vmdk.\n"
        "      Required with --remote option.\n"
        "  --snapshot <moref>:\n"
        "      Snapshot moref having the target vmdk.\n"
        "      Required for SAN transfer with --remote option.\n"
        "  --config <path>:\n"
        "      Specify VDDK config path.\n"
        "  --libdir <path>:\n"
        "      Specify VDDK libdir explicitly.\n"
        "  --san:\n"
        "      Try to use SAN transfer.\n"
        "      Causion!!! several limitations for restore.\n"
        "\n"
        "Options for dump command:\n"
        "  --mode <mode>:\n"
        "      Specify dump mode. Choose full, diff, or incr.\n"
        "  --blocksize <size>:\n"
        "      Block size for read/write operations.\n"
        "      This is optional with  --mode full option.\n"
        "  --nread <size>:\n"
        "      Number of blocks to read. This is for test.\n"
        "\n"
        "Options for restore command:\n"
        "  --create:\n"
        "      Create vmdk file before restoring.(this may not work.)\n"
        "  --metadata:\n"
        "      Write metadata explicitly.\n"
        "  --omitzeroblock:\n"
        "      Do not write all-zero blocks for thin vmdk.\n"
        "      You should use this option only for restoring to empty vmdk.\n"
        "\n"
        "Other options:\n"
        "  --help: \n"
        "      Show this message.\n"
        "  --shared: \n"
        "      Use shared lock in lock command.\n"
        "\n",
        cfg_.versionStr.c_str(),
        cfg_.programName.c_str()
        );
}

bool Command::run()
{
    /* To finalize VDDK on exit. */
    if (cfg_.cmd == CMD_DUMP ||
        cfg_.cmd == CMD_DUMPTEST ||
        cfg_.cmd == CMD_RESTORE) {

        if (::signal(SIGTERM, signalHandler) == SIG_ERR ||
            ::signal(SIGINT, signalHandler) == SIG_ERR) {

            WRITE_LOG0("regist signal handler failed.\n");
            return false;
        }
    }
    
    try {
        switch (cfg_.cmd) {
        case CMD_DUMP:     doDumpFork(); break;
        case CMD_DUMPTEST: doDumpTest(); break;
        case CMD_RESTORE:  doRestore();  break;
        case CMD_PRINT:    doPrint();    break;
        case CMD_CHECK:    doCheck();    break;
        case CMD_DIGEST:   doDigest();   break;
        case CMD_MERGE:    doMerge();    break;
        case CMD_RDIFF:    doRdiff();    break;
        case CMD_LOCK:     doLock();     break;
        case CMD_HELP:     showHelp();   break;
        default:
            ::printf("Error: specify a valid command.\n");
            showHelp();
        }
    } catch (const VixException& e) {
        e.writeLog();
        return false;
    } catch (const ExceptionStack& e) {
        WRITE_LOG0("%s\n", e.sprint().c_str());
        return false;
    } catch (const MyException& e) {
        WRITE_LOG0("%s\n", e.sprint().c_str());
        return false;
    } catch (const std::string& e) {
        WRITE_LOG0("%s\n", e.c_str());
        return false;
    }

    return true;
}

bool Command::parseCommandlineOptions(int argc, char * const argv[])
{
    cfg_.programName = "vmdkbkp";
    cfg_.versionStr = VMDKBKP_VERSION;
    
    cfg_.isRemote = 0;
    cfg_.isCreate = 0;
    cfg_.blocksize = DEFAULT_BLOCK_SIZE;

    while (1) {
        int optionIndex;
        static struct option longOptions[] = {
            {"local", 1, 0, 'l'},
            {"remote", 1, 0, 'r'},
            {"server", 1, 0, 's'},
            {"username", 1, 0, 'u'},
            {"password", 1, 0, 'p'},
            {"vm", 1, 0, 'v'},
            {"snapshot", 1, 0, '0'},
            {"create", 0, 0, 'c'},
            {"blocksize", 1, 0, 'b'},
            {"config", 1, 0, 'f'},
            {"libdir", 1, 0, 'd'},
            {"mode", 1, 0, '1'},
            {"dumpin", 1, 0, '3'},
            {"digestin", 1, 0, '4'},
            {"dumpout", 1, 0, '5'},
            {"digestout", 1, 0, '6'},
            {"bmpin", 1, 0, '7'},
            {"rdiffout", 1, 0, '8'},
            {"nread", 1, 0, 'a'},
            {"shared", 0, 0, 1},
            {"san", 0, 0, 2},
            {"omitzeroblock", 0, 0, 'z'},
            {"metadata", 0, 0, 'm'},
            {"help", 0, 0, 'h'},
            {0, 0, 0, 0}
        };

        int c = getopt_long(argc, argv,
                            "l:r:s:u:p:v:0:c:b:f:d:1:3:4:5:6:7:8:a:hzm",
                            longOptions, &optionIndex);
        if (c == -1) {
            break;
        }

        switch (c) {
        case 'l': /* local */
            cfg_.isRemote = 0;
            cfg_.vmdkPath = strdup(optarg);
            break;
        case 'r': /* remote */
            cfg_.isRemote = true;
            cfg_.vmdkPath = strdup(optarg);
            break;
        case 's': /* server */
            cfg_.server = strdup(optarg);
            break;
        case 'u': /* username */
            cfg_.username = strdup(optarg);
            break;
        case 'p': /* password */
            cfg_.password = strdup(optarg);
            break;
        case 'v': /* vm */
            cfg_.vmMorefStr = strdup(optarg);
            break;
        case '0': /* snapshot */
            cfg_.snapshotStr = strdup(optarg);
            break;
        case 'c': /* create */
            cfg_.isCreate = true;
            break;
        case 'b': /* blocksize */
            cfg_.blocksize = atol(optarg);
            break;
        case 'f': /* configPath */
            cfg_.configPath = strdup(optarg);
            break;
        case 'd': /* libDir */
            cfg_.libDir = strdup(optarg);
            break;
        case '1': /* mode */
            cfg_.setMode(optarg);
            break;
        case 'z': /* omitzeroblock */
            cfg_.isWriteZeroBlock = false;
            break;
        case 'm': /* metadata */
            cfg_.isWriteMetadata = true;
            break;
        case '3': /* dumpIn */
            cfg_.dumpInFileName = strdup(optarg);
            break;
        case '4': /* digestIn */
            cfg_.digestInFileName = strdup(optarg);
            break;
        case '5': /* dumpOut */
            cfg_.dumpOutFileName = strdup(optarg);
            break;
        case '6': /* digestOut */
            cfg_.digestOutFileName = strdup(optarg);
            break;
        case '7': /* bmpIn */
            cfg_.bmpInFileName = strdup(optarg);
            break;
        case '8': /* rdiffOut */
            cfg_.rdiffOutFileName = strdup(optarg);
            break;
        case 'a': /* nread */
            cfg_.numReadBlockForTest = atol(optarg);
            break;
        case 1: /* shared */
            cfg_.useSharedLock = true;
            break;
        case 2: /* san */
            cfg_.isUseSan = true;
            break;
        case 'h': /* help */
        default:
            showHelp();
            return false;
        }

    } /* while */

    /* get command */
    if (optind >= argc) {
        ::printf("Error: specify a command.\n");
        showHelp();
        return false;
    }
    cfg_.cmdStr = strdup(argv[optind ++]);
    cfg_.setCmd(cfg_.cmdStr);

    /* For merge/check/restore command. */
    if (cfg_.cmd == CMD_MERGE ||
        cfg_.cmd == CMD_CHECK ||
        cfg_.cmd == CMD_RESTORE) {
        
        while (optind < argc) {
            cfg_.archiveList.push_back(strdup(argv[optind ++]));
        }
    } else {
        /* When "--local" option is omitted. */
        if (optind < argc) {
            if (! cfg_.isRemote) {
                cfg_.vmdkPath = strdup(argv[optind ++]);
            }
        }
    }

    /* When unknown options are detected. */
    if (optind != argc) {
        ::printf("Error: invalid option(s) detected.\n");
        showHelp();
        return false;
    }

    /* blocksize must be the integral multiple of sector size */
    if (cfg_.blocksize % cfg_.sectorSize != 0) {
        ::printf("block size is not the integral multiple of sector size %d\n",
               cfg_.sectorSize);
        return false;
    }
    cfg_.nSectorsPerBlock = cfg_.blocksize / cfg_.sectorSize;

    /* mode check */
    if (cfg_.cmd == CMD_DUMP && cfg_.mode == DUMPMODE_UNKNOWN) {
        ::printf("You must specify --mode option in dump command.\n");
        return false;
    }

    return true;
}

/******************************************************************************
 * doDump command and related functions.
 ******************************************************************************/

void Command::doDumpNoFork()
{
    WRITE_LOG1("doDump() called.\n");
    assert(cfg_.cmd == CMD_DUMP);

    /* Initialize and open vmdk */
    WRITE_LOG1("********** Initialize **********\n");
    VddkManager vddkMgr(cfg_, true, true); /* read only, try san */
    VmdkManager vmdkMgr(vddkMgr);
    vmdkMgr.open();

    WRITE_LOG1("Current transport mode: %s\n",
               vmdkMgr.getTransportMode().c_str());

    VmdkDumpHeader prevDumpH; /* for dumpIn */
    VmdkDumpHeader currDumpH; /* for dumpOut */
    VmdkDigestHeader prevDigestH; /* for digestIn */
    VmdkDigestHeader currDigestH; /* for digestOut */
    VmdkDumpHeader rdiffH; /* for rdiffOut */

    WRITE_LOG1("********** VMDK Info **********\n");
    VmdkInfo vmdkInfo;
    vmdkMgr.readVmdkInfo(vmdkInfo);

    WRITE_LOG1("********** VMDK metadata **********\n");
    vmdkMgr.readMetadata(currDumpH.getMetadata());

    WRITE_LOG1("********** Initialize archive header files. **********\n");
    ArchiveManagerForDump arcMgr(cfg_);
    arcMgr.readHeaders(prevDumpH, prevDigestH);
    arcMgr.setHeaders(vmdkInfo, prevDumpH, prevDigestH,
                       currDumpH, currDigestH, rdiffH);
    arcMgr.writeHeaders(currDumpH, currDigestH, rdiffH);

    Bitmap changedBlockBitmap;
    arcMgr.readChangedBlockBitmap(changedBlockBitmap);
    if (cfg_.mode == DUMPMODE_INCR) {
        MY_CHECK_AND_THROW(
            currDumpH.getDiskSize() == changedBlockBitmap.size(),
            "doDump(): Bitmap size is not disk size.");
    }

    /* Dump each block. */
    VmdkDumpBlock prevDumpB(cfg_.blocksize), currDumpB(cfg_.blocksize);
    VmdkDigestBlock prevDigestB, currDigestB;

    const size_t maxOft = (cfg_.numReadBlockForTest == 0
                           ? vmdkInfo.nBlocks
                           : cfg_.numReadBlockForTest);
    
    WRITE_LOG1("********** VMDK read **********\n");
    double timeBegin = getTime();

    /* To calculate realtime throughput */
    double bulkTimeBegin = timeBegin;
    double bulkTimeEnd;

    for (uint64 oft = 0; oft < maxOft; oft ++) {

        if (isSignal_) {
            std::string msg("Signal received.\n");
            WRITE_LOG0("%s\n", msg.c_str()); throw msg;
        }
        
        /* Retry times when block read of vmdk is failed. */
        int tryalTimes = 30;
        
        /* Read from dumpIn and digestIn */
        arcMgr.readFromStreams(prevDumpB, prevDigestB);

        bool maybeChanged =
            (cfg_.mode != DUMPMODE_INCR) ||
            changedBlockBitmap[oft];
        
        if (maybeChanged) {
            /* Read a block from vmdk file */
            while (tryalTimes > 0) {
                try {
                    vmdkMgr.readBlock(oft, currDumpB.getBuf());
                    break;
                } catch (const VixException& e) {
                    WRITE_LOG0("%s\n", e.sprint().c_str());
                    tryalTimes --;
                    if (tryalTimes <= 0) {
                        vmdkMgr.close(); throw;
                    }
                    if (tryalTimes <= 28) {
                        WRITE_LOG0("Reset vddk and retry read block %llu.\n",
                                   static_cast<unsigned long long>(oft));
                        vmdkMgr.close();
                        vddkMgr.reset();
                        vmdkMgr.open();
                        WRITE_LOG0("Reset vddk done.\n");
                    }
                }
            }
            
            currDumpB.setIsAllZero();
            currDumpB.setOffset(oft);
        } else {
            /* Read the block from dumpIn instead of vmdk file */
            currDumpB.copyDataFrom(prevDumpB);
            assert(currDumpB.getOffset() == oft);
        }
        
        /* Calc MD5 of the block. */
        currDigestB.set(currDumpB);
        
        /* Write to output dump/digest/rdiff. */
        bool isChanged = arcMgr.writeToStreams
            (prevDumpB, prevDigestB,
             currDumpB, currDigestB);
        
        /* Show progress. */
        const size_t INTERVAL = 64;
        if (currDumpB.getOffset() % INTERVAL == 0) {
            std::cout << currDumpB.getOffset() << " ";
        }
        /* if (currDumpB.getOffset() % 8 == 0) */
        {
            if (maybeChanged) {
                if (isChanged) { std::cout << "o"; }
                else           { std::cout << "."; }
            } else             { std::cout << "_"; }
        }
        if (currDumpB.getOffset() % INTERVAL == INTERVAL - 1) {
            bulkTimeEnd = getTime();
            std::cout << " "
                      << INTERVAL / (bulkTimeEnd - bulkTimeBegin)
                      << "blks/s\n";
            std::cout.flush();
            bulkTimeBegin = bulkTimeEnd;
        }

    } /* for */
        
    /* Elapsed time */
    double timeEnd = getTime();
    ::printf("\nElapsed time to dump: %f sec\n", timeEnd - timeBegin);

    WRITE_LOG1("********** doDump() end **********\n");
}

void Command::doDumpFork()
{
    WRITE_LOG1("doDump() called.\n");
    assert(cfg_.cmd == CMD_DUMP);

    /* Initialize and open vmdk */
    WRITE_LOG1("********** Initialize **********\n");
    VddkController vddkCtrl(cfg_, true /* read only */, cfg_.isUseSan);
    vddkCtrl.start();
    vddkCtrl.open();

    WRITE_LOG1("Current transport mode: %s\n",
              vddkCtrl.getTransportMode().c_str());

    VmdkDumpHeader prevDumpH; /* for dumpIn */
    VmdkDumpHeader currDumpH; /* for dumpOut */
    VmdkDigestHeader prevDigestH; /* for digestIn */
    VmdkDigestHeader currDigestH; /* for digestOut */
    VmdkDumpHeader rdiffH; /* for rdiffOut */

    WRITE_LOG1("********** VMDK Info **********\n");
    VmdkInfo vmdkInfo;
    vddkCtrl.readVmdkInfo(vmdkInfo);

    WRITE_LOG1("********** VMDK metadata **********\n");
    vddkCtrl.readMetadata(currDumpH.getMetadata());

    WRITE_LOG1("********** Initialize archive header files. **********\n");
    ArchiveManagerForDump arcMgr(cfg_);
    arcMgr.readHeaders(prevDumpH, prevDigestH);
    arcMgr.setHeaders(vmdkInfo, prevDumpH, prevDigestH,
                       currDumpH, currDigestH, rdiffH);
    arcMgr.writeHeaders(currDumpH, currDigestH, rdiffH);

    Bitmap changedBlockBitmap;
    arcMgr.readChangedBlockBitmap(changedBlockBitmap);
    if (cfg_.mode == DUMPMODE_INCR) {
        MY_CHECK_AND_THROW(
            currDumpH.getDiskSize() == changedBlockBitmap.size(),
            "doDump(): Bitmap size is not disk size.");
    }

    /* Dump each block. */
    VmdkDumpBlock prevDumpB(cfg_.blocksize), currDumpB(cfg_.blocksize);
    VmdkDigestBlock prevDigestB, currDigestB;

    WRITE_LOG1("********** VMDK read **********\n");
    
    const size_t maxOft = (cfg_.numReadBlockForTest == 0
                           ? vmdkInfo.nBlocks
                           : cfg_.numReadBlockForTest);
    
    double timeBegin = getTime();

    /* To calculate realtime throughput */
    double bulkTimeBegin = timeBegin;
    double bulkTimeEnd;

    for (uint64 oft = 0; oft < maxOft; oft ++) {

        if (isSignal_) {
            std::string msg("Signal received.\n");
            WRITE_LOG0("%s\n", msg.c_str()); throw msg;
        }
        
        /* Retry times when block read of vmdk is failed. */
        int tryalTimes = 10;
        
        /* Read from dumpIn and digestIn */
        arcMgr.readFromStreams(prevDumpB, prevDigestB);

        bool maybeChanged =
            (cfg_.mode != DUMPMODE_INCR) ||
            changedBlockBitmap[oft];
        
        if (maybeChanged) {
            /* Read a block from vmdk file */
            while (tryalTimes > 0) {
                try {
                    vddkCtrl.readBlock(oft, currDumpB.getBuf());
                    break;
                } catch (const MyException& e) {
                    /*
                      Read block may fail when
                      first write to the vmdk by the power-on VM
                      after creating the snapshot during this dump.
                      This is why SCSI reservation conflict occurs
                      then all continuous accesses to the LUN
                      in this session will fail.
                    */
                    WRITE_LOG0("%s\n", e.sprint().c_str());
                    tryalTimes --;
                    if (tryalTimes <= 0) {
                        vddkCtrl.close(); vddkCtrl.stop(); throw;
                    }
                    if (tryalTimes <= 8) {
                        vddkCtrl.close();
                        WRITE_LOG0("Reset VDDK and retry read block %llu.\n",
                                   static_cast<unsigned long long>(oft));
                        arcMgr.pause();
                        bool ret = vddkCtrl.reset(true /* read only */,
                                                  cfg_.isUseSan);
                        if (! ret) {
                            WRITE_LOG0("Reset VDDK failed.\n");
                            vddkCtrl.close(); vddkCtrl.stop(); throw;
                        }
                        arcMgr.resume();
                        WRITE_LOG0("Reset VDDK done.\n");
                        vddkCtrl.open();
                    }
                }
            }
            currDumpB.setIsAllZero();
            currDumpB.setOffset(oft);
        } else {
            /* Read the block from dumpIn instead of vmdk file */
            currDumpB.copyDataFrom(prevDumpB);
            assert(currDumpB.getOffset() == oft);
        }
        
        /* Calc MD5 of the block. */
        currDigestB.set(currDumpB);
        
        /* Write to output dump/digest/rdiff. */
        bool isChanged = arcMgr.writeToStreams
            (prevDumpB, prevDigestB,
             currDumpB, currDigestB);
        
        /* Show progress. */
        const size_t INTERVAL = 64;
        if (currDumpB.getOffset() % INTERVAL == 0) {
            std::cout << currDumpB.getOffset() << " ";
        }
        {
            if (maybeChanged) {
                if (isChanged) { std::cout << "o"; }
                else           { std::cout << "."; }
            } else             { std::cout << "_"; }
        }
        if (currDumpB.getOffset() % INTERVAL == INTERVAL - 1) {
            bulkTimeEnd = getTime();
            std::cout << " "
                      << INTERVAL / (bulkTimeEnd - bulkTimeBegin)
                      << "blks/s\n";
            std::cout.flush();
            bulkTimeBegin = bulkTimeEnd;
        }

    } /* for */
        
    /* Elapsed time */
    double timeEnd = getTime();
    ::printf("\nElapsed time to dump: %f sec\n", timeEnd - timeBegin);

    WRITE_LOG1("********** doDump() end **********\n");
}

/******************************************************************************
 * doDumpTest command and related functions.
 ******************************************************************************/

void Command::doDumpTest()
{
    WRITE_LOG1("doDumpTest() called.\n");
    assert(cfg_.cmd == CMD_DUMPTEST);

    if (true) {
        doDumpTestFork();
    } else {
        doDumpTestNoFork();
    }
}

void Command::doDumpTestFork()
{
    /* Initialize and open vmdk */
    WRITE_LOG1("********** Initialize **********\n");

    VddkController vddkCtrl(cfg_, true, true); /* read only, try san */
    vddkCtrl.start();
    vddkCtrl.open();

    WRITE_LOG1("Current transport mode: %s\n",
              vddkCtrl.getTransportMode().c_str());

    WRITE_LOG1("********** VMDK Info **********\n");
    VmdkInfo vmdkInfo;
    vddkCtrl.readVmdkInfo(vmdkInfo);
    
    uint8 buf[cfg_.blocksize];

    const size_t maxOft = (cfg_.numReadBlockForTest == 0
                           ? vmdkInfo.nBlocks
                           : cfg_.numReadBlockForTest);
    
    double bulkTimeEnd, bulkTimeBegin;
    bulkTimeBegin = getTime();
    
    for (uint64 oft = 0; oft < maxOft; oft ++) {

        if (isSignal_) {
            std::string msg("Signal received.\n");
            WRITE_LOG0("%s\n", msg.c_str()); throw msg;
        }
        
        try {
            vddkCtrl.readBlock(oft, buf);
        } catch (const MyException& e) {
            vddkCtrl.close(); vddkCtrl.reset(true, true); vddkCtrl.open();
        }
        /* Show progress. */
        const size_t INTERVAL = 64;
        if (oft % INTERVAL == 0) {
            std::cout << oft << " ";
        }
        std::cout << 'o';
        if (oft % INTERVAL == INTERVAL - 1) {
            bulkTimeEnd = getTime();
            std::cout << " " << INTERVAL / (bulkTimeEnd - bulkTimeBegin)
                      << "blks/s\n";
            bulkTimeBegin = bulkTimeEnd;
        }
    }

}

void Command::doDumpTestNoFork()
{
    /* Initialize and open vmdk */
    WRITE_LOG1("********** Initialize **********\n");

    VddkManager vddkMgr(cfg_, true, true); /* read only, try san */
    VmdkManager vmdkMgr(vddkMgr);
    vmdkMgr.open();

    WRITE_LOG1("Current transport mode: %s\n",
               vmdkMgr.getTransportMode().c_str());
    
    WRITE_LOG1("********** VMDK Info **********\n");
    VmdkInfo vmdkInfo;
    vmdkMgr.readVmdkInfo(vmdkInfo);
    
    uint8 buf[cfg_.blocksize];

    double bulkTimeEnd, bulkTimeBegin;
    bulkTimeBegin = getTime();

    const size_t maxOft = (cfg_.numReadBlockForTest == 0
                           ? vmdkInfo.nBlocks
                           : cfg_.numReadBlockForTest);
    
    for (uint64 oft = 0; oft < maxOft; oft ++) {

        if (isSignal_) {
            std::string msg("Signal received.\n");
            WRITE_LOG0("%s\n", msg.c_str()); throw msg;
        }

        try {
            vmdkMgr.readBlock(oft, buf);
        } catch (const VixException& e) {
            vmdkMgr.reopen();
        }
        /* Show progress. */
        const size_t INTERVAL = 64;
        if (oft % INTERVAL == 0) {
            std::cout << oft << " ";
        }
        std::cout << 'o';
        if (oft % INTERVAL == INTERVAL - 1) {
            bulkTimeEnd = getTime();
            std::cout << " " << INTERVAL / (bulkTimeEnd - bulkTimeBegin)
                      << "blks/s\n";
            bulkTimeBegin = bulkTimeEnd;
        }
    }
}

/******************************************************************************
 * doRestore command and related functions.
 ******************************************************************************/

void Command::doRestoreOld()
{
    WRITE_LOG1("doRestoreOld() called.\n");
    assert(cfg_.cmd == CMD_RESTORE);

    /* Initialize managers. */
    VddkController vddkCtrl(cfg_, false, false);
    vddkCtrl.start();
    ArchiveManager arcMgr(cfg_);
    
    /* Read archive header data. */
    VmdkDumpHeader dumpH;
    arcMgr.readDumpHeader(dumpH);

    /* Create vmdk file if required. */
    if (cfg_.isCreate) {
        vddkCtrl.createVmdkFile(dumpH);
    }

    /* Open vmdk file. */
    vddkCtrl.open();
    
    /* SAN transfer mode does not support meta data write.
       You must specify --metadata option  explicitly
       to restore vmdk metadata. */
    if (cfg_.isWriteMetadata) {
        vddkCtrl.writeMetadata(dumpH.getMetadata());
    }

    /* Prepare a zero block. */
    ByteArray zeroBlock(dumpH.getBlockSize());
    for (ByteArray::iterator i = zeroBlock.begin();
         i != zeroBlock.end(); ++ i) { *i = 0; }

    /* Restore each block */
    VmdkDumpBlock dumpB(dumpH.getBlockSize());
    double timeBegin = getTime();
    while (arcMgr.canReadFromDump()) {
        
        /* Read block from dump file */
        arcMgr.readFromDump(dumpB);
        std::cout << "."; /* debug */

        /* Write the block to vmdk file */
        if (! dumpB.isAllZero() || cfg_.isWriteZeroBlock) {
            const uint8* buf;
            if (dumpB.isAllZero()) {
                buf = reinterpret_cast<const uint8 *>(&zeroBlock[0]);
            } else {
                buf = dumpB.getBufConst();
            }
            /* zero block is not recraimed by VMware automatically  */
            vddkCtrl.writeBlock(dumpB.getOffset(), buf);
        }
    } /* while */
    double timeEnd = getTime();
        
    /* Shrink if local */
    if (! cfg_.isRemote) {
        vddkCtrl.shrinkVmdk();
    }

    /* Show elapsed time */
    ::printf("Elapsed time to restore: %f sec\n", timeEnd - timeBegin);
}

void Command::doRestore()
{
    WRITE_LOG1("doRestore() called.\n");
    assert(cfg_.cmd == CMD_RESTORE);
    size_t nArchives = cfg_.archiveList.size();
    MY_CHECK_AND_THROW(nArchives >= 1,
                       "doRestore: one or more archives (dump or rdiff) "
                       "files are required.\n");
    assert(nArchives >= 1);

    /* Prepare multi-archive manager. */
    MultiArchiveManager mArcMgr(cfg_.archiveList);
    VmdkDumpHeader dumpH; mArcMgr.getDumpHeader(dumpH);

    /* Decide which transfer mode SAN or NBD to use for restore. */
    bool isFullRestore = dumpH.isFull();
    bool isSkipZeroBlock = ! cfg_.isWriteZeroBlock;
    bool isUseSanForRestore = isFullRestore && isSkipZeroBlock;
    
    if (cfg_.isUseSan && isUseSanForRestore) {
        /* Currently an efficient block allocate method for
           empty thin vmdk is not available.
           If such a method can be available in the future,
           You should implement allocateNonZeroBlock()
           by the method and use doRestoreSAN() method to restore
           vmdk file.

           If you use thick vmdk, you can use --san when ctkEnabled is not true.
           When ctkEnabled is true, do not use SAN transfer.
        */
        doRestoreSAN(mArcMgr);
    } else {
        doRestoreNBD(mArcMgr);
    }
}

void Command::doRestoreNBD(MultiArchiveManager& mArcMgr)
{
    WRITE_LOG1("doRestoreNBD() called.\n");
    assert(cfg_.cmd == CMD_RESTORE);

    /* Initialize managers. */
    mArcMgr.pause();
    VddkController vddkCtrl(cfg_, false, false);
    vddkCtrl.start();
    mArcMgr.resume();
    
    /* Read archive header data. */
    VmdkDumpHeader dumpH;
    mArcMgr.getDumpHeader(dumpH);

    /* Create vmdk file if required. */
    if (cfg_.isCreate) {
        vddkCtrl.createVmdkFile(dumpH);
    }

    /* Open vmdk file. */
    vddkCtrl.open();
    
    /* Write metadata if required.
       This may not needed to restore to new empty vmdk. */
    if (cfg_.isWriteMetadata) {
        vddkCtrl.writeMetadata(dumpH.getMetadata());
    }

    size_t blockSize = dumpH.getBlockSize();

    /* Restore each block. */
    double timeBegin = getTime();
    writeBlocksToVmdk(vddkCtrl, mArcMgr, blockSize);
    double timeEnd = getTime();
        
    /* Shrink if local */
    if (! cfg_.isRemote) {
        vddkCtrl.shrinkVmdk();
    }

    /* Show elapsed time */
    ::printf("Elapsed time to restore: %f sec\n", timeEnd - timeBegin);
}

void Command::doRestoreSAN(MultiArchiveManager& mArcMgr)
{
    WRITE_LOG1("doRestoreSAN() called.\n");
    assert(cfg_.cmd == CMD_RESTORE);

    size_t blockSize;
    {    
        /* Initialize vddk manager via NBD. */
        mArcMgr.pause();
        VddkController vddkCtrl(cfg_, false, false);
        vddkCtrl.start();
        mArcMgr.resume();
    
        /* Read archive header data. */
        VmdkDumpHeader dumpH;
        mArcMgr.getDumpHeader(dumpH);
        blockSize = dumpH.getBlockSize();
        
        /* Create vmdk file if required. */
        if (cfg_.isCreate) {
            vddkCtrl.createVmdkFile(dumpH);
        }

        /* Open vmdk file. */
        vddkCtrl.open();
    
        /* Write metadata if required.
           This may not needed to restore to new empty vmdk. */
        if (cfg_.isWriteMetadata) {
            vddkCtrl.writeMetadata(dumpH.getMetadata());
        }

        /* Allocate non-zero blocks for thin vmdk. */
        VmdkDigestHeader digestH;
        ArchiveManager arcMgr(cfg_);
        arcMgr.readDigestHeader(digestH);

        MY_CHECK_AND_THROW(isTheSameVMDK(dumpH, digestH),
                           "The specified digest is not "
                           "corresponding of the input dump file(s).");

        allocateNonZeroBlock(vddkCtrl, arcMgr, blockSize);

        /* Automatically,
           1. vmdk will be closed.
           2. vddk will be finalized.
           3. arcMgr will be finalized. */
    }

    assert(! cfg_.isWriteZeroBlock);
    
    /* Initialize vddk manager via  SAN. */
    mArcMgr.pause();
    VddkController vddkCtrl(cfg_, false, true);
    vddkCtrl.start();
    vddkCtrl.open();
    mArcMgr.resume();

    /* Restore each block. */
    double timeBegin = getTime();
    writeBlocksToVmdk(vddkCtrl, mArcMgr, blockSize);
    double timeEnd = getTime();

    /* Show elapsed time */
    ::printf("Elapsed time to restore: %f sec\n", timeEnd - timeBegin);
}

void Command::allocateNonZeroBlock(VddkController& vddkCtrl,
                                   ArchiveManager& arcMgr,
                                   size_t blockSize)
{
    WRITE_LOG1("allocateNonZeroBlock() called.\n");
    assert(cfg_.cmd == CMD_RESTORE);
    
    /* Temporal Digest block. */
    VmdkDigestBlock digestB;
    
    /* Prepare a zero block. */
    ByteArray zeroBlock(blockSize, 0);
    const uint8 *buf =
        reinterpret_cast<const uint8 *>(&zeroBlock[0]);

    typedef enum {NONE, ZERO} Wflag;
    Wflag flag = NONE;
    
    for (uint64 offset = 0; arcMgr.canReadFromDigest(); offset ++) {

        /* Read from digest. */
        arcMgr.readFromDigest(digestB);

        /* Write zero-block if digest says it's non-zero block. */
        if (digestB.isAllZero()) {
            flag = NONE;
        } else {
            vddkCtrl.writeBlock(offset, buf);
            flag = ZERO;
        }

        /* Show progress. */
        const size_t INTERVAL = 64;
        if (offset % INTERVAL == 0) {
            std::cout << offset;
        }
        {
            std::string blkSt;
            if (flag == NONE) {
                blkSt = "_";
            } else {
                blkSt = ".";
            }
            std::cout << blkSt; std::cout.flush();
        }
        if (offset % INTERVAL == INTERVAL - 1) {
            std::cout << "\n";
        }
    }
}

void Command::writeBlocksToVmdk(VddkController& vddkCtrl,
                                MultiArchiveManager& mArcMgr,
                                size_t blockSize)
{
    WRITE_LOG1("writeBlocksToVmdk() called.\n");
    assert(cfg_.cmd == CMD_RESTORE);
    
    /* Prepare a zero block. */
    ByteArray zeroBlock(blockSize, 0);

    /* Restore each block. */
    VmdkDumpBlock dumpB(blockSize);

    typedef enum {NONE, ZERO, NONZERO} Wflag;
    Wflag flag = NONE;
    
    /* Write each block to vmdk. */
    for (uint64 offset = 0; ! mArcMgr.isEOF(); offset ++) {

        if (isSignal_) {
            std::string msg("Signal received.\n");
            WRITE_LOG0("%s\n", msg.c_str()); throw msg;
        }
        
        /* Read block from dump file. */
        bool isExist = mArcMgr.readBlock(dumpB);
        
        if (isExist) {
            assert(offset == dumpB.getOffset());

            /* Write the block to vmdk file */
            if (! dumpB.isAllZero() || cfg_.isWriteZeroBlock) {
                const uint8* buf;
                if (dumpB.isAllZero()) {
                    buf = reinterpret_cast<const uint8 *>(&zeroBlock[0]);
                    flag = ZERO;
                } else {
                    buf = dumpB.getBufConst();
                    flag = NONZERO;
                }
                /* zero block is not recraimed by VMware automatically  */
                vddkCtrl.writeBlock(offset, buf);
                
            } else {
                flag = NONE;
            }
        }

        /* Show progress. */
        const size_t INTERVAL = 64;
        if (offset % INTERVAL == 0) {
            std::cout << offset << " "; std::cout.flush();
        }
        {
            std::string blkSt;
            if (flag == NONE) {
                blkSt = "_";
            } else if (flag == ZERO) {
                blkSt = ".";
            } else {
                blkSt = "o";
            }
            std::cout << blkSt; std::cout.flush();
        }
        if (offset % INTERVAL == INTERVAL - 1) {
            std::cout << std::endl;
        }
    }

    WRITE_LOG1("writeBlocksToVmdk() end.\n");
}

/******************************************************************************
 * doCheck command.
 ******************************************************************************/

void Command::doCheck()
{
    WRITE_LOG1("doCheck() called.\n");
    assert(cfg_.cmd == CMD_CHECK);
    size_t nArchives = cfg_.archiveList.size();
    MY_CHECK_AND_THROW(nArchives >= 1,
                       "doCheck(): one or more archives (dump or rdiff) "
                       "files are required.\n");
    assert(nArchives >= 1);

    /* Initialize archive managers. */
    MultiArchiveManager mArcMgr(cfg_.archiveList);
    ArchiveManager arcMgr(cfg_); /* Use digestIn only. */

    /* Read headers */
    VmdkDumpHeader dumpH;
    VmdkDigestHeader digestH;
    mArcMgr.getDumpHeader(dumpH);
    arcMgr.readDigestHeader(digestH);

    WRITE_LOG1("%s\n", dumpH.toString().c_str());
    WRITE_LOG1("%s\n", digestH.toString().c_str());

    /* Check the headers are valid. */
    bool isTheSameVMDKHeader
        = isTheSameVMDK(dumpH, digestH);
    bool isTheSameSnapshotHeader
        = isTheSameSnapshot(dumpH, digestH);
    WRITE_LOG1("isTheSameVMDK: %s\n"
               "isTheSameSnapshot: %s\n",
               (isTheSameVMDKHeader ? "true" : "false"),
               (isTheSameSnapshotHeader ? "true" : "false"));

    /* Read each block */
    VmdkDumpBlock dumpB(dumpH.getBlockSize());
    VmdkDigestBlock digestB, digestCheck;

    /* Check each block contents and digest are the corresponding ones. */
    bool isTheSameAllBlocks = true;
    uint64 offset = 0;
    
    while (! mArcMgr.isEOF() && arcMgr.canReadFromDigest()) {

        bool isExist = mArcMgr.readBlock(dumpB);
        arcMgr.readFromDigest(digestB);

        bool isValid = true;
        if (isExist) {
            assert(offset == dumpB.getOffset());
            
            /* Calc digest of blockIn and compare it with digestIn. */
            digestCheck.set(dumpB);
            if (digestB != digestCheck) {
                isValid = false;
                isTheSameAllBlocks = false;
            }
        }
        
        /* Show progress. */
        if (offset % 64 == 0) {
            std::cout << "\n" << offset;
        } else {
            if (isExist && isValid) { std::cout << "."; }
            else if (isExist)       { std::cout << "X"; }
            else                    { std::cout << "_"; }
        }
        std::cout.flush();

        ++ offset;
    }
    
    WRITE_LOG1("isTheSameAllBlocks: %s\n",
               (isTheSameAllBlocks ? "true" : "false"));
    std::cout << "\nCheck: " << (isTheSameAllBlocks
                                 && isTheSameVMDKHeader
                                 && isTheSameSnapshotHeader
                                 ? "OK" : "WRONG")
              << std::endl;
}

/******************************************************************************
 * doPrint command.
 ******************************************************************************/

void Command::doPrint()
{
    assert(cfg_.cmd == CMD_PRINT);
    
    /* Initialize input archives. */
    ArchiveManager arcMgr(cfg_);

    /* Read headers */
    VmdkDumpHeader dumpH;
    VmdkDigestHeader digestH;
    VmdkDumpBlock dumpB(cfg_.blocksize);
    VmdkDigestBlock digestB;

    /* Print dump data. */
    if (arcMgr.isDumpInOpen()) {
        std::cout << "==========VMDKDUMP HEADER BEGIN==========\n";
        arcMgr.readDumpHeader(dumpH);
        dumpH.print(std::cout);
        std::cout << "==========VMDKDUMP HEADER END==========\n";
        std::cout.flush();
        
        /* Print block metadata of dump. */
        while (arcMgr.canReadFromDump()) {
            arcMgr.readFromDump(dumpB);
            dumpB.print(std::cout);
            std::cout.flush();
        }
    }

    /* Print digest data. */
    if (arcMgr.isDigestInOpen()) {
        std::cout << "==========VMDKDIGEST HEADER BEGIN==========\n";
        arcMgr.readDigestHeader(digestH);
        digestH.print(std::cout);
        std::cout << "==========VMDKDIGEST HEADER END==========\n";
        std::cout.flush();

        /* Print block metadata of digest. */
        while (arcMgr.canReadFromDigest()) {
            arcMgr.readFromDigest(digestB);
            digestB.print(std::cout);
            std::cout.flush();
        }
    }
}

/******************************************************************************
 * doDigest command.
 ******************************************************************************/

void Command::doDigest()
{
    assert(cfg_.cmd == CMD_DIGEST);
    
    ArchiveManager arcMgr(cfg_);

    VmdkDumpHeader prevDumpH;
    VmdkDigestHeader currDigestH;
    
    arcMgr.readDumpHeader(prevDumpH);
    currDigestH.set(prevDumpH);
    arcMgr.writeDigestHeader(currDigestH);

    VmdkDumpBlock prevDumpB(prevDumpH.getBlockSize());
    VmdkDigestBlock currDigestB;

    for (uint64 offset = 0; offset < prevDumpH.getDiskSize(); offset ++) {
        arcMgr.readFromDump(prevDumpB);
        currDigestB.set(prevDumpB);
        arcMgr.writeToDigest(currDigestB);
    }
}

/******************************************************************************
 * doMerge command.
 ******************************************************************************/

void Command::doMerge()
{
    WRITE_LOG1("doMerge() called\n");
    assert(cfg_.cmd == CMD_MERGE);
    size_t nArchives = cfg_.archiveList.size();
    MY_CHECK_AND_THROW(nArchives >= 2,
                       "doMerge(): two or more archive (dump or rdiff) "
                       "files are required.\n");
    assert(nArchives >= 2);

    MultiArchiveManager mArcMgr(cfg_.archiveList);
    VmdkDumpHeader currDumpH;
    mArcMgr.getDumpHeader(currDumpH);

    /* true -> dump output, or rdiff output. */
    const bool isFull = currDumpH.isFull();
    const uint64 blockSize = currDumpH.getBlockSize();
    const uint64 diskSize = currDumpH.getDiskSize();
    
    /* It will be used for dump/rdiff output only. */
    ArchiveManager arcMgr(cfg_);
    
    /* Write output dump/rdiff header data. */
    if (isFull) {
        arcMgr.writeDumpHeader(currDumpH);
    } else {
        arcMgr.writeRdiffHeader(currDumpH);
    }
    
    VmdkDumpBlock currDumpB(blockSize);
    
    for (uint64 oft = 0; oft < diskSize; oft ++) {
        /* Get the latest block of oft. */
        bool isExist = mArcMgr.readBlock(currDumpB);

        /* Write the block to dump/rdiff output. */
        if (isExist) {
            assert (oft == currDumpB.getOffset());
            if (isFull) {
                arcMgr.writeToDump(currDumpB);
            } else {
                arcMgr.writeToRdiff(currDumpB);
            }
        }

        /* show progress */
        if (oft % 64 == 0) { std::cout << "\n" << oft; }
        std::cout << (isExist ? "." : "_");
        std::cout.flush();
    }

    WRITE_LOG1("doMerge() done\n");
}

/******************************************************************************
 * doRdiff commands.
 ******************************************************************************/

void Command::doRdiff()
{
    /* not implemented yet. */
    std::cout << "This function is not implemented yet." << std::endl;
}

/******************************************************************************
 * doLock commands.
 ******************************************************************************/

void Command::doLock()
{
    /* protocol: (using stdin/stdout)

       1. self:   LOCKED
       2. caller: UNLOCK
       3. self:   UNLOCKED
     */

#if 0 /* Currently do not lock anything */
    bool isExclusive = ! cfg_.useSharedLock;
    ScopedResourceLock lk(cfg_.lockResourceName, isExclusive);
#endif
    
    const size_t sz = 1024;
    char buf[sz];

    /* 1. */
    std::cout << "LOCKED" << std::endl;
    
    /* 2. */
    std::cin.getline(buf, sz);
    std::string unlock(buf);
    MY_CHECK_AND_THROW(unlock == "UNLOCK", "Caller did not say UNLOCK.");

    /* 3. */
    std::cout << "UNLOCKED" << std::endl;
}

/* end of file */
