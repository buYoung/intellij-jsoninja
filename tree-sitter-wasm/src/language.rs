use tree_sitter::Language;

use crate::error::WasmResult;
use crate::ir::LanguageDescriptor;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SupportedLanguage {
    Java,
    Kotlin,
    TypeScript,
    Go,
    C,
    Cpp,
    CSharp,
    Python,
    Rust,
    Scala,
    JsDoc,
}

impl SupportedLanguage {
    pub fn from_id(language_id: i32) -> Option<Self> {
        match language_id {
            0 => Some(Self::Java),
            1 => Some(Self::Kotlin),
            2 => Some(Self::TypeScript),
            3 => Some(Self::Go),
            4 => Some(Self::C),
            5 => Some(Self::Cpp),
            6 => Some(Self::CSharp),
            7 => Some(Self::Python),
            8 => Some(Self::Rust),
            9 => Some(Self::Scala),
            10 => Some(Self::JsDoc),
            _ => None,
        }
    }

    pub fn all() -> [Self; 11] {
        [Self::Java, Self::Kotlin, Self::TypeScript, Self::Go, Self::C, Self::Cpp, Self::CSharp, Self::Python, Self::Rust, Self::Scala, Self::JsDoc]
    }

    pub fn as_json_name(self) -> &'static str {
        match self {
            Self::Java => "java",
            Self::Kotlin => "kotlin",
            Self::TypeScript => "typescript",
            Self::Go => "go",
            Self::C => "c",
            Self::Cpp => "cpp",
            Self::CSharp => "csharp",
            Self::Python => "python",
            Self::Rust => "rust",
            Self::Scala => "scala",
            Self::JsDoc => "jsdoc",
        }
    }

    pub fn id(self) -> i32 {
        match self {
            Self::Java => 0,
            Self::Kotlin => 1,
            Self::TypeScript => 2,
            Self::Go => 3,
            Self::C => 4,
            Self::Cpp => 5,
            Self::CSharp => 6,
            Self::Python => 7,
            Self::Rust => 8,
            Self::Scala => 9,
            Self::JsDoc => 10,
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
                Self::C => tree_sitter_c::LANGUAGE.into(),
                Self::Cpp => tree_sitter_cpp::LANGUAGE.into(),
                Self::CSharp => tree_sitter_c_sharp::LANGUAGE.into(),
                Self::Python => tree_sitter_python::LANGUAGE.into(),
                Self::Rust => tree_sitter_rust::LANGUAGE.into(),
                Self::Scala => tree_sitter_scala::LANGUAGE.into(),
                Self::JsDoc => tree_sitter_javascript::LANGUAGE.into(),
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

pub fn supported_languages() -> Vec<LanguageDescriptor> {
    SupportedLanguage::all()
        .into_iter()
        .map(|language| LanguageDescriptor {
            id: language.id(),
            name: language.as_json_name(),
        })
        .collect()
}
