/**
 * @file
 * @brief Stream class to deal with file descriptor directly.
 *
 * The classes probably depend on GNU g++.
 *
 * Copyright(C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_FDIOSTREAM_HPP_
#define VMDKBACKUP_FDIOSTREAM_HPP_

#include <ext/stdio_filebuf.h>

/**
 * @brief Stream class template with file descriptor.
 */
template<typename T>
class basic_fdbuf
    : public __gnu_cxx::stdio_filebuf<T>
{
public:
    basic_fdbuf(int fd, std::ios_base::openmode mode)
        : __gnu_cxx::stdio_filebuf<T>(fd, mode) {}
    /* fd() member is available. */
};

/**
 * @brief Output stream with file descriptor.
 */
class fdostream
    :public std::ostream
{
private:
    basic_fdbuf<char> buf_;

public:
    fdostream(int fd)
        : std::ostream(&buf_)
        , buf_(fd, std::ios::out | std::ios::binary) {}
    bool is_open() {return buf_.is_open();}
    int fd() {return buf_.fd();}
};

/**
 * @brief Input stream with file descriptor.
 */
class fdistream
    :public std::istream
{
private:
    basic_fdbuf<char> buf_;

public:
    fdistream(int fd)
        : std::istream(&buf_)
        , buf_(fd, std::ios::in | std::ios::binary) {}
    bool is_open() {return buf_.is_open();}
    int fd() {return buf_.fd();}
};


/******************************************************************************
 * The following is an another implementation
 ******************************************************************************/

// template<typename T>
// class basic_fdostream
//     : public std::basic_ostream<T>
// {
// private:
//     __gnu_cxx::stdio_filebuf<T> buf_;
    
// public:
//     basic_fdostream(int fd)
//         : std::basic_ostream<T>(&buf_)
//         , buf_(fd, std::ios::out | std::ios::binary) {}
//     int fd() {return buf_.fd();}
// };


// template<typename T>
// class basic_fdistream
//     : public std::basic_istream<T>
// {
// private:
//     __gnu_cxx::stdio_filebuf<T> buf_;
    
// public:
//     basic_fdistream(int fd)
//         : std::basic_istream<T>(&buf_)
//         , buf_(fd, std::ios::in | std::ios::binary) {}

//     int fd() {return buf_.fd();}
// };

// typedef basic_fdostream<char> fdostream;
// typedef basic_fdistream<char> fdistream;


#endif /* VMDKBACKUP_FDIOSTREAM_HPP_ */
