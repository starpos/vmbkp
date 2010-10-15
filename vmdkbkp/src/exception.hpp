/**
 * @file
 * @brief Definitions exceptions and related macros.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_EXCEPTION_HPP_
#define VMDKBACKUP_EXCEPTION_HPP_

#include <iostream>
#include <sstream>
#include <string>
#include <vector>

#include <assert.h>

/**
 * @brief Exception for stream operator<< and operator>>.
 *
 * Stacked error messages are supported.
 */
class ExceptionStack
{
private:
    std::vector<std::string> msgs_;
    std::vector<std::string> files_;
    std::vector<int> lines_;

public:
    ExceptionStack(const std::string& errMessage,
                   const std::string& file, int line)
        : msgs_(5)
        , files_(5)
        , lines_(5) {
        add(errMessage, file, line);
    }

    ExceptionStack& add(const std::string& errMessage,
             const std::string& file, int line) {
        msgs_.push_back(errMessage);
        files_.push_back(file);
        lines_.push_back(line);
        return *this;
    }

    std::string sprint() const {
        std::stringstream ss;
        print(ss);
        return ss.str();
    }
    
    void print(std::ostream& os = std::cerr) const {

        assert(msgs_.size() == files_.size());
        assert(msgs_.size() == lines_.size());

        os << "StreamExcpetion: \n";
        
        std::vector<std::string>::const_iterator iMsg; 
        std::vector<std::string>::const_iterator iFile;
        std::vector<int>::const_iterator iLine;
        
        for (iMsg = msgs_.begin(), iFile = files_.begin(), iLine = lines_.begin();
             iMsg != msgs_.end() && iFile != files_.end() && iLine != lines_.end();
             ++ iMsg, ++ iFile, ++ iLine) {
            
            os << "    " << *iMsg
               << " ("<< *iFile
               << ":" << *iLine
               << ")\n";
        }
    }
};

/**
 * @brief General exception of this software.
 */
class MyException
{
private:
    const std::string errMessage_;
    const std::string file_;
    const int line_;
    
public:
    MyException(const std::string& errMessage,
                const std::string& file, int line)
        : errMessage_(errMessage)
        , file_(file)
        , line_(line) {}
    
    std::string sprint(const char* msg = NULL) const {
        std::stringstream ss;
        if (msg != '\0') { ss << msg << "\n"; }
        ss << "MyException: " << errMessage_
           << " [" << file_ << ":" << line_ << "]" << "\n";
        return ss.str();
    }

    void print(std::ostream& os = std::cerr, const char* msg = NULL) const {
        os << sprint(msg);
    }
};

#define MY_THROW_ERROR(errMessage)                                   \
    do {                                                             \
        throw MyException((errMessage), __FILE__, __LINE__);         \
    } while(0)

#define MY_CHECK_AND_THROW(predicate, errMessage)                    \
    do {                                                             \
        if (!(predicate)) {                                          \
            throw MyException((errMessage), __FILE__, __LINE__);     \
        }                                                            \
    } while(0)

#endif /* VMDKBACKUP_EXCEPTION_HPP_ */
