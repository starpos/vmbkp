/**
 * @file
 * @brief Unit test for bitmap.hpp.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#include <assert.h>
#include <stdlib.h>
#include <time.h>

#include <iostream>
#include <string>

#include "bitmap.hpp"

int main(int argc, char *argv[])
{
    srand(time(0));

    std::string opt;
    if (argc == 2) {
        opt = argv[1];
    } else {
        return 0;
    }
    
    Bitmap bmp;
    std::string a;
    
    if (opt == "in") {
        
        std::cin >> bmp;
        a = bmp.toString(a);
        std::cerr << a << "\n";

        std::cout << "size: " << bmp.size() << "\n";

    } else if (opt == "out") {

        bmp.resize(1024);
        for (int i = 0; i < 100; i ++) {
            bmp.set(static_cast<int>(1024.0 * rand() / (static_cast<double>(RAND_MAX) + 1.0)));
        }
        
        std::cout << bmp;
        a = bmp.toString(a);
        std::cerr << a << "\n";
    }

    size_t size = bmp.size();
    Bitmap bmp2(size);
    for (size_t i = 0; i < size; i ++) {
        if (bmp.get(i)) {
            bmp2.set(i);
        }
    }
    assert(bmp == bmp2);
    
    return 0;
}
