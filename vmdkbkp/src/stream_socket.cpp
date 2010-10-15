/**
 * @file
 * @brief Implementation of StreamSocket.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */

#include "stream_socket.hpp"

/******************************************************************************
 * StreamSocket members.
 ******************************************************************************/

StreamSocket::StreamSocket(std::istream& is, std::ostream& os)
    : is_(is)
    , os_(os) {}

StreamSocket::StreamSocket(StreamSocket& sock)
    : is_(sock.is_)
    , os_(sock.os_) {}

void StreamSocket::sendMsg(const std::string& msg)
{
    size_t length = msg.length();
    if (length > MAX_CONTROL_MESSAGE_SIZE) {
        throw std::string("length > MAX_CONTROL_MESSAGE_SIZE");
    }
    sendSize(length);
    
    os_.write(msg.c_str(), length);
    os_.flush();
}

std::string StreamSocket::recvMsg()
{
    size_t length = recvSize();
    if (length > MAX_CONTROL_MESSAGE_SIZE) {
        std::stringstream ss;
        ss << "length > MAX_CONTROL_MESSAGE_SIZE ";
        ss << length;
        throw ss.str();
    }
    
    char buf2[length];
    is_.read(buf2, length);
    if (is_.bad()) { throw std::string("recvMsg() error"); }
    return std::string(buf2, length);
}

void StreamSocket::sendBuf(const ByteArray& buf)
{
    size_t size = buf.size();
    sendSize(size);
    
    os_.write(&buf[0], size);
    os_.flush();
}

ByteArrayPtr StreamSocket::recvBuf()
{
    size_t size = recvSize();
    
    ByteArrayPtr ret = ByteArrayPtr(new ByteArray(size));
    is_.read(&(*ret)[0], size);
    if (is_.bad()) { throw std::string("recvBuf() error"); }
    return ret;
}

void StreamSocket::sendSize(size_t size)
{
    char buf[sizeof(size_t)];
    ::memcpy(buf, &size, sizeof(size_t));

    os_.write(buf, sizeof(size_t));
}

size_t StreamSocket::recvSize()
{
    char buf[sizeof(size_t)];
    is_.read(buf, sizeof(size_t));
    if (is_.bad()) { throw std::string("recvSize() error"); }
    size_t length = 0;
    ::memcpy(&length, buf, sizeof(size_t));

    return length;
}

std::istream& StreamSocket::getIs()
{
    return is_;
}

std::ostream& StreamSocket::getOs()
{
    return os_;
}

/* end of file */
