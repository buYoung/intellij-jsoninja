type RootAddres struct {
    ZipCode string `json:"zip-code"`
}

type Root struct {
    Active bool `json:"active"`
    Address RootAddres `json:"address"`
    Id int `json:"id"`
    Name string `json:"name"`
    Scores []int `json:"scores"`
}
