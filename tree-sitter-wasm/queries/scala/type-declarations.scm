(class_definition name: (_) @declaration.name) @declaration
(trait_definition name: (_) @declaration.name) @declaration
(enum_definition name: (_) @declaration.name) @declaration
(type_definition name: (type_identifier) @alias.name type: (_) @alias.type) @alias
(class_parameter name: (_) @field.name type: (_) @field.type) @field
(val_definition pattern: (_) @field.name type: (_) @field.type) @field
(var_definition pattern: (_) @field.name type: (_) @field.type) @field
(simple_enum_case name: (_) @enum.value) @enum.member
