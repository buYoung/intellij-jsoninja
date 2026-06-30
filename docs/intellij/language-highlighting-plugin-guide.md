# 언어 하이라이팅 플러그인 구현 가이드

이 문서는 IntelliJ Platform 기반 언어 플러그인에서 코드 하이라이팅을 붙이는 가장 작은 구현 단위를 코드 조각 중심으로 정리한 가이드입니다.

기본 하이라이팅은 아래 순서로 붙습니다.

1. `Language`와 `LanguageFileType`로 파일과 언어를 연결합니다.
2. 렉서가 토큰을 만듭니다.
3. `SyntaxHighlighter`가 토큰을 색상 키로 매핑합니다.
4. `SyntaxHighlighterFactory`가 에디터에 하이라이터를 제공합니다.
5. `ColorSettingsPage`가 설정 화면에 색상 항목과 데모 텍스트를 노출합니다.

문맥을 봐야만 칠할 수 있는 경우에는 마지막에 `Annotator`를 추가합니다.

## 1. 최소 구성

하이라이팅만 먼저 붙일 때 필요한 최소 파일은 아래 정도입니다.

- `MyLanguage`
- `MyFileType`
- `MyTokenType`
- `MyLexerAdapter`
- `MySyntaxHighlighterColors`
- `MySyntaxHighlighter`
- `MySyntaxHighlighterFactory`
- `MyColorSettingsPage`
- `plugin.xml`

아래 예시는 언어 이름을 `Demo`라고 가정한 최소 골격입니다.

## 2. `Language`

```java
package com.example.demo;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;

public final class DemoLanguage extends Language {
  public static final DemoLanguage INSTANCE = new DemoLanguage();

  private DemoLanguage() {
    super("Demo");
  }

  @Override
  public LanguageFileType getAssociatedFileType() {
    return DemoFileType.INSTANCE;
  }
}
```

핵심은 `super("Demo")`에 들어가는 이름입니다. 이 값은 `plugin.xml`의 `language="Demo"`와 정확히 같아야 합니다.

## 3. `LanguageFileType`

```java
package com.example.demo;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class DemoFileType extends LanguageFileType {
  public static final DemoFileType INSTANCE = new DemoFileType();

  private DemoFileType() {
    super(DemoLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "Demo";
  }

  @Override
  public @NotNull String getDescription() {
    return "Demo language file";
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "demo";
  }

  @Override
  public Icon getIcon() {
    return null;
  }
}
```

아이콘이 없다면 일단 `null`로 시작해도 됩니다.

## 4. 토큰 타입

렉서가 내보내는 토큰 타입이 있어야 하이라이터가 색을 매길 수 있습니다.

```java
package com.example.demo.psi;

import com.example.demo.DemoLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class DemoTokenType extends IElementType {
  public DemoTokenType(@NotNull @NonNls String debugName) {
    super(debugName, DemoLanguage.INSTANCE);
  }

  @Override
  public String toString() {
    return "DemoTokenType." + super.toString();
  }
}
```

토큰 상수는 보통 별도 인터페이스나 클래스에 모읍니다.

```java
package com.example.demo.psi;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

public interface DemoTokenTypes {
  IElementType KEYWORD = new DemoTokenType("KEYWORD");
  IElementType IDENTIFIER = new DemoTokenType("IDENTIFIER");
  IElementType STRING = new DemoTokenType("STRING");
  IElementType NUMBER = new DemoTokenType("NUMBER");
  IElementType LINE_COMMENT = new DemoTokenType("LINE_COMMENT");
  IElementType L_PAREN = new DemoTokenType("L_PAREN");
  IElementType R_PAREN = new DemoTokenType("R_PAREN");
  IElementType COMMA = new DemoTokenType("COMMA");
  IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;
}
```

## 5. 렉서

실제 프로젝트에서는 보통 JFlex나 Grammar-Kit이 만든 렉서를 감싸서 씁니다. 여기서는 구조만 보이도록 어댑터만 둡니다.

```java
package com.example.demo.lexer;

import com.intellij.lexer.FlexAdapter;

public final class DemoLexerAdapter extends FlexAdapter {
  public DemoLexerAdapter() {
    super(new _DemoLexer(null));
  }
}
```

이미 손으로 작성한 렉서가 있다면 `SyntaxHighlighter`에서 그 렉서를 바로 반환하면 됩니다.

## 6. 색상 키 모음

