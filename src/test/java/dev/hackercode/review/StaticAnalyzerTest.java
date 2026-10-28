package dev.hackercode.review;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hackercode.execution.Language;
import org.junit.jupiter.api.Test;

class StaticAnalyzerTest {
    final StaticAnalyzer analyzer = new StaticAnalyzer();

    private java.util.List<String> rules(Language lang, String code) {
        return analyzer.analyze(lang, code).findings().stream().map(StaticAnalyzer.Finding::rule).toList();
    }

    @Test
    void detectsLoopNestingInBraceLanguages() {
        String code = """
                int main() {
                  for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                      for (int k = 0; k < n; k++) { s++; }
                    }
                  }
                }
                """;
        StaticAnalyzer.Result r = analyzer.analyze(Language.CPP, code);
        assertThat(r.maxLoopDepth()).isEqualTo(3);
        assertThat(r.estimatedTime()).startsWith("O(n^3)");
        assertThat(rules(Language.CPP, code)).contains("deep-loops");
    }

    @Test
    void detectsLoopNestingInPython() {
        String code = "for i in a:\n    for j in a:\n        print(i, j)\nfor k in a:\n    pass\n";
        assertThat(analyzer.analyze(Language.PYTHON, code).maxLoopDepth()).isEqualTo(2);
    }

    @Test
    void flagsLanguageSpecificHazards() {
        assertThat(rules(Language.C, "char b[8];\ngets(b);\nstrcpy(b, s);")).contains("c-gets", "c-unbounded-copy");
        assertThat(rules(Language.JAVA, "if (name == \"admin\") {}")).contains("java-string-eq");
        assertThat(rules(Language.PYTHON, "def f(xs=[]):\n    return xs\n")).contains("py-mutable-default");
        assertThat(rules(Language.PYTHON, "try:\n    x()\nexcept:\n    pass\n")).contains("py-bare-except");
        assertThat(rules(Language.JAVASCRIPT, "var x = 1;\nif (x == '1') {}")).contains("js-var", "js-loose-eq");
        assertThat(rules(Language.GO, "v, _ := strconv.Atoi(s)")).contains("go-ignored-err");
    }

    @Test
    void doesNotFlagCleanCode() {
        String clean = """
                import sys

                def main() -> None:
                    n = int(sys.stdin.readline())
                    print("even" if n % 2 == 0 else "odd")

                main()
                """;
        assertThat(analyzer.analyze(Language.PYTHON, clean).findings()).isEmpty();
    }

    @Test
    void ignoresPatternsInsideStringLiterals() {
        assertThat(rules(Language.C, "printf(\"never call gets(buf)\\n\");")).doesNotContain("c-gets");
    }
}
