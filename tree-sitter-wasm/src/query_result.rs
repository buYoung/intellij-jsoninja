use serde::Serialize;

use crate::diagnostics::Diagnostic;
use crate::ir::Span;

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
pub struct QueryCaptureResult {
    pub name: String,
    pub text: String,
    pub span: Span,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
pub struct QueryExecutionResult {
    pub captures: Vec<QueryCaptureResult>,
    pub diagnostics: Vec<Diagnostic>,
    pub has_syntax_errors: bool,
}
