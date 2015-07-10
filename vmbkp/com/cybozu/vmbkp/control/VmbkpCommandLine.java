/**
 * @file
 * @brief VmbkpCommandLine
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.control;

import java.util.logging.Logger;
import java.util.List;

import com.cybozu.vmbkp.util.Option;
import com.cybozu.vmbkp.util.CommandLine;

import com.cybozu.vmbkp.profile.ConfigGlobal;
import com.cybozu.vmbkp.profile.ConfigGroup;

import com.cybozu.vmbkp.control.VmbkpCommand;
import com.cybozu.vmbkp.control.VmbkpVersion;

/**
 * @brief Command line parser specialized for vmbkp.
 */
public class VmbkpCommandLine
    extends CommandLine
{
    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(VmbkpCommandLine.class.getName());

    /**
     * Command string.
     */
    private String commandStr_;
    
    /**
     * Command.
     */
    private VmbkpCommand command_;

    /**
     * List of target virtual machine(s) or group(s).
     */
    private List<String> targetList_;

    /**
     * Constructor.
     */
    public VmbkpCommandLine(String[] args)
        throws Exception
    {
        /* Parse options and rest arguments */
        super(args);
        
        /* Initialize member variables. */
        commandStr_ = null;
        command_ = VmbkpCommand.UNKNOWN;

        if (args.length == 0) {
            throw new Exception("There is no arguments.");
        }

        /* --help option makes command HELP */
        if (isOption("--help")) {
            command_ = VmbkpCommand.HELP;
            return;
        }
        
        /* Initialize command and targets. */
        List<String> restArgs = getRestArgs();
        if (restArgs.isEmpty()) {
            System.out.println("Specify command.");
            throw new Exception("There is no command.");
        }
        commandStr_ = restArgs.get(0);
        command_ = VmbkpCommand.parse(commandStr_);
        targetList_ = restArgs.subList(1, restArgs.size());
    }

    /**
     * Initialize all options here.
     */
    @Override protected void initializeOptions()
    {
        /* the same as help command */
        registerOption("--help", 0); 

        /* optional for all commands. */
        registerOption("--conf", 1); 
        registerOption("--grpconf", 1);

        /* required for restore */
        registerOption("--name", 1); 

        /* optional for restore */
        registerOption("--host", 1); 
        registerOption("--datastore", 1);
        registerOption("--folder", 1);

        /* optional for restore/check */
        registerOption("--generation", 1);

        /* optional for backup/restore */
        registerOption("--san", 0);
        registerOption("--nbd", 0);
        registerOption("--mode", 1);
        
        /* optional for backup/restore */
        registerOption("--novmdk", 0); 
        registerOption("--gzip", 0);

        /* optional for backup/restore/check/clean */
        registerOption("--dryrun", 0);

        /* optional for status */
        registerOption("--detail", 0);

        /* optional for clean */
        registerOption("--all", 0);
        registerOption("--force", 0);

        /* optional for list */
        registerOption("--exist", 1);
        registerOption("--mtime", 1);
        registerOption("--mmin", 1);
    }
    
    /**
     * Get command.
     */
    public VmbkpCommand getCommand()
    {
        return command_;
    }

    /**
     * Get command string.
     */
    public String getCommandStr()
    {
        return commandStr_;
    }

    /**
     * Get global config path.
     */
    public String getGlobalConfigPath()
    {
        return getConfigPath("--conf", ConfigGlobal.FILE_NAME);
    }

    /**
     * Get group config path.
     */
    public String getGroupConfigPath()
    {
        return getConfigPath("--grpconf", ConfigGroup.FILE_NAME);
    }

    private String getConfigPath(String optStr, String defaultPath)
    {
        String path = null;
        
        if (isOption(optStr)) {
            Option opt = super.getOption(optStr);
            if (opt.isValid()) {
                path = opt.getArgs().get(0);
            } else {
                logger_.warning("option invalid: " + opt.toString());
            }
        }

        if (path == null) {
            logger_.info(String.format
                         ("Use default config file %s.",
                          defaultPath));
            path = defaultPath;
        }
        return path;
    }             
    
    /**
     * Show help messages.
     */
    public void showHelpMessages()
    {
        System.out.printf
            ("\n" +
             "Online Virtual Machine Backup Tool for VMware vSphere.\n" +
             "Version %s\n" +
             "Copyright (C) 2009,2010 Cybozu Labs, Inc. All rights reserved.\n" +
             "This software comes with ABSOLUTELY NO WARRANTY. This is free software,\n" +
             "and you are welcome to modify and redistribute it under the GPL v2 license.\n" +
             "\n" +
             "Usage: java -jar vmbkp.jar [command] [option(s)] [target(s)]\n" +
             "\n" +
             "Command:\n" +
             "  update:  update availability information of all virtual machines\n" +
             "           in the VMware vSphere environment.\n" +
             "  backup:  backup the specified virtual machine(s).\n" +
             "  restore: restore the specified virtual machine.\n" +
             "  status:  show status of the archives.\n" +
             "  check:   validate the archives.\n" +
             "  destroy: destroy the specified virtual machine removing vmdk files.\n" +
             "  clean:   delete archives of the specified vm.\n" +
             "  list:    list vm moref with various filters.\n" +
             "  help:    show this message.\n" +
             "\n" +
             "Global options:\n" +
             "  --conf <file>:    global configuration file.\n" +
             "  --grpconf <file>: group configuration file.\n" +
             "  --help:           show this message.\n" +
             "\n" +
             "Options for backup command:\n" +
             "  --novmdk:       backup except vmdk contents.\n" +
             "  --dryrun:       do not backup really.\n" +
             "  --gzip:         use gzip to output compression.\n" +
             "  --nbd:          use NBD transfer instead of SAN.\n" +
             "  --mode <mode>:  specify wanted backup level (incr, diff, full).\n" +
             "\n" +
             "Options for restore command:\n" +
             "  --name <name>:      new name of virtual machine (required).\n" +
             "  --generation <id>:  generation id to restore.\n" +
             "  --host <name>:      VMware host to restore.\n" +
             "  --datastore <name>: VMware datastore to restore.\n" +
             "  --folder <name>:    VMware folder to restore. (path is not supported now)\n" +
             "  --novmdk:           restore except vmdk contents.\n" +
             "  --dryrun:           do not restore really.\n" +
             "  --san:              use SAN transfer instead of NBD.\n" +
             "\n" +
             "Options for status command:\n" +
             "  --detail: show detailed status.\n" +
             "\n" +
             "Options for check command:\n" +
             "  --generation <id>: generation id to check.\n" +
             "                     you can specify just one target with this.\n" +
             "  --dryrun:          do not scan archives really.\n" +
             "\n" +
             "Options for clean command:\n" +
             "  --all:    Delete all generations instead of failed generations.\n" +
             "  --force:  Delete all generations if the specified vm is alive.\n" +
             "  --dryrun: do not clean really.\n" +
             "\n" +
             "Options for list command:\n" +
             "  --exist <yes/no/both>: Select by existance of each vm.\n" +
             "  --mtime <(+/-/)N>:     Select where latest generation is created N days ago.\n" +
             "  --mmin <(+/-/)N>:      Select where latest generation is created N minutes ago.\n" +
             "                         +N means greater than N, -N means less than N.\n" +
             "\n" +
             "Target:\n" +
             "  all:     all available virtual machines. \n" +
             "           please run with update command to get \n" +
             "           the latest availability information.\n" +
             "  <group>: name of a group defined in\n" +
             "           the group configuration file.\n" +
             "  <vm>:    name of a virtual machine.\n" +
             "  <moref>: moref of a virtual machine.\n" +
             "\n", VmbkpVersion.version);
    }
    
    /**
     * Validate command and all options.
     */
    @Override public boolean isValid()
    {
        return command_ != VmbkpCommand.UNKNOWN &&
            super.isValid();
    }
    
    /**
     * Get the list of targets.
     */
    public List<String> getTargets()
    {
        return targetList_;
    }

}
