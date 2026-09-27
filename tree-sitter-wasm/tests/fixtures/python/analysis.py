from typing import TypedDict, Required, NotRequired, Optional, ClassVar
class Base(TypedDict, total=False):
    keep: Required[int]
    maybe: str | None
class Child(Base):
    extra: NotRequired[list["Node"]]
class Node:
    name: str
    cache: ClassVar[int] = 0
Loose = TypedDict("Loose", {"a-b": Required[str], "x": Optional[int]}, total=False)
Alias = list[Child]
