[
  (class_declaration
    name: (identifier) @type.name)
  (record_declaration
    name: (identifier) @type.name)
] @type.declaration

(field_declaration
  type: (_) @field.type
  declarator: (variable_declarator
    name: (identifier) @field.name))
