#!/bin/sh

java -ea jp.co.cybozu.labs.util.TestLockFileManager
sleep 1

java -ea jp.co.cybozu.labs.util.TestLockFileManager 0 &
java -ea jp.co.cybozu.labs.util.TestLockFileManager 1 &
java -ea jp.co.cybozu.labs.util.TestLockFileManager 2 &
java -ea jp.co.cybozu.labs.util.TestLockFileManager 3 & 
java -ea jp.co.cybozu.labs.util.TestLockFileManager 4 &
java -ea jp.co.cybozu.labs.util.TestLockFileManager 5 &
java -ea jp.co.cybozu.labs.util.TestLockFileManager 6 &
java -ea jp.co.cybozu.labs.util.TestLockFileManager 7 &
java -ea jp.co.cybozu.labs.util.TestLockFileManager 8 &
java -ea jp.co.cybozu.labs.util.TestLockFileManager 9 &

wait
