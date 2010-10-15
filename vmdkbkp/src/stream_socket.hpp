/**
 * @file
 * @brief Header file of StreamSocket.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_STREAM_SOCKET_HPP_
#define VMDKBACKUP_STREAM_SOCKET_HPP_

#include <string.h>
#include <boost/shared_ptr.hpp>
#include "serialize.hpp"

/**
 * Default message size for std::string messages.
 */
#define MAX_CONTROL_MESSAGE_SIZE 256

typedef boost::shared_ptr<ByteArray> ByteArrayPtr;

/**
 * @brief Data sender/receiver through iostream like socket.
 *
 * Currently the message size can be represented by 'size_t'.
 * A message consists [size of data (sizeof(size_t))][data(size of data)].
 */
class StreamSocket
{
private:
    std::istream& is_;
    std::ostream& os_;
    
public:
    /**
     * Constructor.
     * This class does not manage any state.
     */
    StreamSocket(std::istream& is, std::ostream& os);
    /**
     * Constructor.
     * This class does not manage any state.
     */
    StreamSocket(StreamSocket& sock);
    /**
     * Send message to its child.
     * Message size must be smaller than MAX_CONTROL_MESSSAGE_SIZE.
     * Use sendBuf for large data.
     */
    void sendMsg(const std::string& msg);
    /**
     * Recieve message from its child.
     */
    std::string recvMsg();
    /**
     * Send buffer to its child.
     */
    void sendBuf(const ByteArray& buf);
    /**
     * Recieve data from its child.
     */
    ByteArrayPtr recvBuf();
    /**
     * Get input stream.
     */
    std::istream& getIs();
    /**
     * Get output stream.
     */
    std::ostream& getOs();

private:
    /**
     * Send size_t data.
     * Currently the message size can be represented by 'size_t'.
     */
    void sendSize(size_t size);
    /**
     * Receive size_t data.
     * Currently the message size can be represented by 'size_t'.
     */
    size_t recvSize();
};

typedef boost::shared_ptr<StreamSocket> StreamSocketPtr;

#endif /* VMDKBACKUP_STREAM_SOCKET_HPP_ */
