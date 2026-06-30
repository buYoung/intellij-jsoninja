use serde::Serialize;

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum PrimitiveKind {
    String,
    Integer,
    Decimal,
    Number,
    Boolean,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum DeclarationKind {
    Class,
    Interface,
    Record,
    Struct,
    Enum,
    TypeAlias,
    Object,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
pub struct Span {
    pub start_row: usize,
    pub start_col: usize,
    pub end_row: usize,
    pub end_col: usize,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
pub struct Annotation {
    pub name: String,
    pub text: String,
    pub span: Span,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
pub struct TypeParameter {
    pub name: String,
    pub constraints: Vec<TypeReference>,
    pub span: Span,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
pub struct EnumValue {
    pub name: String,
    pub value_text: Option<String>,
    pub span: Span,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
pub struct Field {
    pub name: String,
    pub source_name: String,
    pub optional: bool,
    pub span: Span,
    pub annotations: Vec<Annotation>,
    #[serde(rename = "type")]
    pub type_reference: TypeReference,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
pub struct Declaration {
    pub name: String,
    pub kind: DeclarationKind,
    pub span: Span,
    pub annotations: Vec<Annotation>,
    pub type_parameters: Vec<TypeParameter>,
    pub super_types: Vec<TypeReference>,
    pub fields: Vec<Field>,
    pub enum_values: Vec<EnumValue>,
    pub aliased_type: Option<TypeReference>,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum TypeReference {
    Primitive {
        primitive: PrimitiveKind,
        span: Span,
    },
    Named {
        name: String,
        type_arguments: Vec<TypeReference>,
        is_type_parameter: bool,
        span: Span,
    },
    List {
        element_type: Box<TypeReference>,
        span: Span,
    },
    Map {
        key_type: Box<TypeReference>,
        value_type: Box<TypeReference>,
        span: Span,
    },
    Nullable {
        wrapped_type: Box<TypeReference>,
        span: Span,
    },
    Union {
        members: Vec<TypeReference>,
        span: Span,
    },
    InlineObject {
        fields: Vec<Field>,
        span: Span,
    },
    Unknown {
        raw_text: String,
        span: Span,
    },
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
pub struct LanguageDescriptor {
    pub id: i32,
    pub name: &'static str,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
pub struct AnalysisOutput {
    pub language: &'static str,
    pub declarations: Vec<Declaration>,
    pub diagnostics: Vec<crate::diagnostics::Diagnostic>,
}