색상 키는 보통 별도 클래스로 분리해두면 `SyntaxHighlighter`와 `ColorSettingsPage`에서 함께 쓰기 좋습니다.

```java
package com.example.demo.highlighting;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

public final class DemoSyntaxHighlighterColors {
  public static final TextAttributesKey KEYWORD =
    TextAttributesKey.createTextAttributesKey("DEMO.KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);

  public static final TextAttributesKey IDENTIFIER =
    TextAttributesKey.createTextAttributesKey("DEMO.IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);

  public static final TextAttributesKey STRING =
    TextAttributesKey.createTextAttributesKey("DEMO.STRING", DefaultLanguageHighlighterColors.STRING);

  public static final TextAttributesKey NUMBER =
    TextAttributesKey.createTextAttributesKey("DEMO.NUMBER", DefaultLanguageHighlighterColors.NUMBER);

  public static final TextAttributesKey COMMENT =
    TextAttributesKey.createTextAttributesKey("DEMO.COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);

  public static final TextAttributesKey PARENTHESES =
    TextAttributesKey.createTextAttributesKey("DEMO.PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES);

  public static final TextAttributesKey COMMA =
    TextAttributesKey.createTextAttributesKey("DEMO.COMMA", DefaultLanguageHighlighterColors.COMMA);

  public static final TextAttributesKey PROPERTY_KEY =
    TextAttributesKey.createTextAttributesKey("DEMO.PROPERTY_KEY", DefaultLanguageHighlighterColors.INSTANCE_FIELD);

  public static final TextAttributesKey BAD_CHARACTER =
    TextAttributesKey.createTextAttributesKey("DEMO.BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);

  private DemoSyntaxHighlighterColors() {
  }
}
```

이름은 `DEMO.KEYWORD`처럼 언어별 접두어를 붙여두는 것이 관리하기 쉽습니다.

## 7. `SyntaxHighlighter`

문법 하이라이팅의 핵심은 여기입니다. 렉서를 반환하고, 토큰별 색상 매핑을 정의합니다.

```java
package com.example.demo.highlighting;

import com.example.demo.lexer.DemoLexerAdapter;
import com.example.demo.psi.DemoTokenTypes;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class DemoSyntaxHighlighter extends SyntaxHighlighterBase {
  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<>();

  static {
    ATTRIBUTES.put(DemoTokenTypes.KEYWORD, DemoSyntaxHighlighterColors.KEYWORD);
    ATTRIBUTES.put(DemoTokenTypes.IDENTIFIER, DemoSyntaxHighlighterColors.IDENTIFIER);
    ATTRIBUTES.put(DemoTokenTypes.STRING, DemoSyntaxHighlighterColors.STRING);
    ATTRIBUTES.put(DemoTokenTypes.NUMBER, DemoSyntaxHighlighterColors.NUMBER);
    ATTRIBUTES.put(DemoTokenTypes.LINE_COMMENT, DemoSyntaxHighlighterColors.COMMENT);
    ATTRIBUTES.put(DemoTokenTypes.L_PAREN, DemoSyntaxHighlighterColors.PARENTHESES);
    ATTRIBUTES.put(DemoTokenTypes.R_PAREN, DemoSyntaxHighlighterColors.PARENTHESES);
    ATTRIBUTES.put(DemoTokenTypes.COMMA, DemoSyntaxHighlighterColors.COMMA);
    ATTRIBUTES.put(DemoTokenTypes.BAD_CHARACTER, DemoSyntaxHighlighterColors.BAD_CHARACTER);
  }

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return new DemoLexerAdapter();
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    return pack(ATTRIBUTES.get(tokenType));
  }
}
```

여기서 중요한 기준은 단순합니다.

- 렉서 토큰만 보고 알 수 있는 것은 여기서 끝냅니다.
- PSI 문맥이 필요한 것은 여기서 처리하지 않습니다.

## 8. `SyntaxHighlighterFactory`

에디터는 등록된 팩토리를 통해 하이라이터를 얻습니다.

```java
package com.example.demo.highlighting;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DemoSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  @Override
  public @NotNull SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile) {
    return new DemoSyntaxHighlighter();
  }
}
```

대부분의 언어는 이 정도로 충분합니다.

## 9. `ColorSettingsPage`

설정 화면에 색상 항목과 미리보기를 노출하려면 이 페이지가 필요합니다.

