/**
 * @file
 * @brief AdapterType
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

/**
 * @brief Management class for Disk Adapter Type.
 *
 * Currently ide, buslogic, lsilogic,
 * lsilogicsas, unknown are supported.
 */
public enum AdapterType
{
    IDE, BUSLOGIC, LSILOGIC, LSILOGICSAS, UNKNOWN;

    /**
     * Parse string.
     */
    public static AdapterType parse(String typeStr)
    {
        if (typeStr.equals("ide")) {
            return IDE;
        } else if (typeStr.equals("buslogic")) {
            return BUSLOGIC;
        } else if (typeStr.equals("lsilogic")) {
            return LSILOGIC;
        } else if (typeStr.equals("lsilogicsas")) {
            return LSILOGICSAS;
        } else {
            return UNKNOWN;
        }
    }

    /**
     * toString()
     */
    public String toString()
    {
        switch(this) {
        case IDE:
            return "ide";
        case BUSLOGIC:
            return "buslogic";
        case LSILOGIC:
            return "lsilogic";
        case LSILOGICSAS:
            return "lsilogicsas";
        default:
            return "unknown";
        }
    }

    /**
     * scsi or ide or unknown.
     */
    public String toTypeString()
    {
        switch(this) {
        case IDE:
            return "ide";
        case BUSLOGIC:
        case LSILOGIC:
        case LSILOGICSAS:
            return "scsi";
        default:
            return "unknown";
        }
    }
}
