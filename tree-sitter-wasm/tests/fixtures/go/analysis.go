
        type UserResponse[T interface{ ~int | ~string }] struct {
            Name string `json:"name"`
            Scores []int
            Meta map[string]*string
        }
        
