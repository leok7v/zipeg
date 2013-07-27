#include <stdio.h>
#include <stdlib.h>
#include <io.h>
#include <string.h>
#include <direct.h>


static int stridx(char* s, int ix, char ch) {
    char* c = strchr(&s[ix], ch);
    return c == NULL ? -1 : c - s; 
}

int main(int argc, char* argv[]) {
    if (argc < 2) {
        fprintf(stderr, "envsub <filename>\n "
            "will replace all occurances of $environment_variables$ with "
            "their values (if present) or empty string and will spit the result to stdout.");
        exit(1);
    }
    FILE* f = fopen(argv[1], "rb");
    char cwd[1024];
    _getcwd(cwd, sizeof cwd);
    if (f == NULL) {
        fprintf(stderr, "file not found: %s in %s\n", argv[1], cwd);
        exit(1);
    }
    fseek(f, 0, SEEK_END);
    int size=ftell(f);
    char* buf = (char*)malloc(size + 1);
    memset(buf, 0, size + 1);
    fseek(f, 0, SEEK_SET);
    if (fread(buf, 1, size, f) != size) {
        fprintf(stderr, "failed to read file: %s\n", argv[1]);
        exit(1);
    }
    fclose(f);
    int i = 0;
    int k = 0;
    while (i < size) {
        i = stridx(buf, k, '$');
        if (i < 0) {
            i = size;
            break;
        }
        int j = stridx(buf, i + 1, '$');
        if (j < 0) {
            i = size;
            break;
        }
        if (i - k > 0) {
            fwrite(&buf[k], 1, i - k, stdout);
        }
        if (j - i - 1 > 0) {
            char* name = (char*)malloc(j - i);
            memset(name, 0, j - i);
            memcpy(name, &buf[i+1], j - i - 1);
            char* v = getenv(name);
            if (v != NULL && strlen(v) > 0) {
                fwrite(v, 1, strlen(v), stdout);
            }
            free(name);
        }
        i = k = j + 1;
    }
    if (i - k > 0) {
        fwrite(&buf[k], 1, i - k, stdout);
    }
    return 0;
}

