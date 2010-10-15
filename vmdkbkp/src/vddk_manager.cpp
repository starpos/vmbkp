/**
 * @file
 * @brief Implementation of VddkManager, VmdkManager.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#include "vddk_manager.hpp"
#include "ipc_lock_manager.hpp"

/******************************************************************************
 * Local functions.
 ******************************************************************************/

/**
 * Print VixDiskLibInfo data for debug.
 */
static void printVixDiskLibInfo(const VixDiskLibInfo& info)
{
    char adapterTypeStr[64];
    switch (info.adapterType) {
    case VIXDISKLIB_ADAPTER_IDE:
        strcpy(adapterTypeStr, "IDE");
        break;
    case VIXDISKLIB_ADAPTER_SCSI_BUSLOGIC:
        strcpy(adapterTypeStr, "BusLogic SCSI");
        break;
    case VIXDISKLIB_ADAPTER_SCSI_LSILOGIC:
        strcpy(adapterTypeStr, "LsiLogic SCSI");
        break;
    default:
        strcpy(adapterTypeStr, "unknown");
    }

    ::printf("********** vmdk information **********\n"
             "# of sectors: %llu\n"
             "# of links: %d\n"
             "adapter type: %s\n"
             "BIOS geometry: %u/%u/%u\n"
             "physical geometry: %u/%u/%u\n",
             static_cast<unsigned long long>(info.capacity), info.numLinks,
             adapterTypeStr,
             info.biosGeo.cylinders, info.biosGeo.heads, info.biosGeo.sectors,
             info.physGeo.cylinders, info.physGeo.heads, info.physGeo.sectors);
}

/**
 * Progress callback function for VDDK shrink.
 * Do not call this directly from your code.
 */
static Bool shrinkProgressFunc(void * /* progressData */,
                               int percentCompleted)
{
    WRITE_LOG1("Shrinking: %d%% Done\n", percentCompleted);
    return TRUE;
}

/**
 * Progress callback for VMDK Clone.
 * Do not call this directly from your code.
 */
static Bool cloneProgressFunc(void* /* progressData */,
                              int percentCompleted)
{
    WRITE_LOG1("Cloning: %d%% Done\n", percentCompleted);
    return TRUE;
}


/******************************************************************************
 * VddkManager members.
 ******************************************************************************/

VddkManager::VddkManager(const ConfigData& cfg, bool isReadOnly, bool isSAN)
    : cfg_(cfg)
    , conn_(0)
    , isReadOnly_(isReadOnly)
    , isSAN_(isSAN)
{
    WRITE_LOG1("VddkManager constructor begin\n");
    initialize();
    connect();
    WRITE_LOG1("VddkManager constructor end\n");
}

VddkManager::~VddkManager()
{
    try {
        disconnect();
        finalize();
        
    } catch (const VixException& e) {
        WRITE_LOG0("~VddkManager() failed.\n");
        ::exit(1);
    }
}

void VddkManager::initialize()
{
    WRITE_LOG1("VddkManager::initialize() begin.\n");
    
    const char *libDir = (isSAN_ ? cfg_.libDir : NULL);
    const char *configFile = cfg_.configPath;
    
    WRITE_LOG1("call VixDiskLib_InitEx().\n");
    if (cfg_.isRemote) {
        vixError_ = VixDiskLib_InitEx(VIXDISKLIB_VERSION_MAJOR,
                                      VIXDISKLIB_VERSION_MINOR,
                                      logFunc, logFunc, logFunc,
                                      libDir, configFile);
        CHECK_AND_THROW(vixError_);

    } else {
        vixError_ = VixDiskLib_InitEx(VIXDISKLIB_VERSION_MAJOR,
                                      VIXDISKLIB_VERSION_MINOR,
                                      NULL, NULL, NULL,
                                      NULL, NULL);
        CHECK_AND_THROW(vixError_);
    }

    WRITE_LOG1("VddkManager::initialize() end.\n");
}

