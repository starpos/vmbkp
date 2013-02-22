/**
 * @file
 * @brief Implmenetation of dump/rdiff/digest header classes.
 *
 * Copyright (C) 2009 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO
 */
#include <sstream>
#include <algorithm>

#include "header.hpp"
#include <openssl/md5.h>

/**********************************************************************
 * Local functions
 **********************************************************************/

/**
 * Generate uuid.
 * 
 * @param uuid Output.
 */
static void setUUIDLocal(ByteArray& uuid) {
    assert(uuid.size() == 16);
    for (int i = 0; i < 16; i ++) {
        uuid[i] = static_cast<char>
            (256.0 * rand() / (RAND_MAX + 1.0));
    }
}

/**
 * Copy uuid from src to dst.
 *
 * @param dst Output.
 * @param src Input.
 */
static void setUUIDLocal(ByteArray& dst, const ByteArray& src) {
    assert(dst.size() == 16 && src.size() == 16);
    dst = src;
}

/*********************************************************************
 * Member functions for VmdkDumpHeader
 *********************************************************************/

VmdkDumpHeader::VmdkDumpHeader()
    : diskSize_(0)
    , blockSize_(0)
    , adapterType_(0)
    , isFull_(true)
    , uuid_(16)
{
    setUUID();
    setTimeStampNow();
}

void VmdkDumpHeader::initialize(uint64 diskSize,
                                uint64 blockSize,
                                int32 adapterType)
{
    diskSize_ = diskSize;
    blockSize_ = blockSize;
    adapterType_ = adapterType;
}

bool VmdkDumpHeader::operator==(const VmdkDumpHeader &rhs) const {
    return diskSize_ == rhs.diskSize_
        && blockSize_ == rhs.blockSize_
        && adapterType_ == rhs.adapterType_
        && isFull_ == rhs.isFull_
        && uuid_ == rhs.uuid_
        && timeStamp_ == rhs.timeStamp_
        && metaData_ == rhs.metaData_;
}

bool VmdkDumpHeader::operator!=(const VmdkDumpHeader &rhs) const {
    return !(*this == rhs);
}

void VmdkDumpHeader::setUUID() {
    setUUIDLocal(uuid_);
}

void VmdkDumpHeader::setUUID(const ByteArray& uuid) {
    setUUIDLocal(uuid_, uuid);
}

const ByteArray& VmdkDumpHeader::getUUID() const {
    assert(uuid_.size() == 16);
    return uuid_;
}

void VmdkDumpHeader::setTimeStamp(const time_t time) {
    timeStamp_.setTimeStamp(time);
}

void VmdkDumpHeader::setTimeStampNow() {
    timeStamp_.setTimeStamp();
}

time_t VmdkDumpHeader::getTimeStamp() const {
    return timeStamp_.getTimeStamp();
}

void VmdkDumpHeader::copyDataFrom(const VmdkDumpHeader& src) {
    diskSize_ = src.diskSize_;
    blockSize_ = src.blockSize_;
    adapterType_ = src.adapterType_;
    isFull_ = src.isFull_;
    uuid_ = src.uuid_;
    timeStamp_ = src.timeStamp_;
    metaData_ = src.metaData_;
}

void VmdkDumpHeader::print(std::ostream& os) const {
    os << "VmdkDumpHeader:\n"
       << "diskSize_: " << diskSize_ << "\n"
       << "blockSize_: " << blockSize_ << "\n"
       << "adapterType_: " << adapterType_ << "\n"
       << "isFull_: " << isFull_ << "\n"
       << "uuid_: ";
    put(uuid_, os);
    os << "timeStamp_: " << timeStamp_.getTimeStamp() << "\n"
       << "metaData_: \n";
    put(metaData_, os);
}

std::string VmdkDumpHeader::toString() const {
    std::stringstream ss;
    print(ss);
    return ss.str();
}

bool VmdkDumpHeader::isTheSameVMDK(const VmdkDumpHeader& rhs) const {
    return diskSize_ == rhs.diskSize_
        && blockSize_ == rhs.blockSize_
        && uuid_ == rhs.uuid_;
    /* timeStamp_ can be different! */
}

/*********************************************************************
 * Functions for VmdkDumpHeader
 *********************************************************************/

