(struct_specifier name: (_) @declaration.name body: (field_declaration_list)) @declaration
(class_specifier name: (_) @declaration.name body: (field_declaration_list)) @declaration
(alias_declaration name: (type_identifier) @alias.name) @alias
(enum_specifier name: (_) @enum.name body: (enumerator_list)) @enum
(field_declaration declarator: (_) @field.declarator) @field
(type_definition declarator: (type_identifier) @alias.name) @alias
