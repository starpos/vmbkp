.SUFFIXES: .java .class
.PHONY: build clean rebuild jar doc clean_doc
TARGET=vmbkp.jar
JAVAC=javac
JAR=jar
BUILD_FLAGS=-Xlint:deprecation -Xlint:unchecked
VIJAVA_CLASSPATH=dom4j-1.6.1.jar:vijava55b20130927.jar:.
ifeq ($(DEBUG),1)
	DEBUG_FLAGS=-g:none
else
	DEBUG_FLAGS=
endif

default: build
all: build jar doc
clean_all: clean clean_jar clean_doc
rebuild:
	$(MAKE) clean
	$(MAKE) build
build: version build_config build_util build_soap build_profile build_control build_itest

clean:
	rm -f com/cybozu/vmbkp/**/*.class

com/cybozu/vmbkp/control/VmbkpVersion.java: com/cybozu/vmbkp/control/VmbkpVersion.java.template ../VERSION
	sed "s|XXXXXXXXXX|`cat ../VERSION`|" $< > $@

version: com/cybozu/vmbkp/control/VmbkpVersion.java

.java.class:
	$(JAVAC) $(DEBUG_FLAGS) $(BUILD_FLAGS) -cp $(VIJAVA_CLASSPATH) $<

jar: build clean_jar $(TARGET)

$(TARGET): build
	$(JAR) -cvfm $@ vmbkp.manifest com
	$(JAR) -i $@

clean_jar:
	rm -f $(TARGET)

doc:
	doxygen doxygen.config

gtags_doc:
	gtags -v
	#htags -anosT --tabs 4

clean_doc:
	rm -rf apidoc GPATH GRTAGS GSYMS GTAGS #HTML javadoc

build_config: \
com/cybozu/vmbkp/config/ConfigLikeGit.class \
com/cybozu/vmbkp/config/Entry.class \
com/cybozu/vmbkp/config/EntryMap.class \
com/cybozu/vmbkp/config/FileNotSetException.class \
com/cybozu/vmbkp/config/FormatBool.class \
com/cybozu/vmbkp/config/FormatInt.class \
com/cybozu/vmbkp/config/FormatString.class \
com/cybozu/vmbkp/config/Group.class \
com/cybozu/vmbkp/config/GroupMap.class \
com/cybozu/vmbkp/config/NotNormalFileException.class \
com/cybozu/vmbkp/config/ParseException.class \
com/cybozu/vmbkp/config/Parser.class \
com/cybozu/vmbkp/config/TestConfigLikeGit.class \
com/cybozu/vmbkp/config/TestFormatX.class \
com/cybozu/vmbkp/config/TestParser.class 

build_util: \
com/cybozu/vmbkp/util/LockFileManager.class \
com/cybozu/vmbkp/util/LockFileManagerN.class \
com/cybozu/vmbkp/util/LockFileManagerM.class \
com/cybozu/vmbkp/util/TestLockFileManager.class \
com/cybozu/vmbkp/util/ManagedEntityInfo.class \
com/cybozu/vmbkp/util/VmInfo.class \
com/cybozu/vmbkp/util/SnapInfo.class \
com/cybozu/vmbkp/util/BackupMode.class \
com/cybozu/vmbkp/util/Option.class \
com/cybozu/vmbkp/util/CommandLine.class \
com/cybozu/vmbkp/util/TestBitSet.class \
com/cybozu/vmbkp/util/TestSerialize.class \
com/cybozu/vmbkp/util/Utility.class \
com/cybozu/vmbkp/util/TestLogger.class \
com/cybozu/vmbkp/util/AdapterType.class \
com/cybozu/vmbkp/util/VmdkInfo.class \
com/cybozu/vmbkp/util/VmdkBitmap.class \
com/cybozu/vmbkp/util/TestBitmap.class \
com/cybozu/vmbkp/util/TestBitmapRead.class \
com/cybozu/vmbkp/util/TestBitmapWrite.class \
com/cybozu/vmbkp/util/XmlIndent.class \
com/cybozu/vmbkp/util/VmbkpOvf.class \
com/cybozu/vmbkp/util/TestOvf.class 

build_soap: build_util \
com/cybozu/vmbkp/soap/PrintUtil.class \
com/cybozu/vmbkp/soap/Connection.class \
com/cybozu/vmbkp/soap/GlobalManager.class \
com/cybozu/vmbkp/soap/VirtualMachineManager.class \
com/cybozu/vmbkp/soap/SnapshotManager.class \
com/cybozu/vmbkp/soap/VirtualMachineConfigManager.class \
com/cybozu/vmbkp/soap/VirtualControllerManager.class \
com/cybozu/vmbkp/soap/VirtualDiskManager.class 

build_profile: build_util build_soap \
com/cybozu/vmbkp/profile/ConfigWrapper.class \
com/cybozu/vmbkp/profile/ConfigGlobal.class \
com/cybozu/vmbkp/profile/ConfigGroup.class \
com/cybozu/vmbkp/profile/ProfileAllVm.class \
com/cybozu/vmbkp/profile/ProfileGeneration.class \
com/cybozu/vmbkp/profile/ProfileVm.class \
com/cybozu/vmbkp/profile/TestProfileGeneration.class \
com/cybozu/vmbkp/profile/TestProfileVm.class \
com/cybozu/vmbkp/profile/TestConfigGroup.class \
com/cybozu/vmbkp/profile/TestConfigWrapper.class 

build_control: build_util build_soap build_profile \
com/cybozu/vmbkp/control/VmArchiveManager.class \
com/cybozu/vmbkp/control/VmbkpLog.class \
com/cybozu/vmbkp/control/VmbkpLogFormatter.class \
com/cybozu/vmbkp/control/VmbkpCommand.class \
com/cybozu/vmbkp/control/VmbkpCommandLine.class \
com/cybozu/vmbkp/control/TestVmbkpCommandLine.class \
com/cybozu/vmbkp/control/VmdkBkpFailedException.class \
com/cybozu/vmbkp/control/VmdkBkp.class \
com/cybozu/vmbkp/control/BackupFailedException.class \
com/cybozu/vmbkp/control/BackupInfo.class \
com/cybozu/vmbkp/control/StatusInfo.class \
com/cybozu/vmbkp/control/RestoreInfo.class \
com/cybozu/vmbkp/control/CheckInfo.class \
com/cybozu/vmbkp/control/CleanInfo.class \
com/cybozu/vmbkp/control/ListInfo.class \
com/cybozu/vmbkp/control/VmbkpVersion.class \
com/cybozu/vmbkp/control/VmbkpMain.class 

build_itest: build_control \
com/cybozu/vmbkp/itest/TestVmsoap.class \
com/cybozu/vmbkp/itest/TestAddEmptyDisks.class \
com/cybozu/vmbkp/itest/TestSnapshot.class \
com/cybozu/vmbkp/itest/TestLock.class 


