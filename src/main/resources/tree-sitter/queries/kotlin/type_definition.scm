[
  (class_declaration
    name: (type_identifier) @type.name)
  (object_declaration
    name: (simple_identifier) @type.name)
] @type.declaration

(property_declaration
  (variable_declaration
    name: (simple_identifier) @field.name
    type: (_) @field.type))
