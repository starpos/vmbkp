#include <stdio.h>
#include "generator.hpp"

struct Test1
{
    int i_;
    Test1() : i_(0) {}
};

struct Test2
{
    int i_;
    Test2(int i) : i_(i) {}
};

int main()
{

    Generator0<Test1> gen0;
    Generator1<Test2, int> gen1(5);

    Test1* t1 = gen0();
    Test1* t2 = gen0();

    ::printf("%d, %d\n", t1->i_, t2->i_);

    Test2* t3 = gen1();
    Test2* t4 = gen1();

    ::printf("%d, %d\n", t3->i_, t4->i_);

    return 0;
}
