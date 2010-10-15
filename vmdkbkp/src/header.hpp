/**
 * @file
 * @brief Classes for dump/rdiff/digest headers.
 *
 * <pre>
 * *.dump or *.rdiff file consists the following data.
 * serialized VmdkDumpHeader
 * repeated(# <= VmdkDumpHeader.diskSize_) serialized VmdkDumpBlock
 *
 * *.digest file consists the following data.
 * serialized VmdkDigestHeader
 * repeated(# == VmdkDigestHeader.diskSize_) serialized VmdkDigestHeader
 *
 * There are operator>> for serialize and operator<< for deserialize
 * associated with all the following class.
 * You should call them for serialization usage only.
 * </pre>
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_HEADER_HPP_
#define VMDKBACKUP_HEADER_HPP_

#include <openssl/crypto.h>
#include <openssl/md5.h>

#include "vixDiskLib.h"

#include "serialize.hpp"
#include "util.hpp"

/**
 *@brief Boolean value with null.
 */
enum Boolean {BFALSE = 0, BTRUE = 1, BNULL = 2};

/**
 * @brief Header data of dump/rdiff file.
 *
 * <pre>
 * Each vmdk is identified with uuid_.
 * Each backup snapshot is identified with uuid_ and timeStamp_.
 * This rule is also applied to VmdkDigestHeader.
 * See ::isTheSameVMDK() and ::isTheSameSnapshot().
 * </pre>
 */
class VmdkDumpHeader
{
private:
    uint64 diskSize_; /* number of blocks */
    uint64 blockSize_; /* bytes */
    int32 adapterType_; /* adapter type (VixDiskLibAdapterType) */
    bool isFull_; /* true when executing full vmdkdump. */
    ByteArray uuid_; /* unique id, this is not uuid in vmdk metadata. */
    TimeStamp timeStamp_; /* time */
    StringMap metaData_; /* metadata of vmdk */
    
public:
    /* Constructor. */
    VmdkDumpHeader();
    /* Initializer. */
    void initialize(uint64 diskSize, uint64 blockSize, int32 adapterType);

    /* Comparators. */
    bool operator==(const VmdkDumpHeader &rhs) const;
    bool operator!=(const VmdkDumpHeader &rhs) const;
    bool isTheSameVMDK(const VmdkDumpHeader& rhs) const;

    /* Accessors. */
    StringMap& getMetadata() {return metaData_;};
    void setFull(bool isFull) {isFull_ = isFull;};
    bool isFull() const {return isFull_;};
    void setUUID();
    void setUUID(const ByteArray& uuid);
    const ByteArray& getUUID() const;
    void setTimeStamp(const time_t time);
    void setTimeStampNow();
    time_t getTimeStamp() const;
    uint64 getDiskSize() const { return diskSize_; }
    uint64 getBlockSize() const { return blockSize_; }
    int32 getAdapterType() const { return adapterType_; }

    /* Copy method. */
    void copyDataFrom(const VmdkDumpHeader& src);

    /* Stream operators. */
    friend std::ostream& operator<<(std::ostream& os, const VmdkDumpHeader& head);
    friend std::istream& operator>>(std::istream& is, VmdkDumpHeader& head);

    /* For test. */
    void print(std::ostream& os = std::cout) const;
    std::string toString() const;
    friend class TestHeader;
};

/**
 * @brief Each block content with some metadata in dump/rdiff file.
 *
 * <pre>
 * How to use with legacy ary:
 * (1) Initialize with blocksize.
 * (2) Call getBuf() and fill block_ with any operation.
 * (3) Call set() of header information.
 *     Now the object is completed. You can call operator>>.
 *
 * How to use with operator<<:
 * (1) Initialize with blocksize.
 * (2) Call operator<< to fill the whole contents.
 *     Now the object is completed. You can call operator>>.
 * </pre>
 */
class VmdkDumpBlock
{
private:
    uint64 offset_;
    Boolean isAllZero_;
    ByteArray block_; /* block_.size() is const blockSize_ */

public:
    const size_t blockSize_;

    /* Constructor. */
    VmdkDumpBlock(size_t blockSize);

    /* Comparators. */
    bool operator==(const VmdkDumpBlock &rhs) const;
    bool operator!=(const VmdkDumpBlock &rhs) const;

    /* Reveal byte array to avoid unneccessary copy. */
    uint8* getBuf()
        {return reinterpret_cast<uint8 *>(&block_[0]);}
    const uint8* getBufConst() const
        {return reinterpret_cast<const uint8 *>(&block_[0]);}

    /* Accessors. */
    void set(uint64 offset, Boolean isAllZero)
        {offset_ = offset; isAllZero_ = isAllZero;}
    void setOffset(uint64 offset) {offset_ = offset;}
    uint64 getOffset() const {return offset_;}
    bool isAllZero() const {
        assert(isAllZero_ != BNULL);
        return isAllZero_ == BTRUE;
    }
    void setAllZero() { isAllZero_ = BTRUE; }
    void setNonZero() { isAllZero_ = BFALSE; }
    void setIsAllZero();

    /* Copy method. */
    void copyDataFrom(const VmdkDumpBlock& src);

