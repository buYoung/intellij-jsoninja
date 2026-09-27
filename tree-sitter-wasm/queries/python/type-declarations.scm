(class_definition name: (identifier) @declaration.name) @declaration
(assignment left: (identifier) @alias.name) @alias
(assignment left: (identifier) @field.name type: (_) @field.type) @field
(pair key: (string) @field.name value: (_) @field.type) @field