```java
package com.example.demo.highlighting;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public final class DemoColorSettingsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
    new AttributesDescriptor("Keyword", DemoSyntaxHighlighterColors.KEYWORD),
    new AttributesDescriptor("Identifier", DemoSyntaxHighlighterColors.IDENTIFIER),
    new AttributesDescriptor("String", DemoSyntaxHighlighterColors.STRING),
    new AttributesDescriptor("Number", DemoSyntaxHighlighterColors.NUMBER),
    new AttributesDescriptor("Comment", DemoSyntaxHighlighterColors.COMMENT),
    new AttributesDescriptor("Parentheses", DemoSyntaxHighlighterColors.PARENTHESES),
    new AttributesDescriptor("Comma", DemoSyntaxHighlighterColors.COMMA),
    new AttributesDescriptor("Property Key", DemoSyntaxHighlighterColors.PROPERTY_KEY)
  };

  private static final Map<String, TextAttributesKey> TAGS = Map.of(
    "propertyKey", DemoSyntaxHighlighterColors.PROPERTY_KEY
  );

  @Override
  public @NotNull String getDisplayName() {
    return "Demo";
  }

  @Override
  public @Nullable Icon getIcon() {
    return null;
  }

  @Override
  public @NotNull SyntaxHighlighter getHighlighter() {
    return new DemoSyntaxHighlighter();
  }

  @Override
  public @NotNull String getDemoText() {
    return """
      func main(value, count) {
        // line comment
        let <propertyKey>name</propertyKey> = "demo"
        call(name, 42)
      }
      """;
  }

  @Override
  public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return TAGS;
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return DESCRIPTORS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }
}
```

## 10. `plugin.xml`

하이라이팅이 실제로 동작하려면 확장점 등록이 필요합니다.

```xml
<idea-plugin>
  <id>com.example.demo</id>
  <name>Demo Language</name>
  <vendor>Example</vendor>

  <extensions defaultExtensionNs="com.intellij">
    <fileType
      name="Demo"
      implementationClass="com.example.demo.DemoFileType"
      fieldName="INSTANCE"
      extensions="demo"
      language="Demo"/>

    <lang.syntaxHighlighterFactory
      language="Demo"
      implementationClass="com.example.demo.highlighting.DemoSyntaxHighlighterFactory"/>

    <colorSettingsPage
      implementation="com.example.demo.highlighting.DemoColorSettingsPage"/>
  </extensions>
</idea-plugin>
```

하이라이팅만 먼저 붙일 때는 이 정도 등록으로 시작할 수 있습니다.

## 11. 실제 언어 플러그인이 없을 때의 미리보기 하이라이팅

이 패턴은 "언어 지원 전체"가 아니라 "생성 결과를 그 언어처럼 보이게 보여주기"가 목적일 때 적합합니다.

예를 들어 아래 같은 경우입니다.

- `json -> Go type` 결과 미리보기
- 템플릿 생성 결과 미리보기
- 코드 생성 결과를 읽기 좋게 보여주는 전용 뷰어

이 경우에는 해당 언어 플러그인이 없더라도 충분히 쓸만한 미리보기 하이라이팅을 제공할 수 있습니다.

추천 구조는 아래와 같습니다.

1. 결과를 보여주는 전용 에디터에 하이라이터를 직접 설치합니다.
2. 런타임에 목표 언어의 `FileType`이 있으면 그 하이라이터를 사용합니다.
3. 없으면 플러그인 내부의 경량 `PreviewSyntaxHighlighter`로 폴백합니다.
4. 자체 하이라이터는 대표 토큰만 다룹니다.

핵심은 컴파일 타임에 특정 언어 플러그인 클래스에 묶이지 않는 것입니다. 즉 `com.goide.*` 같은 클래스를 직접 참조하지 않고, `FileTypeManager`와 `HighlighterFactory`만으로 런타임 재사용 여부를 결정합니다.

### 11.1 에디터에 런타임 폴백 하이라이터 설치

아래는 Go 미리보기를 예로 든 Kotlin 코드입니다.

```kotlin
package com.example.preview

import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter

fun installGoPreviewHighlighter(editor: EditorEx, project: Project?) {
  val goFileType = FileTypeManager.getInstance().findFileTypeByName("Go")
  val externalHighlighter = goFileType?.let {
    HighlighterFactory.createHighlighter(it, editor.colorsScheme, project)
  }

  editor.highlighter = externalHighlighter
    ?: LexerEditorHighlighter(GoPreviewSyntaxHighlighter(), editor.colorsScheme)
}
```

