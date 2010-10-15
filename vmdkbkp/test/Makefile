CC=gcc
CXX=g++
CFLAGS=-Wall -g
CFLAGS+=`pkg-config --cflags vix-disklib` -I../src
ifeq ($(DEBUG),0)
	CFLAGS+=-DNDEBUG -O2
	GPROF=0
else
	CFLAGS+=-DDEBUG
	GPROF=1
endif
ifeq ($(GPROF),1)
    CFLAGS+=-pg
endif
LDFLAGS=-Wl,-rpath,\$$ORIGIN `pkg-config --libs vix-disklib` -lcrypto -lboost_iostreams-mt -lboost_thread-mt -lrt
BINARIES=test_header test_serialize test_fdiostream test_bitmap \
test_archive_manager test_gzip test_queue test_fork test_fork2 test_fork3 \
test_stream_socket test_vddk_wrapper test_data_reader test_data_writer \
test_archive_io_manager test_thread test_file_lock test_file_lock2 \
test_mq test_ipc_mq test_ipc_lock test_ipc_lock_manager

.PHONY: all build test clean rebuild tmpdir

all: build

build: $(BINARIES)

test: $(BINARIES) tmpdir
	./test_header
	./test_serialize
	echo "hogehogehoge" > hoge.txt; ./test_fdiostream 5< hoge.txt 6> hoge2.txt
	./test_bitmap out 2> bitmap1 | ./test_bitmap in 2> bitmap2; diff -u bitmap1 bitmap2
	./test_archive_manager 16
	./test_gzip
	./test_queue > tmp/test_queue.log
	./test_fork
	./test_fork3
	./test_stream_socket

tmpdir:
	if [ ! -d tmp ]; then mkdir tmp; fi
	if [ ! -d tmp/out ]; then mkdir tmp/out; fi

clean:
	rm -f *.o $(BINARIES)

rebuild: clean build

test_header: test_header.cpp ../src/header.o ../src/util.o ../src/serialize.o
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_header.cpp ../src/header.o ../src/util.o ../src/serialize.o

test_serialize: test_serialize.cpp ../src/serialize.o
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_serialize.cpp ../src/serialize.o

test_fdiostream: test_fdiostream.cpp ../src/fdiostream.hpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_fdiostream.cpp

test_bitmap: test_bitmap.cpp ../src/bitmap.hpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_bitmap.cpp

test_archive_manager: test_archive_manager.cpp ../src/archive_manager.hpp ../src/serialize.o ../src/archive_manager.o ../src/util.o ../src/header.o
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_archive_manager.cpp ../src/serialize.o ../src/archive_manager.o ../src/util.o ../src/header.o

test_gzip: test_gzip.cpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_gzip.cpp

test_queue: test_queue.cpp ../src/queue.hpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS)  test_queue.cpp

test_fork: test_fork.cpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS)  test_fork.cpp

test_fork2: test_fork2.cpp ../src/fork_manager.hpp ../src/vddk_manager.hpp
	$(CXX) -o $@ -Wno-write-strings $(CFLAGS) $(LDFLAGS)  test_fork2.cpp -lvmdkbkp -L../src

test_fork3: test_fork3.cpp ../src/fork_manager.hpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS)  test_fork3.cpp

test_stream_socket: test_stream_socket.cpp ../src/fork_manager.hpp ../src/stream_socket.o
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS)  test_stream_socket.cpp ../src/stream_socket.o

test_vddk_wrapper: test_vddk_wrapper.cpp
	$(CXX) -o $@ -Wno-write-strings $(CFLAGS) $(LDFLAGS) test_vddk_wrapper.cpp -lvmdkbkp -L../src

test_data_reader: test_data_reader.cpp ../src/data_reader.hpp ../src/queue.hpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_data_reader.cpp -lvmdkbkp -L../src

test_data_writer: test_data_writer.cpp ../src/data_writer.hpp ../src/queue.hpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_data_writer.cpp

test_archive_io_manager: test_archive_io_manager.cpp ../src/archive_io_manager.hpp ../src/data_reader.hpp ../src/data_writer.hpp ../src/queue.hpp ../src/serialize.o ../src/util.o ../src/header.o
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_archive_io_manager.cpp ../src/serialize.o  ../src/util.o ../src/header.o

test_thread: test_thread.cpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_thread.cpp

test_fork_thread: test_fork_thread.cpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_fork_thread.cpp

test_fork4: test_fork4.cpp ../src/fork_manager.hpp ../src/util.hpp ../src/util.o
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS)  test_fork4.cpp ../src/util.o

test_file_lock: test_file_lock.cpp ../src/file_lock.hpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_file_lock.cpp

test_file_lock2: test_file_lock2.cpp ../src/file_lock.hpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_file_lock2.cpp

test_mq: test_mq.cpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_mq.cpp

test_ipc_mq: test_ipc_mq.cpp ../src/ipc_mq.hpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_ipc_mq.cpp

test_ipc_lock: test_ipc_lock.cpp ../src/ipc_lock.hpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_ipc_lock.cpp

test_ipc_lock_manager: test_ipc_lock_manager.cpp ../src/ipc_lock_manager.hpp
	$(CXX) -o $@ $(CFLAGS) $(LDFLAGS) test_ipc_lock_manager.cpp
