#ifndef __HashMapS2L__
#define __HashMapS2L__

#include "c.h"

class HashMapS2L {
public: // HashMap never grows nor rehashes the keys
    HashMapS2L(int capacity); // not_a_value == 0
    HashMapS2L(int capacity, int64_t not_a_value);
    virtual ~HashMapS2L();
    // put makes a copy of the key; may return false on overflow
    // or not enough memory for the map at contruction time
    bool put(const char* k, int64_t v);
    // get returns 
    int64_t get(const char* k) const;
    int64_t remove(const char* k);
    const char* keyAt(int pos) const; // pos in [0..capacity-1], null if not_a_value
    int getCapacity() const { return capacity; }
private:
    int capacity;
    char** keys;
    int64_t* values;
    int64_t not_found;
};

#endif