이 방식의 장점은 아래와 같습니다.

- Go 플러그인이 설치되어 있으면 기존 하이라이터를 그대로 재사용합니다.
- Go 플러그인이 없어도 미리보기는 계속 동작합니다.
- 선택 의존 없이 배포할 수 있습니다.

같은 패턴은 Go 외의 다른 언어에도 거의 그대로 적용할 수 있습니다. 바뀌는 것은 `findFileTypeByName("Go")`와 폴백 하이라이터 이름 정도입니다.

아직 언어별 미리보기 하이라이터조차 만들지 않았다면, 가장 보수적으로는 평문 하이라이터로 내릴 수도 있습니다.

```kotlin
package com.example.preview

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter

fun installPlainFallback(editor: EditorEx) {
  editor.highlighter = LexerEditorHighlighter(PlainSyntaxHighlighter(), editor.colorsScheme)
}
```

또, 미리보기 전체가 아니라 특정 구간만 목표 언어처럼 보이게 하고 싶다면 `LayeredLexerEditorHighlighter`를 고려할 수 있습니다. 이 방식은 기본 하이라이터 위에 특정 토큰 구간만 다른 하이라이터를 덧입힐 때 적합합니다.

### 11.2 경량 미리보기 하이라이터

미리보기 전용 하이라이터는 전체 언어를 구현할 필요가 없습니다. 생성 결과에서 눈에 띄어야 하는 핵심 조각만 처리하면 충분합니다.

Go 타입 미리보기라면 보통 아래 정도면 충분합니다.

- `type`
- `struct`
- `interface`
- `map`
- `[]`
- `string`, `int`, `bool`, `float64`
- 백틱 태그
- 중괄호, 괄호, 쉼표

```kotlin
package com.example.preview

import com.intellij.lang.Language
import com.intellij.psi.tree.IElementType
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

object GoPreviewColors {
  val KEYWORD: TextAttributesKey =
    TextAttributesKey.createTextAttributesKey("GO_PREVIEW.KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)

  val TYPE_NAME: TextAttributesKey =
    TextAttributesKey.createTextAttributesKey("GO_PREVIEW.TYPE_NAME", DefaultLanguageHighlighterColors.CLASS_NAME)

  val BUILTIN_TYPE: TextAttributesKey =
    TextAttributesKey.createTextAttributesKey("GO_PREVIEW.BUILTIN_TYPE", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL)

  val FIELD_NAME: TextAttributesKey =
    TextAttributesKey.createTextAttributesKey("GO_PREVIEW.FIELD_NAME", DefaultLanguageHighlighterColors.INSTANCE_FIELD)

  val TAG: TextAttributesKey =
    TextAttributesKey.createTextAttributesKey("GO_PREVIEW.TAG", DefaultLanguageHighlighterColors.STRING)

  val PUNCTUATION: TextAttributesKey =
    TextAttributesKey.createTextAttributesKey("GO_PREVIEW.PUNCTUATION", DefaultLanguageHighlighterColors.BRACES)

  val BAD_CHARACTER: TextAttributesKey =
    TextAttributesKey.createTextAttributesKey("GO_PREVIEW.BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)
}
```

```kotlin
package com.example.preview

import com.intellij.lang.Language
import com.intellij.psi.tree.IElementType

private class GoPreviewTokenType(debugName: String) : IElementType(debugName, Language.ANY)

object GoPreviewTokenTypes {
  val TYPE_KEYWORD = GoPreviewTokenType("TYPE_KEYWORD")
  val STRUCT_KEYWORD = GoPreviewTokenType("STRUCT_KEYWORD")
  val INTERFACE_KEYWORD = GoPreviewTokenType("INTERFACE_KEYWORD")
  val MAP_KEYWORD = GoPreviewTokenType("MAP_KEYWORD")
  val BUILTIN_TYPE = GoPreviewTokenType("BUILTIN_TYPE")
  val TYPE_NAME = GoPreviewTokenType("TYPE_NAME")
  val FIELD_NAME = GoPreviewTokenType("FIELD_NAME")
  val TAG = GoPreviewTokenType("TAG")
  val L_BRACE = GoPreviewTokenType("L_BRACE")
  val R_BRACE = GoPreviewTokenType("R_BRACE")
  val L_BRACKET = GoPreviewTokenType("L_BRACKET")
  val R_BRACKET = GoPreviewTokenType("R_BRACKET")
  val L_PAREN = GoPreviewTokenType("L_PAREN")
  val R_PAREN = GoPreviewTokenType("R_PAREN")
  val COMMA = GoPreviewTokenType("COMMA")
  val BAD_CHARACTER = GoPreviewTokenType("BAD_CHARACTER")
}
```

