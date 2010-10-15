#!/bin/sh

java -ea jp.co.cybozu.labs.profile.TestConfigWrapper;
sleep 1
java -ea jp.co.cybozu.labs.profile.TestConfigWrapper 0 &
java -ea jp.co.cybozu.labs.profile.TestConfigWrapper 1 &
java -ea jp.co.cybozu.labs.profile.TestConfigWrapper 2 &
java -ea jp.co.cybozu.labs.profile.TestConfigWrapper 3 &
java -ea jp.co.cybozu.labs.profile.TestConfigWrapper 4 &
java -ea jp.co.cybozu.labs.profile.TestConfigWrapper 5 &
java -ea jp.co.cybozu.labs.profile.TestConfigWrapper 6 &
java -ea jp.co.cybozu.labs.profile.TestConfigWrapper 7 &
java -ea jp.co.cybozu.labs.profile.TestConfigWrapper 8 &
java -ea jp.co.cybozu.labs.profile.TestConfigWrapper 9 &
wait
