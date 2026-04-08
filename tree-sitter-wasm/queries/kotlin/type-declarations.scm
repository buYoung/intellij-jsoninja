;; Captures Kotlin classes, interfaces, objects, constructor properties,
;; body properties, and the common metadata required by the runtime analyzer.
[
  (class_declaration
    "class" @type.kind
    name: (identifier) @type.name)
  (class_declaration
    "interface" @type.kind
    name: (identifier) @type.name)
  (object_declaration
    "object" @type.kind
    name: (identifier) @type.name)
] @type.declaration

(annotation) @annotation

(class_declaration
  (type_parameters
    (type_parameter
      (identifier) @generic.param)))

(class_declaration
  (delegation_specifiers
    (delegation_specifier) @extends))

(object_declaration
  (delegation_specifiers
    (delegation_specifier) @extends))

(class_parameter
  (identifier) @field.name
  . (_) @field.type) @field.declaration

(property_declaration
  (variable_declaration
    (identifier) @field.name)
  . (_) @field.type) @field.declaration

(class_parameter
  (nullable_type) @field.nullable)

(property_declaration
  (nullable_type) @field.nullable)