void VddkManager::connect()
{
    WRITE_LOG1("VddkManager::connect() begin.\n");
    
    if (cfg_.isRemote) {
        remoteConnect();
    } else {
        localConnect();
    }

    WRITE_LOG1("VddkManager::connect() end.\n");
}

void VddkManager::remoteConnect()
{
    Bool readOnly = (isReadOnly_ ? TRUE : FALSE);
    const char *transports = (isSAN_ ? "san:nbd" : "nbd");
    
    std::string vmxSpec("moref=");
    vmxSpec += cfg_.vmMorefStr;

    clearConnParams();
    connParams_.vmxSpec = const_cast<char *>(vmxSpec.c_str());
    connParams_.serverName = cfg_.server;
    connParams_.credType = VIXDISKLIB_CRED_UID;
    connParams_.creds.uid.userName = cfg_.username;
    connParams_.creds.uid.password = cfg_.password;
    connParams_.port = 902; /* fixed */

    WRITE_LOG1("call VixDiskLib_ConnectEx().\n");
    {
        //bool isExclusive = true;
        //ScopedResourceLock lk(cfg_.lockResourceName, isExclusive);
        vixError_ = VixDiskLib_ConnectEx
            (&connParams_, readOnly, cfg_.snapshotStr, transports, &conn_);
    }
    CHECK_AND_THROW(vixError_);

    WRITE_LOG1("Available transport modes: %s\n",
               VixDiskLib_ListTransportModes());
}

void VddkManager::localConnect()
{
    Bool readOnly = (isReadOnly_ ? TRUE : FALSE);
    WRITE_LOG1("call VixDiskLib_ConnectEx().\n");
    vixError_ = VixDiskLib_ConnectEx(NULL, readOnly, NULL, NULL, &conn_);
    CHECK_AND_THROW(vixError_);
}

void VddkManager::clearConnParams()
{
    ::memset(&connParams_, 0, sizeof(connParams_));
}

void VddkManager::finalize()
{
    if (cfg_.isRemote) {
        WRITE_LOG1("call VixDiskLib_Cleanup().\n");
        uint32 numCleanedUp, numRemaining;
        vixError_ =
            VixDiskLib_Cleanup(&connParams_, &numCleanedUp, &numRemaining);

        /* These errors do not seem to be error
           while they are sometimes reported. */
        if (vixError_ != VIX_E_NOT_SUPPORTED &&
            vixError_ != VIX_E_HOST_NOT_CONNECTED) {
            CHECK_AND_THROW(vixError_);
        }
    }

    WRITE_LOG1("call VixDiskLib_Exit().\n");
    VixDiskLib_Exit();
}

void VddkManager::disconnect()
{
    if (conn_ != NULL) {
        WRITE_LOG1("call VixDiskLib_Disconnect().\n");
        //bool isExclusive = true;
        //ScopedResourceLock lk(cfg_.lockResourceName, isExclusive);
        VixDiskLib_Disconnect(conn_);
    }
    conn_ = NULL;
}

void VddkManager::reset()
{
    WRITE_LOG1("reset VDDK connection and library.\n");
    disconnect();
    finalize();
    initialize();
    connect();
}

void VddkManager::createVmdkFile(const VmdkDumpHeader& dumpH)
{
    const char *localDiskPath, *remoteDiskPath;
    if (cfg_.isRemote) {
        localDiskPath = "tmp.vmdk";
        remoteDiskPath = cfg_.vmdkPath;
    } else {
        localDiskPath = cfg_.vmdkPath;
        remoteDiskPath = NULL;
    }

    MY_CHECK_AND_THROW(dumpH.isFull(),
                       "Error: vmdkdump must be full image when --create option.");
    WRITE_LOG1("dumpH.adapterType_: %d\n", dumpH.getAdapterType());

    if (cfg_.isRemote) {
        createRemoteVmdkFile(
            localDiskPath, remoteDiskPath,
            static_cast<VixDiskLibAdapterType>(dumpH.getAdapterType()),
            dumpH.getDiskSize(),
            dumpH.getBlockSize());
    } else {
        createLocalVmdkFile(
            localDiskPath,
            static_cast<VixDiskLibAdapterType>(dumpH.getAdapterType()),
            dumpH.getDiskSize(),
            dumpH.getBlockSize());
    }
}

