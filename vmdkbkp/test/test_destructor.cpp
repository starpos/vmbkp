#include <stdio.h>

struct TestSuper
{
    int i;
    TestSuper() : i(0) {}
    ~TestSuper() {::printf("TestSuper ends.\n");}
};

struct TestSub : TestSuper
{
    int j;
    TestSub() : TestSuper(), j(0) {}
    ~TestSub() {::printf("TestSub ends.\n");}
};


int main()
{
    TestSub a;

    return 0;
}
