/**
 * @file
 * @brief Bitmap class with serialization feature.
 * 
 * Copyright (C) 2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_BITMAP_HPP
#define VMDKBACKUP_BITMAP_HPP

#include <stdio.h>
#include <stdint.h>

#include "serialize.hpp"

/**
 * @brief Bitmap class.
 */
class Bitmap
{
private:
    std::vector<uint8_t> bmp_;
    size_t bmpSize_; /* bitmap size */

public:
    /**
     * Constructor
     */
    Bitmap(size_t size)
        : bmp_((size + 7) / 8)
        , bmpSize_(size) {}

    /**
     * Constructor.
     * operator>>() must be called before calling any method.
     */
    Bitmap()
        : bmp_(0)
        , bmpSize_(0) {}

    /**
     * Resize the size of bitmap.
     * The contents does not be changed.
     */
    void resize(size_t size) {
        bmpSize_ = size;
        bmp_.resize((size + 7) / 8);
    }

    /**
     * Size of bitmap.
     * 
     * @return Number of bits.
     */
    size_t size() const {
        return bmpSize_;
    }

    /**
     * Getter.
     *
     * @idx Index of the bit.
     * @return True if the bit is on, or false.
     */
    bool get(size_t idx) const {
        if (idx >= bmpSize_) { return false; }
        return ((bmp_[getIdx(idx)] & getMask(idx)) > 0 ? true : false);
    }

    /**
     * Getter.
     *
     * @idx Index of the bit.
     * @return True if the bit is on, or false.
     */
    bool operator[](size_t idx) const {
        return get(idx);
    }

    /**
     * Setter.
     *
     * @param idx Index of the bit to set.
     * @param flag True if set on, or false.
     */
    void set(size_t idx, bool flag) {
        assert (idx < bmpSize_);

        size_t vidx = getIdx(idx);
        uint8_t tmp = bmp_[vidx];
        uint8_t mask = getMask(idx);

        if (flag && (tmp & mask) == 0) {
            /* bit on */
            bmp_[vidx] |= mask;
        }
        if (!flag && (tmp & mask) != 0) {
            /* bit off */
            bmp_[vidx] &= ~mask;
        }
    }

    /**
     * Setter.
     *
     * @param idx Index of the bit to set on.
     */
    void set(size_t idx) {
        set(idx, true);
    }

    /**
     * Convert the bitmap to a string.
     *
     * @param out Output stream.
     * @return The output stream.
     */
    std::string& toString(std::string& out) const {
        size_t i;
        for (i = 0; i < bmpSize_; i ++) {
            out.append((get(i) ? "1" : "0"));
        }
        return out;
    }

    /**
     * Print the bitmap to a stream as human-readable format.
     */
    void print(std::ostream& os) const {
        os << "size: " << bmpSize_ << "\n";
        for (size_t i = 0; i < bmpSize_; i ++) {
            os << (get(i) ? "1" : "0");
            if (i % 32 == 31) {
                os << "\n";
            } else if (i % 8 == 7) {
                os << " ";
            }
        }
        if (bmpSize_ % 32 != 0) {
            os << "\n";
        }
    }

    /**
     * Comparator.
     */
    bool operator==(Bitmap& rhs) const {
        return bmpSize_ == rhs.bmpSize_ &&
            bmp_ == rhs.bmp_;
    }
    
    /**
     * Comparator.
     */
    bool operator!=(Bitmap& rhs) const {
        return ! (*this == rhs);
    }
    
private:
    /**
     * Convert bitset index to vector index.
     */
    size_t getIdx(size_t idx) const {
        return idx / 8;
    }
    
    /**
     * Create mask.
     *
     * @param idx bitset index. (0 <= idx < bmpSize_).
     * @return mask for bit operation.
     */
    uint8_t getMask(size_t idx) const {
        assert(idx < bmpSize_);
        return static_cast<uint8_t>(1) << (7 - (idx % 8));
    }

    /* Stream operators for serialization. */
    friend std::ostream& operator<<(std::ostream& os, const Bitmap& bmp);
    friend std::istream& operator>>(std::istream& is, Bitmap& bmp);
};

/**
 * Output stream operator for Bitmap
 */
inline std::ostream& operator<<(std::ostream& os, const Bitmap& bmp)
{
    /* write header */
    try {
        putAsString(os, bmp.bmpSize_);
    } catch (ExceptionStack& e) {
        e.add("operator<<()", __FILE__, __LINE__); throw;
    }
    
    for (std::vector<uint8_t>::const_iterator i = bmp.bmp_.begin();
         i != bmp.bmp_.end();
         ++ i) {
        os.put(static_cast<char>(*i));
        if (os.fail()) {
            throw ExceptionStack("operator<<()", __FILE__, __LINE__);
        }
    }
    return os;
};

/**
 * Input stream operator for Bitmap
 */
inline std::istream& operator>>(std::istream& is, Bitmap& bmp)
{
    /* read header */
    size_t bmpSize = 0;
    try {
        getAsString(bmpSize, is);
    } catch (ExceptionStack& e) {
        e.add("operator>>()", __FILE__, __LINE__); throw;
    }

    bmp.bmpSize_ = bmpSize;
    bmp.bmp_.resize((bmp.bmpSize_ + 7) / 8);
    
    std::vector<uint8_t>::iterator i = bmp.bmp_.begin();
    while (is.peek() != EOF && i != bmp.bmp_.end()) {
        *i = static_cast<uint8_t>(is.get());
        ++ i;
        if (is.fail()) {
            throw ExceptionStack("operator>>()", __FILE__, __LINE__);
        }
    }
    return is;
};

#endif /* VMDKBACKUP_BITMAP_HPP */
