#nullable enable
using System.Collections.Generic;
using System.Text.Json.Serialization;

public class RootAddres
{
    [JsonPropertyName("zip-code")]
    public string ZipCode { get; set; } = default!;
}

public class Root
{
    [JsonPropertyName("active")]
    public bool Active { get; set; } = default!;
    [JsonPropertyName("address")]
    public RootAddres Address { get; set; } = default!;
    [JsonPropertyName("id")]
    public long Id { get; set; } = default!;
    [JsonPropertyName("name")]
    public string Name { get; set; } = default!;
    [JsonPropertyName("scores")]
    public List<long> Scores { get; set; } = default!;
}
