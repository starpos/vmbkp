/**
 * @file
 * @brief Generate changed block bitmap file using information of rdiff file.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#include <iostream>

#include <boost/iostreams/filtering_stream.hpp>
#include <boost/iostreams/filter/gzip.hpp>
#include <boost/iostreams/device/file.hpp>

#include <assert.h>

#include "header.hpp"
#include "bitmap.hpp"

namespace io = boost::iostreams;

/**
 * Main function.
 *
 * stdin : input rdiff file.
 * stdout : output bitmap.
 */
int main()
{
    VmdkDumpHeader rdiffH;

    io::filtering_istream in;
    in.push(io::gzip_decompressor());
    in.push(std::cin);

    in >> rdiffH;

    Bitmap bmp(rdiffH.getDiskSize());
    VmdkDumpBlock rdiffB(rdiffH.getBlockSize());
    
    while (in.peek() != EOF) {
        in >> rdiffB;
        bmp.set(rdiffB.getOffset());
    }
    
    std::cout << bmp;

    return 0;
}
