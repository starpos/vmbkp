#include <iostream>

int main()
{

    int i, j;
    for (i = 0, j = 0; i < 10 && j < 10; ++i, ++j) {
        std::cout << "i:" << i << ", "
                  << "j:" << j << "\n";
    }

    

    return 0;
}
