/**
 * @file
 * @brief VmdkBitmap
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import java.util.BitSet;
import java.util.logging.Logger;
import java.io.Serializable;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;

/**
 * @brief Block bitmap for changed blocks management.
 *
 * The main purpose of the class is to manage aligned block bitmap
 * with the interface that deals with more high-resolution unit.
 * (Ex. interface unit: 1 byte, bitmap unit: 1 mega bytes)
 */
public class VmdkBitmap
    implements java.io.Serializable
{
    /**
     * Logger.
     */
    private static final Logger logger_
        = Logger.getLogger(VmdkBitmap.class.getName());

    /**
     * Internal bitset.
     */
    private BitSet bs_;

    /**
     * Size of the bitset.
     */
    private int bsSize_;

    /**
     * disk size: bsSize_ * blockSize_
     */
    private long diskSizeInBytes_;

    /**
     * default 1MB = 1048576
     */
    private int blockSize_;

    /**
     * Constructor.
     */
    public VmdkBitmap(long diskSizeInBytes, int blockSize)
    {
        try {
            VmdkBitmapInit(diskSizeInBytes, blockSize);
        } catch (Exception e) {
            logger_.severe("VmdkBitmap Constructor failed.");
            logger_.info(Utility.toString(e));
        }
    }

    /**
     * Constructor.
     */
    public VmdkBitmap(long diskSizeInBytes)
    {
        try {
            /* default blockSize_ is 1MB */
            VmdkBitmapInit(diskSizeInBytes, 1024 * 1024);
        } catch (Exception e) {
            logger_.severe("VmdkBitmap Constructor failed.");
            logger_.info(Utility.toString(e));
        }
    }

    /**
     * Constructor.
     */
    public VmdkBitmap(int blockSize, FilterInputStream in)
    {
        bs_ = null;
        bsSize_ = -1;
        diskSizeInBytes_ = -1;
        blockSize_ = blockSize;

        boolean ret = false;
        try {
            ret = readFrom(in);
            if (!ret) throw new Exception();
        } catch (Exception e) {
            logger_.severe("VmdkBitmap Constructor failed.");
            logger_.info(Utility.toString(e));
        }                
    }

    /**
     * Initializer
     *
     * @param diskSizeInBytes disk size in bytes.
     * @param blockSize block size in bytes.
     */
    private void VmdkBitmapInit(long diskSizeInBytes, int blockSize)
        throws Exception
    {
        diskSizeInBytes_ = diskSizeInBytes;
        blockSize_ = blockSize;

        if (diskSizeInBytes_ % (long) blockSize_ != 0) {
            throw new Exception("diskSizeInBytes % blockSize must be 0.");
        }

        long bsSizeTmp = diskSizeInBytes_ / (long) blockSize_;

        if (bsSizeTmp > (long) Integer.MAX_VALUE) {
            throw new Exception("Too large disk or too small blocksize.");
        }

        bsSize_ = (int) bsSizeTmp;
        bs_ = new BitSet(bsSize_);
    }

    /**
     * Set a given range to true.
     * The range is given in bytes.
     *
     * @param offset offset in bytes.
     * @param length in bytes.
     * @return true with no problem, or false.
     */
    public boolean setRangeInBytes(long offset, long length)
    {
        return setRangeInBytes(offset, length, true);
    }
    
    /**
     * Set a given range to a given flag.
     * The range is given in bytes.
     *
     * @param offset offset in bytes.
     * @param length in bytes.
     * @param flag true to set, false to clear.
     * @return true with no problem, or false.
     */
    public boolean setRangeInBytes(long offset, long length, boolean flag)
    {
        if (length <= 0) {
            return false;
        }
        
        long oft0L = offset / (long) blockSize_;
        long oft1L = (offset + length + (long) blockSize_ - 1) / (long) blockSize_;

        if (oft0L > (long)Integer.MAX_VALUE ||
            oft1L > (long)Integer.MAX_VALUE) {
            /* error */
            return false;
        }

        if (oft0L < oft1L) {
            bs_.set((int) oft0L, (int) oft1L);
        } else {
            bs_.set((int) oft0L);
        }
        return true;
    }

    /**
     * Get state of the block specified by an offset.
     *
     * @param offset offset in block size.
     * @return true if set, or false.
     */
    public boolean get(int offsetInBlock)
    {
        if (offsetInBlock < bsSize_) {
            return bs_.get(offsetInBlock);
        } else {
            return false;
        }
    }

    /**
     * Set offset bit 'flag'.
     *
     * @param offsetInBlock offset in blockSize.
     * @param flag true or false.
     */
    public void set(int offsetInBlock, boolean flag)
    {
        if (offsetInBlock < bsSize_) {
            bs_.set(offsetInBlock);
        }
    }

    /**
     * Set offset bit 'true'.
     *
     * @param offsetInBlock offset in blockSize.
     */
    public void set(int offsetInBlock)
    {
        set(offsetInBlock, true);
    }
    
    /**
     * Return byte string for C/C++ compatibility
     *
     * 1 byte data contains information of 8 blocks.
     * Inside a byte, the highest bit stores i'th block,
     * and the lowest bit stores (i + 7)'th block information.
     *
     * Likewise, when byte[j] stores i to (i + 7) blocks' information,
     * byte[j + 1] stores (i + 8) to (i + 8 + 7) ones.
     *
     * Causion! This will allocate huge memory for whole byte string
     *
     * @return byte string of the whole bitmap.
     */
    public byte[] getAsByteString()
    {
        int size = (bsSize_ + 7) / 8;

        byte[] ret = new byte[size];
        for (int i = 0; i < size; i ++) {
            ret[i] = 0;
            for (int j = 0; j < 8; j ++) {
                if (bs_.get(i * 8 + j)) {
                    ret[i] |= (1 << (7 - j));
                }
            }
        }
        return ret;
    }

    /**
     * Serialize integer value and put to stream.
     *
     * @param out output stream.
     * @param val input integer.
     * @return true in success.
     */
    private void serializeInteger(FilterOutputStream out, Integer val)
        throws Exception
    {
        byte[] a = val.toString().getBytes();
        out.write(a);
        out.write(0);
    }

    /**
     * Deserialize integer value from input stream.
     *
     * @param in input stream.
     * @return output integer.
     */
    private Integer deserializeInteger(FilterInputStream in)
        throws Exception
    {
        StringBuffer sb = new StringBuffer();
        int i = -1;
        while ((i = in.read()) > 0) {
            sb.append((char) i);
        }
        if (i < 0) {
            throw new Exception("It must not be i < 0.");
        }

        return Integer.valueOf(sb.toString());
    }        
    
    /**
     * Write bitmap data to output stream.
     */
    public void writeTo(FilterOutputStream out)
        throws IOException, Exception
    {
        /* Write header */
        serializeInteger(out, bsSize_);

        int size = (bsSize_ + 7) / 8;
        byte tmp;
        for (int i = 0; i < size; i ++) {
            tmp = 0;
            for (int j = 0; j < 8; j ++) {
                if (bs_.get(i * 8 + j)) {
                    tmp |= (1 << (7 - j));
                }
            }
            out.write(tmp);
        }
        out.flush();
    }

    /**
     * Read bitmap data from input stream.
     *
     * The size of the stream and the bitmap must be same.
     */
    public boolean readFrom(FilterInputStream in)
        throws IOException, Exception
    {
        /* Read header */
        Integer bsSizeI = deserializeInteger(in);

        /* Reset size members. */
        bsSize_ = bsSizeI.intValue();
        diskSizeInBytes_ = (long)bsSize_ * (long)blockSize_;

        /* Reset BitSet if needed. */
        if (bs_ == null || bsSize_ != bs_.length()) {
            bs_ = new BitSet(bsSize_);
        }

        logger_.info(String.format
                     ("%d:%d:%d\n",
                      blockSize_, bsSize_, diskSizeInBytes_));

        /* loop */
        int size = (bsSize_ + 7) / 8;
        byte tmp;
        for (int i = 0; i < size; i ++) {
            int r = in.read();
            if (r == -1) {
                return false;
            }
            tmp = (byte) r;
            for (int j = 0; j < 8; j ++) {
                if ((tmp & (1 << (7 - j))) > 0) {
                    bs_.set(i * 8 + j, true);
                } else {
                    bs_.set(i * 8 + j, false);
                }
            }
        }
        return true;
    }

    /**
     * Return bitmap string for human reading.
     *
     * Causion! This will allocate huge memory for whole byte string
     *
     * @return Human-readable bitmap view.
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer(bsSize_);
        for (int i = 0; i < bsSize_; i ++) {
            sb.append(bs_.get(i) ? '1' : '0');
        }
        return sb.toString();
    }

    /**
     * Set all bit to false.
     */
    public void clear()
    {
        bs_.clear();
    }

    /**
     * Check the all bits are zero or not.
     *
     * @return true when all bits are zero, or false.
     */
    public boolean isAllZero()
    {
        return bs_.isEmpty();
    }
    
}
