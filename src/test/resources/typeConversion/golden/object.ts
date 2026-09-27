export interface RootAddres {
  "zip-code": string;
}

export interface Root {
  active: boolean;
  address: RootAddres;
  id: number;
  name: string;
  scores: number[];
}