void VddkManager::createLocalVmdkFile(
    const char *diskPath,
    VixDiskLibAdapterType adapterType,
    int64 nBlocks, int64 blockSize)
{
    VixDiskLibCreateParams createParams;

    assert(blockSize % VIXDISKLIB_SECTOR_SIZE == 0);
    
    createParams.adapterType = adapterType;
    createParams.capacity = nBlocks * (blockSize / VIXDISKLIB_SECTOR_SIZE);
    createParams.diskType = VIXDISKLIB_DISK_MONOLITHIC_SPARSE;
    createParams.hwVersion = 7; /* for ESX(i)4 */
    
    vixError_ = VixDiskLib_Create(conn_, diskPath, &createParams, NULL, NULL);
    CHECK_AND_THROW(vixError_);
}

void VddkManager::createRemoteVmdkFile(
    const char *localTmpDiskPath,
    const char *remoteDiskPath,
    VixDiskLibAdapterType adapterType,
    int64 nBlocks, int64 blockSize)
{
    assert(blockSize % VIXDISKLIB_SECTOR_SIZE == 0);
    VixDiskLibCreateParams createParams;

    VixDiskLibConnection localConn;
    VixDiskLibConnectParams cnxParamsLocal;
    ::memset(&cnxParamsLocal, 0, sizeof(cnxParamsLocal));
    vixError_ = VixDiskLib_Connect(&cnxParamsLocal, &localConn);
    CHECK_AND_THROW(vixError_);
    
    /* create local vmdk at first */
    ::memset(&createParams, 0, sizeof(createParams));
    createParams.adapterType = adapterType;
    createParams.capacity = nBlocks * (blockSize / VIXDISKLIB_SECTOR_SIZE);
    createParams.diskType = VIXDISKLIB_DISK_MONOLITHIC_SPARSE;
    createParams.hwVersion = 7; /* for ESX(i)4 */
    WRITE_LOG1("localTmpDiskPath: %s\n"
               "remoteDiskPath: %s\n"
               "adapterType: %u\n"
               "nBlocks: %lld\n"
               "blockSize: %lld\n",
               localTmpDiskPath, remoteDiskPath, adapterType,
               static_cast<long long>(nBlocks),
               static_cast<long long>(blockSize));
    
    vixError_ = VixDiskLib_Create(
        localConn, localTmpDiskPath, &createParams, NULL, NULL);
    CHECK_AND_THROW(vixError_);

    /* check required disk space for creation */
    VixDiskLibHandle srcHandle = NULL;
    vixError_ = VixDiskLib_Open(localConn, localTmpDiskPath, 0, &srcHandle);
    CHECK_AND_THROW(vixError_);
    uint64 spaceNeeded;
    VixDiskLib_SpaceNeededForClone(
        srcHandle, VIXDISKLIB_DISK_VMFS_THIN, &spaceNeeded);
    if (srcHandle) {
        vixError_ = VixDiskLib_Close(srcHandle);
        CHECK_AND_THROW(vixError_);
    }
    WRITE_LOG1("Required space for cloning: %llu\n",
               static_cast<unsigned long long>(spaceNeeded));
    
    /* exec cloning */
    ::memset(&createParams, 0, sizeof(createParams));
    createParams.adapterType = adapterType;
    createParams.capacity = nBlocks * (blockSize / VIXDISKLIB_SECTOR_SIZE);
    createParams.diskType = VIXDISKLIB_DISK_VMFS_THIN;
    createParams.hwVersion = 7; /* for ESX(i)4 */

    WRITE_LOG1("Clone begin\n");
    vixError_ = VixDiskLib_Clone(conn_, remoteDiskPath,
                                 localConn, localTmpDiskPath,
                                 &createParams,
                                 cloneProgressFunc,
                                 NULL, TRUE);
    CHECK_AND_THROW(vixError_);
    WRITE_LOG1("Clone end\n");

    /* unlink temporal local vmdk file */
    vixError_ = VixDiskLib_Unlink(localConn, localTmpDiskPath);
    CHECK_AND_THROW(vixError_);

    VixDiskLib_Disconnect(localConn);
}

