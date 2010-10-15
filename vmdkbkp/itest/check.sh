#!/bin/sh

. ./functions.sh

### check dump and digest is the corresponding one.
doCheck 0.dump 0.digest
doCheck 1.dump 1.digest
doCheck 0_diff.dump 0_diff.digest
doCheck 1_diff.dump 1_diff.digest
doCheck 0_incr.dump 0_incr.digest
doCheck 1_incr.dump 1_incr.digest

### check the generated dumps/digests by several methods are the same.
for gen in 0 1;
do 
    for tgt in ${gen}_diff ${gen}_incr ${gen}_restore ${gen}_diff_restore ${gen}_incr_restore;
    do
        echo "check " ${gen} vs $tgt "..."
        checkDumps ${gen}.dump ${tgt}.dump
        checkDigests ${gen}.digest ${tgt}.digest
    done
done

echo "check " 1-0_diff.rdiff vs 1-0_incr.rdiff "..."
checkDumps 1-0_diff.rdiff 1-0_incr.rdiff

