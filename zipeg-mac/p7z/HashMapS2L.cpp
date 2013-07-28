#include "HashMapS2L.hpp"

static const char* REMOVED = "this entry has been removed";

HashMapS2L::HashMapS2L(int c) : HashMapS2L(c, 0) {
}

HashMapS2L::HashMapS2L(int c, int64_t not_a_value) {
    assert(c >= 4);
    if (c < 4) {
        throw "HashMapS2L capacity < 4";
    } else {
        keys = (char**)calloc(c, sizeof(char*));
        values = (int64_t*)calloc(c, sizeof(int64_t));
        capacity = c;
        not_found = not_a_value;
    }
}

HashMapS2L::~HashMapS2L() {
    if (keys != null) {
        int i = 0;
        while (i < capacity) {
            if (keys[i] != null && keys[i] != REMOVED) {
                free(keys[i]);
                keys[i] = null;
            }
            i++;
        }
        free(keys);
        keys = null;
    }
    if (values != null) {
        free(values);
        values = null;
    }
}

static int hash(const char* k, int capacity) { /* assumes k != null && k[0] != 0 */
    int h = *k++;
    while (*k) {
        h = h * 13 + *k++;
    }
    h = (h & 0x7FFFFFFF) % capacity;
    return h;
}

bool HashMapS2L::put(const char* k, int64_t v) {
    if (v == not_found || k == null || k[0] == 0 || keys == null || values == null) {
        return false;
    } else {
        int h = hash(k, capacity);
        int h0 = h;
        for (;;) {
            if (keys[h] == null || keys[h] == k || strcmp(keys[h], k) == 0 || keys[h] == REMOVED) {
                break;
            }
            h = (h + 1) % capacity;
            if (h == h0) {
                return true;
            }
        }
        if (keys[h] == null || keys[h] == REMOVED) {
            const char* s = strdup(k);
            if (s == null) {
                return false;
            }
            keys[h] = (char*)s;
        }
        values[h] = v;
        return true;
    }
}

int64_t HashMapS2L::remove(const char* k) {
    if (k == null || k[0] == 0 || keys == null || values == null) {
        return not_found;
    }
    int h = hash(k, capacity);
    int h0 = h;
    for (;;) {
        if (keys[h] == null) {
            return not_found;
        }
        if (strcmp(keys[h], k) == 0) {
            free(keys[h]);
            keys[h] = (char*)REMOVED;
            return values[h];
        }
        h = (h + 1) % capacity;
        if (h == h0) {
            return not_found;
        }
    }
}

int64_t HashMapS2L::get(const char* k) const {
    int h = hash(k, capacity);
    int h0 = h;
    for (;;) {
        if (keys[h] == null) {
            return not_found;
        }
        if (strcmp(keys[h], k) == 0) {
            return values[h];
        }
        h = (h + 1) % capacity;
        if (h == h0) {
            return not_found;
        }
    }
}

const char* HashMapS2L::keyAt(int pos) const {
    assert(0 <= pos && pos < capacity);
    const char* k = keys[pos];
    return k == REMOVED ? null : k;
}

