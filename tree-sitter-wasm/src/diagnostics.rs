use serde::Serialize;

use crate::ir::Span;

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum DiagnosticSeverity {
    Error,
    Warning,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
pub struct Diagnostic {
    pub code: String,
    pub severity: DiagnosticSeverity,
    pub message: String,
    pub span: Option<Span>,
    pub declaration_name: Option<String>,
}

impl Diagnostic {
    pub fn error(
        code: impl Into<String>,
        message: impl Into<String>,
        span: Option<Span>,
        declaration_name: Option<&str>,
    ) -> Self {
        Self {
            code: code.into(),
            severity: DiagnosticSeverity::Error,
            message: message.into(),
            span,
            declaration_name: declaration_name.map(str::to_string),
        }
    }

    pub fn warning(
        code: impl Into<String>,
        message: impl Into<String>,
        span: Option<Span>,
        declaration_name: Option<&str>,
    ) -> Self {
        Self {
            code: code.into(),
            severity: DiagnosticSeverity::Warning,
            message: message.into(),
            span,
            declaration_name: declaration_name.map(str::to_string),
        }
    }
}
