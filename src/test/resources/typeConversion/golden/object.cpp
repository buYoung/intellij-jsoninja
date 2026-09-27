/* Warning: C++ field 'zipCode' cannot retain JSON key 'zip-code' without a serializer mapping. */

#include <cstdint>
#include <string>
#include <vector>

struct RootAddres {
    std::string zipCode;
};

struct Root {
    bool active;
    RootAddres address;
    std::int64_t id;
    std::string name;
    std::vector<std::int64_t> scores;
};
