;; Captures Go type declarations for structs, interfaces, aliases, and
;; named struct fields. Struct tags are surfaced as annotations.
[
  (type_spec
    name: (type_identifier) @type.name
    type: (struct_type
      "struct" @type.kind))
  (type_spec
    name: (type_identifier) @type.name
    type: (interface_type
      "interface" @type.kind))
  (type_alias
    name: (type_identifier) @type.name)
] @type.declaration

(type_parameter_declaration
  name: (identifier) @generic.param)

(field_declaration
  name: (field_identifier) @field.name
  type: (_) @field.type) @field.declaration

(field_declaration
  tag: [
    (raw_string_literal)
    (interpreted_string_literal)
  ] @annotation)

(type_spec
  type: (struct_type
    (field_declaration_list
      (field_declaration
        !name
        type: [
          (type_identifier)
          (qualified_type)
          (generic_type)
          (pointer_type)
        ] @extends))))
