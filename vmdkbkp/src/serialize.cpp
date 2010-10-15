/**
 * @file
 * @brief Implementaion of all defined in serialize.hpp.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#include <iomanip>

#include "serialize.hpp"

void put(const StringMap& map, std::ostream& os)
{
    for (StringMap::const_iterator i = map.begin(); i != map.end(); ++i) {
        os << i->first << " -> " << i->second << "\n";
    }
}

void put(const ByteArray& ary, std::ostream& os)
{
    os << std::hex;
    for (ByteArray::const_iterator i = ary.begin(); i != ary.end(); ++i) {
        os << std::setw(2) << std::setfill('0')
           << static_cast<int>(static_cast<unsigned char>(*i));
    }
    os << std::dec
       << "\n";
}

void getAsString(std::string& out, std::istream& is)
{
	std::getline(is, out, '\0');
	if (is.fail()) {
        throw ExceptionStack("getAsString", __FILE__, __LINE__);
    }
}

/****************************************
 * stream operators for StringMap
 ****************************************/

std::ostream& operator<<(std::ostream& os, const StringMap& map)
{
    try {
        putAsString(os, map.size());
        for (StringMap::const_iterator i = map.begin(); i != map.end(); ++i) {
            putAsString(os, i->first);
            putAsString(os, i->second);
        }
    } catch (ExceptionStack& e) {
        e.add("operator<< for StringMap", __FILE__, __LINE__);
        throw;
    }
	return os;
}

std::istream& operator>>(std::istream& is, StringMap& map)
{
    map.clear();
	size_t size;
    try {
        getAsString(size, is);
        for (size_t i = 0; i < size; i++) {
            std::string key, value;
            getAsString(key, is);
            getAsString(value, is);
            map[key] = value;
        }
    } catch (ExceptionStack& e) {
        e.add("operator>> for StringMap", __FILE__, __LINE__);
        throw;
    }
	return is;
}


/****************************************
 * stream operators for StringMap
 ****************************************/

std::ostream& operator<<(std::ostream& os, const ByteArray& ary)
{
    try {
        putAsString(os, ary.size());
        os.write(&ary[0], ary.size());
        if (os.fail()) {
            throw ExceptionStack("operator<< for ByteArray", __FILE__, __LINE__);
        }
    } catch (ExceptionStack& e) {
        e.add("operator<< for ByteArray", __FILE__, __LINE__);
        throw;
    }
    return os;
}

std::istream& operator>>(std::istream& is, ByteArray& ary)
{
    try {
        size_t size;
        getAsString(size, is);
        ary.resize(size);
        is.read(&ary[0], ary.size());
        if (is.fail()) {
            throw ExceptionStack("operator>> for ByteArray", __FILE__, __LINE__);
        }
    } catch (ExceptionStack& e) {
        e.add("operator>> for ByteArray", __FILE__, __LINE__);
        throw;
    }
	return is;
}

/* end of file */
