/**
 * @file
 * @brief Option
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import java.util.List;
import java.util.LinkedList;
import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * @brief Manage each option in the command line.
 *
 * Currently the number of arguments for each option is fixed.
 * Variable number of arguments should be supported
 * for more general option manager.
 */
public class Option
{
    /**
     * Option string. like "--conf".
     */
    private String opt_;

    /**
     * Number of arguments of the option.
     */
    private int argc_;

    /**
     * List of arguments.
     *
     * Satisfy args_.size() == argc_
     * when the option is completely parsed.
     */
    private List<String> args_;

    /**
     * Next index of the global arguments.
     * If nextIdx < 0, this is not specified in the command line option.
     * Or this means the next start index in the argument array.
     */
    public int nextIdx;
    
    /**
     * Constructor.
     *
     * @param opt Option string.
     * @param argc # of arguments of the option.
     */
    public Option(String opt, int argc)
    {
        opt_ = opt;
        argc_ = argc;
        args_ = new LinkedList<String>();
        nextIdx = -1;
    }

    /**
     * Get option string.
     */
    public String getOpt()
    {
        return opt_;
    }

    /**
     * Get number of arguments for the option.
     */
    public int getArgc()
    {
        return argc_;
    }

    /**
     * Get the list of arguments.
     */
    public List<String> getArgs()
    {
        return args_;
    }

    /**
     * Add the specified argument.
     */
    protected void addArg(String arg)
    {
        args_.add(arg);
    }

    /**
     * Convert to string as human-readable format.
     */
    public String toString()
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        
        pw.printf("Option: %s, %d, %d, args: ",
                  opt_, argc_, nextIdx);
        for (String arg: args_) {
            pw.printf("%s, ", arg);
        }
        pw.flush();
        return sw.toString();
    }

    /**
     * True when the option is specified in the command-line.
     */
    public boolean isSpecified()
    {
        return nextIdx >= 0;
    }
    
    /**
     * Whether the option is valid or not.
     */
    public boolean isValid()
    {
        return (isSpecified() &&
                opt_ != null &&
                argc_ >= 0 &&
                args_ != null &&
                args_.size() == argc_);
    }
}
