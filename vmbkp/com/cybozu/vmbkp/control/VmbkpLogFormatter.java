/**
 * @file
 * @brief VmbkpLogFormatter
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.control;

import java.util.logging.Formatter;
import java.util.Calendar;
import java.util.logging.LogRecord;

/**
 * @brief Log formatter for the software.
 */
public final class VmbkpLogFormatter
    extends Formatter
{
    public synchronized String format(final LogRecord rec)
    {
        StringBuffer msg = new StringBuffer(1024);

        String header = String.format
            ("%tD %<tT.%<tL [%s, %s, %s, %s] ",
             rec.getMillis(),
             rec.getLevel().toString(), rec.getThreadID(),
             rec.getLoggerName(), rec.getSourceMethodName());
        msg.append(header);
        msg.append(formatMessage(rec));
        msg.append('\n');
        
        Throwable throwable = rec.getThrown();
        if (throwable != null) {
            msg.append(throwable.toString());
            msg.append('\n');
            for (StackTraceElement trace: throwable.getStackTrace()) {
                msg.append('\t');
                msg.append(trace.toString());
                msg.append('\n');
            }
        }
        return msg.toString();
    }
}
