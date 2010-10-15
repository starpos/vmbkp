/**
 * @file
 * @brief classes and functions for serialized primitive types.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 * 
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_SERIALIZED_HPP_
#define VMDKBACKUP_SERIALIZED_HPP_

#include <map>

#include "exception.hpp"

/* typedef for often used containers */
typedef std::map<std::string, std::string> StringMap;
typedef std::vector<char> ByteArray;

/* Put operators for debug */
void put(const StringMap& map, std::ostream& os = std::cout);
void put(const ByteArray& ary, std::ostream& os = std::cout);


/**
 * ostream operators for primitive integers and std::string.
 */
template<typename T>
std::ostream& putAsString(std::ostream& os, const T& t)
{
    std::ostream& ret = os << t << '\0';
    if (os.fail()) {
        throw ExceptionStack("putAsString", __FILE__, __LINE__);
    }
	return ret;
}

/**
 * istream operators for primitive integers.
 */
template<typename T>
void getAsString(T& out, std::istream& is)
{
	std::string line;
	std::getline(is, line, '\0');
	std::istringstream(line) >> out;
	if (is.fail()) {
        throw ExceptionStack("getAsString", __FILE__, __LINE__);
    }
}

/**
 * istream operators for std::string.
 */
void getAsString(std::string& out, std::istream& is);


/* stream operators for StringMap */
std::ostream& operator<<(std::ostream& os, const StringMap& map);
std::istream& operator>>(std::istream& is, StringMap& map);

/* stream operators for ByteArray */
std::ostream& operator<<(std::ostream& os, const ByteArray& ary);
std::istream& operator>>(std::istream& is, ByteArray& ary);


#endif /* VMDKBACKUP_SERIALIZED_HPP_ */
