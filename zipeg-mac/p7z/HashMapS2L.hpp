#ifndef __HashMapS2L__
#define __HashMapS2L__

#include "c.h"

class HashMapS2L {
public: // HashMap never grows nor rehashes the keys
    HashMapS2L(int capacity); // not_a_value == 0
    HashMapS2L(int capacity, uint64_t not_a_value);
    virtual ~HashMapS2L();
    // put makes a copy of the key; may return false on overflow
    // or not enough memory for the map at contruction time
    bool put(const char* k, uint64_t v);
    // get returns 
    uint64_t get(const char* k);
    uint64_t remove(const char* k);
private:
    int capacity;
    char** keys;
    uint64_t* values;
    uint64_t not_found;
};

#endif
