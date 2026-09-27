from typing import Any, NotRequired, TypedDict

RootAddres = TypedDict("RootAddres", {
    "zip-code": str
})

class Root(TypedDict):
    active: bool
    address: RootAddres
    id: int
    name: str
    scores: list[int]
