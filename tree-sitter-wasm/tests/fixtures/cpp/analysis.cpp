namespace api {
struct Child { std::string name; };
class Root {
    int hidden;
public:
    bool active;
    static int count;
    void run();
    std::vector<std::optional<int>> items;
    std::map<std::string, Child> children;
};
using Roots = std::vector<Root>;
}
