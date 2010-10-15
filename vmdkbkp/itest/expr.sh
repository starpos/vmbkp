#!/bin/sh

. ./config.sh
. ./functions.sh

### Test of dump.
doDumpFull "$DISKPATH_D0" $VM_D $SNAPSHOT_D0 0.dump 0.digest;
doDumpFull "$DISKPATH_D1" $VM_D $SNAPSHOT_D1 1.dump 1.digest;
doDumpDiff "$DISKPATH_D1" $VM_D $SNAPSHOT_D1 0.dump 0.digest 1_diff.dump 1_diff.digest 1-0_diff.rdiff
makeBmp 1-0_diff.rdiff 1.bmp
doDumpIncr "$DISKPATH_D1" $VM_D $SNAPSHOT_D1 0.dump 0.digest 1_incr.dump 1_incr.digest 1.bmp 1-0_incr.rdiff

### Test of merge.
doMerge 1_diff.dump 1-0_diff.rdiff 0_diff.dump
doMerge 1_incr.dump 1-0_incr.rdiff 0_incr.dump

### Test of digest.
doDigest 0_diff.dump 0_diff.digest
doDigest 0_incr.dump 0_incr.digest

### Test of check.
doCheck 0.dump 0.digest
doCheck 1.dump 1.digest
doCheck 0_diff.dump 0_diff.digest
doCheck 1_diff.dump 1_diff.digest
doCheck 0_incr.dump 0_incr.digest
doCheck 1_incr.dump 1_incr.digest

### Test of restore.
# full 0
doRestore "$DISKPATH_R" $VM_R 0.dump;
doDumpFull "$DISKPATH_R" $VM_R "" 0_restore.dump 0_restore.digest;
# full 1
doRestore "$DISKPATH_R" $VM_R 1.dump;
doDumpFull "$DISKPATH_R" $VM_R "" 1_restore.dump 1_restore.digest;
# diff 0
doRestore "$DISKPATH_R" $VM_R 1_diff.dump;
doRestore "$DISKPATH_R" $VM_R 1-0_diff.rdiff;
doDumpFull "$DISKPATH_R" $VM_R "" 0_diff_restore.dump 0_diff_restore.digest;
# diff 1
doRestore "$DISKPATH_R" $VM_R 1_diff.dump;
doDumpFull "$DISKPATH_R" $VM_R "" 1_diff_restore.dump 1_diff_restore.digest;
# incr 0
doRestore "$DISKPATH_R" $VM_R 1_incr.dump;
doRestore "$DISKPATH_R" $VM_R 1-0_incr.rdiff;
doDumpFull "$DISKPATH_R" $VM_R "" 0_incr_restore.dump 0_incr_restore.digest;
# incr 1
doRestore "$DISKPATH_R" $VM_R 1_incr.dump;
doDumpFull "$DISKPATH_R" $VM_R "" 1_incr_restore.dump 1_incr_restore.digest;


