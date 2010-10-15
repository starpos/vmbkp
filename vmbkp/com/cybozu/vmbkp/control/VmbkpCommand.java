/**
 * @file
 * @brief VmbkpCommand
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.control;

import java.util.logging.Logger;

/**
 * @brief Vmbkp command.
 */
public enum VmbkpCommand
{
    UPDATE,  /* get and update all available
                virtual machines in vSphere environment. */
    BACKUP,  /* execute backup of the specified vm(s). */
    RESTORE, /* execute restore of the specified vm. */
    STATUS,  /* show status of the archives. */
    CHECK,   /* check the archives are valid. */
    DESTROY, /* destroy the specified aliving vm, removing its vmdk files. */
    CLEAN,   /* delete archives of the specified vm. */
    LIST,    /* print list of vm with a various maching parameters. */
    HELP,    /* show help message. */
    UNKNOWN; /* Anything else. */

    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(VmbkpCommand.class.getName());
    
    /**
     * Parse command string.
     */
    public static VmbkpCommand parse(String commandStr) {

        VmbkpCommand cmd = UNKNOWN;
        try {
            cmd = VmbkpCommand.valueOf(commandStr.toUpperCase());
        } catch (Exception e) {
            logger_.warning(e.toString());
        }

        if (cmd == UNKNOWN) { 
            logger_.warning
                (String.format("VmbkpCommand.parse: not defined %s", commandStr));
        }
        return cmd;
    }
}
