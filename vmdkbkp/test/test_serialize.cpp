/**
 * @file
 * @brief Unit test for serialize.hpp,serialize.cpp.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#include <iostream>
#include <fstream>

#include <stdio.h>
#include <stdlib.h>

#include "serialize.hpp"


#ifdef _WIN32
typedef __int64 int64_t;
#else
#include <stdint.h>
#endif


struct A {
    int a_;
    std::string b_;
    int64_t c_;
    StringMap map_;
    ByteArray bytes_;
    bool is_;
};

static inline std::ostream& operator<<(std::ostream& os, const A& a)
{
    putAsString(os, a.a_);
    putAsString(os, a.b_);
    putAsString(os, a.c_);
    os << a.map_;
    os << a.bytes_;
    putAsString(os, a.is_);
    return os;
}

static inline std::istream& operator>>(std::istream& is, A& a)
{
    getAsString(a.a_, is);
    getAsString(a.b_, is);
    getAsString(a.c_, is);
    is >> a.map_;
    is >> a.bytes_;
    getAsString(a.is_, is);
    return is;
}

int main()
{
    {
        std::ofstream ofs("t.txt", std::ios::binary);
        A a;
        a.a_ = 1343;
        a.b_ = "asthi asdf ";
        a.c_ = 123456789012345LL;
        a.map_["abc"] = "xyz";
        a.map_["xxx"] = "dsf";
        a.map_["yxxx"] = "ssdsf";
        a.bytes_.push_back('1');
        a.bytes_.push_back('2');
        a.bytes_.push_back('\0');
        a.bytes_.push_back('3');
        a.bytes_.push_back('\0');
        a.bytes_.push_back('4');
        a.is_ = false;
        ofs << a;
    }
    {
        std::ifstream ifs("t.txt", std::ios::binary);
        A a;
        ifs >> a;
        printf("a='%d', '%s'\n", a.a_, a.b_.c_str());
        std::cout << "c=" << a.c_ << std::endl;
        put(a.map_);
        for (size_t i = 0; i < a.bytes_.size(); ++i) {
            std::cout << (int)a.bytes_[i] << "*";
        }
        std::cout << std::endl;
        std::cout << "bool:" << a.is_ << "\n";
    }
}