std::ostream& operator<<(std::ostream& os, const VmdkDumpHeader& head)
{
    try {
        putAsString(os, head.diskSize_);
        putAsString(os, head.blockSize_);
        putAsString(os, head.adapterType_);
        putAsString(os, head.isFull_);
        os << head.uuid_;
        os << head.timeStamp_;
        os << head.metaData_;
        if (os.fail()) {
            throw ExceptionStack
                ("operator<< for VmdkDumpHeader", __FILE__, __LINE__);
        }
    } catch (ExceptionStack& e) {
        e.add("operator<< for VmdkDumpHeader", __FILE__, __LINE__);
        throw;
    }
    return os;
}

std::istream& operator>>(std::istream& is, VmdkDumpHeader& head)
{
    try {
        getAsString(head.diskSize_, is);
        getAsString(head.blockSize_, is);
        getAsString(head.adapterType_, is);
        getAsString(head.isFull_, is);
        is >> head.uuid_;
        is >> head.timeStamp_;
        is >> head.metaData_;
        if (is.fail()) {
            throw ExceptionStack
                ("operator>> for VmdkDumpHeader", __FILE__, __LINE__);
        }
    } catch (ExceptionStack& e) {
        e.add("operator>> for VmdkDumpHeader", __FILE__, __LINE__);
        throw;
    }
    return is;
}

/*********************************************************************
 * Member functions for VmdkDumpBlock
 *********************************************************************/

VmdkDumpBlock::VmdkDumpBlock(size_t blockSize)
    : offset_(0)
    , isAllZero_(BNULL)
    , block_(blockSize)
    , blockSize_(blockSize)
{
    assert(blockSize_ % VIXDISKLIB_SECTOR_SIZE == 0);
}

bool VmdkDumpBlock::operator==(const VmdkDumpBlock &rhs) const {
    return offset_ == rhs.offset_
        && isAllZero_ == rhs.isAllZero_
        && (isAllZero_ == BTRUE || block_ == rhs.block_);
}

bool VmdkDumpBlock::operator!=(const VmdkDumpBlock &rhs) const {
    return !(*this == rhs);
}

static inline bool isNonZero(uint8 byte)
{
    return byte != 0;
}

void VmdkDumpBlock::setIsAllZero() {
    /* Check the all bytes are zero, or not. */
    if (std::find_if(block_.begin(), block_.end(), isNonZero) == block_.end()) {
        isAllZero_ = BTRUE;
    } else {
        isAllZero_ = BFALSE;
    }
}

void VmdkDumpBlock::copyDataFrom(const VmdkDumpBlock& src)
{
    offset_ = src.offset_;
    isAllZero_ = src.isAllZero_;
    
    assert(blockSize_ == src.blockSize_);
    assert(block_.size() == src.block_.size());
    if (isAllZero_ == BFALSE) {
        copy(src.block_.begin(), src.block_.end(), block_.begin());
    }
}

void VmdkDumpBlock::print(std::ostream& os) const {
    os << "VmdkDumpBlock:\t"
       << "offset_: " << offset_ << "\t"
       << "isAllZero_: " << isAllZero_ << "\t"
       << "block_.size(): " << block_.size() << "\n";

    /* contents of block is not printed. */
}

std::string VmdkDumpBlock::toString() const {
    std::stringstream ss;
    print(ss);
    return ss.str();
}

/*********************************************************************
 * Functions for VmdkDumpBlock
 *********************************************************************/

std::ostream& operator<<(std::ostream& os, const VmdkDumpBlock& blk)
{
    assert(blk.blockSize_ == blk.block_.size());
    assert(blk.isAllZero_ != BNULL);
    
    try {
        putAsString(os, blk.offset_);
        putAsString(os, blk.isAllZero_ == BTRUE);
        if (blk.isAllZero_ == BFALSE) {
            os << blk.block_;
            if (os.fail()) {
                throw ExceptionStack(
                    "operator<< for VmdkDumpBlock", __FILE__, __LINE__);
            }
        }
    } catch (ExceptionStack& e) {
        e.add("operator<< for VmdkDumpBlock", __FILE__, __LINE__);
        throw;
    }
    return os;
}

std::istream& operator>>(std::istream& is, VmdkDumpBlock& blk)
{
    try {
        getAsString(blk.offset_, is);
        bool isAllZero;
        getAsString(isAllZero, is);
        blk.isAllZero_ = (isAllZero ? BTRUE : BFALSE);
        if (blk.isAllZero_ == BFALSE) {
            is >> blk.block_;
            if (is.fail()) {
                throw ExceptionStack(
                    "operator>> for VmdkDumpBlock", __FILE__, __LINE__);
            }
        }
    } catch (ExceptionStack& e) {
        e.add("operator>> for VmdkDumpBlock", __FILE__, __LINE__);
        throw;
    }
    assert(blk.blockSize_ == blk.block_.size());
    return is;
}

