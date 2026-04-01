;; Captures TypeScript declarations used by JSON <-> Type conversion.
;; The query focuses on interfaces, type aliases, classes, enums, and fields.
[
  (interface_declaration
    "interface" @type.kind
    name: (type_identifier) @type.name)
  (type_alias_declaration
    "type" @type.kind
    name: (type_identifier) @type.name)
  (class_declaration
    "class" @type.kind
    name: (type_identifier) @type.name)
  (abstract_class_declaration
    "class" @type.kind
    name: (type_identifier) @type.name)
  (enum_declaration
    "enum" @type.kind
    name: (identifier) @type.name)
] @type.declaration

(decorator) @annotation

[
  (interface_declaration
    type_parameters: (type_parameters
      (type_parameter
        (type_identifier) @generic.param)))
  (type_alias_declaration
    type_parameters: (type_parameters
      (type_parameter
        (type_identifier) @generic.param)))
  (class_declaration
    type_parameters: (type_parameters
      (type_parameter
        (type_identifier) @generic.param)))
  (abstract_class_declaration
    type_parameters: (type_parameters
      (type_parameter
        (type_identifier) @generic.param)))
]

(interface_declaration
  (extends_type_clause
    type: (_) @extends))

(class_declaration
  (class_heritage
    (extends_clause
      value: (_) @extends)))

(abstract_class_declaration
  (class_heritage
    (extends_clause
      value: (_) @extends)))

(property_signature
  name: [
    (property_identifier)
    (private_property_identifier)
    (string)
    (number)
  ] @field.name
  type: (type_annotation (_) @field.type)) @field.declaration

(property_signature
  "?" @field.nullable)

(public_field_definition
  name: [
    (property_identifier)
    (private_property_identifier)
    (string)
    (number)
  ] @field.name
  type: (type_annotation (_) @field.type)) @field.declaration

(public_field_definition
  "?" @field.nullable)