    /* Stream operators. */
    friend std::ostream& operator<<(std::ostream& os, const VmdkDumpBlock& blk);
    friend std::istream& operator>>(std::istream& is, VmdkDumpBlock& blk);

    /* For test. */
    void print(std::ostream& os = std::cout) const;
    std::string toString() const;
    friend class TestHeader;
};

/**
 * @brief The header of digest file.
 *
 * See ::isTheSameVMDK() and ::isTheSameSnapshot().
 */
class VmdkDigestHeader
{
private:
    uint64 diskSize_; /* number of blocks */
    uint64 blockSize_; /* bytes */
    ByteArray uuid_; /* unique id. */
    TimeStamp timeStamp_; /* time */
public:
    /* Constructors. */
    VmdkDigestHeader();
    VmdkDigestHeader(uint64 diskSize, uint64 blockSize,
                     ByteArray uuid, TimeStamp timeStamp);
    /* Initializer. */
    void initialize(uint64 diskSize, uint64 blockSize);

    /* Comparators. */
    bool operator==(const VmdkDigestHeader& rhs) const;
    bool operator!=(const VmdkDigestHeader& rhs) const;
    bool isTheSameVMDK(const VmdkDigestHeader& rhs) const;

    /* Accessors. */
    void setUUID();
    void setUUID(const ByteArray& uuid);
    const ByteArray& getUUID() const;
    void setTimeStamp(const time_t time);
    void setTimeStampNow();
    time_t getTimeStamp() const;
    uint64 getDiskSize() const { return diskSize_; }
    uint64 getBlockSize() const { return blockSize_; }

    /* Copy method */
    void copyDataFrom(const VmdkDigestHeader& src);
    void set(const VmdkDumpHeader& src);

    /* Stream operators */
    friend std::ostream& operator<<(std::ostream& os, const VmdkDigestHeader& head);
    friend std::istream& operator>>(std::istream& is, VmdkDigestHeader& head);

    /* For test. */
    void print(std::ostream& os = std::cout) const;
    std::string toString() const;
    friend class TestHeader;

};

/**
 * @brief Digest file consists the following data repeatedly.
 */
class VmdkDigestBlock
{
private:
    Boolean isAllZero_;
    ByteArray digest_;

public:
    /* Constructors. */
    VmdkDigestBlock();
    VmdkDigestBlock(const VmdkDumpBlock& vdBlock);

    /* Comparators. */
    bool operator==(const VmdkDigestBlock &rhs) const;
    bool operator!=(const VmdkDigestBlock &rhs) const;

    /* Reveal byte arrya to avoid unneccessary copy. */
    uint8* getBuf() {return reinterpret_cast<uint8 *>(&digest_[0]);}

    /* Accessors. */
    void setAllZero() {isAllZero_ = BTRUE;}
    void setNonZero() {isAllZero_ = BFALSE;}
    bool isAllZero() const {
        assert(isAllZero_ != BNULL);
        return isAllZero_ == BTRUE;
    }

    /* Make the digest data of the specified VmdkDumpBlock. */
    void set(const VmdkDumpBlock& vdBlock);

    /* Copy method. */
    void copyDataFrom(const VmdkDigestBlock& src);
    
    /* Stream operators. */
    friend std::ostream& operator<<(std::ostream& os, const VmdkDigestBlock& blk);
    friend std::istream& operator>>(std::istream& is, VmdkDigestBlock& blk);

    /* For test. */
    void print(std::ostream& os = std::cout) const;
    std::string toString() const;
    friend class TestHeader;
};

/******************************************************************************
 * Stream operators.
 ******************************************************************************/

std::ostream& operator<<(std::ostream& os, const VmdkDumpHeader& head);
std::istream& operator>>(std::istream& is, VmdkDumpHeader& head);

std::ostream& operator<<(std::ostream& os, const VmdkDumpBlock& blk);
std::istream& operator>>(std::istream& is, VmdkDumpBlock& blk);

std::ostream& operator<<(std::ostream& os, const VmdkDigestHeader& head);
std::istream& operator>>(std::istream& is, VmdkDigestHeader& head);

std::ostream& operator<<(std::ostream& os, const VmdkDigestBlock& blk);
std::istream& operator>>(std::istream& is, VmdkDigestBlock& blk);

/******************************************************************************
 * Functions for interaction among different-type objects.
 ******************************************************************************/

/**
 * Calclator of MD5 digest.
 *
 * @param dumpB Input block data.
 * @param digestB Output digest data.
 */
void calcMD5(const VmdkDumpBlock& dumpB, VmdkDigestBlock& digestB);

/**
 * Check the dump/rdiff file and the digest file are
 * born in a series of backup generations of a vmdk file.
 *
 * @param dumpH Input
 * @param digestH Input
 */
bool isTheSameVMDK(const VmdkDumpHeader& dumpH,
                   const VmdkDigestHeader& digestH);
/**
 * Check the dump/rdiff file and the digest file are
 * born in a backup generation of a vmdk file.
 *
 * @param dumpH Input
 * @param digestH Input
 * @return True if they are created by a dump execution.
 */
bool isTheSameSnapshot(const VmdkDumpHeader& dumpH,
                       const VmdkDigestHeader& digestH);

#endif /* VMDKBACKUP_HEADER_HPP_ */