```kotlin
package com.example.preview

import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.tree.IElementType

class GoPreviewSyntaxHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = GoPreviewLexer()

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = when (tokenType) {
    GoPreviewTokenTypes.TYPE_KEYWORD,
    GoPreviewTokenTypes.STRUCT_KEYWORD,
    GoPreviewTokenTypes.INTERFACE_KEYWORD,
    GoPreviewTokenTypes.MAP_KEYWORD -> pack(GoPreviewColors.KEYWORD)

    GoPreviewTokenTypes.BUILTIN_TYPE -> pack(GoPreviewColors.BUILTIN_TYPE)
    GoPreviewTokenTypes.TYPE_NAME -> pack(GoPreviewColors.TYPE_NAME)
    GoPreviewTokenTypes.FIELD_NAME -> pack(GoPreviewColors.FIELD_NAME)
    GoPreviewTokenTypes.TAG -> pack(GoPreviewColors.TAG)

    GoPreviewTokenTypes.L_BRACE,
    GoPreviewTokenTypes.R_BRACE,
    GoPreviewTokenTypes.L_BRACKET,
    GoPreviewTokenTypes.R_BRACKET,
    GoPreviewTokenTypes.L_PAREN,
    GoPreviewTokenTypes.R_PAREN,
    GoPreviewTokenTypes.COMMA -> pack(GoPreviewColors.PUNCTUATION)

    GoPreviewTokenTypes.BAD_CHARACTER -> pack(GoPreviewColors.BAD_CHARACTER)
    else -> emptyArray()
  }
}
```

### 11.3 경량 렉서의 현실적인 범위

미리보기 렉서는 파서를 대체하려고 하면 금방 무거워집니다. 목적은 "정확한 Go 지원"이 아니라 "읽기 좋은 생성 결과"여야 합니다.

그래서 보통 아래 정도의 규칙으로 충분합니다.

- 예약어 몇 개를 키워드로 분류
- 내장 타입 이름을 별도 분류
- 대문자로 시작하는 식별자를 타입 이름으로 분류
- 필드 이름과 백틱 태그를 대충 구분
- 괄호류와 쉼표를 구분

