/**
 * @file
 * @brief Unit test for header.hpp,header.cpp.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#include <iostream>
#include <fstream>

#include <time.h>

#include "header.hpp"

#ifdef DEBUG
#define VERBOSE 1
#else
#define VERBOSE 0
#endif

class TestHeader
{
public:

    void testVmdkDumpHeader() {
        VmdkDumpHeader h1, h2;

        h1.diskSize_ = 10987134;
        h1.blockSize_ = 1048576;
        h1.adapterType_ = 2;
        h1.setUUID();
        h1.metaData_["abc"] = "12as876 asd0a7   ads07asdf 3";
        h1.metaData_["def"] = "45 asdva09sd8asd asef asdf6";
        h1.metaData_["aa0s987asdf"] = "a0f987asvaef asdvasdv a08ew fas";
        h1.setTimeStamp(time(0));
        
        /* serialize and deserialize */
        {
            {
                std::ofstream ofs("t.txt", std::ios::binary);
                ofs << h1;
            }
            {
                std::ifstream ifs("t.txt", std::ios::binary);
                ifs >> h2;
            }
            printf("h1.metadata\n");
            put(h1.getMetadata());
            printf("h2.metadata\n");
            put(h2.getMetadata());
        
            ASSERT_EQUAL(h1, h2);
        }
        {
            h1.metaData_.clear();
            {
                std::ofstream ofs("t.txt", std::ios::binary);
                ofs << h1;
            }
            {
                std::ifstream ifs("t.txt", std::ios::binary);
                ifs >> h2;
            }
            ASSERT_EQUAL(h1, h2);
        }

        printf("%s passed.\n", __FUNCTION__);
        
    }


    void testVmdkDumpBlock() {
        {
            VmdkDumpBlock h1(512), h2(512);
            h1.offset_ = 81741345;
            h1.isAllZero_ = BTRUE;
            {
                std::ofstream ofs("t.txt", std::ios::binary);
                ofs << h1;
            }
            {
                std::ifstream ifs("t.txt", std::ios::binary);
                ifs >> h2;
            }
            ASSERT_EQUAL(h1, h2);
        }
        {
            VmdkDumpBlock h1(512), h2(512);
            h1.offset_ = 981730471;
            h1.isAllZero_ = BFALSE;
            {
                std::ofstream ofs("t.txt", std::ios::binary);
                ofs << h1;
            }
            {
                std::ifstream ifs("t.txt", std::ios::binary);
                ifs >> h2;
            }
            ASSERT_EQUAL(h1, h2);
        }
        {
            VmdkDumpBlock h1(512), h2(512);
            h1.setOffset(89712396714364);
            h1.setAllZero();
            {
                std::ofstream ofs("t.txt", std::ios::binary);
                ofs << h1;
            }
            {
                std::ifstream ifs("t.txt", std::ios::binary);
                ifs >> h2;
            }
            ASSERT_EQUAL(h1, h2);
            ASSERT(h2.isAllZero() == true);
        }
        {
            VmdkDumpBlock h1(512), h2(512);
            h1.setOffset(89712396714364);
            h1.setNonZero();
            {
                std::ofstream ofs("t.txt", std::ios::binary);
                ofs << h1;
            }
            {
                std::ifstream ifs("t.txt", std::ios::binary);
                ifs >> h2;
            }
            ASSERT_EQUAL(h1, h2);
            ASSERT(h2.isAllZero() == false);
        }
        {
            /* Check setIsAllZero() case1. */
            VmdkDumpBlock h1(512);
            h1.setOffset(0);
            ::memset(h1.getBuf(), 0, 512);
            h1.setIsAllZero();
            ASSERT(h1.isAllZero() == true);
        }
        {
            /* Check setIsAllZero() case2. */
            VmdkDumpBlock h1(512);
            h1.setOffset(0);
            ::memset(h1.getBuf(), 0, 512);
            h1.block_[511] = 1;
            h1.setIsAllZero();
            ASSERT(h1.isAllZero() == false);
        }
        
        printf("%s passed.\n", __FUNCTION__);
    }

    void testVmdkDigestHeader() {

        VmdkDigestHeader h1, h2;

        h1.diskSize_ = 897124140897;
        h1.blockSize_ = 1048576;
        h1.setUUID();
        h1.setTimeStampNow();
        h1.print();

        std::stringstream ss;
        ss << h1;
        ss >> h2;

        ASSERT_EQUAL(h1, h2);

        ASSERT(h1.isTheSameVMDK(h2));
        
        h2.print();
        
        
    }

    
    void testVmdkDigestBlock() {
        {
            VmdkDigestBlock h1, h2;
            h1.setAllZero();
            h1.digest_[0] = 123;
            h1.digest_[1] = 56;
            h1.digest_[2] = 9;

            {
                std::ofstream ofs("t.txt", std::ios::binary);
                ofs << h1;
            }
            {
                std::ifstream ifs("t.txt", std::ios::binary);
                ifs >> h2;
            }
            ASSERT(h2.isAllZero() == true);
            ASSERT_EQUAL(h1, h2);
        }
        {
            VmdkDigestBlock h1, h2;
            h1.setNonZero();
            h1.digest_[0] = 23;
            h1.digest_[1] = 98;
            h1.digest_[2] = 111;

            {
                std::ofstream ofs("t.txt", std::ios::binary);
                ofs << h1;
            }
            {
                std::ifstream ifs("t.txt", std::ios::binary);
                ifs >> h2;
            }
            ASSERT(h2.isAllZero() == false);
            ASSERT_EQUAL(h1, h2);
        }
        
        printf("%s passed.\n", __FUNCTION__);
    }
};


int main()
{
    TestHeader th;
    th.testVmdkDumpHeader();
    th.testVmdkDumpBlock();
    th.testVmdkDigestBlock();
    th.testVmdkDigestHeader();
    
    return 0;
}
