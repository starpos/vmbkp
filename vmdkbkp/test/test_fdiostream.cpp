/**
 * @file
 * @brief Unit test for fdiostream.hpp.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#include <iostream>
#include <string>
#include "fdiostream.hpp"

int main()
{
    std::string str;
    std::string str2;
    
//     fdistream fdin(5);
//     fdostream fdout(1);

//     fdbuf fdbufin(5, std::ios::in);
//     fdbuf fdbufout(1, std::ios::out);
//     std::istream fdin(&fdbufin);
//     std::ostream fdout(&fdbufout);


    FILE *fp = fdopen(1, "rb");
    if (fp) {
        std::cerr << "fp is true\n";
        if (feof(fp)) {
            std::cerr << "fp is eol\n";
        } else {
            std::cerr << "fp is not eol\n";
        }
    } else {
        std::cerr << "fp is false\n";
    }
    
    fdistream fdin(5);
    fdostream fdout(6);

    std::cerr << "fdin.is_open(): " << fdin.is_open() << "\n";
    std::cerr << "fdout.is_open(): " << fdout.is_open() << "\n";
    
    //std::istream& fdin = fdin2;

//     {
//         int c = fdin.get();
//         fdin.unget();

//         std::cerr << "fdin.get(): " << c << "\n";
//     }

    if (fdin.peek() == EOF) {
        std::cerr << "fdin eof 1\n";
    }

    {
        int i = 0;
        
        while (fdin.peek() != EOF) {
            fdin >> str;
            //str2 = "hoge";
            //fdout << i << ": " << str << ":" << str2 << '\n';
            fdout << str << "\n";
            ++ i;
        } 
    }

    if (fdin.peek() == EOF) {
        std::cerr << "fdin eof 2\n";
    }

    return 0;
}
