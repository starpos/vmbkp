/**
 * @file
 * @brief Check specified two digest files are the same or not.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#include <iostream>
#include <fstream>
#include <string>

#include <boost/iostreams/filtering_stream.hpp>
#include <boost/iostreams/filter/gzip.hpp>
#include <boost/iostreams/device/file.hpp>

#include <assert.h>

#include "header.hpp"
#include "util.hpp"

namespace io = boost::iostreams;

static void initializeInput(io::filtering_istream& is,
                            const std::string& filename)
{
    if (isGzipFileName(filename)) {
        is.push(io::gzip_decompressor());
    }
    is.push(io::file_source(filename));
}

static bool checkDigestAndDigest(const std::string& digest1Fn,
                                 const std::string& digest2Fn)
{
    io::filtering_istream digest1In, digest2In;
    initializeInput(digest1In, digest1Fn);
    initializeInput(digest2In, digest2Fn);
    
    VmdkDigestHeader digest1H, digest2H;
    digest1In >> digest1H;
    digest2In >> digest2H;

    if (digest1H.getDiskSize() != digest2H.getDiskSize() ||
        digest1H.getBlockSize() != digest2H.getBlockSize()) {

        return false;
    }

    VmdkDigestBlock digest1B, digest2B;
    while (digest1In.peek() != EOF && digest2In.peek() != EOF) {
        digest1In >> digest1B;
        digest2In >> digest2B;

        if (digest1B != digest2B) {
            return false;
        }
    }
    return true;
}

int main(int argc, char *argv[])
{
    if (argc != 3) {
        std::cout << "usage check_digest_and_digest [digest1] [digest2]\n";
        ::exit(1);
    }
    
    if (checkDigestAndDigest(argv[1], argv[2])) {
        std::cout << "SAME\n";
        return 0;
    } else {
        std::cout << "DIFFERENT\n";
        return 1;
    }
}
