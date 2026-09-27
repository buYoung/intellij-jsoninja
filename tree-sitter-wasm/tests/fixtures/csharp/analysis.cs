using System.Text.Json.Serialization;
namespace Demo {
using People = System.Collections.Generic.List<Person>;
public record Person([property: JsonPropertyName("full-name")] string Name, int Age = 0);
public class Root {
    private string Hidden;
    public static int Count;
    [JsonPropertyName("raw-name")]
    public string Name { get; set; }
    public List<Person?> People { get; set; }
    public int x, y;
    public string this[int index] { get => ""; }
    public void Run() {}
}
}
