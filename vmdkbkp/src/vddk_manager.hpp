/**
 * @file
 * @brief Management of VDDK environemnt, vmdk file.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_VDDK_MANAGER_HPP_
#define VMDKBACKUP_VDDK_MANAGER_HPP_

#include "header.hpp"

/**
 * @brief vSphere environment manager.
 *
 * This class manages initialize/finalize soap connection.
 *
 */
class VddkManager
{
private:
    /**
     * All config data to connect, to accesse target vmdk file.
     */
    const ConfigData& cfg_;
    /**
     * Connection. This is just a pointer.
     */
    VixDiskLibConnection conn_;
    /**
     * Parameter for VixDiskLib_ConnextEx().
     * This is C struct.
     */
    VixDiskLibConnectParams connParams_;
    /**
     * Error data for VDDK.
     * Just an integer.
     */
    VixError vixError_;
    /**
     * Read only flag.
     */
    bool isReadOnly_;
    /**
     * if true, it will try to use SAN transfer,
     * else NBD transfer.
     */
    bool isSAN_;

public:
    /**
     * Constructor.
     *
     * @param cfg Config data.
     * @param isReadOnly True if you will not use write operations.
     * @param isSAN True if you want to use SAN transfer.
     *        Specify false if you use NBD transfer explicitly.
     * @exception Throws VixException.
     */
    VddkManager(const ConfigData& cfg, bool isReadOnly, bool isSAN);
    /**
     * Destructor.
     */
    ~VddkManager();

    /**
     * Create vmdk file using the specified dump header.
     *
     * @param dumpH Dump header data.
     * @exception Throws VixException, ExceptionStack.
     */
    void createVmdkFile(const VmdkDumpHeader& dumpH);
    /**
     * Return connection.
     */
    VixDiskLibConnection getConnection() const {
        return conn_;
    }
    /**
     * Return config.
     */
    const ConfigData& getConfig() const {
        return cfg_;
    }
    /**
     * Return isReadOnly_ flag.
     */
    bool isReadOnly() const {
        return isReadOnly_;
    }
    /**
     * Return isSAN_ flag.
     */
    bool isSAN() const {
        return isSAN_;
    }
    /**
     * Reset the VDDK connection and library.
     */
    void reset();

private:
    /**
     * Initialize the VDDK library 
     * You must call this only once before opening vmdk files.
     *
     * @exception Throws VixException.
     */
    void initialize();
    /**
     * cleanup and exit from VDDK library.
     * Call disconnect() before.
     * You must call this only once after closing all vmdk files.
     *
     * @exception Throws VixException.
     */
    void finalize();
    /**
     * Connect to the soap server.
     * Call initialize() before.
     *
     * @exception Throws VixException.
     */
    void connect();
    /**
     * Remote connect.
     * @exception Throws VixException.
     */
    void remoteConnect();
    /**
     * Local connect.
     * @exception Throws VixException.
     */
    void localConnect();
    /**
     * Disconnect from the soap server,
     *
     * @exception Throws VixException.
     */
    void disconnect();
    /**
     * Create local vmdk file with VixDiskLib_Create().
     *
     * @param diskPath Local path of the newly created vmdk file.
     * @param adapterType Defined in VixDiskLib.h.
     * @param nBlocks The size of vmdk disk in blocks.
     * @param blockSize Block size in vmdkdump.
     * @exception Throws VixException.
     */
    void createLocalVmdkFile(
        const char *diskPath,
        VixDiskLibAdapterType adapterType,
        int64 nBlocks, int64 blockSize);
    /**
     * VDDK cannot create remote vmdk directly,
     * so create local vmdk first and then clone it.
     *
     * @param localDiskPath See createLocalVmdkFile().
     * @param remoteDiskPath Remote vmdk path to be created.
     * @param adapterType See createLocalVmdkFile().
     * @param nBlocks See createLocalVmdkFile().
     * @param blockSize See createLocalVmdkFile().
     * @exception Throws VixException.
     */
    void createRemoteVmdkFile(
        const char *localDiskPath,
        const char *remoteDiskPath,
        VixDiskLibAdapterType adapterType,
        int64 nBlocks, int64 blockSize);
    /**
     * Clear with 0 connParams_.
     */
    void clearConnParams();
};

/**
 * @brief Vmdk manager class.
 *
 */
class VmdkManager
{
private:
    /**
     * Vmdk handle.
     */
    VixDiskLibHandle handle_;
    /**
     * Vddk manager to use its connection.
     */
    const VddkManager& vddkMgr_;
    /**
     * Number of sectors per block.
     */
    const uint32 nSectorsPerBlock_;
    /**
     * Error data for VDDK.
     * Just an integer.
     */
    VixError vixError_;

public:    
    /** 
     * Constructor
     *
     * @exception Throws VixException.
     */
    VmdkManager(const VddkManager& vddkMgr);
    /**
     * Destructor
     *
     * This will close opened vmdk file.
     */
    ~VmdkManager();

    /**
     * Read vmdk information.
     * A wrapper of VixDiskLib_GetInfo().
     *
     * @param vmdkInfo returned vmdk information.
     * @exception Throws VixException, std::string.
     */
    void readVmdkInfo(VmdkInfo& vmdkInfo);
    /**
     * Read meta data of vmdk file.
     * A wrapper of VixDiskLib_ReadMetadata().
     *
     * @param metadata Read metadata will be stored in this map.
     *        This must be preallocated.
     * @exception Throws VixException.
     */
    void readMetadata(StringMap& metadata);
    /**
     * Write meta data of vmdk file.
     * A wrapper of VixDiskLib_WriteMetadata().
     * 
     * @param metadata All data in this map will be written.
     * @exception Throws VixException.
     */
    void writeMetadata(const StringMap& metadata);
    /**
     * A simple wrapper of VDDK Shrink.
     *
     * @exception Throws VixException.
     */
    void shrinkVmdk();
    /**
     * Read metadata value of the specified key.
     *
     * Called by readMetadata().
     *
     * @return True if success, false if the key is not found.
     * @exception Throws VixException.
     */
    bool readMetadataValue(
        const char* key,
        std::vector<char>& val);
    /**
     * Read a block from the vmdk file.
     *
     * @param blockOffset Block offset in the vmdk.
     * @param buf read contents will be copied to this buffer.
     * @return true in success or false.
     * @exception Throws VixException.
     */
    void readBlock(
        const uint64 blockOffset, uint8* buf);
    /**
     * Write a block to the vmdk file.
     *
     * @param blockOffset Block offset in the vmdk.
     * @param buf contents will be copied from this buffer.
     * @return true in success or false.
     * @exception Throws VixException.
     */
    void writeBlock(
        const uint64 blockOffset, const uint8* buf);
    /**
     * Wrapper of VixDiskLib_GetTransportMode().
     */
    std::string getTransportMode() const;
    /**
     * Open the vmdk.
     */
    void open();
    /**
     * Close the vmdk.
     */
    void close();
    /**
     * Re-open the vmdk.
     */
    void reopen();
};

#endif /* VMDKBACKUP_VDDK_MANAGER_HPP_ */