```kotlin
package com.example.preview

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

class GoPreviewLexer : LexerBase() {
  private var buffer: CharSequence = ""
  private var startOffset: Int = 0
  private var endOffset: Int = 0
  private var tokenStart: Int = 0
  private var tokenEnd: Int = 0
  private var tokenType: IElementType? = null

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    this.buffer = buffer
    this.startOffset = startOffset
    this.endOffset = endOffset
    tokenStart = startOffset
    tokenEnd = startOffset
    locateToken()
  }

  override fun getState(): Int = 0

  override fun getTokenType(): IElementType? = tokenType

  override fun getTokenStart(): Int = tokenStart

  override fun getTokenEnd(): Int = tokenEnd

  override fun getBufferSequence(): CharSequence = buffer

  override fun getBufferEnd(): Int = endOffset

  override fun advance() {
    locateToken()
  }

  private fun locateToken() {
    var index = tokenEnd
    while (index < endOffset && buffer[index].isWhitespace()) {
      index++
    }

    if (index >= endOffset) {
      tokenType = null
      tokenStart = endOffset
      tokenEnd = endOffset
      return
    }

    tokenStart = index
    val current = buffer[index]

    when (current) {
      '{' -> single(index, GoPreviewTokenTypes.L_BRACE)
      '}' -> single(index, GoPreviewTokenTypes.R_BRACE)
      '[' -> single(index, GoPreviewTokenTypes.L_BRACKET)
      ']' -> single(index, GoPreviewTokenTypes.R_BRACKET)
      '(' -> single(index, GoPreviewTokenTypes.L_PAREN)
      ')' -> single(index, GoPreviewTokenTypes.R_PAREN)
      ',' -> single(index, GoPreviewTokenTypes.COMMA)
      '`' -> readTag(index)
      else -> {
        if (current.isJavaIdentifierStart()) {
          readIdentifier(index)
        }
        else {
          single(index, GoPreviewTokenTypes.BAD_CHARACTER)
        }
      }
    }
  }

  private fun readIdentifier(index: Int) {
    var end = index + 1
    while (end < endOffset && buffer[end].isJavaIdentifierPart()) {
      end++
    }

    val text = buffer.subSequence(index, end).toString()
    tokenType = when {
      text == "type" -> GoPreviewTokenTypes.TYPE_KEYWORD
      text == "struct" -> GoPreviewTokenTypes.STRUCT_KEYWORD
      text == "interface" -> GoPreviewTokenTypes.INTERFACE_KEYWORD
      text == "map" -> GoPreviewTokenTypes.MAP_KEYWORD
      text in setOf("string", "int", "int64", "bool", "float64", "any") -> GoPreviewTokenTypes.BUILTIN_TYPE
      text.first().isUpperCase() -> GoPreviewTokenTypes.TYPE_NAME
      else -> GoPreviewTokenTypes.FIELD_NAME
    }
    tokenEnd = end
  }

  private fun readTag(index: Int) {
    var end = index + 1
    while (end < endOffset && buffer[end] != '`') {
      end++
    }
    if (end < endOffset) {
      end++
    }
    tokenType = GoPreviewTokenTypes.TAG
    tokenEnd = end
  }

  private fun single(index: Int, type: IElementType) {
    tokenType = type
    tokenEnd = index + 1
  }
}
```

이 렉서는 정확한 Go 문법 처리기가 아닙니다. 하지만 타입 생성 결과 미리보기에는 충분히 실용적입니다.

### 11.4 선택 의존이 필요해지는 시점

아래 수준까지 가면 단순 폴백 하이라이터를 넘어섭니다.

- 실제 Go PSI 사용
- 검사
- 포매터
- 참조 해석
- 리팩터링

이 경우에는 해당 언어 플러그인과 직접 연동해야 하므로 선택 의존이나 별도 모듈 분리가 필요합니다.

개념적으로는 아래처럼 나눕니다.

```xml
<depends optional="true" config-file="my.plugin.go.xml">org.jetbrains.plugins.go</depends>
```

그 다음 `my.plugin.go.xml` 안에 Go 플러그인이 있을 때만 활성화할 확장점을 둡니다.

```xml
<idea-plugin>
  <extensions defaultExtensionNs="com.intellij">
    <someExtension implementation="com.example.preview.GoSpecificExtension"/>
  </extensions>
</idea-plugin>
```

위 방식은 하나의 플러그인 안에서 Go 통합만 조건부로 여는 가장 직접적인 패턴입니다.

실전에서는 언어별 통합 기능을 아예 별도 조각이나 별도 모듈 XML로 나누고, 그 조각 안에서 대상 언어 플러그인 의존을 직접 선언하는 방식도 자주 씁니다.

```xml
<idea-plugin package="com.example.preview.go">
  <dependencies>
    <plugin id="org.jetbrains.plugins.go"/>
  </dependencies>

  <extensions defaultExtensionNs="com.intellij">
    <completion.contributor
      language="go"
      implementationClass="com.example.preview.GoSpecificContributor"/>
  </extensions>
</idea-plugin>
```

핵심은 구조를 두 층으로 나누는 것입니다.

- 기본 미리보기 기능은 언어 플러그인 없이도 항상 동작하게 유지합니다.
- 실제 언어 플러그인 클래스나 확장점을 참조하는 코드는 별도 조각이나 선택 의존 영역 안에만 둡니다.

즉, 하이라이터 재사용은 런타임 탐색으로 느슨하게 연결하고, 강한 통합 기능만 분리하는 것이 안전합니다.

### 11.5 언제 이 방식을 써야 하나

이 방식이 맞는 경우는 아래와 같습니다.

- 생성 결과를 읽기 좋게 보여주는 것이 목적일 때
- 특정 언어 플러그인을 필수 의존으로 만들고 싶지 않을 때
- 편집기 안에서 미리보기만 제공하면 충분할 때

이 방식이 맞지 않는 경우는 아래와 같습니다.

- 실제 언어 수준의 정확한 구문 분석이 필요할 때
- 참조 이동, 포매팅, 검사까지 기대할 때
- 기존 언어 플러그인의 PSI나 확장점에 강하게 의존할 때

요약하면, "설치되어 있으면 기존 언어 하이라이터를 쓰고, 없으면 자체 경량 하이라이터로 내려간다"가 가장 현실적인 기본 전략입니다.

## 12. `ParserDefinition`은 언제 필요한가

순수한 렉서 기반 색상만 보면 꼭 필요하지는 않습니다. 하지만 언어 플러그인으로 계속 확장할 계획이라면 보통 초기에 같이 넣어두는 편이 자연스럽습니다.

```java
package com.example.demo.psi;

