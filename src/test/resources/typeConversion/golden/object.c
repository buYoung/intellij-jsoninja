/* Warning: C field 'zip_code' cannot retain JSON key 'zip-code' without a serializer mapping. */

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

typedef struct RootAddres {
    char * zip_code;
} RootAddres;

/* JSONinja collection: array v1 */
typedef struct JsoninjaArray1 {
    size_t length;
    int64_t *data;
} JsoninjaArray1;

typedef struct Root {
    bool active;
    RootAddres address;
    int64_t id;
    char * name;
    JsoninjaArray1 scores;
} Root;
