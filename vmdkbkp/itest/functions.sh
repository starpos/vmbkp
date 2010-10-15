#!/bin/sh

. ./config.sh

doDumpFull()
{
    DISKPATH=$1
    VM=$2
    SNAPSHOT=$3
    DUMPOUT=$4
    DIGESTOUT=$5
    if [ "$SNAPSHOT" = "" ]; then
        SNAPSHOT_OPT=""
    else
        SNAPSHOT_OPT="--snapshot $SNAPSHOT"
    fi
    echo "doDumpFull() " $DISKPATH $VM $SNAPSHOT $DUMPOUT $DIGESTOUT;
    ./bin/vmdkbkp dump --mode full \
    $CONNECT_OPT \
    --remote "${DISKPATH}" --vm $VM $SNAPSHOT_OPT \
    --dumpout $DUMPOUT --digestout $DIGESTOUT ;
}

doDumpDiff()
{
    DISKPATH=$1
    VM=$2
    SNAPSHOT=$3
    DUMPIN=$4
    DIGESTIN=$5
    DUMPOUT=$6
    DIGESTOUT=$7
    RDIFFOUT=$8
    if [ "$SNAPSHOT" = "" ]; then
        SNAPSHOT_OPT=
    else
        SNAPSHOT_OPT="--snapshot $SNAPSHOT"
    fi
    echo "doDumpDiff() " $DISKPATH $VM $SNAPSHOT $DUMPIN $DIGESTIN $DUMPOUT $DIGESTOUT $RDIFFOUT;
    ./bin/vmdkbkp dump --mode diff \
    $CONNECT_OPT \
    --remote "${DISKPATH}" --vm $VM $SNAPSHOT_OPT \
    --dumpin $DUMPIN --digestin $DIGESTIN \
    --dumpout $DUMPOUT --digestout $DIGESTOUT \
    --rdiffout $RDIFFOUT ;
}

doDumpIncr()
{
    DISKPATH=$1
    VM=$2
    SNAPSHOT=$3
    DUMPIN=$4
    DIGESTIN=$5
    DUMPOUT=$6
    DIGESTOUT=$7
    BMPIN=$8
    RDIFFOUT=$9
    if [ "$SNAPSHOT" = "" ]; then
        SNAPSHOT_OPT=
    else
        SNAPSHOT_OPT="--snapshot $SNAPSHOT"
    fi
    echo "doDumpIncr() " $DISKPATH $VM $SNAPSHOT $DUMPIN $DIGESTIN $DUMPOUT $DIGESTOUT $BMPIN $RDIFFOUT;
    ./bin/vmdkbkp dump --mode incr \
    $CONNECT_OPT \
    --remote "${DISKPATH}" --vm $VM $SNAPSHOT_OPT \
    --dumpin $DUMPIN --digestin $DIGESTIN \
    --dumpout $DUMPOUT --digestout $DIGESTOUT \
    --bmpin $BMPIN --rdiffout $RDIFFOUT ;
}

doRestore()
{
    DISKPATH=$1
    VM=$2
    DUMPIN=$3
    echo "doRestore() " $DISKPATH $VM $DUMPIN;
    ./bin/vmdkbkp restore \
    $CONNECT_OPT \
    --remote "${DISKPATH}" --vm $VM \
    --dumpin $DUMPIN ;
}

doMerge()
{
    DUMP=$1
    RDIFF=$2
    DUMPOUT=$3
    echo "doMerge() " $DUMP $RDIFF $DUMPOUT
    ./bin/vmdkbkp merge --dumpout $DUMPOUT $DUMP $RDIFF
}

doDigest()
{
    DUMPIN=$1
    DIGESTOUT=$2
    echo "doDigest() " $DUMPIN $DIGETSOUT
    ./bin/vmdkbkp digest --dumpin $DUMPIN --digestout $DIGESTOUT
}

doCheck()
{
    DUMPIN=$1
    DIGESTIN=$2
    echo "doCheck() " $DUMPIN $DIGETSIN
    ./bin/vmdkbkp check --dumpin $DUMPIN --digestin $DIGESTIN
}

makeBmp()
{
    echo "makeRdiff() " $1 $2
    ./bin/rdiff2bmp < $1 > $2
}

checkDumps()
{
    ./bin/check_dump_and_dump $1 $2
}

checkDigests()
{
    ./bin/check_digest_and_digest $1 $2
}