import com.example.demo.DemoLanguage;
import com.example.demo.lexer.DemoLexerAdapter;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public final class DemoParserDefinition implements ParserDefinition {
  public static final IFileElementType FILE = new IFileElementType(DemoLanguage.INSTANCE);

  @Override
  public @NotNull Lexer createLexer(Project project) {
    return new DemoLexerAdapter();
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    throw new UnsupportedOperationException("Parser is not implemented yet");
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE;
  }

  @Override
  public @NotNull TokenSet getCommentTokens() {
    return TokenSet.create(DemoTokenTypes.LINE_COMMENT);
  }

  @Override
  public @NotNull TokenSet getStringLiteralElements() {
    return TokenSet.create(DemoTokenTypes.STRING);
  }

  @Override
  public @NotNull PsiElement createElement(ASTNode node) {
    throw new UnsupportedOperationException("PSI factory is not implemented yet");
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new DemoFile(viewProvider);
  }
}
```

등록은 아래처럼 추가합니다.

```xml
<lang.parserDefinition
  language="Demo"
  implementationClass="com.example.demo.psi.DemoParserDefinition"/>
```

## 13. 문맥 기반 강조가 필요할 때 `Annotator` 추가

예를 들어 아래 같은 경우는 렉서만으로 판단하기 어렵습니다.

- 특정 위치의 문자열만 프로퍼티 키로 칠하고 싶을 때
- 특정 식별자가 함수 호출일 때만 다른 색을 주고 싶을 때
- 잘못된 문법 조각에 오류 강조를 주고 싶을 때

이럴 때는 `Annotator`를 붙입니다.

```java
package com.example.demo.highlighting;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class DemoAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (isPropertyKey(element)) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .textAttributes(DemoSyntaxHighlighterColors.PROPERTY_KEY)
        .create();
    }
  }

  private boolean isPropertyKey(@NotNull PsiElement element) {
    return element.getNode() != null
      && "PROPERTY".equals(element.getNode().getElementType().toString());
  }
}
```

등록은 아래처럼 합니다.

```xml
<annotator
  language="Demo"
  implementationClass="com.example.demo.highlighting.DemoAnnotator"/>
```

여기서 핵심은 역할 분리입니다.

- `SyntaxHighlighter`: 토큰만 보고 칠할 수 있는 것
- `Annotator`: PSI 문맥을 보고 칠해야 하는 것

## 14. 구현 순서 추천

처음부터 모든 기능을 한 번에 넣지 말고 아래 순서로 가면 가장 안정적입니다.

1. `Language`
2. `LanguageFileType`
3. 렉서
4. `SyntaxHighlighterColors`
5. `SyntaxHighlighter`
6. `SyntaxHighlighterFactory`
7. `plugin.xml` 등록
8. `ColorSettingsPage`
9. 필요하면 `ParserDefinition`
10. 필요하면 `Annotator`

## 15. 바로 시작할 때 체크할 항목

- `Language` 이름과 `plugin.xml`의 `language` 값이 정확히 같은지 확인합니다.
- 렉서가 실제로 원하는 토큰 타입을 내보내는지 확인합니다.
- `getTokenHighlights()`에서 빠진 토큰이 없는지 확인합니다.
- 설정 화면 데모 텍스트에 대표 문법이 충분히 들어 있는지 확인합니다.
- 문맥 기반 강조를 `SyntaxHighlighter`에 억지로 넣지 않았는지 확인합니다.

## 16. 가장 작은 출발점

정말 최소로 시작하려면 아래 네 조각만 먼저 맞추면 됩니다.

```text
Language
FileType
SyntaxHighlighter
SyntaxHighlighterFactory
```

그 다음에 설정 화면이 필요하면 `ColorSettingsPage`, PSI 문맥 강조가 필요하면 `Annotator`를 얹는 방식으로 확장하면 됩니다.
