#include <iostream>
#include <fstream>
#include <string>

/* for copy1 */
#include <boost/iostreams/device/file_descriptor.hpp>
#include <boost/iostreams/device/file.hpp>
#include <boost/iostreams/filtering_stream.hpp>

/* for copy2 */
#include <boost/iostreams/filtering_streambuf.hpp>
#include <boost/iostreams/filter/gzip.hpp>

#include <boost/iostreams/copy.hpp>

#include <time.h>
#include <stdlib.h>
#include <assert.h>

namespace io = boost::iostreams;

void copy(const std::string& inFile, const std::string& outFile)
{
    io::filtering_istream in;
    in.push(io::gzip_decompressor());
    in.push(io::file_source(inFile));
    
    io::filtering_ostream out;
    out.push(io::gzip_compressor(io::gzip_params(io::gzip::best_speed)));
    out.push(io::file_sink(outFile));

    io::copy(in, out);
}

void copy2(const std::string& inFile, const std::string& outFile)
{
    std::ifstream fin(inFile.c_str(), std::ios_base::binary);
    io::filtering_streambuf<io::input> in;
    in.push(io::gzip_decompressor());
    in.push(fin);

    std::ofstream fout(outFile.c_str(), std::ios_base::binary);
    io::filtering_streambuf<io::output> out;
    out.push(io::gzip_compressor(io::gzip_params(io::gzip::best_speed)));
    out.push(fout);

    io::copy(in, out);
}

char makeRandomByte()
{
    int a = static_cast<int>(::rand() / ((double) RAND_MAX + 1.0) * 256);
    return static_cast<char>(a - 128);
}

void writeRandomChar(std::ostream& out, char c)
{
    out << c;
}

void genRandGzipedFile(const std::string& filename, size_t size)
{
    io::filtering_ostream out;
    out.push(io::gzip_compressor(io::gzip_params(io::gzip::best_speed)));
    out.push(io::file_sink(filename));

    size_t i;
    for (i = 0; i < size; i ++) {
        writeRandomChar(out, makeRandomByte());
    }
}

bool isSameGzipedFiles(const std::string& file1,
                       const std::string& file2)
{
    io::filtering_istream in1, in2;
    in1.push(io::gzip_decompressor());
    in1.push(io::file_source(file1));
    in2.push(io::gzip_decompressor());
    in2.push(io::file_source(file2));
    
    char c1,c2;

    while (in1.peek() != EOF && in2.peek() != EOF) {
        in1 >> c1;
        in2 >> c2;

        if (c1 != c2) {
            return false;
        }
    }
    return true;
}


int main(int argc, char *argv[])
{
    ::srand(time(0));

    const std::string src("tmp/testfile1.gz");
    const std::string dst1("tmp/testfile2.gz");
    const std::string dst2("tmp/testfile3.gz");
    
    genRandGzipedFile(src, 1048576);
    
    copy(src, dst1);
    copy2(src, dst2);

    assert(isSameGzipedFiles(src, dst1));
    assert(isSameGzipedFiles(src, dst2));
    assert(isSameGzipedFiles(dst1, dst2));
    
    return 0;
}
