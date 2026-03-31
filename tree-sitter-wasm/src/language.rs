use tree_sitter::Language;

use crate::error::WasmResult;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SupportedLanguage {
    Java,
    Kotlin,
    TypeScript,
    Go,
}

impl SupportedLanguage {
    pub fn from_id(language_id: i32) -> Option<Self> {
        match language_id {
            0 => Some(Self::Java),
            1 => Some(Self::Kotlin),
            2 => Some(Self::TypeScript),
            3 => Some(Self::Go),
            _ => None,
        }
    }

    pub fn all() -> [Self; 4] {
        [Self::Java, Self::Kotlin, Self::TypeScript, Self::Go]
    }

    pub fn as_json_name(self) -> &'static str {
        match self {
            Self::Java => "java",
            Self::Kotlin => "kotlin",
            Self::TypeScript => "typescript",
            Self::Go => "go",
        }
    }

    pub fn to_tree_sitter_language(self) -> WasmResult<Language> {
        #[cfg(feature = "language-grammars")]
        {
            let language = match self {
                Self::Java => tree_sitter_java::LANGUAGE.into(),
                Self::Kotlin => tree_sitter_kotlin_ng::LANGUAGE.into(),
                Self::TypeScript => tree_sitter_typescript::LANGUAGE_TYPESCRIPT.into(),
                Self::Go => tree_sitter_go::LANGUAGE.into(),
            };
            Ok(language)
        }

        #[cfg(not(feature = "language-grammars"))]
        {
            let _ = self;
            Err(crate::error::WasmRuntimeError::new(
                crate::error::WasmErrorCode::InvalidLanguage,
                "Language grammars are not enabled in this build.",
            ))
        }
    }
}

pub fn supported_languages_json() -> String {
    let languages = SupportedLanguage::all()
        .iter()
        .map(|language| format!("\"{}\"", language.as_json_name()))
        .collect::<Vec<_>>()
        .join(",");
    format!("[{languages}]")
}