/*********************************************************************
 * Member functions for VmdkDigestHeader
 *********************************************************************/

VmdkDigestHeader::VmdkDigestHeader()
    : diskSize_(0)
    , blockSize_(0)
    , uuid_(16)
{
    setUUID();
    setTimeStampNow();
}

VmdkDigestHeader::VmdkDigestHeader(
    uint64 diskSize,
    uint64 blockSize,
    ByteArray uuid,
    TimeStamp timeStamp)
    : diskSize_(diskSize)
    , blockSize_(blockSize)
    , uuid_(uuid)
    , timeStamp_(timeStamp) {}

void VmdkDigestHeader::initialize(uint64 diskSize, uint64 blockSize)
{
    diskSize_ = diskSize;
    blockSize_ = blockSize;
}

bool VmdkDigestHeader::operator==(const VmdkDigestHeader& rhs) const {
    return diskSize_ == rhs.diskSize_
        && blockSize_ == rhs.blockSize_
        && uuid_ == rhs.uuid_
        && timeStamp_ == rhs.timeStamp_;
}

bool VmdkDigestHeader::operator!=(const VmdkDigestHeader& rhs) const {
    return ! (*this == rhs);
}

void VmdkDigestHeader::setUUID() {
    setUUIDLocal(uuid_);
}

void VmdkDigestHeader::setUUID(const ByteArray& uuid) {
    setUUIDLocal(uuid_, uuid);
}

const ByteArray& VmdkDigestHeader::getUUID() const {
    assert(uuid_.size() == 16);
    return uuid_;
}

void VmdkDigestHeader::setTimeStamp(const time_t time) {
    timeStamp_.setTimeStamp(time);
}

void VmdkDigestHeader::setTimeStampNow() {
    timeStamp_.setTimeStamp();
}

time_t VmdkDigestHeader::getTimeStamp() const {
    return timeStamp_.getTimeStamp();
}

void VmdkDigestHeader::print(std::ostream& os) const {
    os << "VmdkDigestHeader:\n"
       << "diskSize_: " << diskSize_ << "\n"
       << "blockSize_: " << blockSize_ << "\n"
       << "uuid_: ";
    put(uuid_, os);
    os << "timeStamp_: " << timeStamp_.getTimeStamp()
       << "\n";
}

std::string VmdkDigestHeader::toString() const {
    std::stringstream ss;
    print(ss);
    return ss.str();
}

bool VmdkDigestHeader::isTheSameVMDK(const VmdkDigestHeader& rhs) const {
    return diskSize_ == rhs.diskSize_
        && blockSize_ == rhs.blockSize_
        && uuid_ == rhs.uuid_;
    /* timeStamp_ can be different! */
}

void VmdkDigestHeader::copyDataFrom(const VmdkDigestHeader& src)
{
    diskSize_ = src.diskSize_;
    blockSize_ = src.blockSize_;
    uuid_ = src.uuid_;
    timeStamp_ = src.timeStamp_;
}

void VmdkDigestHeader::set(const VmdkDumpHeader& src)
{
    diskSize_ = src.getDiskSize();
    blockSize_ = src.getBlockSize();
    uuid_ = src.getUUID();
    setTimeStamp(src.getTimeStamp());
}

/*********************************************************************
 * Functions for VmdkDigestHeader
 *********************************************************************/

std::ostream& operator<<(std::ostream& os, const VmdkDigestHeader& head)
{
    try {
        putAsString(os, head.diskSize_);
        putAsString(os, head.blockSize_);
        os << head.uuid_;
        os << head.timeStamp_;
        if (os.fail()) {
            throw ExceptionStack
                ("opeartor<< for VmdkDigestHeader", __FILE__, __LINE__);
        }
    } catch (ExceptionStack& e) {
        e.add("operator<< for VmdkDigestHeader", __FILE__, __LINE__);
        throw;
    }
    return os;
}

std::istream& operator>>(std::istream& is, VmdkDigestHeader& head)
{
    try {
        getAsString(head.diskSize_, is);
        getAsString(head.blockSize_, is);
        is >> head.uuid_;
        is >> head.timeStamp_;
        if (is.fail()) {
            throw ExceptionStack
                ("opeartor>> for VmdkDigestHeader", __FILE__, __LINE__);
        }
    } catch (ExceptionStack& e) {
        e.add("operator>> for VmdkDigestHeader", __FILE__, __LINE__);
        throw;
    }
    return is;
}

