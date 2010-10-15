/**
 * @file
 * @brief Check specified two dump files are the same or not.
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

namespace io = boost::iostreams;

static bool checkDumpAndDump(const std::string& dump1Fn,
                             const std::string& dump2Fn)
{
    io::filtering_istream dump1In, dump2In;
    dump1In.push(io::gzip_decompressor());
    dump1In.push(io::file_source(dump1Fn));
    dump2In.push(io::gzip_decompressor());
    dump2In.push(io::file_source(dump2Fn));
    
    VmdkDumpHeader dump1H, dump2H;
    dump1In >> dump1H;
    dump2In >> dump2H;

    if (dump1H.getDiskSize() != dump2H.getDiskSize()) { return false; }
    if (dump1H.getBlockSize() != dump2H.getBlockSize()) { return false; }
    if (dump1H.isFull() != dump2H.isFull()) { return false; }

    VmdkDumpBlock dump1B(dump1H.getBlockSize()), dump2B(dump1H.getBlockSize());
    while (dump1In.peek() != EOF && dump2In.peek() != EOF) {
        dump1In >> dump1B;
        dump2In >> dump2B;

        if (dump1B != dump2B) {
            return false;
        }
    }
    return true;
}

int main(int argc, char *argv[])
{
    if (argc != 3) {
        std::cout << "usage check_dump_and_dump [dump1] [dump2]\n";
        ::exit(1);
    }
    
    if (checkDumpAndDump(argv[1], argv[2])) {
        std::cout << "SAME\n";
        return 0;
    } else {
        std::cout << "DIFFERENT\n";
        return 1;
    }
}
