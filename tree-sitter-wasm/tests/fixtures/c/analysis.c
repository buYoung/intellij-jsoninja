typedef struct Address { int zip; } Address;
typedef struct Root {
    int a, b;
    bool enabled;
    char *label;
    Address address;
    int values[3];
    int *maybe;
} Root;
