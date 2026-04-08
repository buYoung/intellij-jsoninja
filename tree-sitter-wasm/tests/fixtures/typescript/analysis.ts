
        type ApiResponse<T> = Record<string, Array<T | null>>;
        interface User extends Base {
          name?: string;
          profile: { age: number; active: boolean };
        }
        
