;; Captures Java type declarations and the most common structural elements
;; needed by the type-conversion pipeline.
[
  (class_declaration
    "class" @type.kind
    name: (identifier) @type.name
    body: (class_body))
  (interface_declaration
    "interface" @type.kind
    name: (identifier) @type.name
    body: (interface_body))
  (enum_declaration
    "enum" @type.kind
    name: (identifier) @type.name
    body: (enum_body))
  (record_declaration
    "record" @type.kind
    name: (identifier) @type.name
    parameters: (formal_parameters)
    body: (class_body))
] @type.declaration

[
  (marker_annotation)
  (annotation)
] @annotation

(class_declaration
  type_parameters: (type_parameters
    (type_parameter
      (type_identifier) @generic.param)))

(interface_declaration
  type_parameters: (type_parameters
    (type_parameter
      (type_identifier) @generic.param)))

(record_declaration
  type_parameters: (type_parameters
    (type_parameter
      (type_identifier) @generic.param)))

(class_declaration
  superclass: (superclass (_) @extends))

(class_declaration
  interfaces: (super_interfaces (_) @extends))

(interface_declaration
  (extends_interfaces (_) @extends))

(record_declaration
  interfaces: (super_interfaces (_) @extends))

(field_declaration
  type: (_) @field.type
  declarator: (variable_declarator
    name: (identifier) @field.name)) @field.declaration

(record_declaration
  parameters: (formal_parameters
    (formal_parameter
      type: (_) @field.type
      name: (identifier) @field.name) @field.declaration))
