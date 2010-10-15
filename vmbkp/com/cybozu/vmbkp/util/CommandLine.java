/**
 * @file
 * @brief CommandLine
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import java.util.List;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import com.cybozu.vmbkp.util.Option;

/**
 * @brief Command line parser.
 */
public class CommandLine
{
    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(CommandLine.class.getName());

    /**
     * The map of option string -> option data.
     */
    private TreeMap<String, Option> optionMap_;

    /**
     * Arguments of non-option.
     */
    private List<String> restArgs_;
    
    /**
     * Constructor.
     */
    public CommandLine(String[] args)
        throws Exception
    {
        /* Initialize member variables. */
        initializeRestArgs();
        initializeOptionMap();
        
        int idx = 0;
        Option opt = null;
        
        while (idx < args.length) {
            if ((opt = parseOption(args, idx)) != null) {
                /* Parsed a option. */
                idx = opt.nextIdx;
            } else {
                /* Parse rest argument(s) */
                restArgs_.add(args[idx ++]);
            }
        }
    }

    /**
     * Prepare optionMap_ for later command-line parse.
     */
    private void initializeOptionMap()
    {
        optionMap_ = new TreeMap<String, Option>();
        initializeOptions();
    }

    /**
     * Initialize all options here.
     * Subclass must override this method.
     */
    protected void initializeOptions() {}

    /**
     * You must call this from initializeOptions() only.
     *
     * @param optStr Option string.
     * @param argc The number of arguments for the option.
     */
    protected void registerOption(String optStr, int argc)
    {
        optionMap_.put(optStr, new Option(optStr, argc));
    }

    /**
     * Parse an option.
     *
     * @param args argument array.
     * @param idx parse start index in the argument array.
     * @return option in success, or null.
     */
    private Option parseOption(String[] args, int idx)
        throws Exception
    {
        assert(idx < args.length);

        Option opt = null;
        for (Map.Entry<String,Option> entry: optionMap_.entrySet()) {
            if (args[idx].equals(entry.getKey())) {

                opt = entry.getValue();
                idx ++;

                /* Get arguments of the option */
                for (int i = 0; i < opt.getArgc(); i ++) {
                    if (i < args.length == false) {
                        logger_.warning
                            (String.format
                             ("The option %s requires more arguments.",
                              opt.getOpt()));
                        throw new Exception();
                    }

                    opt.addArg(args[idx ++]);
                }
                
                opt.nextIdx = idx;
                break;
            }
        }

        return opt;
    }

    /**
     * Initialize restArgs_.
     */
    private void initializeRestArgs()
    {
        restArgs_ = new LinkedList<String>();
    }

    /**
     * If predicate is false, warning is put with str message.
     */
    private void checkAndLog(boolean predicate, String str)
    {
        if (predicate == false) {
            logger_.warning(str);
        }
    }

    /**
     * Get the list of rest arguments.
     */
    public List<String> getRestArgs()
    {
        return restArgs_;
    }
    
    /**
     * Check whether the option is set in command line.
     *
     * @return If the optStr option is specified
     */
    public boolean isOption(String optStr)
    {
        Option opt = getOption(optStr);
        return (opt != null && opt.isSpecified());
    }

    /**
     * Get argments of the specified option.
     *
     * @return May return empty list, never null.
     */
    public List<String> getOptionArgs(String optStr)
    {
        Option opt = getOption(optStr);
        if (opt == null || opt.isValid() == false) {
            return new LinkedList<String>();
        } else {
            return opt.getArgs();
        }
    }

    /**
     * For debug.
     */
    public void print()
    {
        System.out.print(toString());
    }

    /**
     * For debug.
     */
    public String toString()
    {
        return toStringAllOptions("\n") + 
            "Rest arguments: " +
            toStringRestArgs(", ") + "\n";
    }

    /**
     * For debug.
     */
    public String toStringAllOptions(String interval)
    {
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String,Option> entry: optionMap_.entrySet()) {
            Option opt = entry.getValue();
            if (opt.isSpecified()) {
                sb.append(opt.toString());
                if (interval != null) { sb.append(interval); }
            }
        }
        return sb.toString();
    }

    /**
     * For debug.
     */
    public String toStringRestArgs(String interval)
    {
        StringBuffer sb = new StringBuffer();
        for (String arg: restArgs_) {
            sb.append(arg);
            if (interval != null) { sb.append(interval); }
        }
        return sb.toString();
    }

    /**
     * Get the option with the option string.
     *
     * @return option object if found, or null.
     */
    protected Option getOption(String optStr)
    {
        return optionMap_.get(optStr);
    }

    /**
     * Validate all options.
     */
    public boolean isValid()
    {
        boolean isAllValid = true;
        for (Map.Entry<String,Option> entry: optionMap_.entrySet()) {
            Option opt = entry.getValue();
            if (opt.isSpecified() && opt.isValid() == false) {
                isAllValid = false;
                logger_.warning(opt.toString());
                break;
            }
        }
        return isAllValid;
    }
}
