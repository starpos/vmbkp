#!/bin/sh

if [ -z "$1" ]; then
    echo "Specify your backup directory."
    exit
fi

VSPHERE_VCENTER_HOST=${2-"VCENTER_HOST"}
VSPHERE_USERNAME=${3-"USERNAME"}
VSPHERE_PASSWORD=${4-"PASSWORD"}

REPOSITORY=$(cd $(dirname $0);pwd)
WORKDIR=`pwd`
BASEDIR=$(dirname $1)
if [ ! -d $BASEDIR ]; then
    echo $BASEDIR does not exist.
    exit
fi

INSTALLDIR=$(cd $(dirname $1);pwd)/$(basename $1)
ARCDIR=$INSTALLDIR/archives

if [ -d $INSTALLDIR ]; then
    echo $INSTALLDIR already exists.
    exit
fi
mkdir $INSTALLDIR
mkdir $ARCDIR

# Build java code with vi-java.
cd $REPOSITORY/vmbkp
make jar
if [ $? -ne 0 ]; then
    echo make vmbkp.jar failed.
    exit
fi
cp *.jar vmbkp $INSTALLDIR/
cat conf/vmbkp_global.conf \
    |sed "s|ARCHIVE_DIRECTORY|$ARCDIR|" \
    |sed "s|VMDKBKP_PATH|$INSTALLDIR/bin/vmdkbkp|" \
    |sed "s|VCENTER_HOST|$VSPHERE_VCENTER_HOST|" \
    |sed "s|USERNAME|$VSPHERE_USERNAME|" \
    |sed "s|PASSWORD|$VSPHERE_PASSWORD|" \
    > $INSTALLDIR/vmbkp_global.conf
cp conf/vmbkp_group.conf $INSTALLDIR/

# Build c++ code with VDDK.
cd $REPOSITORY/vmdkbkp/src
make
if [ $? -ne 0 ]; then
    echo make vmdkbkp failed.
fi
make install PREFIX=$INSTALLDIR
if [ $? -ne 0 ]; then
    echo make install failed.
fi

cd $WORKDIR
