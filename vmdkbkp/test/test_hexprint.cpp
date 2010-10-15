#include <iostream>
#include <stdio.h>

int main()
{
    char c;
    int i, j;
    unsigned char u;

    c = 43;
    u = static_cast<unsigned char>(c);
    i = static_cast<int>(c);
    j = static_cast<int>(u);
    
    printf("%02x %02x %02x %02x\n", c, u, i, j);
    std::cout.flush();
    std::cout << std::hex
              << c << " "
              << u << " "
              << i << " "
              << j << "\n";

    return 0;
}