/******************************************************************************
 * VmdkManager members.
 ******************************************************************************/

VmdkManager::VmdkManager(const VddkManager& vddkMgr)
    : handle_(0)
    , vddkMgr_(vddkMgr)
    , nSectorsPerBlock_(vddkMgr.getConfig().nSectorsPerBlock)
{
    WRITE_LOG1("VmdkManager constructor begin\n");
    /* open(); */
    /* you must call open() explicitly. */
    WRITE_LOG1("VmdkManager constructor end\n");
}

VmdkManager::~VmdkManager()
{
    close();
}

void VmdkManager::open()
{
    WRITE_LOG1("VmdkManager::open() begin\n");
    const VixDiskLibConnection conn = vddkMgr_.getConnection();
    const ConfigData& cfg = vddkMgr_.getConfig();
    assert(conn != NULL);
    
    uint32 openFlags = 0;
    if (vddkMgr_.isReadOnly()) {
        openFlags |= VIXDISKLIB_FLAG_OPEN_READ_ONLY;
    }

    WRITE_LOG1("call VixDiskLib_Open().\n");
    {
        //bool isExclusive = true;
        //ScopedResourceLock lk
        //(vddkMgr_.getConfig().lockResourceName, isExclusive);
        WRITE_LOG0("VmdkManager::open() begin\n");
        vixError_ = VixDiskLib_Open(conn, cfg.vmdkPath, openFlags, &handle_);
        WRITE_LOG0("VmdkManager::open() end\n");
    }
    CHECK_AND_THROW(vixError_);
    WRITE_LOG1("VmdkManager::open() end\n");
}

void VmdkManager::close()
{
    WRITE_LOG1("VmdkManager::close() begin\n");
    if (handle_ != NULL) {
        WRITE_LOG1("call VixDiskLib_Close().\n");
        //bool isExclusive = true;
        //ScopedResourceLock lk
        //(vddkMgr_.getConfig().lockResourceName, isExclusive);
        WRITE_LOG0("VmdkManager::close() begin\n");
        VixDiskLib_Close(handle_);
        WRITE_LOG0("VmdkManager::close() end\n");
    }
    handle_ = NULL;
    WRITE_LOG1("VmdkManager::close() end\n");
}

void VmdkManager::reopen()
{
    WRITE_LOG1("reopen vmdk.\n");
    close();
    open();
}

void VmdkManager::readVmdkInfo(
    VmdkInfo& vmdkInfo)
{
    VixDiskLibInfo *info = NULL;
    vixError_ = VixDiskLib_GetInfo(handle_, &info);
    CHECK_AND_THROW(vixError_);

    MY_CHECK_AND_THROW(
        (info->capacity % nSectorsPerBlock_ == 0),
        "Error: capacity of the vmdk disk is not "
        "the integral multiple of blocksize.\n");
    vmdkInfo.adapterType = info->adapterType;
    vmdkInfo.nBlocks = info->capacity / nSectorsPerBlock_;
    vmdkInfo.numLinks = info->numLinks;

    /* print information for debug */
    if (VERBOSE) { printVixDiskLibInfo(*info); }

    ::printf("Supported transport modes: %s\n",
             VixDiskLib_ListTransportModes());
    VixDiskLib_FreeInfo(info);
}

