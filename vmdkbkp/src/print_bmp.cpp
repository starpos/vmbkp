/**
 * @file
 * @brief Print bitmap file as human-readable format.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#include <iostream>

#include <assert.h>

#include "bitmap.hpp"

/**
 * Main function.
 *
 * stdin : input bitmap file.
 */
int main()
{
    Bitmap bmp;
    std::cin >> bmp;
    bmp.print(std::cout);
    
    return 0;
}