/*********************************************************************
 * Member functions for VmdkDigestBlock
 *********************************************************************/

VmdkDigestBlock::VmdkDigestBlock()
    : isAllZero_(BNULL)
    , digest_(MD5_DIGEST_LENGTH)
{}

VmdkDigestBlock::VmdkDigestBlock(const VmdkDumpBlock& vdBlock)
    : isAllZero_(BNULL)
    , digest_(MD5_DIGEST_LENGTH)
{
    set(vdBlock);
}

bool VmdkDigestBlock::operator==(const VmdkDigestBlock &rhs) const {
    assert(isAllZero_ != BNULL);
    return isAllZero_ == rhs.isAllZero_
        && (isAllZero_ == BTRUE || digest_ == rhs.digest_);
}

bool VmdkDigestBlock::operator!=(const VmdkDigestBlock &rhs) const {
    return !(*this == rhs);
}

void VmdkDigestBlock::set(const VmdkDumpBlock& vdBlock) {
    isAllZero_ = (vdBlock.isAllZero() ? BTRUE : BFALSE);
    if (isAllZero_ == BFALSE) {
        calcMD5(vdBlock, *this);
    }
}

void VmdkDigestBlock::copyDataFrom(const VmdkDigestBlock& src) {
    isAllZero_ = src.isAllZero_;
    if (isAllZero_ == BFALSE) {
        copy(src.digest_.begin(), src.digest_.end(), digest_.begin());
    }
}

void VmdkDigestBlock::print(std::ostream& os) const {
    os << "VmdkDigestBlock:\t"
       << "isAllZero_: " << isAllZero_;
    if (isAllZero_) {
        os << "\n";
    } else {
        os << "\t" << "digest_: ";
        put(digest_, os);
    }
}

std::string VmdkDigestBlock::toString() const {
    std::stringstream ss;
    print(ss);
    return ss.str();
}

/*********************************************************************
 * Functions for VmdkDigestBlock
 *********************************************************************/

std::ostream& operator<<(std::ostream& os, const VmdkDigestBlock& blk)
{
    assert(blk.digest_.size() == MD5_DIGEST_LENGTH);
    assert(blk.isAllZero_ != BNULL);

    try {
        putAsString(os, blk.isAllZero_ == BTRUE);
        if (blk.isAllZero_ == BFALSE) {
            os << blk.digest_;
        }
        if (os.fail()) {
            throw ExceptionStack(
                "operator<< for VmdkDigestBlock", __FILE__, __LINE__);
        }
    } catch (ExceptionStack& e) {
        e.add("operator<< for VmdkDigestBlock", __FILE__, __LINE__);
        throw;
    }
    return os;
}

std::istream& operator>>(std::istream& is, VmdkDigestBlock& blk)
{
    try {
        bool isAllZero;
        getAsString(isAllZero, is);
        blk.isAllZero_ = (isAllZero ? BTRUE : BFALSE);
        if (blk.isAllZero_ == BFALSE) {
            is >> blk.digest_;
        }
        if (is.fail()) {
            throw ExceptionStack(
                "operator>> for VmdkDigestBlock", __FILE__, __LINE__);
        }
    } catch (ExceptionStack& e) {
        e.add("operator>> for VmdkDigestBlock", __FILE__, __LINE__);
        throw;
    }
    assert(blk.digest_.size() == MD5_DIGEST_LENGTH);
    return is;
}

/*********************************************************************
 * Functions for interaction among different-type objects.
 *********************************************************************/

void calcMD5(const VmdkDumpBlock& dumpB, VmdkDigestBlock& digestB) {
    MD5(dumpB.getBufConst(), dumpB.blockSize_, digestB.getBuf());
}

bool isTheSameVMDK(const VmdkDumpHeader& dumpH,
                   const VmdkDigestHeader& digestH)
{
    bool ret = true;

    ret &= (dumpH.getDiskSize() == digestH.getDiskSize());
    ret &= (dumpH.getBlockSize() == digestH.getBlockSize());
    ret &= (dumpH.getUUID() == digestH.getUUID());

    return ret;
}

bool isTheSameSnapshot(const VmdkDumpHeader& dumpH, const VmdkDigestHeader& digestH)
{
    bool ret = true;

    ret &= isTheSameVMDK(dumpH, digestH);
    ret &= (dumpH.getTimeStamp() == digestH.getTimeStamp());

    return ret;
}

/* end of file */