bool VmdkManager::readMetadataValue(
    const char* key, std::vector<char>& val)
{
    assert (key != NULL && key[0] != '\0');

    /* Get val size */
    size_t requiredLen;
    try {
        vixError_ =
            VixDiskLib_ReadMetadata(
                handle_, key, NULL, 0, &requiredLen);

        if (vixError_ != VIX_OK &&
            vixError_ != VIX_E_BUFFER_TOOSMALL) {
            THROW_ERROR(vixError_);
        }
    } catch (const VixException& e) {
        e.print();
        if (e.errorCode() == VIX_E_DISK_KEY_NOTFOUND) {
            return false;
        } else {
            throw;
        }
    }

    val.resize(requiredLen);
    vixError_ =
        VixDiskLib_ReadMetadata(
            handle_, key, &val[0], requiredLen, NULL);
    CHECK_AND_THROW(vixError_);

    return true;
}    

void VmdkManager::readMetadata(StringMap& metadata)
{
    /* Get the number of metadata. */
    size_t requiredLen;
    vixError_ =
        VixDiskLib_GetMetadataKeys(handle_, NULL, 0, &requiredLen);
    if (vixError_ != VIX_OK && vixError_ != VIX_E_BUFFER_TOOSMALL) {
        THROW_ERROR(vixError_);
    }
    std::vector<char> metadatabuf;
    metadatabuf.resize(requiredLen);

    /* Get metadata keys */
    vixError_ =
        VixDiskLib_GetMetadataKeys(
            handle_, &metadatabuf[0], requiredLen, NULL);
    CHECK_AND_THROW(vixError_);

    /* Get metadata value for each key. */
    char *key;
    std::vector<char> val;
    key = &metadatabuf[0];
    while (key[0] != '\0') {
        if (VERBOSE) { /* debug */
            for (int i = 0; i < 20; i ++) { ::printf("%02x ", key[i]); }
        }

        /* Read metadata value */
        if (! readMetadataValue(key, val)) { break; }

        if (VERBOSE) { ::printf("%s = %s\n", key, &val[0]); } /* debug */
        
        std::string skey(key);
        std::string sval(&val[0]);
        
        if (VERBOSE) { std::cout << skey << "-->" << sval << "\n"; } /* debug */

        metadata[skey] = sval;
        key += (1 + strlen(key));
    } /* while */
    
}

void VmdkManager::writeMetadata(const StringMap& metadata)
{
    if (VERBOSE) { /* debug */
        std::cout << "-----metadata-----\n";
        std::cout << metadata;
        std::cout << "------------------\n";
    }
    
    for (StringMap::const_iterator i = metadata.begin();
         i != metadata.end(); ++ i) 
    {         
        std::string key = i->first;
        std::string value = i->second;

        if (VERBOSE) {
            ::printf("write metadata: key: %s value: %s.\n",
                     key.c_str(), value.c_str()); /* debug */
        }
        
        vixError_ = VixDiskLib_WriteMetadata(
            handle_, key.c_str(), value.c_str());
        CHECK_AND_THROW(vixError_);
    }
}

void VmdkManager::shrinkVmdk()
{
    vixError_ = VixDiskLib_Shrink(handle_, shrinkProgressFunc, NULL);
    CHECK_AND_THROW(vixError_);
}

void VmdkManager::readBlock(
    const uint64 blockOffset, uint8* buf)
{
    const VixDiskLibSectorType curSector = blockOffset * nSectorsPerBlock_;

    {
        //bool isExclusive = false;
        //ScopedResourceLock lk
        //(vddkMgr_.getConfig().lockResourceName, isExclusive);
        vixError_ =
        VixDiskLib_Read(handle_, curSector, nSectorsPerBlock_, buf);
    }
    CHECK_AND_THROW(vixError_);
}

void VmdkManager::writeBlock(
    const uint64 blockOffset, const uint8* buf)
{
    const VixDiskLibSectorType curSector = blockOffset * nSectorsPerBlock_;

    {
        //bool isExclusive = false;
        //ScopedResourceLock lk
        //(vddkMgr_.getConfig().lockResourceName, isExclusive);
        vixError_ =
            VixDiskLib_Write(handle_, curSector, nSectorsPerBlock_, buf);
    }
    CHECK_AND_THROW(vixError_);
}

std::string VmdkManager::getTransportMode() const
{
    std::string ret(VixDiskLib_GetTransportMode(handle_));
    return ret;
}

/* end of file */
