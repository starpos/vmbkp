.PHONY: all build clean rebuild install

PREFIX = /usr/local
THREAD = 1
CC = gcc
CXX = g++
CFLAGS = -fPIC -Wall -Wextra
CFLAGS += `pkg-config --cflags vix-disklib`
LDFLAGS = -Wl,-rpath,\$$ORIGIN  -L. `pkg-config --libs vix-disklib` -lcrypto -lboost_iostreams-mt -lrt
ifeq ($(DEBUG),1)
	CFLAGS += -DDEBUG -g
	GPROF = 1
else
	CFLAGS += -DNDEBUG -O2
	GPROF = 0
endif
ifeq ($(THREAD),1)
	CFLAGS += -DVMDKBACKUP_THREAD
        LDFLAGS += -lboost_thread-mt -lpthread
endif
ifeq ($(GPROF),1)
    CFLAGS+=-pg
endif

.PHONY: all build clean rebuild

EXEC_FILES=vmdkbkp rdiff2bmp check_dump_and_dump check_digest_and_digest print_bmp ipc_lock_manager
LIB_FILES=libvmdkbkp.so

all: build

build: version.hpp $(LIB_FILES) $(EXEC_FILES)
clean:
	rm -f *.o *.gch $(LIB_FILES) $(EXEC_FILES) version.hpp
rebuild: clean build

install: $(EXEC_FILES) $(LIB_FILES)
	if [ ! -d "$(PREFIX)/bin" ]; then mkdir $(PREFIX)/bin; fi
	cp $(EXEC_FILES) $(LIB_FILES) $(PREFIX)/bin/

HEADER_FILES= \
macro.hpp exception.hpp serialize.hpp util.hpp header.hpp bitmap.hpp \
file_lock.hpp \
ipc_mq.hpp ipc_lock.hpp ipc_lock_manager.hpp \
vddk_manager.hpp archive_manager.hpp \
fork_manager.hpp stream_socket.hpp vddk_wrapper.hpp \
queue.hpp data_reader.hpp data_writer.hpp archive_io_manager.hpp \
command.hpp version.hpp
OBJ_FILES= \
serialize.o util.o header.o \
vddk_manager.o stream_socket.o vddk_wrapper.o \
archive_manager.o \
command.o

.cpp.o:
	$(CXX) -c $< -o $@ $(CFLAGS) 

version.hpp: version.hpp.template
	sed "s|XXXXXXXXXX|`cat ../../VERSION`|" version.hpp.template > version.hpp

libvmdkbkp.so: $(OBJ_FILES)
	$(CXX) -shared -o $@ $(CFLAGS) $(OBJ_FILES) $(LDFLAGS)

vmdkbkp: vmdkbkp.o $(LIB_FILES)
	$(CXX) -o $@ $(CFLAGS) $< $(LDFLAGS) -lvmdkbkp

rdiff2bmp: rdiff2bmp.o $(LIB_FILES)
	$(CXX) -o $@ $(CFLAGS) $< $(LDFLAGS) -lvmdkbkp

check_dump_and_dump: check_dump_and_dump.o $(LIB_FILES)
	$(CXX) -o $@ $(CFLAGS) $< $(LDFLAGS) -lvmdkbkp

check_digest_and_digest: check_digest_and_digest.o $(LIB_FILES)
	$(CXX) -o $@ $(CFLAGS) $< $(LDFLAGS) -lvmdkbkp

print_bmp: print_bmp.o $(LIB_FILES)
	$(CXX) -o $@ $(CFLAGS) $< $(LDFLAGS) -lvmdkbkp

ipc_lock_manager: ipc_lock_manager.o ipc_lock_manager.hpp $(LIB_FILES)
	$(CXX) -o $@ $(CFLAGS) $< $(LDFLAGS)
